from sqlalchemy import Column, Integer, String, Boolean, DateTime

from sqlalchemy.ext.declarative import declarative_base
Base = declarative_base()

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
