"""Survey a running AntWeb: request every Struts action anonymously, crawl public
pages for real example URLs, call the API, and report what each one returns.

Used to record the baseline (expectations.json) and to see what changed.
Output: tests/e2e/out/survey.json and a Markdown summary (also written to the
GitHub Actions job summary).

    ANTWEB_URL=https://staging.antweb.org python tests/e2e/survey.py
"""
import json
import os
import re
import sys
import time
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.parse import urljoin, urlparse

sys.path.insert(0, str(Path(__file__).parent))
from antweb import BASE_URL, BROKEN, IS_PRODUCTION, get, new_session, struts_actions  # noqa: E402
from policy import NEVER, SEEDS, WRITE_ACTIONS  # noqa: E402
from api_cases import API_CASES  # noqa: E402
from fingerprint import KEY_PAGES, fingerprint  # noqa: E402

OUT = Path(__file__).parent / "out"
CRAWL_BUDGET = int(os.environ.get("ANTWEB_CRAWL_BUDGET", "300"))
PER_ACTION = int(os.environ.get("ANTWEB_SAMPLES_PER_ACTION", "3"))
PROBE_WRITES = os.environ.get("ANTWEB_PROBE_WRITE_ACTIONS") == "1" and not IS_PRODUCTION
WORKERS = int(os.environ.get("ANTWEB_WORKERS", "4"))
HREF = re.compile(r'href\s*=\s*["\']([^"\'#]+)', re.I)


def action_of(url: str) -> str | None:
    p = urlparse(url).path
    if p in ("", "/"):
        return "/"
    return p[:-3] if p.endswith(".do") else None


def main() -> int:
    OUT.mkdir(exist_ok=True)
    actions = struts_actions()
    session = new_session()
    results = {"base_url": BASE_URL, "time": time.strftime("%Y-%m-%d %H:%M:%S %Z"),
               "actions": {}, "pages": {}, "api": {}}

    # 1. Every action, anonymously, no parameters.
    def probe(a):
        if a.path in NEVER:
            return a.path, {"category": "skipped", "detail": NEVER[a.path]}
        if a.path in WRITE_ACTIONS and not PROBE_WRITES:
            return a.path, {"category": "skipped", "detail": "write action; set ANTWEB_PROBE_WRITE_ACTIONS=1 on staging"}
        try:
            o = get(new_session(), a.url)
            return a.path, o.__dict__
        except Exception as e:  # timeouts, connection resets
            return a.path, {"category": "request_failed", "detail": str(e)[:200]}

    with ThreadPoolExecutor(WORKERS) as ex:
        for path, res in ex.map(probe, actions):
            results["actions"][path] = res

    # 2. Crawl public pages for real example URLs (anonymous, GET only).
    blocked = set(NEVER) | set(WRITE_ACTIONS)
    queue, seen, per_action = list(SEEDS), set(), defaultdict(int)
    while queue and len(results["pages"]) < CRAWL_BUDGET:
        url = queue.pop(0)
        if url in seen:
            continue
        seen.add(url)
        act = action_of(url)
        if act is None or act in blocked or per_action[act] >= PER_ACTION:
            continue
        per_action[act] += 1
        try:
            resp = session.get(BASE_URL + url, timeout=60)
            from antweb import classify
            o = classify(resp)
            results["pages"][url] = o.__dict__
            if o.category == "ok":
                for href in HREF.findall(re.sub(r"<!--.*?-->", "", resp.text, flags=re.S)):
                    absu = urljoin(BASE_URL + url, href.strip())
                    pu = urlparse(absu)
                    if pu.netloc and pu.netloc != urlparse(BASE_URL).netloc:
                        continue
                    rel = pu.path + (("?" + pu.query) if pu.query else "")
                    if action_of(rel) and rel not in seen:
                        queue.append(rel)
        except Exception as e:
            results["pages"][url] = {"category": "request_failed", "detail": str(e)[:200]}

    # 3. Content fingerprints of key pages (tiers 2 and 5).
    snapshots = {}
    for url in KEY_PAGES:
        try:
            r = new_session().get(BASE_URL + url, timeout=120)
            snapshots[url] = {"status": r.status_code, **fingerprint(r.text)}
        except Exception as e:
            snapshots[url] = {"status": 0, "detail": str(e)[:200]}
    (OUT / "snapshots.json").write_text(json.dumps(snapshots, indent=1, sort_keys=True) + "\n")

    # 4. API v3.1.
    for name, url in API_CASES.items():
        try:
            r = session.get(BASE_URL + url, timeout=120)
            info = {"status": r.status_code, "bytes": len(r.content)}
            try:
                data = r.json()
                info["json"] = True
                info["shape"] = type(data).__name__
                if isinstance(data, dict):
                    info["keys"] = sorted(list(data.keys()))[:10]
            except ValueError:
                info["json"] = False
                info["start"] = r.text[:120]
            results["api"][name] = info
        except Exception as e:
            results["api"][name] = {"status": 0, "detail": str(e)[:200]}

    (OUT / "survey.json").write_text(json.dumps(results, indent=1, sort_keys=True))
    # Candidate baseline: what each action and crawled page returns today.
    baseline = {
        "recorded_from": BASE_URL, "recorded_at": results["time"],
        "actions": {p: r["category"] for p, r in sorted(results["actions"].items()) if r["category"] != "skipped"},
        "pages": {u: r["category"] for u, r in sorted(results["pages"].items())},
        "api": {n: r.get("status") for n, r in sorted(results["api"].items())},
    }
    (OUT / "expectations.json").write_text(json.dumps(baseline, indent=1, sort_keys=True) + "\n")
    md = summary(results)
    print(md)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as f:
            f.write(md)
    broken = [p for p, r in results["actions"].items() if r["category"] in BROKEN] + \
             [u for u, r in results["pages"].items() if r["category"] in BROKEN]
    return 1 if broken and os.environ.get("ANTWEB_SURVEY_STRICT") == "1" else 0


