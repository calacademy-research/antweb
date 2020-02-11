#! /usr/bin/python

# To Do.
#   Performance tuning. Prevent long runs.
#   Specimen georeferenced parameter.
# integrate recently added fields. Biogeographicregion and collection date
# Collapse specimens and geoSpecimens into specimens?
# Remove the classes into separate files.

import configparser  
from flask_sqlalchemy import SQLAlchemy
from sqlalchemy import create_engine
from flask import Flask, jsonify, json, request
from flask_restful import Api

from sqlalchemy import Column, Integer, String
from sqlalchemy.ext.declarative import declarative_base

import MySQLdb as mysqldb

from datetime import datetime, timedelta

Base = declarative_base()

app = Flask(__name__)

# Read config file
config = configparser.ConfigParser()  
config.read('api_db.conf')

# MySQL configurations
dbUrl = 'mysql+mysqldb://' + config.get('DB', 'user') + \
	 ':' + config.get('DB', 'password') + '@' + \
	 config.get('DB', 'host') + ":" + config.get('DB', 'port') + '/' + config.get('DB', 'db')
#print("dbUrl:" + dbUrl)

#app = Flask(__name__)
app.config['SQLALCHEMY_DATABASE_URI'] = dbUrl
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = True

'''
engine = create_engine('mysql+mysqldb://antweb:f0rm1c6@localhost:3306/ant')
connection = engine.connect()
result = connection.execute("select occurrenceId from darwin_core_2 limit 20")
for row in result:
    print("occurrenceId:", row['occurrenceId'])
connection.close()
'''

db = SQLAlchemy(app)
    
api = Api(app)

@app.route("/")
def hello():  
    return "Hello World!" + " " + app.name + " " + config.get('DB', 'user')

class Images(db.Model):
    __tablename__ = 'image'

    uid = Column(String, primary_key=True)
    shotType = Column('shot_type', String)
    code = Column('image_of_id', String)
    uploadDate = Column('upload_date', String)
    shotNumber = Column('shot_number', String)
    hasTiff = Column('has_tiff', String)

    def __repr__(self):
       return "<Images(uid,='%s', shotType='%s', code='%s', uploadDate='%s', shotNumber='%s', hasTiff='%s')>" % (
         self.uid, self.shotType, self.code, self.uploadDate, self.shotNumber, self.hasTiff)
    
@app.route('/images', methods=['GET'])
def getImages():
    since = request.args.get('since', default='*', type=str)
    shotType = request.args.get('shotType', default='*', type=str)
    code = request.args.get('code', default='*', type=str)
    limit = request.args.get('limit', default = '1000', type = int)            
    offset = request.args.get('offset', default = '0', type = int)
    
    print('since:' + since + ' shotType:' + shotType + ' code:' + code)
        
    query = Images.query;
    if (since != '*'):
      day_interval_before = datetime.now() - timedelta(days=int(since))  
      query = query.filter(Images.uploadDate >= day_interval_before)
    if (shotType != '*'):
      query = query.filter(Images.shotType == shotType)
    if (code != '*'):
      query = query.filter(Images.code == code)
    query = query.limit(limit)
    query = query.offset(offset)    
    data = query.all()
    
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
          'uid': image.uid
        , 'shotType': image.shotType
        , "urls:": urls
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
    #print(query)
    
    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['limit'] = limit
    metaDataDict['specimenCount'] = specimenCount
    metaDataDict['imageCount'] = imageCount
    

    return jsonify(metaData=metaDataDict, specimens=dataList)                 

