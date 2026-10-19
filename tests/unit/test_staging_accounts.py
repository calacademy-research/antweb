"""tools/staging/create-test-accounts.py: hashes AntWeb accepts, the staging-only guards, deletes
limited to test data, and secret names the e2e workflow reads.

Run: python3 tests/unit/test_staging_accounts.py   (also in .github/workflows/tests.yaml)
"""
import datetime
import importlib.util
import os
import re

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
spec = importlib.util.spec_from_file_location("accounts", os.path.join(ROOT, "tools", "staging", "create-test-accounts.py"))
accounts = importlib.util.module_from_spec(spec)
spec.loader.exec_module(accounts)

PLAIN = "zz-Test pässword 1"
# Made by PasswordHash.hash(PLAIN) in Java (src/org/calacademy/antweb/util/PasswordHash.java).
FROM_JAVA = "pbkdf2_sha256$600000$sZYLQTBwPHCC1cAJJ3I9Xw==$XZLol7ZH5isK2jTyfJGvIaot2Nl4tWhcNDNnc8wUNG8="
# Checked in Java by test/org/calacademy/antweb/security/PasswordHashCompatTest.java.
FROM_PYTHON = "pbkdf2_sha256$600000$EFLAnAOcwM/wlxNQAfym7Q==$avqXmaL6ssODHyTONRhEmnEgtCoAyMZ1gZohd5KKDVU="


def test_java_hash_matches():
    assert accounts.password_matches(FROM_JAVA, PLAIN)
    assert not accounts.password_matches(FROM_JAVA, "zz-Test password 1")
    assert not accounts.password_matches("plain-text", "plain-text")


def test_python_hash_is_reproducible_and_in_java_format():
    salt = accounts.base64.b64decode(FROM_PYTHON.split("$")[2])
    assert accounts.hash_password(PLAIN, salt=salt) == FROM_PYTHON
    h = accounts.hash_password("abc")
    parts = h.split("$")
    assert parts[0] == "pbkdf2_sha256" and parts[1] == "600000"
    assert len(parts[2]) == 24 and len(parts[3]) == 44
    assert accounts.password_matches(h, "abc") and not accounts.password_matches(h, "abd")
    assert h != accounts.hash_password("abc"), "a new random salt each time"


def test_new_password():
    pws = {accounts.new_password() for _ in range(50)}
    assert len(pws) == 50
    for pw in pws:
        assert len(pw) == 32 and re.fullmatch(r"[A-Za-z0-9]+", pw)


def _t(h, m):
    return datetime.datetime(2026, 10, 19, h, m)


def test_guards():
    ok = accounts.guard_problems("staging", "/root/antweb-next", _t(14, 0), True, "/root/antweb-next")
    assert ok == []
    assert accounts.guard_problems("antweb-prod", "/root/antweb-next", _t(14, 0))
    assert accounts.guard_problems("staging", "/root/antweb", _t(14, 0))
    assert accounts.guard_problems("staging", "/root/antweb-next", _t(14, 0), container_dir="/root/antweb")
    assert accounts.guard_problems("staging", "/root/antweb-next", _t(14, 0), project_dir_exists=False)
    assert accounts.guard_problems("staging", "/root/antweb-next", _t(4, 45))
    assert accounts.guard_problems("staging", "/root/antweb-next", _t(8, 14))
    assert accounts.guard_problems("staging", "/root/antweb-next", _t(8, 15)) == []
    assert accounts.guard_problems("staging", "/root/antweb-next", _t(4, 44)) == []


def test_sql_literals_refuse_quotes():
    assert accounts.q("zztest_admin") == "'zztest_admin'"
    for bad in ("a'b", "a\\b", "a\nb"):
        try:
            accounts.q(bad)
        except ValueError:
            continue
        raise AssertionError("accepted " + repr(bad))


def test_removal_only_touches_test_data():
    for table, where in accounts.REMOVE_TABLES:
        assert "{gid}" in where or "{logins}" in where or "'zztest%'" in where, (table, where)
    by_table = dict(accounts.REMOVE_TABLES)
    assert by_table["specimen"] == "access_group = {gid}"
    assert by_table["login"] == "id in ({logins})"
    assert by_table["ant_group"] == "id = {gid}"
    assert "antcat" in by_table["taxon"] and "not in (select taxon_name from specimen)" in by_table["taxon"]


def test_names_and_secret_names_match_the_tests_and_workflow():
    assert accounts.GROUP_NAME == "ZZ Test Group (staging only)" and accounts.CODE_PREFIX == "zztest"
    workflow = open(os.path.join(ROOT, ".github", "workflows", "e2e.yaml"), encoding="utf-8").read()
    helper = open(os.path.join(ROOT, "tests", "e2e", "staging_accounts.py"), encoding="utf-8").read()
    for _name, secret, *_ in accounts.ACCOUNTS:
        for suffix in ("_USER", "_PASSWORD"):
            assert f"secrets.{secret}{suffix}" in workflow, secret + suffix
            assert f'"{secret}{suffix}"' in helper, secret + suffix
    assert f'"{accounts.GROUP_NAME}"' in helper and f'"{accounts.CODE_PREFIX}"' in helper


def main():
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("ok", name)


if __name__ == "__main__":
    main()
