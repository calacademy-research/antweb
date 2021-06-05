
#! /usr/bin/python3
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

#import os
#assert os.path.exists('/var/www/html/apiV3/api_db.conf')

import configparser  
from flask_sqlalchemy import SQLAlchemy
from flask import Flask, jsonify, json, request, Response
from flask_restful import Api

from sqlalchemy import create_engine, or_
from sqlalchemy import Column, Integer, String, Boolean, DateTime, ForeignKey, Numeric
from sqlalchemy.orm import sessionmaker, relationship

from sqlalchemy.exc import OperationalError

from sqlalchemy.ext.declarative import declarative_base

#from home.taxon import Taxon
#from home.geolocale import Geolocale
#from home.geolocaleTaxon import GeolocaleTaxon

#from home.taxaImage import TaxaImage
#from home.image import Image
#from home.specimen import Specimen


dbUrl = 'mysql+pymysql://antweb:f0rm1c6@mysql:3306/ant?autocommit=true'
#dbUrl = 'mysql+mysqldb://antweb:f0rm1c6@localhost:3306/ant?autocommit=true'
engine = create_engine(dbUrl, pool_recycle=280)

'''
connection = engine.connect()
result = connection.execute("select occurrenceId from darwin_core_2 limit 20")
for row in result:
    print("occurrenceId:", row['occurrenceId'])
connection.close()
'''

Base = declarative_base()

Session = sessionmaker(bind=engine)
session = Session()

#import MySQLdb as mysqldb

import time

from datetime import datetime, timedelta

application = Flask(__name__)
#app.run(port=80)
api = Api(application)

isDevMode = 0
 
# MySQL configurations  
try:
    #apiDbConf = '/var/www/html/apiV3/api_db.conf'                                                                                                                                                                        
    #apiDbConf = '/antweb/deploy/api/v3/api_db.conf'
    apiDbConf = 'api_db.conf'  

    import os
    assert os.path.exists(apiDbConf)

    # Read config file
    config = configparser.ConfigParser()  
    #config.read('api_db.conf')
    #config.read('/var/www/html/apiV3/api_db.conf')
    config.read(apiDbConf)

    #print(dbUrl)
except Exception as e :
    pass
    #print('Exception e: ' + str(e),' reading configuration file')

#app = Flask(__name__)
application.config['SQLALCHEMY_DATABASE_URI'] = dbUrl
application.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = True

#application.config['JSON_AS_ASCII'] = False

db = SQLAlchemy(application)
    
version = "V3.1"    
localPhpServer = "http://localhost:5000"
localTomcatServer = "http://localhost/antweb"
apiServer = "http://api.antweb.org"
prodServer = "https://www.antweb.org"
apiUri = "api" + version + ".do"

globalMessage = "Either the server is overloaded or there is an error in the application."

log = False

import sys
    
if (sys.platform == "darwin"):
  isDevMode = 1
  print("isDevMode:" + str(isDevMode))
       
print("server started. devMode:" + str(isDevMode) + " platform:" + sys.platform)
       
@application.route("/")
def hello():  
    message = "Antweb API " + version \
      + "<br><br><a href='" + localTomcatServer + "/" + apiUri + "'>" + version + " Local API Documentation</a>" \
      + "<br><br><a href='" + prodServer + "/" + apiUri + "'>" + version + " Antweb API Documentation</a>"
      #+ "<br><br>Application:" + application.name
    return message 


# - Various Methods, etc.. ---------------------------------------------------------------

class HaltException(Exception):
    pass
    
def initCap(x):
    val = x
    try:
      val = x[:1].upper() + x[1:]
    except TypeError as error:
      pass #print("error:" + str(error))
    return val
    
def down(request):
    # This code brings the server down. Only accessible if &up=1 is appended to each api call.
    up = request.args.get('up', default = '*', type=str)
    if (up != '1'):
      print("Down")
      #raise HaltException("We decided we want to halt the script")  
      #userExit()
      #exit()
      return 1
    return 0

def setGlobals(request):
    print("request:" + str(request))
        
    global limit, offset, log, ndjson, startTime
    startTime = time.time()    
    limit = request.args.get('limit', default = '10000', type = int)
    offset = request.args.get('offset', default = '0', type = int)
    log = request.args.get('log', default = '*', type=str)
    ndjson = request.args.get('ndjson', default = '*', type=str)    
    
def getNdjson(dataList):
    empty = ''.encode('utf8')
    jsonResponse = []
    i = 0;
    lineEnd = '\n'.encode('utf8')
    #print('log:' + str(log))
    for datum in dataList:
      if (log == 'true'):
        if (i > 0 and i % 10000 == 0):
            pass
            #print('log i:' + str(i))
      #data = '{"index" : {"_index":"specimens","_id":' + str(i) + '} }'
      header = {
        "index": {
          "_index": "specimens",
          "_id": i
        }
      }

      jsonResponse.extend(json.dumps(header, sort_keys=False).encode('utf8') + lineEnd + json.dumps(datum, indent=None, ensure_ascii=False).encode('utf8') + lineEnd)
      #print("response:" + str(jsonResponse))
      i = i + 1
      #print("ndjson jsonResponse:" + jsonResponse)
    #jsonO = b''.join(jsonResponse)
    response = Response(bytearray(jsonResponse), content_type="application/json; charset=utf-8" )
    #print('Finished jdson request. length:' + str(len(dataList)) + ' bytes:' + str(len(jsonResponse)))
    return response
    
def returnJsonify(method, returnVal):
    if (returnVal is None):
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
    decimalLatitude = Column(Numeric) # Could be Float or Numeric
    decimalLongitude = Column(Numeric) # was: double
    latLonMaxError = Column(String)
    dateDetermined = Column(DateTime) # date
    dateCollectedStart = Column(DateTime)
    dateCollectedEnd = Column(DateTime)
    habitat = Column(String) #text
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
    #dateCollected = Column(String)
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

    #isMale = Column('is_male', Boolean)
    #isWorker = Column('is_worker', Boolean)
    #isQueen = Column('isQueen', Boolean)
    
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
             self.occurrenceId, self.ownerInstitutionCode \
           , self.basisOfRecord, self.institutionCode, self.collectionCode \
           , self.code, self.dctermsModified, self.nomenclaturalCode \
           , self.kingdom, self.phylum, self.classVal, self.order \
           , self.family, self.subfamily, self.genus, self.subgenus \
           , self.species, self.subspecies, self.scientific_name \
           , self.higherClassification, self.typeStatus \
           , self.region, self.subregion, self.country, self.adm1 \
           , self.bioregion, self.decimalLatitude, self.decimalLongitude \
           , self.latLonMaxError, self.dateDetermined, self.dateCollectedStart, self.dateCollectedEnd \
           , self.habitat, self.microhabitat \
           , self.collectedBy, self.samplingMethod, self.lifeStageSex \
           , self.medium, self.collectionCode, self.determinedBy \
           , self.localityName, self.localityNotes, self.specimenNotes \
           , self.collectionNotes \
           , self.minimumElevationInMeters, self.bioregion, self.museum, self.taxonName \
           , self.ownedBy, self.locatedAt, self.collectedBy \
           , self.caste, self.subcaste, self.fossil, self.status, self.imageCount
       #    , self.isMale, self.isWorker, self.isQueen
        )

    def getSpecDict(sp):
    
        coordinates = { 
          'lat': sp.decimalLatitude
        , 'lon': sp.decimalLongitude
        }
        if (not sp.decimalLatitude and not sp.decimalLongitude):
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
        , 'genus':  initCap(sp.genus)
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
        , 'localityName': sp.localityNotes
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
    if (geolocale is None):
      return query
    georank = geolocale.georank
    if (georank == "region"):
        query = query.filter(Specimen.region.contains(geolocaleName))
    if (georank == "subregion"):
        query = query.filter(Specimen.subregion.contains(geolocaleName))
    if (georank == "country"):
        query = query.filter(Specimen.country.contains(geolocaleName))
    if (georank == "adm1"):
        query = query.filter(Specimen.adm1.contains(geolocaleName))
    return query
    
