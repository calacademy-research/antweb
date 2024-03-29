#! /usr/local/bin/python3.6

# Can be execute in local environment (in /Users/mark/dev/calacademy/antweb/src/py directory) as such:
#     sudo python3.6 imageCheck.py
# On server, until python is in sudo's path... On API server. Live server does not have python3.6
#     sudo /usr/local/bin/python3.6 imageCheck.py
    
# To Do.
#   Performance tuning. Prevent long runs.
#   Specimen georeferenced parameter.
# integrate recently added fields. Biogeographicregion and collection date
# Remove the classes into separate files.

#import os
#assert os.path.exists('/var/www/html/apiV3/api_db.conf')

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

# was engine = create_engine('mysql+mysqldb://antweb:f0rm1c6@mysql:3306/ant')

# CHANGED APR 2, 2021 - attempting to get working with new docker config
#import MySQLdb as mysqldb
#OLD_PROTOCOL = 'mysql+mysqldb://'
#PROTOCOL = OLD_PROTOCOL

NEW_PROTOCOL = 'mysql+pymysql://'
PROTOCOL = NEW_PROTOCOL

SQLALCHEMY_DATABASE_URI = PROTOCOL + 'antweb:f0rm1c6@127.0.0.1:3306/ant'
engine = create_engine(SQLALCHEMY_DATABASE_URI)

Base = declarative_base()

Session = sessionmaker(bind=engine)
session = Session()

from datetime import datetime, timedelta

import os.path

import time
start = time.time()

application = Flask(__name__)
#app.run(port=80)
api = Api(application)

isDevMode = 0

#image_root_dir = "/mnt/antweb/images"
# Now, in the /root/antweb/data/images directory on the server and
# in the /Users/mark/volumes/antweb/data/images directory on mark's dev machine.
image_root_dir = "../../data/images"

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
    #dbUrl = OLD_PROTOCOL + config.get('DB', 'user') + ':' + config.get('DB', 'password') + '@' + config.get('DB', 'host') + ":" + config.get('DB', 'port') + '/' + config.get('DB', 'db')
    dbUrl = PROTOCOL + config.get('DB', 'user') + ':' + config.get('DB', 'password') + '@' + config.get('DB', 'host') + ":" + config.get('DB', 'port') + '/' + config.get('DB', 'db')

    isDevMode = 1 # This will not execute deployed on server. This is how we determined devMode.
    if (isDevMode):
      print("isDevMode:" + str(isDevMode))

except Exception as e :
    print('Exception reading configuration file:', e)
    dbUrl = SQLALCHEMY_DATABASE_URI

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
    dir = image_root_dir + '/' + code
    #print(dir)
    isDir = os.path.isdir(dir)
    if (not isDir):
        print('not dir:' + dir)
        return 0

    return 1

class Image(Base):
    __tablename__ = 'image'

    id = Column(String, primary_key=True)
    shotType = Column('shot_type', String)
    code = Column('image_of_id', String)
    uploadDate = Column('upload_date', DateTime)
    shotNumber = Column('shot_number', String)
    hasTiff = Column('has_tiff', String)

    def __repr__(self):
       return "<Image(uid='%s', shotType='%s', code='%s', uploadDate='%s', shotNumber='%s', hasTiff='%s')>" % (
           self.id, self.shotType, self.code, self.uploadDate, self.shotNumber, self.hasTiff)

def findImage(code):
    query = session.query(Image)
    if (code != '*'):
      query = query.filter(Image.code == code)
    
    data = []
    
    try:
        data = query.all()
    except UnicodeEncodeError as uniError:
        print("uniError:" + code)        
    except Exception as e:
        print("images error:", e, e.args)
    
    for image in data:
      return 1
      
    return 0    

def procImages():
    #since = request.args.get('since', default='*', type=str)
    #shotType = request.args.get('shotType', default='*', type=str)
    #code = request.args.get('code', default='*', type=str)
    #limit = request.args.get('limit', default = '10000', type = int)            
    #offset = request.args.get('offset', default = '0', type = int)
    
    since='*'
    shotType='*'
    code='*'
    #limit=100000
    limit=10
    offset=0
    
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
    except Exception as e:
        print("images error:", e, e.args)
    
    lastCode = ""
    specimenCount = 0
    imageCount = 0

    for image in data:
        imageCount = imageCount + 1;
        code = image.code
        if (code != lastCode):      
            # Process this specimen
            #print(image.code)
            specimenCount = specimenCount + 1
            lastCode = code
            retVal = dirExists(code)


    # SELECT uid,shot_type,upload_date,shot_number,has_tiff FROM image WHERE image_of_id=? ORDER BY shot_number ASC     
    if (isDevMode):
        print(query)
    
    return 'specimenCount:' + str(specimenCount) + ' imageCount:' + str(imageCount) 

