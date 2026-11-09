"""Free registered API keys and the limits of the AntWeb API v4 (step 6, PR 3; decided by Brian Fisher 2026-11-08).

What it does: checks the key a program sends in the X-API-Key header, decides the level (no key, registered,
collaborator, admin) and its limits, and counts every request in the database so that the limits hold across the
three gunicorn workers. Used only by api_v4.py; the v3.1 routes in api.py do not use it.

Keys: "aw_" plus random characters (new_key()). The key itself is never stored, logged or sent back: the database
holds only its SHA-256 (api_key.key_hash) and a short prefix for display. A key is accepted only from the
X-API-Key header and only with status 'active'.

Limits (requests a minute, requests a day, records per answer, records a day):
  no key (per visitor IP):  5, 50, 25, 1,250 (50 answers of 25)
  registered key:           60, 5,000, 500, 50,000
  collaborator and admin:   per key, from the api_key columns per_minute, per_day, max_records, records_per_day;
                            NULL = the registered value. The columns override the defaults for every level.
Records a day are reserved before the query runs and the unused part is given back after it, so paging with
offset stops at the daily record limit even with requests running at the same time in several workers.

Where the counts live (MySQL, written only with the API's usage login antweb_api_usage, usageUser= and
usagePassword= in etc/api-db.properties; the data login stays read-only):
  api_rate         requests in the current minute per key or per visitor, and requests and records per day per
                   visitor without a key (rows older than two days are deleted). db/upgrade/9.4.0/2026-11-08-api-rate.sql
  api_usage_daily  requests, records and refusals (HTTP 429) per key and day; key_id 0 = all visitors without a key.
  api_key          last_used (at most one update a minute per key).
The usage connections use READ COMMITTED and retry a transaction after a deadlock (retried()).
Visitors without a key are counted by a hash of their IP, for IPv6 of its /64 network (visitor_network(); SHA-256
with a salt that changes every day and is derived from the usage login's password), never by the IP itself. Days and minutes are UTC.

Docs: docs/flows/api.md, section "v4 and API keys". Tests: tests/unit/test_api_v4.py.
"""
import hashlib
import hmac
import ipaddress
import math
import re
import secrets
import time
from collections import namedtuple
from datetime import datetime, timedelta, timezone

from sqlalchemy import text
from sqlalchemy.exc import OperationalError

KEY_HEADER = 'X-API-Key'
KEY_START = 'aw_'
# "aw_" and 40 characters of base64url (new_key); a little room either way for keys made by hand.
KEY_RE = re.compile(r'^aw_[A-Za-z0-9_-]{20,80}$')
# Anything in a web address that looks like a key: refused (keys are accepted only in the header).
KEY_IN_URL_RE = re.compile(r'aw_[A-Za-z0-9_-]{16,}')
KEY_PARAM_NAMES = ('key', 'apikey', 'api_key', 'x-api-key', 'x_api_key', 'token', 'access_token')
DISPLAY_PREFIX_LENGTH = 7          # "aw_" and 4 characters, for the admin page

GET_A_KEY_URL = 'https://www.antweb.org/apiKey.do'
CONTACT = 'antweb@antweb.org'

Limits = namedtuple('Limits', 'per_minute per_day max_records records_per_day')
NO_KEY = Limits(per_minute=5, per_day=50, max_records=25, records_per_day=50 * 25)
REGISTERED = Limits(per_minute=60, per_day=5000, max_records=500, records_per_day=50000)
# Records in one answer, whatever a key's max_records says (one answer is held in memory by a worker).
ABSOLUTE_MAX_RECORDS = 10000
KEY_LEVELS = ('registered', 'collaborator', 'admin')
NO_KEY_LEVEL = 'none'

KeyRow = namedtuple('KeyRow', 'id level status per_minute per_day max_records records_per_day')
# Who is asking: key_id None for a visitor without a key; bucket names their api_rate rows.
Visitor = namedtuple('Visitor', 'key_id level bucket')
# The answer of UsageStore.admit(): ok, or the reason ('minute', 'day', 'records'), the limit and the wait.
Admission = namedtuple('Admission', 'ok reason limit retry_after reserved records_left requests_left')