@application.route('/specimens', methods=['GET'])
def getSpecimen():
    # Process parameters
    family = request.args.get('family', default = '*', type=str)
    subfamily = request.args.get('subfamily', default = '*', type=str)
    genus = request.args.get('genus', default = '*', type=str)
    species = request.args.get('species', default = '*', type=str)
    code = request.args.get('specimenCode', default = '*', type=str)
    region = request.args.get('region', default = '*', type=str)
    subregion = request.args.get('subregion', default = '*', type=str)
    country = request.args.get('country', default = '*', type=str)
    adm1 = request.args.get('adm1', default = '*', type=str)
    bioregion = request.args.get('bioregion', default = '*', type=str)
    habitat = request.args.get('habitat', default = '*', type=str)
    type = request.args.get('type', default='*', type=str)
    georeferenced = request.args.get('georeferenced', default='*', type=str)
    bbox = request.args.get('bbox', default = '*', type=str)
    minDate = request.args.get('minDate', default = '*', type=str)
    maxDate = request.args.get('maxDate', default = '*', type=str)   
    minElevation = request.args.get('minElevation', default = '*', type=str) 
    maxElevation = request.args.get('maxElevation', default = '*', type=str) 
    museum = request.args.get('museum', default = '*', type=str)
    ownedby = request.args.get('ownedby', default = '*', type=str)    
    locatedat = request.args.get('locatedat', default = '*', type=str)
    collectedby = request.args.get('collectedby', default = '*', type=str)
    caste = request.args.get('caste', default = '*',  type=str)
    subcaste = request.args.get('subcaste', default = '*',  type=str)
    fossil = request.args.get('fossil', default = '*',  type=str)
    validGenus = request.args.get('validGenus', default='*', type=str)
    validSubfamily = request.args.get('validSubfamily', default='*', type=str)
    status = request.args.get('status', default = '*', type=str)
    hasImage = request.args.get('hasImage', default='*', type=str)
    geolocaleName = request.args.get('geolocaleName', default='*', type=str)
    lifeStageSex = request.args.get('lifeStageSex', default='*', type=str)
    setGlobals(request)
    if (down(request)): return ""
      
      
    # Build query criteria
    query = session.query(Specimen)
    if (family != '*') :
      query = query.filter(Specimen.family == family)
    if (subfamily != '*') :
      query = query.filter(Specimen.subfamily == subfamily)
    if (genus != '*') :
      query = query.filter(Specimen.genus == genus)
    if (species != '*') :
      query = query.filter(Specimen.species == species)
    if (code != '*') :
      #print("code:" + code)
      query = query.filter(Specimen.code == code)
    if (region != '*') :
      query = query.filter(Specimen.region.contains(region))
    if (subregion != '*') :
      query = query.filter(Specimen.subregion.contains(subregion))
    if (country != '*') :
      query = query.filter(Specimen.country.contains(country))
    if (adm1 != '*') :
      query = query.filter(Specimen.adm1.contains(adm1))
    if (bioregion != '*') :
      query = query.filter(Specimen.bioregion.contains(bioregion))
    if (habitat != '*') :
      query = query.filter(Specimen.habitat.contains(habitat))
    if (type != '*') :
      query = query.filter(Specimen.typeStatus == type)
    if (georeferenced == 'true'):
      query = query.filter(Specimen.decimalLatitude != None)
      query = query.filter(Specimen.decimalLongitude != None)    
    if (bbox != '*') :
      coords = bbox.split(",")
      query = query.filter(Specimen.decimalLatitude <= coords[0], Specimen.decimalLatitude >= coords[2]) 
      query = query.filter(Specimen.decimalLongitude <= coords[1], Specimen.decimalLongitude >= coords[3])
    if (maxDate != '*') :
      query = query.filter(Specimen.dateCollectedStart < maxDate)
    if (minDate != '*') :
      query = query.filter(Specimen.dateCollectedStart > minDate)
    if (minElevation != '*') :
      query = query.filter(Specimen.minimumElevationInMeters > minElevation)
    if (maxElevation != '*') :
      query = query.filter(Specimen.minimumElevationInMeters < maxElevation)   
    if (museum != '*') :
      query = query.filter(Specimen.museum == museum)
    if (ownedby != '*') :
      query = query.filter(Specimen.ownedby == ownedby)
    if (locatedat != '*') :
      query = query.filter(Specimen.locatedat == locatedat)
    if (collectedby != '*') :
      query = query.filter(Specimen.collectedby == collectedby)
    if (caste != '*') :
      query = query.filter(Specimen.caste == caste)
    if (subcaste != '*') :
      query = query.filter(Specimen.subcaste == subcaste)
    if (fossil != '*') :
      if (fossil == 'true'): fossil = 1
      if (fossil == 'false'): fossil = 0
      if (fossil == 1 or fossil == 0 or fossil == '1' or fossil == '0'):
        query = query.filter(Specimen.fossil == fossil)
    if (status != '*') :
      query = query.filter(Specimen.status == status)
    if (hasImage != '*'):
      if (hasImage == 'true'):
        query = query.filter(Specimen.imageCount > 0)
      if (hasImage == 'false'):
        query = query.filter(Specimen.imageCount <= 0)
    if (geolocaleName != '*'):
        query = query.join(Taxon).join(GeolocaleTaxon).join(Geolocale).filter(Geolocale.name == geolocaleName)   
        query = addExtraGeolocaleListCriteria(query, geolocaleName)
                     
    if (lifeStageSex != '*'):
      query = query.filter(Specimen.lifeStageSex.like("%" + lifeStageSex + "%"))   

    query = query.limit(limit)
    query = query.offset(offset)

    if (log == 'true'):
      print(" query:" + str(query))

    
    try:
        data = query.all()
    except OperationalError as error:
        message = "specimens operational error:" + str(error) + " on request:" + str(request)
        print(message)
        return message    
    except:
        message = "specimens error request:" + str(request)
        print(message) #  + error.orig.message, error.params
        return message
        
    if (log == 'true'):
        print('Query complete')        

    dataList = []
    skipSubfamily = 0
    skipGenus = 0
    for sp in data:
      skip = 0;
      if (validSubfamily == 'true'):
        skip = sp.subfamily not in ('agroecomyrmecinae', 'amblyoponinae', 'aneuretinae', 'apomyrminae', 'dolichoderinae', 'dorylinae', 'ectatomminae', 'formicinae', 'hetero', 'leptanillinae', 'martialinae', 'myrmeciinae', 'myrmicinae', 'paraponerinae', 'ponerinae', 'proceratiinae', 'pseudomyrmecinae')
        if (skip):
          skipSubfamily = skipSubfamily + 1
          print("skipping code:" + sp.code + " because in invalid (or fossil) subfamily:" + sp.subfamily)
      if (not skip and validGenus == 'true'):
        # This commented out set would be irregardless of fossil.
        #skip = sp.genus not in ('Acanthognathus','Acanthomyrmex','Acanthoponera','Acanthostichus','Acromyrmex','Acropyga','Adelomyrmex','Adetomyrma','Adlerzia','Aenictogiton','Aenictus','Afromyrma','Afropone','Agastomyrma','Agraulomyrmex','Agroecomyrmex','Alloformica','Alloiomma','Allomerus','Amblyopone','Ancyridris','Aneuretellus','Aneuretus','Anillidris','Anillomyrma','Ankylomyrma','Anochetus','Anomalomyrma','Anonychomyrma','Anoplolepis','Aphaenogaster','Aphomomyrmex','Apomyrma','Apterostigma','Aptinoma','Archaeopone','Archimyrmex','Archiponera','Aretidris','Armania','Armaniella','Arnoldius','Asphinctopone','Asymphylomyrmex','Atopomyrmex','Atta','Attaichnus','Attopsis','Aulacopone','Austromorium','Austroponera','Avitomyrmex','Axinidris','Azteca','Baikuris','Bajcaridris','Baracidris','Bariamyrma','Basiceros','Belonopelta','Biamomyrma','Bilobomyrma','Blepharidatta','Boloponera','Boltonidris','Boltonimecia','Bondroitia','Bothriomyrmex','Bothroponera','Brachymyrmex','Brachyponera','Brachytarsites','Bradoponera','Bregmatomyrma','Britaneuretus','Brownimecia','Buniapone','Burmomyrma','Calomyrmex','Calyptites','Calyptomyrmex','Camelomecia','Camponotites','Camponotus','Cananeuretus','Canapone','Cardiocondyla','Carebara','Casaleia','Cataglyphis','Cataglyphoides','Cataulacus','Centromyrmex','Cephalomyrmex','Cephalopone','Cephalotes','Cerapachys','Ceratomyrmex','Cheliomyrmex','Chimaeridris','Chimaeromyrma','Chronomyrmex','Chronoxenus','Chrysapace','Cladomyrma','Clavipetiola','Colobopsis','Colobostruma','Conoformica','Crematogaster','Cretomyrma','Cretopone','Cryptomyrmex','Cryptopone','Ctenobethylus','Curticorna','Curtipalpulus','Cyatta','Cylindromyrmex','Cyphoidris','Cyphomyrmex','Cyrtopone','Dacatria','Dacetinops','Daceton','Diacamma','Diaphoromyrma','Dicroaspis','Dilobocondyla','Dinomyrmex','Dinoponera','Diplomorium','Discothyrea','Dlusskyidris','Doleromyrma','Dolichoderus','Dolichomyrma','Dolioponera','Dolopomyrmex','Dorylus','Dorymyrmex','Drymomyrmex','Eburopone','Echinopla','Eciton','Ecphorella','Ectatomma','Ectomomyrmex','Elaeomyrmex','Elaphrodites','Eldermyrmex','Electromyrmex','Electroponera','Emeryopone','Emplastus','Enneamerus','Eoaenictites','Eocenidris','Eocenomyrma','Eoformica','Eogorgites','Eoleptocerites','Eomyrmex','Eoponerites','Eotapinoma','Epelysidris','Epopostruma','Erromyrma','Eulithomyrmex','Euponera','Euprenolepis','Eurhopalothrix','Eurymyrmex','Eurytarsites','Eusphinctus','Eutetramorium','Fallomyrma','Feroponera','Fisheropone','Fonsecahymen','Forelius','Formica','Formicoxenus','Formosimyrma','Froggattella','Fulakora','Furcisutura','Fushuniformica','Fushunomyrmex','Gaoligongidris','Gauromyrmex','Gerontoformica','Gesomyrmex','Gigantiops','Glaphyromyrmex','Gnamptogenys','Goniomma','Gracilidris','Hagensia','Haidomyrmex','Haidomyrmodes','Haidoterminus','Harpagoxenus','Harpegnathos','Heeridris','Heteroponera','Huaxiaformica','Huberia','Hylomyrma','Hypopomyrmex','Hypoponera','Iberoformica','Ilemomyrmex','Imhoffia','Indomyrma','Iridomyrmex','Iroponera','Ishakidris','Kalathomyrmex','Kartidris','Kempfidris','Khetania','Klondikia','Kohlsimyrma','Kotshkorkia','Ktunaxia','Kyromyrma','Labidus','Lachnomyrmex','Lasiomyrma','Lasiophanes','Lasius','Lenomyrmex','Lepisiota','Leptanilla','Leptanilloides','Leptogasteritus','Leptogenys','Leptomyrmex','Leptomyrmula','Leptothorax','Leucotaphus','Liaoformica','Linepithema','Linguamyrmex','Liometopum','Liomyrmex','Lioponera','Lividopone','Loboponera','Lonchomyrmex','Longicapitia','Longiformica','Lophomyrmex','Lordomyrma','Loweriella','Macabeemyrma','Magnogasterites','Malagidris','Manica','Martialis','Mayaponera','Mayriella','Megalomyrmex','Megaponera','Melissotarsus','Melophorus','Meranoplus','Mesoponera','Mesostruma','Messelepone','Messor','Metapone','Mianeuretus','Microdaceton','Miomyrmex','Miosolenopsis','Monomorium','Myanmyrma','Mycetagroicus','Mycetarotes','Mycetophylax','Mycetosoritis','Mycocepurus','Myopias','Myopopone','Myrcidris','Myrmecia','Myrmecina','Myrmecites','Myrmecocystus','Myrmecorhynchus','Myrmelachista','Myrmica','Myrmicaria','Myrmicocrypta','Myrmoteras','Mystrium','Nebothriomyrmex','Neivamyrmex','Neocerapachys','Neoponera','Nesomyrmex','Nomamyrmex','Noonilla','Nothomyrmecia','Notoncus','Notostigma','Novomessor','Nylanderia','Ochetellus','Ochetomyrmex','Octostruma','Ocymyrmex','Odontomachus','Odontoponera','Oecophylla','Onychomyrmex','Ooceraea','Opamyrma','Ophthalmopone','Opisthopsis','Orapia','Orbicapitia','Orbigastrula','Orectognathus','Ovalicapito','Ovaligastrula','Overbeckia','Oxyepoecus','Oxyidris','Oxyopomyrmex','Pachycondyla','Paltothyreus','Papyrius','Parameranoplus','Paramycetophylax','Paraneuretus','Paraparatrechina','Paraphaenogaster','Paraponera','Parasyscia','Paratopula','Paratrechina','Parvaponera','Patagonomyrmex','Perissomyrmex','Peronomyrmex','Petalomyrmex','Petraeomyrmex','Petropone','Phalacromyrmex','Phaulomyrma','Pheidole','Philidris','Phrynoponera','Pilotrochus','Pityomyrmex','Plagiolepis','Platythyrea','Plectroctena','Plesiomyrmex','Podomyrma','Poecilomyrma','Pogonomyrmex','Polyergus','Polyrhachis','Ponera','Ponerites','Poneropterus','Prenolepis','Prionomyrmex','Prionopelta','Pristomyrmex','Proatta','Probolomyrmex','Procerapachys','Proceratium','Procryptocerus','Prodimorphomyrmex','Proformica','Proiridomyrmex','Prolasius','Promyopias','Propodilobus','Protalaridris','Protaneuretus','Protanilla','Protazteca','Protoformica','Protomyrmica','Protopone','Protrechina','Psalidomyrmex','Pseudarmania','Pseudectatomma','Pseudoatta','Pseudocamponotus','Pseudolasius','Pseudomyrmex','Pseudoneoponera','Pseudonotoncus','Pseudoponera','Quadrulicapito','Quineangulicapito','Rasopone','Ravavy','Recurvidris','Rhopalomastix','Rhopalothrix','Rhytidoponera','Rogeria','Romblonella','Rossomyrmex','Rostromyrmex','Rotastruma','Royidris','Santschiella','Scyphodon','Secostruma','Sericomyrmex','Sicilomyrmex','Simopelta','Simopone','Sinoformica','Sinomyrmex','Sinotenuicapito','Solenopsis','Solenopsites','Sphaerogasterites','Sphecomyrma','Sphinctomyrmex','Stegomyrmex','Stenamma','Stereomyrmex','Stigmacros','Stigmatomma','Stigmomyrmex','Stiphromyrmex','Streblognathus','Strongylognathus','Strumigenys','Syllophopsis','Syscia','Talaridris','Tanipone','Taphopone','Tapinolepis','Tapinoma','Tatuidris','Technomyrmex','Temnothorax','Terataner','Teratomyrmex','Tetheamyrma','Tetramorium','Tetraponera','Thaumatomyrmex','Titanomyrma','Trachymyrmex','Tranopelta','Trichomyrmex','Tropidomyrmex','Turneria','Typhlomyrmex','Tyrannomyrmex','Usomyrma','Veromessor','Vicinopone','Vitsika','Vollenhovia','Vombisidris','Wasmannia','Wilsonia','Wumyrmex','Xenomyrmex','Xymmer','Yantaromyrmex','Yavnella','Ypresiomyrma','Yunodorylus','Zasphinctus','Zatania','Zhangidris','Zherichinius','Zigrasimecia')
        skip = sp.genus not in ('Ankylomyrma','Tatuidris','Adetomyrma','Amblyopone','Fulakora','Myopopone','Mystrium','Onychomyrmex','Prionopelta','Stigmatomma','Xymmer','Aneuretus','Apomyrma','Anillidris','Anonychomyrma','Aptinoma','Arnoldius','Axinidris','Azteca','Bothriomyrmex','Chronoxenus','Doleromyrma','Dolichoderus','Dorymyrmex','Ecphorella','Forelius','Froggattella','Gracilidris','iridomyrmex','Leptomyrmex','Linepithema','Liometopum','Loweriella','Nebothriomyrmex','Ochetellus','Papyrius','Philidris','Ravavy','Tapinoma','Technomyrmex','Turneria','Acanthostichus','Aenictogiton','Aenictus','Cerapachys','Cheliomyrmex','Chrysapace','Cylindromyrmex','Dorylus','Eburopone','Eciton','Eusphinctus','Labidus','Leptanilloides','Lioponera','Lividopone','Neivamyrmex','Neocerapachys','Nomamyrmex','Ooceraea','Parasyscia','Simopone','Sphinctomyrmex','Syscia','Tanipone','Vicinopone','Yunodorylus','Zasphinctus','Ectatomma','Gnamptogenys','Rhytidoponera','Typhlomyrmex','Acropyga','Agraulomyrmex','Alloformica','Anoplolepis','Aphomomyrmex','Bajcaridris','Brachymyrmex','Bregmatomyrma','Calomyrmex','Camponotus','Cataglyphis','Cladomyrma','Colobopsis','Dinomyrmex','Echinopla','Euprenolepis','Formica','Gesomyrmex','Gigantiops','iberoformica','Lasiophanes','Lasius','Lepisiota','Melophorus','Myrmecocystus','Myrmecorhynchus','Myrmelachista','Myrmoteras','Notoncus','Notostigma','Nylanderia','Oecophylla','Opisthopsis','Overbeckia','Paraparatrechina','Paratrechina','Petalomyrmex','Plagiolepis','Polyergus','Polyrhachis','Prenolepis','Proformica','Prolasius','Pseudolasius','Pseudonotoncus','Rossomyrmex','Santschiella','Stigmacros','Tapinolepis','Teratomyrmex','Zatania','Acanthoponera','Aulacopone','Heteroponera','Anomalomyrma','Leptanilla','Noonilla','Opamyrma','Phaulomyrma','Protanilla','Scyphodon','Yavnella','Martialis','Myrmecia','Nothomyrmecia','Acanthognathus','Acanthomyrmex','Acromyrmex','Adelomyrmex','Adlerzia','Allomerus','Ancyridris','Anillomyrma','Aphaenogaster','Apterostigma','Aretidris','Atopomyrmex','Atta','Austromorium','Baracidris','Bariamyrma','Basiceros','Blepharidatta','Bondroitia','Calyptomyrmex','Cardiocondyla','Carebara','Cataulacus','Cephalotes','Chimaeridris','Colobostruma','Crematogaster','Cryptomyrmex','Cyatta','Cyphoidris','Cyphomyrmex','Dacatria','Dacetinops','Daceton','Diaphoromyrma','Dicroaspis','Dilobocondyla','Diplomorium','Dolopomyrmex','Epelysidris','Epopostruma','Erromyrma','Eurhopalothrix','Eutetramorium','Formicoxenus','Formosimyrma','Gaoligongidris','Gauromyrmex','Goniomma','Harpagoxenus','Huberia','Hylomyrma','indomyrma','ishakidris','Kalathomyrmex','Kartidris','Kempfidris','Lachnomyrmex','Lasiomyrma','Lenomyrmex','Leptothorax','Liomyrmex','Lophomyrmex','Lordomyrma','Malagidris','Manica','Mayriella','Megalomyrmex','Melissotarsus','Meranoplus','Mesostruma','Messor','Metapone','Microdaceton','Monomorium','Mycetagroicus','Mycetarotes','Mycetophylax','Mycetosoritis','Mycocepurus','Myrmecina','Myrmica','Myrmicaria','Myrmicocrypta','Nesomyrmex','Novomessor','Ochetomyrmex','Octostruma','Ocymyrmex','Orectognathus','Oxyepoecus','Oxyopomyrmex','Paramycetophylax','Paratopula','Patagonomyrmex','Perissomyrmex','Peronomyrmex','Phalacromyrmex','Pheidole','Pilotrochus','Podomyrma','Poecilomyrma','Pogonomyrmex','Pristomyrmex','Proatta','Procryptocerus','Propodilobus','Protalaridris','Pseudoatta','Recurvidris','Rhopalomastix','Rhopalothrix','Rogeria','Romblonella','Rostromyrmex','Rotastruma','Royidris','Secostruma','Sericomyrmex','Solenopsis','Stegomyrmex','Stenamma','Stereomyrmex','Strongylognathus','Strumigenys','Syllophopsis','Talaridris','Temnothorax','Terataner','Tetheamyrma','Tetramorium','Trachymyrmex','Tranopelta','Trichomyrmex','Tropidomyrmex','Tyrannomyrmex','Veromessor','Vitsika','Vollenhovia','Vombisidris','Wasmannia','Xenomyrmex','Paraponera','Anochetus','Asphinctopone','Austroponera','Belonopelta','Boloponera','Bothroponera','Brachyponera','Buniapone','Centromyrmex','Cryptopone','Diacamma','Dinoponera','Dolioponera','Ectomomyrmex','Emeryopone','Euponera','Feroponera','Fisheropone','Hagensia','Harpegnathos','Hypoponera','iroponera','Leptogenys','Loboponera','Mayaponera','Megaponera','Mesoponera','Myopias','Neoponera','Odontomachus','Odontoponera','Ophthalmopone','Pachycondyla','Paltothyreus','Parvaponera','Phrynoponera','Platythyrea','Plectroctena','Ponera','Promyopias','Psalidomyrmex','Pseudoneoponera','Pseudoponera','Rasopone','Simopelta','Streblognathus','Thaumatomyrmex','Discothyrea','Probolomyrmex','Proceratium','Myrcidris','Pseudomyrmex','Tetraponera')
        if (skip):
          skipGenus = skipGenus + 1
          #print("skipping code:" + sp.code + " because in invalid (or fossil) genus:" + sp.genus)
                
      if (not skip):    
        specDict = Specimen.getSpecDict(sp)

        #if (specDict['specimenCode'] == 'antweb1038039'):
        #  print("ownedBy:" + specDict['ownedBy'])  # Would appear (only on server) in terminal as SMNG, G\xc3\xb6rlitz, Germany
        dataList.append(specDict)

    if (log == 'true'):
      print(' skipSubfamil:' + str(skipSubfamily) + ' skipGenus:' + str(skipGenus))

    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['request'] = str(request)
    metaDataDict['limit'] = limit
    metaDataDict['offset'] = offset
    metaDataDict['count'] = len(dataList)
    metaDataDict['queryTime'] = time.strftime("%H:%M:%S", time.gmtime(time.time() - startTime))

    if (ndjson != 'true'):
      return jsonify(metaData=metaDataDict, specimens=dataList) # return flask Response
    else:
      return getNdjson(dataList) # return flask Response

