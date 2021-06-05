#! /usr/local/bin/python3.6

# Can be execute in local environment (in /Users/mark/dev/calacademy/antweb/api/v3 directory) as such:
#     python3.6 api.py
	
# To Do.
#   Performance tuning. Prevent long runs.
#   Specimen georeferenced parameter.
# integrate recently added fields. Biogeographicregion and collection date
# Remove the classes into separate files.

#import os
#assert os.path.exists('/var/www/html/apiV3/api_db.conf')

import configparser  
from flask_sqlalchemy import SQLAlchemy
from flask import Flask, jsonify, json, request
from flask_restful import Api

from sqlalchemy import create_engine, or_
from sqlalchemy import Column, Integer, String, Boolean, DateTime, ForeignKey
from sqlalchemy.orm import sessionmaker, relationship

from sqlalchemy.ext.declarative import declarative_base

#from home.taxon import Taxon
#from home.geolocale import Geolocale
#from home.geolocaleTaxon import GeolocaleTaxon
from home.taxaImage import TaxaImage
from home.image import Image
from home.specimen import Specimen

dbUrl = 'mysql+mysqldb://antweb:f0rm1c6@mysql:3306/ant?autocommit=true'
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

import MySQLdb as mysqldb

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

    #print(config.get('DB', 'user'))
    #dbUrl = 'mysql+mysqldb://' + config.get('DB', 'user') + ':' + config.get('DB', 'password') + '@' + config.get('DB', 'host') + ":" + config.get('DB', 'port') + '/' + config.get('DB', 'db') 

    isDevMode = 1 # This will not execute deployed on server. This is how we determined devMode.   
    if (isDevMode):
      print("isDevMode:" + str(isDevMode))

except Exception as e :
    pass
    #print('Exception e: ' + str(e),' reading configuration file')
    #dbUrl = 'mysql+mysqldb://antweb:f0rm1c6@localhost:3306/ant'

#app = Flask(__name__)
application.config['SQLALCHEMY_DATABASE_URI'] = dbUrl
application.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = True

db = SQLAlchemy(application)
    
version = "V3"    
localPhpServer = "http://localhost:5000"
localTomcatServer = "http://localhost/antweb"
apiServer = "http://api.antweb.org"
prodServer = "https://www.antweb.org"
apiUri = "api" + version + ".do"
    
@application.route("/")
def hello():  
    message = "Antweb API " + version \
      + "<br><br><a href='" + localTomcatServer + "/" + apiUri + "'>" + version + " Local API Documentation</a>" \
      + "<br><br><a href='" + prodServer + "/" + apiUri + "'>" + version + " Antweb API Documentation</a>"
      #+ "<br><br>Application:" + application.name
    return message 


# ---------------------------------------------------------------------------------------

class Taxon(Base):
    __tablename__ = 'taxon'
    taxonName = Column('taxon_name', String, primary_key=True)
    rank = Column(String)
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
    typed = Column(Boolean)
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
    
    def __repr__(self):
       return "<Taxa( \
           taxonName='%s', rank='%s', subfamily='%s', genus='%s' \
         , species='%s%', subspecies='%s', parent='%s' \
         , fossil='%s', source='%s', created='%s' \
	     , family='%s', kingdomName='%s', phylumName='%s' \
         , className='%s', orderName='%s', antcat='%s', subfamilyCount ='%s' \
         , genusCount='%s', speciesCount='%s', specimenCount='%s', imageCount='%s' \
         , parentTaxonName='%s', typed='%s', antcatId='%s', authorDate='%s' \
         , authors='%s', year='%s', status='%s', currentValidName='%s', bioregion='%s' \
         , country='%s', currentValidParent='%s', lineNum='%s', accessGroup='%s' \
       )>" % (         
           self.taxonName, self.rank, self.subfamily, self.genus \
         , self.species, self.subspecies , self.parent \
         , self.fossil, self.source, self.created \
         , self.family, self.kingdomName, self.phylumName \
         , self.className, self.orderName, self.antcat, self.subfamilyCount \
         , self.genusCount, self.speciesCount, self.subspeciesCount, self.imageCount \
         , self.parentTaxonName, self.typed, self.antcatId, self.authorDate \
         , self.authors, self.year, self.status, self.currentValidName, self.bioregion \
         , self.country, self.currentValidParent, self.lineNum, self.accessGroup \
       )
    
