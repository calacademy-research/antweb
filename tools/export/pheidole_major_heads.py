#!/usr/bin/env python3
"""One-off export for Jack Longino (2026-10-27): head images of Pheidole major workers, grouped by his clades.

Input: tools/data/pheidole_clades_longino_2026-10-27.csv (columns "major clade","species"; names such as
"Pheidole albipes", "Pheidole impressa detrita" or morphospecies such as "Pheidole JTL202").

Output (under --out, default /root/pheidole_export_<today>):
  <clade>/<clade>_<Genus_species>_<code>.jpg   medium-size head images (shot type h) of major workers
                                               (a second head shot of the same specimen gets _2, ...)
  <clade>/<clade>_contact_sheet.pdf            4 x 6 tiles per page, species and specimen code under each tile
  <clade>/index.csv                            the rows of index.csv for this clade
  index.csv                                    one row per image, all clades
  not_found.csv                                species with no AntWeb name, or no major-worker head image, and why
  download_failures.csv                        images that could not be fetched (only when there are any)
  zips/<clade>.zip                             the clade folder, ready to share

Major worker = specimen.caste 'worker' and specimen.subcaste 'major/soldier', the rule of the site's caste filter
(Caste.getSpecimenClause("majorSoldier")) and of the API's /specimens?caste=worker&subcaste=major/soldier.
The upload sets subcaste from the life stage text (Caste.getWorkerSubcaste: "major", "soldier", "1s" ...).

Name matching: AntWeb's taxon_name is "myrmicinaepheidole <epithet(s)>" in lower case. Jack's epithets are
compared case-insensitively with taxon.taxon_name and with specimen.original_taxon_name (the name as uploaded,
for specimens AntWeb later filed under another name). A name that matches only when punctuation is ignored
("jtl-202" for "JTL202") is used and marked in the match column; other close names are listed in not_found.csv.

Runs on the AntWeb host (staging): python3 standard library only. The database is read with the mysql client
inside the MySQL container (docker exec), with the login from etc/db.properties (the password goes through the
environment, never the command line). Images come from https://static.antweb.org (4 downloads at a time,
retries, resumable: files already on disk are kept). The PDFs embed the JPEG files as they are (no Pillow).

    python3 pheidole_major_heads.py --dry-run     # query only: counts per clade and the size estimate
    python3 pheidole_major_heads.py               # query, download, contact sheets, zips

Unit test (no database): tests/unit/test_pheidole_export.py
"""
import argparse
import concurrent.futures
import csv
import datetime
import difflib
import math
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_CLADES = os.path.join(HERE, "..", "data", "pheidole_clades_longino_2026-10-27.csv")
PREFIX = "myrmicinaepheidole "
IMAGE_BASE = "https://static.antweb.org/images"
SPECIMEN_PAGE = "https://www.antweb.org/specimen.do?code="
SIZE = "med"  # SpecimenImage.getMedres(): <code>/<code>_<shot>_<n>_med.jpg
USER_AGENT = "AntWeb one-off export (antweb@antweb.org)"

INDEX_COLUMNS = ["clade", "species", "antweb_taxon_name", "match", "taxon_status", "specimen_code", "shot_number",
                 "caste", "subcaste", "life_stage", "country", "adm1", "collection_code", "locality_name",
                 "photographer", "image_url", "specimen_url", "file_name"]
NOT_FOUND_COLUMNS = ["clade", "species", "antweb_taxon_name", "reason", "close_names"]


# ---------------------------------------------------------------- names and files (pure, unit-tested)

def read_clades(path):
    """[(clade, species)] in file order; blank rows and exact duplicate rows dropped."""
    rows, seen = [], set()
    with open(path, newline="", encoding="utf-8-sig") as f:
        reader = csv.reader(f)
        header = next(reader, None)
        if not header or [h.strip().lower() for h in header[:2]] != ["major clade", "species"]:
            raise SystemExit(f"{path}: expected the header 'major clade,species', got {header}")
        for r in reader:
            if len(r) < 2:
                continue
            clade, species = r[0].strip(), " ".join(r[1].split())
            if not clade or not species or (clade, species) in seen:
                continue
            seen.add((clade, species))
            rows.append((clade, species))
    return rows


