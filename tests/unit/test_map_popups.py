"""Map marker popups: titles shown decoded, links encoded once (no server needed).

Bug of 2026-10-22: on group.do?id=80 the map popups showed "West+Bengal%2C+India" and linked
locality.do?code=loc22.8%252C88.3 ("Locality not found"). Map.java sends locality names and codes
form-encoded (and the maps stored in object_map hold them so); drawMap.js showed them as they came
and encoded the code a second time. Collection maps linked locality.do with a collection code.

This runs the real web/maps/sdk/drawMap.js and web/maps/drawGoogleMap.js under Node
(tests/unit/map_popups_harness.js, a stand-in for Leaflet) with map function calls as Map.java
writes them, and checks every popup:
  - no "%25" followed by two hex digits in a link (double encoding);
  - the title is decoded and HTML-escaped;
  - locality popups link locality.do?code=<code encoded once>, collection popups collection.do?name=,
    specimen popups specimen.do?name=;
  - a missing code ('' or the 'null' that older stored maps hold) gives no link.

Run: python3 tests/unit/test_map_popups.py   (also in .github/workflows/tests.yaml; needs node)
"""
import html
import json
import os
import re
import shutil
import subprocess
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
HARNESS = os.path.join(ROOT, "tests", "unit", "map_popups_harness.js")
DOUBLE_ENCODED = re.compile(r"%25[0-9A-Fa-f]{2}")
DOMAIN = "'https://www.antweb.org/'"


def run(calls):
    node = shutil.which("node")
    assert node, "node is needed (it is on the GitHub runners)"
    with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as f:
        f.write("\n".join(calls) + "\n")
        name = f.name
    try:
        out = subprocess.run([node, HARNESS, ROOT, name], capture_output=True, text=True, timeout=60)
    finally:
        os.unlink(name)
    assert out.returncode == 0, out.stderr
    return json.loads(out.stdout)


def links(popup):
    return [html.unescape(h) for h in re.findall(r'href="([^"]*)"', popup)]


def title(popup):
    m = re.search(r"<b>(.*?)</b>", popup)
    return html.unescape(m.group(1)) if m else None


def test_group_map_popup():
    # As stored for group 80 (International Pest Ant Registry) and read back by ObjectMapDb.getMap().
    call = ("drawGoogleMapLocalities('small', 'map1',new Array(22.8,-1.5),new Array(88.3,36.8),"
            "new Array('West+Bengal%2C+India','Nairobi'),new Array('loc22.8%2C88.3','KE%23N%C3%A9'), " + DOMAIN + ");")
    (popups,) = run([call])
    assert len(popups) == 2, popups
    assert title(popups[0]) == "West Bengal, India", popups[0]
    assert links(popups[0]) == ["/locality.do?code=loc22.8%2C88.3"], popups[0]
    assert links(popups[1]) == ["/locality.do?code=KE%23N%C3%A9"], popups[1]
    for p in popups:
        for link in links(p):
            assert not DOUBLE_ENCODED.search(link), link
    print("Group (and country, adm1, museum, search) map popups: decoded title, link encoded once.")


def test_locality_and_collection_popups():
    locality = ("drawGoogleMapLocality('small', 'map2',-18.9,47.5,new Array('Ambohidahy+%3Cb%3E'),"
                "new Array('BLF1234+a'), " + DOMAIN + ");")
    collection = "drawGoogleMapCollection('small', 'map3',-18.9,47.5,new Array('BLF%2F01'), " + DOMAIN + ");"
    loc, col = run([locality, collection])
    assert title(loc[0]) == "Ambohidahy <b>", loc[0]           # shown as text, not markup
    assert "<b>Ambohidahy <b>" not in loc[0], loc[0]
    assert links(loc[0]) == ["/locality.do?code=BLF1234%20a"], loc[0]
    assert links(col[0]) == ["/collection.do?name=BLF%2F01"], col[0]
    print("Locality and collection map popups link their own pages.")


def test_missing_code_has_no_link():
    calls = [
        "drawGoogleMapLocalities('small', 'map4',new Array(1.5,2.5),new Array(3.5,4.5),"
        "new Array('null','Somewhere'),new Array('null',''), " + DOMAIN + ");",
    ]
    (popups,) = run(calls)
    assert all(not links(p) for p in popups), popups
    assert title(popups[1]) == "Somewhere", popups[1]
    print("A locality without a code gets no link (was locality.do?code=null).")


def test_specimen_popups_unchanged():
    call = ("drawGoogleMapSpecimens('small', 'map5',new Array(1.5,2.5),new Array(3.5,4.5),"
            "new Array('Camponotus maculatus','Pheidole megacephala'),new Array('casent0103190','casent0922067'),"
            "new Array('https://static.antweb.org/images/x.jpg',''), " + DOMAIN + ");")
    (popups,) = run([call])
    assert links(popups[0]) == ["/specimen.do?name=casent0103190"], popups[0]
    assert title(popups[0]) == "Camponotus maculatus"
    assert 'src="https://static.antweb.org/images/x.jpg"' in popups[0]
    assert "<img" not in popups[1]
    print("Specimen map popups link specimen.do once.")


def test_java_sends_encoded_values():
    """Map.java writes locality names/codes and collection codes form-encoded, through jsString."""
    src = open(os.path.join(ROOT, "src/org/calacademy/antweb/Map.java"), encoding="utf-8").read()
    assert "jsString(formEncode(localityName))" in src and "jsString(formEncode(localityCode))" in src
    assert "jsString(formEncode(collectionCode))" in src
    assert "theArrayString.append(jsString(value))" in src
    assert "setLocalityCode(HttpUtil.encode(localityCode))" in src  # the encoding drawMap.js decodes
    print("Map.java sends the values drawMap.js decodes.")


def main():
    test_group_map_popup()
    test_locality_and_collection_popups()
    test_missing_code_has_no_link()
    test_specimen_popups_unchanged()
    test_java_sends_encoded_values()


if __name__ == "__main__":
    main()
