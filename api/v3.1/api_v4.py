"""The AntWeb API v4 at https://www.antweb.org/v4/ (step 6, PR 3; decided by Brian Fisher 2026-11-08): the routes of
v3.1 with free registered API keys, limits per level and clearer field names.

How it works: api.py mounts this small Flask application under /v4 (mount(), werkzeug DispatcherMiddleware), so
the v3.1 application, its routes and its checks stay exactly as they were. Caddy sends /v4/ to the api container
without stripping the path (docker/caddy/Caddyfile and Caddyfile.common). For each data request v4:
  1. refuses a key in the web address (400), keys go only in the X-API-Key header;
  2. answers 503 when the usage login is not configured (never open without limits);
  3. checks the key (401 if unknown, not active or malformed) and the level (api_keys.py);
  4. while api.V4_OPEN is False (v4Open= in etc/api-db.properties, api.readV4Open()), answers only admin keys (503
     for everyone else); once it is True, everyone at their level's limits, and v3.1 itself answers 410 (api.apiEnded());
  5. refuses unknown parameters (400, with the v4 names);
  6. counts the request and reserves its records in the database, or answers 429 with Retry-After;
  7. runs the v3.1 route itself in an inner request (let in by flask.g.apiV4Inner, not up=1, with the level's
     record limit), renames the fields,
     adds localityCode, and gives back the records it did not use.

Field names in v4 (the v3.1 name, then the Darwin Core term):
  specimenCode         specimen code (v3.1 specimenCode; /taxaImages: code)     dwc:catalogNumber
  collectingEventCode  AntWeb's "collection code", e.g. JTL7095 (v3.1 collectionCode)   dwc:eventID / dwc:fieldNumber
  localityCode         AntWeb's locality code (not in v3.1)                      dwc:locationID
/specimens also takes collectingEventCode= and localityCode= as filters.

The key is never written to a log, an error or a reply: the inner request has no key header, and nothing here prints
headers. Docs: docs/flows/api.md, section "v4 and API keys". Tests: tests/unit/test_api_v4.py.
"""
import json
import urllib.parse

from flask import Flask, Response, g, request
from sqlalchemy import bindparam, column, create_engine, select, table, text
from sqlalchemy.engine import URL
from werkzeug.datastructures import MultiDict
from werkzeug.middleware.dispatcher import DispatcherMiddleware

import api_keys

api = None          # the api module (mount())
app = None          # the v4 Flask application
usage = None        # api_keys.UsageStore, None when the usage login is missing
clock = api_keys.utcnow

VERSION = 'v4'
PUBLIC_URL = 'https://www.antweb.org/v4'

FIELD_RENAMES = {'collectionCode': 'collectingEventCode'}
FIELDS = [
    {'name': 'specimenCode', 'v3.1': 'specimenCode (in /taxaImages: code)', 'darwinCore': 'catalogNumber',
     'meaning': 'The code of one specimen, e.g. CASENT0922626.'},
    {'name': 'collectingEventCode', 'v3.1': 'collectionCode', 'darwinCore': 'eventID, fieldNumber',
     'meaning': 'The collecting event (what AntWeb calls the collection code), e.g. JTL7095.'},
    {'name': 'localityCode', 'v3.1': '(not given)', 'darwinCore': 'locationID',
     'meaning': 'The locality where the specimen was collected, as coded by the collector.'},
]
# New filters of v4 (applied inside the v3.1 /specimens query through flask.g, api.v4SpecimenFilters()).
EXTRA_PARAMS = {'getSpecimen': ('collectingEventCode', 'localityCode')}
# Old or Darwin Core names, pointed to the v4 name in the 400 reply.
HINTS = {'collectionCode': 'collectingEventCode', 'catalogNumber': 'specimenCode', 'eventID': 'collectingEventCode',
         'fieldNumber': 'collectingEventCode', 'locationID': 'localityCode', 'code': 'specimenCode'}
# Routes whose records are specimens (they get localityCode).
SPECIMEN_LISTS = {'specimens': 'specimens', 'geoSpecimens': 'specimens'}
NOT_FOR_V4 = ('up', 'log')

NOT_CONFIGURED_MESSAGE = ('The AntWeb API v4 is not available: API keys are not configured on this server. Please try'
                          ' again later or write to antweb@antweb.org.')
NOT_OPEN_MESSAGE = ('The AntWeb API v4 is not open yet. It opens with free registered API keys in the coming weeks;'
                    ' see https://www.antweb.org/api.do for news.')
STORE_ERROR_MESSAGE = 'The request could not be counted, so it was not answered. Please try again in a minute.'


# --- set up ---------------------------------------------------------------------------------------------------

