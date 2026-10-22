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
                                                      repository does not know, or the repository's SQL and the
                                                      committed live snapshot (doc/schema/live-schema-*.tsv, the
                                                      newest) differ in a way that is not on the allow-list
                                                      below (CI, tests.yaml)

The live snapshot (2026-10-22): doc/schema/live-schema-<date>.tsv is the output of the query above on production. The
catch-up file db/upgrade/8.115/2026-10-22-schema-catch-up.sql brings the repository's SQL to it; its guarded
procedure calls (CALL schema_add_column / schema_modify_column / schema_drop_column / schema_drop_empty_table /
schema_drop_view) are read
here like the ALTER TABLE and DROP VIEW they run. What may still differ (--check passes):
  - tables only live whose names say they are dated backups (BACKUP_PATTERN; the same pattern as SchemaCheck.java),
  - the old live-only tables and views in LIVE_ONLY_UNUSED / LIVE_ONLY_UNUSED_VIEWS (each has a section in
    docs/data-model.md, proposed for dropping),
  - a column whose type the repository does not know ("?", from CREATE TABLE ... AS SELECT),
  - a table only the repository has whose name contains * (made at run time, e.g. login_inactive_backup_*).
  - a table or column added or changed by a db/upgrade file whose name is dated after the snapshot (pending: it is run
    on production after the merge; --check prints a notice until the snapshot is refreshed).
Everything else fails the check: add the change to a db/upgrade file (and run it on production), or refresh the
snapshot when production changed (the command in docs/data-model.md).
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

SNAPSHOT_DIR = ROOT / "doc" / "schema"   # doc/ is mounted in the container (/antweb/deploy/doc): SchemaCheck.java reads it

# Dated backup tables (made by hand or by the code before a bulk change). Same pattern as
# src/org/calacademy/antweb/util/SchemaCheck.java BACKUP (tests/unit/test_datamodel_list_tables.py checks that).
BACKUP_PATTERN = r"(_bak_|_backup_|_deleted_)|_?([0-9]{8}|[0-9]{4}_[0-9]{2}_[0-9]{2})$"
BACKUP = re.compile(BACKUP_PATTERN)

# Old tables and views production has and no SQL in the repository creates, that no code reads (search of src/, web/,
# api/, bin/, 2026-10-22). docs/data-model.md section 13 proposes dropping them (needs Brian's yes, back up first).
# A table dropped from production after the snapshot is removed from here when the snapshot is refreshed.
LIVE_ONLY_UNUSED = {
    "afro_types_tmp", "artist_copy", "description", "favorite_images", "groups_project", "homepage", "image_backup",
    "image_count", "image_count_materialized", "junkspec", "proj_taxont", "similar_species", "synonymy",
    "temp_image_artist",
}
LIVE_ONLY_UNUSED_VIEWS = {"darwin_core_2_test"}

COLUMN_SKIP = ("primary", "key", "index", "unique", "constraint", "foreign", "fulltext", "check", "spatial")


def version_key(path):
    """Sort key of an upgrade file: the version folder right under db/upgrade (also for files in subfolders), then
    the path below it."""
    rel = path.relative_to(ROOT / "db" / "upgrade").parts
    parts = []
    for p in rel[0].split("."):
        parts.append(int(p) if p.isdigit() else 0)
    return (parts, "/".join(rel[1:]))


def opens_quote(text, i):
    """True when text[i] opens a quoted string. An apostrophe inside a word (a note such as "doesn't") does not."""
    ch = text[i]
    if ch not in "'\"`":
        return False
    return not (ch == "'" and i > 0 and text[i - 1].isalnum())


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
            elif opens_quote(line, i):
                q = ch
                buf.append(ch)
            elif ch == "#" or re.match(r"--(\s|$)", line[i:]):
                break
            else:
                buf.append(ch)
            i += 1
        out.append("".join(buf))
    return "\n".join(out)