@application.route('/taxa', methods=['GET'])
def getTaxa():
    taxonName = request.args.get('taxonName', default='*', type=str)
    rank = request.args.get('rank', default='*', type=str)
    subfamily = request.args.get('subfamily', default='*', type=str)
    genus = request.args.get('genus', default='*', type=str)
    species = request.args.get('species', default='*', type=str)
    subspecies = request.args.get('subspecies', default='*', type=str)
    parent = request.args.get('parent', default='*', type=str)
    status = request.args.get('status', default='*', type=str)

    print("v3 request:" + str(request))

    #print('id:' + id)

    query = session.query(Taxon)
    if (taxonName != '*'):
      query = query.filter(Taxon.taxonName == taxonName)
    if (rank != '*'):
      query = query.filter(Taxon.rank == rank)
    if (subfamily != '*'):
      query = query.filter(Taxon.subfamily == subfamily)
    if (genus != '*'):
      query = query.filter(Taxon.genus == genus)
    if (species != '*'):
      query = query.filter(Taxon.species == species)
    if (subspecies != '*'):
      query = query.filter(Taxon.subspecies == subspecies)
    if (parent != '*'):
      query = query.filter(Taxon.parent == parent)
    if (status != '*'):
      query = query.filter(Taxon.status == status)

    try:
        data = query.all()
    except:
        print("v3 taxa error:")
        return
    #print(query)
    
    taxonCount = 0
    dataList = []
    taxonDict = {}
    taxa = []    
    for taxon in data:
        taxonCount += 1
        taxonDict = {
          'taxonName': taxon.taxonName
        , 'rank': taxon.rank
        , 'subfamily': taxon.subfamily
        , 'genus': taxon.genus
        , 'species': taxon.species
        , 'subspecies': taxon.subspecies
        , 'parent': taxon.parent
        , 'fossil': taxon.fossil
        , 'source': taxon.source
        , 'created': taxon.created
        , 'family': taxon.family
        , 'kingdomName': taxon.kingdomName
        , 'phylumName': taxon.phylumName
        , 'className': taxon.className
        , 'orderName': taxon.orderName
        , 'antcat': taxon.antcat
        , 'subfamilyCount': taxon.subfamilyCount
        , 'genusCount': taxon.genusCount
        , 'speciesCount': taxon.speciesCount
        , 'specimenCount': taxon.specimenCount
        , 'imageCount': taxon.imageCount
        , 'parentTaxonName': taxon.parentTaxonName
        , 'typed': taxon.typed
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
    metaDataDict['taxonCount'] = taxonCount
    
    return jsonify(metaData=metaDataDict, taxa=dataList)                 

# ----------------------------------------------------------------------------------------

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
    
    def __repr__(self):
       return "<Geolocales( \
           id='%s', name='%s', parent='%s', georank='%s' \
         , isValid='%s' \
         , region='%s', subregion='%s', country='%s', bioregion='%s' \
         , subfamily_count='%s', genus_count='%s', species_count='%s' \
	     , specimen_count='%s', image_count='%s', imaged_specimen_count='%s' \
         , endemicSpeciesCount='%s', introducedSpeciesCount='%s' \
       )>" % (         
           self.id, self.name, self.parent, self.georank \
         , self.isValid \
         , self.region, self.subregion, self.country, self.bioregion \
         , self.subfamilyCount, self.genusCount, self.speciesCount \
         , self.specimenCount, self.imageCount, self.imagedSpecimenCount \
         , self.endemicSpeciesCount, self.introducedSpeciesCount \
       )


@application.route('/geolocales', methods=['GET'])
def getGeolocales():
    id = request.args.get('id', default='*', type=str)
    name = request.args.get('name', default='*', type=str)
    parent = request.args.get('parent', default='*', type=str)
    georank = request.args.get('georank', default='*', type=str)

    print("v3 request:" + str(request))        
    # print('id:' + id + " name:" + name + " parent:" + parent + " georank:" + georank)
        
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
    except:
        message = "v3 geolocales error"
        print(message)
        return message

    if (isDevMode):
        print(query)
    
    geolocaleCount = 0
    dataList = []
    geolocaleDict = {}
    geolocales = []    
    for geolocale in data:
        geolocaleCount += 1
        geolocaleDict = {
          'id': geolocale.id
        , 'name': geolocale.name
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
        }
        geolocales.append(geolocaleDict)

        #print(geolocaleDict)
        
        dataList.append(geolocaleDict)
     
    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['geolocaleCount'] = geolocaleCount
    
    return jsonify(metaData=metaDataDict, geolocales=dataList)                 

# ----------------------------------------------------------------------------------------

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
    taxon = relationship("Taxon")

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
    region =request.args.get('region', default='*', type=str)
    subregion =request.args.get('subregion', default='*', type=str)
    country =request.args.get('country', default='*', type=str)
    adm1 =request.args.get('adm1', default='*', type=str)
    taxonName = request.args.get('taxonName', default='*', type=str)
    rank = request.args.get('rank', default='*', type=str)
    subfamily =request.args.get('subfamily', default='*', type=str)
    genus =request.args.get('genus', default='*', type=str)
    species =request.args.get('species', default='*', type=str)
    status = request.args.get('status', default='*', type=str)
    limit = request.args.get('limit', default = '10000', type = int)
    offset = request.args.get('offset', default = '0', type = int)
    
    print("v3 request:" + str(request))
    #print('id:' + id)
     
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
    if (adm1 != '*'):
      query = query.filter(Geolocale.georank == 'adm1')      
      query = query.filter(Geolocale.name == adm1)    
    # Taxon criteria        
    if (taxonName != '*'):
      query = query.filter(GeolocaleTaxon.taxonName == taxonName)
    if (rank != '*'):
      if (rank == 'species'):
        #query = query.filter(Taxon.rank == 'species' or Taxon.rank == 'subspecies')
        query = query.filter(or_(Taxon.rank == 'species', Taxon.rank == 'subspecies'))
      else:
        query = query.filter(Taxon.rank == rank)
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
    except:
        print("v3 geolocaleTaxa error")
        return

    if (isDevMode):
        pass #print("isDevMode:" + str(isDevMode) + " query:" + str(query))
    
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
        , 'rank': geolocaleTaxon.taxon.rank
        , 'subfamily': geolocaleTaxon.taxon.subfamily
        , 'genus': geolocaleTaxon.taxon.genus
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
            pass #print("geolocaleTaxa geolocaleName:" + str(geolocaleTaxon.geolocale) + " repr:" + str(geolocaleTaxon))
        
        dataList.append(geolocaleTaxaDict)
     
    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['geolocaleCount'] = geolocaleTaxaCount
    
    return jsonify(metaData=metaDataDict, geolocaleTaxa=dataList)                 

# ----------------------------------------------------------------------------------------

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
    name = request.args.get('name', default='*', type=str)
    
    print("v3 request:" + str(request))        
    #print('id:' + name)
        
    query = session.query(Bioregion)
    if (name != '*'):
      query = query.filter(Bioregion.name == name)

    try:
        data = query.all()
    except:
        print("v3 bioregions error")
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
          'name': bioregion.name
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
    metaDataDict['bioregionCount'] = bioregionCount
    
    return jsonify(metaData=metaDataDict, bioregions=dataList) 
    
# ----------------------------------------------------------------------------------------    

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
    rank = request.args.get('rank', default='*', type=str)
    subfamily =request.args.get('subfamily', default='*', type=str)
    genus =request.args.get('genus', default='*', type=str)
    species =request.args.get('species', default='*', type=str)
    status = request.args.get('status', default='*', type=str)
    
    limit = request.args.get('limit', default = '10000', type = int)
    offset = request.args.get('offset', default = '0', type = int)
    
    print("v3 request:" + str(request))    
    #print('id:' + id)
     
    query = session.query(BioregionTaxon)
    query = query.join(Bioregion).join(Taxon)
    # Bioregion criteria
    if (bioregionName != '*'):
      query = query.filter(Bioregion.name == bioregionName)      
    # Taxon criteria        
    if (taxonName != '*'):
      query = query.filter(BioregionTaxon.taxonName == taxonName)
    if (rank != '*'):
      if (rank == 'species'):
        #query = query.filter(Taxon.rank == 'species' or Taxon.rank == 'subspecies')
        query = query.filter(or_(Taxon.rank == 'species', Taxon.rank == 'subspecies'))
      else:
        query = query.filter(Taxon.rank == rank)
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
    except:
        print("v3 bioregionTaxa error")
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
        , 'rank': bioregionTaxon.taxon.rank
        , 'subfamily': bioregionTaxon.taxon.subfamily
        , 'genus': bioregionTaxon.taxon.genus
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
    metaDataDict['bioregionCount'] = bioregionTaxaCount
    
    return jsonify(metaData=metaDataDict, bioregionTaxa=dataList)                 

# ----------------------------------------------------------------------------------------


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
    limit = request.args.get('limit', default = '10000', type = int)
    offset = request.args.get('offset', default = '0', type = int)

    print("v3 request:" + str(request))
    #print('taxonName:' + taxonName + ' shotType:' + shotType + ' code:' + code)

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
    except:
        print("v3 taxaImages error")
        return
            
    taxaDict = TaxaImage.getTaxaDict(data)

    taxonCount = TaxaImage.getTaxonCount(taxaDict)
    specimenCount = TaxaImage.getSpecimenCount(taxaDict)
    imageCount = TaxaImage.getImageCount(taxaDict)
    dataList = TaxaImage.getDataList(taxaDict)

    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['limit'] = limit
    metaDataDict['taxonCount'] = taxonCount
    metaDataDict['specimenCount'] = specimenCount
    metaDataDict['imageCount'] = imageCount
    
    return jsonify(metaData=metaDataDict, taxaImages=dataList)

                
# ----------------------------------------------------------------------------------------

    
@application.route('/images', methods=['GET'])
def getImages():
    since = request.args.get('since', default='*', type=str)
    shotType = request.args.get('shotType', default='*', type=str)
    code = request.args.get('code', default='*', type=str)
    limit = request.args.get('limit', default = '10000', type = int)            
    offset = request.args.get('offset', default = '0', type = int)
    
    print("v3 request:" + str(request))
    #print('since:' + since + ' shotType:' + shotType + ' code:' + code)
        
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
    except:
        print("v3 images error")
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
          'imageId': image.uid
        , 'shotType': image.shotType
        , "urls": urls
        , 'uploadDate': image.uploadDate
        , 'shotNumber': image.shotNumber
        , 'hasTiff': image.hasTiff
        }
        if (tCode != image.code):
            if (tCode != ''): # we have one to insert. (Is not the first record).
              specimenDict = {
                  'code': tCode
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
          'code': tCode
        , 'url': 'http://localhost:5000/specimens?code=' + tCode
        , 'images': images
      }
			  
    specimenCount += 1
    dataList.append(specimenDict)
     
    # SELECT uid,shot_type,upload_date,shot_number,has_tiff FROM image WHERE image_of_id=? ORDER BY shot_number ASC     
    if (isDevMode):
        print(query)
    
    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['limit'] = limit
    metaDataDict['specimenCount'] = specimenCount
    metaDataDict['count'] = imageCount
    

    return jsonify(metaData=metaDataDict, images=dataList)                 