def taxon_key(species):
    """'Pheidole JTL202' -> 'myrmicinaepheidole jtl202'; None when the genus is not Pheidole."""
    parts = species.split()
    if len(parts) < 2 or parts[0].lower() != "pheidole":
        return None
    return PREFIX + " ".join(parts[1:]).lower()


def squash(name):
    """Lower case, letters and digits only: 'myrmicinaepheidole jtl-202' -> 'myrmicinaepheidolejtl202'."""
    return re.sub(r"[^0-9a-z]", "", name.lower())


def match_name(species, known):
    """Match one of Jack's names against the set of known AntWeb names (lower case taxon names).

    Returns (taxon_name or None, how, close) where how is 'exact', 'ignoring punctuation', 'not Pheidole' or
    'none', and close is a list of close AntWeb names (for not_found.csv)."""
    key = taxon_key(species)
    if key is None:
        return None, "not Pheidole", []
    if key in known:
        return key, "exact", []
    sq = squash(key)
    same = sorted(k for k in known if squash(k) == sq)
    if len(same) == 1:
        return same[0], "ignoring punctuation", []
    epithet = key[len(PREFIX):]
    epithets = {k[len(PREFIX):]: k for k in known if k.startswith(PREFIX)}
    close = [epithets[e] for e in difflib.get_close_matches(epithet, list(epithets), n=3, cutoff=0.8)]
    return None, "none", sorted(set(same) | set(close)) if same else close


def safe(text):
    """File-name part: letters, digits, '.', '-' and '_' kept, everything else '_'."""
    return re.sub(r"_+", "_", re.sub(r"[^A-Za-z0-9._-]+", "_", text.strip())).strip("_") or "x"


def file_name(clade, species, code, shot_number=1):
    """<clade>_<Genus_species>_<specimencode>.jpg; a second head shot of the same specimen ends in _2."""
    base = f"{safe(clade)}_{safe(species.replace(' ', '_'))}_{safe(code)}"
    if str(shot_number) not in ("", "1"):
        base += f"_{safe(str(shot_number))}"
    return base + ".jpg"


def image_url(code, shot_number, base=IMAGE_BASE, size=SIZE):
    return f"{base}/{code}/{code}_h_{shot_number}_{size}.jpg"


def unescape_mysql(value):
    """mysql --batch escapes \\, tab, newline and NUL; NULL is printed as NULL."""
    if value == "NULL":
        return ""
    return re.sub(r"\\(.)", lambda m: {"n": "\n", "t": "\t", "0": "\0", "\\": "\\"}.get(m.group(1), m.group(1)),
                  value)


def parse_tsv(text, ncols):
    rows = []
    for line in text.splitlines():
        if not line:
            continue
        cells = [unescape_mysql(c) for c in line.split("\t")]
        if len(cells) != ncols:
            raise SystemExit(f"unexpected mysql output line ({len(cells)} columns, wanted {ncols}): {line[:200]}")
        rows.append(cells)
    return rows


# ---------------------------------------------------------------- database

def read_db_login(path):
    props = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.rstrip("\n").split("=", 1)
                props[k.strip()] = v
    if "user" not in props or "password" not in props:
        raise SystemExit(f"{path}: no user= or password= line")
    return props["user"], props["password"]


def mysql(sql, ncols, args):
    user, password = read_db_login(args.db_properties)
    env = dict(os.environ, MYSQL_PWD=password)
    cmd = ["docker", "exec", "-e", "MYSQL_PWD", args.mysql_container, "mysql", "-u", user, "--batch",
           "--skip-column-names", "--default-character-set=utf8mb4", args.database, "-e", sql]
    p = subprocess.run(cmd, env=env, capture_output=True, text=True)
    if p.returncode != 0:
        raise SystemExit("mysql failed: " + p.stderr.strip())
    return parse_tsv(p.stdout, ncols)


TAXA_SQL = ("select lower(taxon_name), coalesce(status,''), lower(coalesce(current_valid_name,'')) from taxon"
            " where taxon_name like 'myrmicinaepheidole %'")