def new_key():
    """A new key: "aw_" and 40 random characters (240 bits). Shown once to its owner, stored only as key_hash()."""
    return KEY_START + secrets.token_urlsafe(30)


def key_hash(key):
    """SHA-256 of the key, 64 hex characters (api_key.key_hash). Keys are long and random, so no salt is needed."""
    return hashlib.sha256(key.encode('utf-8')).hexdigest()


def display_prefix(key):
    return key[:DISPLAY_PREFIX_LENGTH]


def key_in_url(args):
    """True when the web address carries something that looks like a key, or a parameter named like one."""
    for name, value in args.items(multi=True):
        if name.strip().lower() in KEY_PARAM_NAMES or KEY_IN_URL_RE.search(name) or KEY_IN_URL_RE.search(value or ''):
            return True
    return False


def limits_for(row):
    """The limits of a key (KeyRow), or of visitors without a key (row None). A key's own columns win; NULL means
    the registered value, for every level."""
    if row is None:
        return NO_KEY

    def pick(own, default):
        return own if own is not None and own > 0 else default
    max_records = min(pick(row.max_records, REGISTERED.max_records), ABSOLUTE_MAX_RECORDS)
    return Limits(per_minute=pick(row.per_minute, REGISTERED.per_minute),
                  per_day=pick(row.per_day, REGISTERED.per_day),
                  max_records=max_records,
                  records_per_day=pick(row.records_per_day, REGISTERED.records_per_day))


def client_ip(headers, remote_addr):
    """The visitor, as elsewhere in AntWeb (util/HttpUtil.getClientIpAddress): CF-Connecting-IP (Cloudflare), else
    the last X-Forwarded-For entry (set by Caddy), else the TCP peer."""
    ip = (headers.get('CF-Connecting-IP') or '').strip()
    if ip:
        return ip
    xff = headers.get('X-Forwarded-For') or ''
    if xff.strip():
        return xff.split(',')[-1].strip()
    return remote_addr or ''


def visitor_network(ip):
    """What is counted as one visitor without a key: the IPv4 address itself, or the /64 network of an IPv6 address
    (one household or server usually holds a whole /64, so counting single IPv6 addresses would let one visitor take
    the no-key limits many times). An IPv4 address written as IPv6 (::ffff:a.b.c.d) counts as the IPv4 address.
    Anything that is not an address is counted as written."""
    try:
        addr = ipaddress.ip_address(ip.strip().split('%')[0])
    except ValueError:
        return ip
    if addr.version == 6:
        if addr.ipv4_mapped is not None:
            return str(addr.ipv4_mapped)
        return str(ipaddress.ip_network(str(addr) + '/64', strict=False))
    return str(addr)


def ip_hash(ip, secret, day):
    """SHA-256 of the IP with a salt of the day (HMAC of the date with the secret): the same visitor has the same hash
    all day in every worker, and the IP cannot be recovered without the secret."""
    salt = hmac.new(secret.encode('utf-8'), day.isoformat().encode('ascii'), hashlib.sha256).digest()
    return hashlib.sha256(salt + ip.encode('utf-8')).hexdigest()


def utcnow():
    return datetime.now(timezone.utc).replace(tzinfo=None)


def seconds_to_next_minute(now):
    return max(1, 60 - now.second)


def seconds_to_next_day(now):
    tomorrow = datetime(now.year, now.month, now.day) + timedelta(days=1)
    return max(1, int(math.ceil((tomorrow - now).total_seconds())))


UNITS = {'minute': 'requests a minute', 'day': 'requests a day', 'records': 'records a day'}


