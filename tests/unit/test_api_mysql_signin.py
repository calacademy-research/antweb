"""The API's sign-in to MySQL 8.4 with caching_sha2_password, the path that needs the cryptography package (review of
#362, 2026-11-14). Production's usage login (antweb_api_usage) signs in this way without SSL: the first sign-in after a
restart has no cached password on the server, so PyMySQL asks for the server's RSA key and encrypts the password with
cryptography (pymysql._auth.sha2_rsa_encrypt). Without a working cryptography every v4 request is refused with 503
(staging, 2026-11-12), and a push to master reaches production before the staging tests, so CI checks it in the built
API image:

  1. sha2_rsa_encrypt with a key made here (no database);
  2. with MYSQL_HOST set (CI starts a mysql:8.4 container and a caching_sha2_password user): a real first sign-in
     without SSL, then a second one (the server's cached, fast path), each running "select 1".

Run in the API image: docker run --rm --entrypoint python -e MYSQL_HOST=... antweb/api:dev /t/test_api_mysql_signin.py
"""
import os

import cryptography
import pymysql
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from pymysql import _auth


def test_the_password_is_encrypted_with_the_servers_key():
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    pem = key.public_key().public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo)
    encrypted = _auth.sha2_rsa_encrypt(b"a password", b"0123456789abcdefghij", pem)
    assert len(encrypted) == 256, len(encrypted)


def test_a_first_and_a_cached_sign_in_without_ssl():
    host = os.environ.get("MYSQL_HOST")
    if not host:
        print("skip: MYSQL_HOST is not set")
        return
    for attempt in ("first (full authentication, RSA)", "second (cached)"):
        c = pymysql.connect(host=host, user=os.environ.get("MYSQL_USER", "probe"),
                            password=os.environ.get("MYSQL_PASSWORD", "ci-probe"), ssl_disabled=True, connect_timeout=20)
        try:
            with c.cursor() as cur:
                cur.execute("select 1")
                assert cur.fetchone() == (1,), attempt
                cur.execute("select current_user(), @@version")
                print(attempt, cur.fetchone())
        finally:
            c.close()


if __name__ == "__main__":
    print("cryptography", cryptography.__version__, "PyMySQL", getattr(pymysql, "VERSION_STRING", "?"))
    test_the_password_is_encrypted_with_the_servers_key()
    print("ok   test_the_password_is_encrypted_with_the_servers_key")
    test_a_first_and_a_cached_sign_in_without_ssl()
    print("ok   test_a_first_and_a_cached_sign_in_without_ssl")
