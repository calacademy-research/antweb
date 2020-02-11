from sqlalchemy import Column, Integer, String, DateTime, Numeric, Float, Boolean
from flask_sqlalchemy import Model

from sqlalchemy.ext.declarative import declarative_base
Base = declarative_base()

#import sqlalchemy as db

from flask_sqlalchemy import SQLAlchemy

#db = SQLAlchemy(application)

class Specimen(Base):
    __tablename__ = 'api3_specimen'

    #db = SQLAlchemy(model_class=Specimen)

    #db = Model

    occurrenceId = Column(String, primary_key=True)
    ownerInstitutionCode = Column(String)
    basisOfRecord = Column(String)
    institutionCode = Column(String)
    collectionCode = Column(String)
    code = Column("catalogNumber", String)
    dctermsModified = Column("dcterms:modified", DateTime)
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
    bioregion = Column(String)
    decimalLatitude = Column(Numeric) # Could be Float or Numeric
    decimalLongitude = Column(Numeric) # was: double
    georeferenceRemarks = Column(String)
    dateIdentified = Column(DateTime) # date
    dateCollected = Column(DateTime)
    habitat = Column(String) #text
    microhabitat = Column(String)
    habitats = Column(String)
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
    antweb_taxon_name = Column("taxon_name", String)
    museum = Column(String)
    ownedby = Column(String)
    locatedat = Column(String)
    collectedby = Column(String)
    caste = Column(String)
    fossil = Column('fossil', Boolean)
    status = Column('taxon_status', String)

    #isMale = Column('is_male', Boolean)
    #isWorker = Column('is_worker', Boolean)
    #isQueen = Column('isQueen', Boolean)
    
    def __repr__(self):
       return "<Specimen(occurrenceId='%s', ownerInstitutionCode='%s' \
           , basisOfRecord='%s', institutionCode='%s', collectionCode='%s' \
           , code='%s', dctermsModified='%s', nomenclaturalCode='%s' \
           , kingdom='%s', phylum='%s', classVal='%s', order='%s' \
           , family='%s', subfamily='%s', genus='%s', subgenus='%s' \
           , specificEpithet='%s', intraspecificEpithet='%s', scientific_name='%s' \
           , higherClassification='%s', typeStatus='%s', stateProvince='%s' \
           , country='%s', bioregion='%s', decimalLatitude='%s', decimalLongitude='%s' \
           , georeferenceRemarks='%s', dateIdentified='%s', dateCollected='%s' \
           , habitat='%s', microhabitat='%s', habitats='%s' \
           , recordedBy='%s', samplingProtocol='%s', sex='%s' \
           , preparations='%s', fieldNumber='%s', identifiedBy='%s' \
           , locality='%s', locationRemarks='%s', occurrenceRemarks='%s' \
           , fieldNotes='%s', eventDate='%s', verbatimEventDate='%s ' \
           , minimumElevationInMeters='%s', biogeographicregion='%s', taxon_name='%s' \
           , museum='%s', ownedby='%s', locatedat='%s', collectedby='%s' \
           , caste='%s', fossil='%s', status='%s' \
      #     , isMale='%s', isWorker='%s', isQueen='%s' \
       )>" % (
             self.occurrenceId, self.ownerInstitutionCode \
           , self.basisOfRecord, self.institutionCode, self.collectionCode \
           , self.code, self.dctermsModified, self.nomenclaturalCode \
           , self.kingdom, self.phylum, self.classVal, self.order \
           , self.family, self.subfamily, self.genus, self.subgenus \
           , self.specificEpithet, self.intraspecificEpithet, self.scientific_name \
           , self.higherClassification, self.typeStatus, self.stateProvince \
           , self.country, self.bioregion, self.decimalLatitude, self.decimalLongitude \
           , self.georeferenceRemarks, self.dateIdentified, self.dateCollected \
           , self.habitat, self.microhabitat, self.habitats \
           , self.recordedBy, self.samplingProtocol, self.sex \
           , self.preparations, self.fieldNumber, self.identifiedBy \
           , self.locality, self.locationRemarks, self.occurrenceRemarks \
           , self.fieldNotes, self.eventDate, self.verbatimEventDate \
           , self.minimumElevationInMeters, self.biogeographicregion, self.antweb_taxon_name \
           , self.museum, self.ownedby, self.locatedat, self.collectedby \
           , self.caste, self.fossil, self.status
       #    , self.isMale, self.isWorker, self.isQueen
        )

    def getSpecDict(sp):
    
        coordinates = { 
          'lat': sp.decimalLatitude
        , 'lon': sp.decimalLongitude
        }
        geo = {
          'coordinates': coordinates       
        }
    
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
        , 'bioregion': sp.bioregion
        , 'decimalLatitude': sp.decimalLatitude
        , 'decimalLongitude': sp.decimalLongitude
        , 'geo': geo
        , 'georeferenceRemarks': sp.georeferenceRemarks
        , 'dateIdentified': sp.dateIdentified
        , 'dateCollected': sp.dateCollected
        , 'habitat': sp.habitat
        , 'microhabitat': sp.microhabitat
        , 'habitats': sp.habitats
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
        , 'minimumElevationInMeters': sp.minimumElevationInMeters
        , 'biogeographicregion': sp.biogeographicregion
        , 'antwebTaxonName': sp.antweb_taxon_name
        , 'museum': sp.museum
        , 'ownedby': sp.ownedby
        , 'locatedat': sp.locatedat
        , 'collectedby': sp.collectedby    
        , 'caste': sp.caste  
        , 'fossil': sp.fossil  
        , 'status': sp.status
        #, 'isMale': sp.isMale
        #, 'isWorker': sp.isWorker
        #, 'isQueen': sp.isQueen
        }
    
        return specDict