def usage_store(props):
    """The counters, with the usage login from etc/api-db.properties, or None without it."""
    creds = api._usage_credentials(props)
    if creds is None:
        print('API v4: no usage login (usageUser= and usagePassword=) in ' + api.API_PROPERTIES
              + '; v4 answers 503 until it is there', flush=True)
        return None
    url = URL.create('mysql+pymysql', username=creds[0], password=creds[1], host='mysql', port=3306, database='ant')
    # READ COMMITTED: no gap locks, so two workers adding the first request of a new minute rarely deadlock (and
    # api_keys.retried() tries again when they do).
    engine = create_engine(url, pool_recycle=280, pool_pre_ping=True, pool_size=2, max_overflow=4,
                           isolation_level='READ COMMITTED',
                           connect_args={'init_command': "SET SESSION time_zone = '+00:00'"})
    print('API v4 usage user:', creds[0], flush=True)
    return api_keys.UsageStore(engine, creds[1])


def mount(apimod):
    """Create the v4 application and serve it under /v4 of the v3.1 application (api.application)."""
    global api, app, usage
    api = apimod
    app = Flask('api_v4')
    app.json = api.AntwebJSONProvider(app)
    app.after_request(api.addLicenseHeader)
    app.teardown_appcontext(lambda exc=None: api.session.remove())
    app.add_url_rule('/', 'front', front)
    app.add_url_rule('/<name>', 'data', data)
    app.register_error_handler(Exception, handle_uncaught)
    usage = usage_store(api._apiProps)
    api.application.wsgi_app = DispatcherMiddleware(api.application.wsgi_app, {'/v4': app})
    return app


def routes():
    """v4 route name -> v3.1 endpoint, from the v3.1 url map (every data route; not / and static)."""
    out = {}
    for rule in api.application.url_map.iter_rules():
        if rule.endpoint in api.UNCHECKED_ENDPOINTS or '<' in rule.rule:
            continue
        out[rule.rule.lstrip('/')] = rule.endpoint
    return out


def accepted(endpoint):
    names = [p for p in api.acceptedParams(endpoint) if p not in NOT_FOR_V4]
    return names + [p for p in EXTRA_PARAMS.get(endpoint, ()) if p not in names]


def status():
    if usage is None:
        return 'not configured'
    return 'open' if api.V4_OPEN else 'closed'


# --- replies --------------------------------------------------------------------------------------------------

def reply(body, code, headers=None):
    response = app.json.response(body)
    response.status_code = code
    for k, v in (headers or {}).items():
        response.headers[k] = v
    return response


def error(message, code, headers=None, **extra):
    body = {'error': message}
    body.update(extra)
    return reply(body, code, headers)


def handle_uncaught(err):
    from werkzeug.exceptions import HTTPException
    if isinstance(err, HTTPException):
        return error(err.name, err.code)
    # the type and message only (no request headers, so never the key)
    print('API v4 error: ' + type(err).__name__ + ': ' + str(err)[:300], flush=True)
    return error('The request could not be completed. Please try again later or simplify the query.', 500)


def front():
    """The v4 front door: status, how to get a key, the limits, the field names. Always 200."""
    lim = {'withoutKey': _limits_dict(api_keys.NO_KEY), 'registeredKey': _limits_dict(api_keys.REGISTERED),
           'collaborator': 'set per key by the AntWeb team',
           'note': 'Limits without a key are per visitor (IP address). Days and minutes are UTC.'}
    messages = {'open': 'The AntWeb API v4 is open. Send your free key in the X-API-Key header.',
                'closed': NOT_OPEN_MESSAGE, 'not configured': NOT_CONFIGURED_MESSAGE}
    st = status()
    return reply({
        'name': 'Antweb API v4', 'status': st, 'message': messages[st],
        'getAKey': api_keys.GET_A_KEY_URL, 'keyHeader': api_keys.KEY_HEADER, 'limits': lim,
        'routes': ['/v4/' + r for r in sorted(routes())], 'fieldNames': FIELDS,
        'documentation': api.API_DOCS_URL, 'terms': api.API_TERMS_URL, 'guideForPrograms': api.LLMS_URL,
        'terms_of_use': api.API_TERMS, 'contact': api_keys.CONTACT, 'citation': api.CITATION,
        'license': api.LICENSE,
    }, 200)


def _limits_dict(lim):
    return {'requestsPerMinute': lim.per_minute, 'requestsPerDay': lim.per_day, 'recordsPerAnswer': lim.max_records,
            'recordsPerDay': lim.records_per_day}


# --- a data request -------------------------------------------------------------------------------------------

