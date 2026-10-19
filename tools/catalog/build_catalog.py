#!/usr/bin/env python3
"""Build the AntWeb function catalog: one row per Struts action, per action sub-action
(an `action=` value the class dispatches on) and per API v3.1 route.

    python3 tools/catalog/build_catalog.py                     # regenerate docs/catalog/
    python3 tools/catalog/build_catalog.py --traffic t.tsv     # also fill the 30-day hit columns
    python3 tools/catalog/build_catalog.py --check             # CI: fail if an action has no row

Machine columns come from the code and are rewritten on every run:
  - actions, classes and forwards (JSPs) from WEB-INF/struts-config.xml;
  - sub-actions from the Java source (string literals compared with the `action` parameter,
    or the class's ACTIONS set where it has one);
  - the access rule from the checks found in the Java class (and its superclass) and in the
    JSPs it forwards to (adminCheck.jsp, curatorCheck.jsp, loginCheck.jsp includes);
  - e2e coverage from tests/e2e (URLs each test requests, including parametrized lists and
    the recorded baseline in expectations.json);
  - API routes from api/v3.1/api.py;
  - traffic (the "hits" column) from a file written by tools/traffic-by-action.sh (optional;
    key<TAB>total is enough). A key missing from the file is shown as "<N" with --traffic-min N
    (for a list cut at its smallest counts), else 0. When no file is given, the hit counts
    already in actions.csv are kept. An action's count includes its ?action= requests.

Hand columns (purpose, status, notes) are read back from the existing docs/catalog/actions.csv
by key and kept. Rows whose key no longer exists are reported on stderr and dropped.

The access rule is a static reading of the code, not a proof: "no check found" means the
generator found no login, curator or admin check, not that none exists. See docs/catalog/README
section in actions.md for the meaning of each value.

Python 3.9+, standard library only.
"""
import argparse
import ast
import csv
import io
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
STRUTS = REPO / "WEB-INF" / "struts-config.xml"
SRC = REPO / "src"
WEB = REPO / "web"
E2E = REPO / "tests" / "e2e"
API = REPO / "api" / "v3.1" / "api.py"
OUT = REPO / "docs" / "catalog"

HAND = ["purpose", "status", "notes"]
COLUMNS = ["key", "kind", "url", "class", "jsps", "access", "access_where", "access_evidence",
           "writes", "anon_result", "e2e_tests", "e2e_probe", "hits"] + HAND

RANK = {"public": 0, "logged-in": 1, "curator": 2, "admin": 3}


# ---------------------------------------------------------------- struts-config

def struts_actions():
    root = ET.parse(STRUTS).getroot()
    out = []
    for a in root.find("action-mappings").findall("action"):
        forwards = [(f.get("name"), f.get("path")) for f in a.findall("forward")]
        out.append({
            "path": a.get("path"),
            "type": a.get("type"),
            "forward": a.get("forward"),
            "unknown": a.get("unknown") == "true",
            "parameter": a.get("parameter"),
            "forwards": forwards,
        })
    return out


def action_url(path):
    return "/" if path == "/" else path + ".do"


# ---------------------------------------------------------------- Java source

def strip_java_comments(text):
    """Remove // and /* */ comments, keeping string and char literals and line breaks."""
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if c == '"' or c == "'":
            q, j = c, i + 1
            while j < n and text[j] != q:
                j += 2 if text[j] == "\\" else 1
            out.append(text[i:j + 1])
            i = j + 1
        elif text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j < 0 else j
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append("\n" * text.count("\n", i, j))
            i = j
        else:
            out.append(c)
            i += 1
    return "".join(out)


def java_file(cls):
    if not cls:
        return None
    p = SRC / (cls.replace(".", "/") + ".java")
    return p if p.exists() else None


def matching(text, i, open_ch, close_ch):
    """Index just past the bracket that closes the one at text[i]."""
    depth, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            i = j
        elif c == open_ch:
            depth += 1
        elif c == close_ch:
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return n


def method_spans(code):
    """(name, start, end) of each method body in the class (rough, good enough for actions)."""
    spans = []
    for m in re.finditer(r"(?:public|protected|private)[\w\s<>\[\],.?]*?\s(\w+)\s*\(", code):
        name = m.group(1)
        p = m.end() - 1
        close = matching(code, p, "(", ")")
        rest = code[close:close + 300]
        b = re.match(r"\s*(?:throws[\w\s,.]+)?\{", rest)
        if not b:
            continue
        start = close + b.end() - 1
        spans.append((name, start, matching(code, start, "{", "}")))
    return spans


def line_of(code, i):
    return code.count("\n", 0, i) + 1


ADMIN_NEG = re.compile(r"!\s*(?:LoginMgr\.isAdmin\(|\w+\.isAdmin\(\)|isAdmin\w*\b(?!\())")
CUR_NEG = re.compile(r"!\s*Boolean\.TRUE\.equals\(\w+\.isUpload\w+\(|!\s*(?:LoginMgr\.isCurator\(|\w+\.isCurator\(\)|DescEditSafety\.canEdit\(|SpeciesListAccess\.canEdit\(|DescHistory\.canView\(|mayRead\()")
LOGIN_NEG = re.compile(r"\b(?:accessLogin|login|accessGroup)\w*\s*==\s*null|!\s*LoginMgr\.isLoggedIn\(")
SYSTEM = re.compile(r"isSecureCode|isPostInitializeCode|\bisScheduler\b|\bisSecure\b")
OWNER = re.compile(r"getId\(\)\s*!=(?!\s*null)|!\s*isOwn\w*|getCuratorId\(\)\s*!=|!\s*ownsIt")
DENY_BODY = re.compile(r"\breturn\b")
ACTION_VAR_DEF = re.compile(
    r"String\s+(\w+)\s*=\s*(?:\(String\)\s*\w+\.get\(\"action\"\)|\w+\.getAction\(\)|request\.getParameter\(\"action\"\))")
WRITE_SIGNS = re.compile(
    r"executeUpdate|\b(?:save|update|insert|delete|remove|set|unset|store|write|create|move|add|like)[A-Z]\w*\s*\(.*?accessLogin"
    r"|\w+Db\)?\.(?:save|update|insert|delete|remove|unset|set[A-Z]|store|create|move|add)\w*\("
    r"|new\s+File(?:Writer|OutputStream)|Files\.(?:write|copy|move|delete)|\.renameTo\(|\.mkdirs?\(|\.transferTo\("
    r"|Uploader\(|writeDataFile\(|LogMgr\.appendLog\(\"admin|Emailer\.send|sendMail\(")


