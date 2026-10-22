"""Locality codes are stored plain (2026-10-22); the one-off repair of the codes stored URL-encoded.

Until 2026-10-22 SpecimenUploadParse.cleanLocalityCode passed every locality code through
HttpUtil.encodePath, so production held 1,117 specimens in 67 codes such as Ahi_%3C5m and KM55%22%22805.

Without a database:
  - the upload no longer encodes the code; the specimen links encode the plain code once in UTF-8;
  - tools/data/locality_codes.py decodes %XX runs as UTF-8 when valid, else ISO-8859-1; classifies each
    code as ok, merge (same group and locality name already use the decoded code) or collision; writes
    the repair SQL (backup table, guarded updates, counts, rollback) and leaves out skipped groups and
    collisions.
With a MySQL container (CI: the MySQL 8.4 of the staging refresh step; LOCALITY_CODES_DB_CONTAINER and
LOCALITY_CODES_DB_PASSWORD, database `ant`, utf8mb3_general_ci as production):
  - preview finds the encoded codes and the collisions (MySQL ignores case and accents);
  - the repair SQL changes exactly the chosen rows, a second run stops at the backup table, and the
    rollback restores every stored code.

Run: python3 tests/unit/test_locality_codes.py   (also in .github/workflows/tests.yaml)
"""
import importlib.util
import os
import re
import subprocess
import sys
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
spec = importlib.util.spec_from_file_location("locality_codes", os.path.join(ROOT, "tools", "data", "locality_codes.py"))
lc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lc)


def src(path):
    return open(os.path.join(ROOT, path), encoding="utf-8").read()


def code_lines(text):
    """The lines of a Java file that are not comments."""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return [line for line in text.split("\n") if not line.strip().startswith("//")]


def test_upload_stores_plain_codes():
    parse = code_lines(src("src/org/calacademy/antweb/upload/SpecimenUploadParse.java"))
    assert not [l for l in parse if "encodePath(" in l], "SpecimenUploadParse encodes a code again"
    assert any("plainLocalityCode(" in l for l in parse)
    specimen = code_lines(src("src/org/calacademy/antweb/Specimen.java"))
    assert not [l for l in specimen if "HttpUtil.encodePath(" in l], "locality links must encode with Html.url (UTF-8)"
    for path in ("src/org/calacademy/antweb/home/LocalityDb.java", "src/org/calacademy/antweb/home/CollectionDb.java"):
        lines = [l for l in code_lines(src(path)) if "localitycode =" in l.lower() or "collectioncode =" in l.lower()]
        assert lines and all("DBUtil.sqlLiteral(" in l for l in lines), (path, lines)
    include = src("web/maps/googleMapInclude.jsp")
    assert include.count("Html.url(objectName)") == 2 and "Html.escAttr(AntwebProps.getDomainApp() + \"/bigMap.do?\"" in include, \
        "the Enlarge Map link must encode the code once and escape the attribute"
    print("The upload stores locality codes plain; links and lookups use the plain code.")


def test_decode():
    cases = {
        "Ahi_%3C5m": "Ahi_<5m",
        "Nosy_Mangabe_%3C5": "Nosy_Mangabe_<5",
        "Bequia_Peggy%3Fs_Rock": "Bequia_Peggy?s_Rock",
        "MontagnedesFran%E7ais_150": "MontagnedesFrançais_150",       # ISO-8859-1
        "MontagnedesFran%C3%A7ais_150": "MontagnedesFrançais_150",    # valid UTF-8
        "KM55%22%22805": 'KM55""805',
        "KM55%22%22840-B": 'KM55""840-B',
        "KM55%22815": 'KM55"815',
        "Mah%E9%20Mont": "Mahé Mont",
        "plain_code": "plain_code",
        "a%3e%3Cb": "a><b",
        "ant%F0%9F%90%9C": "ant?",                                       # 4-byte UTF-8: utf8mb3 column
    }
    for stored, decoded in cases.items():
        assert lc.decode(stored) == decoded, (stored, lc.decode(stored), decoded)
    print("Decoding: %XX runs as UTF-8 when valid, else ISO-8859-1.")


def test_classify():
    encoded = [
        ("Ahi_%3C5m", "1", "California Academy of Sciences", 5, "Ahi"),
        ("MontagnedesFran%E7ais_150", "1", "California Academy of Sciences", 3, "Montagne des Français"),
        ("KM55%22815", "88", "Economo Ant Lab", 9, "KM55"),
        ("KM55%22%22805", "88", "Economo Ant Lab", 142, "KM55 805"),
        ("X%3C1", "88", "Economo Ant Lab", 1, "X"),
        ("X%3c1", "88", "Economo Ant Lab", 2, "X"),
    ]
    existing = [
        ('KM55"815', "88", 4, "KM55"),                                           # same group and name: merge
        ("MontagnedesFrancais_150", "1", 7, "Montagne des Francais"),            # accent-insensitive match: collision
    ]
    rows = {r["stored_code"]: r for r in lc.classify(encoded, existing)}
    assert rows["Ahi_%3C5m"]["status"] == "ok" and rows["Ahi_%3C5m"]["decoded_code"] == "Ahi_<5m"
    assert rows["KM55%22815"]["status"] == "merge", rows["KM55%22815"]
    assert rows["MontagnedesFran%E7ais_150"]["status"] == "collision", rows["MontagnedesFran%E7ais_150"]
    assert "MontagnedesFrancais_150" in rows["MontagnedesFran%E7ais_150"]["detail"]
    assert rows["KM55%22%22805"]["status"] == "ok"
    assert rows["X%3C1"]["status"] == "merge" and rows["X%3c1"]["status"] == "merge"   # both decode to X<1
    print("Preview: ok, merge and collision.")
    return list(rows.values())


