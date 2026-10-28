#!/usr/bin/env python3
"""tools/export/pheidole_major_heads.py (the one-off Pheidole major-worker head export for Jack Longino), no database.

- Jack's names match AntWeb taxon names case-insensitively ("Pheidole JTL202" -> myrmicinaepheidole jtl202),
  trinomials included; punctuation-only differences match and are marked; other close names are reported;
- file names are <clade>_<Genus_species>_<code>.jpg (_2 for a second head shot); image URLs use the _med size;
- mysql --batch output is unescaped;
- a whole run with a fake database and a local image folder writes index.csv, not_found.csv (with reasons),
  one folder, contact sheet PDF and zip per clade; a second run keeps the files already there.

Run: python3 tests/unit/test_pheidole_export.py
"""
import csv
import importlib.util
import io
import os
import sys
import tempfile
import zipfile
from contextlib import redirect_stdout

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
spec = importlib.util.spec_from_file_location(
    "pheidole_major_heads", os.path.join(ROOT, "tools", "export", "pheidole_major_heads.py"))
px = importlib.util.module_from_spec(spec)
spec.loader.exec_module(px)

KNOWN = {"myrmicinaepheidole albipes", "myrmicinaepheidole jtl202", "myrmicinaepheidole impressa detrita",
         "myrmicinaepheidole jtl-271", "myrmicinaepheidole megacephala", "myrmicinaepheidole sp_epem093"}


def fake_jpeg(w=40, h=30):
    """Enough of a JPEG for the size reader and the PDF writer: SOI, an APP0, SOF0, EOI."""
    app0 = b"\xff\xe0\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00"
    sof = b"\xff\xc0\x00\x11\x08" + h.to_bytes(2, "big") + w.to_bytes(2, "big") + b"\x03" + b"\x01\x22\x00" * 3
    return b"\xff\xd8" + app0 + sof + b"\x00" * 200 + b"\xff\xd9"


def test_match():
    assert px.taxon_key("Pheidole albipes") == "myrmicinaepheidole albipes"
    assert px.match_name("Pheidole albipes", KNOWN) == ("myrmicinaepheidole albipes", "exact", [])
    assert px.match_name("Pheidole Albipes", KNOWN)[0] == "myrmicinaepheidole albipes"
    assert px.match_name("Pheidole JTL202", KNOWN) == ("myrmicinaepheidole jtl202", "exact", [])
    assert px.match_name("Pheidole impressa detrita", KNOWN)[0] == "myrmicinaepheidole impressa detrita"
    assert px.match_name("Pheidole sp_EPEM093", KNOWN)[0] == "myrmicinaepheidole sp_epem093"
    name, how, _ = px.match_name("Pheidole JTL271", KNOWN)
    assert (name, how) == ("myrmicinaepheidole jtl-271", "ignoring punctuation")
    name, how, close = px.match_name("Pheidole megacefala", KNOWN)
    assert name is None and how == "none" and close == ["myrmicinaepheidole megacephala"]
    assert px.match_name("Pheidole nosuchname", KNOWN) == (None, "none", [])
    assert px.match_name("Camponotus albipes", KNOWN)[1] == "not Pheidole"


def test_names_and_urls():
    assert px.file_name("bicarinata", "Pheidole albipes", "casent0005792") == \
        "bicarinata_Pheidole_albipes_casent0005792.jpg"
    assert px.file_name("megacephala_Malagasy", "Pheidole JTL202", "jtl-ant-01", "2") == \
        "megacephala_Malagasy_Pheidole_JTL202_jtl-ant-01_2.jpg"
    assert px.file_name("c", "Pheidole a/b (x)", "x y") == "c_Pheidole_a_b_x_x_y.jpg"
    assert px.image_url("casent0005792", "1") == \
        "https://static.antweb.org/images/casent0005792/casent0005792_h_1_med.jpg"
    assert px.unescape_mysql("NULL") == ""
    assert px.unescape_mysql(r"a\tb\\c\nd") == "a\tb\\c\nd"
    assert px.jpeg_info(fake_jpeg(40, 30)) == (40, 30, 3)
    assert px.jpeg_info(b"<html>") is None


def test_read_clades():
    with tempfile.TemporaryDirectory() as d:
        p = os.path.join(d, "c.csv")
        with open(p, "w", newline="") as f:
            f.write("major clade,species\r\nbicarinata,Pheidole  albipes\r\nbicarinata,Pheidole albipes\r\n,\r\n")
        assert px.read_clades(p) == [("bicarinata", "Pheidole albipes")]
    rows = px.read_clades(px.DEFAULT_CLADES)
    assert len(rows) == 1517 and len({c for c, _ in rows}) == 41, (len(rows), len({c for c, _ in rows}))