def limit_message(has_key, reason, limit, wait, citation):
    """The 429 text (Brian Fisher 2026-11-08). Visitors with a key are not told to get one."""
    who = 'your key' if has_key else 'visitors without a key'
    wait_text = '{:,} seconds'.format(wait)
    if wait >= 7200:
        wait_text += ' (about {:,} hours)'.format(int(round(wait / 3600.0)))
    message = 'You have reached the limit for {}: {:,} {}. Please wait {}. '.format(who, limit, UNITS[reason], wait_text)
    if not has_key:
        message += 'For higher limits, get a free API key at {}. '.format(GET_A_KEY_URL)
    message += 'Larger projects: write to {}. Please cite AntWeb: {}'.format(CONTACT, citation)
    return message


INVALID_KEY_MESSAGE = 'This API key is not valid. Get a free key at {}.'.format(GET_A_KEY_URL)
KEY_IN_URL_MESSAGE = ('Send your API key in the X-API-Key header, never in the web address (addresses end up in logs'
                      ' and browser history). Get a free key at {}.'.format(GET_A_KEY_URL))


# MySQL errors after which the whole transaction is tried again: deadlock (two workers creating the same new minute
# row at once; seen in a test with real MySQL 8.0, 2026-11-08) and lock wait timeout.
RETRY_ERRORS = (1213, 1205)
ATTEMPTS = 4


def retried(fn):
    """Run fn() again (up to ATTEMPTS times, a few milliseconds apart) after a deadlock or lock wait timeout."""
    for attempt in range(ATTEMPTS):
        try:
            return fn()
        except OperationalError as err:
            code = err.orig.args[0] if getattr(err, 'orig', None) is not None and err.orig.args else None
            if code not in RETRY_ERRORS or attempt == ATTEMPTS - 1:
                raise
            time.sleep(0.005 * (attempt + 1))