# --- Geo Specimens API ------------------------------------------------------------------------------------

# http://localhost:5000/geoSpecimens?coords=37,%20-122&limit=8000&radius=2
@application.route('/geoSpecimens', methods=['GET'])
def getGeoSpecimens():
    coords = request.args.get('coords', default = '*', type=str) #(?coord=37, -122) is cal academy
    if (coords == '*'):
      return 'Must enter coords for a geoSpecimens request'
    radius = request.args.get('radius', default = '5', type = int)  # in kilometers
    distinct = request.args.get('distinct', default = '*', type=str)
    setGlobals(request)
    if (down(request)): return ""
    
    query = session.query(Specimen)
    coordsArray = coords.split(",")
    query = query.filter(Specimen.decimalLatitude <= (int(coordsArray[0]) + int(radius)), Specimen.decimalLatitude >= (int(coordsArray[0]) - int(radius))) 
    query = query.filter(Specimen.decimalLongitude <= (int(coordsArray[1]) + int(radius)), Specimen.decimalLongitude >= (int(coordsArray[1]) - int(radius))) 

    if (distinct != '*'):
      if (distinct == 'subfamily'):
        query = session.query(Specimen.subfamily).distinct()   # was db.session...
      if (distinct == 'genus'):
        query = session.query(Specimen.genus).distinct()     # was db.session...

      query = query.limit(limit)
      query = query.offset(offset)

      try:
          data = query.all()
      except OperationalError as error:        
        message = "geoSpecimens operational error:" + str(error) + " on request:" + str(request)
        print(message)
        return message
      except:
        message = "geoSpecimens error request:" + str(request)
        print(message)  
        return message  
        
      dataList = []
      for sp in data:
        if (distinct == 'subfamily'):
          dataList.append(sp.subfamily)
        #  specDict = { 'subfamily': sp.subfamily }
        if (distinct == 'genus'):
          dataList.append(sp.genus)
        #  specDict = { 'genus': sp.genus }
        #dataList.append(specDict)
    
      params = []
      params.append(request.args)    
      metaDataDict = {}
      metaDataDict['parameters'] = params
      metaDataDict['request'] = str(request)      
      metaDataDict['limit'] = limit
      metaDataDict['count'] = len(dataList)
    
      if (distinct == 'subfamily'):
        return jsonify(metaData=metaDataDict, subfamilies=dataList)    
      if (distinct == 'genus'):
        return jsonify(metaData=metaDataDict, genera=dataList)    
      return jsonify(_metaData=metaDataDict, specimens=dataList)

   # if not distinct. Show everything.
    query = query.limit(limit)
    query = query.offset(offset)
    
    try:
        data = query.all()
    except OperationalError as error:        
        message = "geospecimens operational error:" + str(error) + " on request:" + str(request)
        print(message)
        return message    
    except:
        message = "geoSpecimens 2 error:" + str(request)
        print(message) # + error.orig.message, error.params)
        return message
            
    dataList = []
    for sp in data:
        specDict = Specimen.getSpecDict(sp)

        dataList.append(specDict)

    #print(query)

    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['request'] = str(request)
    metaDataDict['limit'] = limit
    metaDataDict['count'] = len(dataList)

    if (ndjson != 'true'):
      return jsonify(metaData=metaDataDict, specimens=dataList) # return flask Response
    else:
      return getNdjson(dataList) # return flask Response    

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
    setGlobals(request)
    if (down(request)): return ""
           
    # add introduced and endemics

    extraClause = ""
    geolocale = getGeolocale(geolocaleName)
    if (geolocale is None):
       return "Geolocale not found"
    georank = geolocale.georank
    extraClause = " s." + georank + " = '" + geolocaleName + "'"

    query = "select s.taxon_name, s.genus, s.species " \
      + " , count(case when caste='queen' then 1 end)" \
      + " , count(case when subcaste='ergatoid' then 1 end)" \
      + " , count(case when subcaste='alate/dealate' then 1 end)" \
      + " , count(case when subcaste='brachypterous' then 1 end)" \
      + " , count(case when caste='worker' then 1 end)" \
      + " , count(case when subcaste='major/soldier' then 1 end)" \
      + " , count(case when subcaste='normal' then 1 end)" \
      + " , count(case when caste='male' then 1 end)" \
      + " , count(case when subcaste='ergatoid' then 1 end)" \
      + " , count(case when subcaste='alate' then 1 end)" \
      + " from specimen s, taxon t, geolocale_taxon gt, geolocale g" \
      + " where s.taxon_name = t.taxon_name and gt.taxon_name = t.taxon_name " \
      + "   and gt.geolocale_id = g.id and " \
      + extraClause \
      + " and g.name = '" + geolocaleName + "'" \
      + "  and s.status = 'valid'" \
      + " group by s.taxon_name, s.genus, s.species"

    #print(query)
    dataList = []
    result = session.execute(query)
    for row in result:    
        geoSpeciesStats = getGeoSpeciesStatsDict(row)
        dataList.append(geoSpeciesStats)
	
    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['request'] = str(request)
    metaDataDict['count'] = len(dataList)

    if (ndjson != 'true'):
      return jsonify(metaData=metaDataDict, geoSpeciesStats=dataList) # return flask Response
    else:
      return getNdjson(dataList) # return flask Response 
     
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
           self.taxonName, self.taxarank, self.subfamily, self.genus \
         , self.species, self.subspecies , self.parent \
         , self.fossil, self.source, self.created \
         , self.family, self.kingdomName, self.phylumName \
         , self.className, self.orderName, self.antcat, self.subfamilyCount \
         , self.genusCount, self.speciesCount, self.subspeciesCount, self.imageCount \
         , self.parentTaxonName, self.type, self.antcatId, self.authorDate \
         , self.authors, self.year, self.status, self.currentValidName, self.bioregion \
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
    #parent = request.args.get('parentTaxonName', default='*', type=str) # Could do parentTaxonName but not parent.
    status = request.args.get('status', default='*', type=str)
    setGlobals(request)
    if (down(request)): return ""
        
    query = session.query(Taxon)
    if (taxonName != '*'):
      query = query.filter(Taxon.taxonName == taxonName)
    if (taxarank != '*'):
      query = query.filter(Taxon.taxarank == taxarank)
    if (subfamily != '*'):
      query = query.filter(Taxon.subfamily == subfamily)
    if (genus != '*'):
      query = query.filter(Taxon.genus == genus)
    if (species != '*'):
      query = query.filter(Taxon.species == species)
    if (subspecies != '*'):
      query = query.filter(Taxon.subspecies == subspecies)
    #if (parent != '*'):
    #  query = query.filter(Taxon.parent == parent)
    if (status != '*'):
      query = query.filter(Taxon.status == status)

    try:
        data = query.all()
    except OperationalError as error:        
        message = "taxa operational error:" + str(error) + " on request:" + str(request)
        print(message)
        return message
    except:
        message = "taxa error:" + str(request)
        print(message) # + error.orig.message, error.params
        return
        
    #print(query)
    
    if (log == 'true'):
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
        #, 'accessGroup': taxon.accessGroup  #causes: TypeError: Object of type 'VisitableType' is not JSON serializable
        }
        taxa.append(taxonDict)

        #print(taxonDict)
        
        dataList.append(taxonDict)
     
    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['request'] = str(request)
    metaDataDict['taxonCount'] = taxonCount
    
    if (ndjson != 'true'):
      return jsonify(metaData=metaDataDict, taxa=dataList) # return flask Response
    else:
      return getNdjson(dataList) # return flask Response


