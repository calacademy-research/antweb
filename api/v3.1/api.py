# ! /usr/bin/python3
# -*- coding: utf-8 -*-

# Can be execute in local environment (in /Users/mark/dev/calacademy/antweb/api/v3.1 directory) as such:
#     python3 api.py
#
# Accessible, for instance, here:
#   http://localhost:5000/specimens?specimenCode=casent0922626&up=1

# To Do.
#   Performance tuning. Prevent long runs.
#   Remove the classes into separate files.

# To configure development environment for this API:
# pip3 install Flask-SQLAlchemy
# pip3 install Flask-RESTful
# pip3 install PyMySQL

# import os
# assert os.path.exists('/var/www/html/apiV3/api_db.conf')

import configparser
from flask import Flask, json, request, Response, g
from flask import jsonify as flaskJsonify

from sqlalchemy import create_engine, or_, text, func
from sqlalchemy import Column, Integer, String, Boolean, DateTime, ForeignKey, Numeric
from sqlalchemy.orm import sessionmaker, relationship, scoped_session

from sqlalchemy.exc import OperationalError

from sqlalchemy.ext.declarative import declarative_base

# from home.taxon import Taxon
# from home.geolocale import Geolocale
# from home.geolocaleTaxon import GeolocaleTaxon

# from home.taxaImage import TaxaImage
# from home.image import Image
# from home.specimen import Specimen


API_PROPERTIES = '/antweb-etc/api-db.properties'


def _apiProperties(path=API_PROPERTIES):
    # etc/api-db.properties on the server (not in the repository; only this file of etc/ is
    # mounted in the api container, as /antweb-etc/api-db.properties). Empty if missing.
    try:
        with open(path) as f:
            return dict(line.strip().split('=', 1) for line in f if '=' in line and not line.startswith('#'))
    except OSError:
        return {}


def _db_credentials(props):
    # The API only reads. On the servers it logs in as a read-only MySQL user whose
    # name and password are in etc/api-db.properties. Without that file it falls back to
    # the main antweb account, as before.
    user, password = 'antweb', 'CHANGE_ME'
    if props.get('user') and props.get('password'):
        user, password = props['user'], props['password']
    print('API database user:', user, flush=True)
    return user, password


_apiProps = _apiProperties()
_dbUser, _dbPass = _db_credentials(_apiProps)
dbUrl = f'mysql+pymysql://{_dbUser}:{_dbPass}@mysql:3306/ant?autocommit=true'
# dbUrl = 'mysql+mysqldb://antweb:CHANGE_ME@localhost:3306/ant?autocommit=true'
# pool_pre_ping: check a pooled connection before use, so a connection MySQL closed
# while idle is replaced instead of failing the request ("MySQL server has gone away").
engine = create_engine(dbUrl, pool_recycle=280, pool_pre_ping=True)

'''
connection = engine.connect()
result = connection.execute("select occurrenceId from darwin_core_2 limit 20")
for row in result:
    print("occurrenceId:", row['occurrenceId'])
connection.close()
'''

Base = declarative_base()

Session = sessionmaker(bind=engine)
# One session per request (scoped_session), released in remove_session() below.
# A single module-level session kept a dead connection after MySQL restarts and
# was shared between gunicorn threads.
session = scoped_session(Session)

# import MySQLdb as mysqldb

import math
import time
import urllib.parse

from datetime import datetime, timedelta

application = Flask(__name__)


@application.teardown_appcontext
def remove_session(exception=None):
    session.remove()



# Flask 3 writes Decimal values (e.g. decimalLatitude) as quoted strings.
# Keep them as plain JSON numbers, as the API has always returned them.
from decimal import Decimal
from flask.json.provider import DefaultJSONProvider


class AntwebJSONProvider(DefaultJSONProvider):
    @staticmethod
    def default(o):
        if isinstance(o, Decimal):
            return float(o)
        return DefaultJSONProvider.default(o)


application.json = AntwebJSONProvider(application)
# app.run(port=80)

isDevMode = 0

# MySQL configurations  
try:
    # apiDbConf = '/var/www/html/apiV3/api_db.conf'
    # apiDbConf = '/antweb/deploy/api/v3/api_db.conf'
    apiDbConf = 'api_db.conf'

    import os

    assert os.path.exists(apiDbConf)

    # Read config file
    config = configparser.ConfigParser()
    # config.read('api_db.conf')
    # config.read('/var/www/html/apiV3/api_db.conf')
    config.read(apiDbConf)

    # print(dbUrl)
except Exception as e:
    pass
    # print('Exception e: ' + str(e),' reading configuration file')

# app = Flask(__name__)

# application.config['JSON_AS_ASCII'] = False


version = "V3.1"
localPhpServer = "http://localhost:5000"
localTomcatServer = "http://localhost/antweb"
apiServer = "http://api.antweb.org"
prodServer = "https://www.antweb.org"
# The API's own public address, for links in its replies (2026-10-26, finding F6: /images linked localhost:5000).
apiPublicBase = prodServer + "/v3.1"


def specimenApiUrl(code):
    """The public /specimens address of one specimen (the accepted parameter is specimenCode)."""
    return apiPublicBase + "/specimens?specimenCode=" + urllib.parse.quote(str(code), safe="")
apiUri = "api" + version + ".do"

globalMessage = "Either the server is overloaded or there is an error in the application."

log = True

import sys

if sys.platform == "darwin":
    isDevMode = 1
    print("isDevMode:" + str(isDevMode))

print("server started. devMode:" + str(isDevMode) + " platform:" + sys.platform)


@application.route("/")
def hello():
    message = "Antweb API " + version \
              + "<br><br><a href='" + localTomcatServer + "/" + apiUri + "'>" + version + " Local API Documentation</a>" \
              + "<br><br><a href='" + prodServer + "/" + apiUri + "'>" + version + " Antweb API Documentation</a>"
    # + "<br><br>Application:" + application.name
    return message


# - Various Methods, etc.. ---------------------------------------------------------------

class HaltException(Exception):
    pass


def apiError(method, error=None, status=500):
    # Full details (exception text, SQL) go to the server log only; the caller gets a generic JSON error.
    print("API error in " + method + " on request:" + str(request) + " error:" + repr(error), flush=True)
    return jsonify(error="The request could not be completed. Please try again later or simplify the query."), status


@application.errorhandler(Exception)
def handleUncaught(error):
    from werkzeug.exceptions import HTTPException
    if isinstance(error, HTTPException):
        return jsonify(error=error.name), error.code
    import traceback
    traceback.print_exc()
    return apiError("uncaught", error)


def initCap(x):
    val = x
    try:
        val = x[:1].upper() + x[1:]
    except TypeError as error:
        pass  # print("error:" + str(error))
    return val


def down(request):
    # This code brings the server down. Only accessible if &up=1 is appended to each api call.
    up = request.args.get('up', default='*', type=str)
    if up != '1':
        print("Down")
        # raise HaltException("We decided we want to halt the script")
        # userExit()
        # exit()
        return 1
    return 0


# Records per request, for every endpoint, JSON and ndjson alike (2026-10-21: 500, until
# registered API keys arrive; it was 10,000). Larger sets are fetched in pages with offset.
# Set on the server without a code change: maxLimit=<n> in etc/api-db.properties, then
# restart the api container (docker restart antweb_api_1). Default 500.
CAP_DEFAULT = 500


def readMaxLimit(props):
    # The login lines are read exactly as before; only this key tolerates spaces (maxLimit = 750).
    raw = next((v for k, v in props.items() if k.strip() == 'maxLimit'), CAP_DEFAULT)
    try:
        value = int(str(raw).strip())
    except ValueError:
        value = CAP_DEFAULT
    return value if value > 0 else CAP_DEFAULT


MAX_LIMIT = readMaxLimit(_apiProps)
MAX_LIMIT_NDJSON = MAX_LIMIT
# Without a limit parameter a request gets the maximum (as before, when both were 10,000).
DEFAULT_LIMIT = MAX_LIMIT
print('API record limit per request:', MAX_LIMIT, flush=True)

LIMIT_HEADER = 'X-AntWeb-Limit-Notice'


def limitNoticeText():
    return (f"Requests are limited to {MAX_LIMIT:,} records while AntWeb moves to registered API keys. "
            "For larger data needs contact antweb@antweb.org.")


def setGlobals(request):
    # Returns this request's settings: limit, offset, log, ndjson, startTime.
    # They are local to each request. Module globals were shared by the gunicorn threads,
    # so one request could run with another request's limit.
    print("request:" + str(request))

    startTime = time.time()
    log = request.args.get('log', default='*', type=str)
    ndjson = request.args.get('ndjson', default='*', type=str)
    maxLimit = MAX_LIMIT_NDJSON if ndjson == 'true' else MAX_LIMIT
    limit = request.args.get('limit', default=None, type=int)
    requested = limit if limit is not None and limit > 0 else None
    if requested is None:
        limit = DEFAULT_LIMIT
    limit = min(limit, maxLimit)
    offset = request.args.get('offset', default=0, type=int)
    if offset is None or offset < 0:
        offset = 0
    # For limitNotice(): what was asked for and what is used (per request, flask.g).
    g.apiLimitRequested = requested
    g.apiLimit = limit
    return limit, offset, log, ndjson, startTime


def limitNotice(count):
    # The notice when the reply was cut by the cap: more than the cap was asked for, or no limit
    # was given and the reply is full (there may be more). None otherwise.
    requested = getattr(g, 'apiLimitRequested', None)
    limit = getattr(g, 'apiLimit', None)
    if limit is None:
        return None
    if requested is not None and requested > limit:
        return limitNoticeText()
    if requested is None and count >= limit:
        return limitNoticeText()
    return None


def jsonify(*args, **kwargs):
    # flask.jsonify, plus the limit notice: in the reply's metaData (or _metaData) as
    # "limitNotice" and in the X-AntWeb-Limit-Notice header. The record count is the length of
    # the reply's list (specimens, taxa, images, ...).
    notice = None
    if not args:
        lists = [v for k, v in kwargs.items() if isinstance(v, list)]
        if lists:
            notice = limitNotice(len(lists[0]))
        if notice:
            for key in ('metaData', '_metaData'):
                if isinstance(kwargs.get(key), dict):
                    kwargs[key]['limitNotice'] = notice
    response = flaskJsonify(*args, **kwargs)
    if notice:
        response.headers[LIMIT_HEADER] = notice
    return response


def ndjsonLines(dataList):
    # One index header line and one record line per datum, encoded one record at a time.
    # application.json.dumps is the same encoder the request used before (Decimal as number,
    # sorted keys), and it works outside the request context while the reply streams.
    lineEnd = '\n'.encode('utf8')
    for i, datum in enumerate(dataList):
        header = {"index": {"_index": "specimens", "_id": i}}
        yield (application.json.dumps(header, sort_keys=False).encode('utf8') + lineEnd
               + application.json.dumps(datum, indent=None, ensure_ascii=False).encode('utf8') + lineEnd)


def getNdjson(dataList):
    # Streamed: the reply is written as it is encoded, never held in memory as a whole.
    # The limit notice (no metadata in ndjson) is in the X-AntWeb-Limit-Notice header.
    response = Response(ndjsonLines(dataList), content_type="application/json; charset=utf-8")
    notice = limitNotice(len(dataList))
    if notice:
        response.headers[LIMIT_HEADER] = notice
    return response


def returnJsonify(method, returnVal):
    if returnVal is None:
        message = method + ":" + globalMessage
        print(message)
        return message
    else:
        return returnVal


# --- Specimen API -----------------------------------------------------------------------------------


