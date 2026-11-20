#!/usr/bin/env python3
"""Builds iso3166_2.tsv, the ISO 3166-2 subdivisions iso_states.py names AntWeb's states by (Brian Fisher, 2026-11-20:
"let's adopt ISO 3166-2"; review the states about once a year).

Source: pycountry (the Debian iso-codes data of ISO 3166-2), its translations of the names into languages written in
Latin script, and the ADM1 shape names of geoBoundaries (each shape carries its ISO code, shapeISO), so an English or
local name AntWeb uses can be matched to the subdivision. Run where pycountry is installed and the geoBoundaries ADM1
files are on disk (not on the servers):

  pip install pycountry
  python3 build_iso3166_2.py --geoboundaries DIR > iso3166_2.tsv      DIR holds <ISO3>-ADM1.geojson files
  python3 build_iso3166_2.py --countries > iso3166_countries.tsv     the countries (alpha-2, names) for matching

Columns: code, country (ISO alpha-2), type, parent code, the ISO name, other names ( | between them). A first line
starting with # says which pycountry version and geoBoundaries commit it came from."""
import argparse, gettext, glob, json, os, re, unicodedata

LANGS = ["en", "fr", "de", "es", "pt", "it", "nl", "pl", "cs", "sv", "da", "nb", "fi", "tr", "id", "ms", "vi", "ro", "hu",
         "hr", "sl", "sk", "lt", "lv", "et", "ca", "eu", "gl"]


def latin(text):
    return all(not ch.isalpha() or "LATIN" in unicodedata.name(ch, "") for ch in text)


def main():
    import pycountry
    a = argparse.ArgumentParser()
    a.add_argument("--geoboundaries")
    a.add_argument("--countries", action="store_true")
    a.add_argument("--commit", default="8ce2700661a28ee944350e2b329bbabb58d2ff83")
    args = a.parse_args()
    if args.countries:
        print("# ISO 3166-1 countries from pycountry: alpha-2, name, official name, common name")
        for c in sorted(pycountry.countries, key=lambda c: c.alpha_2):
            print("\t".join([c.alpha_2, c.name, getattr(c, "official_name", "") or "", getattr(c, "common_name", "") or ""]))
        return
    trans = []
    for lang in LANGS:
        try:
            trans.append(gettext.translation("iso3166-2", pycountry.LOCALES_DIR, languages=[lang]))
        except OSError:
            pass
    shapes = {}
    for f in glob.glob(os.path.join(args.geoboundaries, "*-ADM1.geojson")):
        try:
            for ft in json.load(open(f, encoding="utf-8"))["features"]:
                p = ft["properties"]
                if p.get("shapeISO") and p.get("shapeName"):
                    shapes.setdefault(p["shapeISO"].strip().upper(), set()).add(p["shapeName"].strip())
        except (OSError, ValueError, KeyError):
            pass
    import importlib.metadata
    print("# ISO 3166-2 from pycountry %s (iso-codes); other names: its translations in Latin script and geoBoundaries ADM1 "
          "shape names (commit %s)" % (importlib.metadata.version("pycountry"), args.commit))
    for s in sorted(pycountry.subdivisions, key=lambda s: s.code):
        others = set(t.gettext(s.name) for t in trans) | shapes.get(s.code.upper(), set())
        others |= {x.strip() for x in re.findall(r"\(([^()]*)\)", s.name)}        # "Calabarzon (Region IV-A)"
        others.add(re.sub(r"\s*\([^()]*\)", "", s.name).strip())
        others |= {x.strip() for x in re.findall(r"\[(.*?)\]", s.name)}
        others = sorted(o for o in others if o and o != s.name and latin(o) and "\t" not in o and "|" not in o)
        print("\t".join([s.code, s.country_code, s.type, s.parent_code or "", s.name, " | ".join(others)]))


if __name__ == "__main__":
    main()