def test_preview_file_and_sql():
    rows = test_classify()
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, "preview.tsv")
        lc.write_preview(rows, path)
        back = lc.read_preview(path)
        assert back == rows, "preview file round trip"
    sql, rollback, chosen = lc.repair_sql(rows, skip_groups=("1",))
    assert {r["stored_code"] for r in chosen} == {"KM55%22815", "KM55%22%22805", "X%3C1", "X%3c1"}, chosen
    assert sql.index("create table specimen_localitycode_bak_20261022") < sql.index("start transaction")
    assert "update specimen set localitycode = 'KM55\"\"805' where localitycode = 'KM55%22%22805'" in sql
    assert "cast(localitycode as binary) = cast('KM55%22%22805' as binary) and access_group = 88;" in sql
    assert "Ahi_" not in sql.split("start transaction")[1], "group 1 is skipped"
    assert "MontagnedesFran" not in sql, "a collision is left out"
    assert "154 as expected" in sql, "9 + 142 + 1 + 2 specimens"
    assert rollback.startswith("update specimen s join specimen_localitycode_bak_20261022 b")
    sql_all, _r, chosen_all = lc.repair_sql(rows, include_collisions=True)
    assert len(chosen_all) == len(rows) and "MontagnedesFrançais_150" in sql_all
    tricky = [{"stored_code": "a'b%5C", "decoded_code": "a'b\\", "group_id": "5", "group_name": "g", "specimens": "1",
               "locality_names": "", "status": "ok", "detail": ""}]
    t, _r, _c = lc.repair_sql(tricky)
    assert "set localitycode = 'a''b\\\\' where localitycode = 'a''b%5C'" in t, t
    nothing, rb, none = lc.repair_sql([r for r in rows if r["group_id"] == "1"], skip_groups=("1",))
    assert not none and not rb and "nothing to repair" in nothing and "create table" not in nothing
    print("Repair SQL: backup first, one guarded update per code and group, skipped groups and collisions left out.")


# ------------------------------------------------------------------ with MySQL

CONTAINER = os.environ.get("LOCALITY_CODES_DB_CONTAINER")
PASSWORD = os.environ.get("LOCALITY_CODES_DB_PASSWORD", "ci-root")


def mysql(sql, check=True):
    env = dict(os.environ, MYSQL_PWD=PASSWORD)
    r = subprocess.run(["docker", "exec", "-i", "-e", "MYSQL_PWD", CONTAINER, "mysql", "-uroot", "ant",
                        "--default-character-set=utf8mb4", "-B", "-N", "--raw"],
                       input=sql.encode("utf-8"), capture_output=True, env=env, timeout=120)
    if check:
        assert r.returncode == 0, r.stderr.decode()
    return r


def rows_of(sql):
    return [line.split("\t") for line in mysql(sql).stdout.decode("utf-8").split("\n") if line]


SCHEMA = """
drop table if exists specimen, ant_group, specimen_localitycode_bak_20261022;
create table ant_group (id int NOT NULL, name varchar(100) DEFAULT NULL, PRIMARY KEY (id))
  DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
create table specimen (code varchar(128) NOT NULL, access_group int DEFAULT NULL, localitycode varchar(128) DEFAULT NULL,
  localityname varchar(128) DEFAULT NULL, collectioncode varchar(128) DEFAULT NULL, PRIMARY KEY (code),
  KEY localitycode (localitycode)) DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
insert into ant_group values (1, 'California Academy of Sciences'), (88, 'Economo Ant Lab');
insert into specimen values
  ('cas1', 1, 'Ahi_%3C5m', 'Ahi', null), ('cas2', 1, 'Ahi_%3C5m', 'Ahi', null),
  ('cas3', 1, 'MontagnedesFran%E7ais_150', 'Montagne des Français', null),
  ('cas4', 1, 'MontagnedesFrancais_150', 'Montagne des Francais', null),
  ('eco1', 88, 'KM55%22%22805', 'KM55 805', null), ('eco2', 88, 'KM55%22%22805', 'KM55 805', null),
  ('eco3', 88, 'KM55%22815', 'KM55', null), ('eco4', 88, 'KM55"815', 'KM55', null),
  ('eco5', 88, 'Plain_<code', 'P', null), ('eco6', 88, 'Fran%C3%A7ais', 'F', null);
"""


