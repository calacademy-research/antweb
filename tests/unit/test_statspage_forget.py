"""A curator's recount of one bioregion or museum makes the statistics page count its tables again (2026-11-09: the
page kept the morning's copy, Oceania 64 genera, while the rebuilt list had 63; the count check's recount differed in
every test run). Source check: OverviewAction calls StatsPageAction.forget() right after both recounts."""
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]


def test_recount_of_a_bioregion_or_museum_forgets_the_kept_tables():
    stats = (ROOT / "src/org/calacademy/antweb/StatsPageAction.java").read_text()
    assert re.search(r"public static void forget\(\)\s*\{\s*s_numbers = null;\s*\}", stats)
    overview = (ROOT / "src/org/calacademy/antweb/OverviewAction.java").read_text()
    for populate in ("MuseumMgr.populate(connection, true);", "BioregionMgr.populate(connection, true);"):
        after = overview.split(populate, 1)[1].lstrip()
        assert after.startswith("StatsPageAction.forget();"), populate


if __name__ == "__main__":
    test_recount_of_a_bioregion_or_museum_forgets_the_kept_tables()
    print("ok")
