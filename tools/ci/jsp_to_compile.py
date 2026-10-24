#!/usr/bin/env python3
"""Which JSP pages to compile for a list of changed ones (the CI step "Every JSP page changed ... compiles").

A page included with <%@ include file="..." %> is compiled inside the page that includes it and uses that page's
variables (taxonReport.jsp uses childrenList of showBrowse-body.jsp), so it cannot be compiled alone: JspC reports
"childrenList cannot be resolved" (2026-10-24). For such a page the pages that include it are compiled instead, up to
the pages that are not included by another one. Pages included with <jsp:include> are their own servlet and are
compiled themselves. At most MAX pages per changed file (antweb-defs.jsp is included by hundreds).

Usage: jsp_to_compile.py [--web DIR] FILE...   (FILE relative to web/, as git diff gives it without "web/")
Prints the pages to compile, one per line, relative to web/.
"""
import argparse
import os
import posixpath
import re
import sys

MAX = 25
STATIC = re.compile(r'<%@\s*include\s+file\s*=\s*"([^"]+)"')


def static_includers(web):
    """{included page: set of pages that include it statically}, all paths relative to web/."""
    out = {}
    for root, _, files in os.walk(web):
        for name in files:
            if not name.endswith(".jsp"):
                continue
            path = os.path.join(root, name)
            rel = os.path.relpath(path, web).replace(os.sep, "/")
            try:
                text = open(path, encoding="utf-8", errors="replace").read()
            except OSError:
                continue
            for target in STATIC.findall(text):
                if target.startswith("/"):
                    inc = target.lstrip("/")
                else:
                    inc = posixpath.normpath(posixpath.join(posixpath.dirname(rel), target))
                out.setdefault(inc, set()).add(rel)
    return out


def pages_to_compile(changed, includers, limit=MAX):
    result = []
    for f in changed:
        tops, seen, todo = [], {f}, [f]
        while todo:
            page = todo.pop(0)
            parents = sorted(includers.get(page, ()))
            if not parents:
                tops.append(page)
                continue
            for p in parents:
                if p not in seen:
                    seen.add(p)
                    todo.append(p)
        for page in sorted(tops)[:limit]:
            if page not in result:
                result.append(page)
    return result


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument("--web", default=os.path.join(os.path.dirname(__file__), "..", "..", "web"))
    ap.add_argument("files", nargs="*")
    a = ap.parse_args(argv)
    for page in pages_to_compile(a.files, static_includers(a.web)):
        print(page)


if __name__ == "__main__":
    main(sys.argv[1:])
