from sqlalchemy import Column, Integer, String

from sqlalchemy.ext.declarative import declarative_base
Base = declarative_base()

class TaxaImage(Base):
    __tablename__ = 'taxon_image'

    taxonName = Column('taxon_name', String)
    subfamily = Column('subfamily', String)
    genus = Column('genus', String)
    species = Column('species', String)
    subspecies = Column('subspecies', String)
    code = Column('code', String)
    uid = Column('uid', Integer, primary_key=True)
    uploadDate = Column('upload_date', String)
    shotType = Column('shot_type', String)
    shotNumber = Column('shot_number', String)
    hasTiff = Column('has_tiff', String)

    def __repr__(self):
       return "<TaxaImage(taxonName='%s', subfamily='%s', genus='%s', species='%ds', subspecies='%s', code='%s' \
         uid='%s', shotType='%s', code='%s', uploadDate='%s', shotNumber='%s', hasTiff='%s')>" % (
         self.taxonName, self.subfamily, self.genus, self.species, self.subspecies, self.code, \
         self.uid, self.shotType, self.code, self.uploadDate, self.shotNumber, self.hasTiff
    )

    # Rather like a static methods...

    def getTaxaDict(data):
        taxaDict = {}
        for taxaImage in data:
    
            # taxaImage is the object created from the result set.
            #taxaDict is the dictionary containing the hierarchical data taxon - specimen - image.

            # taxaDict is of the format taxonName: {taxonDict}
            if taxaImage.taxonName not in taxaDict:
              taxonDict = {
                'taxonName': taxaImage.taxonName 
              , 'subfamily': taxaImage.subfamily
              , 'genus': taxaImage.genus
              , 'species': taxaImage.species
              , 'subspecies': taxaImage.subspecies    
              , 'specimen': []
              }
              taxaDict[taxaImage.taxonName] = taxonDict
              #print('getTaxaDict() + taxonName:' + taxaImage.taxonName)
            else:
              taxonDict = taxaDict[taxaImage.taxonName]
            #print('getTaxaDict() taxonName:' + taxaImage.taxonName)
            specimens = taxonDict['specimen']    

            specimenDict = TaxaImage.getFromSpecimens(specimens, taxaImage.code)    
            if not specimenDict:
              specimenDict = {
                'code': taxaImage.code
              , 'images': []
              }
              specimens.append(specimenDict)

            imagesDict = specimenDict['images']

            if taxaImage.uid not in imagesDict:
              base = 'https://www.antweb.org/images/' + taxaImage.code + '/' + taxaImage.code + '_' \
                + taxaImage.shotType + '_' + str(taxaImage.shotNumber) 
              urls = [base + '_low.jpg' \
                  , base + '_med.jpg'
                  , base + '_high.jpg'
                  , base + '_thumbview.jpg'
              ]
              imageDict = {
                'imageId': taxaImage.uid
              , 'shotType': taxaImage.shotType
              , "urls:": urls
              , 'uploadDate': taxaImage.uploadDate
              , 'shotNumber': taxaImage.shotNumber
              , 'hasTiff': taxaImage.hasTiff
              }
              imagesDict.append(imageDict)
              #print('getTaxaDict() uid:' + str(taxaImage.uid))
            else:
              imageDict = imagesDict[taxaImage.uid]

        return taxaDict

    def getFromSpecimens(specimens, code):
        for specimenDict in specimens:
          if specimenDict['code'] == code:
            return specimenDict      


    def getTaxonCount(taxaDict):
        return len(taxaDict)

    def getSpecimenCount(taxaDict):
        specimenCount = 0
        for taxonDict in taxaDict:
          val = taxaDict[taxonDict]    
          specimenCount = specimenCount + len(val['specimen'])
        return specimenCount

    def getImageCount(taxaDict):
        imageCount = 0
        for key in taxaDict:
          taxonDict = taxaDict[key]    
          specimens = taxonDict['specimen']
          for specimenDict in specimens:
            specimenImages = specimenDict['images']
            #print("imageCount len:" + str(len(specimenImages)))
            imageCount = imageCount + len(specimenImages)
        return imageCount

    def getDataList(taxaDict):
        dataList = []
        for taxonDict in taxaDict:
          #print("taxonDict: " + str(taxaDict[taxonDict]))
          taxonDict = taxaDict[taxonDict]
          #print("taxonName: " + taxonDict['taxonName'])
          dataList.append(taxonDict) 
        return dataList