'''
http://www.antweb.org/images/casent0922603/casent0922603_d_1_high.jpg
{
  "casent0922626":
    {"url": "http:\/\/www.antweb.org\/api\/v2\/?catalogNumber=casent0922626"
    ,"1":
      {"upload_date":"2017-11-09 10:34:34"
      ,"shot_types":
        {"p":
          {"img":["http:\/\/www.antweb.org\/images\/casent0922626\/casent0922626_p_1_high.jpg"
                 ,"http:\/\/www.antweb.org\/images\/casent0922626\/casent0922626_p_1_low.jpg"
                 ,"http:\/\/www.antweb.org\/images\/casent0922626\/casent0922626_p_1_med.jpg"
                 ,"http:\/\/www.antweb.org\/images\/casent0922626\/casent0922626_p_1_thumbview.jpg"]
        }
        ,"h":
          {"img":["http:\/\/www.antweb.org\/images\/casent0922626\/casent0922626_h_1_high.jpg"
                 ,"http:\/\/www.antweb.org\/images\/casent0922626\/casent0922626_h_1_low.jpg"
                 ,"http:\/\/www.antweb.org\/images\/casent0922626\/casent0922626_h_1_med.jpg"
                 ,"http:\/\/www.antweb.org\/images\/casent0922626\/casent0922626_h_1_thumbview.jpg"]
        }
        ,"d":
          {"img":["http:\/\/www.antweb.org\/images\/casent0922626\/casent0922626_d_1_high.jpg"
                 ,"http:\/\/www.antweb.org\/images\/casent0922626\/casent0922626_d_1_low.jpg"
                 ,"http:\/\/www.antweb.org\/images\/casent0922626\/casent0922626_d_1_med.jpg"
                 ,"http:\/\/www.antweb.org\/images\/casent0922626\/casent0922626_d_1_thumbview.jpg"]
          }
        ,"l":
          {"img":["http:\/\/www.antweb.org\/images\/casent0922626\/casent0922626_l_1_high.jpg"
                 ,"http:\/\/www.antweb.org\/images\/casent0922626\/casent0922626_l_1_low.jpg"
                 ,"http:\/\/www.antweb.org\/images\/casent0922626\/casent0922626_l_1_med.jpg"
                 ,"http:\/\/www.antweb.org\/images\/casent0922626\/casent0922626_l_1_thumbview.jpg"]
          }
        }
      }
    },
...
'''

 
class Taxa(db.Model):
    __tablename__ = 'darwin_core_3'

    taxonName = Column('taxon_name', primary_key=True)
    subfamily = Column(String)
    genus = Column(String)
    #species = Column(String)

    def __repr__(self):
       return "<Taxa(taxonName,='%s')>" % (
         self.taxonName)

@app.route('/taxa', methods=['GET'])
def getTaxa():
    rank = request.args.get('rank', default = '*', type = str)    
    country = request.args.get('country', default = '*', type = str)
    habitat = request.args.get('habitat', default = '*', type = str)
    minDate = request.args.get('minDate', default = '*', type = str)
    maxDate = request.args.get('maxDate', default = '*', type = str)   
    minElevation = request.args.get('minElevation', default = '*', type = str) 
    maxElevation = request.args.get('maxElevation', default = '*', type = str) 
    limit = request.args.get('limit', default = '1000', type = int)
    offset = request.args.get('offset', default = '0', type = int)
        
    query = Taxa.query;
 
    if ('subfamily' == rank):
      query = db.session.query(Taxa.subfamily).distinct()
    if ('genus' == rank):
      query = db.session.query(Taxa.genus).distinct()
    
    # if any of these are used, performance suffers!    
    if (country != '*') :
      query = query.filter(Taxa.country.contains(country))
    if (habitat != '*') :
      query = query.filter(Taxa.habitat.contains(habitat))
    if (maxDate != '*') :
      query = query.filter(Taxa.dateCollected < maxDate)
    if (minDate != '*') :
      query = query.filter(Taxa.dateCollected > minDate)
    if (minElevation != '*') :
      query = query.filter(Taxa.minimumElevationInMeters > minElevation)
    if (maxElevation != '*') :
      query = query.filter(Taxa.minimumElevationInMeters < maxElevation)    

    query = query.limit(limit)
    query = query.offset(offset)
    data = query.all()

    data_all = []
    for taxa in data:
      if ('subfamily' == rank):
        data_all.append(Taxa.subfamily)
      if ('genus' == rank):
        data_all.append(Taxa.genus)
      #if ('species' == rank):

    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['limit'] = limit
    metaDataDict['count'] = len(data_all)
        
    print(query)
    return jsonify(metaData=metaDataDict, specimens=data_all) 
    

