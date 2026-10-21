"""The group form explains every field with examples (Brian Fisher, 2026-10-20).

Groups lacked short names because the form was impossible to understand. Static check of the page
source (any host): each field has its plain-language label, an explanation and examples, the short
name points to AntCat's list of institutions, and the broken second hidden "id" field is gone.
"""
import pathlib

FORM = pathlib.Path(__file__).resolve().parents[2] / "web" / "curate" / "viewGroup-body.jsp"


def test_group_form_explains_each_field():
    src = FORM.read_text()
    for label in ("Group number", "Full name", "Short name", "Group admin", "Curators of this group"):
        assert label in src, f"label {label!r} missing"
    assert src.count("Example") >= 4, "every field needs an example"
    assert "https://antcat.org/institutions" in src
    assert "< %= thisGroup.getId() % >" not in src, "broken hidden id field is back"
    assert "Html.escAttr(thisGroup.getName())" in src and "Html.escAttr(thisGroup.getAbbrev()" in src