def data(name):
    endpoint = routes().get(name)
    if endpoint is None:
        return error('Not Found', 404)
    if api_keys.key_in_url(request.args):
        return error(api_keys.KEY_IN_URL_MESSAGE, 400)
    if usage is None:
        return error(NOT_CONFIGURED_MESSAGE, 503, {'Retry-After': '3600'})
    now = clock()
    try:
        row = None
        sent = request.headers.get(api_keys.KEY_HEADER)
        if sent is not None:
            sent = sent.strip()
            row = usage.find_key(api_keys.key_hash(sent)) if api_keys.KEY_RE.match(sent) else None
            sent = None
            if row is None or row.status != 'active':
                return error(api_keys.INVALID_KEY_MESSAGE, 401)
    except Exception as err:  # noqa: BLE001  the database of the counters: closed, never open without limits
        return store_error(err)
    if not api.V4_OPEN and (row is None or (row.level or '').lower() != 'admin'):
        return error(NOT_OPEN_MESSAGE, 503, {'Retry-After': '86400'}, documentation=api.API_DOCS_URL,
                     terms=api.API_TERMS_URL)
    bad = check_params(endpoint)
    if bad is not None:
        return bad

    limits = api_keys.limits_for(row)
    ip = api_keys.client_ip(request.headers, request.remote_addr)
    try:
        visitor = usage.visitor(row, ip, now)
        usage.cleanup(now)
        admission = usage.admit(visitor, limits, wanted_limit(), now)
    except Exception as err:  # noqa: BLE001
        return store_error(err)
    if not admission.ok:
        return too_many(row is not None, admission)

    used = 0
    try:
        response, used = run(name, endpoint, admission, row is not None, limits)
        return response
    finally:
        try:
            usage.settle(visitor, admission.reserved, used, now)
            if row is not None:
                usage.touch(row.id, now)
        except Exception as err:  # noqa: BLE001
            print('API v4: could not settle the counts: ' + type(err).__name__, flush=True)


def store_error(err):
    print('API v4 usage store error: ' + type(err).__name__ + ': ' + str(err)[:300], flush=True)
    return error(STORE_ERROR_MESSAGE, 503, {'Retry-After': '60'})


def too_many(has_key, admission):
    message = api_keys.limit_message(has_key, admission.reason, admission.limit, admission.retry_after, api.CITATION)
    return error(message, 429, {'Retry-After': str(admission.retry_after)}, retryAfter=admission.retry_after,
                 getAKey=api_keys.GET_A_KEY_URL, contact=api_keys.CONTACT, citation=api.CITATION)


def wanted_limit():
    try:
        n = int(request.args.get('limit', ''))
    except ValueError:
        return None
    return n if n > 0 else None


def check_params(endpoint):
    names = accepted(endpoint)
    unknown = [k for k in request.args.keys() if k and k not in names]
    if not unknown:
        return None
    message = 'Unknown parameter: ' + ', '.join(unknown) + '.'
    did_you_mean = {}
    for k in unknown:
        close = [HINTS[k]] if HINTS.get(k) in names else api.suggestParams(k, names)
        if close:
            did_you_mean[k] = close
            message += ' Did you mean ' + ' or '.join(close) + ' (for ' + k + ')?'
    extra = {'unknownParameters': unknown, 'acceptedParameters': names, 'documentation': PUBLIC_URL + '/'}
    if did_you_mean:
        extra['didYouMean'] = did_you_mean
    return error(message, 400, **extra)