def av_pattern(action_vars):
    """Regex alternatives for the action value: its variables, or form.getAction() used directly."""
    return "|".join(list(map(re.escape, action_vars)) + [r"\w+\.getAction\(\)"])


def split_top(cond, sep):
    parts, depth, cur, i = [], 0, [], 0
    while i < len(cond):
        c = cond[i]
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
        if depth == 0 and cond.startswith(sep, i):
            parts.append("".join(cur))
            cur = []
            i += len(sep)
            continue
        cur.append(c)
        i += 1
    parts.append("".join(cur))
    return [p.strip() for p in parts if p.strip()]


NEG_ANY = re.compile(r"!|==\s*null")
POS_PRED = [("admin", re.compile(r"(?<![!\w])\s*(?:LoginMgr\.isAdmin\(|\w+\.isAdmin\(\))")),
            ("curator", re.compile(r"(?<![!\w])\s*(?:LoginMgr\.isCurator\(|\w+\.isCurator\(\))")),
            ("logged-in", re.compile(r"(?<![!\w])\s*LoginMgr\.isLoggedIn\("))]


POS_PRED_FULL = [("admin", re.compile(r"LoginMgr\.isAdmin\(\w+\)|\w+\.isAdmin\(\)")),
                 ("curator", re.compile(r"LoginMgr\.isCurator\(\w+\)|\w+\.isCurator\(\)")),
                 ("logged-in", re.compile(r"LoginMgr\.isLoggedIn\(\w+\)|\w*[Ll]ogin\w*\s*!=\s*null"))]


def positive_guard(cond, action_vars):
    """if (isCurator && "x".equals(action)): sub-action x runs only for that role."""
    av = av_pattern(action_vars)
    lits = set(re.findall(r'(?<!!)"(\w+)"\.equals\((?:%s)\)' % av, cond)) | \
        set(re.findall(r'(?<![!\w])(?:%s)\.equals\("(\w+)"\)' % av, cond))
    if not lits or "||" in cond:
        return None
    for rule, rx in POS_PRED:
        for t in split_top(cond, "&&"):
            if rx.match(t.strip()) or rx.search(" " + t.strip()) and not t.strip().startswith("!"):
                return {"rule": rule, "system": False, "only": lits, "exclude": set(),
                        "conditional": False, "owner": False}
    return None


def classify_cond(cond, action_vars, sets, bools):
    """Read an if-condition guarding a return. Returns None when it is not an access gate,
    else dict(rule, system, only=set|None, exclude=set, conditional=bool, text)."""
    rule, system, conditional = None, False, False
    only, exclude = None, set()
    owner = False

    def lit_terms(term):
        pos, neg = set(), set()
        av = av_pattern(action_vars)
        for m in re.finditer(r'(!?)\s*"(\w+)"\.equals\((?:%s)\)|(!?)\s*(?:%s)\.equals\("(\w+)"\)' % (av, av), term):
            neg_flag = m.group(1) or m.group(3)
            val = m.group(2) or m.group(4)
            (neg if neg_flag else pos).add(val)
        for m in re.finditer(r"(!?)\s*(\w+)\.contains\((?:%s|requested)\)" % av, term):
            vals = sets.get(m.group(2), set())
            (neg if m.group(1) else pos).update(vals)
        for name, vals in bools.items():
            for m in re.finditer(r"(!?)\b%s\b" % re.escape(name), term):
                (neg if m.group(1) else pos).update(vals)
        return pos, neg

    terms = split_top(cond, "&&")
    found_access = False
    for t in terms:
        ors = split_top(t, "||") if "||" in t else [t]
        t_rule, t_sys, t_acc = None, False, False
        for o in ors:
            o_s = o.strip()
            while o_s.startswith("(") and matching(o_s, 0, "(", ")") == len(o_s):
                o_s = o_s[1:-1].strip()
            r = None
            if ADMIN_NEG.search(o_s) and not re.search(r"isUploadImages|isUploadSpecimens", o_s):
                r = "admin"
            elif CUR_NEG.search(o_s) or re.search(r"!\s*\(.*isUploadImages", o_s):
                r = "curator"
            elif LOGIN_NEG.search(o_s):
                r = "logged-in"
            if r:
                t_acc = True
                t_rule = r if t_rule is None or RANK[r] > RANK[t_rule] else t_rule
                continue
            if re.search(r"!\s*\w*\.?(?:%s)\(" % SYSTEM.pattern, o_s) or re.match(r"!\s*(?:isScheduler|isSecure)\b", o_s):
                t_sys = True
                t_acc = True
                continue
            if OWNER.search(o_s):
                owner = True
                t_acc = True
                continue
            if re.fullmatch(r"\w+\s*!=\s*null", o_s):
                continue
            pos, neg = lit_terms(o_s)
            if pos or neg:
                if pos:
                    only = (only or set()) | pos
                exclude |= neg
                continue
            conditional = True
        if t_acc:
            # Terms joined by && deny only when every one fails: passing any one is enough,
            # so the rule is the weakest of them (an owner check counts as logged-in).
            if owner and not t_rule and not t_sys:
                t_rule = "logged-in"
            if t_rule:
                if not found_access:
                    rule = t_rule
                elif rule is not None and RANK[t_rule] < RANK[rule]:
                    rule = t_rule
            if t_sys:
                system = True
            found_access = True
    if not found_access:
        return None
    if rule is None and owner:
        rule = "logged-in"
    if rule is None and system:
        rule = None
    return {"rule": rule, "system": system, "only": only, "exclude": exclude,
            "conditional": conditional, "owner": owner}


