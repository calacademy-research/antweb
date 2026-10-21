#!/usr/bin/env python3
"""Lists AntWeb's database tables and their columns as the SQL in this repository defines them.

The repository holds no full schema dump. The tables are defined by
  1. db/upgrade/<version>/*.sql, applied in version order (3.0.7 ... 8.114), then file name order:
     CREATE TABLE, CREATE TABLE ... LIKE / AS SELECT, ALTER TABLE (ADD, DROP, CHANGE, MODIFY, RENAME),
     RENAME TABLE, DROP TABLE;
  2. CREATE TABLE statements in the Java code (src/**/*.java), for tables the application creates itself
     (string literals joined with +; a constant such as TABLE is resolved in the same file; a name made
     at run time, such as a dated backup table, is shown with * for the run-time part);
  3. tables the code and the upgrade scripts use but that no file in the repository creates (they predate
     the upgrade scripts, about 2010): known from ALTER TABLE statements, and their columns also from the test
     schemas in test/ and tests/ (which hold only the columns the tests need, so those lists are partial).
Views (CREATE VIEW, DROP VIEW) are listed separately. The upgrade files mix SQL with notes and pasted output; see
sql_statements() for how statements are split, and NOT_RUN / RECORD_ONLY for statements the files say were not run.

The result is the schema as the repository describes it. The live database can differ (an upgrade file not run,
a table made by hand on the server, a column changed by hand; on 2026-10-21 museum.specimen_subfamily_dist_json
was found smaller on staging than db/upgrade/6.2 says). --compare shows the differences.

Usage:
  python3 tools/datamodel/list_tables.py              table list with columns (text)
  python3 tools/datamodel/list_tables.py --json       the same as JSON
  python3 tools/datamodel/list_tables.py --compare live.tsv
                                                      differences between the repository's schema and the live
                                                      one; live.tsv is the tab-separated output (mysql -B -N)
                                                      of this read-only query (the full command, which keeps the
                                                      password off the command line, is in docs/data-model.md):
      SELECT c.table_name, c.column_name, c.column_type, t.table_type FROM information_schema.columns c
        JOIN information_schema.tables t USING (table_schema, table_name)
       WHERE c.table_schema = DATABASE() ORDER BY c.table_name, c.ordinal_position
  python3 tools/datamodel/list_tables.py --check      fail (exit 1) when a table has no section in
                                                      docs/data-model.md, or a section names a table the
                                                      repository does not know (CI, tests.yaml)
Standard library only (Python 3.9 or later).
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOC = ROOT / "docs" / "data-model.md"

# Section headings in docs/data-model.md look like:  ### `taxon`   (one table)  or  ### `login_inactive_backup_*`
HEADING = re.compile(r"^#{2,4} +`([a-z0-9_*]+)`", re.M)

COLUMN_SKIP = ("primary", "key", "index", "unique", "constraint", "foreign", "fulltext", "check", "spatial")


def version_key(path):
    parts = []
    for p in path.parent.name.split("."):
        parts.append(int(p) if p.isdigit() else 0)
    return (parts, path.name)


def strip_sql_comments(text):
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    out = []
    for line in text.splitlines():
        s = line.lstrip()
        if s.startswith("--") or s.startswith("#"):
            continue
        # trailing comment outside quotes
        buf, q = [], None
        i = 0
        while i < len(line):
            ch = line[i]
            if q:
                buf.append(ch)
                if ch == q:
                    q = None
            elif ch in "'\"`":
                q = ch
                buf.append(ch)
            elif ch == "#" or (line.startswith("-- ", i)):
                break
            else:
                buf.append(ch)
            i += 1
        out.append("".join(buf))
    return "\n".join(out)


def split_top(text, sep):
    """Splits at sep outside quotes and parentheses."""
    parts, buf, depth, q = [], [], 0, None
    for ch in text:
        if q:
            buf.append(ch)
            if ch == q:
                q = None
            continue
        if ch in "'\"`":
            q = ch
        elif ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        elif ch == sep and depth == 0:
            parts.append("".join(buf))
            buf = []
            continue
        buf.append(ch)
    if "".join(buf).strip():
        parts.append("".join(buf))
    return parts


def ident(s):
    s = s.strip().strip("`").lower()
    if "." in s:  # ant.taxon
        s = s.split(".")[-1].strip("`")
    return s


NAME = r"(`?[A-Za-z0-9_$*]+`?(?:\.`?[A-Za-z0-9_$*]+`?)?)"


class Schema:
    def __init__(self):
        self.tables = {}  # name -> dict(columns: {col: type}, created: src, history: [..], origin)
        self.dropped = {}  # name -> src
        self.last_columns = {}  # name -> columns when it was last dropped (for CREATE TABLE x LIKE other_db.x)
        self.views = {}  # name -> src of the last CREATE VIEW
        self.dropped_views = {}

    def _t(self, name, src, origin="altered only"):
        if name in self.dropped and name not in self.tables and name not in ALTERED_AFTER_DROP:
            return None  # an ALTER left in a file after the table was dropped: the table stays dropped
        if name not in self.tables:
            self.tables[name] = {"columns": {}, "created": None, "origin": origin, "history": []}
            self.dropped.pop(name, None)
        return self.tables[name]

    def create(self, name, body, src, origin):
        t = {"columns": {}, "created": src, "origin": origin, "history": ["created " + src]}
        old = self.tables.get(name)
        if old and origin == "code":
            # The code creates "if not exists" a table an upgrade file already made: keep the file's columns
            # unless the code has more.
            old["history"].append("also created by " + src)
            if not old["columns"]:
                old["columns"] = parse_columns(body)
            return
        t["columns"] = parse_columns(body)
        self.tables[name] = t
        self.dropped.pop(name, None)

    def like(self, name, other, src):
        cols = dict(self.tables.get(other, {}).get("columns", {}) or self.last_columns.get(other, {}))
        self.tables[name] = {"columns": cols, "created": src, "origin": "copy of " + other,
                             "history": ["created " + src + " like " + other]}
        self.dropped.pop(name, None)

    def drop(self, name, src):
        if name in self.views:  # DROP TABLE on a view: MySQL refuses it, but the file means the object is gone
            del self.views[name]
            self.dropped_views[name] = src
            return
        if name in self.tables:
            self.last_columns[name] = self.tables.pop(name)["columns"]
        self.dropped[name] = src

    def rename(self, old, new, src):
        t = self.tables.pop(old, None) or {"columns": {}, "created": None, "origin": "altered only", "history": []}
        t["history"].append("renamed from " + old + " " + src)
        self.tables[new] = t
        self.dropped.pop(new, None)
        self.dropped[old] = src + " (renamed to " + new + ")"

    def alter(self, name, specs, src):
        if name in self.views:  # ALTER TABLE on a view (fails in MySQL; 8.56 tried it)
            return
        t = self._t(name, src)
        if t is None:
            return
        if not t["history"] or not t["history"][-1].endswith(src):
            t["history"].append("altered " + src)
        for spec in split_top(specs, ","):
            s = " ".join(spec.split())
            low = s.lower()
            m = re.match(r"rename\s+(?:to|as)\s+" + NAME, s, re.I)
            if m:
                self.rename(name, ident(m.group(1)), src)
                t = self.tables[ident(m.group(1))]
                name = ident(m.group(1))
                continue
            m = re.match(r"rename\s+column\s+" + NAME + r"\s+to\s+" + NAME, s, re.I)
            if m:
                old, new = ident(m.group(1)), ident(m.group(2))
                t["columns"][new] = t["columns"].pop(old, "?")
                continue
            m = re.match(r"add\s+(?:column\s+)?(?!index|key|unique|primary|constraint|fulltext|foreign|spatial)(?:if\s+not\s+exists\s+)?\(?(.*)$", s, re.I)
            if m and not re.match(r"add\s+(index|key|unique|primary|constraint|fulltext|foreign|spatial)\b", low):
                rest = m.group(1).rstrip(")") if m.group(1).count(")") > m.group(1).count("(") else m.group(1)
                for c in split_top(rest, ","):
                    col = column_def(c)
                    if col:
                        t["columns"][col[0]] = col[1]
                continue
            m = re.match(r"drop\s+(?:column\s+)?(?!index|key|primary|foreign)" + NAME + r"\s*$", s, re.I)
            if m and not re.match(r"drop\s+(index|key|primary|foreign)\b", low):
                t["columns"].pop(ident(m.group(1)), None)
                continue
            m = re.match(r"change\s+(?:column\s+)?" + NAME + r"\s+(.*)$", s, re.I)
            if m:
                old = ident(m.group(1))
                col = column_def(m.group(2))
                if col:
                    t["columns"].pop(old, None)
                    t["columns"][col[0]] = col[1]
                continue
            m = re.match(r"modify\s+(?:column\s+)?(.*)$", s, re.I)
            if m:
                col = column_def(m.group(1))
                if col:
                    t["columns"][col[0]] = col[1]
                continue


def column_def(c):
    c = " ".join(c.split())
    if not c:
        return None
    first = c.split(" ")[0].strip("`").lower()
    if first in COLUMN_SKIP or first.startswith("("):
        return None
    m = re.match(r"`?([A-Za-z0-9_]+)`?\s+([A-Za-z]+(?:\s*\([^)]*\))?(?:\s+unsigned)?)", c)
    if not m:
        return None
    return m.group(1).lower(), re.sub(r"\s+", "", m.group(2).lower()).replace("unsigned", " unsigned")


def parse_columns(body):
    cols = {}
    for c in split_top(body, ","):
        col = column_def(c)
        if col:
            cols[col[0]] = col[1]
    return cols


def body_of(stmt):
    i = stmt.find("(")
    if i < 0:
        return None
    depth = 0
    for j in range(i, len(stmt)):
        if stmt[j] == "(":
            depth += 1
        elif stmt[j] == ")":
            depth -= 1
            if depth == 0:
                return stmt[i + 1:j]
    return stmt[i + 1:]


def select_columns(rest):
    """Column names of CREATE TABLE ... AS SELECT a, b x, t.c ...; None for SELECT *."""
    m = re.search(r"(?is)select\s+(?:distinct\s+)?(.*)", rest)
    if not m:
        return []
    body = m.group(1)
    fm = None
    depth = 0
    for i in range(len(body)):
        ch = body[i]
        depth += ch == "("
        depth -= ch == ")"
        if depth == 0 and re.match(r"(?i)\sfrom\s", body[i:i + 6]):
            fm = i
            break
    items = split_top(body[:fm] if fm is not None else body, ",")
    cols = []
    for it in items:
        it = it.strip()
        if it == "*" or it.endswith(".*"):
            return None
        words = re.findall(r"`?([A-Za-z0-9_]+)`?\s*$", it)
        if words:
            cols.append(words[0].lower())
    return cols


def apply_statement(schema, stmt, src, origin):
    s = " ".join(stmt.split())
    m = re.match(r"create\s+(?:or\s+replace\s+)?(?:algorithm\s*=\s*\w+\s+)?(?:definer\s*=\s*\S+\s+)?"
                 r"(?:sql\s+security\s+\w+\s+)?view\s+" + NAME, s, re.I)
    if m:
        schema.views[ident(m.group(1))] = src
        schema.dropped_views.pop(ident(m.group(1)), None)
        schema.tables.pop(ident(m.group(1)), None)
        return
    m = re.match(r"drop\s+view\s+(?:if\s+exists\s+)?(.*)$", s, re.I)
    if m:
        for n in m.group(1).split(","):
            n = ident(n.split()[0])
            if schema.views.pop(n, None):
                schema.dropped_views[n] = src
        return
    m = re.match(r"create\s+(?:temporary\s+)?table\s+(?:if\s+not\s+exists\s+)?" + NAME + r"\s*(.*)$", s, re.I)
    if m:
        if re.match(r"create\s+temporary", s, re.I):
            return
        name, rest = ident(m.group(1)), m.group(2)
        lm = re.match(r"\(?\s*like\s+" + NAME, rest, re.I)
        if lm:
            schema.like(name, ident(lm.group(1)), src)
        elif rest.startswith("("):
            schema.create(name, body_of(rest), src, origin)
        elif re.match(r"(as\s+)?\(?\s*select", rest, re.I):
            cols = select_columns(rest)
            sm = re.search(r"\bfrom\s+" + NAME, rest, re.I)
            if cols is None and sm:  # select * from x
                schema.like(name, ident(sm.group(1)), src)
            else:
                schema.create(name, "", src, origin)
                schema.tables[name]["columns"] = {c: "?" for c in (cols or [])}
        return
    m = re.match(r"drop\s+table\s+(?:if\s+exists\s+)?(.*)$", s, re.I)
    if m:
        for n in m.group(1).split(","):
            schema.drop(ident(n.split()[0]), src)
        return
    m = re.match(r"rename\s+table\s+(.*)$", s, re.I)
    if m:
        for pair in m.group(1).split(","):
            pm = re.match(r"\s*" + NAME + r"\s+to\s+" + NAME, pair, re.I)
            if pm:
                schema.rename(ident(pm.group(1)), ident(pm.group(2)), src)
        return
    m = re.match(r"alter\s+(?:ignore\s+)?table\s+" + NAME + r"\s+(.*)$", s, re.I)
    if m and ident(m.group(1)) not in ("add", "drop", "modify", "change"):  # "alter table drop column x" (no name)
        schema.alter(ident(m.group(1)), m.group(2), src)


DDL_START = re.compile(r"(?i)(alter|create|drop|rename)\s+(table|view|temporary|or|algorithm|definer|sql|ignore|index|unique)\b")
STATEMENT_START = re.compile(r"(?i)(alter|create|drop|rename|insert|replace|update|delete|select|set|show|use|load|"
                             r"truncate|grant|describe|desc|mysql>|source)\b")


def split_semicolons(text):
    """Splits at semicolons outside quotes; a quote never runs past the end of its line (the files hold notes with
    apostrophes, and SQL strings here never span lines)."""
    parts, buf = [], []
    for line in text.splitlines():
        q, cur = None, []
        for ch in line:
            if q:
                if ch == q:
                    q = None
            elif ch in "'\"`":
                q = ch
            elif ch == ";":
                buf.append("".join(cur))
                parts.append("\n".join(buf))
                buf, cur = [], []
                continue
            cur.append(ch)
        buf.append("".join(cur))
    parts.append("\n".join(buf))
    return parts


def sql_statements(text):
    """Statements of an upgrade file. Many files mix SQL with notes and pasted output and leave out semicolons, so a
    line that starts (in column 1) with CREATE/ALTER/DROP/RENAME TABLE or VIEW always starts a new statement, and a
    line that starts with another SQL keyword does so outside parentheses."""
    stmts = []
    for chunk in split_semicolons(strip_sql_comments(text)):
        buf, depth = [], 0
        for line in chunk.splitlines():
            if buf and (DDL_START.match(line) or (depth == 0 and STATEMENT_START.match(line)
                                                  and not re.search(r"(?i)\bas\s*$", buf[-1]))):
                stmts.append("\n".join(buf))
                buf, depth = [], 0
            buf.append(line)
            depth = max(depth + line.count("(") - line.count(")"), 0)
        if buf:
            stmts.append("\n".join(buf))
    return [s for s in stmts if s.strip()]


JAVA_LIT = r'"(?:[^"\\]|\\.)*"'

# Methods that return a table name, across src/ (e.g. InactiveAccounts.backupTableName):
# name -> the returned string literal, with format placeholders (%04d) as *.
METHOD_RETURNS = {}


def collect_method_returns():
    for p in (ROOT / "src").rglob("*.java"):
        text = p.read_text(encoding="utf-8", errors="replace")
        for m in re.finditer(r"static\s+String\s+([A-Za-z_][A-Za-z0-9_]*)\s*\([^)]*\)\s*\{\s*return\s+(?:String\.format\(\s*)?("
                             + JAVA_LIT + r")", text):
            METHOD_RETURNS[m.group(1)] = '"' + re.sub(r"%[0-9]*[a-z]", "*", m.group(2)[1:-1]) + '"'


def java_unescape(lit):
    """The text of a Java string literal: the usual escapes only, other characters (also non-ASCII) as they are."""
    escapes = {"n": "\n", "t": "\t", "r": "\r", '"': '"', "'": "'", "\\": "\\"}
    return re.sub(r"\\(.)", lambda m: escapes.get(m.group(1), m.group(1)), lit)


def java_create_statements(text):
    """CREATE TABLE statements built from string literals and constants in one Java file."""
    consts = {}
    for m in re.finditer(r"\bString\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*((?:" + JAVA_LIT + r"|[A-Za-z_.()\"\s,]+?)(?:\s*\+\s*(?:"
                         + JAVA_LIT + r"|[^;+]+))*)\s*;", text):
        consts[m.group(1)] = m.group(2)

    def expand(expr, depth=0):
        out = []
        for tok in re.findall(JAVA_LIT + r"|[A-Za-z_][A-Za-z0-9_.]*(?:\([^)]*\))?", expr):
            if tok.startswith('"'):
                out.append(java_unescape(tok[1:-1]))
            elif tok in consts and depth < 3:
                out.append(expand(consts[tok], depth + 1))
            else:
                method = re.match(r"(?:[A-Za-z_][A-Za-z0-9_]*\.)*([A-Za-z_][A-Za-z0-9_]*)\(", tok)
                ret = METHOD_RETURNS.get(method.group(1)) if method else None
                if ret and depth < 3:
                    out.append(expand(ret, depth + 1))
                else:
                    out.append("*")
        s = "".join(out)
        return re.sub(r"\*+", "*", s)

    stmts = []
    for m in re.finditer(r"(?i)create table", text):
        # from the opening quote of the literal, forward over the concatenation (literals, +, names, calls)
        start = text.rfind('"', 0, m.start())
        chunk = re.match(r"(?:" + JAVA_LIT + r"|\s|\+|[A-Za-z_][A-Za-z0-9_.]*(?:\([^)]*\))?)+", text[start:], re.S)
        expr = chunk.group(0) if chunk else ""
        stmts.append(expand(expr))
    return stmts


# Statements in db/upgrade that the files themselves say were not run, or that only record a table's old
# definition. They are skipped, so the list does not count a drop that never happened.
NOT_RUN = {
    # "These have not been executed. Proposed changes below." (charset conversion; also proposes dropping
    # museumBak and taxon_prop20170617, so those two may still exist).
    "db/upgrade/8.56/2021-06-21.sql": None,
}
# Tables an upgrade file drops but a later file alters, so they evidently still exist. Any other ALTER of a dropped
# table is ignored (it would otherwise bring the table back and CI would ask for a section).
ALTERED_AFTER_DROP = {
    # dropped in db/upgrade/6.16/2017-01-16.sql, converted to InnoDB in db/upgrade/8.56/2021-04-08.sql
    "taxon_country",
}
# (file, table): a CREATE TABLE that records what was dropped just before ("These are gone now").
RECORD_ONLY = {
    ("db/upgrade/6.10/2016-09-12.sql", "dwc_image"),
    ("db/upgrade/6.10/2016-09-12.sql", "dwc_specimen"),
}


def build():
    collect_method_returns()
    schema = Schema()
    upgrade_files = sorted((p for p in (ROOT / "db" / "upgrade").rglob("*.sql")), key=version_key)
    for p in upgrade_files:
        rel = str(p.relative_to(ROOT))
        if rel in NOT_RUN:
            continue
        for stmt in sql_statements(p.read_text(encoding="utf-8", errors="replace")):
            m = re.match(r"\s*create\s+table\s+" + NAME, stmt, re.I)
            if m and (rel, ident(m.group(1))) in RECORD_ONLY:
                continue
            apply_statement(schema, stmt, rel, "db/upgrade")
    for p in sorted((ROOT / "src").rglob("*.java")):
        text = p.read_text(encoding="utf-8", errors="replace")
        if not re.search(r"(?i)create table", text):
            continue
        rel = str(p.relative_to(ROOT))
        for stmt in java_create_statements(text):
            apply_statement(schema, stmt, rel, "code")
    # Tables only the tests define (they predate db/upgrade): partial columns.
    test_cols = {}
    for base in ("test", "tests"):
        for p in sorted((ROOT / base).rglob("*")):
            if p.suffix not in (".java", ".py") or not p.is_file():
                continue
            text = p.read_text(encoding="utf-8", errors="replace")
            if not re.search(r"(?i)create table", text):
                continue
            rel = str(p.relative_to(ROOT))
            stmts = java_create_statements(text) if p.suffix == ".java" else [
                s for s in re.findall(r"(?is)create table.*?\)\s*(?:engine[^\"']*)?(?=[\"'])", text)]
            for stmt in stmts:
                tmp = Schema()
                apply_statement(tmp, stmt, rel, "test")
                for n, t in tmp.tables.items():
                    test_cols.setdefault(n, (rel, {}))[1].update(t["columns"])
    for n, (rel, cols) in test_cols.items():
        if n in schema.tables:
            t = schema.tables[n]
            if t["created"] is None:
                for c, ty in cols.items():
                    t["columns"].setdefault(c, ty)
                t["history"].append("columns also from test schema " + rel)
    return schema


def doc_headings():
    """Every table heading of docs/data-model.md, in order (a list, so a heading written twice can be found)."""
    if not DOC.exists():
        return []
    return HEADING.findall(DOC.read_text(encoding="utf-8"))


def norm_type(t):
    t = t.lower().replace(" ", "")
    t = re.sub(r"^(tinyint|smallint|mediumint|int|bigint)\(\d+\)", r"\1", t)  # MySQL 8 drops display widths
    return t.replace("unsigned", " unsigned")


def compare(schema, path):
    live, kinds = {}, {}
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        parts = line.split("\t")
        if len(parts) < 3:
            continue
        t, c, ty = parts[0].lower(), parts[1].lower(), parts[2]
        live.setdefault(t, {})[c] = ty
        kinds[t] = parts[3] if len(parts) > 3 else "BASE TABLE"
    repo = {n: t["columns"] for n, t in schema.tables.items() if "*" not in n}
    patterns = [re.compile("^" + re.escape(n).replace(r"\*", ".*") + "$") for n in schema.tables if "*" in n]
    live_tables = {t for t, k in kinds.items() if k != "VIEW"}
    live_views = {t for t, k in kinds.items() if k == "VIEW"}
    only_live = sorted(t for t in live_tables if t not in repo and not any(p.match(t) for p in patterns))
    only_repo = sorted(t for t in repo if t not in live_tables)
    print("Tables in the live database that the repository does not know:", ", ".join(only_live) or "none")
    print("Tables the repository describes that the live database does not have:", ", ".join(only_repo) or "none")
    print("Views live but not in the repository:", ", ".join(sorted(live_views - set(schema.views))) or "none")
    print("Views in the repository but not live:", ", ".join(sorted(set(schema.views) - live_views)) or "none")
    for t in sorted(set(repo) & live_tables):
        r, l = repo[t], live[t]
        if not r:
            continue
        missing = [c for c in r if c not in l]
        extra = [c for c in l if c not in r]
        differ = [f"{c} {r[c]} -> {l[c]}" for c in r if c in l and r[c] != "?" and norm_type(r[c]) != norm_type(l[c])]
        if missing or extra or differ:
            print(f"\n{t}:")
            if missing:
                print("  not live:", ", ".join(missing))
            if extra:
                print("  live only:", ", ".join(extra))
            if differ:
                print("  type differs (repository -> live):", "; ".join(differ))
    return 0


def main(argv):
    schema = build()
    names = sorted(schema.tables)
    views = sorted(schema.views)
    if "--json" in argv:
        print(json.dumps({"tables": schema.tables, "dropped": schema.dropped, "views": schema.views,
                          "dropped_views": schema.dropped_views}, indent=1, sort_keys=True))
        return 0
    if "--compare" in argv:
        i = argv.index("--compare")
        if i + 1 >= len(argv):
            print("usage: list_tables.py --compare LIVE.tsv (see the top of this file for how to make LIVE.tsv)")
            return 2
        path = Path(argv[i + 1])
        if not path.is_file():
            print(f"--compare: {path} is not a readable file")
            return 2
        return compare(schema, path)
    if "--check" in argv:
        headings = doc_headings()
        sections = set(headings)
        twice = sorted({h for h in headings if headings.count(h) > 1})
        known = set(schema.tables) | set(schema.dropped) | set(schema.views) | set(schema.dropped_views)
        missing = [n for n in names + views if n not in sections]
        unknown = [s for s in sorted(sections) if s not in known]
        for n in twice:
            print(f"::error file=docs/data-model.md::table {n} has two sections in docs/data-model.md (keep one)")
        for n in missing:
            kind = "view" if n in schema.views else "table"
            where = schema.views[n] if n in schema.views else (schema.tables[n]["created"] or schema.tables[n]["origin"])
            print(f"::error file=docs/data-model.md::{kind} {n} ({where}) has no section in docs/data-model.md "
                  "(add a heading ### `" + n + "`; see tools/datamodel/list_tables.py)")
        for s in unknown:
            print(f"::error file=docs/data-model.md::section `{s}` names a table no SQL in the repository creates, "
                  "alters or drops (rename the heading, or remove the section)")
        print(f"{len(names)} tables and {len(views)} views in the repository's SQL, {len(sections)} sections in "
              f"docs/data-model.md, {len(missing)} missing, {len(unknown)} unknown")
        return 1 if missing or unknown or twice else 0
    for n in names:
        t = schema.tables[n]
        print(f"{n}\t{t['created'] or '(not created in the repository: ' + t['origin'] + ')'}")
        for c, ty in t["columns"].items():
            print(f"    {c} {ty}")
    print(f"\n{len(names)} tables.")
    print("Views: " + ", ".join(f"{v} ({schema.views[v]})" for v in views))
    print("Dropped by the upgrade scripts: " + ", ".join(sorted(schema.dropped)))
    print("Views dropped: " + ", ".join(sorted(schema.dropped_views)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