# ---------------------------------------------------------------------------------------

# Find image directories on disk that are not in the database.
def findOrphanDirs():
    orphanImgs = ''
    orphanDirCount = 0
    for dirname, dirnames, filenames in os.walk(image_root_dir):
        # print path to all subdirectories first.
        for subdirname in dirnames:        
            code = subdirname
            found = findImage(code)
            if not found:
                orphanDirCount = orphanDirCount + 1
                commaStr = ''
                if (orphanDirCount > 1):
                    commaStr = ', '
                orphanImgs = orphanImgs + commaStr + code
                                  
    print("findOrphanDirs() Image directories not found in database. orphanDirCount:" + str(orphanDirCount) + " orphanImages:" + orphanImgs);
 
# ---------------------------------------------------------------------------------------
 
def procSpaceFiles():
    print("Space in the fileName:")
    spaceFileCount = 0
    lastDirName = ''
    #dirNameCount = 0
    for dirName, dirNames, fileNames in os.walk(image_root_dir):

        if (' ' in dirName):
            spacelessDirName = dirName.replace(" ", "")
            if (os.path.exists(spacelessDirName)):  
              print("spacelessDirName already exists:" + spacelessDirName + ". Leaving:" + dirName)          
            else:
              print("Move this directory:" + dirName + " to:" + spacelessDirName)
              #Move the directory
              os.rename(dirName, spacelessDirName)          
              dirName = spacelessDirName
            
        for fileName in fileNames:
            if (' ' in fileName):
                spaceFileCount = spaceFileCount + 1
                spaceFileName = fileName
                spaceFilePath = os.path.join(dirName, spaceFileName)
                print('  ' + spaceFilePath)
                spacelessFilePath = spaceFilePath.replace(" ", "")
                if (os.path.exists(spacelessFilePath)):
                  print("DOES exists spaceless:" + spacelessFilePath + ". Incorrect deleted!")
                  os.remove(spaceFilePath);
                  # Delete the fileName!
                else:
                  print("Correct does NOT exist. Moving to spaceless:" + spacelessFilePath)
                  os.rename(spaceFilePath, spacelessFilePath)
                  # Move the file

    print("spaceFileCount:" + str(spaceFileCount))
 
# ---------------------------------------------------------------------------------------
 
def findIgnoredOrigFiles():
    print("Ignored originals with no derivatives created due to file naming conflict:")
    rootDir = image_root_dir
    ignoredCount = 0
    for dirName, dirNames, fileNames in os.walk(rootDir):
      for fileName in fileNames:    
        ignoredCount += ignoredTest('H', dirName, fileName)
        ignoredCount += ignoredTest('P', dirName, fileName)
        ignoredCount += ignoredTest('L', dirName, fileName)
        ignoredCount += ignoredTest('D', dirName, fileName)
    print("ignoredCount:" + str(ignoredCount))

def ignoredTest(shotType, dirName, fileName):
    #print("dir:" + dirName)            
    ignoredCount = 0
    if (shotType + ".tif" in fileName):
      conflictFile = fileName[0:fileName.find(shotType + '.tif') + 1] + '_1.tif'
      #print('  conflictFile:' + conflictFile + ' fileName:' + fileName)
      conflictFilePath = os.path.join(dirName, conflictFile)
      if (os.path.exists(conflictFilePath)): 
        ignoredCount += 1
        print(os.path.join(dirName, fileName))
    if (shotType + ".jpg" in fileName):
      conflictFile = fileName[0:fileName.find(shotType + '.jpg') + 1] + '_1.jpg'
      #print('  conflictFile:' + conflictFile + ' fileName:' + fileName)
      conflictFilePath = os.path.join(dirName, conflictFile)
      if (os.path.exists(conflictFilePath)): 
        ignoredCount += 1
        print(os.path.join(dirName, fileName))    
    return ignoredCount  

# ---------------------------------------------------------------------------------------

def findSmallImages():
    base = "http://www.antweb.org/images/"
    smallImageCount = 0
    for dirName, dirNames, fileNames in os.walk(image_root_dir):
        for fileName in fileNames:
            filePath = os.path.join(dirName, fileName)
            size = os.stat(filePath).st_size
            if ( \
                #(('.tif' in fileName and '_high' in fileName and not '_l_' in fileName) and size < 32000)
                (('.jpg' in fileName and '_high' in fileName and not '_l_' in fileName) and size < 32000) \
                  or (('.jpg' in fileName and '_high' in fileName and '_l_' in fileName) and size < 20000)
                ):
                smallImageCount = smallImageCount + 1
                code = fileName[0:fileName.find('_')]
                print(base + code + '/' + fileName + " size:" + str(size))      
    print("findSmallImages:" + str(smallImageCount))
 
 # ---------------------------------------------------------------------------------------

