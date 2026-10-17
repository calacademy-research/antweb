#!/usr/bin/env bash
# traffic-by-action.sh: count AntWeb requests per action or page, from the access
# logs already kept on the production host. Read-only: it only reads log files
# (and, with -s app, runs "cat" inside the antweb container). Nothing is changed.
#
# Run on antweb-prod as root:
#   bash ~/antweb/tools/traffic-by-action.sh                 # Caddy log (default)
#   bash ~/antweb/tools/traffic-by-action.sh -s tomcat       # Tomcat log (longer history, no user agent)
#   bash ~/antweb/tools/traffic-by-action.sh -s app          # AntWeb's own accessLog.txt (.do only)
#   bash ~/antweb/tools/traffic-by-action.sh -o /root/traffic-caddy.tsv
#
# Sources (all on the host unless noted):
#   caddy   ~/antweb/docker/caddy/logs/access*.log[.gz]   JSON, has user agent and status.
#           Rolls at 100 MiB, keeps 10 files, so it covers days to weeks.
#   tomcat  ~/antweb/logs/localhost_access_log.*.txt[.gz]   one file per day, kept until deleted.
#           No user agent; bots on Caddy's list never get here (Caddy answers them 403).
#           Also has the server's own calls (nightly jobs) that do not pass through Caddy.
#   app     /usr/local/antweb/web/log/accessLog.txt inside antweb_antweb_1 (.do requests only,
#           has user agent). Emptied whenever the container is rebuilt.
#
# Output: tab-separated, header line first, sorted by total, largest first.
#   key  total  human  human_ok  bot_listed  bot_other
# key is the path without query string; for requests with an action= parameter
# (util.do, utilData.do, list.do and others) the key is path?action=<value>.
# Long-tail paths are grouped: /specimen/*, /web/curator/*, /web/bak/*, /v3.1/<endpoint>.
# Static files (css, js, images, fonts) and /images/ are left out.
# human_ok counts human requests answered with a status below 400.
# bot_listed = user agents on Caddy's @bots list (they get 403 at Caddy).
# bot_other  = other crawlers, scripts and empty user agents.
# The date range covered and the files read are printed to stderr.

set -euo pipefail

SOURCE=caddy
OUT=-
ANTWEB_DIR="${ANTWEB_DIR:-$HOME/antweb}"
CONTAINER=antweb_antweb_1

usage() { sed -n '2,35p' "$0"; exit "${1:-0}"; }

while getopts "s:o:d:h" opt; do
  case "$opt" in
    s) SOURCE="$OPTARG" ;;
    o) OUT="$OPTARG" ;;
    d) ANTWEB_DIR="$OPTARG" ;;
    h) usage 0 ;;
    *) usage 1 ;;
  esac
done

command -v python3 >/dev/null || { echo "python3 is needed" >&2; exit 1; }

shopt -s nullglob
case "$SOURCE" in
  caddy)  FILES=("$ANTWEB_DIR"/docker/caddy/logs/access*.log*) ;;
  tomcat) FILES=("$ANTWEB_DIR"/logs/localhost_access_log.*) ;;
  app)    FILES=(-) ;;
  *) echo "unknown source: $SOURCE (caddy, tomcat or app)" >&2; exit 1 ;;
esac
shopt -u nullglob

if [ "${#FILES[@]}" -eq 0 ]; then
  echo "No $SOURCE access logs found under $ANTWEB_DIR. Try -d <antweb dir> or another -s." >&2
  exit 2
fi