class Specimens(db.Model):
    __tablename__ = 'darwin_core_2'

    occurrenceId = Column(String, primary_key=True)
    ownerInstitutionCode = Column(String)
    basisOfRecord = Column(String)
    institutionCode = Column(String)
    collectionCode = Column(String)
    code = Column("catalogNumber", String)
    dctermsModified = db.Column("dcterms:modified", db.DateTime)
    nomenclaturalCode = Column(String)
    kingdom = Column(String)
    phylum = Column(String)
    classVal = Column("class", String)
    order = Column(String)
    family = Column(String)
    subfamily = Column(String)
    genus = Column(String)
    subgenus = Column(String)
    specificEpithet = Column(String)
    intraspecificEpithet = Column(String)
    scientific_name = Column(String)
    higherClassification = Column(String)
    typeStatus = Column(String)
    stateProvince = Column(String)
    country = Column(String)
    decimalLatitude = Column(db.Numeric) # double
    decimalLongitude = Column(db.Float) # double
    georeferenceRemarks = Column(String)
    dateIdentified = Column(db.DateTime) # date
    dateCollected = Column(db.DateTime)
    habitat = Column(String) #text
    recordedBy = Column(String)
    samplingProtocol = Column(String)
    sex = Column(String)
    preparations = Column(String)
    fieldNumber = Column(String)
    identifiedBy = Column(String)
    locality = Column(String)
    locationRemarks = Column(String)
    occurrenceRemarks = Column(String)
    fieldNotes = Column(String)
    eventDate = Column(String)
    verbatimEventDate = Column(String)
    minimumElevationInMeters = Column(Integer)
    biogeographicregion = Column(String)
    antweb_taxon_name = Column(String)
    
    def __repr__(self):
       return "<Specimens(occurrenceId='%s', ownerInstitutionCode='%s' \
           , basisOfRecord='%s', institutionCode='%s', collectionCode='%s' \
           , code='%s', dctermsModified='%s', nomenclaturalCode='%s' \
           , kingdom='%s', phylum='%s', classVal='%s', order='%s' \
           , family='%s', subfamily='%s', genus='%s', subgenus='%s' \
           , specificEpithet='%s', intraspecificEpithet='%s', scientific_name='%s' \
           , higherClassification='%s', typeStatus='%s', stateProvince='%s' \
           , country='%s', decimalLatitude='%s', decimalLongitude='%s' \
           , georeferenceRemarks='%s', dateIdentified='%s', dateCollected='%s', habitat='%s' \
           , recordedBy='%s', samplingProtocol='%s', sex='%s' \
           , preparations='%s', fieldNumber='%s', identifiedBy='%s' \
           , locality='%s', locationRemarks='%s', occurrenceRemarks='%s' \
           , fieldNotes='%s', eventDate='%s', verbatimEventDate='%s ' \
           , minimumElevationInMeters='%s', biogeographicregion='%s', antweb_taxon_name='%s' \
       )>" % (
             self.occurrenceId, self.ownerInstitutionCode \
           , self.basisOfRecord, self.institutionCode, self.collectionCode \
           , self.code, self.dctermsModified, self.nomenclaturalCode \
           , self.kingdom, self.phylum, self.classVal, self.order \
           , self.family, self.subfamily, self.genus, self.subgenus \
           , self.specificEpithet, self.intraspecificEpithet, self.scientific_name \
           , self.higherClassification, self.typeStatus, self.stateProvince \
           , self.country, self.decimalLatitude, self.decimalLongitude \
           , self.georeferenceRemarks, self.dateIdentified, self.dateCollected, self.habitat \
           , self.recordedBy, self.samplingProtocol, self.sex \
           , self.preparations, self.fieldNumber, self.identifiedBy \
           , self.locality, self.locationRemarks, self.occurrenceRemarks \
           , self.fieldNotes, self.eventDate, self.verbatimEventDate \
           , self.minimumElevationInMeters, self.biogeographicregion, self.antweb_taxon_name)