def analyse_class(cls, seen=None):
    """Access gates, action sub-values and write signals of an action class."""
    p = java_file(cls)
    res = {"gates": [], "subs": [], "writes": False, "file": None, "action_vars": [],
           "sets": {}, "deref": False, "postonly": False, "super": None}
    if not p:
        return res
    raw = p.read_text(encoding="utf-8", errors="replace")
    code = strip_java_comments(raw)
    res["file"] = str(p.relative_to(REPO))
    spans = method_spans(code)
    execute = [s for s in spans if s[0] == "execute"]

    action_vars = sorted(set(ACTION_VAR_DEF.findall(code)) | ({"requested"} if "String requested" in code else set()))
    res["action_vars"] = action_vars
    sets = {}
    for m in re.finditer(r"Set<String>\s+(\w+)\s*=\s*(?:java\.util\.)?Set\.of\((.*?)\);", code, re.S):
        sets[m.group(1)] = set(re.findall(r'"(\w+)"', m.group(2)))
    for m in re.finditer(r"(?:String\[\]|List<String>)\s+(\w+)\s*=\s*(?:new String\[\]\s*\{|Arrays\.asList\(|List\.of\()(.*?)[})];", code, re.S):
        sets.setdefault(m.group(1), set(re.findall(r'"(\w+)"', m.group(2))))
    res["sets"] = sets
    bools = {}
    av = av_pattern(action_vars)
    for m in re.finditer(r"boolean\s+(\w+)\s*=\s*([^;]*);", code):
        vals = set(re.findall(r'"(\w+)"\.equals\((?:%s)\)' % av, m.group(2)))
        if vals and not re.search(r"Login|isAdmin|isCurator", m.group(2)):
            bools[m.group(1)] = vals

    # Sub-actions: the ACTIONS set when the class has one, else every literal compared with the variable.
    subs = set()
    if action_vars:
        if "ACTIONS" in sets:
            subs = set(sets["ACTIONS"])
        else:
            for m in re.finditer(r'"(\w+)"\.equals\((?:%s)\)|\b(?:%s)\.equals(?:IgnoreCase)?\("(\w+)"\)' % (av, av), code):
                subs.add(m.group(1) or m.group(2))
            sw = re.search(r"switch\s*\(\s*(?:%s)\s*\)\s*\{" % av, code)
            if sw:
                body = code[sw.end() - 1:matching(code, sw.end() - 1, "{", "}")]
                subs |= set(re.findall(r'case\s+"(\w+)"\s*:', body))
            for name, vals in sets.items():
                if re.search(r"\b%s\.contains\((?:%s)\)" % (name, av), code):
                    subs |= vals
    res["subs"] = sorted(subs)

    # switch (action) { case "x": ... } blocks: a check inside a case applies to that case only.
    switches = []
    for sw in re.finditer(r"switch\s*\(\s*(?:%s)\s*\)\s*\{" % av, code):
        b0 = sw.end() - 1
        switches.append((b0, matching(code, b0, "{", "}")))

    def case_of(pos):
        for b0, b1 in switches:
            if b0 < pos < b1:
                labels = list(re.finditer(r'case\s+"(\w+)"\s*:|default\s*:', code[b0:pos]))
                if labels:
                    return labels[-1].group(1) or "default"
        return None

    # Gates: mustBeX(...) calls and if (...) { ... return ... } with an access predicate.
    for m in re.finditer(r"(?:LoginMgr\.must(BeAdmin|BeCurator|LogIn)|Check\.(admin|curator|login|initLogin|loginValid|initLoginValid|loginForFeature))\(", code):
        kind = m.group(1) or m.group(2)
        rule = {"BeAdmin": "admin", "BeCurator": "curator", "admin": "admin", "curator": "curator"}.get(kind, "logged-in")
        meth = next((s[0] for s in spans if s[1] <= m.start() < s[2]), None)
        # Conditional when it sits inside an if-block within the method.
        cond_ctx = enclosing_if(code, m.start(), spans)
        g = {"rule": rule, "system": False, "only": None, "exclude": set(), "conditional": False,
             "owner": False, "line": line_of(code, m.start()), "method": meth, "text": m.group(0)[:-1]}
        if cond_ctx is not None:
            c = classify_cond(cond_ctx, action_vars, sets, bools)
            if c is not None and c["only"] is None and not c["exclude"] and not c["conditional"]:
                # if (!isAdmin && !isScheduler) { denied = mustBeAdmin(); ... }: the if says who passes.
                g["rule"] = c["rule"] or g["rule"]
                g["system"] = c["system"]
            elif c is None:
                pos, neg = set(), set()
                for mm in re.finditer(r'(!?)\s*"(\w+)"\.equals\((?:%s)\)' % av, cond_ctx):
                    (neg if mm.group(1) else pos).add(mm.group(2))
                for mm in re.finditer(r"(!?)\s*(\w+)\.contains\((?:%s)\)" % av, cond_ctx):
                    (neg if mm.group(1) else pos).update(sets.get(mm.group(2), set()))
                if pos or neg:
                    g["only"] = pos or None
                    g["exclude"] = neg
                else:
                    g["conditional"] = True
            g["text"] += " (inside if)"
        res["gates"].append(g)
    for m in re.finditer(r"\bif\s*\(", code):
        p0 = m.end() - 1
        p1 = matching(code, p0, "(", ")")
        cond = re.sub(r"\s+", " ", code[p0 + 1:p1 - 1])
        rest = code[p1:]
        bm = re.match(r"\s*\{", rest)
        if bm:
            b0 = p1 + bm.end() - 1
            body = code[b0:matching(code, b0, "{", "}")]
        else:
            body = rest[:rest.find(";") + 1]
        # A return that sits behind a further if (if (!isAdmin) { d = Check.valid(); if (d != null) return d; })
        # depends on that other test, not on the access predicate.
        full_body = body
        first_ret = DENY_BODY.search(body)
        if first_ret and re.search(r"\bif\s*\(", body[:first_ret.start()]):
            body = ""
        if not DENY_BODY.search(body) or not NEG_ANY.search(cond):
            # A positive guard: if (LoginMgr.isCurator(request) && "recalc".equals(action)) { ... }
            pg = positive_guard(cond, action_vars)
            if pg is None and bm and any(rx.fullmatch(cond.strip()) for _, rx in POS_PRED_FULL):
                lits = set(re.findall(r'"(\w+)"\.equals\((?:%s)\)|(?:%s)\.equals\("(\w+)"\)' % (av, av), full_body))
                lits = {a or b for a, b in lits}
                if lits:
                    rule = next(r for r, rx in POS_PRED_FULL if rx.fullmatch(cond.strip()))
                    pg = {"rule": rule, "system": False, "only": lits, "exclude": set(),
                          "conditional": False, "owner": False}
            if pg:
                pg["line"] = line_of(code, m.start())
                pg["method"] = next((s[0] for s in spans if s[1] <= m.start() < s[2]), None)
                pg["text"] = cond[:140]
                res["gates"].append(pg)
            if not DENY_BODY.search(body):
                continue
        if re.search(r"mustBe|mustLogIn|Check\.\w+\(", cond):
            continue
        c = classify_cond(cond, action_vars, sets, bools)
        if c is None:
            continue
        if re.search(r"Csrf|isPost|confirm\b", cond) and c["rule"] is None and not c["system"]:
            continue
        c["line"] = line_of(code, m.start())
        c["method"] = next((s[0] for s in spans if s[1] <= m.start() < s[2]), None)
        c["text"] = cond[:140]
        outer = enclosing_if(code, m.start(), spans)
        if outer is not None:
            oc = classify_cond(outer, action_vars, sets, bools)
            pos = set(re.findall(r'(?<!!)"(\w+)"\.equals\((?:%s)\)' % av, outer))
            if pos and c["only"] is None:
                c["only"] = pos
            elif re.fullmatch(r"\s*\w*[Ll]ogin\w*\s*!=\s*null\s*", outer):
                c["login_ctx"] = True  # only checked for logged-in visitors
            elif not pos and oc is None:
                c["conditional"] = True
        res["gates"].append(c)

    for g in res["gates"]:
        if "line" in g and g.get("only") is None:
            pos = len("\n".join(code.split("\n")[:g["line"] - 1]))
            c = case_of(pos + 1)
            if c == "default":
                g["conditional"] = True
            elif c:
                g["only"] = {c}
                g["conditional"] = False
    res["postonly"] = bool(re.search(r"PostOnly\.confirm|isPost\(", code))
    res["writes"] = bool(WRITE_SIGNS.search(code))
    if execute:
        body = code[execute[0][1]:execute[0][2]]
        res["deref"] = bool(re.search(r"\baccessLogin\.get(?:Id|Name|GroupId)\(\)", body))

    sup = re.search(r"class\s+\w+\s+extends\s+([\w.]+)", code)
    if sup and sup.group(1) not in ("AntwebAction", "Action", "DispatchAction"):
        name = sup.group(1)
        pkg = cls.rsplit(".", 1)[0]
        cand = name if "." in name else pkg + "." + name
        if not java_file(cand):
            imp = re.search(r"import\s+([\w.]+\.%s);" % re.escape(name), code)
            cand = imp.group(1) if imp else None
            if not cand:
                for imp in re.finditer(r"import\s+([\w.]+)\.\*;", code):
                    if java_file(imp.group(1) + "." + name):
                        cand = imp.group(1) + "." + name
                        break
        seen = seen or set()
        if cand and java_file(cand) and cand not in seen:
            seen.add(cand)
            sres = analyse_class(cand, seen)
            res["super"] = cand
            for g in sres["gates"]:
                g = dict(g)
                g["text"] = "super " + cand.rsplit(".", 1)[1] + ": " + g["text"]
                # A superclass check runs only when the subclass calls that method.
                if g.get("method") != "execute":
                    g["conditional"] = True
                res["gates"].append(g)
            res["writes"] = res["writes"] or sres["writes"]
            if not res["subs"] and sres["subs"]:
                res["subs"] = sres["subs"]
                res["action_vars"] = sres["action_vars"]
    return res