# --- Distinct Taxon API ------------------------------------------------------------------------------------

@application.route('/distinctTaxa', methods=['GET'])
def getDistinctTaxa():
    taxarank = request.args.get('taxarank', default = '*', type=str)
    country = request.args.get('country', default = '*', type=str)
    habitat = request.args.get('habitat', default = '*', type=str)
    minDate = request.args.get('minDate', default = '*', type=str)
    maxDate = request.args.get('maxDate', default = '*', type=str)   
    minElevation = request.args.get('minElevation', default = '*', type=str) 
    maxElevation = request.args.get('maxElevation', default = '*', type=str) 
    status = request.args.get('status', default = '*', type=str)            
    setGlobals(request)
    if (down(request)): return ""
    
    query = session.query(Specimen)
 
    if ('subfamily' == taxarank):
      query = session.query(Specimen.subfamily).distinct()
    if ('genus' == taxarank):
      query = session.query(Specimen.genus).distinct()
    if ('species' == taxarank):
      query = session.query(Specimen.species).distinct()

    # if any of these are used, performance suffers!    
    if (country != '*') :
      query = query.filter(Specimen.country.contains(country))
    if (habitat != '*') :
      query = query.filter(Specimen.habitat.contains(habitat))
    if (maxDate != '*') :
      query = query.filter(Specimen.dateCollectedStart < maxDate)
    if (minDate != '*') :
      query = query.filter(Specimen.dateCollectedStart > minDate)
    if (minElevation != '*') :
      query = query.filter(Specimen.minimumElevationInMeters > minElevation)
    if (maxElevation != '*') :
      query = query.filter(Specimen.minimumElevationInMeters < maxElevation)    
    if (status != '*') :
      query = query.filter(Specimen.status == status)

    query = query.limit(limit)
    query = query.offset(offset)

    try:
        data = query.all()
    except OperationalError as error:        
        message = "distinctTaxa operational error:" + str(error) + " on request:" + str(request)
        print(message)
        return message    
    except:
        message = "distinctTaxa error:" + str(request)
        print(message) # + error.orig.message, error.params
        return
 
    if (log == 'true'):
        print("log query:" + str(query))

    dataList = []
    for taxa in data:
      if ('subfamily' == taxarank):
        dataList.append(taxa.subfamily)
      if ('genus' == taxarank):
        dataList.append(taxa.genus)
        #print("taxarank:" + taxarank + " genus:" + str(taxa.genus))
      if ('species' == taxarank):
        dataList.append(taxa.species)

    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['request'] = str(request)
    metaDataDict['limit'] = limit
    metaDataDict['count'] = len(dataList)
        
    if (isDevMode):
        print("devMode query:" + str(query))
        
    if ('subfamily' == taxarank):
      if (ndjson != 'true'):
        return jsonify(metaData=metaDataDict, subfamilies=dataList) # return flask Response
      else:
        return getNdjson(dataList) # return flask Response
    if ('genus' == taxarank):
      if (ndjson != 'true'):
        return jsonify(metaData=metaDataDict, genera=dataList) # return flask Response
      else:
        return getNdjson(dataList) # return flask Response

    if (ndjson != 'true'):
      return jsonify(metaData=metaDataDict, taxarank=dataList) # return flask Response
    else:
      return getNdjson(dataList) # return flask Response


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
    