# http://localhost:5000/geoSpecimens?coords=37,%20-122&limit=8000&radius=2
@app.route('/geoSpecimens', methods=['GET'])
def getGeoSpecimens():
    coords = request.args.get('coords', default = '*', type = str) #(?coord=37, -122) is cal academy
    if (coords == '*'):
      return 'Must enter coords for a geoSpecimens request'
    radius = request.args.get('radius', default = '5', type = int)  # in kilometers
    limit = request.args.get('limit', default = '100', type = int)
    offset = request.args.get('offset', default = '0', type = int)
    distinct = request.args.get('distinct', default = '*', type = str)

    query = Specimens.query;    
    coordsArray = coords.split(",")
    query = query.filter(Specimens.decimalLatitude <= (int(coordsArray[0]) + int(radius)), Specimens.decimalLatitude >= (int(coordsArray[0]) - int(radius))) 
    query = query.filter(Specimens.decimalLongitude <= (int(coordsArray[1]) + int(radius)), Specimens.decimalLongitude >= (int(coordsArray[1]) - int(radius))) 

    if (distinct != '*'):
      if (distinct == 'subfamily'):
        query = db.session.query(Specimens.subfamily).distinct()
      if (distinct == 'genus'):
        query = db.session.query(Specimens.genus).distinct()

      query = query.limit(limit)
      query = query.offset(offset)
      data = query.all()    
    
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
    data = query.all()    
    
    data_all = []
    for sp in data:
        specDict = getSpecDict(sp)
                      
        data_all.append(specDict)
    
    #print(query)
    

    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['limit'] = limit
    metaDataDict['count'] = len(data_all)
        
    return jsonify(metadata=metaDataDict, specimens=data_all)    
           
@app.route('/specimens', methods=['GET'])
def getSpecimen():
    # Process parameters
    subfamily = request.args.get('subfamily', default = '*', type = str)
    genus = request.args.get('genus', default = '*', type = str)
    species = request.args.get('species', default = '*', type = str)
    code = request.args.get('code', default = '*', type = str)
    country = request.args.get('country', default = '*', type = str)
    habitat = request.args.get('habitat', default = '*', type = str)
    type = request.args.get('type', default='*', type = str)
    georeferenced = request.args.get('georeferenced', type = str)
    bbox = request.args.get('bbox', default = '*', type = str)
    minDate = request.args.get('minDate', default = '*', type = str)
    maxDate = request.args.get('maxDate', default = '*', type = str)   
    minElevation = request.args.get('minElevation', default = '*', type = str) 
    maxElevation = request.args.get('maxElevation', default = '*', type = str) 
    limit = request.args.get('limit', default = '1000', type = int)
    offset = request.args.get('offset', default = '0', type = int)

    #print("genus:" + genus + " bbox:" + bbox + " type:" + type)
    #print("georeferenced:" + georeferenced + " minDate:" + minDate + " maxDate:" + maxDate)

    # Build query criteria
    query = Specimens.query;
    if (subfamily != '*') :
      query = query.filter(Specimens.subfamily == subfamily)
    if (genus != '*') :
      query = query.filter(Specimens.genus == genus)
    if (species != '*') :
      query = query.filter(Specimens.species == species)
    if (code != '*') :
      query = query.filter(Specimens.code == code)
    if (country != '*') :
      query = query.filter(Specimens.country.contains(country))
    if (habitat != '*') :
      query = query.filter(Specimens.habitat.contains(habitat))
    if (type != '*') :
      query = query.filter(Specimens.typeStatus == type)
    #if (georeferenced == 'true'):
      #query = query.filter(Specimens.decimalLatitude is not null)
      #query = query.filter(Specimens.decimalLongitude is not null)    
    if (bbox != '*') :
      coords = bbox.split(",")
      query = query.filter(Specimens.decimalLatitude <= coords[0], Specimens.decimalLatitude >= coords[2]) 
      query = query.filter(Specimens.decimalLongitude <= coords[1], Specimens.decimalLongitude >= coords[3])
    if (maxDate != '*') :
      query = query.filter(Specimens.dateCollected < maxDate)
    if (minDate != '*') :
      query = query.filter(Specimens.dateCollected > minDate)
    if (minElevation != '*') :
      query = query.filter(Specimens.minimumElevationInMeters > minElevation)
    if (maxElevation != '*') :
      query = query.filter(Specimens.minimumElevationInMeters < maxElevation)      
    query = query.limit(limit)
    query = query.offset(offset)
    data = query.all()

    data_all = []
    for sp in data:
        specDict = getSpecDict(sp)

        data_all.append(specDict)

    params = []
    params.append(request.args)    
    metaDataDict = {}
    metaDataDict['parameters'] = params
    metaDataDict['limit'] = limit
    metaDataDict['offset'] = offset
    metaDataDict['count'] = len(data_all)
        
    return jsonify(metadata=metaDataDict, specimens=data_all) 