def findLowerCaseTifs():
    base = "http://www.antweb.org/images/"
    count = 0
    for dirName, dirNames, fileNames in os.walk(image_root_dir):
        for fileName in fileNames:
            filePath = os.path.join(dirName, fileName)
            if ( \
                #(('.tif' in fileName and '_high' in fileName and not '_l_' in fileName) and size < 32000)
                ('.tif' in fileName)
                ):
                code = fileName[0:fileName.find('_')]
                tail = fileName[fileName.find('_'):fileName.find('.tif')]
                if (tail.islower() or code.islower()): 
                  capitalizedFilePath = os.path.join(dirName, code.upper() + tail.upper() + ".tif")
                  #print("upper:" + capitalizedFilePath)
                  if (not os.path.exists(capitalizedFilePath)):  
                    print("mv " + dirName +  '/' + fileName + " " + capitalizedFilePath)      
                    count = count + 1
    print("findLowerCaseTifs:" + str(count))

def findLowerCaseOrigJpgs():
    base = "http://www.antweb.org/images/"
    count = 0
    for dirName, dirNames, fileNames in os.walk(image_root_dir):
        for fileName in fileNames:
            filePath = os.path.join(dirName, fileName)
            if ( \
                #(('.tif' in fileName and '_high' in fileName and not '_l_' in fileName) and size < 32000)
                ('.jpg' in fileName)
                and not "high" in fileName and not "med" in fileName and not "low" in fileName and not "thumbview" in fileName
                ):
                code = fileName[0:fileName.find('_')]
                tail = fileName[fileName.find('_'):fileName.find('.jpg')]
                if (tail.islower()):   #code.islower() and 
                  capitalizedFilePath = os.path.join(dirName, code.upper() + tail.upper() + ".jpg")
                  #print("upper:" + capitalizedFilePath)
                  if (not os.path.exists(capitalizedFilePath)):  
                    print("mv " + dirName +  '/' + fileName + " " + capitalizedFilePath)      
                    count = count + 1
    print("findLowerCaseJpgs:" + str(count))

 
#-------------------------------------Verify Images --------------------------------

# Fetch from DB and confirm on disk.
def verifyImages():

    since='*'
    shotType='*'
    code='*'
    #code = 'casent0623849'
    # code = 'casent0005904'
    code = 'antweb1008568'
    limit='*' #10
    offset=0
        
    query = session.query(Image)
    if (since != '*'):
      day_interval_before = datetime.now() - timedelta(days=int(since))  
      query = query.filter(Image.uploadDate >= day_interval_before)
    if (shotType != '*'):
      query = query.filter(Image.shotType == shotType)
    if (code != '*'):
      query = query.filter(Image.code == code)
    if (limit != '*'):
      query = query.limit(limit)
    query = query.offset(offset)    

    try:
        data = query.all()
#    except OperationalError as error:
#        message = "images operational error:" + str(error) + " on request:" + str(request)
#        print(message)
#        return message
    except:
        message = "images error:" + str(request)
        print(message) # + error.orig.message, error.params
        return
    
    imageCount = 0
    missingImageCount = 0;
    for image in data:
        imageCount += 1
        c = 0

        c = verifyTif(image)
        c += verifyDerivatives(image)
        #print("verify:" + str(image))
        missingImageCount = missingImageCount + c

    print("verifyImages() imageCount:" + str(imageCount) + " missingImageCount:" + str(missingImageCount) + " code:" + image.code + " num:" + str(image.shotNumber))

# Antweb imageCount:214710  Those without Tifs:70858
def verifyTif(image):
    missingImageCount = 0
    base = image_root_dir + '/' + image.code + '/'
    shotName =  image.code + '_' 
    file1 = base + shotName.upper() + image.shotType.upper() + '.tif'
    
    #file2 = base + shotName.upper() + image.shotType.upper + '_1.tif'
    file2 = ""

    is1 = os.path.exists(file1)
    #is2 = os.path.exists(file2)
    is2 = 0
    c = 0

    if (not is1 and not is2):          
      missingImageCount = missingImageCount + 1
      print("verifyTif() missing:" + file1 + " or file2:" + file2)
      return missingImageCount
    return 0

