"""Content fingerprints of key pages (test tiers 2 and 5).

A fingerprint is what a person would notice if a page broke: its title, its
headings, how many images, specimen links, taxon links and table rows it
shows, and the visible text length. Recorded once (snapshots.json) and
compared on every run, so a framework upgrade that silently drops a section,
a table or the images shows up as a test failure.
"""
import re
from html.parser import HTMLParser

# Pages fingerprinted. Chosen to cover each page type a visitor uses.
KEY_PAGES = [
    "/",
    "/description.do?genus=camponotus&rank=genus",
    "/description.do?genus=nesomyrmex&species=clavipilis&rank=species",
    "/description.do?subfamily=formicinae&rank=subfamily",
    "/images.do?genus=camponotus&rank=genus",
    "/images.do?genus=dorylus&species=nigricans&rank=species",
    "/browse.do?subfamily=formicinae&rank=subfamily",
    "/browse.do?name=formicidae&rank=family&project=allantwebants",
    "/taxonomicPage.do?rank=subfamily&project=allantwebants",
    "/taxonomicPage.do?rank=genus&project=worldants",
    "/taxonomicPage.do?rank=species&countryName=Algeria",
    "/specimen.do?name=casent0103190",
    "/specimenImages.do?code=casent4045392",
    "/bigPicture.do?name=casent4045392&number=1&shot=h",
    "/collection.do?name=ANTC2113",
    "/locality.do?code=Ngerimel",
    "/country.do?name=Comoros",
    "/region.do?name=Africa",
    "/subregion.do?name=Polynesia",
    "/bioregion.do?name=Afrotropical",
    "/adm1.do?name=Antananarivo&country=Madagascar",
    "/island.do?name=Hawaii",
    "/project.do?name=fossilants",
    "/projects.do",
    "/museums.do",
    "/museum.do?museumCode=BMNH",
    "/curators.do",
    "/curator.do?id=79",
    "/groups.do?orderBy=name",
    "/group.do?id=12",
    "/artists.do?sortBy=name",
    "/endemic.do?geolocaleId=2",
    "/introduced.do?geolocaleId=494",
    "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=formicidae&subregion=Eastern Africa&country=Comoros&resultRank=specimen",
    "/statsPage.do",
    "/bioregions.do",
    "/countries.do",
]

# Numbers compared with a tolerance, because staging data can change a little.
COUNT_KEYS = ("images", "specimen_links", "taxon_links", "table_rows", "text_length")
TOLERANCE = 0.10


class _Parser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.title, self.headings, self.text = "", [], []
        self.counts = {"images": 0, "specimen_links": 0, "taxon_links": 0, "table_rows": 0}
        self._in, self._skip = None, 0

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag in ("script", "style"):
            self._skip += 1
        elif tag in ("title", "h1", "h2", "h3"):
            self._in, self._buf = tag, []
        elif tag == "img":
            self.counts["images"] += 1
        elif tag == "tr":
            self.counts["table_rows"] += 1
        elif tag == "a":
            href = a.get("href") or ""
            if "specimen.do" in href:
                self.counts["specimen_links"] += 1
            elif "description.do" in href or "browse.do" in href:
                self.counts["taxon_links"] += 1

    def handle_endtag(self, tag):
        if tag in ("script", "style") and self._skip:
            self._skip -= 1
        elif tag == self._in:
            txt = re.sub(r"\s+", " ", "".join(self._buf)).strip()
            if tag == "title":
                self.title = txt
            elif txt:
                self.headings.append(f"{tag}:{txt[:80]}")
            self._in = None

    def handle_data(self, data):
        if self._skip:
            return
        if self._in:
            self._buf.append(data)
        self.text.append(data)


def fingerprint(html: str) -> dict:
    p = _Parser()
    p.feed(html)
    text = re.sub(r"\s+", " ", "".join(p.text)).strip()
    return {"title": p.title, "headings": p.headings[:25], **p.counts, "text_length": len(text)}


def differences(recorded: dict, current: dict) -> list[str]:
    out = []
    if recorded["title"] != current["title"]:
        out.append(f"title {recorded['title']!r} -> {current['title']!r}")
    missing = [h for h in recorded["headings"] if h not in current["headings"]]
    if missing:
        out.append(f"headings missing: {missing[:5]}")
    for k in COUNT_KEYS:
        r, c = recorded[k], current[k]
        if r == c:
            continue
        if r == 0 or c == 0 or abs(c - r) / max(r, 1) > TOLERANCE:
            out.append(f"{k} {r} -> {c}")
    return out