@application.route('/images', methods=['GET'])
def getImages():
    since = request.args.get('since', default='*', type=str)
    shotType = request.args.get('shotType', default='*', type=str)
    code = request.args.get('specimenCode', default='*', type=str)
    setGlobals(request)
    if (down(request)): return ""
        
    query = session.query(Image)
    if (since != '*'):
      day_interval_before = datetime.now() - timedelta(days=int(since))  
      query = query.filter(Image.uploadDate >= day_interval_before)
    if (shotType != '*'):
      query = query.filter(Image.shotType == shotType)
    if (code != '*'):
      query = query.filter(Image.code == code)
    query = query.limit(limit)
    query = query.offset(offset)    

    try:
        data = query.all()
    except OperationalError as error:        
        message = "images operational error:" + str(error) + " on request:" + str(request)
        print(message)
        return message    
    except:
        message = "images error:" + str(request)
        print(message) # + error.orig.message, error.params
        return
    
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
        #print('code' + image.code + ' base:' + base)
        urls = [base + '_low.jpg' \
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
        if (tCode != image.code):
            if (tCode != ''): # we have one to insert. (Is not the first record).
              specimenDict = {
                  'spefimenCode': tCode
                , 'url': 'http://localhost:5000/specimens?code=' + tCode
                , 'images': images
              }
              specimenCount += 1
              dataList.append(specimenDict)          
            
            images = []
            images.append(imageDict)
            tCode = image.code
            
        else:
            images.append(imageDict)
        
    # Handle the last instance of the record (due to group by procedural logic). 
    if (tCode != ''):   
      specimenDict = {
          'specimenCode': tCode
        , 'url': 'http://localhost:5000/specimens?code=' + tCode
        , 'images': images
      }
              
    specimenCount += 1
    dataList.append(specimenDict)
     
    # SELECT id,shot_type,upload_date,shot_number,has_tiff FROM image WHERE image_of_id=? ORDER BY shot_number ASC     
    if (isDevMode):
        print(query)
    
    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['request'] = str(request)
    metaDataDict['limit'] = limit
    metaDataDict['specimenCount'] = specimenCount
    metaDataDict['count'] = imageCount
    
    if (ndjson != 'true'):
      return jsonify(metaData=metaDataDict, images=dataList) # return flask Response
    else:
      return getNdjson(dataList) # return flask Response


# --- Taxa Images API -------------------------------------------------------------------------------------

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
         self.taxonName, self.subfamily, self.genus, self.species, self.subspecies, self.code, \
         self.uid, self.shotType, self.code, self.uploadDate, self.shotNumber, self.hasTiff
    )

    # Rather like a static methods...

    def getTaxaDict(data):
        taxaDict = {}
        for taxaImage in data:
    
            # taxaImage is the object created from the result set.
            #taxaDict is the dictionary containing the hierarchical data taxon - specimen - image.

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
              #print('getTaxaDict() + taxonName:' + taxaImage.taxonName)
            else:
              taxonDict = taxaDict[taxaImage.taxonName]
            #print('getTaxaDict() taxonName:' + taxaImage.taxonName)
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
              urls = [base + '_low.jpg' \
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
              #print('getTaxaDict() uid:' + str(taxaImage.uid))
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
            #print("imageCount len:" + str(len(specimenImages)))
            imageCount = imageCount + len(specimenImages)
        return imageCount

    def getDataList(taxaDict):
        dataList = []
        for taxonDict in taxaDict:
          #print("taxonDict: " + str(taxaDict[taxonDict]))
          taxonDict = taxaDict[taxonDict]
          #print("taxonName: " + taxonDict['taxonName'])
          dataList.append(taxonDict) 
        return dataList

@application.route('/taxaImages', methods=['GET'])
def getTaxaImages():
    taxonName = request.args.get('taxonName', default='*', type=str)
    subfamily = request.args.get('subfamily', default='*', type=str)
    genus = request.args.get('genus', default='*', type=str)
    species = request.args.get('species', default='*', type=str)
    subspecies = request.args.get('subspecies', default='*', type=str)
    code = request.args.get('code', default='*', type=str)
    imageId = request.args.get('imageId', default='*', type=str)
    #uploadDate = request.args.get('uploadDate', default='*', type=str)
    shotType = request.args.get('shotType', default='*', type=str)
    setGlobals(request)
    if (down(request)): return ""
    
    query = session.query(TaxaImage)
    if (taxonName != '*'):
      query = query.filter(TaxaImage.taxonName == taxonName)
    if (subfamily != '*'):
      query = query.filter(TaxaImage.subfamily == subfamily)
    if (genus != '*'):
      query = query.filter(TaxaImage.genus == genus)
    if (species != '*'):
      query = query.filter(TaxaImage.species == species)
    if (subspecies != '*'):
      query = query.filter(TaxaImage.subspecies == subspecies)
    if (code != '*'):
      query = query.filter(TaxaImage.code == code)
    if (imageId != '*'):
      query = query.filter(TaxaImage.uid == imageId)
    #if (uploadDate != '*'):
    #  query = query.filter(TaxaImage.uploadDate like uploadDate)
    if (shotType != '*'):
      query = query.filter(TaxaImage.shotType == shotType)
    query.order_by(TaxaImage.taxonName, TaxaImage.code) 
    query = query.limit(limit)
    query = query.offset(offset)

    try:
        data = query.all()
    except OperationalError as error:        
        message = "taxaImages operational error:" + str(error) + " on request:" + str(request)
        print(message)
        return message
    except:
        message = "taxaImages error:" + str(request) + " args:" + str(sys.exc_info()[0])
        print(message)
        return message

    # Looks like this might be necessary. Unless we can upgrade Python to 3.8. May 24 2020.
    # will need to be taxaImage.getTaxaDict(data); but the method declaration will need (self,) as will the other methods.
    #taxaImage = TaxaImage()
    taxaDict = TaxaImage.getTaxaDict(data)

    taxonCount = TaxaImage.getTaxonCount(taxaDict)
    specimenCount = TaxaImage.getSpecimenCount(taxaDict)
    imageCount = TaxaImage.getImageCount(taxaDict)
    dataList = TaxaImage.getDataList(taxaDict)
    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['request'] = str(request)
    metaDataDict['limit'] = limit
    metaDataDict['taxonCount'] = taxonCount
    metaDataDict['specimenCount'] = specimenCount
    metaDataDict['imageCount'] = imageCount
    
    if (ndjson != 'true'):
      return returnJsonify("taxaImages", jsonify(metaData=metaDataDict, taxaImages=dataList)) # return flask Response
    else:
      return getNdjson(dataList) # return flask Response


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
           self.id, self.name, self.parent, self.georank \
         , self.isValid \
         , self.region, self.subregion, self.country, self.bioregion \
         , self.subfamilyCount, self.genusCount, self.speciesCount \
         , self.specimenCount, self.imageCount, self.imagedSpecimenCount \
         , self.endemicSpeciesCount, self.introducedSpeciesCount \
         , self.isoCode, self.iso3Code \
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
        print(message) # # + error.orig.message, error.params    
        return
    for geolocale in data:
      return geolocale
    return
    

@application.route('/geolocales', methods=['GET'])
def getGeolocales():
    id = request.args.get('geolocaleId', default='*', type=str)
    name = request.args.get('geolocaleName', default=None, type=str)
    if (name is None):
      name = request.args.get('name', default='*', type=str)  # backwardly compatible
    parent = request.args.get('parent', default='*', type=str)
    georank = request.args.get('georank', default='*', type=str)
    setGlobals(request)
    if (down(request)): return ""
        
    query = session.query(Geolocale)
    if (id != '*'):
      query = query.filter(Geolocale.id == id)
    if (name != '*'):
      query = query.filter(Geolocale.name == name)
    if (parent != '*'):
      query = query.filter(Geolocale.parent == parent)
    if (georank != '*'):
      query = query.filter(Geolocale.georank == georank)        
    query = query.filter(Geolocale.isValid == 1)

    try:
        data = query.all()
    except OperationalError as error:        
        message = "geolocales operational error:" + str(error) + " on request:" + str(request)
        print(message)
        return message
    except:
        message = "geolocales error:" + str(request)
        print(message) # # + error.orig.message, error.params
        return

    #if (isDevMode):
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
        #, 'isValid': geolocale.isValid
        , 'endemicSpeciesCount': geolocale.endemicSpeciesCount
        , 'introducedSpeciesCount': geolocale.introducedSpeciesCount
        , 'isoCode': geolocale.isoCode
        , 'iso3Code': geolocale.iso3Code
        }
        geolocales.append(geolocaleDict)

        #print(geolocaleDict)
        
        dataList.append(geolocaleDict)
     
    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['request'] = str(request)
    metaDataDict['geolocaleCount'] = geolocaleCount
    
    if (ndjson != 'true'):
      return jsonify(metaData=metaDataDict, geolocales=dataList) # return flask Response
    else:
      return getNdjson(dataList) # return flask Response


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
    
    #geolocale = relationship("Geolocale", back_populates="geolocaleTaxa")
    geolocale = relationship("Geolocale")
    taxon = relationship("Taxon", backref="geolocaleTaxon")

    def __repr__(self): 
       return "<GeolocaleTaxa( \
           geolocaleId='%s', taxonName='%s', created='%s', source='%s' \
         , subfamilyCount ='%s', genusCount='%s', speciesCount='%s', specimenCount='%s', imageCount='%s' \
         , isIntroduced='%s', isEndemic='%s' \
       )>" % (         
           self.geolocaleId, self.taxonName, self.created, self.source \
         , self.subfamilyCount, self.genusCount, self.speciesCount, self.specimenCount, self.imageCount \
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
    setGlobals(request)
    if (down(request)): return ""
         
    query = session.query(GeolocaleTaxon)
    query = query.join(Geolocale).join(Taxon)
    # Geolocale criteria
    if (geolocaleId != '*'):
      query = query.filter(GeolocaleTaxon.id == geolocaleId)
    if (geolocaleName != '*'):
      query = query.filter(Geolocale.name == geolocaleName)    
    if (georank != '*'):
      query = query.filter(Geolocale.georank == georank)      
    if (region != '*'):
      query = query.filter(Geolocale.region == region)
    if (subregion != '*'):
      query = query.filter(Geolocale.georank == 'subregion')      
      query = query.filter(Geolocale.name == subregion)      
    if (country != '*'):
      query = query.filter(Geolocale.georank == 'country')      
      query = query.filter(Geolocale.name == country)
    if (island != '*'):
      query = query.filter(Geolocale.georank == 'country')      
      query = query.filter(Geolocale.name == island)
    if (adm1 != '*'):
      if (adm1 == "Hawaii"):
        query = query.filter(Geolocale.georank == 'country')      
        query = query.filter(Geolocale.name == "Hawaii")    
      else:
        query = query.filter(Geolocale.georank == 'adm1')      
        query = query.filter(Geolocale.name == adm1)    
    # Taxon criteria        
    if (taxonName != '*'):
      query = query.filter(GeolocaleTaxon.taxonName == taxonName)
    if (taxarank != '*'):
      if (taxarank == 'species'):
        #query = query.filter(Taxon.taxarank == 'species' or Taxon.taxarank == 'subspecies')
        query = query.filter(or_(Taxon.taxarank == 'species', Taxon.taxarank == 'subspecies'))
      else:
        query = query.filter(Taxon.taxarank == taxarank)
    if (subfamily != '*'):
      query = query.filter(Taxon.subfamily == subfamily)
    if (genus != '*'): 
      query = query.filter(Taxon.genus == genus)
    if (species != '*'):
      query = query.filter(Taxon.species == species)
    if (status != '*'):
      query = query.filter(Taxon.status == status)    

    #print('id:' + id)

    query = query.limit(limit)
    query = query.offset(offset)  

    try:
        data = query.all()
    except OperationalError as error:        
        message = "geolocaleTaxa operational error:" + str(error) + " on request:" + str(request)
        print(message)
        return message    
    except:
        message = "geolocaleTaxa error:" + str(request)
        print(message)  #+ error.orig.message, error.params
        return message
        
    if (isDevMode):
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

        if (isDevMode and geolocaleTaxaCount == 1):
            #print("name:" + geolocaleTaxon.geolocale.name)
            #print(geolocaleTaxaDict)
            print("geolocaleTaxa geolocaleName:" + str(geolocaleTaxon.geolocale) + " repr:" + str(geolocaleTaxon))
        
        dataList.append(geolocaleTaxaDict)
     
    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['request'] = str(request)
    metaDataDict['geolocaleCount'] = geolocaleTaxaCount
    
    if (ndjson != 'true'):
      return returnJsonify("geolocaleTaxa", jsonify(metaData=metaDataDict, geolocaleTaxa=dataList)) # return flask Response
    else:
      return getNdjson(dataList) # return flask Response


# --- Bioregion API -------------------------------------------------------------------------------------

class Bioregion(Base):
    __tablename__ = 'bioregion'

    name = Column(String, primary_key=True)
    description = Column(String);
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
           self.name, self.description \
         , self.subfamilyCount, self.genusCount, self.speciesCount \
         , self.specimenCount, self.imageCount, self.imagedSpecimenCount \
       )