@application.route('/distinctTaxa', methods=['GET'])
def getDistinctTaxa():
    rank = request.args.get('rank', default = '*', type = str)    
    country = request.args.get('country', default = '*', type = str)
    habitat = request.args.get('habitat', default = '*', type = str)
    minDate = request.args.get('minDate', default = '*', type = str)
    maxDate = request.args.get('maxDate', default = '*', type = str)   
    minElevation = request.args.get('minElevation', default = '*', type = str) 
    maxElevation = request.args.get('maxElevation', default = '*', type = str) 
    limit = request.args.get('limit', default = '10000', type = int)
    offset = request.args.get('offset', default = '0', type = int)

    print("v3 request:" + str(request))
        
    query = session.query(Specimen)
 
    if ('subfamily' == rank):
      query = session.query(Specimen.subfamily).distinct()  # was db.session...
    if ('genus' == rank):
      query = session.query(Specimen.genus).distinct()      # was db.session...
     
    # if any of these are used, performance suffers!    
    if (country != '*') :
      query = query.filter(Specimen.country.contains(country))
    if (habitat != '*') :
      query = query.filter(Specimen.habitat.contains(habitat))
    if (maxDate != '*') :
      query = query.filter(Specimen.dateCollected < maxDate)
    if (minDate != '*') :
      query = query.filter(Specimen.dateCollected > minDate)
    if (minElevation != '*') :
      query = query.filter(Specimen.minimumElevationInMeters > minElevation)
    if (maxElevation != '*') :
      query = query.filter(Specimen.minimumElevationInMeters < maxElevation)    

    query = query.limit(limit)
    query = query.offset(offset)

    try:
        data = query.all()
    except:
        print("v3 distinctTaxa error")
        return

    data_all = []
    for taxa in data:
      if ('subfamily' == rank):
        data_all.append(taxa.subfamily)
      if ('genus' == rank):
        #print("rank:" + rank + " genus:" + str(taxa.genus))
        data_all.append(taxa.genus)
      #if ('species' == rank):

    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['limit'] = limit
    metaDataDict['count'] = len(data_all)
        
    if (isDevMode):
        print(query)
        
    if ('subfamily' == rank):    
      return jsonify(metaData=metaDataDict, subfamilies=data_all) 
    if ('genus' == rank):
      return jsonify(metaData=metaDataDict, genera=data_all) 
        
