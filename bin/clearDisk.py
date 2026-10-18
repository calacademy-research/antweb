
# To be execute:
#   python3 clearDisk.py

import os
#from shutil import copyfile
import shutil
from datetime import date
import datetime as DT
import math

#inDir = '/Users/mark/mark/media/assorted/'
#inDir = '/Volumes/ReSeagate/Final\ Cut\ Libraries/'
inDirs = [
    '/usr/local/tomcat/logs/' \
 # , '/usr/local/tomcat/logs/detail' \
  ]

# Never walked: the app's own web/log (host ./logs/web, mounted at
# /usr/local/antweb/web/log), which tomcat owns and logrotate manages
# (docker/antweb/logrotate.conf). Links are never followed or deleted.
SKIP_DIRS = {'web'}

# -------------------------------------------------------------

def walkLogs(directory):
    """(dirpath, name) of the regular files under directory, skipping SKIP_DIRS at its top
    and any symbolic link (to a folder or a file)."""
    top = os.path.normpath(directory)
    for dirpath, dirnames, files in os.walk(top, followlinks=False):
        keep = []
        for d in dirnames:
            if os.path.normpath(dirpath) == top and d in SKIP_DIRS:
                continue
            if os.path.islink(os.path.join(dirpath, d)):
                continue
            keep.append(d)
        dirnames[:] = keep
        for name in files:
            path = os.path.join(dirpath, name)
            if os.path.islink(path) or not os.path.isfile(path):
                continue
            yield dirpath, name

def findFiles(directory):
    print("\nFinding files in " + directory)
    for dirpath, name in walkLogs(directory):
        if isLogOlderThan(name) or isNumberedLogFile(name):
            deleteFile(dirpath, name)

def isNumberedLogFile(name):
    #print("fileOlderThan name:" + name)
    if 'log.9' in name or 'log.8' in name or 'log.7' in name or 'log.6' in name \
      or 'log.5' in name or 'log.4' in name or 'log.3' in name: 
      #print("Numbered log file:" + name)
      return True
    return False  
    #if 'log.2' in name: return True  #including this would match with _log.2020

    #today = date.today()
    #d4 = today.strftime("%Y-%m-%d")
    #print("d4 =", d4)

def isLogOlderThan(name):
    if not ".log" in name.lower() and not ".txt" in name.lower(): return False

    today = DT.date.today()
    daysAgo7 = today - DT.timedelta(days=7)
    daysAgo8 = today - DT.timedelta(days=8)
    daysAgo9 = today - DT.timedelta(days=9)
    daysAgo10 = today - DT.timedelta(days=10)
    daysAgo11 = today - DT.timedelta(days=11)
    daysAgo12 = today - DT.timedelta(days=12)
    daysAgo13 = today - DT.timedelta(days=13)
    daysAgo14 = today - DT.timedelta(days=14)

    if str(daysAgo7) in name or str(daysAgo8) in name or str(daysAgo9) in name \
      or str(daysAgo10) in name or str(daysAgo11) in name or str(daysAgo12) in name \
      or str(daysAgo13) in name or str(daysAgo14) in name \
      : 
      #print("Over a week ago by name:" + name)
      return True
    else:
      pass #print("NO! weekago" + str(weekAgo) + " is NOT in name:" + name)
    #print("week ago:", weekAgo)
    return False

def deleteFile(dirpath, name):
	inFile = os.path.join(dirpath, name)
	#print('delete ' + inFile)
	try:
	  if not os.path.islink(inFile):
	    os.remove(inFile)
	except OSError as e:
	  print ('Not deleted:' + name + ' ' + str(e))

def getSize(start_path = '.'):
    total_size = 0
    for dirpath, name in walkLogs(start_path):
        try:
            total_size += os.path.getsize(os.path.join(dirpath, name))
        except OSError:
            pass
    return total_size

def convertSize(size_bytes):
   if size_bytes == 0:
       return "0B"
   size_name = ("B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
   i = int(math.floor(math.log(size_bytes, 1024)))
   p = math.pow(1024, i)
   s = round(size_bytes / p, 2)
   return "%s %s" % (s, size_name[i])          
          
def purgeOldBakDir():
    today = DT.date.today()
    
    # If in April or later, will delete prior year's backup data.
    manyMoons = today - DT.timedelta(15*365/12)    
    year = today.strftime("%Y")
    oldYear = int(year) - 1
    if str(oldYear) in str(manyMoons):
      oldDir = '/usr/local/antweb/web/log/bak/' + str(oldYear)
      print("Deleting oldDir:" + oldDir)
      try:
        shutil.rmtree(oldDir)
      except FileNotFoundError:
        print ("file not found:" + oldDir)
      except OSError as e:
        print ("not deleted:" + oldDir + " " + str(e))
# -----------------------------          

def main():
  print('Running clearDisk.py.' + "\n")
  purgeOldBakDir()

  for inDir in inDirs:
    beforeBytes = getSize(inDir)
    findFiles(inDir)
    afterBytes = getSize(inDir)
    print("dir:" + inDir + " before:" + str(convertSize(beforeBytes)) + " afterBytes:" + str(convertSize(afterBytes)) + "M")

  print('\n')

if __name__ == '__main__':
  main()