def split_top(text, sep):
    """Splits at sep outside quotes and parentheses."""
    parts, buf, depth, q = [], [], 0, None
    for i, ch in enumerate(text):
        if q:
            buf.append(ch)
            if ch == q or ch == "\n":  # a quote never runs past the end of its line
                q = None
            continue
        if opens_quote(text, i):
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
            # The code creates "if not exists" a table an upgrade file already made: keep the file's columns;
            # the code's are used only when the file gave none.
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
        if old in self.dropped and old not in self.tables and old not in ALTERED_AFTER_DROP:
            return  # a RENAME left in a file after the table was dropped
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
                if ident(m.group(1)) not in self.tables:
                    return
                t = self.tables[ident(m.group(1))]
                name = ident(m.group(1))
                continue
            m = re.match(r"rename\s+column\s+" + NAME + r"\s+to\s+" + NAME, s, re.I)
            if m:
                old, new = ident(m.group(1)), ident(m.group(2))
                t["columns"][new] = t["columns"].pop(old, "?")
                continue
            m = re.match(r"add\s+(?:column\s+)?(?!(?:index|key|unique|primary|constraint|fulltext|foreign|spatial)\b)(?:if\s+not\s+exists\s+)?\(?(.*)$", s, re.I)
            if m and not re.match(r"add\s+(index|key|unique|primary|constraint|fulltext|foreign|spatial)\b", low):
                rest = m.group(1)
                if rest.count(")") > rest.count("(") and rest.rstrip().endswith(")"):
                    rest = rest.rstrip()[:-1]  # "add column (title varchar(1000))": only the one closing parenthesis
                for c in split_top(rest, ","):
                    col = column_def(c)
                    if col:
                        t["columns"][col[0]] = col[1]
                        t.setdefault("column_src", {})[col[0]] = src
                continue
            m = re.match(r"drop\s+(?:column\s+)?(?!(?:index|key|primary|foreign)\b)" + NAME + r"\s*$", s, re.I)
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
                    t.setdefault("column_src", {})[col[0]] = src
                continue
            m = re.match(r"modify\s+(?:column\s+)?(.*)$", s, re.I)
            if m:
                col = column_def(m.group(1))
                if col:
                    t["columns"][col[0]] = col[1]
                    t.setdefault("column_src", {})[col[0]] = src
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
    ty = re.sub(r"\s+", "", m.group(2))
    ty = re.sub(r"^[A-Za-z]+", lambda w: w.group(0).lower(), ty)  # the type word in lower case, enum values as written
    return m.group(1).lower(), re.sub(r"(?i)unsigned$", " unsigned", ty)


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
    m = re.match(r"create\s+(?:temporary\s+)?table\s+(?:if\s+not\s+exists\s+)?`?([A-Za-z0-9_$*]+)", s, re.I)
    if m and not re.search(r"[A-Za-z0-9]", m.group(1)):
        return  # the name is made entirely at run time (Java): nothing to list
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
            if not n.strip():
                continue
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
        elif rest.startswith("(") and not re.match(r"\(\s*select\b", rest, re.I):
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
            if n.strip():
                schema.drop(ident(n.split()[0]), src)
        return
    m = re.match(r"rename\s+table\s+(.*)$", s, re.I)
    if m:
        for pair in m.group(1).split(","):
            pm = re.match(r"\s*" + NAME + r"\s+to\s+" + NAME, pair, re.I)
            if pm:
                schema.rename(ident(pm.group(1)), ident(pm.group(2)), src)
        return
    m = re.match(r"call\s+schema_(add|modify|drop)_column\s*\(\s*'([A-Za-z0-9_]+)'\s*,\s*'([A-Za-z0-9_]+)'\s*"
                 r"(?:,\s*'([^']*)'\s*)?\)\s*$", s, re.I)
    if m:  # the catch-up file's guarded procedures (db/upgrade/8.115): read as the ALTER TABLE they run
        verb, table, column, definition = m.group(1).lower(), m.group(2).lower(), m.group(3), m.group(4) or ""
        if verb == "drop":
            schema.alter(table, "drop column " + column, src)
        elif verb == "add":
            schema.alter(table, "add column " + column + " " + definition, src)
        else:
            schema.alter(table, "modify column " + column + " " + definition, src)
        return
    m = re.match(r"call\s+schema_drop_empty_table\s*\(\s*'([A-Za-z0-9_]+)'\s*\)\s*$", s, re.I)
    if m:  # dropped where it holds no rows (a fresh build); production does not have these tables
        schema.drop(m.group(1).lower(), src)
        return
    m = re.match(r"call\s+schema_drop_view\s*\(\s*'([A-Za-z0-9_]+)'\s*\)\s*$", s, re.I)
    if m:
        n = m.group(1).lower()
        if schema.views.pop(n, None):
            schema.dropped_views[n] = src
        return
    m = re.match(r"alter\s+(?:ignore\s+)?table\s+" + NAME + r"\s+(.*)$", s, re.I)
    if m and ident(m.group(1)) not in ("add", "drop", "modify", "change"):  # "alter table drop column x" (no name)
        schema.alter(ident(m.group(1)), m.group(2), src)