class Specimen(Base):
    __tablename__ = 'api3_1_specimen'

    occurrenceId = Column(String, primary_key=True)
    ownerInstitutionCode = Column(String)
    institutionCode = Column(String)
    collectionCode = Column(String)
    code = Column("specimen_code", String)
    lastModified = Column("last_modified", DateTime)
    kingdom = Column('kingdom_name', String)
    phylum = Column('phylum_name', String)
    classVal = Column('class_name', String)
    order = Column('order_name', String)
    family = Column(String)
    subfamily = Column(String)
    genus = Column(String)
    subgenus = Column(String)
    species = Column(String)
    subspecies = Column(String)
    scientific_name = Column(String)
    higherClassification = Column(String)
    typeStatus = Column(String)
    region = Column(String)
    subregion = Column(String)
    country = Column(String)
    adm1 = Column(String)
    bioregion = Column(String)
    decimalLatitude = Column(Numeric)  # Could be Float or Numeric
    decimalLongitude = Column(Numeric)  # was: double
    latLonMaxError = Column(String)
    dateDetermined = Column(DateTime)  # date
    dateCollectedStart = Column(DateTime)
    dateCollectedEnd = Column(DateTime)
    habitat = Column(String)  # text
    microhabitat = Column(String)
    samplingMethod = Column(String)
    lifeStageSex = Column(String)
    medium = Column(String)
    collectionCode = Column(String)
    determinedBy = Column(String)
    localityName = Column(String)
    localityNotes = Column(String)
    specimenNotes = Column(String)
    collectionNotes = Column(String)
    # dateCollected = Column(String)
    minimumElevationInMeters = Column(Integer)
    bioregion = Column(String)
    museum = Column(String)
    taxonName = Column("taxon_name", String, ForeignKey('taxon.taxon_name'), )
    ownedBy = Column(String)
    locatedAt = Column(String)
    collectedBy = Column(String)
    caste = Column('caste', String)
    subcaste = Column('subcaste', String)
    fossil = Column('fossil', Boolean)
    status = Column('taxon_status', String)
    imageCount = Column('image_count', Integer)

    # isMale = Column('is_male', Boolean)
    # isWorker = Column('is_worker', Boolean)
    # isQueen = Column('isQueen', Boolean)

    taxon = relationship("Taxon")

    def __repr__(self):
        return "<Specimen(occurrenceId='%s', ownerInstitutionCode='%s' \
           , institutionCode='%s', collectionCode='%s' \
           , code='%s', dctermsModified='%s', nomenclaturalCode='%s' \
           , kingdom='%s', phylum='%s', classVal='%s', order='%s' \
           , family='%s', subfamily='%s', genus='%s', subgenus='%s' \
           , species='%s', subspecies='%s', scientific_name='%s' \
           , higherClassification='%s', typeStatus='%s' \
           , region='%', subregion='%s', country='%s', adm1='%s' \
           , bioregion='%s', decimalLatitude='%s', decimalLongitude='%s' \
           , latLonMaxError='%s', dateDetermined='%s', dateCollectedStart='%s', dateCollectedEnd='%s' \
           , habitat='%s', microhabitat='%s' \
           , collectedBy='%s', samplingMethod='%s', lifeStageSex='%s' \
           , medium='%s', collectionCode='%s', determinedBy='%s' \
           , localityName='%s', localityNotes='%s', specimenNotes='%s' \
           , collectionNotes='%s' \
           , minimumElevationInMeters='%s', bioregion='%s', museum='%s', taxonName='%s' \
           , ownedBy='%s', locatedAt='%s', collectedBy='%s' \
           , caste='%s', subcaste='%s', fossil='%s', status='%s' \
      #     , isMale='%s', isWorker='%s', isQueen='%s', imageCount='%s' \
       )>" % (
            self.occurrenceId, self.ownerInstitutionCode
            , self.basisOfRecord, self.institutionCode, self.collectionCode
            , self.code, self.dctermsModified, self.nomenclaturalCode
            , self.kingdom, self.phylum, self.classVal, self.order
            , self.family, self.subfamily, self.genus, self.subgenus
            , self.species, self.subspecies, self.scientific_name
            , self.higherClassification, self.typeStatus
            , self.region, self.subregion, self.country, self.adm1
            , self.bioregion, self.decimalLatitude, self.decimalLongitude
            , self.latLonMaxError, self.dateDetermined, self.dateCollectedStart, self.dateCollectedEnd
            , self.habitat, self.microhabitat
            , self.collectedBy, self.samplingMethod, self.lifeStageSex
            , self.medium, self.collectionCode, self.determinedBy
            , self.localityName, self.localityNotes, self.specimenNotes
            , self.collectionNotes
            , self.minimumElevationInMeters, self.bioregion, self.museum, self.taxonName
            , self.ownedBy, self.locatedAt, self.collectedBy
            , self.caste, self.subcaste, self.fossil, self.status, self.imageCount
            #    , self.isMale, self.isWorker, self.isQueen
        )

    def getSpecDict(sp):
        coordinates = {
            'lat': sp.decimalLatitude
            , 'lon': sp.decimalLongitude
        }
        if not sp.decimalLatitude and not sp.decimalLongitude:
            coordinates = {
                'lat': ""
                , 'lon': ""
            }
        geo = {
            'coordinates': coordinates
        }

        specDict = {
            'specimenCode': sp.code
            , 'occurrenceId': sp.occurrenceId
            , 'ownerInstitutionCode': sp.ownerInstitutionCode
            , 'institutionCode': sp.institutionCode
            , 'collectionCode': sp.collectionCode
            , 'kingdom': initCap(sp.kingdom)
            , 'phylum': initCap(sp.phylum)
            , 'class': initCap(sp.classVal)
            , 'order': initCap(sp.order)
            , 'family': initCap(sp.family)
            , 'subfamily': initCap(sp.subfamily)
            , 'genus': initCap(sp.genus)
            , 'subgenus': sp.subgenus
            , 'species': sp.species
            , 'subspecies': sp.subspecies
            , 'scientificName': sp.scientific_name
            , 'higherClassification': sp.higherClassification
            , 'type': sp.typeStatus
            , 'region': sp.region
            , 'subregion': sp.subregion
            , 'country': sp.country
            , 'adm1': sp.adm1
            , 'bioregion': sp.bioregion
            , 'decimalLatitude': sp.decimalLatitude
            , 'decimalLongitude': sp.decimalLongitude
            , 'geo': geo
            , 'latLonMaxError': sp.latLonMaxError
            , 'dateDetermined': sp.dateDetermined
            , 'dateCollectedStart': sp.dateCollectedStart
            , 'dateCollectedEnd': sp.dateCollectedEnd
            , 'habitat': sp.habitat
            , 'microhabitat': sp.microhabitat
            , 'collectedBy': sp.collectedBy
            , 'samplingMethod': sp.samplingMethod
            , 'lifeStageSex': sp.lifeStageSex
            , 'medium': sp.medium
            , 'collectionCode': sp.collectionCode
            , 'determinedBy': sp.determinedBy
            , 'localityName': sp.localityName
            , 'localityNotes': sp.localityNotes
            , 'specimenNotes': sp.specimenNotes
            , 'collectionNotes': sp.collectionNotes
            , 'minimumElevationInMeters': sp.minimumElevationInMeters
            , 'bioregion': sp.bioregion
            , 'antwebTaxonName': sp.taxonName
            , 'ownedBy': sp.ownedBy
            , 'locatedAt': sp.locatedAt
            , 'collectedBy': sp.collectedBy
            , 'caste': sp.caste
            , 'subcaste': sp.subcaste
            , 'fossil': sp.fossil
            , 'status': sp.status
            , 'imageCount': sp.imageCount
        }

        return specDict


def addExtraGeolocaleListCriteria(query, geolocaleName):
    # Because we don't want all specimen from all species on the species list.
    # Just all the specimen from all of the species on the species list that are in the Geolocale.

    geolocale = getGeolocale(geolocaleName)
    if geolocale is None:
        return query
    georank = geolocale.georank
    if georank == "region":
        query = query.filter(Specimen.region.contains(geolocaleName))
    if georank == "subregion":
        query = query.filter(Specimen.subregion.contains(geolocaleName))
    if georank == "country":
        query = query.filter(Specimen.country.contains(geolocaleName))
    if georank == "adm1":
        query = query.filter(Specimen.adm1.contains(geolocaleName))
    return query


# validSubfamily=true and validGenus=true on /specimens: the extant valid subfamilies and genera, compared in lower
# case. 2026-10-26, finding F7: the genus list was capitalised (five names lower case) while the specimen table
# stores genus in lower case, so validGenus=true dropped every record; both filters ran in Python after limit and
# offset, so pages came back short. Now both are SQL filters on lower(name), applied before limit and offset.
# 'heteroponerinae' added (the list had only 'hetero', which matches nothing).
VALID_SUBFAMILIES = frozenset(n.lower() for n in (
    'agroecomyrmecinae', 'amblyoponinae', 'aneuretinae', 'apomyrminae', 'dolichoderinae', 'dorylinae',
    'ectatomminae', 'formicinae', 'hetero', 'leptanillinae', 'martialinae', 'myrmeciinae', 'myrmicinae',
    'paraponerinae', 'ponerinae', 'proceratiinae', 'pseudomyrmecinae', 'heteroponerinae'))

VALID_GENERA = frozenset(n.lower() for n in (
    'Ankylomyrma', 'Tatuidris', 'Adetomyrma', 'Amblyopone', 'Fulakora', 'Myopopone', 'Mystrium', 'Onychomyrmex',
    'Prionopelta', 'Stigmatomma', 'Xymmer', 'Aneuretus', 'Apomyrma', 'Anillidris', 'Anonychomyrma', 'Aptinoma',
    'Arnoldius', 'Axinidris', 'Azteca', 'Bothriomyrmex', 'Chronoxenus', 'Doleromyrma', 'Dolichoderus',
    'Dorymyrmex', 'Ecphorella', 'Forelius', 'Froggattella', 'Gracilidris', 'iridomyrmex', 'Leptomyrmex',
    'Linepithema', 'Liometopum', 'Loweriella', 'Nebothriomyrmex', 'Ochetellus', 'Papyrius', 'Philidris', 'Ravavy',
    'Tapinoma', 'Technomyrmex', 'Turneria', 'Acanthostichus', 'Aenictogiton', 'Aenictus', 'Cerapachys',
    'Cheliomyrmex', 'Chrysapace', 'Cylindromyrmex', 'Dorylus', 'Eburopone', 'Eciton', 'Eusphinctus', 'Labidus',
    'Leptanilloides', 'Lioponera', 'Lividopone', 'Neivamyrmex', 'Neocerapachys', 'Nomamyrmex', 'Ooceraea',
    'Parasyscia', 'Simopone', 'Sphinctomyrmex', 'Syscia', 'Tanipone', 'Vicinopone', 'Yunodorylus', 'Zasphinctus',
    'Ectatomma', 'Gnamptogenys', 'Rhytidoponera', 'Typhlomyrmex', 'Acropyga', 'Agraulomyrmex', 'Alloformica',
    'Anoplolepis', 'Aphomomyrmex', 'Bajcaridris', 'Brachymyrmex', 'Bregmatomyrma', 'Calomyrmex', 'Camponotus',
    'Cataglyphis', 'Cladomyrma', 'Colobopsis', 'Dinomyrmex', 'Echinopla', 'Euprenolepis', 'Formica', 'Gesomyrmex',
    'Gigantiops', 'iberoformica', 'Lasiophanes', 'Lasius', 'Lepisiota', 'Melophorus', 'Myrmecocystus',
    'Myrmecorhynchus', 'Myrmelachista', 'Myrmoteras', 'Notoncus', 'Notostigma', 'Nylanderia', 'Oecophylla',
    'Opisthopsis', 'Overbeckia', 'Paraparatrechina', 'Paratrechina', 'Petalomyrmex', 'Plagiolepis', 'Polyergus',
    'Polyrhachis', 'Prenolepis', 'Proformica', 'Prolasius', 'Pseudolasius', 'Pseudonotoncus', 'Rossomyrmex',
    'Santschiella', 'Stigmacros', 'Tapinolepis', 'Teratomyrmex', 'Zatania', 'Acanthoponera', 'Aulacopone',
    'Heteroponera', 'Anomalomyrma', 'Leptanilla', 'Noonilla', 'Opamyrma', 'Phaulomyrma', 'Protanilla', 'Scyphodon',
    'Yavnella', 'Martialis', 'Myrmecia', 'Nothomyrmecia', 'Acanthognathus', 'Acanthomyrmex', 'Acromyrmex',
    'Adelomyrmex', 'Adlerzia', 'Allomerus', 'Ancyridris', 'Anillomyrma', 'Aphaenogaster', 'Apterostigma',
    'Aretidris', 'Atopomyrmex', 'Atta', 'Austromorium', 'Baracidris', 'Bariamyrma', 'Basiceros', 'Blepharidatta',
    'Bondroitia', 'Calyptomyrmex', 'Cardiocondyla', 'Carebara', 'Cataulacus', 'Cephalotes', 'Chimaeridris',
    'Colobostruma', 'Crematogaster', 'Cryptomyrmex', 'Cyatta', 'Cyphoidris', 'Cyphomyrmex', 'Dacatria',
    'Dacetinops', 'Daceton', 'Diaphoromyrma', 'Dicroaspis', 'Dilobocondyla', 'Diplomorium', 'Dolopomyrmex',
    'Epelysidris', 'Epopostruma', 'Erromyrma', 'Eurhopalothrix', 'Eutetramorium', 'Formicoxenus', 'Formosimyrma',
    'Gaoligongidris', 'Gauromyrmex', 'Goniomma', 'Harpagoxenus', 'Huberia', 'Hylomyrma', 'indomyrma', 'ishakidris',
    'Kalathomyrmex', 'Kartidris', 'Kempfidris', 'Lachnomyrmex', 'Lasiomyrma', 'Lenomyrmex', 'Leptothorax',
    'Liomyrmex', 'Lophomyrmex', 'Lordomyrma', 'Malagidris', 'Manica', 'Mayriella', 'Megalomyrmex', 'Melissotarsus',
    'Meranoplus', 'Mesostruma', 'Messor', 'Metapone', 'Microdaceton', 'Monomorium', 'Mycetagroicus', 'Mycetarotes',
    'Mycetophylax', 'Mycetosoritis', 'Mycocepurus', 'Myrmecina', 'Myrmica', 'Myrmicaria', 'Myrmicocrypta',
    'Nesomyrmex', 'Novomessor', 'Ochetomyrmex', 'Octostruma', 'Ocymyrmex', 'Orectognathus', 'Oxyepoecus',
    'Oxyopomyrmex', 'Paramycetophylax', 'Paratopula', 'Patagonomyrmex', 'Perissomyrmex', 'Peronomyrmex',
    'Phalacromyrmex', 'Pheidole', 'Pilotrochus', 'Podomyrma', 'Poecilomyrma', 'Pogonomyrmex', 'Pristomyrmex',
    'Proatta', 'Procryptocerus', 'Propodilobus', 'Protalaridris', 'Pseudoatta', 'Recurvidris', 'Rhopalomastix',
    'Rhopalothrix', 'Rogeria', 'Romblonella', 'Rostromyrmex', 'Rotastruma', 'Royidris', 'Secostruma',
    'Sericomyrmex', 'Solenopsis', 'Stegomyrmex', 'Stenamma', 'Stereomyrmex', 'Strongylognathus', 'Strumigenys',
    'Syllophopsis', 'Talaridris', 'Temnothorax', 'Terataner', 'Tetheamyrma', 'Tetramorium', 'Trachymyrmex',
    'Tranopelta', 'Trichomyrmex', 'Tropidomyrmex', 'Tyrannomyrmex', 'Veromessor', 'Vitsika', 'Vollenhovia',
    'Vombisidris', 'Wasmannia', 'Xenomyrmex', 'Paraponera', 'Anochetus', 'Asphinctopone', 'Austroponera',
    'Belonopelta', 'Boloponera', 'Bothroponera', 'Brachyponera', 'Buniapone', 'Centromyrmex', 'Cryptopone',
    'Diacamma', 'Dinoponera', 'Dolioponera', 'Ectomomyrmex', 'Emeryopone', 'Euponera', 'Feroponera', 'Fisheropone',
    'Hagensia', 'Harpegnathos', 'Hypoponera', 'iroponera', 'Leptogenys', 'Loboponera', 'Mayaponera', 'Megaponera',
    'Mesoponera', 'Myopias', 'Neoponera', 'Odontomachus', 'Odontoponera', 'Ophthalmopone', 'Pachycondyla',
    'Paltothyreus', 'Parvaponera', 'Phrynoponera', 'Platythyrea', 'Plectroctena', 'Ponera', 'Promyopias',
    'Psalidomyrmex', 'Pseudoneoponera', 'Pseudoponera', 'Rasopone', 'Simopelta', 'Streblognathus',
    'Thaumatomyrmex', 'Discothyrea', 'Probolomyrmex', 'Proceratium', 'Myrcidris', 'Pseudomyrmex', 'Tetraponera'))


