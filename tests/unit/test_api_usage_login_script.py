"""tools/api/setup-usage-login.sh (step 6, API keys PR 2, 2026-11-08): the generated password is never printed, the
login gets only the key tables (api_key, api_usage_daily and, since PR 3, api_rate; on api_key only the columns of the
key lookup and an update of last_used), broader grants are revoked on every run, the file and MySQL always have the same
password, the password is never on a command line, and the settings file is backed up before it is changed."""
import re
import subprocess
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[2] / "tools/api/setup-usage-login.sh"


def test_script_parses():
    subprocess.run(["bash", "-n", str(SCRIPT)], check=True)


def test_password_is_never_printed():
    # the only echo/printf that uses the password appends it to the settings file
    lines = [l.split("||", 1)[-1] for l in SCRIPT.read_text().splitlines()]   # not the test in front of || (no output)
    uses = [l for l in lines if re.search(r"\b(echo|printf)\b", l) and "$PW" in l]
    assert len(uses) == 1 and uses[0].rstrip().endswith('>> "$P"'), uses


def grant_lines():
    return [l.strip() for l in SCRIPT.read_text().splitlines() if l.strip().startswith("grant ")]


def test_grants_only_the_key_tables():
    grants = grant_lines()
    assert len(grants) == 3, grants
    assert all(re.search(r"on ant\.(api_key|api_usage_daily|api_rate) to 'antweb_api_usage'@'%';$", g) for g in grants), grants
    assert all(" all" not in g.split(" on ")[0] and "grant option" not in g for g in grants), grants
    daily = [g for g in grants if "api_usage_daily" in g]
    assert daily == ["grant select, insert, update on ant.api_usage_daily to 'antweb_api_usage'@'%';"], daily
    # PR 3 (2026-11-08): the v4 counters; the API deletes its old rows
    rate = [g for g in grants if "api_rate" in g]
    assert rate == ["grant select, insert, update, delete on ant.api_rate to 'antweb_api_usage'@'%';"], rate


def test_api_key_least_privilege():
    # api_key: only the columns of the key lookup (and last_used, for the WHERE of its own update); update last_used
    # only; no insert or delete; never name, email, institution, project, note or the confirmation token
    key = [g for g in grant_lines() if "ant.api_key " in g]
    assert len(key) == 1, key
    m = re.fullmatch(r"grant select \(([^)]*)\), update \(last_used\) on ant\.api_key to 'antweb_api_usage'@'%';", key[0])
    assert m, key[0]
    cols = [c.strip() for c in m.group(1).split(",")]
    assert sorted(cols) == sorted(["id", "key_hash", "level", "status", "per_minute", "per_day", "max_records",
                                   "records_per_day", "last_used"]), cols
    # the API's own queries need no more than this
    api_keys_py = (SCRIPT.parents[2] / "api/v3.1/api_keys.py").read_text()
    assert "select id, level, status, per_minute, per_day, max_records, records_per_day\"\n" in api_keys_py
    assert "update api_key set last_used = :now where id = :id" in api_keys_py
    assert "insert into api_key" not in api_keys_py and "delete from api_key" not in api_keys_py


def test_broader_grants_are_revoked_on_every_run():
    text = SCRIPT.read_text()
    revoke = "revoke all privileges, grant option from 'antweb_api_usage'@'%';"
    assert revoke in text
    # after the create-login block, before the grants, in the same mysql session (one heredoc)
    i = text.index(revoke)
    assert text.index("\nfi\n") < i < min(text.index(g) for g in grant_lines())
    block = text[text.rindex("q <<SQL", 0, i):text.index("\nSQL\n", i)]
    assert all(g in block for g in grant_lines()), "the revoke and the grants go in one session"


def test_rate_table_grant_also_for_an_existing_login():
    # the grants run on every run, outside the block that creates the login (PR 2 servers get them too)
    text = SCRIPT.read_text()
    assert text.index("on ant.api_rate") > text.index("\nfi\n"), "api_rate grant must follow the create-login block"
    assert "2026-11-08-api-rate.sql" in text


def test_file_and_mysql_password_agree():
    text = SCRIPT.read_text()
    # both branches: create if missing, then set the password the file has (new or kept)
    assert text.count("create user if not exists 'antweb_api_usage'@'%' identified by '$PW';") == 2
    assert text.count("alter user 'antweb_api_usage'@'%' identified by '$PW';") == 2
    new = text[text.index("\nelse\n"):text.index("\nfi\n")]
    assert new.index("alter user") < new.index('>> "$P"')


def test_password_never_on_a_command_line():
    # docker exec ... -e MYSQL_PWD="<value>" puts the password into ps; the admin login is passed by name only
    # (-e MYSQL_PWD, from the environment), the usage login as an option file on stdin
    text = SCRIPT.read_text()
    assert 'MYSQL_PWD="' not in text.split("export MYSQL_PWD=")[1], "no MYSQL_PWD=value on a docker command line"
    assert "usagePassword" not in "".join(l for l in text.splitlines() if "docker exec" in l and "printf" not in l)
    assert "--defaults-extra-file=/dev/stdin" in text and "docker exec -i antweb_mysql_1 mysql --defaults-extra-file" in text
    assert not re.search(r"mysql[^\n]*\s-p\S", text), "no -p<password>"


def test_settings_file_backed_up_before_change():
    text = SCRIPT.read_text()
    assert text.index('cp -p "$P"') < text.index('>> "$P"')


def test_the_api_image_can_sign_in_as_the_usage_login():
    """The login is made by MySQL 8.4 with its default method, caching_sha2_password; PyMySQL signs in with
    it only when the cryptography package is installed (staging 2026-11-12: every v4 request was 503,
    "'cryptography' package is required for sha256_password or caching_sha2_password auth methods")."""
    req = (SCRIPT.parent.parent.parent / "api" / "v3.1" / "requirements.txt").read_text()
    names = [line.split("==")[0].split("~=")[0].strip().lower() for line in req.splitlines()
             if line.strip() and not line.lstrip().startswith("#")]
    assert "pymysql" in names and "cryptography" in names, names
    assert "identified with mysql_native_password" not in SCRIPT.read_text().lower()


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
    print("ok")