def enclosing_if(code, pos, spans):
    """The condition of the innermost if-block containing pos within its method, or None."""
    meth = next((s for s in spans if s[1] <= pos < s[2]), None)
    if not meth:
        return None
    best = None
    for m in re.finditer(r"\b(?:if|else\s+if)\s*\(", code[meth[1]:pos]):
        p0 = meth[1] + m.end() - 1
        p1 = matching(code, p0, "(", ")")
        bm = re.match(r"\s*\{", code[p1:])
        if not bm:
            continue
        b0 = p1 + bm.end() - 1
        b1 = matching(code, b0, "{", "}")
        if b0 < pos < b1:
            best = re.sub(r"\s+", " ", code[p0 + 1:p1 - 1])
    return best


def rule_for(gates, sub=None):
    """Strongest unconditional rule of the gates that apply to this sub-action (or to the
    whole action when sub is None). Returns (rule, system, evidence, conditional_evidence)."""
    best, system, ev, cond_ev = None, False, [], []
    later = []
    for g in gates:
        if g.get("login_ctx"):
            later.append(g)
            continue
        applies = True
        if g["only"] is not None:
            applies = sub is not None and sub in g["only"]
        if sub is not None and sub in g["exclude"]:
            applies = False
        if sub is None and g["exclude"]:
            applies = False  # applies to every sub-action except some: not to the action as a whole
            cond_ev.append(g)
            continue
        if not applies:
            continue
        if g["conditional"] or (g.get("method") not in ("execute", None) and not re.match(r"(LoginMgr\.must|Check\.)", g["text"])):
            cond_ev.append(g)
            continue
        if g["rule"] and (best is None or RANK[g["rule"]] > RANK[best]):
            best = g["rule"]
        if g["system"]:
            system = True
        ev.append(g)
    # Checks inside if (accessLogin != null) { ... } count only where a login is already required.
    for g in later:
        if g["only"] is not None and (sub is None or sub not in g["only"]):
            continue
        if sub is not None and sub in g["exclude"]:
            continue
        if best is None or g["conditional"]:
            cond_ev.append(g)
            continue
        if g["rule"] and RANK[g["rule"]] > RANK[best]:
            best = g["rule"]
        ev.append(g)
    return best, system, ev, cond_ev


def describe(g):
    who = g["rule"] or ""
    if g["system"]:
        who = (who + "+system") if who else "system"
    if g.get("owner") and g["rule"] in (None, "logged-in"):
        who = (who + "+owner") if who else "owner"
    where = "L%d" % g["line"]
    if g.get("method") and g["method"] != "execute":
        where = g["method"] + "() " + where
    return "%s %s" % (who, where)


# ---------------------------------------------------------------- JSPs

CHECK_INCLUDES = {"/curate/adminCheck.jsp": "admin", "/curate/curatorCheck.jsp": "curator",
                  "/common/loginCheck.jsp": "logged-in"}
INCLUDE = re.compile(r'<%@\s*include\s+file="([^"]+)"|<jsp:include\s+page="([^"]+)"|<tiles:put\s+name="body-content"\s+value="([^"]+\.jsp)"')


def jsp_rule(web_path, depth=0, seen=None):
    """Access check included by a JSP (following includes): (rule, check file) or (None, None)."""
    seen = seen or set()
    if not web_path or not web_path.endswith(".jsp") or web_path in seen or depth > 3:
        return None, None
    seen.add(web_path)
    if web_path in CHECK_INCLUDES:
        return CHECK_INCLUDES[web_path], web_path.rsplit("/", 1)[1]
    f = WEB / web_path.lstrip("/")
    if not f.exists():
        return None, None
    text = re.sub(r"<%--.*?--%>|<!--.*?-->", "", f.read_text(encoding="utf-8", errors="replace"), flags=re.S)
    best = (None, None)
    for m in INCLUDE.finditer(text):
        inc = m.group(1) or m.group(2) or m.group(3)
        if not inc.startswith("/"):
            inc = str(Path(web_path).parent / inc)
            inc = "/" + inc.lstrip("/")
        r = jsp_rule(inc, depth + 1, seen)
        if r[0] and (best[0] is None or RANK[r[0]] > RANK[best[0]]):
            best = r
    return best


# ---------------------------------------------------------------- e2e tests

