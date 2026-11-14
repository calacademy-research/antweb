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

Complete sets (2026-11-10; rows() below): the list routes /specimens, /geoSpecimens and /taxa say in metaData how
many records match the filters (total, on the first page or with total=true), how many this answer holds (returned)
and whether it reaches the end (complete). /specimens and /geoSpecimens take afterCode=<specimen code>: the records
after that code in specimen code order, so a set fetched page by page neither skips nor repeats a record when
records are added or removed meanwhile; metaData.nextAfterCode is the code to send next.

Countries and islands (Brian Fisher, 2026-11-11; COUNTRY below): country= is the stored country of the specimen and
nothing else. An oceanic island that is its own country in AntWeb (Hawaii, Galapagos Islands) is asked for by its own
name and is never part of its political parent.

Ants only (Brian Fisher, 2026-11-10; FAMILIES below): every route that reads specimens and takes a place filter
(/specimens, /geoSpecimens, /geoSpeciesStats, /distinctTaxa, /measurements) answers for ants unless more is asked for,
with the same test the data extract uses (family Formicidae, or no family given): one definition (ANTS), one helper
(ants_only()). allFamilies=true gives every family, family=<name> on /specimens that family; metaData.families says
which was used.

The key is never written to a log, an error or a reply: the inner request has no key header, and nothing here prints
headers. Docs: docs/flows/api.md, section "v4 and API keys". Tests: tests/unit/test_api_v4.py.
"""
import json
import urllib.parse

from flask import Flask, Response, g, request
from sqlalchemy import bindparam, column, create_engine, func, select, table, text
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
# afterCode and total (rows()) are v4 names too: they reach the query through flask.g, never the v3.1 parameters.
# allFamilies (ants_only()) is a v4 name too.
EXTRA_PARAMS = {'getSpecimen': ('collectingEventCode', 'localityCode', 'afterCode', 'total', 'allFamilies'),
                'getGeoSpecimens': ('afterCode', 'total', 'allFamilies'),
                'getGeoSpeciesStats': ('allFamilies',), 'getDistinctTaxa': ('allFamilies',),
                'getMeasurements': ('allFamilies',),
                'getTaxa': ('total',)}
# Old or Darwin Core names, pointed to the v4 name in the 400 reply.
HINTS = {'collectionCode': 'collectingEventCode', 'catalogNumber': 'specimenCode', 'eventID': 'collectingEventCode',
         'fieldNumber': 'collectingEventCode', 'locationID': 'localityCode', 'code': 'specimenCode',
         'after': 'afterCode', 'cursor': 'afterCode', 'allfamilies': 'allFamilies', 'all_families': 'allFamilies',
         'antsOnly': 'allFamilies', 'ants_only': 'allFamilies', 'families': 'allFamilies'}
# Routes whose records are specimens (they get localityCode).
SPECIMEN_LISTS = {'specimens': 'specimens', 'geoSpecimens': 'specimens'}
NOT_FOR_V4 = ('up', 'log')
# Complete sets: what the list routes say and take (the v4 front door and docs/flows/api.md).
PAGING = {
    'routes': ['/v4/specimens', '/v4/geoSpecimens', '/v4/taxa'],
    'total': 'metaData.total is the number of records that match the filters, whatever limit is. It is given on the'
             ' first page (no offset, no afterCode) and on any page with total=true; total=false leaves it out.'
             ' A /v4/specimens request without any filter gets no total unless it says total=true (counting every'
             ' specimen record takes long); metaData.totalNotGiven then says so. total=true without a filter needs an API key.'
             ' A count that takes longer than 3 seconds (20 with a key) is not given either; metaData.totalNotGiven'
             ' says so, and the records are complete page by page. total=true with a key is always counted to the end.',
    'returned': 'metaData.returned is the number of records in this answer.',
    'complete': 'metaData.complete is true when this answer reaches the end of the set.',
    'afterCode': 'On /v4/specimens and /v4/geoSpecimens, afterCode=<specimen code> gives the records after that code'
                 ' in specimen code order. metaData.nextAfterCode is the last code of this answer; send it as'
                 ' afterCode for the next one, until complete is true. Use afterCode or offset, not both.',
    'ndjson': 'With ndjson=true the same values are in the headers X-AntWeb-Total, X-AntWeb-Returned,'
              ' X-AntWeb-Complete and X-AntWeb-Next-After-Code (URL-encoded).',
}
# What country= means (the v4 front door and docs/flows/api.md). Brian Fisher, 2026-11-11: "An oceanic island or
# archipelago with its own ant fauna is its own country in AntWeb. Its specimens never count toward the political
# parent." This reverses the rule of 2026-11-10 (an island filed under a country was part of that country, Hawaii
# under United States). Nothing in the query does it: country= compares the stored specimen.country (api.countryIs),
# which is the island's own name for an island's specimens (tools/place/island_countries.py); the island column is
# never read. So the rule holds on a server once that tool has moved the stored specimens.
COUNTRY = {
    'country': 'country=<name> gives the specimen records whose country in AntWeb is that country: the whole name, in'
               ' any case. An old name AntWeb knows (USA) gives the current country (United States), and'
               ' metaData.countryName names both.',
    'islands': 'An oceanic island or archipelago with its own ant fauna is its own country in AntWeb (Hawaii,'
               ' Galapagos Islands). Ask for it by its own name (country=Hawaii). Its records are never part of its'
               ' political parent: country=United States gives no record of Hawaii.',
    'noCountry': 'A record with no country is in no country search.',
}
# Ants only by default (the v4 front door and docs/flows/api.md). Brian Fisher's definition of 2026-11-10: "Country
# search: every ant specimen record whose country in AntWeb is the country", the same definition as the data extract
# (after his question about the first Nearctic file: "why did you include non ants for an ant study?"). AntWeb also
# holds beetles, wasps, spiders and other animals found with ants (production: 1,289 of the 200,075 specimens of United
# States, Canada and Mexico). The test is the extract's own, word for word (tools/export/specimen_extract.py ANTS,
# DataExtract.ANTS): family Formicidae, or no family given. ANTS below is that text with the table's name left open,
# the one definition every route uses through ants_only(), and tests/unit/test_api_paging.py compares it with the
# extract's. The name of the choice is the extract's too (--all-families, the form's allFamilies).
# Where: every route that reads specimens and takes a place filter (FAMILY_ROUTES). Not /images and /taxaImages
# (images by specimen code or name, no place filter), and not the routes that give names and lists from other tables
# (/taxa, /geolocales, /geolocaleTaxa, /unimagedGeolocaleTaxa, /bioregions, /bioregionTaxa).
# v3.1 is not changed: it answers for every family, as before.
ANTS = "({s}.family = 'formicidae' or {s}.family is null or {s}.family = '')"     # {s}: the specimen table or its alias
ANTS_ONLY, ALL_FAMILIES, ONE_FAMILY = 'antsOnly', 'allFamilies', 'family'
FAMILY_ROUTES = ('getSpecimen', 'getGeoSpecimens', 'getGeoSpeciesStats', 'getDistinctTaxa', 'getMeasurements')
FAMILIES = {
    'routes': ['/v4/specimens', '/v4/geoSpecimens', '/v4/geoSpeciesStats', '/v4/distinctTaxa', '/v4/measurements'],
    'antsOnly': 'These routes answer from the specimen records of ants only unless you ask for more: family'
                ' Formicidae, and the records with no family given. That holds for the records, for metaData.total,'
                ' for the lists of names (/v4/distinctTaxa, /v4/geoSpecimens with distinct=), for the counts of'
                ' /v4/geoSpeciesStats and for the specimens whose measurements /v4/measurements gives. AntWeb also'
                ' holds beetles, wasps, spiders and other animals found with ants; the default leaves them out.',
    'allFamilies': 'allFamilies=true gives the records of every family.',
    'family': 'On /v4/specimens, family=<name> gives the records of that one family (family=vespidae).',
    'metaData': 'metaData.families says which was used: antsOnly, allFamilies or family. With ndjson=true it is in'
                ' the header X-AntWeb-Families. An empty /v4/specimens answer for specimenCode=<a specimen of another family> says'
                ' so in metaData.familiesNote.',
    'otherRoutes': 'The other routes have no family rule: /v4/images and /v4/taxaImages give images by specimen code'
                   ' or name, and the taxon, geolocale and bioregion routes give names and lists, not specimen'
                   ' records.',
}
ALL_FAMILIES_VALUE_MESSAGE = 'allFamilies must be true or false.'
FAMILY_ALL_MESSAGE = ('family=all is not the name of a family. For the records of every family send allFamilies=true;'
                      ' without it the answer holds ants only.')
FAMILIES_HEADER = 'X-AntWeb-Families'

# No total for a /specimens request without a filter, unless it asks (total=true). Review of pull request 348,
# 2026-11-12: the first page of every such request, with or without a key, made MySQL count the whole view
# api3_1_specimen (it joins taxon, and the ants-only test reads every row), and only the per-minute limit stood in
# the way. A request with a filter keeps its total on the first page: that is the number a collaborator needs to
# know a set is complete, and the filter is what makes the count small. Parameters that page, format or choose the
# families are not filters; a blank value or * is none either. /geoSpecimens always has its area (coords), and
# /taxa counts the far smaller taxon table: both keep their totals.
NO_TOTAL_WITHOUT_FILTER = ('getSpecimen',)
NOT_FILTERS = frozenset(('limit', 'offset', 'afterCode', 'total', 'ndjson', 'allFamilies'))
TOTAL_NOT_GIVEN = ('No total is given for a request without a filter: counting every specimen record takes long.'
                   ' Add total=true to get it, or add a filter (for example country=Madagascar).')
TOTAL_NOT_GIVEN_HEADER = 'X-AntWeb-Total-Not-Given'
# A count may take this long (MySQL max_execution_time, for the count only; the records are read as before), 2026-11-13:
# a filter that matches nearly every record (status=valid, georeferenced=true) passes without_filter() and was
# counted on every first page, a read of the whole view by anyone without a key. Longer with a key.
COUNT_MS = {False: 3000, True: 20000}    # total=true with a key: no limit of its own (count_within)
TOTAL_TOO_LONG = ('No total is given: counting the records that match these filters took longer than {s} seconds.'
                  ' The records are complete page by page: go on with {next} until metaData.complete is true.{key}')
# how to go on, by route (review of #353): afterCode where the route takes it, offset elsewhere (/taxa, distinct lists)
TOTAL_TOO_LONG_NEXT = {True: 'afterCode', False: 'offset'}
TOTAL_TOO_LONG_KEY = ' With an API key a count may take longer (X-API-Key header; free keys: {url}/apiKey.do).'
TOTAL_TOO_LONG_HAS_KEY = ' Add total=true to have the records counted to the end.'

BOTH_PAGINGS_MESSAGE = ('Use afterCode or offset, not both. afterCode=<the last specimen code you received> gives the'
                        ' records after it; offset=<n> leaves out the first n records.')
AFTER_CODE_DISTINCT_MESSAGE = 'afterCode pages specimen records; it cannot be used with distinct.'
TOTAL_VALUE_MESSAGE = 'total must be true or false.'

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
        'routes': ['/v4/' + r for r in sorted(routes())], 'fieldNames': FIELDS, 'paging': PAGING,
        'countries': COUNTRY, 'families': FAMILIES,
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
    bad = (check_params(endpoint) or check_paging(endpoint) or check_families(endpoint)
           or check_total_needs_key(endpoint, row is not None))
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


def check_paging(endpoint):
    """400 for afterCode with offset (two ways of paging in one request), afterCode with distinct (the distinct lists
    are names, not specimens) and a total that is not true or false. Before the request is counted."""
    own = EXTRA_PARAMS.get(endpoint, ())
    if 'total' in own and request.args.get('total') not in (None, 'true', 'false'):
        return error(TOTAL_VALUE_MESSAGE, 400, documentation=PUBLIC_URL + '/')
    if 'afterCode' in own and 'afterCode' in request.args:
        if 'offset' in request.args:
            return error(BOTH_PAGINGS_MESSAGE, 400, documentation=PUBLIC_URL + '/')
        if 'distinct' in request.args:
            return error(AFTER_CODE_DISTINCT_MESSAGE, 400, documentation=PUBLIC_URL + '/')
    return None


def check_families(endpoint):
    """400 for an allFamilies that is not true or false, and for family=all (it would match no family and give an
    empty answer). Before the request is counted."""
    if endpoint not in FAMILY_ROUTES:
        return None
    if request.args.get('allFamilies') not in (None, 'true', 'false'):
        return error(ALL_FAMILIES_VALUE_MESSAGE, 400, documentation=PUBLIC_URL + '/')
    if (request.args.get('family') or '').strip().lower() == 'all':
        return error(FAMILY_ALL_MESSAGE, 400, documentation=PUBLIC_URL + '/')
    return None


def family_asked(args):
    """The family /specimens is asked for (family=<name>), or None. A blank family= and family=* name no family: they
    are as if the parameter was not sent (review of pull request 348, 2026-11-12: a blank family= turned the
    ants-only default off, and v3.1's own filter then gave only the records whose family is empty)."""
    family = (args.get('family') or '').strip()
    return family if family not in ('', '*') else None


def family_rule(endpoint, args):
    """Which families a specimen route answers for: 'family' when /specimens is asked for one family (family=<name>,
    v3.1's own filter), 'allFamilies' with allFamilies=true, otherwise 'antsOnly' (the default). None for a route
    whose records are not specimens."""
    if endpoint not in FAMILY_ROUTES:
        return None
    if endpoint == 'getSpecimen' and family_asked(args):
        return ONE_FAMILY
    if args.get('allFamilies') == 'true':
        return ALL_FAMILIES
    return ANTS_ONLY


TOTAL_NEEDS_KEY_MESSAGE = ('A total of every specimen record (total=true without a filter) needs an API key: the count'
                           ' reads every record. Add a filter (for example country=Madagascar), or send your key in'
                           ' the X-API-Key header. Free keys: ' + PUBLIC_URL.rsplit('/v4', 1)[0] + '/apiKey.do')


def check_total_needs_key(endpoint, has_key):
    """400 for total=true on a request without a filter and without a key (review of pull request 348, 2026-11-12:
    a count of the whole view, open to anyone at 5 requests a minute from every address). Before it is counted."""
    if not has_key and request.args.get('total') == 'true' and without_filter(endpoint, request.args):
        return error(TOTAL_NEEDS_KEY_MESSAGE, 400, documentation=PUBLIC_URL + '/')
    return None


def without_filter(endpoint, args):
    """True for a /specimens request that names no filter (NO_TOTAL_WITHOUT_FILTER, NOT_FILTERS): its total would be
    a count of every record."""
    if endpoint not in NO_TOTAL_WITHOUT_FILTER:
        return False
    for k, values in args.lists():
        if k not in NOT_FILTERS and any(v.strip() not in ('', '*') for v in values):
            return False
    return True


def run(name, endpoint, admission, has_key, limits):
    """The v3.1 route in an inner request (g.apiV4Inner) with limit = the reserved records; returns (response, records)."""
    filters = {}
    families = family_rule(endpoint, request.args)
    no_filter = without_filter(endpoint, request.args)      # of the request as it was sent, v4's own filters included
    inner = MultiDict()
    for k, values in request.args.lists():
        if k in EXTRA_PARAMS.get(endpoint, ()):
            filters[k] = values[-1]
        elif k == 'family' and endpoint in FAMILY_ROUTES and not family_asked(request.args):
            continue        # a blank family= (or family=*) names no family: not handed to v3.1's filter
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
        g.apiV4Families = families      # read by ants_only() inside the route
        g.apiV4NoFilter = no_filter     # read by rows(): no total unless total=true
        g.apiV4HasKey = has_key         # read by rows(): how long a count may take (COUNT_MS)
        inner_response = api.application.full_dispatch_request()
        found = getattr(g, 'apiV4Paging', None)     # set by rows() for the list routes
        body = inner_response.get_data()
        code = inner_response.status_code
        link = inner_response.headers.get('Link')
        content_type = inner_response.headers.get('Content-Type')
    if code != 200:
        response = Response(body, status=code, content_type=content_type)
        return response, 0
    paging = None
    # specimen records in specimen code order (not the distinct lists of names): the next page is afterCode=
    by_code = SPECIMEN_LISTS.get(name) is not None and 'distinct' not in request.args
    if ndjson:
        records = ndjson_records(body)
        paging = page_info(found, records, admission.reserved)
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
            paging = page_info(found, obj[key], admission.reserved)
            if len(obj[key]) > admission.reserved:
                obj[key] = obj[key][:admission.reserved]
            count = len(obj[key])
            if SPECIMEN_LISTS.get(name) == key:
                add_locality_codes(name, obj[key])
        notice = limit_notice(has_key, limits, admission, count, by_code)
        note = other_family_note(endpoint, families, count)    # once, whatever the metaData key
        for meta in ('metaData', '_metaData'):
            if isinstance(obj.get(meta), dict):
                obj[meta]['parameters'] = [request.args.to_dict()]
                obj[meta]['request'] = 'GET ' + request.script_root + request.full_path.rstrip('?')
                obj[meta].pop('limitNotice', None)
                if notice:
                    obj[meta]['limitNotice'] = notice
                obj[meta]['level'] = level_name(has_key)
                obj[meta]['recordsLeftToday'] = admission.records_left + (admission.reserved - count)
                if paging:
                    obj[meta].update(paging)
                if families:
                    obj[meta]['families'] = families
                if note:
                    obj[meta]['familiesNote'] = note
        response = app.json.response(obj)
    notice = limit_notice(has_key, limits, admission, count, by_code)
    if notice:
        response.headers[api.LIMIT_HEADER] = notice
    if link:
        response.headers['Link'] = link
    response.headers['X-AntWeb-Level'] = level_name(has_key)
    response.headers['X-AntWeb-Records-Left-Today'] = str(admission.records_left + (admission.reserved - count))
    for field, header in PAGING_HEADERS:
        if paging and field in paging:
            value = paging[field]
            if isinstance(value, bool):
                value = 'true' if value else 'false'
            # the code URL-encoded: ready for afterCode=, and safe as a header whatever characters a code has
            response.headers[header] = urllib.parse.quote(str(value), safe='')
    if families:
        response.headers[FAMILIES_HEADER] = families
    if paging and paging.get('totalNotGiven'):
        response.headers[TOTAL_NOT_GIVEN_HEADER] = ('add total=true' if paging['totalNotGiven'] == TOTAL_NOT_GIVEN
                                                    else 'the count took too long')
    return response, count


PAGING_HEADERS = (('total', 'X-AntWeb-Total'), ('returned', 'X-AntWeb-Returned'), ('complete', 'X-AntWeb-Complete'),
                  ('nextAfterCode', 'X-AntWeb-Next-After-Code'))


def page_info(paging, records, reserved):
    """What metaData says about this page: total (when counted), returned, complete and nextAfterCode (when there is
    a next page of specimens). None for a route without rows(). If this answer had to be cut here (it holds more
    than the reserved records; rows() never gives more), it is not complete and the next page starts after its last
    record."""
    if not paging:
        return None
    out = {}
    if 'total' in paging:
        out['total'] = paging['total']
    if paging.get('totalNotGiven'):
        out['totalNotGiven'] = paging['totalNotGiven']
    cut = len(records) > reserved
    out['returned'] = min(len(records), reserved)
    out['complete'] = bool(paging.get('complete')) and not cut
    if not out['complete'] and paging.get('byCode'):
        last = records[reserved - 1] if cut else (records[-1] if records else None)
        code = last.get('specimenCode') if isinstance(last, dict) else None
        if code is None:
            code = paging.get('lastCode')
        if code is not None:
            out['nextAfterCode'] = code
    return out


def level_name(has_key):
    return 'key' if has_key else 'no key'


NEXT_BY_CODE = (' This answer holds {:,}; to continue, send afterCode=<the last specimenCode of this answer>'
                ' (offset= also works).')
NEXT_BY_OFFSET = ' This answer holds {:,}; ask for the next ones with offset=.'


def limit_notice(has_key, limits, admission, count, by_code=False):
    """The notice when the answer was cut: more was asked for than was given, or no limit was given and the answer is
    full (there may be more). How to go on: afterCode=<last specimenCode> for the specimen records of /specimens and
    /geoSpecimens (by_code; offset stays allowed), offset= for every other list."""
    wanted = wanted_limit()
    if (wanted is not None and wanted > admission.reserved) or (wanted is None and count >= admission.reserved):
        who = 'with your key' if has_key else 'without a key'
        text_ = 'Answers {} hold at most {:,} records, and {:,} records a day.'.format(
            who, limits.max_records, limits.records_per_day)
        text_ += (NEXT_BY_CODE if by_code else NEXT_BY_OFFSET).format(count)
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


def other_family_note(endpoint, families, count):
    """metaData.familiesNote (2026-11-13): an answer for one specimenCode= that is empty because the specimen is not
    an ant says so, and how to see it (allFamilies=true); without the note an empty answer reads as "no such
    specimen". None in every other case: records were given, the answer was not ants only, no specimenCode= was
    sent, the code is in no specimen record, or the specimen is an ant (another filter left it out)."""
    codes = request.args.getlist('specimenCode')
    code = codes[0] if len(codes) == 1 else ''      # one code only: the one v3.1 filtered by
    if count or families != ANTS_ONLY or endpoint != 'getSpecimen' or not code or code == '*':
        return None
    # only the code itself: with another filter, that filter may be why the answer is empty (review of #353)
    for k, values in request.args.lists():
        if k not in NOT_FILTERS and k != 'specimenCode' and any(v.strip() not in ('', '*') for v in values):
            return None
    try:
        # the one definition of an ant (ANTS), so the note and the filter cannot disagree
        # the view the answer comes from (it joins the taxon table, so a record without a taxon row is in no answer
        # and gets no note: allFamilies=true would not bring it either; review of #353)
        row = api.session.execute(text('select family, ' + ANTS.format(s='v') + ' from api3_1_specimen v where specimen_code = :c'),
                                  {'c': code}).first()
    finally:
        api.session.remove()
    if row is None or row[1] or not (row[0] or '').strip():
        return None
    return ('Specimen %s is in family %s, not an ant. Answers made from specimen records hold ants only unless you'
            ' add allFamilies=true.' % (code, row[0].strip().capitalize()))


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


def rows(query, limit, offset, key=None, after_code=False):
    """The records of one page of a list route. `query` is the route's own query: its filters, its fixed order,
    its limit and its offset. Outside v4 (v3.1 while v4 is closed) this is query.all(), exactly as before.

    In v4 it also tells run() (through flask.g.apiV4Paging) what metaData says about the page:
      total     the number of records that match the filters. Counted from this same query without its order,
                limit and offset, so the count and the records cannot use different filters. Only on the first
                page (offset 0, no afterCode) or with total=true; never with total=false (a count reads every
                matching row, so later pages leave it out). Not on the first page of a /specimens request
                without a filter (flask.g.apiV4NoFilter, without_filter()): that count is of every record, so
                it is made only with total=true, and metaData.totalNotGiven says how to ask (unless that page
                already holds every record: then their number is the total).
      complete  whether this page reaches the end: one record more than `limit` is read and not given, so this
                costs no count and is right on every page.
      lastCode  the specimen code of the last record (after_code routes), for metaData.nextAfterCode.
    afterCode (after_code routes): only records with a specimen code greater than the given one, a bound parameter
    compared with the same column the query is ordered by. `key` is a column that is never NULL (the specimen code,
    the taxon name), counted directly; without it the query is counted as a subquery (the distinct lists)."""
    if not getattr(g, 'apiV4Inner', False):
        return query.all()
    f = getattr(g, 'apiV4Filters', None) or {}
    after = f.get('afterCode') if after_code else None
    unlimited = query.limit(None).offset(None)
    paging = {'byCode': bool(after_code)}
    wanted = f.get('total')
    first_page = offset == 0 and not after
    no_filter = bool(getattr(g, 'apiV4NoFilter', False))
    if wanted == 'true' or (wanted != 'false' and first_page and not no_filter):
        counted = unlimited.order_by(None)
        has_key = bool(getattr(g, 'apiV4HasKey', False))
        # total=true asked for with a key: counted to the end (no limit of its own); otherwise COUNT_MS
        ms = None if (has_key and wanted == 'true') else COUNT_MS[has_key]
        total = count_within(counted, key, ms)
        if total is None:
            paging['totalNotGiven'] = TOTAL_TOO_LONG.format(
                next=TOTAL_TOO_LONG_NEXT[bool(after_code)], s=COUNT_MS[has_key] // 1000,
                key=TOTAL_TOO_LONG_HAS_KEY if has_key else TOTAL_TOO_LONG_KEY.format(url=PUBLIC_URL.rsplit('/v4', 1)[0]))
        else:
            paging['total'] = total
    elif wanted != 'false' and first_page and no_filter:
        paging['totalNotGiven'] = TOTAL_NOT_GIVEN
    if after:
        unlimited = unlimited.filter(key > after)
    found = unlimited.limit(limit + 1).offset(offset).all()
    paging['complete'] = len(found) <= limit
    found = found[:limit]
    if paging.get('totalNotGiven') and paging['complete'] and first_page:
        # the first page holds every record: their number is the total, and it cost no count (the first page only:
        # a count that took too long can be on a later page, whose records are not all of them; review of #353)
        del paging['totalNotGiven']
        paging['total'] = len(found)
    if after_code and found:
        paging['lastCode'] = getattr(found[-1], key.key)
    g.apiV4Paging = paging
    return found


MYSQL_TIME_EXCEEDED = 3024     # ER_QUERY_TIMEOUT: maximum statement execution time exceeded
# how MySQL may report a statement stopped by MAX_EXECUTION_TIME, depending on the stage it was in: 3024, 1317
# (ER_QUERY_INTERRUPTED), 1028 (ER_FILSORT_ABORT, "Sort aborted: ... maximum statement execution time exceeded")
MYSQL_TIME_LIMIT_CODES = (MYSQL_TIME_EXCEEDED, 1317, 1028)


def count_statement(counted, key, ms):
    """The SELECT that counts `counted`: count(key), or count(*) of it as a subquery when there is no key (the distinct
    lists). With `ms`, MySQL's optimizer hint MAX_EXECUTION_TIME(ms) on that one statement (top level, where MySQL
    honours it). A hint, not a session setting (review of #353): nothing stays behind on the pooled connection for the
    next request, whatever happens to this one."""
    if key is not None:
        stmt = counted.with_entities(func.count(key)).statement
    else:
        stmt = select(func.count()).select_from(counted.statement.subquery())
    if ms:
        stmt = stmt.prefix_with('/*+ MAX_EXECUTION_TIME(%d) */' % int(ms), dialect='mysql')
    return stmt


def count_within(counted, key, ms):
    """The count of `counted` (rows()), or None when MySQL stopped it after `ms` milliseconds (error 3024). ms None:
    no limit of its own (the database's for every API read): then nothing is caught, as before this limit (a count
    that fails answers HTTP 500, it is not reported as "took too long"). Any other error is passed on. SQLite (the
    unit tests) ignores the MySQL-only hint."""
    session = counted.session
    try:
        return session.execute(count_statement(counted, key, ms)).scalar()
    except Exception as e:      # noqa: BLE001 -- only the time limit of this count is turned into "no total"
        args = getattr(getattr(e, 'orig', None), 'args', None) or [None]
        # 3024 is only ever the time limit; 1317 and 1028 also mean KILL QUERY or a shutdown, so only with the
        # limit's own words (review of #353)
        limit_hit = args[0] == MYSQL_TIME_EXCEEDED or (
            args[0] in MYSQL_TIME_LIMIT_CODES and 'maximum statement execution time' in str(args[1:]).lower())
        if ms is None or not limit_hit:
            raise
        session.rollback()
        return None


def ants_only(query, specimen):
    """v4 only: the ants-only default, on the query of every route that reads specimens and takes a place filter
    (FAMILY_ROUTES). The one place the rule is applied, with the one definition (ANTS: the data extract's test, family
    Formicidae or no family given; MySQL compares the name without case, as in the extract).

    query     the route's SQLAlchemy query and specimen its mapped class (api.Specimen: the view api3_1_specimen
              passes specimen.family through), or
              the WHERE text of a route that writes its SQL as text (/geoSpeciesStats) and specimen the alias of
              the specimen table in it ('s'): " and <the test>" is added.
    Returns the query unchanged outside v4 (v3.1 answers for every family), with allFamilies=true, and when
    /specimens is asked for one family. The records, the totals and the distinct lists of a route are made from the
    one query this is applied to, so they cannot differ. With an outer join (/measurements) a measurement without a
    specimen record stays: it has no family."""
    if not getattr(g, 'apiV4Inner', False) or getattr(g, 'apiV4Families', None) != ANTS_ONLY:
        return query
    if isinstance(query, str):
        return query + ' and ' + ANTS.format(s=specimen)
    return query.filter(text(ANTS.format(s=specimen.__tablename__)))


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
