#! /usr/local/bin/python3.6

# Can be execute in local environment (in /Users/mark/dev/calacademy/antweb/src/py directory) as such:
#     sudo python3.6 imageExif.py
# On server, until python is in sudo's path... On API server. Live server does not have python3.6
#     sudo /usr/local/bin/python3.6 imageExif.py
    

# pip3.6 install piexif
# pip3.6 install --upgrade pip

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

import time
start = time.time()

application = Flask(__name__)
#app.run(port=80)
api = Api(application)

#import io
#from PIL 

# pip install Image
import PIL.Image
import piexif

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

def updateImagesExif():

    since='*'
    shotType='*'
    shotType='D'
    shotNumber = 1
    code='*'
    #code = 'casent0623849'
    code = 'casent0005904';
    limit='*' #10
    offset=0
        
    query = session.query(Image)
    if (since != '*'):
      day_interval_before = datetime.now() - timedelta(days=int(since))  
      query = query.filter(Image.uploadDate >= day_interval_before)
    if (shotType != '*'):
      query = query.filter(Image.shotType == shotType)
    if (shotNumber != '*'):
      query = query.filter(Image.shotNumber == shotNumber)
    if (code != '*'):
      query = query.filter(Image.code == code)
    if (limit != '*'):
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
    
    imageCount = 0
    for image in data:
        imageCount += 1
        modExif(image)        
        print("---updateImagesExif() imageCount:" + str(imageCount) + " code:" + image.code + " num:" + str(image.shotNumber) + " type:" + str(image.shotType))

def modExif(image):
    base = '/data/antweb/images/' + image.code + '/' 
    shotName =  image.code + '_' 
    shotNumber = ''
    if (image.shotNumber > 1):
      shotNumber = "_" + str(image.shotNumber)
    file1 = base + shotName.upper() + image.shotType.upper() + shotNumber + '.tif'
    print('---modExif file:' + file1)

    # Show us what is in the existing metadata.
    # Don't seem to be able to access IPTC or TIFF
    exif_dict = piexif.load(file1)
    for ifd in ("0th", "Exif", "GPS", "1st"):
        for tag in exif_dict[ifd]:
            tagName = piexif.TAGS[ifd][tag]["name"]
            if (tagName is 'ImageResources'):
                print(ifd + ': ImageResources: [long]...')
            elif (tagName is 'XMLPacket'):
                print(ifd + ': XMLPacket: [long]...')            
            else:
                print(ifd + ': ' + tagName + ' value:' + str(exif_dict[ifd][tag]))
                #if (tagName is 'UserComment'):
                #  print("is UserComment")
                  #exif_dict["Exif"]['UserComment'] = 'Hi Mom'
                  #print('change...' + ifd + ': ' + piexif.TAGS[ifd][tag]["name"] + ' value:' + str(exif_dict[ifd][tag]))  
            #print(piexif.TAGS[ifd][tag]["name"], exif_dict[ifd][tag])

    #exif_dict["Exif"]['UserComment'] = 'Hi Mom'

	##exif_dict = {"0th":zeroth_ifd, "Exif":exif_ifd, "GPS":gps_ifd, "1st":first_ifd, "thumbnail":thumbnail}
    # This messes up the EXIF
    #exif_bytes = piexif.dump(exif_dict)
    #im = PIL.Image.open(file1)
    #im.save(file1, exif=exif_bytes)

    exif_bytes = piexif.dump(exif_dict)
    #piexif.insert(exif_bytes, file1)

    new_exif = adjust_exif(exif_dict)
    exif_bytes = piexif.dump(new_exif)
    piexif.insert(exif_bytes, file1)

    #piexif.remove(file1)
    #exif_dict["Exif"]['UserComment'] = 'Hi Mom'
    #exif_dict["Exif"]piexif.TAGS[ifd][tag]["UserComment"] = 'Hi Mom'


  
if __name__ == "__main__":     
    updateImagesExif()
    
end = time.time()
seconds = end - start
if seconds < 60:
  print('Seconds elapsed: ' + str(seconds))
else :
  minutes = seconds / 60;
  print('Minutes elapsed: ' + str(minutes))
      