@application.route('/specimens', methods=['GET'])
def getSpecimen():
    # Process parameters
    family = request.args.get('family', default='*', type=str)
    subfamily = request.args.get('subfamily', default='*', type=str)
    genus = request.args.get('genus', default='*', type=str)
    species = request.args.get('species', default='*', type=str)
    code = request.args.get('specimenCode', default='*', type=str)
    region = request.args.get('region', default='*', type=str)
    subregion = request.args.get('subregion', default='*', type=str)
    country = request.args.get('country', default='*', type=str)
    adm1 = request.args.get('adm1', default='*', type=str)
    bioregion = request.args.get('bioregion', default='*', type=str)
    habitat = request.args.get('habitat', default='*', type=str)
    type = request.args.get('type', default='*', type=str)
    georeferenced = request.args.get('georeferenced', default='*', type=str)
    bbox = request.args.get('bbox', default='*', type=str)
    minDate = request.args.get('minDate', default='*', type=str)
    maxDate = request.args.get('maxDate', default='*', type=str)
    minElevation = request.args.get('minElevation', default='*', type=str)
    maxElevation = request.args.get('maxElevation', default='*', type=str)
    museum = request.args.get('museum', default='*', type=str)
    ownedby = request.args.get('ownedby', default='*', type=str)
    locatedat = request.args.get('locatedat', default='*', type=str)
    collectedby = request.args.get('collectedby', default='*', type=str)
    caste = request.args.get('caste', default='*', type=str)
    subcaste = request.args.get('subcaste', default='*', type=str)
    fossil = request.args.get('fossil', default='*', type=str)
    validGenus = request.args.get('validGenus', default='*', type=str)
    validSubfamily = request.args.get('validSubfamily', default='*', type=str)
    status = request.args.get('status', default='*', type=str)
    hasImage = request.args.get('hasImage', default='*', type=str)
    geolocaleName = request.args.get('geolocaleName', default='*', type=str)
    lifeStageSex = request.args.get('lifeStageSex', default='*', type=str)
    limit, offset, log, ndjson, startTime = setGlobals(request)
    if down(request): return ""

    # Build query criteria
    query = session.query(Specimen)
    if family != '*':
        query = query.filter(Specimen.family == family)
    if subfamily != '*':
        query = query.filter(Specimen.subfamily == subfamily)
    if genus != '*':
        query = query.filter(Specimen.genus == genus)
    if species != '*':
        query = query.filter(Specimen.species == species)
    if code != '*':
        # print("code:" + code)
        query = query.filter(Specimen.code == code)
    if region != '*':
        query = query.filter(Specimen.region == region)
    if subregion != '*':
        query = query.filter(Specimen.subregion ==subregion)
    if country != '*':
        query = query.filter(Specimen.country.contains(country))
    if adm1 != '*':
        query = query.filter(Specimen.adm1 == adm1)
    if bioregion != '*':
        query = query.filter(Specimen.bioregion == bioregion)
    if habitat != '*':
        query = query.filter(Specimen.habitat.contains(habitat))
    if type != '*':
        query = query.filter(Specimen.typeStatus == type)
    if georeferenced == 'true':
        query = query.filter(Specimen.decimalLatitude != None)
        query = query.filter(Specimen.decimalLongitude != None)
    if bbox != '*':
        coords = bbox.split(",")
        query = query.filter(Specimen.decimalLatitude <= coords[0], Specimen.decimalLatitude >= coords[2])
        query = query.filter(Specimen.decimalLongitude <= coords[1], Specimen.decimalLongitude >= coords[3])
    if maxDate != '*':
        query = query.filter(Specimen.dateCollectedStart < maxDate)
    if minDate != '*':
        query = query.filter(Specimen.dateCollectedStart > minDate)
    if minElevation != '*':
        query = query.filter(Specimen.minimumElevationInMeters > minElevation)
    if maxElevation != '*':
        query = query.filter(Specimen.minimumElevationInMeters < maxElevation)
    if museum != '*':
        query = query.filter(Specimen.museum == museum)
    if ownedby != '*':
        query = query.filter(Specimen.ownedby == ownedby)
    if locatedat != '*':
        query = query.filter(Specimen.locatedat == locatedat)
    if collectedby != '*':
        query = query.filter(Specimen.collectedby == collectedby)
    if lifeStageSex != '*':
        query = query.filter(Specimen.lifeStageSex.like("%" + lifeStageSex + "%"))
    if caste != '*':
        query = query.filter(Specimen.caste == caste)
    if subcaste != '*':
        query = query.filter(Specimen.subcaste == subcaste)
    if fossil != '*':
        if fossil == 'true': fossil = 1
        if fossil == 'false': fossil = 0
        if fossil == 1 or fossil == 0 or fossil == '1' or fossil == '0':
            query = query.filter(Specimen.fossil == fossil)
    if status != '*':
        query = query.filter(Specimen.status == status)
    if hasImage != '*':
        if hasImage == 'true':
            query = query.filter(Specimen.imageCount > 0)
        if hasImage == 'false':
            query = query.filter(Specimen.imageCount <= 0)
    if geolocaleName != '*':
        query = query.join(Taxon).join(GeolocaleTaxon).join(Geolocale).filter(Geolocale.name == geolocaleName)
        query = addExtraGeolocaleListCriteria(query, geolocaleName)
    if validSubfamily == 'true':
        query = query.filter(func.lower(Specimen.subfamily).in_(sorted(VALID_SUBFAMILIES)))
    if validGenus == 'true':
        query = query.filter(func.lower(Specimen.genus).in_(sorted(VALID_GENERA)))

    # A fixed order (specimen code, the specimen table's key) so paging with offset neither
    # repeats nor skips records; with 500 per request, paging is how larger sets are fetched.
    query = query.order_by(Specimen.code)
    query = query.limit(limit)
    query = query.offset(offset)

    if log:
        #print(" query:" + str(query))
        print("hasImage:" + hasImage)
        # print("hasImage:" + hasImage + " query:" + str(query))
    try:
        data = query.all()
    except OperationalError as error:
        return apiError("specimens", error)
    except Exception as error:
        return apiError("specimens", error)

    if log:
        print('Query complete')

    dataList = []
    for sp in data:
        specDict = Specimen.getSpecDict(sp)

        # if (specDict['specimenCode'] == 'antweb1038039'):
        #  print("ownedBy:" + specDict['ownedBy'])  # Would appear (only on server) in terminal as SMNG, G\xc3\xb6rlitz, Germany
        dataList.append(specDict)

    params = [request.args]
    metaDataDict = {'parameters': params, 'request': str(request), 'limit': limit, 'offset': offset,
                    'count': len(dataList),
                    'queryTime': time.strftime("%H:%M:%S", time.gmtime(time.time() - startTime))}

    if ndjson != 'true':
        return jsonify(metaData=metaDataDict, specimens=dataList)  # return flask Response
    else:
        return getNdjson(dataList)  # return flask Response


# --- Geo Specimens API ------------------------------------------------------------------------------------

KM_PER_DEGREE_LATITUDE = 111.32


def radiusBox(lat, lon, radiusKm):
    """(minLat, maxLat, minLon, maxLon) of the box around lat, lon that holds a circle of radiusKm kilometres.
    2026-10-26, finding F22: the radius was added to the coordinates as degrees (radius=10 was a box of about
    2,200 km). One degree of latitude is 111.32 km; one degree of longitude is 111.32 * cos(latitude) km."""
    radiusKm = abs(float(radiusKm))
    dLat = radiusKm / KM_PER_DEGREE_LATITUDE
    cosLat = math.cos(math.radians(lat))
    if cosLat < 1e-6:
        dLon = 180.0  # at a pole every longitude is within the radius
    else:
        dLon = min(180.0, radiusKm / (KM_PER_DEGREE_LATITUDE * cosLat))
    return lat - dLat, lat + dLat, lon - dLon, lon + dLon


# http://localhost:5000/geoSpecimens?coords=37,%20-122&limit=8000&radius=2
@application.route('/geoSpecimens', methods=['GET'])
def getGeoSpecimens():
    coords = request.args.get('coords', default='*', type=str)  # (?coord=37, -122) is cal academy
    if coords == '*':
        return 'Must enter coords for a geoSpecimens request'
    radius = request.args.get('radius', default=5.0, type=float)  # in kilometres
    distinct = request.args.get('distinct', default='*', type=str)
    limit, offset, log, ndjson, startTime = setGlobals(request)
    if down(request): return ""

    query = session.query(Specimen)
    coordsArray = coords.split(",")
    try:
        # Decimal coordinates (37.77,-122.47) used to raise ValueError (HTTP 500).
        lat, lon = float(coordsArray[0]), float(coordsArray[1])
    except (ValueError, IndexError):
        return 'coords must be "latitude,longitude", for example coords=37.77,-122.47', 400
    minLat, maxLat, minLon, maxLon = radiusBox(lat, lon, radius)
    query = query.filter(Specimen.decimalLatitude <= maxLat, Specimen.decimalLatitude >= minLat)
    query = query.filter(Specimen.decimalLongitude <= maxLon, Specimen.decimalLongitude >= minLon)

    if distinct != '*':
        # The names of the specimens in the area (2026-10-26, F22: a new query without the area was started here).
        if distinct == 'subfamily':
            query = query.with_entities(Specimen.subfamily).distinct().order_by(Specimen.subfamily)
        elif distinct == 'genus':
            query = query.with_entities(Specimen.genus).distinct().order_by(Specimen.genus)
        elif distinct == 'species':
            query = query.filter(Specimen.species != None).with_entities(Specimen.genus, Specimen.species) \
                .distinct().order_by(Specimen.genus, Specimen.species)
        else:
            return 'distinct must be subfamily, genus or species', 400

        query = query.limit(limit)
        query = query.offset(offset)

        try:
            data = query.all()
        except OperationalError as error:
            return apiError("geoSpecimens", error)
        except Exception as error:
            return apiError("geoSpecimens", error)

        dataList = []
        for sp in data:
            if distinct == 'subfamily':
                dataList.append(sp.subfamily)
            #  specDict = { 'subfamily': sp.subfamily }
            if distinct == 'genus':
                dataList.append(sp.genus)
            if distinct == 'species':
                dataList.append(initCap(sp.genus) + ' ' + sp.species)

        params = [request.args]
        metaDataDict = {'parameters': params, 'request': str(request), 'limit': limit, 'count': len(dataList)}

        if distinct == 'subfamily':
            return jsonify(metaData=metaDataDict, subfamilies=dataList)
        if distinct == 'genus':
            return jsonify(metaData=metaDataDict, genera=dataList)
        return jsonify(metaData=metaDataDict, species=dataList)

    # if not distinct. Show everything (in specimen code order, for stable paging).
    query = query.order_by(Specimen.code)
    query = query.limit(limit)
    query = query.offset(offset)

    try:
        data = query.all()
    except OperationalError as error:
        return apiError("geoSpecimens", error)
    except Exception as error:
        return apiError("geoSpecimens", error)

    dataList = []
    for sp in data:
        specDict = Specimen.getSpecDict(sp)

        dataList.append(specDict)

    # print(query)

    params = [request.args]
    metaDataDict = {'parameters': params, 'request': str(request), 'limit': limit, 'count': len(dataList)}

    if ndjson != 'true':
        return jsonify(metaData=metaDataDict, specimens=dataList)  # return flask Response
    else:
        return getNdjson(dataList)  # return flask Response


