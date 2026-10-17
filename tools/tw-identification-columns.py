#!/usr/bin/env python3
"""Read-only: report the identification-related columns of a TaxonWorks Darwin Core archive.

Usage on antweb-prod (downloads the archive from the link in etc/taxonworks.properties, reads it,
deletes it):  python3 tw-identification-columns.py /root/antweb/etc/taxonworks.properties
or with a local zip:  python3 tw-identification-columns.py /path/to/archive.zip
Prints column names, how many rows fill each identification column, and a few examples.
"""
import collections, csv, io, os, sys, tempfile, urllib.request, zipfile

arg = sys.argv[1]
tmp = None
if arg.endswith(".properties"):
    url = next(l.split("=", 1)[1].strip() for l in open(arg) if l.startswith("url="))
    tmp = tempfile.NamedTemporaryFile(suffix=".zip", delete=False).name
    print("downloading the TaxonWorks archive ...", flush=True)
    urllib.request.urlretrieve(url, tmp)
    arg = tmp
try:
    z = zipfile.ZipFile(arg)
    print("files in archive:", z.namelist())
    name = next(n for n in z.namelist() if n.lower().startswith("occurrence") or n.lower().endswith((".tsv", ".txt", ".csv")))
    delim = "," if name.lower().endswith(".csv") else "\t"
    rows = csv.reader(io.TextIOWrapper(z.open(name), encoding="utf-8", errors="replace"), delimiter=delim)
    head = next(rows)
    print("data file:", name, "| columns:", len(head))
    keys = ("ident", "determin", "previous", "typestatus")
    want = [c for c in head if any(k in c.lower() for k in keys)]
    print("identification-related columns:", want)
    idx = {c: head.index(c) for c in want}
    filled, samples, n = collections.Counter(), collections.defaultdict(list), 0
    for row in rows:
        n += 1
        for c, i in idx.items():
            v = row[i].strip() if i < len(row) else ""
            if v:
                filled[c] += 1
                if len(samples[c]) < 5:
                    samples[c].append(v[:200])
    print("rows:", n)
    for c in want:
        print(f"\n{c}: filled in {filled[c]} rows")
        for s in samples[c]:
            print("    ", s)
finally:
    if tmp and os.path.exists(tmp):
        os.remove(tmp)
