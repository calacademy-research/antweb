#!/usr/bin/env python3
"""tools/ci/jsp_to_compile.py: a statically included JSP is compiled through the pages that include it."""
import os
import sys
import tempfile

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "tools", "ci"))
import jsp_to_compile as j  # noqa: E402


def write(root, rel, text):
    p = os.path.join(root, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, "w").write(text)


def test():
    with tempfile.TemporaryDirectory() as web:
        write(web, "showBrowse.jsp", '<jsp:include page="showBrowse-body.jsp" />')
        write(web, "showBrowse-body.jsp", '<% int childrenList = 1; %>\n<%@include file="/taxonReport.jsp" %>')
        write(web, "taxonReport.jsp", '<%= childrenList %>')
        write(web, "curate/a.jsp", '<%@ include file="../common/defs.jsp" %>')
        write(web, "curate/b.jsp", '<%@ include file="/common/defs.jsp" %>')
        write(web, "common/defs.jsp", '<% %>')
        write(web, "alone.jsp", 'x')
        inc = j.static_includers(web)
        assert inc["taxonReport.jsp"] == {"showBrowse-body.jsp"}, inc
        assert inc["common/defs.jsp"] == {"curate/a.jsp", "curate/b.jsp"}, inc
        # the fragment is compiled through showBrowse-body.jsp (its own servlet via jsp:include)
        assert j.pages_to_compile(["taxonReport.jsp"], inc) == ["showBrowse-body.jsp"]
        assert j.pages_to_compile(["alone.jsp", "showBrowse-body.jsp"], inc) == ["alone.jsp", "showBrowse-body.jsp"]
        assert j.pages_to_compile(["common/defs.jsp"], inc) == ["curate/a.jsp", "curate/b.jsp"]
        assert j.pages_to_compile(["common/defs.jsp"], inc, limit=1) == ["curate/a.jsp"]
    # the real site: taxonReport.jsp (2026-10-24 false failure) goes through showBrowse-body.jsp
    real = j.static_includers(os.path.join(os.path.dirname(__file__), "..", "..", "web"))
    assert "showBrowse-body.jsp" in j.pages_to_compile(["taxonReport.jsp"], real)
    print("OK: jsp_to_compile")


if __name__ == "__main__":
    test()
