# sudo bash
#
# python3.6 dbPull.py > /home/antweb/dbPull.log
#   Testing on Dev machine:
# python3.6 dbPull.py > dbPull.log
#   or on antweb-api:
# sudo /usr/local/bin/python3.6 /home/antweb/antweb_deploy/api/dbPull.py > /home/antweb/dbPull.log


from urllib import request, parse


import logging
import os

logging.basicConfig(filename='example.log',level=logging.DEBUG, format='%(asctime)s %(message)s')

fileLoc = '/data/antweb/web/db/ant-currentDump.sql.gz'

def fetchDbDump():
  # OOPS! We don't need to fetch the db dump. The fileLoc is a shared drive!

  # remove the current ant-currentDump.sql.gz
  try:
    os.remove(fileLoc)
    print('removed existing:' + fileLoc)
  except Exception as e:
    print("exception e:" + str(e))  

  # fetch the new ant-currentDump.sql.gz  
  fileUrl = 'http://www.antweb.org/web/db/ant-currentDump.sql.gz'
  command = "wget " + fileUrl + " ."
  #call(["wget", url, '.'])

  import urllib.request
  try:
    urllib.request.urlretrieve(fileUrl, filename=fileLoc)
    print('Db dump fetched.')
  except Exception as e:
    print("exception e:" + str(e) + " fileUrl:" + fileUrl)    
 


def loadDbDump():

  try:
    dbLoadCommandShort = 'gunzip < ' + fileLoc + ' | mysql -u antweb -p'
    dbLoadCommand = dbLoadCommandShort + 'f0rm1c6' + ' ant'
    print("command:" + dbLoadCommandShort + ' ant')
    retVal = os.system(dbLoadCommand)
    print('loading database retVal:' + str(retVal))
  except Exception as e:
    print("exception e:" + str(e)) 

#fetchDbDump()

loadDbDump()
    