# ---------------------------------------------------------------------------------------


# http://localhost:5000/geoSpecimens?coords=37,%20-122&limit=8000&radius=2
@application.route('/geoSpecimens', methods=['GET'])
def getGeoSpecimens():
    coords = request.args.get('coords', default = '*', type = str) #(?coord=37, -122) is cal academy
    if (coords == '*'):
      return 'Must enter coords for a geoSpecimens request'
    radius = request.args.get('radius', default = '5', type = int)  # in kilometers
    limit = request.args.get('limit', default = '100', type = int)
    offset = request.args.get('offset', default = '0', type = int)
    distinct = request.args.get('distinct', default = '*', type = str)

    print("v3 request:" + str(request))

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
      except:
        print("v3 geoSpecimens error")
        return
    
      data_all = []
      for sp in data:
        if (distinct == 'subfamily'):
          data_all.append(sp.subfamily)
        #  specDict = { 'subfamily': sp.subfamily }
        if (distinct == 'genus'):
          data_all.append(sp.genus)
        #  specDict = { 'genus': sp.genus }
        #data_all.append(specDict)
    
      params = []
      params.append(request.args)    
      metaDataDict = {}
      metaDataDict['parameters'] = params
      metaDataDict['limit'] = limit
      metaDataDict['count'] = len(data_all)
    
      if (distinct == 'subfamily'):
        return jsonify(metaData=metaDataDict, subfamilies=data_all)    
      if (distinct == 'genus'):
        return jsonify(metaData=metaDataDict, genera=data_all)    
      return jsonify(_metaData=metaDataDict, specimens=data_all)

   # if not distinct. Show everything.
    query = query.limit(limit)
    query = query.offset(offset)
    
    try:
        data = query.all()
    except:
        print("v3 geoSpecimens2 error")
        return
            
    data_all = []
    for sp in data:
        specDict = Specimen.getSpecDict(sp)

        data_all.append(specDict)

    #print(query)

    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['limit'] = limit
    metaDataDict['count'] = len(data_all)

    return jsonify(metaData=metaDataDict, specimens=data_all)

# --------------------------------------------------------------------------------------


