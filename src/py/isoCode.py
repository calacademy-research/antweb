#! /usr/local/bin/python3.6

# Can be execute as such:
#     python3.6 isoCode.py

# This program loads the iso and iso3 data from here: http://www.nationsonline.org/oneworld/country_code_list.htm
# into the geolocale table. Cutting and pasting the country/code data into an empty text file called iso.txt...

from sqlalchemy import create_engine, or_
from sqlalchemy import Column, Integer, String, Boolean, DateTime, ForeignKey
from sqlalchemy.orm import sessionmaker, relationship
from sqlalchemy.ext.declarative import declarative_base

engine = create_engine('mysql+mysqldb://antweb:f0rm1c6@mysql:3306/ant')

Base = declarative_base()

Session = sessionmaker(bind=engine)
session = Session()

# ---------------------------------------------------------------------------------------


class Geolocale(Base):
    __tablename__ = 'geolocale'

    name = Column(String, primary_key=True)
    isoCode = Column('isoCode', String)
    iso3Code = Column('iso3Code', String)
    georank = Column(String)

    def __repr__(self):
       return "<Geolocale(name='%s', isoCode='%s', iso3Code='%s', georank='%s')>" % (
         self.name, self.isoCode, self.iso3Code, self.georank)

         
def updateGeolocale(country, isoCode, iso3Code):
    #print("country:" + country + " iso:" + iso + " iso3:" + iso3);

    c = session.query(Geolocale) \
      .filter(Geolocale.name == country) \
      .filter(Geolocale.georank == 'country') \
      .update({"isoCode": isoCode, "iso3Code": iso3Code})
       
    if (c == 0):    
      print("country not found:" + country)

def parseIsoTxt():
    # read in iso.txt
    with open('isoCode.txt') as f:
      for line in f:
        line = line.strip()
        values = line.split("\t")
        try:
          country = values[0]
          isoCode = values[1]
          iso3Code = values[2]
          updateGeolocale(country, isoCode, iso3Code) 
        except IndexError:
          print('') 
  
if __name__ == "__main__": 
    parseIsoTxt()