DDL_START = re.compile(r"(?i)(alter|create|drop|rename)\s+(table|view|temporary|or|algorithm|definer|sql|ignore|index|unique)\b")
STATEMENT_START = re.compile(r"(?i)(alter|create|drop|rename|insert|replace|update|delete|select|set|show|use|load|"
                             r"truncate|grant|describe|desc|mysql>|source|call)\b")


def split_semicolons(text):
    """Splits at semicolons outside quotes; a quote never runs past the end of its line (the files hold notes with
    apostrophes, and SQL strings here never span lines)."""
    parts, buf = [], []
    for line in text.splitlines():
        q, cur = None, []
        for i, ch in enumerate(line):
            if q:
                if ch == q:
                    q = None
            elif opens_quote(line, i):
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
    # DELIMITER // ... DELIMITER ; holds stored procedures (db/upgrade/8.115): their bodies are not table changes
    text = re.sub(r"(?ims)^[ \t]*DELIMITER[ \t]+(?!;)\S+[ \t]*$.*?^[ \t]*DELIMITER[ \t]+;[ \t]*$", "", text)
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


JAVA_LIT = r'"""[\s\S]*?"""|"(?:[^"\\\n]|\\.)*"'

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


def strip_java_comments(text):
    """Java source without // and /* */ comments (string and char literals kept as they are)."""
    out, i, n = [], 0, len(text)
    while i < n:
        ch = text[i]
        if text.startswith('"""', i):  # text block (Java 15+)
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append(text[i:j])
            i = j
        elif ch in "\"'":
            j = i + 1
            while j < n and text[j] != ch and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            out.append(text[i:j + 1])
            i = j + 1
        elif text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j < 0 else j
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            out.append(" ")
            i = n if j < 0 else j + 2
        else:
            out.append(ch)
            i += 1
    return "".join(out)


def java_create_statements(text):
    """CREATE TABLE statements built from string literals and constants in one Java file."""
    consts = {}
    for m in re.finditer(r"\bString\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*((?:" + JAVA_LIT + r"|[A-Za-z_.()\"\s,]+?)(?:\s*\+\s*(?:"
                         + JAVA_LIT + r"|[^;+]+))*)\s*;", text):
        consts[m.group(1)] = m.group(2)

    def expand(expr, depth=0):
        out = []
        for tok in re.findall(JAVA_LIT + r"|[A-Za-z_][A-Za-z0-9_.]*(?:\([^)]*\))?", expr):
            if tok.startswith('"""'):
                out.append(java_unescape(tok[3:-3]))
            elif tok.startswith('"'):
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
    code = strip_java_comments(text)
    for m in re.finditer(JAVA_LIT, code):
        if not re.search(r"(?i)create\s+table", m.group(0)):
            continue
        # from this literal, forward over the concatenation (literals, +, names, calls)
        start = m.start()
        chunk = re.match(r"(?:" + JAVA_LIT + r"|\s|\+|[A-Za-z_][A-Za-z0-9_.]*(?:\([^)]*\))?)+", code[start:], re.S)
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
            if p.suffix not in (".java", ".py") or not p.is_file() or p.name == "test_datamodel_list_tables.py":
                continue  # (the generator's own test holds made-up tables)
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
    if t in ("long", "longvarchar"):  # MySQL's aliases of mediumtext (db/upgrade/7.9 object_map)
        t = "mediumtext"
    return t.replace("unsigned", " unsigned")


def read_live(path):
    """(columns, kinds) of a live schema export: table -> {column: type}, table -> table_type. Names lower case."""
    live, kinds = {}, {}
    for line in Path(path).read_text(encoding="utf-8", errors="replace").splitlines():
        parts = line.split("\t")
        if len(parts) < 3:
            continue
        t, c, ty = parts[0].lower(), parts[1].lower(), parts[2]
        live.setdefault(t, {})[c] = ty
        kinds[t] = parts[3] if len(parts) > 3 else "BASE TABLE"
    return live, kinds


def latest_snapshot(directory=None):
    """The newest committed live snapshot (doc/schema/live-schema-YYYY-MM-DD.tsv), or None."""
    files = sorted(Path(directory or SNAPSHOT_DIR).glob("live-schema-*.tsv"))
    return files[-1] if files else None