# --- GeolocaleSpeciesStats API ------------------------------------------------------------------------------------

def getGeoSpeciesStatsDict(row):
    geoSpeciesStats = {
        'taxonName': row[0]
        , 'genus': row[1]
        , 'species': row[2]
        , 'queenCount': row[3]
        , 'queenErgatoidCount': row[4]
        , 'queenAlateDealateCount': row[5]
        , 'queenBrachypterousCount': row[6]
        , 'workerCount': row[7]
        , 'workerMajorSoldierCount': row[8]
        , 'workerNormalCount': row[9]
        , 'maleCount': row[10]
        , 'maleErgatoidCount': row[11]
        , 'maleAlateCount': row[12]
    }
    return geoSpeciesStats


@application.route('/geoSpeciesStats', methods=['GET'])
def getGeoSpeciesStats():
    geolocaleName = request.args.get('geolocaleName', default='*', type=str)
    limit, offset, log, ndjson, startTime = setGlobals(request)
    if down(request): return ""

    # add introduced and endemics

    extraClause = ""
    geolocale = getGeolocale(geolocaleName)
    if geolocale is None:
        return "Geolocale not found"
    georank = geolocale.georank
    extraClause = " s." + georank + " = :geolocaleName"

    # queenErgatoidCount and maleErgatoidCount count the ergatoids of their caste (2026-10-22: both counted every
    # ergatoid specimen, so a male ergatoid was also a queen ergatoid).
    query = "select s.taxon_name, s.genus, s.species " \
            + " , count(case when caste='queen' then 1 end)" \
            + " , count(case when caste='queen' and subcaste='ergatoid' then 1 end)" \
            + " , count(case when subcaste='alate/dealate' then 1 end)" \
            + " , count(case when subcaste='brachypterous' then 1 end)" \
            + " , count(case when caste='worker' then 1 end)" \
            + " , count(case when subcaste='major/soldier' then 1 end)" \
            + " , count(case when subcaste='normal' then 1 end)" \
            + " , count(case when caste='male' then 1 end)" \
            + " , count(case when caste='male' and subcaste='ergatoid' then 1 end)" \
            + " , count(case when subcaste='alate' then 1 end)" \
            + " from specimen s, taxon t, geolocale_taxon gt, geolocale g" \
            + " where s.taxon_name = t.taxon_name and gt.taxon_name = t.taxon_name " \
            + "   and gt.geolocale_id = g.id and " \
            + extraClause \
            + " and g.name = :geolocaleName" \
            + "  and s.status = 'valid'" \
            + " group by s.taxon_name, s.genus, s.species" \
            + " order by s.taxon_name" \
            + " limit :limit offset :offset"

    # print(query)
    dataList = []
    result = session.execute(text(query), {"geolocaleName": geolocaleName, "limit": limit, "offset": offset})
    for row in result:
        geoSpeciesStats = getGeoSpeciesStatsDict(row)
        dataList.append(geoSpeciesStats)

    params = [request.args]
    metaDataDict = {'parameters': params, 'request': str(request), 'limit': limit, 'offset': offset,
                    'count': len(dataList)}

    if ndjson != 'true':
        return jsonify(metaData=metaDataDict, geoSpeciesStats=dataList)  # return flask Response
    else:
        return getNdjson(dataList)  # return flask Response


# --- Taxon API ------------------------------------------------------------------------------------

class Taxon(Base):
    __tablename__ = 'taxon'
    taxonName = Column('taxon_name', String, primary_key=True)
    taxarank = Column(String)
    subfamily = Column(String)
    genus = Column(String)
    species = Column(String)
    subspecies = Column(String)
    parent = Column(String)
    fossil = Column(Boolean)
    source = Column(String)
    created = Column(DateTime)
    family = Column(String)
    kingdomName = Column('kingdom_name', String)
    phylumName = Column('phylum_name', String)
    className = Column('class_name', String)
    orderName = Column('order_name', String)
    antcat = Column(Integer)
    subfamilyCount = Column('subfamily_count', Integer)
    genusCount = Column('genus_count', Integer)
    speciesCount = Column('species_count', Integer)
    specimenCount = Column('specimen_count', Integer)
    imageCount = Column('image_count', Integer)
    parentTaxonName = Column('parent_taxon_name', String)
    type = Column(Boolean)
    antcatId = Column('antcat_id', Integer)
    authorDate = Column('author_date', String)
    authors = Column(String)
    year = Column(String)
    status = Column(String)
    currentValidName = Column('current_valid_name', String)
    bioregion = Column(String)
    country = Column(String)
    currentValidParent = Column('current_valid_parent', String)
    lineNum = Column('line_num', Integer)
    accessGroup = ('access_group', Integer)

    geolocaleTaxa = relationship("GeolocaleTaxon")

    def __repr__(self):
        return "<Taxa( \
           taxonName='%s', taxarank='%s', subfamily='%s', genus='%s' \
         , species='%s%', subspecies='%s', parent='%s' \
         , fossil='%s', source='%s', created='%s' \
         , family='%s', kingdomName='%s', phylumName='%s' \
         , className='%s', orderName='%s', antcat='%s', subfamilyCount ='%s' \
         , genusCount='%s', speciesCount='%s', specimenCount='%s', imageCount='%s' \
         , parentTaxonName='%s', type='%s', antcatId='%s', authorDate='%s' \
         , authors='%s', year='%s', status='%s', currentValidName='%s', bioregion='%s' \
         , country='%s', currentValidParent='%s', lineNum='%s', accessGroup='%s' \
       )>" % (
            self.taxonName, self.taxarank, self.subfamily, self.genus
            , self.species, self.subspecies, self.parent
            , self.fossil, self.source, self.created
            , self.family, self.kingdomName, self.phylumName
            , self.className, self.orderName, self.antcat, self.subfamilyCount
            , self.genusCount, self.speciesCount, self.subspeciesCount, self.imageCount
            , self.parentTaxonName, self.type, self.antcatId, self.authorDate
            , self.authors, self.year, self.status, self.currentValidName, self.bioregion
            , self.country, self.currentValidParent, self.lineNum, self.accessGroup \
            )


@application.route('/taxa', methods=['GET'])
def getTaxa():
    taxonName = request.args.get('taxonName', default='*', type=str)
    taxarank = request.args.get('taxarank', default='*', type=str)
    subfamily = request.args.get('subfamily', default='*', type=str)
    genus = request.args.get('genus', default='*', type=str)
    species = request.args.get('species', default='*', type=str)
    subspecies = request.args.get('subspecies', default='*', type=str)
    # parent = request.args.get('parentTaxonName', default='*', type=str) # Could do parentTaxonName but not parent.
    status = request.args.get('status', default='*', type=str)
    limit, offset, log, ndjson, startTime = setGlobals(request)
    if down(request): return ""

    query = session.query(Taxon)
    if taxonName != '*':
        query = query.filter(Taxon.taxonName == taxonName)
    if taxarank != '*':
        query = query.filter(Taxon.taxarank == taxarank)
    if subfamily != '*':
        query = query.filter(Taxon.subfamily == subfamily)
    if genus != '*':
        query = query.filter(Taxon.genus == genus)
    if species != '*':
        query = query.filter(Taxon.species == species)
    if subspecies != '*':
        query = query.filter(Taxon.subspecies == subspecies)
    # if (parent != '*'):
    #  query = query.filter(Taxon.parent == parent)
    if status != '*':
        query = query.filter(Taxon.status == status)

    # limit and offset (from setGlobals) were ignored here, unlike the other endpoints.
    # A fixed order (the taxon table's key) so paging with offset neither repeats nor skips (F8).
    query = query.order_by(Taxon.taxonName)
    query = query.limit(limit)
    query = query.offset(offset)

    try:
        data = query.all()
    except OperationalError as error:
        return apiError("taxa", error)
    except Exception as error:
        return apiError("taxa", error)

    # print(query)

    if log == 'true':
        print('query:' + str(query))

    taxonCount = 0
    dataList = []
    taxonDict = {}
    taxa = []
    for taxon in data:
        taxonCount += 1
        taxonDict = {
            'taxonName': taxon.taxonName
            , 'taxarank': taxon.taxarank
            , 'subfamily': taxon.subfamily
            , 'genus': initCap(taxon.genus)
            , 'species': taxon.species
            , 'subspecies': taxon.subspecies
            , 'parentTaxonName': taxon.parentTaxonName
            , 'fossil': taxon.fossil
            , 'source': taxon.source
            , 'created': taxon.created
            , 'family': initCap(taxon.family)
            , 'kingdomName': initCap(taxon.kingdomName)
            , 'phylumName': initCap(taxon.phylumName)
            , 'className': initCap(taxon.className)
            , 'orderName': initCap(taxon.orderName)
            , 'antcat': taxon.antcat
            , 'subfamilyCount': taxon.subfamilyCount
            , 'genusCount': taxon.genusCount
            , 'speciesCount': taxon.speciesCount
            , 'specimenCount': taxon.specimenCount
            , 'imageCount': taxon.imageCount
            , 'parentTaxonName': taxon.parentTaxonName
            , 'type': taxon.type
            , 'antcatId': taxon.antcatId
            , 'authorDate': taxon.authorDate
            , 'authors': taxon.authors
            , 'year': taxon.year
            , 'status': taxon.status
            , 'currentValidName': taxon.currentValidName
            , 'bioregion': taxon.bioregion
            , 'currentValidParent': taxon.currentValidParent
            # , 'accessGroup': taxon.accessGroup  #causes: TypeError: Object of type 'VisitableType' is not JSON serializable
        }
        taxa.append(taxonDict)

        # print(taxonDict)

        dataList.append(taxonDict)

    params = [request.args]
    metaDataDict = {'parameters': params, 'request': str(request), 'taxonCount': taxonCount}

    if ndjson != 'true':
        return jsonify(metaData=metaDataDict, taxa=dataList)  # return flask Response
    else:
        return getNdjson(dataList)  # return flask Response


# --- Distinct Taxon API ------------------------------------------------------------------------------------

@application.route('/distinctTaxa', methods=['GET'])
def getDistinctTaxa():
    taxarank = request.args.get('taxarank', default='*', type=str)
    country = request.args.get('country', default='*', type=str)
    habitat = request.args.get('habitat', default='*', type=str)
    minDate = request.args.get('minDate', default='*', type=str)
    maxDate = request.args.get('maxDate', default='*', type=str)
    minElevation = request.args.get('minElevation', default='*', type=str)
    maxElevation = request.args.get('maxElevation', default='*', type=str)
    status = request.args.get('status', default='*', type=str)
    limit, offset, log, ndjson, startTime = setGlobals(request)
    if down(request): return ""

    query = session.query(Specimen)

    if 'subfamily' == taxarank:
        query = session.query(Specimen.subfamily).distinct()
    if 'genus' == taxarank:
        query = session.query(Specimen.genus).distinct()
    if 'species' == taxarank:
        query = session.query(Specimen.species).distinct()

    # if any of these are used, performance suffers!    
    if country != '*':
        query = query.filter(Specimen.country.contains(country))
    if habitat != '*':
        query = query.filter(Specimen.habitat.contains(habitat))
    if maxDate != '*':
        query = query.filter(Specimen.dateCollectedStart < maxDate)
    if minDate != '*':
        query = query.filter(Specimen.dateCollectedStart > minDate)
    if minElevation != '*':
        query = query.filter(Specimen.minimumElevationInMeters > minElevation)
    if maxElevation != '*':
        query = query.filter(Specimen.minimumElevationInMeters < maxElevation)
    if status != '*':
        query = query.filter(Specimen.status == status)

    # A fixed order so paging with offset neither repeats nor skips (F8).
    if 'subfamily' == taxarank:
        query = query.order_by(Specimen.subfamily)
    elif 'genus' == taxarank:
        query = query.order_by(Specimen.genus)
    elif 'species' == taxarank:
        query = query.order_by(Specimen.species)
    else:
        query = query.order_by(Specimen.code)
    query = query.limit(limit)
    query = query.offset(offset)

    try:
        data = query.all()
    except OperationalError as error:
        return apiError("distinctTaxa", error)
    except Exception as error:
        return apiError("distinctTaxa", error)

    if log == 'true':
        print("log query:" + str(query))

    dataList = []
    for taxa in data:
        if 'subfamily' == taxarank:
            dataList.append(taxa.subfamily)
        if 'genus' == taxarank:
            dataList.append(taxa.genus)
            # print("taxarank:" + taxarank + " genus:" + str(taxa.genus))
        if 'species' == taxarank:
            dataList.append(taxa.species)

    params = [request.args]
    metaDataDict = {'parameters': params, 'request': str(request), 'limit': limit, 'count': len(dataList)}

    if isDevMode:
        print("devMode query:" + str(query))

    if 'subfamily' == taxarank:
        if ndjson != 'true':
            return jsonify(metaData=metaDataDict, subfamilies=dataList)  # return flask Response
        else:
            return getNdjson(dataList)  # return flask Response
    if 'genus' == taxarank:
        if ndjson != 'true':
            return jsonify(metaData=metaDataDict, genera=dataList)  # return flask Response
        else:
            return getNdjson(dataList)  # return flask Response

    if ndjson != 'true':
        return jsonify(metaData=metaDataDict, taxarank=dataList)  # return flask Response
    else:
        return getNdjson(dataList)  # return flask Response


