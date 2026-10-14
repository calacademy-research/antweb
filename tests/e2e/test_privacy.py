"""Files that must not be readable by the public."""
import pytest

from antweb import BASE_URL, new_session


@pytest.mark.parametrize("path", ["/web/log/logins.txt"])
def test_private_file_not_public(path):
    r = new_session().get(BASE_URL + path, timeout=60)
    assert r.status_code in (403, 404), f"{path} is public (HTTP {r.status_code})"