KEY_RE = re.compile(r"(?<![\w.])/?([a-z][A-Za-z0-9]*)\.do\b(\?[^\s\"'#<>]*)?")
API_RE = re.compile(r"/v3\.1/(\w+)")


def py_strings(node):
    out = []
    for n in ast.walk(node):
        if isinstance(n, ast.Constant) and isinstance(n.value, str):
            out.append(n.value)
        elif isinstance(n, ast.JoinedStr):
            out.append("".join(v.value if isinstance(v, ast.Constant) else "{}" for v in n.values))
    return out


def py_names(node):
    return {n.id for n in ast.walk(node) if isinstance(n, ast.Name)} | \
           {n.func.id for n in ast.walk(node) if isinstance(n, ast.Call) and isinstance(n.func, ast.Name)}


def action_dicts(node):
    """Values given for an "action" key in dict literals or keyword arguments."""
    vals = set()
    for n in ast.walk(node):
        if isinstance(n, ast.Dict):
            for k, v in zip(n.keys, n.values):
                if isinstance(k, ast.Constant) and k.value == "action" and isinstance(v, ast.Constant) and isinstance(v.value, str):
                    vals.add(v.value)
    return vals


def scan_e2e(known_paths, known_subs, api_names):
    """{key: set(test ids)} for functional coverage, {path: probe kind} for the anonymous probes."""
    hits, probe = {}, {}
    if not E2E.exists():
        return hits, probe
    modules = {}
    for f in sorted(E2E.glob("*.py")):
        try:
            modules[f.stem] = ast.parse(f.read_text(encoding="utf-8"))
        except SyntaxError:
            continue
    # Module-level constants and functions, by module.
    consts, funcs = {}, {}
    for mod, tree in modules.items():
        consts[mod], funcs[mod] = {}, {}
        for node in tree.body:
            if isinstance(node, (ast.Assign, ast.AnnAssign)):
                targets = node.targets if isinstance(node, ast.Assign) else [node.target]
                for t in targets:
                    if isinstance(t, ast.Name):
                        consts[mod][t.id] = node.value
            elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                funcs[mod][node.name] = node
            elif isinstance(node, ast.ClassDef):
                for sub in node.body:
                    if isinstance(sub, (ast.FunctionDef, ast.AsyncFunctionDef)):
                        funcs[mod][node.name + "." + sub.name] = sub
    imports = {}
    for mod, tree in modules.items():
        imports[mod] = {}
        for node in tree.body:
            if isinstance(node, ast.ImportFrom) and node.module in modules:
                for a in node.names:
                    imports[mod][a.asname or a.name] = (node.module, a.name)

    def resolve(mod, name, depth=0):
        """Strings reachable from a module-level name (constant or helper function)."""
        if depth > 4:
            return [], set()
        if name in imports.get(mod, {}):
            m2, n2 = imports[mod][name]
            return resolve(m2, n2, depth + 1)
        node = consts[mod].get(name) or funcs[mod].get(name)
        if node is None:
            return [], set()
        strs, acts = py_strings(node), action_dicts(node)
        for n in py_names(node) - {name}:
            if n in consts[mod] or n in funcs[mod] or n in imports[mod]:
                s2, a2 = resolve(mod, n, depth + 1)
                strs += s2
                acts |= a2
        return strs, acts

    expectations = {}
    exp_file = E2E / "expectations.json"
    if exp_file.exists():
        expectations = json.loads(exp_file.read_text())
    write_actions = set()
    if "policy" in consts and "WRITE_ACTIONS" in consts["policy"]:
        write_actions = set(py_strings(consts["policy"]["WRITE_ACTIONS"]))
    never = set()
    if "policy" in consts and "NEVER" in consts["policy"]:
        node = consts["policy"]["NEVER"]
        if isinstance(node, ast.Dict):
            never = {k.value for k in node.keys if isinstance(k, ast.Constant)}

    def add(key, tid):
        hits.setdefault(key, set()).add(tid)

    for mod in modules:
        if not mod.startswith("test_"):
            continue
        for fname, node in funcs[mod].items():
            short = fname.rsplit(".", 1)[-1]
            if not short.startswith("test"):
                continue
            tid = "%s.py::%s" % (mod, short)
            # Special parametrized tests driven by expectations.json / policy.py.
            if mod == "test_actions" and short == "test_action_anonymous":
                for p in expectations.get("actions", {}):
                    probe.setdefault(p, set()).add("anon")
                continue
            if mod == "test_actions" and short == "test_write_action_requires_login":
                for p in write_actions:
                    probe.setdefault(p, set()).add("write-probe")
                continue
            if mod == "test_actions" and short == "test_public_page":
                for url, cat in expectations.get("pages", {}).items():
                    if cat in ("ok", "login"):
                        for m in KEY_RE.finditer(url):
                            if "/" + m.group(1) in known_paths:
                                add("/" + m.group(1), tid)
                        if url in ("", "/"):
                            add("/", tid)
                continue
            if mod == "test_actions" and short == "test_every_action_has_a_decision":
                continue
            strs = py_strings(node)
            acts = action_dicts(node)
            for n in py_names(node):
                s2, a2 = resolve(mod, n)
                strs += s2
                acts |= a2
            paths_here = set()
            for s in strs:
                for m in KEY_RE.finditer(s):
                    path = "/" + m.group(1)
                    if path not in known_paths:
                        continue
                    paths_here.add(path)
                    q = m.group(2) or ""
                    am = re.search(r"[?&]action=(\w+)", q)
                    if am and (path, am.group(1)) in known_subs:
                        add("%s.do?action=%s" % (path, am.group(1)), tid)
                    elif not am:
                        add(path, tid)
                for m in API_RE.finditer(s):
                    if m.group(1) in api_names:
                        add("GET /v3.1/" + m.group(1), tid)
                if s.startswith("/v3.1/") and s.split("?")[0] == "/v3.1/":
                    add("GET /v3.1/", tid)
            for path in paths_here:
                for a in acts:
                    if (path, a) in known_subs:
                        add("%s.do?action=%s" % (path, a), tid)
    for p in never:
        probe.setdefault(p, set()).add("never")
    return hits, probe


# ---------------------------------------------------------------- API

def api_routes():
    if not API.exists():
        return []
    text = API.read_text(encoding="utf-8", errors="replace")
    out = []
    for m in re.finditer(r"@\w+\.route\(\s*['\"]([^'\"]+)['\"](?:\s*,\s*methods\s*=\s*\[([^\]]*)\])?\s*\)\s*\ndef\s+(\w+)", text):
        methods = re.findall(r"['\"](\w+)['\"]", m.group(2) or "") or ["GET"]
        out.append((m.group(1), methods, m.group(3)))
    return out