def summary(results) -> str:
    lines = [f"## AntWeb survey of {results['base_url']} ({results['time']})", ""]
    ac = Counter(r["category"] for r in results["actions"].values())
    pc = Counter(r["category"] for r in results["pages"].values())
    lines += [f"Actions: {len(results['actions'])} " + ", ".join(f"{k} {v}" for k, v in sorted(ac.items())),
              f"Crawled pages: {len(results['pages'])} " + ", ".join(f"{k} {v}" for k, v in sorted(pc.items())), ""]
    lines += ["### Actions (anonymous, no parameters)", "", "| Action | Result | Status | Final URL | Title |", "|---|---|---|---|---|"]
    for p, r in sorted(results["actions"].items()):
        lines.append(f"| {p} | {r['category']} | {r.get('status','')} | {r.get('final_url','')[:60]} | {r.get('title', r.get('detail',''))[:60]} |")
    bad = {u: r for u, r in results["pages"].items() if r["category"] not in ("ok", "login")}
    lines += ["", f"### Crawled pages that were not ok ({len(bad)})", "", "| URL | Result | Detail |", "|---|---|---|"]
    for u, r in sorted(bad.items()):
        lines.append(f"| {u[:100]} | {r['category']} | {(r.get('detail') or r.get('title',''))[:80]} |")
    lines += ["", "### Crawled pages (ok)", ""]
    lines += [", ".join(sorted(u for u, r in results["pages"].items() if r["category"] == "ok"))[:20000]]
    lines += ["", "### API", "", "| Case | Status | JSON | Bytes | Shape |", "|---|---|---|---|---|"]
    for n, r in sorted(results["api"].items()):
        lines.append(f"| {n} | {r.get('status')} | {r.get('json','')} | {r.get('bytes','')} | {r.get('shape', r.get('start', r.get('detail','')))} {r.get('keys','')} |")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    sys.exit(main())