def diff(schema, live, kinds):
    """The differences between the repository's schema and a live one, as a dict of lists (names sorted)."""
    repo = {n: t["columns"] for n, t in schema.tables.items() if "*" not in n}
    patterns = [re.compile("^" + re.escape(n).replace(r"\*", ".*") + "$") for n in schema.tables if "*" in n]
    live_tables = {t for t, k in kinds.items() if k != "VIEW"}
    live_views = {t for t, k in kinds.items() if k == "VIEW"}
    d = {
        "only_live": sorted(t for t in live_tables if t not in repo and not any(p.match(t) for p in patterns)),
        "only_repo": sorted(t for t in repo if t not in live_tables),
        "views_only_live": sorted(live_views - set(schema.views)),
        "views_only_repo": sorted(set(schema.views) - live_views),
        "columns": {},
    }
    for t in sorted(set(repo) & live_tables):
        r, l = repo[t], live[t]
        if not r:
            continue
        missing = [c for c in r if c not in l]
        extra = [c for c in l if c not in r]
        differ = [f"{c} {r[c]} -> {l[c]}" for c in r if c in l and r[c] != "?" and norm_type(r[c]) != norm_type(l[c])]
        if missing or extra or differ:
            d["columns"][t] = {"missing": missing, "extra": extra, "differ": differ}
    return d


def compare(schema, path):
    live, kinds = read_live(path)
    if not live:
        print(f"--compare: no rows in {path}; the query or the login failed (see the command in docs/data-model.md)")
        return 2
    d = diff(schema, live, kinds)
    print("Tables in the live database that the repository does not know:", ", ".join(d["only_live"]) or "none")
    print("Tables the repository describes that the live database does not have:", ", ".join(d["only_repo"]) or "none")
    print("Views live but not in the repository:", ", ".join(d["views_only_live"]) or "none")
    print("Views in the repository but not live:", ", ".join(d["views_only_repo"]) or "none")
    for t, c in d["columns"].items():
        print(f"\n{t}:")
        if c["missing"]:
            print("  not live:", ", ".join(c["missing"]))
        if c["extra"]:
            print("  live only:", ", ".join(c["extra"]))
        if c["differ"]:
            print("  type differs (repository -> live):", "; ".join(c["differ"]))
    return 0


def file_date(src):
    """The date in an upgrade file's name (db/upgrade/8.116/2026-11-02-x.sql -> 2026-11-02), or ""."""
    m = re.search(r"db/upgrade/[^/]+/(\d{4}-\d{2}-\d{2})", src or "")
    return m.group(1) if m else ""


def pending_notes(schema, path):
    """Changes the repository's SQL makes in upgrade files dated after the snapshot: not on production yet (they
    are run there after the merge), so --check notes them instead of failing. Returns (notes, explained)."""
    snap_date = re.search(r"(\d{4}-\d{2}-\d{2})", Path(path).name)
    snap_date = snap_date.group(1) if snap_date else "9999"
    live, kinds = read_live(path)
    d = diff(schema, live, kinds)
    notes, explained = [], set()
    for t in d["only_repo"]:
        src = schema.tables[t]["created"] or ""
        if file_date(src) > snap_date:
            notes.append(f"table {t} ({src}) is newer than the snapshot")
            explained.add(("table", t))
    for t, c in d["columns"].items():
        srcs = schema.tables[t].get("column_src", {})
        created = schema.tables[t]["created"] or ""
        for col in c["missing"] + [x.split(" ")[0] for x in c["differ"]]:
            src = srcs.get(col) or created
            if file_date(src) > snap_date:
                notes.append(f"column {t}.{col} ({src}) is newer than the snapshot")
                explained.add((t, col))
    return notes, explained


