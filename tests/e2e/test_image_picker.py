"""Image picker: the Pick and Unpick buttons on a species' images page (images.do).

Reported 2026-10-19: picking casent0852727 (Camponotus singularis) showed "Taxon not found for
family:null subfamily:null genus:false species:singularis". The first Pick button of the page was
inside the page's getComparison form; a browser drops a form inside a form, so that button
submitted getComparison.do (name=singularis, rank=species, genus=false) instead of the pick.
genus was "false" because Taxon.isGenus() (boolean) hid getGenus() from <bean:write>.

These tests only read pages (no pick is made). The curator part needs the staging test account
and never runs against production. The page is parsed the way a browser builds forms: a <form>
start tag inside an open form is ignored, and the buttons belong to the outer form.
"""
import os
from html.parser import HTMLParser
from urllib.parse import parse_qs, urljoin, urlparse

import pytest

from antweb import BASE_URL, IS_PRODUCTION, TIMEOUT, new_session, post_login

USER = os.environ.get("ANTWEB_TEST_USER")
PASSWORD = os.environ.get("ANTWEB_TEST_PASSWORD")

SPECIES_IMAGES = "/images.do?genus=camponotus&species=singularis&rank=species"
PICK_PARAMS = ("taxonName", "caste", "specimenCode", "command")


class FormParser(HTMLParser):
    """Forms, their fields and buttons, with the browser's rule for nested forms."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.forms = []          # {"id", "action", "method", "fields": [(name, value, attrs)], "buttons": [text]}
        self.nested = 0          # form start tags dropped because a form was open
        self.current = None
        self.button = None

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "form":
            if self.current is not None:
                self.nested += 1
                return
            self.current = {"id": a.get("id"), "action": a.get("action") or "", "method": (a.get("method") or "get").lower(),
                            "fields": [], "buttons": []}
            self.forms.append(self.current)
        elif tag == "input":
            self.fields_of(a).append((a.get("name"), a.get("value"), a))
        elif tag == "button":
            self.button = {"text": "", "form": self.owner(a)}

    def handle_data(self, data):
        if self.button is not None:
            self.button["text"] += data

    def handle_endtag(self, tag):
        if tag == "form":
            self.current = None
        elif tag == "button" and self.button is not None:
            if self.button["form"] is not None:
                self.button["form"]["buttons"].append(self.button["text"].strip())
            self.button = None

    def owner(self, attrs):
        if attrs.get("form"):
            return next((f for f in self.forms if f["id"] == attrs["form"]), None)
        return self.current

    def fields_of(self, attrs):
        f = self.owner(attrs)
        return f["fields"] if f is not None else []


def parse(html_text):
    p = FormParser()
    p.feed(html_text)
    return p


def check_comparison_form(p):
    for f in p.forms:
        for name, value, _ in f["fields"]:
            if name in ("genus", "subfamily", "species", "subspecies"):
                assert value not in ("false", "true"), f"form {f['id']} field {name}={value}"


def test_species_images_page_comparison_fields():
    """Anonymous: the getComparison form carries the genus name, not "false"."""
    r = new_session().get(BASE_URL + SPECIES_IMAGES, timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    p = parse(r.text)
    assert p.nested == 0, f"{p.nested} form(s) inside another form"
    check_comparison_form(p)
    comparison = [f for f in p.forms if f["id"] == "getComparison"]
    if comparison:
        genus = [v for n, v, _ in comparison[0]["fields"] if n == "genus"]
        assert genus == ["camponotus"], genus


@pytest.mark.skipif(not USER or not PASSWORD or IS_PRODUCTION,
                    reason="needs ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD, and never runs against production")
def test_pick_buttons_post_complete_requests():
    """Test curator (staging): every Pick/Unpick button posts to defaultSpecimen.do with all its parameters."""
    s = new_session()
    post_login(s, USER, PASSWORD)
    r = s.get(BASE_URL + SPECIES_IMAGES, timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    p = parse(r.text)
    assert p.nested == 0, f"{p.nested} form(s) inside another form: the first Pick button would submit the outer form"
    check_comparison_form(p)

    picks = [(f, b) for f in p.forms for b in f["buttons"] if b in ("Pick", "Unpick")]
    assert picks, "no Pick/Unpick buttons on the species images page (is the test account a curator?)"
    for f, label in picks:
        url = urlparse(urljoin(BASE_URL + "/", f["action"]))
        assert url.path.endswith("/defaultSpecimen.do"), f"{label} posts to {url.path}"
        assert f["method"] == "post", f"{label} uses {f['method']}"
        assert any(n == "_csrf" and v for n, v, _ in f["fields"]), f"{label} form has no token"
        q = {k: v[0] for k, v in parse_qs(url.query).items()}
        for k in PICK_PARAMS:
            assert q.get(k) and q[k] not in ("null", "false"), f"{label}: {k}={q.get(k)!r} in {url.query}"
        assert q["taxonName"] == "formicinaecamponotus singularis", q["taxonName"]
        assert q["command"] == ("post" if label == "Pick" else "unpost")
