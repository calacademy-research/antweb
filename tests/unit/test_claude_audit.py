#!/usr/bin/env python3
"""The audit rotation (.github/scripts/claude_audit.py): every audited file is in exactly one chunk, no chunk is
empty or over the cap, the API area is found, and the scheduled slot and the stop date work."""
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

os.environ["SLOT_HOURS"] = "1,5,9,17"
real_date = datetime.date


class FakeDate(datetime.date):
    today_value = real_date(2026, 10, 24)

    @classmethod
    def today(cls):
        return cls.today_value


a.datetime.date = FakeDate
try:
    first = a.pick(chunks, "", "10 1 * * *")[0]
    second = a.pick(chunks, "", "10 5 * * *")[0]
    assert first != second, "two slots of one day audit the same area"
    assert first == names[(1 * a.SLOTS + 0) % len(chunks)]
    FakeDate.today_value = real_date(2026, 11, 8)
    assert a.pick(chunks, "", "10 1 * * *") == (None, None), "scheduled audits must stop after 2026-11-07"
    assert a.pick(chunks, names[0], "")[0] == names[0], "a named area runs on any day"
finally:
    a.datetime.date = real_date
print(f"audit rotation OK: {len(chunks)} chunks, {len(files)} files")
