#!/usr/bin/env python3

# Can be executed in local environment (in antweb/src/py directory) as such:
#     python3 boltonNgc.py "Bolton NGC Species 2019-Aug.txt"
#
# output file created in current directory.
# boltonNgc.txt is the output file and omitBoltonNgc.txt contains all of the reasons for line omission.

import sys
 
# ---------------------------------------------------------------------------------------
       
def process():

  #inFileLoc = "/Users/mark/Downloads/antweb/Bolton NGC Species 2019-Aug.txt"
  inFileLoc = sys.argv[1]
  print("Parsing:" + inFileLoc)
    
  outFileLoc = 'boltonNgc.txt'
  outFile = open(outFileLoc, 'w', encoding = "ISO-8859-1")

  omitFile = open("omitBoltonNgc.txt", 'w', encoding = "ISO-8859-1")
  
  outFile.write("species group name" + "\t" + "protonym" + "\t" + " Author" + "\n")

  lineNum = 0
  outLineNum = 0

  for line in open(inFileLoc, 'r', encoding = "ISO-8859-1"):

    lineNum = lineNum + 1    
    #print("line:" + str(lineNum) + " " + line)
    
    #if (lineNum > 999):
    #  break

    line = line.strip()

    speciesGroupName = ""
    periodIndex = line.find(".")
    #print("periodIndex:" + str(periodIndex))
    if (periodIndex > 0):
      speciesGroupName = line[0: periodIndex]
    
    if len(speciesGroupName) <= 1:
      omitFile.write(str(lineNum) + " speciesGroupName too short:" + speciesGroupName + "\n")
      continue
      
#    i = speciesGroupName.find("*")   #allow these. Indicators of fossils.
#    if (i >= 0):
#      omitFile.write(str(lineNum) + " Species Group starts with asterix (skip):" + speciesGroupName + "\n")
#      continue
    if (speciesGroupName.find(" ") >= 0):
      omitFile.write(str(lineNum) + " Species Group has space (skip):" + speciesGroupName + "\n")
      continue      
    if (speciesGroupName[0:1].isupper()):
      omitFile.write(str(lineNum) + " Species Group Name is Upper (skip):" + speciesGroupName + "\n")
      continue

    protonym = ""
    details = ""

    # Get the digit position. Find the paren after the first digit (to avoid sub-genera?)
    for i, c in enumerate(line):
        if c.isdigit():
            # print("digit:" + str(i))
            break
    parenIndex = line.find("(", i)
    if (parenIndex > 0):
      details = line[periodIndex + 2: parenIndex]

    # Break the details down into protonym and author.      
    author = ""  
    for i, c in enumerate(details):
        withinParens = details[0:i].find("(") > 0 and details[0:i].find(")") <= 0
        if i > 1 and c.isupper() and not withinParens:
            protonym = details[0:i]
            author = details[i:]
            #print("next upper:" + str(i) + " author is:" + details[i] + " details:" + details)
            break

    #if (lineNum == 14169):
    #  print("14169 - parenIndex:" + str(parenIndex) + " i:" + str(i) + " protonym:" + protonym + " author:" + author)      
      
    if (not protonym[0:1].isupper()):
      if (not "" == protonym[0:1]):
        pass
        #print(str(lineNum) + " Protonym not upper (skip):" + protonym + "\tline:" + line)
      continue

    # Truncate the author after the comma after the colon. 
    colonPos = author.find(":")
    commaPos = author.find(",", colonPos)
    pos = 0
    if (commaPos > 0):
      pos = commaPos
    periodPos = author.find(".", colonPos)
    if (periodPos > 0 and (periodPos < commaPos or commaPos <= 0)):
      pos = periodPos
    if (pos > 0):
      author = author[0: pos]
    #print("author:" + author)  

    #if (lineNum == 14169):
    #  print("14169 - author:" + author + " commaPos:" + str(commaPos) + " periodPos:" + str(periodPos) + " pos:" + str(pos))

    #outLine = str(lineNum) + ": " + speciesGroupName + "\t" + protonym + "\t" + author + "\n"
    outLine = speciesGroupName + "\t" + protonym + "\t" + author + "\n"
    #outLine = str(lineNum) + ":speciesGroupName:" + speciesGroupName + "\tprotonym:" + protonym + "\tauthor:" + author

    #if (lineNum == 28570):
      #print(str(lineNum) + " " + outLine)
    
    #if (lineNum < 1000):
    #  print (outLine)

    outFile.write(outLine)

    outLineNum = outLineNum + 1
  #  line = f1.readline()

  print("parsed lines:" + str(lineNum) + " outputLines:" + str(outLineNum) + " outFileLoc:" + outFileLoc)
  
# ---------------------------------------------------------------------------------------
 
# This will get executed...  
if __name__ == "__main__": 
    
    process()