# Every worker head image of a Pheidole specimen (majors and others, so not_found.csv can say why).
HEADS_SQL = ("select s.code, lower(s.taxon_name), lower(coalesce(s.original_taxon_name,'')), coalesce(s.caste,''),"
             " coalesce(s.subcaste,''), coalesce(s.life_stage,''), coalesce(s.country,''), coalesce(s.adm1,''),"
             " coalesce(s.collectioncode,''), coalesce(s.localityname,''), i.shot_number, coalesce(a.name,'')"
             " from specimen s join image i on i.image_of_id = s.code"
             " left join artist a on a.id = i.artist"
             " where (s.taxon_name like 'myrmicinaepheidole %' or s.original_taxon_name like 'myrmicinaepheidole %')"
             " and lower(trim(i.shot_type)) = 'h' and s.caste = 'worker'")
HEADS_COLS = ["code", "taxon_name", "original_taxon_name", "caste", "subcaste", "life_stage", "country", "adm1",
              "collection_code", "locality_name", "shot_number", "photographer"]

# Specimen counts per name: major workers, all workers, all specimens.
COUNTS_SQL = ("select lower(taxon_name), sum(caste = 'worker' and subcaste = 'major/soldier'), sum(caste = 'worker'),"
              " count(*) from specimen where taxon_name like 'myrmicinaepheidole %' group by lower(taxon_name)")


def is_major(row):
    return row["caste"] == "worker" and row["subcaste"] == "major/soldier"


def load_data(args):
    taxa = {t: (st, cv) for t, st, cv in mysql(TAXA_SQL, 3, args)}
    heads = [dict(zip(HEADS_COLS, r)) for r in mysql(HEADS_SQL, len(HEADS_COLS), args)]
    counts = {t: (int(float(m or 0)), int(float(w or 0)), int(n)) for t, m, w, n in mysql(COUNTS_SQL, 4, args)}
    return taxa, heads, counts


# ---------------------------------------------------------------- plan

def build_plan(clades, taxa, heads, counts, max_per_species=0):
    """Returns (index rows, not_found rows)."""
    by_name = {}
    for h in heads:
        names = {h["taxon_name"]}
        if h["original_taxon_name"]:
            names.add(h["original_taxon_name"])
        for n in names:
            by_name.setdefault(n, []).append(h)
    known = set(taxa) | {h["original_taxon_name"] for h in heads if h["original_taxon_name"].startswith(PREFIX)}

    index, not_found = [], []
    for clade, species in clades:
        name, how, close = match_name(species, known)
        if name is None:
            reason = ("not a Pheidole name" if how == "not Pheidole"
                      else "no AntWeb taxon with this name")
            not_found.append({"clade": clade, "species": species, "antweb_taxon_name": "", "reason": reason,
                              "close_names": "; ".join(close)})
            continue
        status, current = taxa.get(name, ("", ""))
        rows = by_name.get(name, [])
        majors = sorted((h for h in rows if is_major(h)), key=lambda h: (h["code"], h["shot_number"]))
        if max_per_species:
            keep, seen = [], []
            for h in majors:
                if h["code"] not in seen:
                    if len(seen) >= max_per_species:
                        continue
                    seen.append(h["code"])
                keep.append(h)
            majors = keep
        if not majors:
            m, w, n = counts.get(name, (0, 0, 0))
            hints = [h for h in rows if re.search(r"major|soldier", h["life_stage"], re.I)]
            if n == 0 and status and status not in ("valid", "morphotaxon", "unrecognized", "indetermined") and current:
                reason = f"AntWeb lists this name as {status}; its specimens are filed under {current[len(PREFIX):] if current.startswith(PREFIX) else current}"
            elif n == 0:
                reason = "AntWeb has the name but no specimens of it"
            elif m == 0:
                reason = f"no major-worker specimens on AntWeb ({n} specimens, {w} workers)"
            else:
                reason = f"{m} major-worker specimens but none has a head image"
            if rows:
                reason += f"; {len(rows)} worker head images of other subcastes"
            if hints:
                reason += (f"; {len(hints)} of them have 'major' or 'soldier' in the life stage but subcaste "
                           f"'{hints[0]['subcaste']}' (e.g. {hints[0]['code']}: {hints[0]['life_stage']})")
            not_found.append({"clade": clade, "species": species, "antweb_taxon_name": name, "reason": reason,
                              "close_names": ""})
            continue
        for h in majors:
            match = how
            if h["taxon_name"] != name:
                match += f"; uploaded as this name, now filed under {h['taxon_name']}"
            index.append({
                "clade": clade, "species": species, "antweb_taxon_name": name, "match": match,
                "taxon_status": status, "specimen_code": h["code"], "shot_number": h["shot_number"],
                "caste": h["caste"], "subcaste": h["subcaste"], "life_stage": h["life_stage"],
                "country": h["country"], "adm1": h["adm1"], "collection_code": h["collection_code"],
                "locality_name": h["locality_name"], "photographer": h["photographer"],
                "image_url": image_url(h["code"], h["shot_number"]), "specimen_url": SPECIMEN_PAGE + h["code"],
                "file_name": file_name(clade, species, h["code"], h["shot_number"]),
            })
    return index, not_found