# --- Images API -------------------------------------------------------------------------------------

class Image(Base):
    __tablename__ = 'image'

    id = Column(String, primary_key=True)
    shotType = Column('shot_type', String)
    code = Column('image_of_id', String)
    uploadDate = Column('upload_date', String)
    shotNumber = Column('shot_number', String)
    hasTiff = Column('has_tiff', String)

    def __repr__(self):
        return "<Image(id='%s', shotType='%s', code='%s', uploadDate='%s', shotNumber='%s', hasTiff='%s')>" % (
            self.id, self.shotType, self.code, self.uploadDate, self.shotNumber, self.hasTiff)


def imagesBySpecimen(data):
    """The /images reply: (one dict per specimen with its images, specimen count, image count). data: the image rows
    in order of specimen code. 2026-10-22: with no image, no specimen (was specimenCount 1 and one empty dict), and
    every specimen under 'specimenCode' (all but the last were under 'spefimenCode'); tests/unit/test_api_counts.py."""
    specimenCount = 0
    imageCount = 0
    dataList = []
    specimenDict = {}
    tCode = ''
    images = []
    for image in data:
        imageCount += 1
        base = 'https://www.antweb.org/images/' + image.code + '/' + image.code + '_' \
               + image.shotType + '_' + str(image.shotNumber)
        # print('code' + image.code + ' base:' + base)
        urls = [base + '_low.jpg'
            , base + '_med.jpg'
            , base + '_high.jpg'
            , base + '_thumbview.jpg'
                ]
        imageDict = {
            'imageId': image.id
            , 'shotType': image.shotType
            , "urls": urls
            , 'uploadDate': image.uploadDate
            , 'shotNumber': image.shotNumber
            , 'hasTiff': image.hasTiff
        }
        if tCode != image.code:
            if tCode != '':  # we have one to insert. (Is not the first record).
                specimenDict = {
                    'specimenCode': tCode
                    , 'url': specimenApiUrl(tCode)
                    , 'images': images
                }
                specimenCount += 1
                dataList.append(specimenDict)

            images = [imageDict]
            tCode = image.code

        else:
            images.append(imageDict)

    # Handle the last instance of the record (due to group by procedural logic). 
    # 2026-10-22: only when there was one (no images gave specimenCount 1 and an empty specimen), and every
    # specimen under 'specimenCode' (all but the last were under 'spefimenCode').
    if tCode != '':
        specimenDict = {
            'specimenCode': tCode
            , 'url': specimenApiUrl(tCode)
            , 'images': images
        }
        specimenCount += 1
        dataList.append(specimenDict)
    return dataList, specimenCount, imageCount


@application.route('/images', methods=['GET'])
def getImages():
    since = request.args.get('since', default='*', type=str)
    code = request.args.get('specimenCode', default='*', type=str)
    shotType = request.args.get('shotType', default='*', type=str)
    shotNumber = request.args.get('shotNumber', default='*', type=str)
    hasTiff = request.args.get('hasTiff', default='*', type=str)
    limit, offset, log, ndjson, startTime = setGlobals(request)
    if down(request): return ""

    query = session.query(Image)
    if since != '*':
        day_interval_before = datetime.now() - timedelta(days=int(since))
        query = query.filter(Image.uploadDate >= day_interval_before)
    if code != '*':
        query = query.filter(Image.code == code)
    if shotType != '*':
        query = query.filter(Image.shotType == shotType)
    if shotNumber != '*':
        query = query.filter(Image.shotNumber == shotNumber)
    if hasTiff != '*':
        if hasTiff == 'true':
            query = query.filter(Image.hasTiff == 1)
        if hasTiff == 'false':
            query = query.filter(Image.hasTiff == 0)
    # imagesBySpecimen needs the images of one specimen together (2026-10-22: without an order a specimen could be
    # split into several entries and counted more than once)
    query = query.order_by(Image.code, Image.shotType, Image.shotNumber, Image.id)
    query = query.limit(limit)
    query = query.offset(offset)

    try:
        data = query.all()
    except OperationalError as error:
        return apiError("images", error)
    except Exception as error:
        return apiError("images", error)

    dataList, specimenCount, imageCount = imagesBySpecimen(data)

    # SELECT id,shot_type,upload_date,shot_number,has_tiff FROM image WHERE image_of_id=? ORDER BY shot_number ASC     
    if isDevMode:
        print(query)

    params = [request.args]
    metaDataDict = {'parameters': params, 'request': str(request), 'limit': limit, 'specimenCount': specimenCount,
                    'count': imageCount}

    if ndjson != 'true':
        return jsonify(metaData=metaDataDict, images=dataList)  # return flask Response
    else:
        return getNdjson(dataList)  # return flask Response


# --- Measurements API -------------------------------------------------------------------------------------
# Image-level trait measurements (myrmetrics ant body-size dataset). Table: measurement (db/upgrade/8.106).
# One row per image x trait x release. Specimen attributes (taxonomy, caste, locality) come from the
# linked specimen record via api3_1_specimen, not from the measurement row.

class Measurement(Base):
    __tablename__ = 'measurement'

    id = Column(Integer, primary_key=True)
    release = Column('data_release', String)
    recordId = Column('record_id', String)
    code = Column('specimen_code', String)
    imageFilename = Column('image_filename', String)
    imageUrl = Column('image_url', String)
    view = Column('image_view', String)
    imageWidth = Column('image_width', Integer)
    imageHeight = Column('image_height', Integer)
    trait = Column('trait', String)
    traitName = Column('trait_name', String)
    value = Column('value', Numeric(asdecimal=False))
    unit = Column('unit', String)
    valuePx = Column('value_px', Numeric(asdecimal=False))
    traitKpA = Column('trait_kp_a', String)
    traitKpB = Column('trait_kp_b', String)
    scaleValueRaw = Column('scale_value_raw', Numeric(asdecimal=False))
    scaleUnitRaw = Column('scale_unit_raw', String)
    scaleMm = Column('scale_mm', Numeric(asdecimal=False))
    scaleValueSource = Column('scale_value_source', String)
    scaleBarPx = Column('scale_bar_px', Numeric(asdecimal=False))
    scaleBarSource = Column('scale_bar_source', String)
    pxPerMm = Column('px_per_mm', Numeric(asdecimal=False))
    kp1Name = Column('kp1_name', String)
    kp1X = Column('kp1_x', Numeric(asdecimal=False))
    kp1Y = Column('kp1_y', Numeric(asdecimal=False))
    kp1Conf = Column('kp1_conf', Numeric(asdecimal=False))
    kp2Name = Column('kp2_name', String)
    kp2X = Column('kp2_x', Numeric(asdecimal=False))
    kp2Y = Column('kp2_y', Numeric(asdecimal=False))
    kp2Conf = Column('kp2_conf', Numeric(asdecimal=False))
    kp3Name = Column('kp3_name', String)
    kp3X = Column('kp3_x', Numeric(asdecimal=False))
    kp3Y = Column('kp3_y', Numeric(asdecimal=False))
    kp3Conf = Column('kp3_conf', Numeric(asdecimal=False))
    kp4Name = Column('kp4_name', String)
    kp4X = Column('kp4_x', Numeric(asdecimal=False))
    kp4Y = Column('kp4_y', Numeric(asdecimal=False))
    kp4Conf = Column('kp4_conf', Numeric(asdecimal=False))
    kp5Name = Column('kp5_name', String)
    kp5X = Column('kp5_x', Numeric(asdecimal=False))
    kp5Y = Column('kp5_y', Numeric(asdecimal=False))
    kp5Conf = Column('kp5_conf', Numeric(asdecimal=False))
    kp6Name = Column('kp6_name', String)
    kp6X = Column('kp6_x', Numeric(asdecimal=False))
    kp6Y = Column('kp6_y', Numeric(asdecimal=False))
    kp6Conf = Column('kp6_conf', Numeric(asdecimal=False))
    nDetections = Column('n_detections', Integer)
    keypointsCorrected = Column('keypoints_corrected', Boolean)
    scaleCorrected = Column('scale_corrected', Boolean)
    qaKpOk = Column('qa_kp_ok', Boolean)
    qaRulerOk = Column('qa_ruler_ok', Boolean)
    qaMissingHead = Column('qa_missing_head', Boolean)
    qaMissingGaster = Column('qa_missing_gaster', Boolean)
    status = Column('status', String)
    flagReason = Column('flag_reason', String)
    flagNote = Column('flag_note', String)
    poseModel = Column('pose_model', String)
    rulerModel = Column('ruler_model', String)
    contributor = Column('contributor', String)
    recordDate = Column('record_date', String)

    def __repr__(self):
        return "<Measurement(release='%s', recordId='%s')>" % (self.release, self.recordId)

    def getDict(m, sp):
        keypoints = []
        for i in range(1, 7):
            name = getattr(m, 'kp%dName' % i)
            if name:
                keypoints.append({'name': name, 'x': getattr(m, 'kp%dX' % i), 'y': getattr(m, 'kp%dY' % i),
                                  'conf': getattr(m, 'kp%dConf' % i)})
        d = {
            'release': m.release
            , 'recordId': m.recordId
            , 'specimenCode': m.code
            , 'imageFilename': m.imageFilename
            , 'imageUrl': m.imageUrl
            , 'view': m.view
            , 'imageWidth': m.imageWidth
            , 'imageHeight': m.imageHeight
            , 'trait': m.trait
            , 'traitName': m.traitName
            , 'value': m.value
            , 'unit': m.unit
            , 'valuePx': m.valuePx
            , 'traitKeypoints': [m.traitKpA, m.traitKpB]
            , 'scale': {
                'valueRaw': m.scaleValueRaw
                , 'unitRaw': m.scaleUnitRaw
                , 'mm': m.scaleMm
                , 'valueSource': m.scaleValueSource
                , 'barPx': m.scaleBarPx
                , 'barSource': m.scaleBarSource
                , 'pxPerMm': m.pxPerMm
            }
            , 'keypoints': keypoints
            , 'nDetections': m.nDetections
            , 'keypointsCorrected': m.keypointsCorrected
            , 'scaleCorrected': m.scaleCorrected
            , 'qa': {
                'kpOk': m.qaKpOk
                , 'rulerOk': m.qaRulerOk
                , 'missingHead': m.qaMissingHead
                , 'missingGaster': m.qaMissingGaster
            }
            , 'status': m.status
            , 'flagReason': m.flagReason
            , 'flagNote': m.flagNote
            , 'poseModel': m.poseModel
            , 'rulerModel': m.rulerModel
            , 'contributor': m.contributor
            , 'recordDate': str(m.recordDate) if m.recordDate else None
            , 'specimenUrl': prodServer + '/specimen.do?code=' + m.code
        }
        if sp is not None:
            d['specimen'] = {
                'subfamily': sp.subfamily
                , 'genus': sp.genus
                , 'species': sp.species
                , 'subspecies': sp.subspecies
                , 'scientificName': sp.scientific_name
                , 'taxonStatus': sp.status
                , 'fossil': sp.fossil
                , 'lifeStageSex': sp.lifeStageSex
                , 'caste': sp.caste
                , 'subcaste': sp.subcaste
                , 'country': sp.country
                , 'adm1': sp.adm1
                , 'bioregion': sp.bioregion
                , 'decimalLatitude': sp.decimalLatitude
                , 'decimalLongitude': sp.decimalLongitude
                , 'museum': sp.museum
            }
        else:
            d['specimen'] = None  # specimen record no longer on AntWeb
        return d