def getSpecDict(sp):
	specDict = {
	  'occurrenceId': sp.occurrenceId
	, 'ownerInstitutionCode': sp.ownerInstitutionCode 
	, 'basisOfRecord': sp.basisOfRecord
	, 'institutionCode': sp.institutionCode
	, 'collectionCode': sp.collectionCode
	, 'code': sp.code
	, 'dctermsModified': sp.dctermsModified
	, 'nomenclaturalCode': sp.nomenclaturalCode
	, 'kingdom': sp.kingdom
	, 'phylum': sp.phylum
	, 'classVal': sp.classVal
	, 'order': sp.order
	, 'family': sp.family
	, 'subfamily': sp.subfamily
	, 'genus': sp.genus
	, 'subgenus': sp.subgenus
	, 'specificEpithet': sp.specificEpithet
	, 'intraspecificEpithet': sp.intraspecificEpithet
	, 'scientificName': sp.scientific_name
	, 'higherClassification': sp.higherClassification
	, 'type': sp.typeStatus
	, 'stateProvince': sp.stateProvince
	, 'country': sp.country
	, 'decimalLatitude': sp.decimalLatitude
	, 'decimalLongitude': sp.decimalLongitude
	, 'georeferenceRemarks': sp.georeferenceRemarks
	, 'dateIdentified': sp.dateIdentified
	, 'dateCollected': sp.dateCollected
	, 'habitat': sp.habitat
	, 'recordedBy': sp.recordedBy
	, 'samplingProtocol': sp.samplingProtocol
	, 'sex': sp.sex
	, 'preparations': sp.preparations
	, 'fieldNumber': sp.fieldNumber
	, 'identifiedBy': sp.identifiedBy
	, 'locality': sp.locality
	, 'locationRemark': sp.locationRemarks
	, 'occurrenceRemarks': sp.occurrenceRemarks
	, 'fieldNotes': sp.fieldNotes
	, 'verbatimEventDate': sp.verbatimEventDate    #, sp.eventDate \
	, 'minimumEleationInMeters': sp.minimumElevationInMeters
	, 'biogeographicregion': sp.biogeographicregion
	, 'antwebTaxonName': sp.antweb_taxon_name
	}
	
	return specDict
    
if __name__ == "__main__":  
    app.run()
    
    
    
    