"""tools/api/setup-usage-login.sh (step 6, API keys PR 2, 2026-11-08): the generated password is never printed, the
login gets only the key tables (api_key, api_usage_daily and, since PR 3, api_rate), and the settings file is backed up before it is changed."""
import re
import subprocess
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[2] / "tools/api/setup-usage-login.sh"


def test_script_parses():
    subprocess.run(["bash", "-n", str(SCRIPT)], check=True)


def test_password_is_never_printed():
    # the only echo/printf that uses the password appends it to the settings file
    uses = [l for l in SCRIPT.read_text().splitlines() if re.search(r"\b(echo|printf)\b", l) and "$PW" in l]
    assert len(uses) == 1 and uses[0].rstrip().endswith('>> "$P"'), uses


def test_grants_only_the_key_tables():
    grants = [l for l in SCRIPT.read_text().splitlines() if l.strip().startswith("grant ")]
    assert len(grants) == 3, grants
    assert all(re.search(r"on ant\.(api_key|api_usage_daily|api_rate) to 'antweb_api_usage'@'%'", g) for g in grants), grants
    assert all("all" not in g.split(" on ")[0] for g in grants), grants
    keys = [g for g in grants if "api_rate" not in g]
    assert len(keys) == 2 and all("select, insert, update" in g and "delete" not in g for g in keys), keys
    # PR 3 (2026-11-08): the v4 counters; the API deletes its old rows
    rate = [g for g in grants if "api_rate" in g]
    assert len(rate) == 1 and "select, insert, update, delete on ant.api_rate" in rate[0], rate


def test_rate_table_grant_also_for_an_existing_login():
    # the api_rate grant runs on every run, outside the block that creates the login (PR 2 servers get it too)
    text = SCRIPT.read_text()
    assert text.index("on ant.api_rate") > text.index("\nfi\n"), "api_rate grant must follow the create-login block"
    assert "2026-11-08-api-rate.sql" in text


def test_settings_file_backed_up_before_change():
    text = SCRIPT.read_text()
    assert text.index('cp -p "$P"') < text.index('>> "$P"')


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
    print("ok")
