from sqlalchemy import Column, Integer, String

from sqlalchemy.ext.declarative import declarative_base
Base = declarative_base()

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