# The Python part reads every file (plain or .gz) line by line; nothing is written.
PY=$(cat <<'PYEOF'
import sys, os, re, gzip, json, datetime
from collections import defaultdict
from urllib.parse import urlsplit, parse_qs

source = sys.argv[1]
files = sys.argv[2:]

LISTED = re.compile(r"(?i)(CCBot|GPTBot|Googlebot-Images|Sogou|SenutoBot|SiteScoreBot|Twitterbot|YisouSpider|IABot|Turnitin|CFNetwork/.* Darwin|ClaudeBot|SemrushBot|Googlebot|Bingbot|Slurp|DuckDuckBot|Baiduspider|YandexBot|Exabot|facebot|facebookexternalhit|Bytespider|AppleBot|Swiftbot|GoogleOther|Google-InspectionTool|MJ12bot|Alexa crawler|Soso Spider|Pinterestbot|Dotbot|AhrefsBot|archive.org_bot|scrapy|PetalBot|Amazonbot|DataForSeoBot|Yeti|Googlebot-Image|meta-externalagent)")
OTHER = re.compile(r"(?i)(bot\b|bot/|crawl|spider|slurp|scan|python|curl|wget|httpclient|http-client|go-http|java/|okhttp|libwww|perl|ruby|axios|node-fetch|headless|phantom|selenium|playwright|monitor|uptime|check|fetch|feed|preview|zgrab|masscan|nmap|nikto|sqlmap)")
STATIC = re.compile(r"(?i)\.(css|js|map|png|jpe?g|gif|svg|ico|webp|bmp|tiff?|woff2?|ttf|eot|otf)$")

def opener(p):
    if p == "-":
        return sys.stdin
    if p.endswith(".gz"):
        return gzip.open(p, "rt", encoding="utf-8", errors="replace")
    return open(p, "rt", encoding="utf-8", errors="replace")

def key_for(uri):
    try:
        parts = urlsplit(uri)
    except ValueError:
        return None
    path = parts.path or "/"
    if STATIC.search(path) or path.startswith("/images/"):
        return None
    while "//" in path:
        path = path.replace("//", "/")
    segs = path.split("/")
    if path.startswith("/specimen/"):
        path = "/specimen/*"
    elif path.startswith("/web/curator/"):
        path = "/web/curator/*"
    elif path.startswith("/web/bak/"):
        path = "/web/bak/*"
    elif path.startswith("/v3.1/") and len(segs) > 2:
        path = "/v3.1/" + segs[2]
    if len(path) > 120:
        path = path[:120] + "..."
    qs = parse_qs(parts.query, keep_blank_values=True)
    act = qs.get("action")
    if act and act[0]:
        a = re.sub(r"[^A-Za-z0-9_.-]", "", act[0])[:60]
        path = path + "?action=" + a
    return path

def classify(ua):
    if not ua or ua == "-":
        return "bot_other"
    if LISTED.search(ua):
        return "bot_listed"
    if OTHER.search(ua):
        return "bot_other"
    return "human"

counts = defaultdict(lambda: {"total": 0, "human": 0, "human_ok": 0, "bot_listed": 0, "bot_other": 0})
tmin = tmax = None
lines = skipped = 0

TOMCAT = re.compile(r'^\S+ \S+ \S+ \[([^\]]+)\] "(\S+) (\S+)[^"]*" (\d{3}) ')
APP = re.compile(r"^(\S+ \S+) \S+ requestInfoTarget:(\S+).*?user-agent:(.*)$")

def seen(ts):
    global tmin, tmax
    if ts is None:
        return
    if tmin is None or ts < tmin: tmin = ts
    if tmax is None or ts > tmax: tmax = ts

for f in files:
    try:
        fh = opener(f)
    except OSError as e:
        print("cannot read %s: %s" % (f, e), file=sys.stderr)
        continue
    with fh:
        for line in fh:
            lines += 1
            ua = None; status = 200; ts = None
            if source == "caddy":
                try:
                    j = json.loads(line)
                    req = j.get("request", {})
                    uri = req.get("uri", "")
                    hdr = req.get("headers", {}) or {}
                    ua = (hdr.get("User-Agent") or [""])[0]
                    status = int(j.get("status", 0) or 0)
                    t = j.get("ts")
                    ts = datetime.datetime.fromtimestamp(float(t), datetime.timezone.utc) if t is not None else None
                    if "request" not in j:
                        skipped += 1; continue
                except (ValueError, TypeError, AttributeError):
                    skipped += 1; continue
            elif source == "tomcat":
                m = TOMCAT.match(line)
                if not m:
                    skipped += 1; continue
                try:
                    ts = datetime.datetime.strptime(m.group(1), "%d/%b/%Y:%H:%M:%S %z")
                except ValueError:
                    ts = None
                uri = m.group(3); status = int(m.group(4)); ua = None
            else:
                m = APP.match(line.rstrip("\n"))
                if not m:
                    skipped += 1; continue
                try:
                    ts = datetime.datetime.strptime(m.group(1)[:19], "%Y-%m-%d %H:%M:%S")
                except ValueError:
                    ts = None
                full = m.group(2)
                try:
                    sp = urlsplit(full)
                    uri = sp.path + ("?" + sp.query if sp.query else "")
                except ValueError:
                    skipped += 1; continue
                ua = m.group(3).strip(); status = 200
            k = key_for(uri)
            if k is None:
                continue
            seen(ts)
            c = counts[k]
            c["total"] += 1
            if source == "tomcat":
                # No user agent in this log: count everything as "human" (it passed Caddy's bot list).
                cls = "human"
            else:
                cls = classify(ua)
            c[cls] += 1
            if cls == "human" and status < 400:
                c["human_ok"] += 1

fmt = lambda t: t.strftime("%Y-%m-%d %H:%M") if t else "?"
print("# source=%s files=%d lines=%d unparsed=%d keys=%d" % (source, len(files), lines, skipped, len(counts)), file=sys.stderr)
print("# covers %s to %s" % (fmt(tmin), fmt(tmax)), file=sys.stderr)
if source == "tomcat":
    print("# tomcat log has no user agent: 'human' here means 'not refused by Caddy'.", file=sys.stderr)
out = sys.stdout
out.write("key\ttotal\thuman\thuman_ok\tbot_listed\tbot_other\n")
for k, c in sorted(counts.items(), key=lambda kv: (-kv[1]["total"], kv[0])):
    out.write("%s\t%d\t%d\t%d\t%d\t%d\n" % (k, c["total"], c["human"], c["human_ok"], c["bot_listed"], c["bot_other"]))
PYEOF
)

if [ "$OUT" != - ]; then exec > "$OUT"; fi

if [ "$SOURCE" = app ]; then
  echo "# reading accessLog.txt from $CONTAINER" >&2
  docker exec "$CONTAINER" cat /usr/local/antweb/web/log/accessLog.txt \
    | python3 -I -c "$PY" app -
else
  echo "# reading:" >&2
  printf '#   %s\n' "${FILES[@]}" >&2
  python3 -I -c "$PY" "$SOURCE" "${FILES[@]}"
fi