def run(name, endpoint, admission, has_key, limits):
    """The v3.1 route in an inner request (g.apiV4Inner) with limit = the reserved records; returns (response, records)."""
    filters = {}
    inner = MultiDict()
    for k, values in request.args.lists():
        if k in EXTRA_PARAMS.get(endpoint, ()):
            filters[k] = values[-1]
        elif k != 'limit':
            for v in values:
                inner.add(k, v)
    inner['limit'] = str(admission.reserved)
    ndjson = request.args.get('ndjson') == 'true'
    query = urllib.parse.urlencode(list(inner.items(multi=True)))
    with api.application.test_request_context('/' + name, query_string=query, method='GET'):
        g.apiV4Inner = True     # lets the inner request past checkUp()/down() (never up=1)
        g.apiMaxLimit = admission.reserved
        g.apiV4Filters = filters
        inner_response = api.application.full_dispatch_request()
        body = inner_response.get_data()
        code = inner_response.status_code
        link = inner_response.headers.get('Link')
        content_type = inner_response.headers.get('Content-Type')
    if code != 200:
        response = Response(body, status=code, content_type=content_type)
        return response, 0
    if ndjson:
        records = ndjson_records(body)
        records = records[:admission.reserved]
        add_locality_codes(name, records)
        lines = []
        for i, datum in enumerate(records):
            lines.append(app.json.dumps({"index": {"_index": "specimens", "_id": i}}, sort_keys=False))
            lines.append(app.json.dumps(datum, indent=None, ensure_ascii=False))
        response = Response(''.join(line + '\n' for line in lines).encode('utf8'),
                            content_type='application/json; charset=utf-8')
        count = len(records)
    else:
        obj = rename(json.loads(body))
        key = record_list(obj)
        count = 0
        if key is not None:
            if len(obj[key]) > admission.reserved:
                obj[key] = obj[key][:admission.reserved]
            count = len(obj[key])
            if SPECIMEN_LISTS.get(name) == key:
                add_locality_codes(name, obj[key])
        notice = limit_notice(has_key, limits, admission, count)
        for meta in ('metaData', '_metaData'):
            if isinstance(obj.get(meta), dict):
                obj[meta]['parameters'] = [request.args.to_dict()]
                obj[meta]['request'] = 'GET ' + request.script_root + request.full_path.rstrip('?')
                obj[meta].pop('limitNotice', None)
                if notice:
                    obj[meta]['limitNotice'] = notice
                obj[meta]['level'] = level_name(has_key)
                obj[meta]['recordsLeftToday'] = admission.records_left + (admission.reserved - count)
        response = app.json.response(obj)
    notice = limit_notice(has_key, limits, admission, count)
    if notice:
        response.headers[api.LIMIT_HEADER] = notice
    if link:
        response.headers['Link'] = link
    response.headers['X-AntWeb-Level'] = level_name(has_key)
    response.headers['X-AntWeb-Records-Left-Today'] = str(admission.records_left + (admission.reserved - count))
    return response, count


def level_name(has_key):
    return 'key' if has_key else 'no key'


def limit_notice(has_key, limits, admission, count):
    """The notice when the answer was cut: more was asked for than was given, or no limit was given and the answer is
    full (there may be more; the next page is offset=)."""
    wanted = wanted_limit()
    if (wanted is not None and wanted > admission.reserved) or (wanted is None and count >= admission.reserved):
        who = 'with your key' if has_key else 'without a key'
        text_ = 'Answers {} hold at most {:,} records, and {:,} records a day.'.format(
            who, limits.max_records, limits.records_per_day)
        text_ += ' This answer holds {:,}; ask for the next ones with offset=.'.format(count)
        if not has_key:
            text_ += ' For more, get a free API key at {}.'.format(api_keys.GET_A_KEY_URL)
        return text_
    return None


def rename(value, parent=None):
    """v4 field names: collectionCode -> collectingEventCode everywhere; code -> specimenCode in /taxaImages
    specimens."""
    if isinstance(value, list):
        return [rename(v, parent) for v in value]
    if isinstance(value, dict):
        out = {}
        for k, v in value.items():
            new = FIELD_RENAMES.get(k, k)
            if k == 'code' and parent == 'specimen':
                new = 'specimenCode'
            out[new] = rename(v, k)
        return out
    return value


def record_list(obj):
    lists = [k for k, v in obj.items() if isinstance(v, list) and k != 'license']
    return lists[0] if lists else None


def ndjson_records(body):
    records = []
    lines = [line for line in body.decode('utf8').split('\n') if line.strip()]
    for line in lines[1::2]:
        records.append(rename(json.loads(line)))
    return records


def add_locality_codes(name, records):
    """localityCode for each specimen record (the api3_1_specimen view has no locality code; the specimen table has)."""
    if SPECIMEN_LISTS.get(name) is None:
        return
    codes = sorted({r.get('specimenCode') for r in records if isinstance(r, dict) and r.get('specimenCode')})
    found = {}
    if codes:
        stmt = text('select code, localitycode from specimen where code in :codes').bindparams(
            bindparam('codes', expanding=True))
        try:
            found = {c: lc for c, lc in api.session.execute(stmt, {'codes': codes})}
        finally:
            api.session.remove()
    for r in records:
        if isinstance(r, dict) and 'specimenCode' in r:
            r['localityCode'] = found.get(r['specimenCode'])


# --- used by api.getSpecimen (inside the inner request) --------------------------------------------------------

_specimen = table('specimen', column('code'), column('localitycode'))


def specimen_filters(query, Specimen):
    """The v4 filters collectingEventCode= and localityCode= on the /specimens query; nothing outside v4."""
    f = getattr(g, 'apiV4Filters', None)
    if not f:
        return query
    if f.get('collectingEventCode'):
        query = query.filter(Specimen.collectionCode == f['collectingEventCode'])
    if f.get('localityCode'):
        query = query.filter(Specimen.code.in_(select(_specimen.c.code).where(_specimen.c.localitycode == f['localityCode'])))
    return query
