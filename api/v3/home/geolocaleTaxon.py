from sqlalchemy import Column, Integer, String, Boolean, DateTime, ForeignKey

from sqlalchemy.orm import relationship

from sqlalchemy.ext.declarative import declarative_base


#from sqlalchemy.ext.declarative import declarative_base
#Base = declarative_base()

import Base

from home.geolocale import Geolocale
from home.taxon import Taxon

class GeolocaleTaxon(): # was Base
    __tablename__ = 'geolocale_taxon'

    geolocaleId = Column('geolocale_id', Integer, ForeignKey(Geolocale.id), primary_key=True)
    taxonName = Column('taxon_name', String, primary_key=True)
    #taxonName = Column('taxon_name', String, ForeignKey(Taxon.taxonName), primary_key=True)
    created = Column(DateTime)
    subfamilyCount = Column('subfamily_count', Integer)
    genusCount = Column('genus_count', Integer)
    speciesCount = Column('species_count', Integer)
    specimenCount = Column('specimen_count', Integer)
    imageCount = Column('image_count', Integer)
    isIntroduced = Column('is_introduced', Boolean)
    isEndemic = Column('is_endemic', Boolean)
    
    #geolocale = relationship("Geolocale", back_populates="geolocaleTaxa")
    geolocale = relationship("Geolocale")

    def __repr__(self): 
       return "<GeolocaleTaxa( \
           geolocaleId='%s', taxonName='%s', created='%s' \
         , subfamilyCount ='%s', genusCount='%s', speciesCount='%s', specimenCount='%s', imageCount='%s' \
         , isIntroduced='%s', isEndemic='%s' \
       )>" % (         
           self.geolocaleId, self.taxonName, self.created \
         , self.subfamilyCount, self.genusCount, self.speciesCount, self.specimenCount, self.imageCount \
         , self.isIntroduced, self.isEndemic \
       )

#geolocale = relationship("Geolocale")