def write_csv(path, columns, rows):
    tmp = path + ".part"
    with open(tmp, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=columns, extrasaction="ignore")
        w.writeheader()
        w.writerows(rows)
    os.replace(tmp, path)


# ---------------------------------------------------------------- downloads

def is_jpeg_file(path):
    try:
        with open(path, "rb") as f:
            return os.path.getsize(path) > 100 and f.read(2) == b"\xff\xd8"
    except OSError:
        return False


def fetch(url, dest, image_dir=None, retries=4, timeout=60):
    """Copy from image_dir when the file is there, else download. Returns None or an error text."""
    if is_jpeg_file(dest):
        return None
    tmp = dest + ".part"
    if image_dir:
        local = os.path.join(image_dir, *url.split("/")[-2:])
        if is_jpeg_file(local):
            shutil.copyfile(local, tmp)
            os.replace(tmp, dest)
            return None
    err = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=timeout) as r, open(tmp, "wb") as f:
                shutil.copyfileobj(r, f, 256 * 1024)
            if not is_jpeg_file(tmp):
                err = "not a JPEG (a challenge or error page?)"
            else:
                os.replace(tmp, dest)
                return None
        except urllib.error.HTTPError as e:
            err = f"HTTP {e.code}"
            if e.code in (403, 404, 410):
                break
        except (urllib.error.URLError, OSError, TimeoutError) as e:
            err = str(e)
        time.sleep(2 ** (attempt + 1))
    try:
        os.remove(tmp)
    except OSError:
        pass
    return err or "failed"


def download_all(index, out, workers, image_dir):
    jobs = [(r["image_url"], os.path.join(out, safe(r["clade"]), r["file_name"])) for r in index]
    for _, dest in jobs:
        os.makedirs(os.path.dirname(dest), exist_ok=True)
    todo = [j for j in jobs if not is_jpeg_file(j[1])]
    print(f"images: {len(jobs)}, already on disk: {len(jobs) - len(todo)}, to fetch: {len(todo)}", flush=True)
    failures, done, lock = [], [0], threading.Lock()
    start = time.time()

    def one(job):
        err = fetch(job[0], job[1], image_dir)
        with lock:
            done[0] += 1
            if err:
                failures.append({"image_url": job[0], "file": job[1], "error": err})
            if done[0] % 200 == 0 or done[0] == len(todo):
                rate = done[0] / max(time.time() - start, 1)
                print(f"  {done[0]}/{len(todo)} fetched, {len(failures)} failed, "
                      f"about {int((len(todo) - done[0]) / max(rate, 0.01) / 60)} min left", flush=True)

    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as ex:
        list(ex.map(one, todo))
    return failures


def head_size(url, timeout=30):
    try:
        req = urllib.request.Request(url, method="HEAD", headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=timeout) as r:
            n = int(r.headers.get("Content-Length") or 0)
            return n if (r.headers.get("Content-Type") or "").startswith("image/") and n > 0 else None
    except (urllib.error.URLError, OSError, ValueError, TimeoutError):
        return None


# ---------------------------------------------------------------- contact sheets (stdlib PDF, JPEGs embedded as is)

