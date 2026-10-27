"""API v3.1 image counts (Brian Fisher, 2026-10-27): every reply with an imageCount (every image file, kept so
clients do not break) also has antImageCount and labelImageCount (images of the ant, label images).

The grouped query (api.imageSplitSql) runs against a small SQLite database here (no MySQL needed). Run inside the API
image (CI) or locally with Flask and SQLAlchemy installed:
  API_DIR=api/v3.1 python3 tests/unit/test_api_image_split.py
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
        create table specimen (code text, taxon_name text);
        create table image (id int, image_of_id text, shot_type text);
        insert into specimen values ('s1', 'a'), ('s2', 'a'), ('s3', 'b'), ('s4', 'c');
        insert into image values (1, 's1', 'h'), (2, 's1', 'p'), (3, 's1', 'l'), (4, 's2', 'L '), (5, 's3', 'd'), (6, 's3', null);
    """)
    return db


def run(db):
    return lambda sql, params: db.execute(sql, params).fetchall()


def test_split_by_specimen():
    got = api.imageSplit(["s1", "s2", "s3", "s4", "nope"], False, run(database()))
    assert got == {"s1": (2, 1), "s2": (0, 1), "s3": (2, 0)}, got


def test_split_by_name():
    got = api.imageSplit(["a", "b", "c"], True, run(database()))
    assert got == {"a": (2, 2), "b": (2, 0)}, got


def test_shot_split():
    assert api.shotSplit(["h", "L", "l ", "p", None]) == (3, 2)


def test_replies_add_the_fields():
    for fn in (api.getSpecimen, api.getGeoSpecimens):
        assert "addImageSplit(dataList, 'specimenCode')" in inspect.getsource(fn), fn.__name__
    assert "addImageSplit(dataList, 'taxonName', True)" in inspect.getsource(api.getTaxa)
    for fn in (api.getImages, api.getTaxaImages):
        src = inspect.getsource(fn)
        assert "'antImageCount': antImages" in src and "'labelImageCount': labelImages" in src, fn.__name__
        assert "'imageCount'" in src or "'count': imageCount" in src, fn.__name__ + " keeps its imageCount"


if __name__ == "__main__":
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("ok", name)
