#! /usr/local/bin/python3.6

# Can be execute in local environment (in /Users/mark/dev/calacademy/antweb/src/py directory) as such:
#     python3.6 hitApi.py


from flask import Flask, jsonify, json, request
from flask_restful import Api

from datetime import datetime, timedelta

import json
import urllib.request


application = Flask(__name__)
api = Api(application)
    
version = "V3.1"
localPhpServer = "http://localhost:5000"
localTomcatServer = "http://localhost/antweb"
apiServer = "http://api.antweb.org"
prodServer = "https://www.antweb.org"
apiUri = "api" + version + ".do"
 
# ---------------------------------------------------------------------------------------
       
def getGeolocaleTaxa():

    geolocaleName = "Comoros"
 
    #base = 'http://localhost:5000'
    base = 'http://api.antweb.org/v3.1'
    jsonUrl = base + '/geolocaleTaxa?rank=species&geolocaleName=' + geolocaleName
      
    print("Data for geolocale:" + geolocaleName + " from " + jsonUrl)

    output = json.load(urllib.request.urlopen(jsonUrl))
    #print("json:" + str(output));

    if (geolocaleName != '*'):
      for t in output['geolocaleTaxa']:
        print(" taxonName:"  + str(t['taxonName']))           
  
# ---------------------------------------------------------------------------------------
 
# This will get executed...  
if __name__ == "__main__": 
    
    getGeolocaleTaxa()