@application.route('/bioregions', methods=['GET'])
def getBioregions():
    name = request.args.get('bioregionName', default='*', type=str)
    setGlobals(request)
    if (down(request)): return ""
        
    query = session.query(Bioregion)
    if (name != '*'):
      query = query.filter(Bioregion.name == name)

    try:
        data = query.all()
    except OperationalError as error:        
        message = "bioregions operational error:" + str(error) + " on request:" + str(request)
        print(message)
        return message    
    except:
        print("bioregions error:" + str(request)) # + error.orig.message, error.params
        return

    if (isDevMode):
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

        #print(bioregionDict)
        
        dataList.append(bioregionDict)
     
    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['request'] = str(request)
    metaDataDict['bioregionCount'] = bioregionCount
    
    if (ndjson != 'true'):
      return jsonify(metaData=metaDataDict, bioregions=dataList) # return flask Response
    else:
      return getNdjson(dataList) # return flask Response
    
    
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
           self.bioregionName, self.taxonName, self.created \
         , self.subfamilyCount, self.genusCount, self.speciesCount, self.specimenCount, self.imageCount \
       )

           
@application.route('/bioregionTaxa', methods=['GET'])
def getBioregionTaxa():
    bioregionName = request.args.get('bioregionName', default='*', type=str)
    taxonName = request.args.get('taxonName', default='*', type=str)
    taxarank = request.args.get('taxarank', default='*', type=str)
    subfamily =request.args.get('subfamily', default='*', type=str)
    genus =request.args.get('genus', default='*', type=str)
    species =request.args.get('species', default='*', type=str)
    status = request.args.get('status', default='*', type=str)
    setGlobals(request)
    if (down(request)): return ""
        
    query = session.query(BioregionTaxon)
    query = query.join(Bioregion).join(Taxon)
    # Bioregion criteria
    if (bioregionName != '*'):
      query = query.filter(Bioregion.name == bioregionName)      
    # Taxon criteria        
    if (taxonName != '*'):
      query = query.filter(BioregionTaxon.taxonName == taxonName)
    if (taxarank != '*'):
      if (taxarank == 'species'):
        #query = query.filter(Taxon.taxarank == 'species' or Taxon.taxarank == 'subspecies')
        query = query.filter(or_(Taxon.taxarank == 'species', Taxon.taxarank == 'subspecies'))
      else:
        query = query.filter(Taxon.taxarank == taxarank)
    if (subfamily != '*'):
      query = query.filter(Taxon.subfamily == subfamily)
    if (genus != '*'): 
      query = query.filter(Taxon.genus == genus)
    if (species != '*'):
      query = query.filter(Taxon.species == species)
    if (status != '*'):
      query = query.filter(Taxon.status == status)    
    query = query.limit(limit)
    query = query.offset(offset)    

    try:
        data = query.all()
    except OperationalError as error:        
        message = "bioregionTaxa operational error:" + str(error) + " on request:" + str(request)
        print(message)
        return message    
    except:
        print("bioregionTaxa error:" + str(request)) # + error.orig.message, error.params
        return

    if (isDevMode):
        print("isDevMode:" + str(isDevMode) + " query:" + str(query))
    
    bioregionTaxaCount = 0
    dataList = []
    bioregionTaxaDict = {}
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

        if (isDevMode and bioregionTaxaCount == 1):
            print("bioregionTaxa bioregionName:" + str(bioregionTaxon.bioregion) + " repr:" + str(bioregionTaxon))
        
        dataList.append(bioregionTaxaDict)
     
    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['request'] = str(request)
    metaDataDict['bioregionCount'] = bioregionTaxaCount
    
    if (ndjson != 'true'):
      return jsonify(metaData=metaDataDict, bioregionTaxa=dataList) # return flask Response
    else:
      return getNdjson(dataList) # return flask Response