# ---------------------------------------------------------------- traffic

def read_traffic(path):
    """{key: total} from traffic-by-action.sh output (only the first two columns are used)."""
    t = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            if line.startswith("#") or line.startswith("key\t"):
                continue
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 2:
                continue
            try:
                total = int(parts[1])
            except ValueError:
                continue
            key = parts[0]
            if key.startswith("/v3.1"):
                name = key[len("/v3.1"):].strip("/")
                key = "GET /v3.1/" + name
            t[key] = t.get(key, 0) + total
    return t


# ---------------------------------------------------------------- build

def build(traffic=None, traffic_absent="0", removed=None):
    """removed: {key: row} of rows marked "removed" in actions.csv. A removed action or sub-action
    that is no longer in the code keeps its row (status removed), with its e2e tests and traffic
    brought up to date, so the catalog records what was taken out and the test that checks it."""
    removed = removed or {}
    actions = struts_actions()
    known_paths = {a["path"] for a in actions}
    removed_paths = {k for k, r in removed.items() if r.get("kind") == "action"}
    analyses = {}
    for a in actions:
        if a["type"] and a["type"] not in analyses:
            analyses[a["type"]] = analyse_class(a["type"])
    known_subs = set()
    for a in actions:
        an = analyses.get(a["type"])
        if an:
            for s in an["subs"]:
                known_subs.add((a["path"], s))
    for k, r in removed.items():
        if r.get("kind") == "sub-action" and ".do?action=" in k:
            path, sub = k.split(".do?action=", 1)
            known_subs.add((path, sub))
    routes = api_routes()
    api_names = {r[0].strip("/") for r in routes if r[0] != "/"}
    hits, probe = scan_e2e(known_paths | removed_paths, known_subs, api_names)

    exp = {}
    exp_file = E2E / "expectations.json"
    if exp_file.exists():
        exp = json.loads(exp_file.read_text()).get("actions", {})

    rows = []
    for a in sorted(actions, key=lambda x: x["path"].lower()):
        path = a["path"]
        url = action_url(path)
        jsps = []
        for _, fp in a["forwards"]:
            if fp and fp not in jsps:
                jsps.append(fp)
        if a["forward"]:
            jsps = [a["forward"]]
        # JSP rule: the check in the "success" forward's JSP when there is one, else the weakest
        # check over the action's JSP forwards (each must have one).
        succ = [fp for n, fp in a["forwards"] if n == "success" and fp and fp.endswith(".jsp")]
        cand = succ or [fp for fp in jsps if fp.endswith(".jsp")]
        jrules = [jsp_rule(fp) for fp in cand]
        jsp_r, jsp_file = None, None
        if jrules and all(r[0] for r in jrules):
            jsp_r, jsp_file = min(jrules, key=lambda r: RANK[r[0]])
        an = analyses.get(a["type"]) if a["type"] else None
        if a["unknown"]:
            rule, where, ev_txt, writes = "public", "-", "unknown-path handler (404 page)", "no"
        else:
            rule, where, ev_txt, writes = access_summary(an, None, jsp_r, jsp_file)
        row = base_row(path, url, "action", a["type"], jsps, rule, where, ev_txt, writes,
                       exp.get(path, ""), sorted(hits.get(path, ())), probe.get(path, set()))
        rows.append(row)
        if an:
            for s in an["subs"]:
                key = "%s.do?action=%s" % (path, s)
                srule, swhere, sev, _ = access_summary(an, s, jsp_r, jsp_file)
                rows.append(base_row(key, key, "sub-action", a["type"], [], srule, swhere, sev, writes,
                                     "", sorted(hits.get(key, ())), set()))
    # A plain forward to another action (an alias) has that action's access rule.
    by_url = {r["url"]: r for r in rows if r["kind"] == "action"}
    for r in rows:
        if r["kind"] == "action" and not r["class"] and r["jsps"].endswith(".do") and r["jsps"] in by_url:
            t = by_url[r["jsps"]]
            r["access"], r["access_where"] = t["access"], t["access_where"]
            r["access_evidence"] = "alias of " + r["jsps"]
    for route, methods, func in routes:
        key = "%s /v3.1%s" % ("/".join(methods), route if route != "/" else "/")
        name = route.strip("/")
        tkey = "GET /v3.1/" + name if name else "GET /v3.1/"
        rows.append(base_row(key, "/v3.1" + route, "api", "api/v3.1/api.py:" + func, [], "public", "-",
                             "API (read-only, no login)", "no", "", sorted(hits.get(tkey, ())), set()))
    present = {r["key"] for r in rows}
    gone = []
    for k, old in removed.items():
        if k in present or old.get("kind") not in ("action", "sub-action"):
            continue
        r = {c: old.get(c, "") for c in COLUMNS}
        r["e2e_tests"] = " ".join(sorted(hits.get(k, ())))
        r["e2e_probe"] = ",".join(sorted(probe.get(k, set())))
        r["access"], r["access_where"], r["access_evidence"] = "removed", "-", "no longer in the code"
        r["writes"], r["anon_result"] = "", ""
        gone.append(r)
    if gone:
        api = [r for r in rows if r["kind"] == "api"]
        rest = [r for r in rows if r["kind"] != "api"] + gone

        def order(r):
            base = r["key"].split(".do?action=", 1)[0] if r["kind"] == "sub-action" else r["key"]
            return (base.lower(), 0 if r["kind"] == "action" else 1, r["key"])
        rows = sorted(rest, key=order) + api
    if traffic is not None:
        for r in rows:
            if r["kind"] == "api":
                name = r["url"][len("/v3.1"):].strip("/")
                tk = "GET /v3.1/" + name
                r["hits"] = str(traffic[tk]) if tk in traffic else traffic_absent
                continue
            tk = r["url"] if r["kind"] == "action" else r["key"]
            n = traffic.get(tk)
            if r["kind"] == "action" and r["url"] != "/":
                subs = [v for k, v in traffic.items() if k.startswith(r["url"] + "?action=")]
                if subs:
                    n = (n or 0) + sum(subs)
            r["hits"] = str(n) if n is not None else traffic_absent
    return rows


