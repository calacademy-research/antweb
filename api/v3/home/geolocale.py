from sqlalchemy import Column, Integer, String


from sqlalchemy.ext.declarative import declarative_base
Base = declarative_base()

class Geolocale(Base):
    __tablename__ = 'geolocale'

    id = Column(String, primary_key=True)
    name = Column(String)
    parent = Column(String)
    georank = Column(String)
    isValid = Column('is_valid', String)
    region = Column(String)
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
         , isValid='%s%' \
         , region='%s', bioregion='%s' \
         , subfamily_count='%s', genus_count='%s', species_count='%s' \
	     , specimen_count='%s', image_count='%s', imaged_specimen_count='%s' \
         , endemicSpeciesCount='%s', introducedSpeciesCount='%s' \
       )>" % (         
           self.id, self.name, self.parent, self.georank \
         , self.isValid \
         , self.region , self.bioregion \
         , self.subfamilyCount, self.genusCount, self.speciesCount \
         , self.specimenCount, self.imageCount, self.imagedSpecimenCount \
         , self.endemicSpeciesCount, self.introducedSpeciesCount 
       )
