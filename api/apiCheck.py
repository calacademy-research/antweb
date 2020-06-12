# sudo /usr/local/bin/python3.6 /antweb/deploy/api/apiCheck.py > /antweb/log/apiCheck.log &

from urllib import request, parse


import logging
logging.basicConfig(filename='example.log',level=logging.DEBUG, format='%(asctime)s %(message)s')

def testServer():
  #url = "http://api.antweb.org/v3/specimens?code=casent0922626"
  #url = "http://localhost:5000/specimens?code=casent0922626"
  url = "http://localhost/v3.1/specimens?code=casent0922626"

  response = ""

  try:
    u = request.urlopen(url)
    resp = u.read()
    response = str(resp)
  except Exception as e:
    print("exception e:" + str(e))

  #print("response:" + response)    

  if (response.startswith("b'{")):
    pass #print("Successful test")
  else:
    message = 'Failed test. Restart'
    logging.warning(message)
    print(message)
    from subprocess import call
    call(["apachectl", "restart"])

    
  #if (response.contains("INTERNAL SERVER ERROR")):
  #    print("restart")

import time 
while True:
    #print("This prints once a minute.")
    time.sleep(60)
    testServer()