def access_summary(an, sub, jsp_r, jsp_file):
    if an is None:
        rule, system, ev, cond = None, False, [], []
        writes = "no"
    else:
        rule, system, ev, cond = rule_for(an["gates"], sub)
        writes = "yes" if an["writes"] else "no"
    where = []
    if rule or system:
        where.append("class")
    use_jsp = sub is None and jsp_r
    if use_jsp:
        where.append("jsp")
        if rule is None or RANK[jsp_r] > RANK[rule]:
            rule = jsp_r
    parts = [describe(g) for g in ev]
    if use_jsp:
        parts.append(jsp_file)
    if cond:
        parts.append("conditional: " + ", ".join(describe(g) for g in cond[:4]))
    if an and an["postonly"]:
        parts.append("POST-only changes")
    if an and an["deref"] and not rule and not system:
        parts.append("uses accessLogin without a check")
    if rule and system:
        label = rule + " or system"
    elif not rule and not system and cond and any(g["rule"] or g["system"] for g in cond):
        label = "partial"
    elif system:
        label = "system"
    elif rule:
        label = rule
    else:
        label = "public" if writes == "no" else "no check found"
    if label == "public" and an and an["deref"]:
        label = "no check found"
    return label, "+".join(where) or "-", "; ".join(parts), writes


def base_row(key, url, kind, cls, jsps, rule, where, ev, writes, anon, tests, probe):
    return {
        "key": key, "kind": kind, "url": url, "class": (cls or "").replace("org.calacademy.antweb.", ""),
        "jsps": " ".join(jsps), "access": rule, "access_where": where, "access_evidence": ev,
        "writes": writes, "anon_result": anon, "e2e_tests": " ".join(tests),
        "e2e_probe": ",".join(sorted(probe)), "hits": "",
        "purpose": "", "status": "", "notes": "",
    }


def read_existing(path):
    if not path.exists():
        return {}
    with open(path, encoding="utf-8", newline="") as fh:
        return {r["key"]: r for r in csv.DictReader(fh)}


def write_csv(rows, path):
    buf = io.StringIO()
    w = csv.DictWriter(buf, fieldnames=COLUMNS, lineterminator="\n")
    w.writeheader()
    for r in rows:
        w.writerow({c: r.get(c, "") for c in COLUMNS})
    path.write_text(buf.getvalue(), encoding="utf-8")


def md_cell(s):
    return (s or "").replace("|", "\\|").replace("\n", " ")


def write_md(rows, path, traffic_label):
    n_act = sum(1 for r in rows if r["kind"] == "action")
    n_sub = sum(1 for r in rows if r["kind"] == "sub-action")
    n_api = sum(1 for r in rows if r["kind"] == "api")
    out = ["# AntWeb function catalog", "",
           "Generated by `tools/catalog/build_catalog.py` from struts-config.xml, the Java sources, the JSPs, "
           "tests/e2e and api/v3.1/api.py. Do not edit this file: edit the purpose, status and notes columns "
           "in `actions.csv` and run the script again (they are kept by key).", "",
           "%d actions, %d sub-actions, %d API routes." % (n_act, n_sub, n_api), "",
           "**Hits**: %s" % (traffic_label or "not loaded"), "",
           "## Columns", "",
           "- **Access**: the check the generator found. `public` (no check, no sign of writing data), "
           "`logged-in`, `curator`, `admin`, `system` (the nightly scheduler's secure code or AntWeb's own call), "
           "`admin or system`, `partial` (checks found only on some paths, e.g. the save branch of a public page; "
           "read the evidence), `no check found` (the class shows signs of changing data or uses the login "
           "but no check was found). Where: `class` (Java), `jsp` (an included adminCheck/curatorCheck/loginCheck; "
           "it runs after the Java class). This is a static reading of the code; `conditional` checks only "
           "apply on some paths.",
           "- **Anon**: what an anonymous request without parameters gets on staging (tests/e2e/expectations.json).",
           "- **E2E**: functional end-to-end tests that request this URL. `probe` = only the anonymous probe "
           "in test_actions.py (bare URL, no login); `never` = listed in policy.NEVER (not requested by the suite).",
           "- **Status**: keep, check (cannot tell from the code), remove? (looks unused; reason in notes), "
           "removed (taken out; the row is kept with access `removed` and the e2e test that checks it stays gone).", ""]
    sections = [("Struts actions and sub-actions", ("action", "sub-action")), ("API v3.1", ("api",))]
    for title, kinds in sections:
        out += ["## " + title, "",
                "| URL | Class | JSPs | Access | Where | Anon | E2E | Hits | Purpose | Status | Notes |",
                "|---|---|---|---|---|---|---|---|---|---|---|"]
        for r in rows:
            if r["kind"] not in kinds:
                continue
            url = r["url"] if r["kind"] != "sub-action" else "&nbsp;&nbsp;`?action=%s`" % r["key"].split("action=", 1)[1]
            if r["kind"] != "sub-action":
                url = "`%s`" % url
            tests = r["e2e_tests"].split()
            e2e = "%d: %s" % (len(tests), ", ".join(t.split("::")[1] for t in tests[:3]) + (" ..." if len(tests) > 3 else "")) if tests else ""
            if not tests and r["e2e_probe"]:
                e2e = r["e2e_probe"].replace("anon", "probe")
            hits = r["hits"]
            out.append("| " + " | ".join(md_cell(x) for x in [
                url, r["class"], " ".join("`%s`" % j for j in r["jsps"].split()), r["access"],
                r["access_where"], r["anon_result"], e2e, hits, r["purpose"], r["status"], r["notes"]]) + " |")
        out.append("")
    path.write_text("\n".join(out), encoding="utf-8")


TIERS = [
    (1, "Uploads", "specimen, species list, image and name uploads"),
    (2, "Curator and admin saves", "pages and sub-actions that change data"),
    (3, "Login, accounts and passwords", ""),
    (4, "Nightly and system jobs", "the scheduler's data jobs and the calls the host and the app make"),
    (5, "Public pages", "ordered by traffic"),
    (6, "Other curator and admin pages", "read-only tools and reports"),
]
ACCOUNT = {"/login", "/logout", "/forgotPassword", "/resetPassword", "/editLogin", "/saveLogin", "/loginAs",
           "/passwordResetCampaign", "/inactiveAccounts", "/editSession", "/antwebInvite", "/newLogin", "/viewLogin",
           "/viewLogins"}
SYSTEM_KEYS = ("/utilData", "/schedule", "/util.do?action=postInstantiate", "/util.do?action=isRestart", "/uptime")
SAVE_SUB = re.compile(r"action=(save|delete|deleteTaxon|remove|removeAll|confirm|mark|send|transfer|moveOrDelete|recalc|setDebug|toggleDownTime|email|refresh)$")
UPLOAD_KEYS = re.compile(r"^/(upload\.do|imageUploader|imageUploadBatch|descEditImageUpload|worldAuthoritySave|validateSpeciesList|teamMemberImgUpload)")


