#!/usr/bin/env python3
"""The notes under every taxon list are one block in the project lead's wording (Brian Fisher, 2026-10-28).

<div id="list-notes"><b>Notes on specimen and image counts</b>, then numbered notes:
  1. (administrators only) Specimen total / Genera total / Species total, id="subtaxon-total" with data-subtaxon-total;
  2. the asterisk note (what a count marked * includes);
  3. the Images note, id="images-definition" (one number, label images not counted, the place's images first).
The Port of Entry list keeps its own note. The "identified only to genus" line of the counting rules is on another
branch and is not added here.

Run: python3 tests/unit/test_list_notes.py   (also in .github/workflows/tests.yaml)
"""
import os
import sys

JSP = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "web", "taxonReportBody.jsp")


def main():
    jsp = open(JSP, encoding="utf-8").read()
    block = jsp[jsp.index('<div id="list-notes">'):]
    block = block[:block.index("</div>")]

    # the heading and the order of the three notes
    assert block.startswith('<div id="list-notes"><b>Notes on specimen and image counts</b>'), block[:80]
    i_total = block.index('id="subtaxon-total" data-subtaxon-total="<%= subtaxonTotal %>"')
    i_star = block.index("<%= asterixNote %>")
    i_images = block.index('id="images-definition"')
    assert i_total < i_star < i_images, "the notes are out of order"
    assert block.count("<%= ++noteNum %>. ") == 3, "the notes are numbered"
    assert "<% if (totalNote != null) { %>" in block, "the total is for administrators only"
    assert "String totalNote = null;\n   if (LoginMgr.isAdmin(request)) {" in jsp

    # 1. the total, in plain words, with the right units per rank
    assert '"Specimen total (administrators only): the " + org.calacademy.antweb.util.Html.esc(totalColumn)' in jsp
    assert '+ " column adds up to " + Formatter.commaFormat(subtaxonTotal) + " specimens. This can be lower than Specimens on "' in jsp
    assert ('+ locale + "\'s overview page, which also counts specimens not on this list, such as those identified only to"\n'
            '           + " subfamily or family."') in jsp
    assert '"Genera total (administrators only): the Genera column adds up to "' in jsp
    assert '+ " genera; it should equal Genera on " + locale + "\'s overview page."' in jsp
    assert '"Species total (administrators only): the Species column adds up to "' in jsp
    assert '+ " species and subspecies; it should equal Species / subspecies on " + locale + "\'s overview page."' in jsp

    # 2. the asterisk note
    assert '"Specimen"' in jsp and '"specimen filed under that name"' in jsp
    assert '"genus under the row"' in jsp and '"subfamily under the row"' in jsp and 'nextPluralRank + " name under the row"' in jsp
    assert ('String asterixNote = asterixCounts + " counts marked with an asterisk (*) include every " + asterixUnit\n'
            '        + ", whatever its taxonomic status (valid species, morphospecies, indetermined, unidentifiable or unrecognized names)"\n'
            '        + (displayGlobal ? "." : ", in " + locale + ".");') in jsp

    # 3. the Images note: one number, no sentence about parentheses
    assert ('"Images: the images of the ant (head, profile, dorsal and other views) of that name in all of"\n'
            '        + " AntWeb, from specimens collected anywhere. Specimen label images are not counted."\n'
            '        + (imagesFromPhrase == null ? "" : " Click the number to see the images; those " + imagesFromPhrase + " come first.");') in jsp
    assert "org.calacademy.antweb.util.Html.esc(imagesDefinition)" in block
    assert "parenthes" not in jsp.lower() and "in brackets" not in jsp

    # the old notes are gone; the Port of Entry note stays
    for old in ("Total of the ", "Numbers marked * count every ", "<BR>* <%= asterixNote %>",
                "String imagesDefinition = org.calacademy.antweb.ImageCounts.DEFINITION"):
        assert old not in jsp, "old note still there: " + old
    assert '<span id="port-of-entry-list-note">These are ant species intercepted at ports of entry' in jsp
    print("List notes: one block, heading and three notes in the 2026-10-28 wording.")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, ValueError) as e:
        print("FAIL:", e)
        sys.exit(1)