# http://localhost:5000/measurements?genus=pheidole&trait=WL&release=1.0&status=reviewed
@application.route('/measurements', methods=['GET'])
def getMeasurements():
    release = request.args.get('release', default='*', type=str)
    trait = request.args.get('trait', default='*', type=str)
    view = request.args.get('view', default='*', type=str)
    mStatus = request.args.get('status', default='*', type=str)
    hasValue = request.args.get('hasValue', default='*', type=str)
    code = request.args.get('specimenCode', default='*', type=str)
    subfamily = request.args.get('subfamily', default='*', type=str)
    genus = request.args.get('genus', default='*', type=str)
    species = request.args.get('species', default='*', type=str)
    country = request.args.get('country', default='*', type=str)
    adm1 = request.args.get('adm1', default='*', type=str)
    bioregion = request.args.get('bioregion', default='*', type=str)
    caste = request.args.get('caste', default='*', type=str)
    taxonStatus = request.args.get('taxonStatus', default='*', type=str)
    limit, offset, log, ndjson, startTime = setGlobals(request)
    if down(request): return ""

    query = session.query(Measurement, Specimen).outerjoin(Specimen, Specimen.code == Measurement.code)

    if release != '*':
        query = query.filter(Measurement.release == release)
    if trait != '*':
        query = query.filter(Measurement.trait.in_(trait.split(',')))
    if view != '*':
        query = query.filter(Measurement.view == view)
    if mStatus != '*':
        query = query.filter(Measurement.status.in_(mStatus.split(',')))
    if hasValue == 'true':
        query = query.filter(Measurement.value != None)
    if hasValue == 'false':
        query = query.filter(Measurement.value == None)
    if code != '*':
        query = query.filter(Measurement.code == code)
    if subfamily != '*':
        query = query.filter(Specimen.subfamily == subfamily)
    if genus != '*':
        query = query.filter(Specimen.genus == genus)
    if species != '*':
        query = query.filter(Specimen.species == species)
    if country != '*':
        query = query.filter(Specimen.country.contains(country))
    if adm1 != '*':
        query = query.filter(Specimen.adm1 == adm1)
    if bioregion != '*':
        query = query.filter(Specimen.bioregion == bioregion)
    if caste != '*':
        query = query.filter(Specimen.caste == caste)
    if taxonStatus != '*':
        query = query.filter(Specimen.status == taxonStatus)

    query = query.order_by(Measurement.id)
    query = query.limit(limit)
    query = query.offset(offset)

    try:
        data = query.all()
    except OperationalError as error:
        return apiError("measurements", error)
    except Exception as error:
        return apiError("measurements", error)

    dataList = [Measurement.getDict(m, sp) for (m, sp) in data]

    params = [request.args]
    metaDataDict = {'parameters': params, 'request': str(request), 'limit': limit, 'offset': offset,
                    'count': len(dataList),
                    'queryTime': time.strftime("%H:%M:%S", time.gmtime(time.time() - startTime))}

    if ndjson != 'true':
        return jsonify(metaData=metaDataDict, measurements=dataList)
    else:
        return getNdjson(dataList)


# --- Taxa Images API -------------------------------------------------------------------------------------

# noinspection PyMethodParameters
class TaxaImage(Base):
    __tablename__ = 'taxon_image'

    taxonName = Column('taxon_name', String)
    subfamily = Column('subfamily', String)
    genus = Column('genus', String)
    species = Column('species', String)
    subspecies = Column('subspecies', String)
    code = Column('code', String)
    uid = Column(Integer, primary_key=True)
    uploadDate = Column('upload_date', String)
    shotType = Column('shot_type', String)
    shotNumber = Column('shot_number', String)
    hasTiff = Column('has_tiff', String)

    def __repr__(self):
        return "<TaxaImage(taxonName='%s', subfamily='%s', genus='%s', species='%ds', subspecies='%s', code='%s' \
         uid='%s', shotType='%s', code='%s', uploadDate='%s', shotNumber='%s', hasTiff='%s')>" % (
            self.taxonName, self.subfamily, self.genus, self.species, self.subspecies, self.code,
            self.uid, self.shotType, self.code, self.uploadDate, self.shotNumber, self.hasTiff
        )

    # Rather like a static methods...

    def getTaxaDict(data):
        taxaDict = {}
        for taxaImage in data:

            # taxaImage is the object created from the result set.
            # taxaDict is the dictionary containing the hierarchical data taxon - specimen - image.

            # taxaDict is of the format taxonName: {taxonDict}
            if taxaImage.taxonName not in taxaDict:
                taxonDict = {
                    'taxonName': taxaImage.taxonName
                    , 'subfamily': taxaImage.subfamily
                    , 'genus': taxaImage.genus
                    , 'species': taxaImage.species
                    , 'subspecies': taxaImage.subspecies
                    , 'specimen': []
                }
                taxaDict[taxaImage.taxonName] = taxonDict
                # print('getTaxaDict() + taxonName:' + taxaImage.taxonName)
            else:
                taxonDict = taxaDict[taxaImage.taxonName]
            # print('getTaxaDict() taxonName:' + taxaImage.taxonName)
            specimens = taxonDict['specimen']

            specimenDict = TaxaImage.getFromSpecimens(specimens, taxaImage.code)
            if not specimenDict:
                specimenDict = {
                    'code': taxaImage.code
                    , 'images': []
                }
                specimens.append(specimenDict)

            imagesDict = specimenDict['images']

            if taxaImage.uid not in imagesDict:
                base = 'https://www.antweb.org/images/' + taxaImage.code + '/' + taxaImage.code + '_' \
                       + taxaImage.shotType + '_' + str(taxaImage.shotNumber)
                urls = [base + '_low.jpg'
                    , base + '_med.jpg'
                    , base + '_high.jpg'
                    , base + '_thumbview.jpg'
                        ]
                imageDict = {
                    'imageId': taxaImage.uid
                    , 'shotType': taxaImage.shotType
                    , "urls:": urls
                    , 'uploadDate': taxaImage.uploadDate
                    , 'shotNumber': taxaImage.shotNumber
                    , 'hasTiff': taxaImage.hasTiff
                }
                imagesDict.append(imageDict)
                # print('getTaxaDict() uid:' + str(taxaImage.uid))
            else:
                imageDict = imagesDict[taxaImage.uid]

        return taxaDict

    def getFromSpecimens(specimens, code):
        for specimenDict in specimens:
            if specimenDict['code'] == code:
                return specimenDict

    def getTaxonCount(taxaDict):
        return len(taxaDict)

    def getSpecimenCount(taxaDict):
        specimenCount = 0
        for taxonDict in taxaDict:
            val = taxaDict[taxonDict]
            specimenCount = specimenCount + len(val['specimen'])
        return specimenCount

    def getImageCount(taxaDict):
        imageCount = 0
        for key in taxaDict:
            taxonDict = taxaDict[key]
            specimens = taxonDict['specimen']
            for specimenDict in specimens:
                specimenImages = specimenDict['images']
                # print("imageCount len:" + str(len(specimenImages)))
                imageCount = imageCount + len(specimenImages)
        return imageCount

    def getDataList(taxaDict):
        dataList = []
        for taxonDict in taxaDict:
            # print("taxonDict: " + str(taxaDict[taxonDict]))
            taxonDict = taxaDict[taxonDict]
            # print("taxonName: " + taxonDict['taxonName'])
            dataList.append(taxonDict)
        return dataList


@application.route('/taxaImages', methods=['GET'])
def getTaxaImages():
    taxonName = request.args.get('taxonName', default='*', type=str)
    subfamily = request.args.get('subfamily', default='*', type=str)
    genus = request.args.get('genus', default='*', type=str)
    species = request.args.get('species', default='*', type=str)
    subspecies = request.args.get('subspecies', default='*', type=str)
    specimenCode = request.args.get('specimenCode', default='*', type=str)
    imageId = request.args.get('imageId', default='*', type=str)
    # uploadDate = request.args.get('uploadDate', default='*', type=str)
    shotType = request.args.get('shotType', default='*', type=str)
    shotNumber = request.args.get('shotNumber', default='*', type=str)
    hasTiff = request.args.get('hasTiff', default='*', type=str)

    limit, offset, log, ndjson, startTime = setGlobals(request)
    if down(request): return ""

    query = session.query(TaxaImage)
    if taxonName != '*':
        query = query.filter(TaxaImage.taxonName == taxonName)
    if subfamily != '*':
        query = query.filter(TaxaImage.subfamily == subfamily)
    if genus != '*':
        query = query.filter(TaxaImage.genus == genus)
    if species != '*':
        query = query.filter(TaxaImage.species == species)
    if subspecies != '*':
        query = query.filter(TaxaImage.subspecies == subspecies)
    if specimenCode != '*':
        query = query.filter(TaxaImage.code == specimenCode)
    if imageId != '*':
        query = query.filter(TaxaImage.uid == imageId)
    # if (uploadDate != '*'):
    #  query = query.filter(TaxaImage.uploadDate like uploadDate)
    if shotType != '*':
        query = query.filter(TaxaImage.shotType == shotType)
    if shotNumber != '*':
        query = query.filter(TaxaImage.shotNumber == shotNumber)
    if hasTiff != '*':
        if hasTiff == 'true':
            query = query.filter(TaxaImage.hasTiff == 1)
        if hasTiff == 'false':
            query = query.filter(TaxaImage.hasTiff == 0)

    # F8: the order was computed and thrown away (no "query ="); uid (the key) makes it complete.
    query = query.order_by(TaxaImage.taxonName, TaxaImage.code, TaxaImage.uid)
    query = query.limit(limit)
    query = query.offset(offset)

    try:
        data = query.all()
    except OperationalError as error:
        return apiError("taxaImages", error)
    except Exception as error:
        return apiError("taxaImages", error)

    # Looks like this might be necessary. Unless we can upgrade Python to 3.8. May 24 2020.
    # will need to be taxaImage.getTaxaDict(data); but the method declaration will need (self,) as will the other methods.
    # taxaImage = TaxaImage()
    taxaDict = TaxaImage.getTaxaDict(data)

    taxonCount = TaxaImage.getTaxonCount(taxaDict)
    specimenCount = TaxaImage.getSpecimenCount(taxaDict)
    imageCount = TaxaImage.getImageCount(taxaDict)
    dataList = TaxaImage.getDataList(taxaDict)
    params = [request.args]
    metaDataDict = {'parameters': params, 'request': str(request), 'limit': limit, 'taxonCount': taxonCount,
                    'specimenCount': specimenCount, 'imageCount': imageCount}

    if ndjson != 'true':
        return returnJsonify("taxaImages", jsonify(metaData=metaDataDict, taxaImages=dataList))  # return flask Response
    else:
        return getNdjson(dataList)  # return flask Response


# --- Geolocale API -------------------------------------------------------------------------------------

class Geolocale(Base):
    __tablename__ = 'geolocale'

    id = Column(String, primary_key=True)
    name = Column(String)
    parent = Column(String)
    georank = Column(String)
    isValid = Column('is_valid', String)
    region = Column(String)
    subregion = Column(String)
    country = Column(String)
    bioregion = Column(String)
    subfamilyCount = Column('subfamily_count', Integer)
    genusCount = Column('genus_count', Integer)
    speciesCount = Column('species_count', Integer)
    specimenCount = Column('specimen_count', Integer)
    imageCount = Column('image_count', Integer)
    imagedSpecimenCount = Column('imaged_specimen_count', Integer)
    endemicSpeciesCount = Column('endemic_species_count', Integer)
    introducedSpeciesCount = Column('introduced_species_count', Integer)
    isoCode = Column(String)
    iso3Code = Column(String)

    def __repr__(self):
        return "<Geolocales( \
           id='%s', name='%s', parent='%s', georank='%s' \
         , isValid='%s' \
         , region='%s', subregion='%s', country='%s', bioregion='%s' \
         , subfamily_count='%s', genus_count='%s', species_count='%s' \
         , specimen_count='%s', image_count='%s', imaged_specimen_count='%s' \
         , endemicSpeciesCount='%s', introducedSpeciesCount='%s' \
         , isoCode='%s', iso3Code='%s' \
       )>" % (
            self.id, self.name, self.parent, self.georank
            , self.isValid
            , self.region, self.subregion, self.country, self.bioregion
            , self.subfamilyCount, self.genusCount, self.speciesCount
            , self.specimenCount, self.imageCount, self.imagedSpecimenCount
            , self.endemicSpeciesCount, self.introducedSpeciesCount
            , self.isoCode, self.iso3Code
            )


def getGeolocale(geolocaleName):
    query = session.query(Geolocale)
    query = query.filter(Geolocale.name == geolocaleName)
    try:
        data = query.all()
    except OperationalError as error:
        print("getGeolocales operational error:" + str(error))
        return
    except:
        message = "getGeolocale error:" + str(request)
        print(message)  # # + error.orig.message, error.params
        return
    for geolocale in data:
        return geolocale
    return