def jpeg_info(data):
    """(width, height, components) from the SOF marker, or None."""
    if data[:2] != b"\xff\xd8":
        return None
    i = 2
    while i + 9 < len(data):
        if data[i] != 0xFF:
            i += 1
            continue
        marker = data[i + 1]
        if marker in (0xD8, 0x01) or 0xD0 <= marker <= 0xD7 or marker == 0xFF:
            i += 1 if marker == 0xFF else 2
            continue
        length = int.from_bytes(data[i + 2:i + 4], "big")
        if 0xC0 <= marker <= 0xCF and marker not in (0xC4, 0xC8, 0xCC):
            h = int.from_bytes(data[i + 5:i + 7], "big")
            w = int.from_bytes(data[i + 7:i + 9], "big")
            return (w, h, data[i + 9]) if w and h else None
        i += 2 + length
    return None


def pdf_text(s, max_chars):
    s = s if len(s) <= max_chars else s[:max_chars - 1] + "~"
    s = s.encode("latin-1", "replace").decode("latin-1")
    return "(" + s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)") + ")"


PAGE_W, PAGE_H, MARGIN, HEADER = 612, 792, 28, 26  # US Letter, points
COLS, ROWS, TEXT_H = 4, 6, 20


def contact_sheet(path, title, tiles, footer=""):
    """tiles: [(jpeg path, line 1, line 2)]. Writes a PDF with COLS x ROWS tiles per page."""
    per_page = COLS * ROWS
    pages = max(1, math.ceil(len(tiles) / per_page))
    tile_w = (PAGE_W - 2 * MARGIN) / COLS
    tile_h = (PAGE_H - 2 * MARGIN - HEADER) / ROWS
    offsets = {}
    tmp = path + ".part"
    with open(tmp, "wb") as f:
        def obj(num, body_head, stream=None):
            offsets[num] = f.tell()
            f.write(f"{num} 0 obj\n".encode())
            if stream is None:
                f.write(body_head.encode() + b"\nendobj\n")
            else:
                f.write(body_head.encode() + b"\nstream\n" + stream + b"\nendstream\nendobj\n")

        f.write(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
        obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
        obj(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        obj(4, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
        nxt, kids = 5, []
        for p in range(pages):
            ops = [f"BT /F2 11 Tf {MARGIN} {PAGE_H - MARGIN - 12} Td {pdf_text(title, 80)} Tj ET",
                   f"BT /F1 9 Tf {PAGE_W - MARGIN - 70} {PAGE_H - MARGIN - 12} Td "
                   f"{pdf_text(f'page {p + 1} of {pages}', 20)} Tj ET"]
            if footer:
                ops.append(f"BT /F1 6 Tf {MARGIN} {MARGIN - 14} Td {pdf_text(footer, 160)} Tj ET")
            xobjs = []
            for k, (jpg, line1, line2) in enumerate(tiles[p * per_page:(p + 1) * per_page]):
                col, row = k % COLS, k // COLS
                x0 = MARGIN + col * tile_w
                y0 = PAGE_H - MARGIN - HEADER - (row + 1) * tile_h
                max_chars = int((tile_w - 4) / 3.4)
                ops.append(f"BT /F1 6.5 Tf {x0 + 2:.1f} {y0 + 11:.1f} Td {pdf_text(line1, max_chars)} Tj ET")
                ops.append(f"BT /F1 6.5 Tf {x0 + 2:.1f} {y0 + 3:.1f} Td {pdf_text(line2, max_chars)} Tj ET")
                try:
                    with open(jpg, "rb") as jf:
                        data = jf.read()
                except OSError:
                    data = b""
                info = jpeg_info(data)
                box_w, box_h = tile_w - 6, tile_h - TEXT_H - 4
                if not info:
                    ops.append(f"BT /F1 7 Tf {x0 + 2:.1f} {y0 + TEXT_H + box_h / 2:.1f} Td (image missing) Tj ET")
                    continue
                w, h, comps = info
                cs = {1: "/DeviceGray", 4: "/DeviceCMYK"}.get(comps, "/DeviceRGB")
                decode = " /Decode [1 0 1 0 1 0 1 0]" if comps == 4 else ""
                name = f"Im{k}"
                obj(nxt, f"<< /Type /XObject /Subtype /Image /Width {w} /Height {h} /ColorSpace {cs}"
                         f" /BitsPerComponent 8 /Filter /DCTDecode{decode} /Length {len(data)} >>", data)
                xobjs.append((name, nxt))
                nxt += 1
                scale = min(box_w / w, box_h / h)
                dw, dh = w * scale, h * scale
                dx = x0 + 3 + (box_w - dw) / 2
                dy = y0 + TEXT_H + 2 + (box_h - dh) / 2
                ops.append(f"q {dw:.2f} 0 0 {dh:.2f} {dx:.2f} {dy:.2f} cm /{name} Do Q")
            content = "\n".join(ops).encode("latin-1")
            obj(nxt, f"<< /Length {len(content)} >>", content)
            content_num = nxt
            xo = " ".join(f"/{n} {num} 0 R" for n, num in xobjs)
            obj(nxt + 1, f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 {PAGE_W} {PAGE_H}] /Contents {content_num} 0 R"
                         f" /Resources << /Font << /F1 3 0 R /F2 4 0 R >> /XObject << {xo} >> >> >>")
            kids.append(nxt + 1)
            nxt += 2
        obj(2, f"<< /Type /Pages /Count {len(kids)} /Kids [{' '.join(f'{k} 0 R' for k in kids)}] >>")
        xref = f.tell()
        f.write(f"xref\n0 {nxt}\n0000000000 65535 f \n".encode())
        for num in range(1, nxt):
            f.write(f"{offsets[num]:010d} 00000 n \n".encode())
        f.write(f"trailer\n<< /Size {nxt} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode())
    os.replace(tmp, path)
    return pages


# ---------------------------------------------------------------- main

def summary(clades, index, not_found):
    order = []
    for c, _ in clades:
        if c not in order:
            order.append(c)
    listed = {c: 0 for c in order}
    for c, _ in clades:
        listed[c] += 1
    imgs = {c: 0 for c in order}
    with_img = {c: set() for c in order}
    for r in index:
        imgs[r["clade"]] += 1
        with_img[r["clade"]].add(r["species"])
    nomatch = {c: 0 for c in order}
    for r in not_found:
        if not r["antweb_taxon_name"]:
            nomatch[r["clade"]] += 1
    print(f"{'clade':28} {'listed':>6} {'no name':>7} {'w/ image':>8} {'images':>7}")
    for c in order:
        print(f"{c:28} {listed[c]:6} {nomatch[c]:7} {len(with_img[c]):8} {imgs[c]:7}")
    print(f"{'TOTAL':28} {len(clades):6} {sum(nomatch.values()):7} "
          f"{sum(len(v) for v in with_img.values()):8} {len(index):7}")
    return order


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--clades", default=DEFAULT_CLADES, help="Jack's CSV (major clade, species)")
    ap.add_argument("--out", default=f"/root/pheidole_export_{datetime.date.today():%Y-%m-%d}")
    ap.add_argument("--project", default="/root/antweb-next", help="AntWeb project folder (for etc/db.properties)")
    ap.add_argument("--mysql-container", default="antweb_mysql_1")
    ap.add_argument("--database", default="ant")
    ap.add_argument("--dry-run", action="store_true", help="query and print counts and the size estimate only")
    ap.add_argument("--max-per-species", type=int, default=0, help="at most N specimens per species (0 = all)")
    ap.add_argument("--workers", type=int, default=4, help="downloads at a time")
    ap.add_argument("--image-dir", default="", help="copy from this folder (<code>/<file>) when the file is there")
    ap.add_argument("--no-zip", action="store_true")
    args = ap.parse_args(argv)
    args.db_properties = os.path.join(args.project, "etc", "db.properties")

    clades = read_clades(args.clades)
    print(f"{args.clades}: {len(clades)} rows, {len({c for c, _ in clades})} clades", flush=True)
    taxa, heads, counts = load_data(args)
    print(f"database: {len(taxa)} Pheidole names, {len(heads)} Pheidole worker head images, "
          f"{sum(1 for h in heads if is_major(h))} of major workers", flush=True)
    index, not_found = build_plan(clades, taxa, heads, counts, args.max_per_species)
    order = summary(clades, index, not_found)
    near = sum(1 for r in index if r["match"].startswith("ignoring"))
    print(f"not_found.csv rows: {len(not_found)} ({sum(1 for r in not_found if not r['antweb_taxon_name'])} with no "
          f"AntWeb name, {sum(1 for r in not_found if r['close_names'])} of them with close names); "
          f"images matched ignoring punctuation: {near}")

    os.makedirs(args.out, exist_ok=True)
    write_csv(os.path.join(args.out, "index.csv"), INDEX_COLUMNS, index)
    write_csv(os.path.join(args.out, "not_found.csv"), NOT_FOUND_COLUMNS, not_found)
    print(f"wrote {args.out}/index.csv and not_found.csv")

    if args.dry_run:
        sample = [index[int(i * len(index) / 20)] for i in range(min(20, len(index)))]
        sizes = [s for s in (head_size(r["image_url"]) for r in sample) if s]
        if sizes:
            avg = sum(sizes) / len(sizes)
            est = avg * len(index) / 1e6
            print(f"size: {len(sizes)} of {len(sample)} sample images answered, average {avg / 1e3:.0f} kB; "
                  f"images about {est:.0f} MB, with contact sheets and zips about {4 * est:.0f} MB on disk")
        else:
            est = 0
            print("size: no sample image answered a HEAD request from this server (static.antweb.org not "
                  "reachable or blocked?); the full run will fail the same way. Check: curl -sI "
                  + (sample[0]["image_url"] if sample else IMAGE_BASE))
        free = shutil.disk_usage(args.out).free / 1e6
        print(f"free disk at {args.out}: {free:.0f} MB" + ("   NOT ENOUGH" if est and 4 * est > free * 0.9 else ""))
        print("dry run: nothing downloaded")
        return 0

    failures = download_all(index, args.out, args.workers, args.image_dir or None)
    if failures:
        write_csv(os.path.join(args.out, "download_failures.csv"), ["image_url", "file", "error"], failures)
        print(f"{len(failures)} images failed: {args.out}/download_failures.csv (run again to retry them)")

    today = f"{datetime.date.today():%Y-%m-%d}"
    zips = os.path.join(args.out, "zips")
    if not args.no_zip:
        os.makedirs(zips, exist_ok=True)
    for clade in order:
        rows = [r for r in index if r["clade"] == clade]
        if not rows:
            continue
        folder = os.path.join(args.out, safe(clade))
        os.makedirs(folder, exist_ok=True)
        write_csv(os.path.join(folder, "index.csv"), INDEX_COLUMNS, rows)
        tiles = [(os.path.join(folder, r["file_name"]), r["species"], r["specimen_code"]) for r in rows]
        pages = contact_sheet(os.path.join(folder, f"{safe(clade)}_contact_sheet.pdf"),
                              f"Pheidole clade {clade}: major workers, head view", tiles,
                              f"AntWeb (www.antweb.org), exported {today}. Photographers and specimen data: index.csv.")
        if not args.no_zip:
            zpath = os.path.join(zips, f"{safe(clade)}.zip")
            with zipfile.ZipFile(zpath + ".part", "w", zipfile.ZIP_STORED) as z:
                for name in sorted(os.listdir(folder)):
                    if not name.endswith(".part"):
                        z.write(os.path.join(folder, name), f"{safe(clade)}/{name}")
            os.replace(zpath + ".part", zpath)
        print(f"  {clade}: {len(rows)} images, contact sheet {pages} pages", flush=True)
    if not args.no_zip:
        for name in ("index.csv", "not_found.csv"):
            shutil.copyfile(os.path.join(args.out, name), os.path.join(zips, name))
        total = sum(os.path.getsize(os.path.join(zips, n)) for n in os.listdir(zips))
        print(f"zips: {zips} ({total / 1e6:.0f} MB, plus index.csv and not_found.csv)")
    print("done" + (f"; {len(failures)} images missing, see download_failures.csv" if failures else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