def test_with_mysql():
    if not CONTAINER:
        print("MySQL part skipped (LOCALITY_CODES_DB_CONTAINER not set).")
        return
    mysql(SCHEMA)
    with tempfile.TemporaryDirectory() as d:
        os.makedirs(os.path.join(d, "etc"))
        with open(os.path.join(d, "etc", "db.properties"), "w") as f:
            f.write("user=root\npassword=" + PASSWORD + "\n")
        preview = os.path.join(d, "preview.tsv")
        out = subprocess.run([sys.executable, os.path.join(ROOT, "tools", "data", "locality_codes.py"), "preview",
                              "--project", d, "--container", CONTAINER, "--out", preview],
                             capture_output=True, text=True, timeout=120)
        assert out.returncode == 0, out.stderr
        assert "5 stored codes (per group), 7 specimens" in out.stdout, out.stdout
        rows = {r["stored_code"]: r for r in lc.read_preview(preview)}
        assert rows["Ahi_%3C5m"]["status"] == "ok" and rows["Ahi_%3C5m"]["specimens"] == "2"
        assert rows["MontagnedesFran%E7ais_150"]["status"] == "collision"
        assert rows["KM55%22815"]["status"] == "merge"
        assert rows["Fran%C3%A7ais"]["decoded_code"] == "Français"
        repair = os.path.join(d, "repair.sql")
        out = subprocess.run([sys.executable, os.path.join(ROOT, "tools", "data", "locality_codes.py"), "sql",
                              "--preview", preview, "--skip-group", "1", "--out", repair],
                             capture_output=True, text=True, timeout=60)
        assert out.returncode == 0, out.stderr
        text = open(repair, encoding="utf-8").read()
        result = rows_of(text)
        assert ["4", "4"] in result, result            # backed_up = expected, changed = expected
        assert ["counts match"] in result, result
        assert result[-1] == ["0"], result             # nothing encoded left in group 88
        got = dict((c, l) for c, l in rows_of("select code, localitycode from specimen order by code;"))
        assert got["eco1"] == 'KM55""805' and got["eco3"] == 'KM55"815' and got["eco6"] == "Français", got
        assert got["cas1"] == "Ahi_%3C5m" and got["cas3"] == "MontagnedesFran%E7ais_150", "group 1 untouched"
        assert got["eco5"] == "Plain_<code"
        # A second run stops at the backup table and changes nothing.
        again = mysql(text, check=False)
        assert again.returncode != 0 and b"already exists" in again.stderr, again.stderr
        assert rows_of("select count(*) from specimen_localitycode_bak_20261022;") == [["4"]]
        # A count that differs stops before the commit: nothing changes.
        mysql("drop table specimen_localitycode_bak_20261022; update specimen set localitycode = 'KM55%22%22805' where code in ('eco1', 'eco2');"
              " update specimen set localitycode = 'KM55%22815' where code = 'eco3'; update specimen set localitycode = 'Fran%C3%A7ais' where code = 'eco6';")
        bad = mysql(text.replace("4 as expected", "5 as expected").replace("@changed = 4", "@changed = 5"), check=False)
        assert bad.returncode != 0 and b"more than 1 row" in bad.stderr, bad.stderr
        assert rows_of("select localitycode from specimen where code = 'eco1';") == [["KM55%22%22805"]], "rolled back"
        mysql("drop table specimen_localitycode_bak_20261022;")
        assert ["4", "4"] in rows_of(text)
        # The locality page's lookup: the plain code.
        assert rows_of("select count(*) from specimen where localitycode = 'KM55\"\"805';") == [["2"]]
        # The Integrity Check query (Queries.java encodedLocalityCodes) lists only group 1 now.
        listed = rows_of("select s.localitycode from specimen s where s.localitycode regexp '%[0-9A-Fa-f]{2}'"
                         " union all select s.collectioncode from specimen s where s.collectioncode regexp '%[0-9A-Fa-f]{2}';")
        assert sorted(x[0] for x in listed) == ["Ahi_%3C5m", "Ahi_%3C5m", "MontagnedesFran%E7ais_150"], listed
        # Rollback.
        rollback = re.search(r"^-- Rollback: (.*)$", text, re.M).group(1)
        mysql(rollback)
        got = dict((c, l) for c, l in rows_of("select code, localitycode from specimen order by code;"))
        assert got["eco1"] == "KM55%22%22805" and got["eco3"] == "KM55%22815" and got["eco6"] == "Fran%C3%A7ais", got
        mysql("drop table specimen, ant_group, specimen_localitycode_bak_20261022;")
    print("MySQL: preview, repair (4 specimens of group 88), second run refused, rollback.")


def main():
    test_upload_stores_plain_codes()
    test_decode()
    test_preview_file_and_sql()
    test_with_mysql()


if __name__ == "__main__":
    main()