@application.route('/geolocales', methods=['GET'])
def getGeolocales():
    id = request.args.get('geolocaleId', default='*', type=str)
    name = request.args.get('geolocaleName', default=None, type=str)
    if name is None:
        name = request.args.get('name', default='*', type=str)  # backwardly compatible
    parent = request.args.get('parent', default='*', type=str)
    georank = request.args.get('georank', default='*', type=str)
    limit, offset, log, ndjson, startTime = setGlobals(request)
    if down(request): return ""

    query = session.query(Geolocale)
    if id != '*':
        query = query.filter(Geolocale.id == id)
    if name != '*':
        query = query.filter(Geolocale.name == name)
    if parent != '*':
        query = query.filter(Geolocale.parent == parent)
    if georank != '*':
        query = query.filter(Geolocale.georank == georank)
    query = query.filter(Geolocale.isValid == 1)
    # limit and offset were ignored here (every geolocale was returned).
    query = query.order_by(Geolocale.id).limit(limit).offset(offset)

    try:
        data = query.all()
    except OperationalError as error:
        return apiError("geolocales", error)
    except Exception as error:
        return apiError("geolocales", error)

    # if (isDevMode):
    #  print("query:" + str(query))

    geolocaleCount = 0
    dataList = []
    geolocaleDict = {}
    geolocales = []
    for geolocale in data:
        geolocaleCount += 1
        geolocaleDict = {
            'geolocaleId': geolocale.id
            , 'geolocaleName': geolocale.name
            , 'georank': geolocale.georank
            , 'parent': geolocale.parent
            , 'region': geolocale.region
            , 'subregion': geolocale.subregion
            , 'country': geolocale.country
            , 'bioregion': geolocale.bioregion
            , 'subfamilyCount': geolocale.subfamilyCount
            , 'genusCount': geolocale.genusCount
            , 'speciesCount': geolocale.speciesCount
            , 'specimenCount': geolocale.specimenCount
            , 'imageCount': geolocale.imageCount
            , 'imagedSpecimenCount': geolocale.imagedSpecimenCount
            # , 'isValid': geolocale.isValid
            , 'endemicSpeciesCount': geolocale.endemicSpeciesCount
            , 'introducedSpeciesCount': geolocale.introducedSpeciesCount
            , 'isoCode': geolocale.isoCode
            , 'iso3Code': geolocale.iso3Code
        }
        geolocales.append(geolocaleDict)

        # print(geolocaleDict)

        dataList.append(geolocaleDict)

    params = [request.args]
    metaDataDict = {'parameters': params, 'request': str(request), 'limit': limit, 'offset': offset,
                    'geolocaleCount': geolocaleCount}

    if ndjson != 'true':
        return jsonify(metaData=metaDataDict, geolocales=dataList)  # return flask Response
    else:
        return getNdjson(dataList)  # return flask Response


# --- Geolocale Taxon -------------------------------------------------------------------------------------

class GeolocaleTaxon(Base):
    __tablename__ = 'geolocale_taxon'

    geolocaleId = Column('geolocale_id', Integer, ForeignKey('geolocale.id'), primary_key=True)
    taxonName = Column('taxon_name', String, ForeignKey('taxon.taxon_name'), primary_key=True)
    created = Column(DateTime)
    source = Column(String)
    subfamilyCount = Column('subfamily_count', Integer)
    genusCount = Column('genus_count', Integer)
    speciesCount = Column('species_count', Integer)
    specimenCount = Column('specimen_count', Integer)
    imageCount = Column('image_count', Integer)
    isIntroduced = Column('is_introduced', Boolean)
    isEndemic = Column('is_endemic', Boolean)

    # geolocale = relationship("Geolocale", back_populates="geolocaleTaxa")
    geolocale = relationship("Geolocale")
    taxon = relationship("Taxon", backref="geolocaleTaxon")

    def __repr__(self):
        return "<GeolocaleTaxa( \
           geolocaleId='%s', taxonName='%s', created='%s', source='%s' \
         , subfamilyCount ='%s', genusCount='%s', speciesCount='%s', specimenCount='%s', imageCount='%s' \
         , isIntroduced='%s', isEndemic='%s' \
       )>" % (
            self.geolocaleId, self.taxonName, self.created, self.source
            , self.subfamilyCount, self.genusCount, self.speciesCount, self.specimenCount, self.imageCount
            , self.isIntroduced, self.isEndemic \
            )


@application.route('/geolocaleTaxa', methods=['GET'])
def getGeolocaleTaxa():
    geolocaleId = request.args.get('geolocaleId', default='*', type=str)
    geolocaleName = request.args.get('geolocaleName', default='*', type=str)
    georank = request.args.get('georank', default='*', type=str)
    region = request.args.get('region', default='*', type=str)
    subregion = request.args.get('subregion', default='*', type=str)
    country = request.args.get('country', default='*', type=str)
    island = request.args.get('island', default='*', type=str)
    adm1 = request.args.get('adm1', default='*', type=str)
    taxonName = request.args.get('taxonName', default='*', type=str)
    taxarank = request.args.get('taxarank', default='*', type=str)
    subfamily = request.args.get('subfamily', default='*', type=str)
    genus = request.args.get('genus', default='*', type=str)
    species = request.args.get('species', default='*', type=str)
    status = request.args.get('status', default='*', type=str)
    limit, offset, log, ndjson, startTime = setGlobals(request)
    if down(request): return ""

    query = session.query(GeolocaleTaxon)
    query = query.join(Geolocale).join(Taxon)
    # Geolocale criteria
    if geolocaleId != '*':
        query = query.filter(GeolocaleTaxon.id == geolocaleId)
    if geolocaleName != '*':
        query = query.filter(Geolocale.name == geolocaleName)
    if georank != '*':
        query = query.filter(Geolocale.georank == georank)
    if region != '*':
        query = query.filter(Geolocale.region == region)
    if subregion != '*':
        query = query.filter(Geolocale.georank == 'subregion')
        query = query.filter(Geolocale.name == subregion)
    if country != '*':
        query = query.filter(Geolocale.georank == 'country')
        query = query.filter(Geolocale.name == country)
    if island != '*':
        query = query.filter(Geolocale.georank == 'country')
        query = query.filter(Geolocale.name == island)
    if adm1 != '*':
        if adm1 == "Hawaii":
            query = query.filter(Geolocale.georank == 'country')
            query = query.filter(Geolocale.name == "Hawaii")
        else:
            query = query.filter(Geolocale.georank == 'adm1')
            query = query.filter(Geolocale.name == adm1)
    # Taxon criteria
    if taxonName != '*':
        query = query.filter(GeolocaleTaxon.taxonName == taxonName)
    if taxarank != '*':
        if taxarank == 'species':
            # query = query.filter(Taxon.taxarank == 'species' or Taxon.taxarank == 'subspecies')
            query = query.filter(or_(Taxon.taxarank == 'species', Taxon.taxarank == 'subspecies'))
        else:
            query = query.filter(Taxon.taxarank == taxarank)
    if subfamily != '*':
        query = query.filter(Taxon.subfamily == subfamily)
    if genus != '*':
        query = query.filter(Taxon.genus == genus)
    if species != '*':
        query = query.filter(Taxon.species == species)
    if status != '*':
        query = query.filter(Taxon.status == status)

    # print('id:' + id)

    # A fixed order (the geolocale_taxon key) so paging with offset neither repeats nor skips (F8).
    query = query.order_by(GeolocaleTaxon.geolocaleId, GeolocaleTaxon.taxonName)
    query = query.limit(limit)
    query = query.offset(offset)

    try:
        data = query.all()
    except OperationalError as error:
        return apiError("geolocaleTaxa", error)
    except Exception as error:
        return apiError("geolocaleTaxa", error)

    if isDevMode:
        print("isDevMode:" + str(isDevMode) + " query:" + str(query))

    geolocaleTaxaCount = 0
    dataList = []
    geolocaleTaxaDict = {}
    geolocaleTaxa = []
    for geolocaleTaxon in data:
        geolocaleTaxaCount += 1
        geolocaleTaxaDict = {
            'geolocaleId': geolocaleTaxon.geolocaleId
            , 'geolocaleName': geolocaleTaxon.geolocale.name
            , 'region': geolocaleTaxon.geolocale.region
            , 'subregion': geolocaleTaxon.geolocale.subregion
            , 'country': geolocaleTaxon.geolocale.country
            , 'bioregion': geolocaleTaxon.geolocale.bioregion
            , 'georank': geolocaleTaxon.geolocale.georank

            , 'taxonName': geolocaleTaxon.taxonName
            , 'status': geolocaleTaxon.taxon.status
            , 'taxarank': geolocaleTaxon.taxon.taxarank
            , 'subfamily': initCap(geolocaleTaxon.taxon.subfamily)
            , 'genus': initCap(geolocaleTaxon.taxon.genus)
            , 'species': geolocaleTaxon.taxon.species
            , 'subspecies': geolocaleTaxon.taxon.subspecies
            , 'source': geolocaleTaxon.taxon.source

            , 'subfamilyCount': geolocaleTaxon.subfamilyCount
            , 'genusCount': geolocaleTaxon.genusCount
            , 'speciesCount': geolocaleTaxon.speciesCount
            , 'specimenCount': geolocaleTaxon.specimenCount
            , 'imageCount': geolocaleTaxon.imageCount
        }
        geolocaleTaxa.append(geolocaleTaxaDict)

        if isDevMode and geolocaleTaxaCount == 1:
            # print("name:" + geolocaleTaxon.geolocale.name)
            # print(geolocaleTaxaDict)
            print("geolocaleTaxa geolocaleName:" + str(geolocaleTaxon.geolocale) + " repr:" + str(geolocaleTaxon))

        dataList.append(geolocaleTaxaDict)

    params = [request.args]
    metaDataDict = {'parameters': params, 'request': str(request), 'geolocaleCount': geolocaleTaxaCount}

    if ndjson != 'true':
        return returnJsonify("geolocaleTaxa",
                             jsonify(metaData=metaDataDict, geolocaleTaxa=dataList))  # return flask Response
    else:
        return getNdjson(dataList)  # return flask Response


# --- Bioregion API -------------------------------------------------------------------------------------

class Bioregion(Base):
    __tablename__ = 'bioregion'

    name = Column(String, primary_key=True)
    description = Column(String)
    subfamilyCount = Column('subfamily_count', Integer)
    genusCount = Column('genus_count', Integer)
    speciesCount = Column('species_count', Integer)
    specimenCount = Column('specimen_count', Integer)
    imageCount = Column('image_count', Integer)
    imagedSpecimenCount = Column('imaged_specimen_count', Integer)

    def __repr__(self):
        return "<Bioregions( \
           name='%s', description='%s' \
         , subfamily_count='%s', genus_count='%s', species_count='%s' \
         , specimen_count='%s', image_count='%s', imaged_specimen_count='%s' \
       )>" % (
            self.name, self.description
            , self.subfamilyCount, self.genusCount, self.speciesCount
            , self.specimenCount, self.imageCount, self.imagedSpecimenCount
            )


@application.route('/bioregions', methods=['GET'])
def getBioregions():
    name = request.args.get('bioregionName', default='*', type=str)
    limit, offset, log, ndjson, startTime = setGlobals(request)
    if down(request): return ""

    query = session.query(Bioregion)
    if name != '*':
        query = query.filter(Bioregion.name == name)
    # limit and offset were ignored here (every bioregion was returned).
    query = query.order_by(Bioregion.name).limit(limit).offset(offset)

    try:
        data = query.all()
    except OperationalError as error:
        return apiError("bioregions", error)
    except Exception as error:
        return apiError("bioregions", error)

    if isDevMode:
        print(query)

    bioregionCount = 0
    dataList = []
    bioregionDict = {}
    bioregions = []
    for bioregion in data:
        bioregionCount += 1
        bioregionDict = {
            'bioregionName': bioregion.name
            , 'description': bioregion.description
            , 'subfamilyCount': bioregion.subfamilyCount
            , 'genusCount': bioregion.genusCount
            , 'speciesCount': bioregion.speciesCount
            , 'specimenCount': bioregion.specimenCount
            , 'imageCount': bioregion.imageCount
            , 'imagedSpecimenCount': bioregion.imagedSpecimenCount
        }
        bioregions.append(bioregionDict)

        # print(bioregionDict)

        dataList.append(bioregionDict)

    params = [request.args]
    metaDataDict = {'parameters': params, 'request': str(request), 'limit': limit, 'offset': offset,
                    'bioregionCount': bioregionCount}

    if ndjson != 'true':
        return jsonify(metaData=metaDataDict, bioregions=dataList)  # return flask Response
    else:
        return getNdjson(dataList)  # return flask Response


# --- Bioregion Taxon API -------------------------------------------------------------------------------------    

class BioregionTaxon(Base):
    __tablename__ = 'bioregion_taxon'

    bioregionName = Column('bioregion_name', Integer, ForeignKey('bioregion.name'), primary_key=True)
    taxonName = Column('taxon_name', String, ForeignKey('taxon.taxon_name'), primary_key=True)
    created = Column(DateTime)
    subfamilyCount = Column('subfamily_count', Integer)
    genusCount = Column('genus_count', Integer)
    speciesCount = Column('species_count', Integer)
    specimenCount = Column('specimen_count', Integer)
    imageCount = Column('image_count', Integer)

    bioregion = relationship("Bioregion")
    taxon = relationship("Taxon")

    def __repr__(self):
        return "<BioregionTaxa( \
           bioregionName='%s', taxonName='%s', created='%s' \
         , subfamilyCount ='%s', genusCount='%s', speciesCount='%s', specimenCount='%s', imageCount='%s' \
       )>" % (
            self.bioregionName, self.taxonName, self.created
            , self.subfamilyCount, self.genusCount, self.speciesCount, self.specimenCount, self.imageCount \
            )