# --- Unimaged Geolocale Taxa API -------------------------------------------------------------------------------------    

@application.route('/unimagedGeolocaleTaxa', methods=['GET'])
def getUnimagedGeolocaleTaxa():
    geolocaleName = request.args.get('geolocaleName', default='*', type=str)
    byCaste = request.args.get('byCaste', default = '*',  type=str)
    #caste = request.args.get('caste', default = '*',  type=str)
    taxarank = request.args.get('taxarank', default='*', type=str)
    status = request.args.get('status', default = '*', type=str)        
    setGlobals(request)
    if (down(request)): return ""
       
    geolocale = getGeolocale(geolocaleName)
    if (geolocale is None):
      return "Must enter valid geolocale"
    #georank = geolocale.georank
    #geoClause = georank + " = '" + geolocaleName + "'" 

    if (byCaste != '*'):
      if (byCaste == 'true'): byCaste = 1
      if (byCaste == 'false'): byCaste = 0

    rankClause = " and t.taxarank in ('species', 'subspecies')"
    if (taxarank != '*'):
      rankClause = " and t.taxarank = '" + taxarank + "'"

    statusClause = ""
    if (status != '*'):
      statusClause = " and t.status = '" + status + "'"

    casteFieldW = ""
    casteFieldQ = ""
    casteFieldM = ""
    casteClauseW = ""
    casteClauseQ = ""
    casteClauseM = ""
    if (byCaste == '1'):
      casteFieldW = ", 'worker' as caste"
      casteFieldQ = ", 'queen' as caste"
      casteFieldM = ", 'male' as caste"
      casteClauseW = " and caste = 'worker'"
      casteClauseQ = " and caste = 'queen'"
      casteClauseM = " and caste = 'male'"
    
    # query = ""
    query = "select gt.taxon_name" + casteFieldW + " from geolocale_taxon gt, taxon t where gt.taxon_name = t.taxon_name " \
      + " " + rankClause + statusClause + " and geolocale_id = " + str(geolocale.id) \
      + " and gt.taxon_name not in (select taxon_name from specimen where image_count > 1 " + casteClauseW + ")"
    if (byCaste == '1'):
      query += " union select gt.taxon_name" + casteFieldQ + " from geolocale_taxon gt, taxon t where gt.taxon_name = t.taxon_name " \
        + " " + rankClause + statusClause + " and geolocale_id = " + str(geolocale.id) \
        + " and gt.taxon_name not in (select taxon_name from specimen where image_count > 1 " + casteClauseQ + ")"
      query += " union select gt.taxon_name" + casteFieldM + " from geolocale_taxon gt, taxon t where gt.taxon_name = t.taxon_name " \
        + " " + rankClause + statusClause + " and geolocale_id = " + str(geolocale.id) \
        + " and gt.taxon_name not in (select taxon_name from specimen where image_count > 1 " + casteClauseM + ")" \
        + " order by taxon_name, caste desc"
    if (int(limit) > 0):
      query += " limit " + str(limit)        
        
    # Note: Without &byCaste=1 the number of taxa returned could be greater because the is_worker, is_queen, is_male are not computed (by scheduler).

    if (isDevMode):
      print("query:" + query)

    connection = engine.connect()
    result = connection.execute(query)

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
      if (byCaste == '1'):
        thisTaxonName = row['taxon_name']
        thisCaste = row['caste']
        
        isSame = (thisTaxonName == lastTaxonName)
        isFirst = (lastTaxonName == "")
        #print("taxonName:" + thisTaxonName + " caste:" + thisCaste + " lastTaxonName:" + lastTaxonName + " same:" + str(isSame) + " first:" + str(isFirst))

        if (not isSame):
          if (not isFirst):
            #Add the last one
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
       
      else: # byCaste is not '1'
        unimagedGeolocaleTaxaCount += 1
        unimagedGeolocaleTaxaDict = {
          'taxonName': row['taxon_name']
        }            
        dataList.append(unimagedGeolocaleTaxaDict)

    # Add the last one.
    if (someData and byCaste == '1'):
      unimagedGeolocaleTaxaDict = {
    	'taxonName': thisTaxonName
      , 'castes': casteStr
      }
      dataList.append(unimagedGeolocaleTaxaDict)                  
      unimagedGeolocaleTaxaCount += 1

    connection.close()
     
    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['request'] = str(request)
    metaDataDict['unimagedGeolocaleTaxaCount'] = unimagedGeolocaleTaxaCount
    metaDataDict['unimagedGeolocaleTaxaCasteCount'] = unimagedGeolocaleTaxaCasteCount    

    if (ndjson != 'true'):
      return jsonify(metaData=metaDataDict, unimagedGeolocaleTaxaDict=dataList) # return flask Response
    else:
      return getNdjson(dataList) # return flask Response



if __name__ == "__main__":  
    application.run()