def fake_mysql(sql, ncols, args):
    if sql == px.TAXA_SQL:
        return [["myrmicinaepheidole albipes", "valid", ""], ["myrmicinaepheidole jtl202", "morphotaxon", ""],
                ["myrmicinaepheidole oldname", "synonym", "myrmicinaepheidole albipes"],
                ["myrmicinaepheidole minoronly", "valid", ""]]
    if sql == px.HEADS_SQL:
        def row(code, taxon, sub, life="", orig="", shot="1"):
            return [code, taxon, orig, "worker", sub, life, "Costa Rica", "Heredia", "JTL-001", "La Selva", shot,
                    "Jack Longino"]
        return [row("casent0000001", "myrmicinaepheidole albipes", "major/soldier", "1s"),
                row("casent0000001", "myrmicinaepheidole albipes", "major/soldier", "1s", shot="2"),
                row("casent0000002", "myrmicinaepheidole albipes", "normal", "1w"),
                row("jtlc000003", "myrmicinaepheidole albipes", "major/soldier", "major",
                    orig="myrmicinaepheidole jtl202"),
                row("casent0000004", "myrmicinaepheidole minoronly", "normal", "1w, 1 major")]
    if sql == px.COUNTS_SQL:
        return [["myrmicinaepheidole albipes", "3", "4", "5"], ["myrmicinaepheidole minoronly", "0", "1", "1"]]
    raise AssertionError(sql)


def test_full_run():
    px.mysql = fake_mysql
    with tempfile.TemporaryDirectory() as d:
        clades = os.path.join(d, "clades.csv")
        with open(clades, "w", newline="") as f:
            f.write("major clade,species\nbicarinata,Pheidole albipes\nbicarinata,Pheidole JTL202\n"
                    "bicarinata,Pheidole oldname\ndiligens,Pheidole minoronly\ndiligens,Pheidole albipez\n")
        imgs = os.path.join(d, "images")
        for code, shot in (("casent0000001", 1), ("casent0000001", 2), ("jtlc000003", 1)):
            os.makedirs(os.path.join(imgs, code), exist_ok=True)
            with open(os.path.join(imgs, code, f"{code}_h_{shot}_med.jpg"), "wb") as f:
                f.write(fake_jpeg())
        out = os.path.join(d, "out")
        argv = ["--clades", clades, "--out", out, "--project", d, "--image-dir", imgs]
        with redirect_stdout(io.StringIO()) as log:
            assert px.main(argv) == 0
        text = log.getvalue()
        assert "TOTAL" in text and "done" in text, text

        index = list(csv.DictReader(open(os.path.join(out, "index.csv"))))
        assert [r["file_name"] for r in index] == [
            "bicarinata_Pheidole_albipes_casent0000001.jpg", "bicarinata_Pheidole_albipes_casent0000001_2.jpg",
            "bicarinata_Pheidole_albipes_jtlc000003.jpg", "bicarinata_Pheidole_JTL202_jtlc000003.jpg"]
        assert index[0]["photographer"] == "Jack Longino" and index[0]["country"] == "Costa Rica"
        assert index[0]["specimen_url"] == "https://www.antweb.org/specimen.do?code=casent0000001"
        assert "now filed under myrmicinaepheidole albipes" in index[3]["match"]

        nf = {r["species"]: r for r in csv.DictReader(open(os.path.join(out, "not_found.csv")))}
        assert set(nf) == {"Pheidole oldname", "Pheidole minoronly", "Pheidole albipez"}
        assert "synonym" in nf["Pheidole oldname"]["reason"] and "albipes" in nf["Pheidole oldname"]["reason"]
        assert "no major-worker specimens" in nf["Pheidole minoronly"]["reason"]
        assert "'major' or 'soldier' in the life stage" in nf["Pheidole minoronly"]["reason"]
        assert nf["Pheidole albipez"]["reason"] == "no AntWeb taxon with this name"
        assert nf["Pheidole albipez"]["close_names"] == "myrmicinaepheidole albipes"

        pdf = open(os.path.join(out, "bicarinata", "bicarinata_contact_sheet.pdf"), "rb").read()
        assert pdf.startswith(b"%PDF-1.4") and pdf.rstrip().endswith(b"%%EOF")
        assert pdf.count(b"/Subtype /Image") == 4 and b"(page 1 of 1)" in pdf and b"(casent0000001)" in pdf
        z = zipfile.ZipFile(os.path.join(out, "zips", "bicarinata.zip"))
        assert sorted(z.namelist()) == sorted(["bicarinata/" + r["file_name"] for r in index]
                                              + ["bicarinata/index.csv", "bicarinata/bicarinata_contact_sheet.pdf"])
        assert not os.path.exists(os.path.join(out, "zips", "diligens.zip"))
        assert os.path.exists(os.path.join(out, "zips", "not_found.csv"))

        # Second run: nothing to fetch (the image folder is gone, the files on disk are kept).
        import shutil
        shutil.rmtree(imgs)
        with redirect_stdout(io.StringIO()) as log:
            assert px.main(argv) == 0
        assert "already on disk: 4, to fetch: 0" in log.getvalue(), log.getvalue()


def test_pages():
    with tempfile.TemporaryDirectory() as d:
        jpg = os.path.join(d, "a.jpg")
        with open(jpg, "wb") as f:
            f.write(fake_jpeg())
        tiles = [(jpg, "Pheidole (x)", f"code{i}") for i in range(30)] + [(os.path.join(d, "missing.jpg"), "a", "b")]
        assert px.contact_sheet(os.path.join(d, "s.pdf"), "t", tiles) == 2
        pdf = open(os.path.join(d, "s.pdf"), "rb").read()
        assert b"/Count 2" in pdf and b"(page 2 of 2)" in pdf and b"(image missing)" in pdf
        assert b"(Pheidole \\(x\\))" in pdf


if __name__ == "__main__":
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    for t in tests:
        t()
        print("ok", t.__name__)
    print(f"{len(tests)} tests passed")