def tier(r):
    key = r["key"]
    base = key.split(".do")[0]
    if UPLOAD_KEYS.match(key) or key == "/upload":
        return 1
    if base in ACCOUNT or key.startswith("/passwordResetCampaign"):
        return 3
    if key.startswith(SYSTEM_KEYS):
        return 4
    if r["kind"] == "sub-action" and SAVE_SUB.search(key):
        return 2
    if r["kind"] == "action" and r["writes"] == "yes" and r["access"] not in ("public", "partial", "no check found"):
        return 2
    if r["access"] in ("public", "partial") or r["kind"] == "api":
        return 5
    return 6


# Within a tier, these come first (most used or most damaging when broken), then by traffic.
PRIORITY = [r"action=specimenUpload$", r"action=taxonWorksUpload$", r"action=reloadSpecimenList$",
            r"action=removeSpecimenList$", r"action=uploadSpeciesList$", r"action=fetchAndReloadWorldants$",
            r"^/descEditImageUpload$", r"^/(editProject|editGeolocale|defaultSpecimen|deleteImages|artist|bigPicture)$",
            r"^/speciesList(Tool|Move|History)\.do\?action=(save|confirm)$", r"action=(save|delete|deleteTaxon|transfer|moveOrDelete|remove)$",
            r"^/(resetPassword|forgotPassword|login|logout|editLogin|saveLogin)$",
            r"action=set[1-5]$", r"action=(taxonWorksNightly|worldantsFetchAndReload)$", r"action=(postInstantiate|isRestart)$",
            r"action=recalc$"]


def priority(r):
    for i, rx in enumerate(PRIORITY):
        if re.search(rx, r["key"]):
            return i if not rx.endswith("recalc$") else 99
    return 50


def hits_num(r):
    h = r.get("hits", "")
    try:
        return int(h)
    except ValueError:
        return 0


def write_gaps(rows, path, traffic_label):
    gaps = [r for r in rows if not r["e2e_tests"] and r["status"] not in ("remove?", "removed") and r["key"] != "/notFound"]
    out = ["# E2E coverage gaps", "",
           "Generated by `tools/catalog/build_catalog.py` with the catalog. Actions, sub-actions and API routes that no "
           "functional end-to-end test requests, most important first. \"Probe\" means the only test is "
           "test_actions.py's anonymous request without parameters (it shows the page answers and asks for a login "
           "where it should, not that the function works). Rows marked remove? or removed in the catalog are left out.", "",
           "Hits: %s." % (traffic_label or "not loaded"), "",
           "%d of %d rows have no functional e2e test." % (len(gaps), len(rows)), ""]
    n = 0
    for t, title, desc in TIERS:
        rs = sorted([r for r in gaps if tier(r) == t], key=lambda r: (priority(r), -hits_num(r), r["key"].lower()))
        if not rs:
            continue
        out += ["## %d. %s (%d)" % (t, title, len(rs)), ""]
        if desc:
            out += [desc[0].upper() + desc[1:] + ".", ""]
        out += ["| # | URL | Access | Hits | Probe | Purpose | Status |", "|---|---|---|---|---|---|---|"]
        for r in rs:
            n += 1
            out.append("| %d | `%s` | %s | %s | %s | %s | %s |" % (
                n, md_cell(r["url"] if r["kind"] == "action" else r["key"]), md_cell(r["access"]), md_cell(r["hits"]),
                md_cell(r["e2e_probe"].replace("anon", "probe")), md_cell(r["purpose"]), md_cell(r["status"])))
        out.append("")
    path.write_text("\n".join(out), encoding="utf-8")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--traffic", help="output of tools/traffic-by-action.sh (tab-separated)")
    ap.add_argument("--traffic-label", help='where the traffic came from, e.g. "Caddy log 2026-09-18 to 2026-10-18"')
    ap.add_argument("--traffic-min", type=int, default=0,
                    help="the traffic file leaves out keys below this count: show them as <N instead of 0")
    ap.add_argument("--out-dir", default=str(OUT))
    ap.add_argument("--check", action="store_true",
                    help="do not write; fail if a struts-config action or sub-action has no row in actions.csv")
    args = ap.parse_args()
    out_dir = Path(args.out_dir)
    csv_path = out_dir / "actions.csv"
    existing = read_existing(csv_path)

    traffic = read_traffic(args.traffic) if args.traffic else None
    absent = "<%d" % args.traffic_min if args.traffic_min else "0"
    removed = {k: r for k, r in existing.items() if r.get("status") == "removed"}
    rows = build(traffic, absent, removed)

    if args.check:
        keys = {r["key"] for r in rows}
        missing = [r["key"] for r in rows if r["kind"] in ("action", "sub-action") and r["key"] not in existing]
        stale = [k for k, r in existing.items() if r.get("kind") in ("action", "sub-action") and k not in keys
                 and r.get("status") != "removed"]
        nopurpose = [k for k, r in existing.items() if r.get("kind") == "action" and not r.get("purpose")]
        for k in missing:
            print("::error::%s has no row in docs/catalog/actions.csv" % k)
        for k in stale:
            print("::warning::%s is in docs/catalog/actions.csv but no longer in the code" % k)
        for k in nopurpose:
            print("::warning::%s has no purpose in docs/catalog/actions.csv" % k)
        if missing:
            print("Run: python3 tools/catalog/build_catalog.py, fill in the purpose and status of the new rows, "
                  "and commit docs/catalog/.", file=sys.stderr)
            return 1
        print("catalog check: %d rows, every action and sub-action has a row" % len(rows))
        return 0

    label = args.traffic_label
    for r in rows:
        old = existing.get(r["key"])
        if old:
            for c in HAND:
                r[c] = old.get(c, "")
            if traffic is None:
                r["hits"] = old.get("hits", "")
    gone = [k for k in existing if k not in {r["key"] for r in rows} and any(existing[k].get(c) for c in HAND)]
    for k in gone:
        print("dropped annotated row (no longer in the code): %s" % k, file=sys.stderr)
    if label is None:
        lab_file = out_dir / ".traffic-label"
        if traffic is None and lab_file.exists():
            label = lab_file.read_text().strip()
        elif traffic is not None:
            label = Path(args.traffic).name
    out_dir.mkdir(parents=True, exist_ok=True)
    if label:
        (out_dir / ".traffic-label").write_text(label + "\n")
    write_csv(rows, csv_path)
    write_md(rows, out_dir / "actions.md", label)
    write_gaps(rows, out_dir / "coverage-gaps.md", label)
    print("wrote %s (%d rows), actions.md and coverage-gaps.md" % (csv_path.relative_to(REPO) if csv_path.is_relative_to(REPO) else csv_path, len(rows)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
