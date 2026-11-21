"""The countries whose adm1s are written in English: Country.ENGLISH_NAMED (the line on the country page, Java) equals
ENGLISH in tools/place/iso_states.py (the renames). Run: python3 tests/unit/test_adm1_names_note.py"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools", "place"))
import iso_states  # noqa: E402


def test_the_two_lists_are_the_same():
    java = open(os.path.join(ROOT, "src/org/calacademy/antweb/geolocale/Country.java"), encoding="utf-8").read()
    block = re.search(r"ENGLISH_NAMED = .*?\)\)\);", java, re.S).group(0)
    codes = re.findall(r'"([A-Z]{2})"', block)
    assert len(codes) == len(set(codes)) and set(codes) == set(iso_states.ENGLISH), sorted(set(codes) ^ set(iso_states.ENGLISH))


if __name__ == "__main__":
    test_the_two_lists_are_the_same()
    print("1 test passed")
