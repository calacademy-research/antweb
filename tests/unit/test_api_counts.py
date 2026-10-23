"""API v3.1 counts (api/v3.1/api.py), 2026-10-21: /unimagedGeolocaleTaxa; 2026-10-22: /images records and counts.

A taxon is imaged when one of its specimens has an image of the ant, that is an image whose shot type is
not 'l' (label). Before, the endpoint used specimen.image_count > 1: a taxon whose specimens had exactly
one image each was listed as unimaged, and label images counted as images of the ant.

The rule (api.imagedTaxaSql) is run against a small SQLite database here (no MySQL needed). Run inside the
API image (CI, .github/workflows/tests.yaml):
  docker run --rm --entrypoint python -v "$PWD/tests/unit:/t:ro" antweb/api:dev /t/test_api_counts.py
or locally with Flask and SQLAlchemy installed:
  API_DIR=api/v3.1 python3 tests/unit/test_api_counts.py
"""
import inspect
import os
import sqlite3
import sys

sys.path.insert(0, os.environ.get("API_DIR", "/api"))
import api  # noqa: E402


def database():
    db = sqlite3.connect(":memory:")
    db.executescript("""
        create table specimen (code text, taxon_name text, caste text, image_count int);
        create table image (id int, image_of_id text, shot_type text);
        create table taxon (taxon_name text);
        insert into taxon values ('one image'), ('labels only'), ('no image'), ('two images'), ('queen only');
        insert into specimen values ('s1', 'one image', 'worker', 1), ('s2', 'labels only', 'worker', 2),
            ('s3', 'no image', 'worker', 0), ('s4', 'two images', 'worker', 2), ('s5', 'queen only', 'queen', 1);
        insert into image values (1, 's1', 'h'), (2, 's2', 'l'), (3, 's2', 'L '), (4, 's4', 'p'), (5, 's4', 'd'), (6, 's5', 'h');
    """)
    return db


def unimaged(db, caste_clause=""):
    rows = db.execute("select taxon_name from taxon where taxon_name not in (" + api.imagedTaxaSql(caste_clause) + ")"
                      " order by taxon_name").fetchall()
    return [r[0] for r in rows]


def test_a_taxon_with_one_image_of_the_ant_is_imaged():
    assert unimaged(database()) == ["labels only", "no image"], unimaged(database())


def test_caste_clause_uses_the_specimen():
    db = database()
    assert unimaged(db, " and s.caste = 'queen'") == ["labels only", "no image", "one image", "two images"]
    assert unimaged(db, " and s.caste = 'worker'") == ["labels only", "no image", "queen only"]


def test_endpoint_uses_the_rule():
    source = inspect.getsource(api.getUnimagedGeolocaleTaxa)
    assert "image_count > 1" not in source
    assert source.count("imagedTaxaSql(") == 3, source


class _ImageRow:
    def __init__(self, image_id, code, shot, number):
        self.id, self.code, self.shotType, self.shotNumber = image_id, code, shot, number
        self.uploadDate, self.hasTiff = "2026-10-01", 0


class _Query:
    """Stands in for session.query(Image): filters and paging return itself, all() the rows."""
    def __init__(self, rows):
        self.rows = rows

    def filter(self, *args):
        return self

    def limit(self, n):
        return self

    def offset(self, n):
        return self

    def all(self):
        return self.rows


def test_images_records_are_keyed_by_specimen_code():
    """/images groups the image rows by specimen; every record names its specimen as specimenCode (every
    record but the last was keyed 'spefimenCode' until 2026-10-22). metaData: count = image rows,
    specimenCount = records."""
    rows = [_ImageRow(1, "casent0000001", "h", 1), _ImageRow(2, "casent0000001", "p", 1),
            _ImageRow(3, "casent0000002", "h", 1), _ImageRow(4, "casent0000003", "d", 2)]
    real = api.session.query
    api.session.query = lambda *a, **k: _Query(rows)
    try:
        body = api.application.test_client().get("/images?up=1").get_json()
    finally:
        api.session.query = real
    records = body["images"]
    assert [r.get("specimenCode") for r in records] == ["casent0000001", "casent0000002", "casent0000003"], records
    assert all("spefimenCode" not in r for r in records), records
    assert [len(r["images"]) for r in records] == [2, 1, 1], records
    assert body["metaData"]["count"] == 4 and body["metaData"]["specimenCount"] == 3, body["metaData"]


if __name__ == "__main__":
    failed = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print("ok:", name)
            except AssertionError as e:
                failed += 1
                print("FAIL:", name, e)
    print("API counts", "FAILED" if failed else "OK")
    sys.exit(1 if failed else 0)
