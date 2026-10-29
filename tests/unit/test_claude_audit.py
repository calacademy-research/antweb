#!/usr/bin/env python3
"""The audit rotation (.github/scripts/claude_audit.py): every audited file is in exactly one chunk, no chunk is
empty or over the cap, the API area is found, and the scheduled slot and the stop date work.
The spend guard (2026-10-29): the cost of an answer from its usage block, the ledger sum since the start, and the
decision to pause the audit at the reserve or after the credit expires (.github/audit-budget.ini)."""
import datetime
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
os.environ.setdefault("REPO_ROOT", str(ROOT))
sys.path.insert(0, str(ROOT / ".github" / "scripts"))
import claude_audit as a  # noqa: E402

a.ROOT = ROOT
chunks = a.chunks()
names = [n for n, _ in chunks]
files = [f for _, fs in chunks for f in fs]
assert len(files) == len(set(files)), "a file is in two chunks"
assert all(fs for _, fs in chunks), "an empty chunk"
for n, fs in chunks:
    size = sum(len((ROOT / f).read_text(encoding="utf-8", errors="replace")) for f in fs)
    assert size <= a.CHUNK_CAP, (n, size)
assert any(n.startswith("api-") for n in names), "the API area found no files"
assert any(n.startswith("upload-") for n in names) and any(n.startswith("security-") for n in names)

os.environ["SLOT_HOURS"] = "0,1,2,4,5,6,8,9,10,17,18,22"
real_date = datetime.date


class FakeDate(datetime.date):
    today_value = real_date(2026, 10, 24)

    @classmethod
    def today(cls):
        return cls.today_value


a.datetime.date = FakeDate
try:
    first = a.pick(chunks, "", "10 1 * * *")[0]
    second = a.pick(chunks, "", "10 2 * * *")[0]
    assert first != second, "two slots of one day audit the same area"
    assert first == names[(1 * a.SLOTS + 1) % len(chunks)]   # 10-24, second slot of the day (hour 1)
    assert a.SLOTS == 12
    FakeDate.today_value = real_date(2026, 11, 8)
    assert a.pick(chunks, "", "10 1 * * *") == (None, None), "scheduled audits must stop after 2026-11-07"
    assert a.pick(chunks, names[0], "")[0] == names[0], "a named area runs on any day"
finally:
    a.datetime.date = real_date

# --- Spend guard -------------------------------------------------------------------------------------------
cr = a.cr
UTC = datetime.timezone.utc


def close(x, y):
    return abs(x - y) < 1e-9


# Cost: prices in one place (claude_review.py), every usage field counted.
assert (cr.PRICE_IN, cr.PRICE_OUT) == (4.00, 20.00) and cr.MODEL == "claude-opus-5-5"
assert close(cr.cost({"input_tokens": 1_000_000}), 4.00)
assert close(cr.cost({"output_tokens": 1_000_000}), 20.00)
assert close(cr.cost({"input_tokens": 100_000, "output_tokens": 20_000}), 0.40 + 0.40)
assert close(cr.cost({"cache_read_input_tokens": 1_000_000}), cr.PRICE_CACHE_READ)
assert close(cr.cost({"cache_creation_input_tokens": 1_000_000}), cr.PRICE_CACHE_WRITE_5M)
assert close(cr.cost({"cache_creation_input_tokens": 300, "cache_creation":
                      {"ephemeral_5m_input_tokens": 100, "ephemeral_1h_input_tokens": 200}}),
             (100 * cr.PRICE_CACHE_WRITE_5M + 200 * cr.PRICE_CACHE_WRITE_1H) / 1e6)
assert cr.cost({}) == 0 and cr.cost(None) == 0 and cr.cost({"input_tokens": None}) == 0

# The config in the repository parses and has the approved values.
b = a.load_budget()
assert b["start"] == datetime.datetime(2026, 10, 29, 19, 0, tzinfo=UTC), b["start"]
assert (b["start_balance"], b["reserve"]) == (59.87, 10.00)
assert b["expires"] == real_date(2026, 11, 8)
assert b["review_estimate"] > 0

# Ledger: lines before the start, comments and broken lines are ignored; a line written by ledger_line is read back.
t0 = b["start"]
line = a.ledger_line(t0 + datetime.timedelta(hours=3), "123", "audit", 1.23456, "upload-1\tbad\nnote")
assert line.count("\t") == 4 and line.endswith("\n") and "\t1.2346\t" in line
ledger = ("# utc_time\trun_id\tkind\tcost_usd\tnote\n"
          "2026-10-29T18:10:00Z\t1\taudit\t2.50\tbefore the start\n"
          "2026-10-29T19:00:00Z\t2\taudit\t1.00\tat the start\n"
          "garbage line\n"
          "2026-10-30T01:10:00Z\t3\taudit\tnot-a-number\tx\n"
          "\n" + line)
spent, n = a.ledger_spent(ledger, t0)
assert n == 2 and close(spent, 1.0 + 1.2346), (spent, n)
assert a.ledger_spent("", t0) == (0.0, 0)

# Decision: run above the reserve; pause at or below it; pause from the expiry day on; reviews count.
now = datetime.datetime(2026, 10, 30, 12, 0, tzinfo=UTC)
go, left, msg = a.budget_decision(b, 20.0, 3, now)
assert go and close(left, 59.87 - 20.0 - 3 * b["review_estimate"]), (go, left, msg)
go, left, msg = a.budget_decision(b, 49.87, 0, now)
assert not go and close(left, 10.0) and msg == "Audit paused: estimated credit left $10.00, reserve $10.00", msg
go, left, msg = a.budget_decision(b, 45.0, 0, now)
assert go, "$14.87 left is above the reserve"
go, left, msg = a.budget_decision(b, 45.0, int(5 / b["review_estimate"]) + 1, now)
assert not go and msg.startswith("Audit paused: estimated credit left $"), "reviews must count against the credit"
go, left, msg = a.budget_decision(b, 0.0, 0, datetime.datetime(2026, 11, 8, 0, 5, tzinfo=UTC))
assert not go and "expired" in msg, msg
go, left, msg = a.budget_decision(b, 0.0, 0, datetime.datetime(2026, 11, 7, 23, 55, tzinfo=UTC))
assert go, msg

# budget_check: a missing ledger pauses (spend unknown); an empty one runs; the review count is added.
import tempfile  # noqa: E402

a.count_review_runs = lambda repo, token, start: 4
with tempfile.TemporaryDirectory() as d:
    os.environ["LEDGER"] = os.path.join(d, "missing.tsv")
    go, lines = a.budget_check(now)
    assert not go and "ledger" in lines[0], lines
    path = os.path.join(d, "ledger.tsv")
    with open(path, "w") as f:
        f.write(ledger)
    os.environ["LEDGER"] = path
    go, lines = a.budget_check(now)
    assert go and "4 Claude review runs" in lines[1] and "2 ledger lines" in lines[1], lines
    with open(path, "a") as f:
        f.write(a.ledger_line(now, "9", "audit", 45.0, "big"))
    go, lines = a.budget_check(now)
    assert not go and lines[0].startswith("- Audit paused: estimated credit left $"), lines
    a.count_review_runs = lambda repo, token, start: None
    with open(path, "w") as f:
        f.write("")
    go, lines = a.budget_check(now)
    assert go and "could not be counted" in lines[1], lines
    del os.environ["LEDGER"]

print(f"spend guard OK: start {b['start']:%Y-%m-%d %H:%M} UTC, ${b['start_balance']:.2f}, reserve ${b['reserve']:.2f}")
print(f"audit rotation OK: {len(chunks)} chunks, {len(files)} files")