class UsageStore:
    """Key lookups and the counters, in the database the usage login may write (api_key, api_usage_daily, api_rate).
    Works on MySQL (servers) and SQLite (unit tests)."""

    CLEANUP_EVERY = timedelta(minutes=10)
    KEEP_RATE_ROWS = timedelta(days=2)
    TOUCH_EVERY = timedelta(minutes=1)

    def __init__(self, engine, secret):
        self.engine = engine
        self.secret = secret
        self.last_cleanup = None

    # --- keys ---
    def find_key(self, hashed):
        with self.engine.connect() as c:
            r = c.execute(text("select id, level, status, per_minute, per_day, max_records, records_per_day"
                               " from api_key where key_hash = :h"), {"h": hashed}).fetchone()
        return KeyRow(*r) if r else None

    def touch(self, key_id, now):
        with self.engine.begin() as c:
            c.execute(text("update api_key set last_used = :now where id = :id"
                           " and (last_used is null or last_used < :cutoff)"),
                      {"now": now, "id": key_id, "cutoff": now - self.TOUCH_EVERY})

    def visitor(self, row, ip, now):
        if row is not None:
            return Visitor(key_id=row.id, level=row.level if row.level in KEY_LEVELS else 'registered',
                           bucket='key:%d' % row.id)
        return Visitor(key_id=None, level=NO_KEY_LEVEL, bucket='ip:' + ip_hash(visitor_network(ip), self.secret, now.date()))

    # --- counters ---
    def _add(self, c, table, keys, adds):
        """Insert the row or add to it; returns (requests, records) after the change. The row stays locked (MySQL)
        until the transaction ends, so two workers cannot both take the last request of a limit."""
        cols = list(keys) + list(adds)
        params = dict(keys)
        params.update(adds)
        values = ', '.join(':' + k for k in cols)
        sets = ', '.join('%s = %s + :%s' % (k, k, k) for k in adds)
        if c.dialect.name == 'sqlite':
            sql = 'insert into %s (%s) values (%s) on conflict(%s) do update set %s' % (
                table, ', '.join(cols), values, ', '.join(keys), sets)
        else:
            sql = 'insert into %s (%s) values (%s) on duplicate key update %s' % (table, ', '.join(cols), values, sets)
        c.execute(text(sql), params)
        where = ' and '.join('%s = :%s' % (k, k) for k in keys)
        lock = '' if c.dialect.name == 'sqlite' else ' for update'
        return tuple(c.execute(text('select requests, records from %s where %s%s' % (table, where, lock)),
                               dict(keys)).fetchone())

    def _daily(self, c, visitor, now, requests, records):
        if visitor.key_id is not None:
            return self._add(c, 'api_usage_daily', {'day': now.date(), 'key_id': visitor.key_id},
                             {'requests': requests, 'records': records})
        return self._add(c, 'api_rate', {'bucket': visitor.bucket, 'period': 'd', 'start': _day_start(now)},
                         {'requests': requests, 'records': records})

    def _aggregate(self, c, visitor, now, requests, records):
        # key_id 0: all visitors without a key, for the admin page (a key's own row is its daily row)
        if visitor.key_id is None:
            self._add(c, 'api_usage_daily', {'day': now.date(), 'key_id': 0}, {'requests': requests, 'records': records})

    def admit(self, visitor, limits, wanted, now):
        """Count one request and reserve its records, or refuse it (nothing counted but a refusal)."""
        admission = retried(lambda: self._admit(visitor, limits, wanted, now))
        if not admission.ok:
            self.note_limited(visitor, now)
        return admission

    def _admit(self, visitor, limits, wanted, now):
        refused = None
        conn = self.engine.connect()
        trans = conn.begin()
        try:
            minute = self._add(conn, 'api_rate', {'bucket': visitor.bucket, 'period': 'm', 'start': _minute_start(now)},
                               {'requests': 1, 'records': 0})
            day = self._daily(conn, visitor, now, 1, 0) if minute[0] <= limits.per_minute else None
            left = limits.records_per_day - day[1] if day else 0
            if minute[0] > limits.per_minute:
                refused = Admission(False, 'minute', limits.per_minute, seconds_to_next_minute(now), 0, None, None)
            elif day[0] > limits.per_day:
                refused = Admission(False, 'day', limits.per_day, seconds_to_next_day(now), 0, None, None)
            elif left <= 0:
                refused = Admission(False, 'records', limits.records_per_day, seconds_to_next_day(now), 0, 0, None)
            if refused:
                trans.rollback()   # a refused request is not counted as a request, only as a refusal (below)
            else:
                reserve = min(limits.max_records, left, wanted if wanted else limits.max_records)
                self._daily(conn, visitor, now, 0, reserve)
                self._aggregate(conn, visitor, now, 1, reserve)
                trans.commit()
                return Admission(True, None, None, None, reserve, left - reserve, limits.per_day - day[0])
        except Exception:
            if trans.is_active:
                trans.rollback()
            raise
        finally:
            conn.close()
        return refused

    def settle(self, visitor, reserved, used, now):
        """Give back the reserved records the answer did not use."""
        unused = reserved - used
        if unused <= 0:
            return

        def give_back():
            with self.engine.begin() as c:
                self._daily(c, visitor, now, 0, -unused)
                self._aggregate(c, visitor, now, 0, -unused)
        retried(give_back)

    def note_limited(self, visitor, now):
        key_id = visitor.key_id if visitor.key_id is not None else 0

        def count():
            with self.engine.begin() as c:
                self._add(c, 'api_usage_daily', {'day': now.date(), 'key_id': key_id},
                          {'requests': 0, 'records': 0, 'limited': 1})
        retried(count)

    def cleanup(self, now):
        """Delete api_rate rows older than two days, at most every ten minutes per worker."""
        if self.last_cleanup is not None and now - self.last_cleanup < self.CLEANUP_EVERY:
            return
        self.last_cleanup = now
        with self.engine.begin() as c:
            c.execute(text("delete from api_rate where start < :cutoff"), {"cutoff": now - self.KEEP_RATE_ROWS})


def _minute_start(now):
    return now.replace(second=0, microsecond=0)


def _day_start(now):
    return datetime(now.year, now.month, now.day)