@application.route('/specimens', methods=['GET'])
def getSpecimen():
    # Process parameters
    family = request.args.get('family', default = '*', type = str)
    subfamily = request.args.get('subfamily', default = '*', type = str)
    genus = request.args.get('genus', default = '*', type = str)
    species = request.args.get('species', default = '*', type = str)
    code = request.args.get('code', default = '*', type = str)
    country = request.args.get('country', default = '*', type = str)
    bioregion = request.args.get('bioregion', default = '*', type = str)
    habitat = request.args.get('habitat', default = '*', type = str)
    type = request.args.get('type', default='*', type = str)
    georeferenced = request.args.get('georeferenced', default=0, type = int)
    bbox = request.args.get('bbox', default = '*', type = str)
    minDate = request.args.get('minDate', default = '*', type = str)
    maxDate = request.args.get('maxDate', default = '*', type = str)   
    minElevation = request.args.get('minElevation', default = '*', type = str) 
    maxElevation = request.args.get('maxElevation', default = '*', type = str) 
    museum = request.args.get('museum', default = '*', type = str)
    ownedby = request.args.get('ownedby', default = '*', type = str)    
    locatedat = request.args.get('locatedat', default = '*', type = str)
    collectedby = request.args.get('collectedby', default = '*', type = str)
    caste = request.args.get('caste', default = '*',  type = str)
    fossil = request.args.get('fossil', default = '*',  type = str)
    validGenus = request.args.get('validGenus', default='*', type=str)
    validSubfamily = request.args.get('validSubfamily', default='*', type=str)
    status = request.args.get('status', default = '*', type = str)
    ndjson = request.args.get('ndjson', default = 0, type = int)
    limit = request.args.get('limit', default = '10000', type = int)
    offset = request.args.get('offset', default = '0', type = int)
    
    print("v3 request:" + str(request))    
    #print("genus:" + genus + " bbox:" + bbox + " type:" + type)
    #print("georeferenced:" + georeferenced + " minDate:" + minDate + " maxDate:" + maxDate)

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
    if (country != '*') :
      query = query.filter(Specimen.country.contains(country))
    if (bioregion != '*') :
      query = query.filter(Specimen.bioregion.contains(bioregion))
    if (habitat != '*') :
      query = query.filter(Specimen.habitat.contains(habitat))
    if (type != '*') :
      query = query.filter(Specimen.typeStatus == type)
    if (georeferenced == 1):
      query = query.filter(Specimen.decimalLatitude != None)
      query = query.filter(Specimen.decimalLongitude != None)    
    if (bbox != '*') :
      coords = bbox.split(",")
      query = query.filter(Specimen.decimalLatitude <= coords[0], Specimen.decimalLatitude >= coords[2]) 
      query = query.filter(Specimen.decimalLongitude <= coords[1], Specimen.decimalLongitude >= coords[3])
    if (maxDate != '*') :
      query = query.filter(Specimen.dateCollected < maxDate)
    if (minDate != '*') :
      query = query.filter(Specimen.dateCollected > minDate)
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
    if (fossil != '*') :
      if (fossil == 'true'): fossil = 1
      if (fossil == 'false'): fossil = 0
      if (fossil == 1 or fossil == 0 or fossil == '1' or fossil == '0'):
        query = query.filter(Specimen.fossil == fossil)
    if (status != '*') :
      query = query.filter(Specimen.status == status)
        
    query = query.limit(limit)
    query = query.offset(offset)

    try:
        data = query.all()
    except:
        print("v3 specimens error")
        return

    data_all = []
    for sp in data:
    
      skip = 0;      
      if (validSubfamily == 'true'):
        skip = sp.subfamily not in ('agroecomyrmecinae', 'amblyoponinae', 'aneuretinae', 'apomyrminae', 'dolichoderinae', 'dorylinae', 'ectatomminae', 'formicinae', 'hetero', 'leptanillinae', 'martialinae', 'myrmeciinae', 'myrmicinae', 'paraponerinae', 'ponerinae', 'proceratiinae', 'pseudomyrmecinae')

        if (skip):
          print("skipping code:" + sp.code + " because in invalid (or fossil) subfamily:" + sp.subfamily)
      if (not skip and validGenus == 'true'):
        # This commented out set would be irregardless of fossil.
        #skip = sp.genus not in ('Acanthognathus','Acanthomyrmex','Acanthoponera','Acanthostichus','Acromyrmex','Acropyga','Adelomyrmex','Adetomyrma','Adlerzia','Aenictogiton','Aenictus','Afromyrma','Afropone','Agastomyrma','Agraulomyrmex','Agroecomyrmex','Alloformica','Alloiomma','Allomerus','Amblyopone','Ancyridris','Aneuretellus','Aneuretus','Anillidris','Anillomyrma','Ankylomyrma','Anochetus','Anomalomyrma','Anonychomyrma','Anoplolepis','Aphaenogaster','Aphomomyrmex','Apomyrma','Apterostigma','Aptinoma','Archaeopone','Archimyrmex','Archiponera','Aretidris','Armania','Armaniella','Arnoldius','Asphinctopone','Asymphylomyrmex','Atopomyrmex','Atta','Attaichnus','Attopsis','Aulacopone','Austromorium','Austroponera','Avitomyrmex','Axinidris','Azteca','Baikuris','Bajcaridris','Baracidris','Bariamyrma','Basiceros','Belonopelta','Biamomyrma','Bilobomyrma','Blepharidatta','Boloponera','Boltonidris','Boltonimecia','Bondroitia','Bothriomyrmex','Bothroponera','Brachymyrmex','Brachyponera','Brachytarsites','Bradoponera','Bregmatomyrma','Britaneuretus','Brownimecia','Buniapone','Burmomyrma','Calomyrmex','Calyptites','Calyptomyrmex','Camelomecia','Camponotites','Camponotus','Cananeuretus','Canapone','Cardiocondyla','Carebara','Casaleia','Cataglyphis','Cataglyphoides','Cataulacus','Centromyrmex','Cephalomyrmex','Cephalopone','Cephalotes','Cerapachys','Ceratomyrmex','Cheliomyrmex','Chimaeridris','Chimaeromyrma','Chronomyrmex','Chronoxenus','Chrysapace','Cladomyrma','Clavipetiola','Colobopsis','Colobostruma','Conoformica','Crematogaster','Cretomyrma','Cretopone','Cryptomyrmex','Cryptopone','Ctenobethylus','Curticorna','Curtipalpulus','Cyatta','Cylindromyrmex','Cyphoidris','Cyphomyrmex','Cyrtopone','Dacatria','Dacetinops','Daceton','Diacamma','Diaphoromyrma','Dicroaspis','Dilobocondyla','Dinomyrmex','Dinoponera','Diplomorium','Discothyrea','Dlusskyidris','Doleromyrma','Dolichoderus','Dolichomyrma','Dolioponera','Dolopomyrmex','Dorylus','Dorymyrmex','Drymomyrmex','Eburopone','Echinopla','Eciton','Ecphorella','Ectatomma','Ectomomyrmex','Elaeomyrmex','Elaphrodites','Eldermyrmex','Electromyrmex','Electroponera','Emeryopone','Emplastus','Enneamerus','Eoaenictites','Eocenidris','Eocenomyrma','Eoformica','Eogorgites','Eoleptocerites','Eomyrmex','Eoponerites','Eotapinoma','Epelysidris','Epopostruma','Erromyrma','Eulithomyrmex','Euponera','Euprenolepis','Eurhopalothrix','Eurymyrmex','Eurytarsites','Eusphinctus','Eutetramorium','Fallomyrma','Feroponera','Fisheropone','Fonsecahymen','Forelius','Formica','Formicoxenus','Formosimyrma','Froggattella','Fulakora','Furcisutura','Fushuniformica','Fushunomyrmex','Gaoligongidris','Gauromyrmex','Gerontoformica','Gesomyrmex','Gigantiops','Glaphyromyrmex','Gnamptogenys','Goniomma','Gracilidris','Hagensia','Haidomyrmex','Haidomyrmodes','Haidoterminus','Harpagoxenus','Harpegnathos','Heeridris','Heteroponera','Huaxiaformica','Huberia','Hylomyrma','Hypopomyrmex','Hypoponera','Iberoformica','Ilemomyrmex','Imhoffia','Indomyrma','Iridomyrmex','Iroponera','Ishakidris','Kalathomyrmex','Kartidris','Kempfidris','Khetania','Klondikia','Kohlsimyrma','Kotshkorkia','Ktunaxia','Kyromyrma','Labidus','Lachnomyrmex','Lasiomyrma','Lasiophanes','Lasius','Lenomyrmex','Lepisiota','Leptanilla','Leptanilloides','Leptogasteritus','Leptogenys','Leptomyrmex','Leptomyrmula','Leptothorax','Leucotaphus','Liaoformica','Linepithema','Linguamyrmex','Liometopum','Liomyrmex','Lioponera','Lividopone','Loboponera','Lonchomyrmex','Longicapitia','Longiformica','Lophomyrmex','Lordomyrma','Loweriella','Macabeemyrma','Magnogasterites','Malagidris','Manica','Martialis','Mayaponera','Mayriella','Megalomyrmex','Megaponera','Melissotarsus','Melophorus','Meranoplus','Mesoponera','Mesostruma','Messelepone','Messor','Metapone','Mianeuretus','Microdaceton','Miomyrmex','Miosolenopsis','Monomorium','Myanmyrma','Mycetagroicus','Mycetarotes','Mycetophylax','Mycetosoritis','Mycocepurus','Myopias','Myopopone','Myrcidris','Myrmecia','Myrmecina','Myrmecites','Myrmecocystus','Myrmecorhynchus','Myrmelachista','Myrmica','Myrmicaria','Myrmicocrypta','Myrmoteras','Mystrium','Nebothriomyrmex','Neivamyrmex','Neocerapachys','Neoponera','Nesomyrmex','Nomamyrmex','Noonilla','Nothomyrmecia','Notoncus','Notostigma','Novomessor','Nylanderia','Ochetellus','Ochetomyrmex','Octostruma','Ocymyrmex','Odontomachus','Odontoponera','Oecophylla','Onychomyrmex','Ooceraea','Opamyrma','Ophthalmopone','Opisthopsis','Orapia','Orbicapitia','Orbigastrula','Orectognathus','Ovalicapito','Ovaligastrula','Overbeckia','Oxyepoecus','Oxyidris','Oxyopomyrmex','Pachycondyla','Paltothyreus','Papyrius','Parameranoplus','Paramycetophylax','Paraneuretus','Paraparatrechina','Paraphaenogaster','Paraponera','Parasyscia','Paratopula','Paratrechina','Parvaponera','Patagonomyrmex','Perissomyrmex','Peronomyrmex','Petalomyrmex','Petraeomyrmex','Petropone','Phalacromyrmex','Phaulomyrma','Pheidole','Philidris','Phrynoponera','Pilotrochus','Pityomyrmex','Plagiolepis','Platythyrea','Plectroctena','Plesiomyrmex','Podomyrma','Poecilomyrma','Pogonomyrmex','Polyergus','Polyrhachis','Ponera','Ponerites','Poneropterus','Prenolepis','Prionomyrmex','Prionopelta','Pristomyrmex','Proatta','Probolomyrmex','Procerapachys','Proceratium','Procryptocerus','Prodimorphomyrmex','Proformica','Proiridomyrmex','Prolasius','Promyopias','Propodilobus','Protalaridris','Protaneuretus','Protanilla','Protazteca','Protoformica','Protomyrmica','Protopone','Protrechina','Psalidomyrmex','Pseudarmania','Pseudectatomma','Pseudoatta','Pseudocamponotus','Pseudolasius','Pseudomyrmex','Pseudoneoponera','Pseudonotoncus','Pseudoponera','Quadrulicapito','Quineangulicapito','Rasopone','Ravavy','Recurvidris','Rhopalomastix','Rhopalothrix','Rhytidoponera','Rogeria','Romblonella','Rossomyrmex','Rostromyrmex','Rotastruma','Royidris','Santschiella','Scyphodon','Secostruma','Sericomyrmex','Sicilomyrmex','Simopelta','Simopone','Sinoformica','Sinomyrmex','Sinotenuicapito','Solenopsis','Solenopsites','Sphaerogasterites','Sphecomyrma','Sphinctomyrmex','Stegomyrmex','Stenamma','Stereomyrmex','Stigmacros','Stigmatomma','Stigmomyrmex','Stiphromyrmex','Streblognathus','Strongylognathus','Strumigenys','Syllophopsis','Syscia','Talaridris','Tanipone','Taphopone','Tapinolepis','Tapinoma','Tatuidris','Technomyrmex','Temnothorax','Terataner','Teratomyrmex','Tetheamyrma','Tetramorium','Tetraponera','Thaumatomyrmex','Titanomyrma','Trachymyrmex','Tranopelta','Trichomyrmex','Tropidomyrmex','Turneria','Typhlomyrmex','Tyrannomyrmex','Usomyrma','Veromessor','Vicinopone','Vitsika','Vollenhovia','Vombisidris','Wasmannia','Wilsonia','Wumyrmex','Xenomyrmex','Xymmer','Yantaromyrmex','Yavnella','Ypresiomyrma','Yunodorylus','Zasphinctus','Zatania','Zhangidris','Zherichinius','Zigrasimecia')
        skip = sp.genus not in ('Ankylomyrma','Tatuidris','Adetomyrma','Amblyopone','Fulakora','Myopopone','Mystrium','Onychomyrmex','Prionopelta','Stigmatomma','Xymmer','Aneuretus','Apomyrma','Anillidris','Anonychomyrma','Aptinoma','Arnoldius','Axinidris','Azteca','Bothriomyrmex','Chronoxenus','Doleromyrma','Dolichoderus','Dorymyrmex','Ecphorella','Forelius','Froggattella','Gracilidris','iridomyrmex','Leptomyrmex','Linepithema','Liometopum','Loweriella','Nebothriomyrmex','Ochetellus','Papyrius','Philidris','Ravavy','Tapinoma','Technomyrmex','Turneria','Acanthostichus','Aenictogiton','Aenictus','Cerapachys','Cheliomyrmex','Chrysapace','Cylindromyrmex','Dorylus','Eburopone','Eciton','Eusphinctus','Labidus','Leptanilloides','Lioponera','Lividopone','Neivamyrmex','Neocerapachys','Nomamyrmex','Ooceraea','Parasyscia','Simopone','Sphinctomyrmex','Syscia','Tanipone','Vicinopone','Yunodorylus','Zasphinctus','Ectatomma','Gnamptogenys','Rhytidoponera','Typhlomyrmex','Acropyga','Agraulomyrmex','Alloformica','Anoplolepis','Aphomomyrmex','Bajcaridris','Brachymyrmex','Bregmatomyrma','Calomyrmex','Camponotus','Cataglyphis','Cladomyrma','Colobopsis','Dinomyrmex','Echinopla','Euprenolepis','Formica','Gesomyrmex','Gigantiops','iberoformica','Lasiophanes','Lasius','Lepisiota','Melophorus','Myrmecocystus','Myrmecorhynchus','Myrmelachista','Myrmoteras','Notoncus','Notostigma','Nylanderia','Oecophylla','Opisthopsis','Overbeckia','Paraparatrechina','Paratrechina','Petalomyrmex','Plagiolepis','Polyergus','Polyrhachis','Prenolepis','Proformica','Prolasius','Pseudolasius','Pseudonotoncus','Rossomyrmex','Santschiella','Stigmacros','Tapinolepis','Teratomyrmex','Zatania','Acanthoponera','Aulacopone','Heteroponera','Anomalomyrma','Leptanilla','Noonilla','Opamyrma','Phaulomyrma','Protanilla','Scyphodon','Yavnella','Martialis','Myrmecia','Nothomyrmecia','Acanthognathus','Acanthomyrmex','Acromyrmex','Adelomyrmex','Adlerzia','Allomerus','Ancyridris','Anillomyrma','Aphaenogaster','Apterostigma','Aretidris','Atopomyrmex','Atta','Austromorium','Baracidris','Bariamyrma','Basiceros','Blepharidatta','Bondroitia','Calyptomyrmex','Cardiocondyla','Carebara','Cataulacus','Cephalotes','Chimaeridris','Colobostruma','Crematogaster','Cryptomyrmex','Cyatta','Cyphoidris','Cyphomyrmex','Dacatria','Dacetinops','Daceton','Diaphoromyrma','Dicroaspis','Dilobocondyla','Diplomorium','Dolopomyrmex','Epelysidris','Epopostruma','Erromyrma','Eurhopalothrix','Eutetramorium','Formicoxenus','Formosimyrma','Gaoligongidris','Gauromyrmex','Goniomma','Harpagoxenus','Huberia','Hylomyrma','indomyrma','ishakidris','Kalathomyrmex','Kartidris','Kempfidris','Lachnomyrmex','Lasiomyrma','Lenomyrmex','Leptothorax','Liomyrmex','Lophomyrmex','Lordomyrma','Malagidris','Manica','Mayriella','Megalomyrmex','Melissotarsus','Meranoplus','Mesostruma','Messor','Metapone','Microdaceton','Monomorium','Mycetagroicus','Mycetarotes','Mycetophylax','Mycetosoritis','Mycocepurus','Myrmecina','Myrmica','Myrmicaria','Myrmicocrypta','Nesomyrmex','Novomessor','Ochetomyrmex','Octostruma','Ocymyrmex','Orectognathus','Oxyepoecus','Oxyopomyrmex','Paramycetophylax','Paratopula','Patagonomyrmex','Perissomyrmex','Peronomyrmex','Phalacromyrmex','Pheidole','Pilotrochus','Podomyrma','Poecilomyrma','Pogonomyrmex','Pristomyrmex','Proatta','Procryptocerus','Propodilobus','Protalaridris','Pseudoatta','Recurvidris','Rhopalomastix','Rhopalothrix','Rogeria','Romblonella','Rostromyrmex','Rotastruma','Royidris','Secostruma','Sericomyrmex','Solenopsis','Stegomyrmex','Stenamma','Stereomyrmex','Strongylognathus','Strumigenys','Syllophopsis','Talaridris','Temnothorax','Terataner','Tetheamyrma','Tetramorium','Trachymyrmex','Tranopelta','Trichomyrmex','Tropidomyrmex','Tyrannomyrmex','Veromessor','Vitsika','Vollenhovia','Vombisidris','Wasmannia','Xenomyrmex','Paraponera','Anochetus','Asphinctopone','Austroponera','Belonopelta','Boloponera','Bothroponera','Brachyponera','Buniapone','Centromyrmex','Cryptopone','Diacamma','Dinoponera','Dolioponera','Ectomomyrmex','Emeryopone','Euponera','Feroponera','Fisheropone','Hagensia','Harpegnathos','Hypoponera','iroponera','Leptogenys','Loboponera','Mayaponera','Megaponera','Mesoponera','Myopias','Neoponera','Odontomachus','Odontoponera','Ophthalmopone','Pachycondyla','Paltothyreus','Parvaponera','Phrynoponera','Platythyrea','Plectroctena','Ponera','Promyopias','Psalidomyrmex','Pseudoneoponera','Pseudoponera','Rasopone','Simopelta','Streblognathus','Thaumatomyrmex','Discothyrea','Probolomyrmex','Proceratium','Myrcidris','Pseudomyrmex','Tetraponera')
        if (skip):
          print("skipping code:" + sp.code + " because in invalid (or fossil) genus:" + sp.genus)
      
      if (not skip):    
        specDict = Specimen.getSpecDict(sp)

        data_all.append(specDict)

    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['limit'] = limit
    metaDataDict['offset'] = offset
    metaDataDict['count'] = len(data_all)

    if (ndjson == 0):
      response = jsonify(metaData=metaDataDict, specimens=data_all)
    else:
      #response = jsonify(specimens=data_all[0])
      i = 0;
      response = ""
      for datum in data_all:
        data = '{"index" : {"_index":"tetramorium01","_id":' + str(i) + '} }'
        response = response + data + '\\n' + json.dumps(datum) + '\\n'
        i = i + 1
        #print("ndjson response:" + response)


    #print("response:" + str(response.status_code))
    #print("response:" + str(response.data))
    return response
 
if __name__ == "__main__":  
    application.run()