@application.route('/bioregionTaxa', methods=['GET'])
def getBioregionTaxa():
    bioregionName = request.args.get('bioregionName', default='*', type=str)
    taxonName = request.args.get('taxonName', default='*', type=str)
    taxarank = request.args.get('taxarank', default='*', type=str)
    subfamily = request.args.get('subfamily', default='*', type=str)
    genus = request.args.get('genus', default='*', type=str)
    species = request.args.get('species', default='*', type=str)
    status = request.args.get('status', default='*', type=str)
    limit, offset, log, ndjson, startTime = setGlobals(request)
    if down(request): return ""

    query = session.query(BioregionTaxon)
    query = query.join(Bioregion).join(Taxon)
    # Bioregion criteria
    if bioregionName != '*':
        query = query.filter(Bioregion.name == bioregionName)
    # Taxon criteria
    if taxonName != '*':
        query = query.filter(BioregionTaxon.taxonName == taxonName)
    if taxarank != '*':
        if taxarank == 'species':
            # query = query.filter(Taxon.taxarank == 'species' or Taxon.taxarank == 'subspecies')
            query = query.filter(or_(Taxon.taxarank == 'species', Taxon.taxarank == 'subspecies'))
        else:
            query = query.filter(Taxon.taxarank == taxarank)
    if subfamily != '*':
        query = query.filter(Taxon.subfamily == subfamily)
    if genus != '*':
        query = query.filter(Taxon.genus == genus)
    if species != '*':
        query = query.filter(Taxon.species == species)
    if status != '*':
        query = query.filter(Taxon.status == status)
    # A fixed order (the bioregion_taxon key) so paging with offset neither repeats nor skips (F8).
    query = query.order_by(BioregionTaxon.bioregionName, BioregionTaxon.taxonName)
    query = query.limit(limit)
    query = query.offset(offset)

    try:
        data = query.all()
    except OperationalError as error:
        return apiError("bioregionTaxa", error)
    except Exception as error:
        return apiError("bioregionTaxa", error)

    if isDevMode:
        print("isDevMode:" + str(isDevMode) + " query:" + str(query))

    bioregionTaxaCount = 0
    dataList = []
    bioregionTaxa = []
    for bioregionTaxon in data:
        bioregionTaxaCount += 1
        bioregionTaxaDict = {
            'bioregionName': bioregionTaxon.bioregion.name
            , 'taxonName': bioregionTaxon.taxonName
            , 'status': bioregionTaxon.taxon.status
            , 'taxarank': bioregionTaxon.taxon.taxarank
            , 'subfamily': initCap(bioregionTaxon.taxon.subfamily)
            , 'genus': initCap(bioregionTaxon.taxon.genus)
            , 'species': bioregionTaxon.taxon.species
            , 'subspecies': bioregionTaxon.taxon.subspecies
            , 'source': bioregionTaxon.taxon.source

            , 'subfamilyCount': bioregionTaxon.subfamilyCount
            , 'genusCount': bioregionTaxon.genusCount
            , 'speciesCount': bioregionTaxon.speciesCount
            , 'specimenCount': bioregionTaxon.specimenCount
            , 'imageCount': bioregionTaxon.imageCount
        }
        bioregionTaxa.append(bioregionTaxaDict)

        if isDevMode and bioregionTaxaCount == 1:
            print("bioregionTaxa bioregionName:" + str(bioregionTaxon.bioregion) + " repr:" + str(bioregionTaxon))

        dataList.append(bioregionTaxaDict)

    params = [request.args]
    metaDataDict = {'parameters': params, 'request': str(request), 'bioregionCount': bioregionTaxaCount}

    if ndjson != 'true':
        return jsonify(metaData=metaDataDict, bioregionTaxa=dataList)  # return flask Response
    else:
        return getNdjson(dataList)  # return flask Response


# --- Unimaged Geolocale Taxa API -------------------------------------------------------------------------------------    

def imagedTaxaSql(casteClause=""):
    """Names (taxon_name) that are imaged: a specimen of the name has an image of the ant, that is an image
    whose shot type is not 'l' (label). Used by /unimagedGeolocaleTaxa (2026-10-21; before: a specimen with
    image_count > 1, so a name whose specimens had one image each was "unimaged", and label images counted).
    casteClause: "" or " and s.caste = '...'" (specimen alias s)."""
    return ("select s.taxon_name from specimen s join image i on i.image_of_id = s.code"
            " where lower(trim(coalesce(i.shot_type, ''))) <> 'l'" + casteClause)


@application.route('/unimagedGeolocaleTaxa', methods=['GET'])
def getUnimagedGeolocaleTaxa():
    geolocaleName = request.args.get('geolocaleName', default='*', type=str)
    byCaste = request.args.get('byCaste', default='*', type=str)
    # caste = request.args.get('caste', default = '*',  type=str)
    taxarank = request.args.get('taxarank', default='*', type=str)
    status = request.args.get('status', default='*', type=str)
    limit, offset, log, ndjson, startTime = setGlobals(request)
    if down(request): return ""

    geolocale = getGeolocale(geolocaleName)
    if geolocale is None:
        return "Must enter valid geolocale"
    # georank = geolocale.georank
    # geoClause = georank + " = '" + geolocaleName + "'"

    if byCaste != '*':
        if byCaste == 'true': byCaste = 1
        if byCaste == 'false': byCaste = 0

    rankClause = " and t.taxarank in ('species', 'subspecies')"
    if taxarank != '*':
        rankClause = " and t.taxarank = :taxarank"

    statusClause = ""
    if status != '*':
        statusClause = " and t.status = :status"

    casteFieldW = ""
    casteFieldQ = ""
    casteFieldM = ""
    casteClauseW = ""
    casteClauseQ = ""
    casteClauseM = ""
    if byCaste == '1':
        casteFieldW = ", 'worker' as caste"
        casteFieldQ = ", 'queen' as caste"
        casteFieldM = ", 'male' as caste"
        casteClauseW = " and s.caste = 'worker'"
        casteClauseQ = " and s.caste = 'queen'"
        casteClauseM = " and s.caste = 'male'"

    # query = ""

    query = f"select gt.taxon_name {casteFieldW} from geolocale_taxon gt, taxon t where gt.taxon_name = t.taxon_name" \
            f" {rankClause}{statusClause} and geolocale_id = {str(geolocale.id)}" \
            f" and gt.taxon_name not in ({imagedTaxaSql(casteClauseW)})"

    if byCaste == '1':

        query += f" union select gt.taxon_name {casteFieldQ} from geolocale_taxon gt, taxon t" \
                 f" where gt.taxon_name = t.taxon_name {rankClause}{statusClause}" \
                 f" and geolocale_id = {str(geolocale.id)}" \
                 f" and gt.taxon_name not in ({imagedTaxaSql(casteClauseQ)})"

        query += f" union select gt.taxon_name {casteFieldM} from geolocale_taxon gt, taxon t" \
                 f" where gt.taxon_name = t.taxon_name {rankClause}{statusClause}" \
                 f" and geolocale_id = {str(geolocale.id)}" \
                 f" and gt.taxon_name not in ({imagedTaxaSql(casteClauseM)})" \
                 f" order by taxon_name, caste desc"

    if int(limit) > 0:
        query += " limit " + str(int(limit)) + " offset " + str(int(offset))

    # Note: Without &byCaste=1 the number of taxa returned could be greater because the is_worker, is_queen, is_male are not computed (by scheduler).

    if isDevMode:
        print("query:" + query)

    connection = engine.connect()
    # taxarank and status come from the URL: pass them as bound parameters, never pasted into the SQL.
    result = connection.execute(text(query), {"taxarank": taxarank, "status": status})

    unimagedGeolocaleTaxaCount = 0
    unimagedGeolocaleTaxaCasteCount = 0

    dataList = []
    unimagedGeolocaleTaxaDict = {}
    lastTaxonName = ""
    casteStr = ""
    thisTaxonName = ""
    casteCount = 0
    someData = False
    for row in result:
        someData = True
        if byCaste == '1':
            thisTaxonName = row['taxon_name']
            thisCaste = row['caste']

            isSame = (thisTaxonName == lastTaxonName)
            isFirst = (lastTaxonName == "")
            # print("taxonName:" + thisTaxonName + " caste:" + thisCaste + " lastTaxonName:" + lastTaxonName + " same:" + str(isSame) + " first:" + str(isFirst))

            if not isSame:
                if not isFirst:
                    # Add the last one
                    unimagedGeolocaleTaxaDict = {
                        'taxonName': lastTaxonName
                        , 'castes': casteStr
                    }
                    dataList.append(unimagedGeolocaleTaxaDict)
                    unimagedGeolocaleTaxaCount += 1
                casteStr = thisCaste
                casteCount = 0
            else:
                casteStr = casteStr + ", " + thisCaste
                casteCount = casteCount + 1

            unimagedGeolocaleTaxaCasteCount += 1
            lastTaxonName = thisTaxonName

        else:  # byCaste is not '1'
            unimagedGeolocaleTaxaCount += 1
            unimagedGeolocaleTaxaDict = {
                'taxonName': row['taxon_name']
            }
            dataList.append(unimagedGeolocaleTaxaDict)

    # Add the last one.
    if someData and byCaste == '1':
        unimagedGeolocaleTaxaDict = {
            'taxonName': thisTaxonName
            , 'castes': casteStr
        }
        dataList.append(unimagedGeolocaleTaxaDict)
        unimagedGeolocaleTaxaCount += 1

    connection.close()

    params = [request.args]
    metaDataDict = {'parameters': params, 'request': str(request),
                    'unimagedGeolocaleTaxaCount': unimagedGeolocaleTaxaCount,
                    'unimagedGeolocaleTaxaCasteCount': unimagedGeolocaleTaxaCasteCount}

    if ndjson != 'true':
        return jsonify(metaData=metaDataDict, unimagedGeolocaleTaxaDict=dataList)  # return flask Response
    else:
        return getNdjson(dataList)  # return flask Response


# --- Parameter check (every endpoint) ---------------------------------------------------------------
# A parameter the endpoint does not know used to be ignored, so a misspelt filter returned the
# first 10,000 records of everything (for example /specimens?code=... instead of specimenCode).
# Now such a request gets HTTP 400 with the list of accepted parameter names and, when an
# accepted name is close, a "did you mean" hint. The accepted names themselves are unchanged.
#
# The accepted names of an endpoint are the request.args.get('name') calls in its function (read
# from the source once at start), plus the settings every endpoint reads (setGlobals, down).

import ast
import difflib
import inspect
import textwrap

COMMON_PARAMS = ('up', 'limit', 'offset', 'ndjson', 'log')

UNCHECKED_ENDPOINTS = ('hello', 'static')


def readParams(function):
    """The names in request.args.get('name', ...) calls inside a view function."""
    tree = ast.parse(textwrap.dedent(inspect.getsource(function)))
    names = []
    for node in ast.walk(tree):
        if (isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) and node.func.attr == 'get'
                and isinstance(node.func.value, ast.Attribute) and node.func.value.attr == 'args'
                and node.args and isinstance(node.args[0], ast.Constant) and isinstance(node.args[0].value, str)):
            if node.args[0].value not in names:
                names.append(node.args[0].value)
    return names


ACCEPTED_PARAMS = {}


def acceptedParams(endpoint):
    if endpoint not in ACCEPTED_PARAMS:
        own = readParams(application.view_functions[endpoint])
        ACCEPTED_PARAMS[endpoint] = own + [p for p in COMMON_PARAMS if p not in own]
    return ACCEPTED_PARAMS[endpoint]


def suggestParams(name, accepted):
    """Accepted names close to an unknown one: same name in other case, a name that ends with it
    (code -> specimenCode), or a near spelling."""
    lower = name.lower()
    byLower = {a.lower(): a for a in accepted}
    found = []
    if lower in byLower:
        found.append(byLower[lower])
    if len(lower) >= 3:
        found += [a for a in accepted if a.lower() != lower and a.lower().endswith(lower)]
    found += [byLower[m] for m in difflib.get_close_matches(lower, list(byLower), n=3, cutoff=0.75)]
    out = []
    for a in found:
        if a not in out:
            out.append(a)
    return out[:3]


@application.before_request
def checkParams():
    endpoint = request.endpoint
    if endpoint is None or endpoint in UNCHECKED_ENDPOINTS or endpoint not in application.view_functions:
        return None
    accepted = acceptedParams(endpoint)
    unknown = [k for k in request.args.keys() if k and k not in accepted]
    if not unknown:
        return None
    message = "Unknown parameter: " + ", ".join(unknown) + "."
    didYouMean = {}
    for k in unknown:
        close = suggestParams(k, accepted)
        if close:
            didYouMean[k] = close
            message += " Did you mean " + " or ".join(close) + " (for " + k + ")?"
    body = {'error': message, 'unknownParameters': unknown, 'acceptedParameters': accepted,
            'documentation': prodServer + "/" + apiUri}
    if didYouMean:
        body['didYouMean'] = didYouMean
    return jsonify(body), 400


if __name__ == "__main__":
    application.run()
