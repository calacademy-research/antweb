#! /usr/local/bin/python3.6

# Can be execute in local environment (in /Users/mark/dev/calacademy/antweb/src/py directory) as such:
#     sudo python3.6 util.py
# On server, until python is in sudo's path...
#     sudo /usr/local/bin/python3.6 util.py


import configparser  
from flask_sqlalchemy import SQLAlchemy
from flask import Flask, jsonify, json, request
from flask_restful import Api

from sqlalchemy import create_engine, or_
from sqlalchemy import Column, Integer, String, Boolean, DateTime, ForeignKey
from sqlalchemy.orm import sessionmaker, relationship

from sqlalchemy.ext.declarative import declarative_base

engine = create_engine('mysql+mysqldb://antweb:f0rm1c6@mysql:3306/ant')

Base = declarative_base()

Session = sessionmaker(bind=engine)
session = Session()

import MySQLdb as mysqldb

from datetime import datetime, timedelta

import os.path


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
    dbUrl = 'mysql+mysqldb://' + config.get('DB', 'user') + ':' + config.get('DB', 'password') + '@' + config.get('DB', 'host') + ":" + config.get('DB', 'port') + '/' + config.get('DB', 'db') 

    isDevMode = 1 # This will not execute deployed on server. This is how we determined devMode.   
    if (isDevMode):
      print("isDevMode:" + str(isDevMode))

except Exception as e :
    print('Exception e: ' + str(e),' reading configuration file')
    dbUrl = 'mysql+mysqldb://antweb:f0rm1c6@mysql:3306/ant'

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
       
# ----------------------------------------------------------------------------------------

def dirExists(code):
    #print(code)
    dir = '/data/antweb/images/' + code
    #print(dir)
    isDir = os.path.isdir(dir)
    if (not isDir):
        print('not dir:' + dir)
        return 0

    return 1


class Specimen(Base):
    __tablename__ = 'api3_1_specimen'

    code = Column("specimen_code", String, primary_key=True)
    ownerInstitutionCode = Column("ownerInstitutionCode", String)

    def __repr__(self):
       return "<Specimen(code='%s', ownerInstitutionCode='%s' \
       )>" % (
             self.code, self.ownerInstitutionCode \
       )

    def getSpecDict(sp):
        specDict = {
          'ownerInstitutionCode': sp.ownerInstitutionCode 
        , 'specimenCode': sp.code
        }
    
        return specDict
            
    

class Image(Base):
    __tablename__ = 'image'

    uid = Column(String, primary_key=True)
    shotType = Column('shot_type', String)
    code = Column('image_of_id', String)
    uploadDate = Column('upload_date', String)
    shotNumber = Column('shot_number', String)
    hasTiff = Column('has_tiff', String)

    def __repr__(self):
       return "<Image(uid='%s', shotType='%s', code='%s', uploadDate='%s', shotNumber='%s', hasTiff='%s')>" % (
         self.uid, self.shotType, self.code, self.uploadDate, self.shotNumber, self.hasTiff)

def findImage(code):
    query = session.query(Image)
    if (code != '*'):
      query = query.filter(Image.code == code)
    
    data = []
    
    try:
        data = query.all()
    except UnicodeEncodeError as uniError:
        print("uniError:" + code)        
    except Error as error:
        print("images error:" + error.orig.message, error.params)
    
    for image in data:
      return 1
      
    return 0    

def getSpecimen(code):

    query = session.query(Specimen)
    if (code != '*'):
      query = query.filter(Specimen.code == code)
    
    data = []
    
    try:
        data = query.all()
    except UnicodeEncodeError as uniError:
        print("uniError:" + code)        
    except Error as error:
        print("images error:" + error.orig.message, error.params)
    
    for specimen in data:
      print(specimen)
      
    return 1
  
if __name__ == "__main__": 
    getSpecimen('antweb1038039')
   
    # application.run()
