"""Page speed of the species pages that were slow (2026-11-17, Jack Longino and Brian Fisher: "image loading from
species pages is quite sluggish now, different from before").

Cause: browse.do and images.do decided page-level work with instanceof Family / Subfamily / Genus, and Species
extends Genus extends Subfamily extends Family, so every species page also read the genus rows of its whole subfamily
and the subfamily rows of the family (about 5 of 7 seconds for Pheidole megacephala's images page on staging).
BrowseAction.isRank fixed it; tests/unit/test_browse_rank_checks.py keeps instanceof out.

Each page gets two tries and the faster counts (a run of the suite loads staging too). The limit is generous: it
catches a page several times slower than it should be, not a small drift. Read-only, anonymous.
"""
import time

import pytest

from antweb import BASE_URL, new_session, get

LIMIT_SECONDS = 4.0
PAGES = [
    "/images.do?genus=pheidole&species=megacephala&rank=species",     # 2,452 specimens: 7 s before the fix
    "/images.do?genus=tetramorium&species=bicarinatum&rank=species",
    "/browse.do?genus=pheidole&species=megacephala&rank=species",
]


def timed(session, path):
    started = time.monotonic()
    outcome = get(session, path)
    return outcome, time.monotonic() - started


@pytest.mark.parametrize("path", PAGES)
def test_species_page_is_quick(path):
    s = new_session()
    best = None
    for _ in range(2):
        outcome, seconds = timed(s, path)
        assert outcome.category == "ok", f"{path}: {outcome}"
        best = seconds if best is None else min(best, seconds)
        if best < LIMIT_SECONDS:
            break
    assert best < LIMIT_SECONDS, f"{BASE_URL}{path} took {best:.1f} s (limit {LIMIT_SECONDS} s)"