# imageCount:214710 missingImageCount:44
def verifyDerivatives(image):
    missingImageCount = 0
    base = image_root_dir + '/' + image.code + '/' + image.code + '_' + image.shotType + '_' + str(image.shotNumber)
    files = [base + '_low.jpg' \
      , base + '_med.jpg'
      , base + '_high.jpg'
      , base + '_thumbview.jpg'
    ]
    for fileName in files:
      if not os.path.exists(fileName):
        missingImageCount += 1
        print("verifyDerivatives() missing file:" + fileName)
    return missingImageCount


# ----------------------------------------------------------------------------------------

def regenerateDerivatives():

    since='*'
    shotType='*'
    code='*'
    #code = 'casent0623849'
    # code = 'casent0005904'
    code = 'antweb1008568'
    limit='*' #10
    offset=0

    query = session.query(Image)
    if (since != '*'):
      day_interval_before = datetime.now() - timedelta(days=int(since))
      query = query.filter(Image.uploadDate >= day_interval_before)
    if (shotType != '*'):
      query = query.filter(Image.shotType == shotType)
    if (code != '*'):
      query = query.filter(Image.code == code)
    if (limit != '*'):
      query = query.limit(limit)
    query = query.offset(offset)

    try:
        data = query.all()
#    except OperationalError as error:
#        message = "images operational error:" + str(error) + " on request:" + str(request)
#        print(message)
#        return message
    except:
        message = "images error:" + str(request)
        print(message) # + error.orig.message, error.params
        return

    imageCount = 0
    missingImageCount = 0;
    for image in data:
        imageCount += 1
        c = 0

        c += regenForImage(image)
        #print("verify:" + str(image))
        missingImageCount = missingImageCount + c

        break

    print("regenerateDerivatives() imageCount:" + str(imageCount) + " missingImageCount:" + str(missingImageCount) + " code:" + image.code + " num:" + str(image.shotNumber))

def regenForImage(image):
    count = 0
    base = image_root_dir + '/' + image.code + '/' + image.code + '_' + image.shotType + '_' + str(image.shotNumber);
    files = [base + '_low.jpg' \
      , base + '_med.jpg'
      , base + '_high.jpg'
      , base + '_thumbview.jpg'
    ]
    for fileName in files:
      i = 0
      print("regenForImage() file:" + fileName)
      #resizeImage(fileName, 1000, 800)
      resizeImage('../../data/images/antweb1008568/','ANTWEB1008568_D_2.tif') # 'antweb1008568_p_3_low.jpg')
      i = i + 1
      if (i > 0):
       break


    return count

# From example 14 here: https://www.programcreek.com/python/example/82689/wand.image.Image
def resizeImage(path, width, height):
    filename_without_extension, extension = os.path.splitext(path)

    with Image(filename=path) as src:
        img = src.clone()

    current_aspect_ratio = img.width / img.height

    if not width:
        width = int(current_aspect_ratio * height)

    if not height:
        height = int(width / current_aspect_ratio)

    desired_aspect_ratio = width / height

    # Crop the image to fit the desired AR
    if desired_aspect_ratio > current_aspect_ratio:
        newheight = int(img.width / desired_aspect_ratio)
        img.crop(
            0,
            int((img.height / 2) - (newheight / 2)),
            width=img.width,
            height=newheight,
        )
    else:
        newwidth = int(img.height * desired_aspect_ratio)
        img.crop(
            int((img.width / 2) - (newwidth / 2)), 0, width=newwidth, height=img.height,
        )

    img.resize(width, height)

    return img

def resizeImage(path, fileName):
  #!/usr/bin/env python
  # encoding: utf-8
  from wand.image import Image

  pathFile = path + fileName

  with Image(filename=pathFile) as img:
    print ('format:' + img.format)
    with img.convert('jpeg') as converted:
        converted.save(filename=path + 'tOut.jpg')
        converted.resize(72,72)
        converted.save(filename='tOutputimage_thumbnail.jpg')



# ----------------------------------------------------------------------------------------
  
if __name__ == "__main__": 
    #print(procImages())

    #procSpaceFiles()

    #findOrphanDirs()

    #findSmallImages()

    #findLowerCaseTifs()
    #findLowerCaseOrigJpgs()
   
    #verifyImages()

    regenerateDerivatives()

    #findIgnoredOrigFiles()
    
    
end = time.time()
seconds = end - start
if seconds < 60:
  print('Seconds elapsed: ' + str(seconds))
else :
  minutes = seconds / 60;
  print('Minutes elapsed: ' + str(minutes))
      