def snapshot_errors(schema, path):
    """Differences between the repository's SQL and the committed snapshot that the allow-list does not explain.
    Changes made by upgrade files dated after the snapshot are pending (pending_notes), not errors."""
    live, kinds = read_live(path)
    if not live:
        return [f"{path} has no rows"]
    d = diff(schema, live, kinds)
    _notes, pending = pending_notes(schema, path)
    d["only_repo"] = [t for t in d["only_repo"] if ("table", t) not in pending]
    for t, c in d["columns"].items():
        c["missing"] = [x for x in c["missing"] if (t, x) not in pending]
        c["differ"] = [x for x in c["differ"] if (t, x.split(" ")[0]) not in pending]
    errors = []
    for t in d["only_live"]:
        if not BACKUP.search(t) and t not in LIVE_ONLY_UNUSED:
            errors.append(f"table {t} is live (snapshot) but no SQL in the repository creates it: add a db/upgrade file, "
                          "or (an old unused table) add it to LIVE_ONLY_UNUSED with a section in docs/data-model.md")
    for t in d["only_repo"]:
        errors.append(f"table {t} is in the repository's SQL but not live (snapshot): run its db/upgrade file on "
                      "production and refresh the snapshot, or drop it in a db/upgrade file")
    for v in d["views_only_live"]:
        if v not in LIVE_ONLY_UNUSED_VIEWS:
            errors.append(f"view {v} is live (snapshot) but no SQL in the repository creates it")
    for v in d["views_only_repo"]:
        errors.append(f"view {v} is in the repository's SQL but not live (snapshot)")
    for t, c in d["columns"].items():
        for col in c["missing"]:
            errors.append(f"column {t}.{col} is in the repository's SQL but not live (snapshot)")
        for col in c["extra"]:
            errors.append(f"column {t}.{col} is live (snapshot) but not in the repository's SQL")
        for x in c["differ"]:
            errors.append(f"column {t}.{x}: the type differs (repository -> snapshot)")
    for t in sorted(LIVE_ONLY_UNUSED - set(d["only_live"])):
        errors.append(f"LIVE_ONLY_UNUSED names {t}, which is not a live-only table of the snapshot: remove it from the list")
    for v in sorted(LIVE_ONLY_UNUSED_VIEWS - set(d["views_only_live"])):
        errors.append(f"LIVE_ONLY_UNUSED_VIEWS names {v}, which is not a live-only view of the snapshot: remove it")
    return errors


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
        snapshot = latest_snapshot()
        live_names = set(read_live(snapshot)[1]) if snapshot else set()
        headings = doc_headings()
        sections = set(headings)
        twice = sorted({h for h in headings if headings.count(h) > 1})
        known = set(schema.tables) | set(schema.views) | live_names
        gone = {**schema.dropped, **schema.dropped_views}
        missing = [n for n in sorted(set(names + views) | live_names)
                   if n not in sections and not any(re.match("^" + re.escape(p).replace(r"\*", ".*") + "$", n)
                                                    for p in sections if "*" in p)]
        unknown = [s for s in sorted(sections) if s not in known and s not in gone]
        dropped = [s for s in sorted(sections) if s not in known and s in gone]
        for s in dropped:
            print(f"::error file=docs/data-model.md::section `{s}`: the table was dropped by {gone[s]}; move it to "
                  "\"Dropped already\" (a line, not a section)")
        for n in twice:
            print(f"::error file=docs/data-model.md::table {n} has two sections in docs/data-model.md (keep one)")
        for n in missing:
            if n in schema.views:
                kind, where = "view", schema.views[n]
            elif n in schema.tables:
                kind, where = "table", schema.tables[n]["created"] or schema.tables[n]["origin"]
            else:
                kind, where = "table", "live only, " + snapshot.name
            print(f"::error file=docs/data-model.md::{kind} {n} ({where}) has no section in docs/data-model.md "
                  "(add a heading ### `" + n + "`; see tools/datamodel/list_tables.py)")
        for s in unknown:
            print(f"::error file=docs/data-model.md::section `{s}` names a table no SQL in the repository creates, "
                  "alters or drops, and the live snapshot does not have (rename the heading, or remove the section)")
        drift = snapshot_errors(schema, snapshot) if snapshot else ["no snapshot doc/schema/live-schema-*.tsv"]
        for e in drift:
            print(f"::error file=db/upgrade::{e}")
        if snapshot:
            for n in pending_notes(schema, snapshot)[0]:
                print(f"::notice file=db/upgrade::{n}: run it on production, then refresh the snapshot")
        print(f"{len(names)} tables and {len(views)} views in the repository's SQL, {len(sections)} sections in "
              f"docs/data-model.md, {len(missing)} missing, {len(unknown)} unknown, {len(dropped)} dropped; "
              f"snapshot {snapshot.name if snapshot else 'none'}: {len(live_names)} tables and views, "
              f"{len(drift)} unexplained differences")
        return 1 if missing or unknown or twice or dropped or drift else 0
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
