"""API v3.1 counts (api/v3.1/api.py), 2026-10-21: /unimagedGeolocaleTaxa.

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
