"""The group form explains every field with examples (Brian Fisher, 2026-10-20).

Groups lacked short names because the form was impossible to understand. Static check of the page
source (any host): each field has its plain-language label, an explanation and examples, the short
name points to AntCat's list of institutions, and the broken second hidden "id" field is gone.
"""
import pathlib

FORM = pathlib.Path(__file__).resolve().parents[2] / "web" / "curate" / "viewGroup-body.jsp"


def test_group_form_explains_each_field():
    src = FORM.read_text()
    # "Upload group" since 2026-10-20 (Brian: "group" alone means little on the Curate page).
    for label in ("Upload group number", "Full name", "Short name", "Admin curator", "Logins in this upload group"):
        assert label in src, f"label {label!r} missing"
    assert src.count("Example") >= 4, "every field needs an example"
    assert "https://antcat.org/institutions" in src
    assert "< %= thisGroup.getId() % >" not in src, "broken hidden id field is back"
    assert "Html.escAttr(thisGroup.getName())" in src and "Html.escAttr(thisGroup.getAbbrev()" in src


def test_group_number_cannot_be_edited():
    """Typing another number into the old text box saved this form over that other group (fixed 2026-10-20)."""
    src = FORM.read_text()
    assert '<input type="hidden" name="id" value="<%= thisGroup.getId() %>">' in src
    assert '<input type="text" class="input_200" name="id"' not in src
    assert "addUploader.do" in src, "the form points to Add an uploader"
