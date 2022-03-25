package org.calacademy.antweb;

import java.util.*;
import java.io.*;

import com.zonageek.jpeg.ExifBlock;
import com.zonageek.jpeg.Jpeg;
import com.zonageek.jpeg.JpegException;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

/** Class SpecimenImage keeps track of the information about a specimen image */
public class SpecimenImage implements Serializable {

    private static final Log s_log = LogFactory.getLog(SpecimenImage.class);

    public static final String DRACULA = "/images/casent0435930/casent0435930_h_1_med.jpg";
    public static String TEXTURED = "/images/casent0171158/casent0171158_h_1_med.jpg";
     
    private static final String image_root = "/images";
    protected String code;
    protected String shot;
    protected int number;
    protected String highResJpgPath;
    protected String artist = "April Nobile";
    protected Artist artistObj;
    protected String date;
    protected String description;
    protected String copyright;  // "California Academy of Sciences, 2000-2010";
    protected boolean hasTiff = false;
    private int groupId = 0;
    private int artistId = 0;
    private int uploadId = 0;

	public void setGroupId(int groupId) {
	  this.groupId = groupId;
	}
	public int getGroupId() {
	  return groupId;
	}
	public Group getGroup() {
	    return GroupMgr.getGroup(groupId);
	}
			
	public int getArtistId() {
		return artistId;
	}
	public void setArtistId(int artistId) {
		this.artistId = artistId;
	}
	public Artist getArtist() {
		return ArtistMgr.getArtist(artistId);
	}
	
	public void setUploadId(int uploadId) {
	  this.uploadId = uploadId;
	}
	public int getUploadId() {
	  return uploadId;
	}	
	
	public String getCopyright() {
		return copyright;
	}
	public void setCopyright(String copyright) {
		this.copyright = copyright;
	}

	public String getDate() {
		return date;
	}
	public void setDate(String date) {
		this.date = date;
	}

    public String getCode() {
      return code;
    }
    public void setCode(String code) {
      this.code = code;
    }

    // Was shot. ShotType to be consistent with DB. Shot is deprecated.
    public String getShotType() {
        return shot;
    }
    public void setShotType(String shotType) {
        this.shot = shotType;
    }

    public String getShot() {
        return shot;
    }
    public void setShot(String shot) {
        this.shot = shot;
    }

    // To be Consistent with DB, should be this.
    public int getShotNumber() {
        return number;
    }
    public void setShotNumber(int number) {
        this.number = number;
    }

    public int getNumber() {
        return number;
    }
    public void setNumber(int number) {
        this.number = number;
    }

    public String getThumbPath() { return AntwebProps.getDocRoot() + getThumbview(); }
    public String getLowPath() { return AntwebProps.getDocRoot() + getLowres(); }
    public String getMedPath() { return AntwebProps.getDocRoot() + getMedres(); }
    public String getHighPath() { return AntwebProps.getDocRoot() + getHighres(); }

	private String getTiffUrl() {
        String path = null;

        String withNum = image_root + "/" + code + "/" + code.toUpperCase() + "_" + shot.toUpperCase()  + "_" + number + ".tif";
        String withoutNum = image_root + "/" + code + "/" + code.toUpperCase() + "_" + shot.toUpperCase()  + ".tif";

        // For performance sake, when done with the debugging move this into the else statement.
        String docRoot = Utility.getDocRoot();

        if (number > 1) {
          path = withNum;
        } else {
          if (AntwebUtil.fileFound(docRoot + withNum)) {
              path = withNum;
          } else {
              path = withoutNum;            
          }
        }

        String docPath = docRoot + path;

        // Find the incorrectly flagged (has_tif == 1 in database). Remove code here and above.
        boolean found = AntwebUtil.fileFound(docPath);
        //A.log("getTiffPath() found:" + found + " + path:" + path);
        if (!found) {
          String testPath = getHighResJpgPath();
          //A.log("getTiffPath() test:" + testPath);
          if (AntwebUtil.fileFound(testPath)) {   // REMOVE this check for performance
            path = testPath;
            LogMgr.appendLog("OJNotOT.txt", testPath);
            //ImageUtil.getNotTifList().add(this);
            // Update the is_tif flag in the images table. This is an error condition.
            // Resolve the root cause existing in existing codebase.
          }
        }

        s_log.debug("getTiffPath() found:" + found + " docPath:" + docPath);

        return path;
	}

	private String getHighResJpgUrl() {
        String withNum = image_root + "/" + code + "/" + code.toUpperCase() + "_" + shot.toUpperCase()  + "_" + number + ".jpg";
        String withoutNum = image_root + "/" + code + "/" + code.toUpperCase() + "_" + shot.toUpperCase()  + ".jpg";

        if (number > 1) {
          return withNum;
        } else {
            String docRoot = Utility.getDocRoot();
            if (AntwebUtil.fileFound(docRoot + withNum)) {
                return withNum;
            } else {
                return withoutNum;
            }
        }
    }

    private String getOrigPath() {
        if (getHasTiff()) {
            return getTiffPath();
        } else {
            return getHighResJpgPath();
        }
    }

    public boolean isHasTiff() {
        return hasTiff;
    }
	public boolean getHasTiff() {
	  return isHasTiff();
    }
	public void setHasTiff(boolean hasTiff) {
		this.hasTiff = hasTiff;
	}

    public String getOrigUrl() {
      if (isHasTiff()) {
        return getTiffUrl();
      } else {
          return getHighResJpgUrl();
      }
    }

    private String getTiffPath() {
      return AntwebProps.getDocRoot() + getTiffUrl();
    }
    private String getHighResJpgPath() {
      return AntwebProps.getDocRoot() + getHighResJpgUrl();
    }

    public ArrayList<String> getOrigAndDerivPaths() {
      ArrayList<String> paths = new ArrayList<>();
      //String imgPath = "/data/antweb";
      String imgPath = AntwebProps.getDocRoot();  // Probably has extra slash. Remove from get methods.
      paths.add(imgPath + getOrigPath());
      paths.add(imgPath + getThumbview());
      paths.add(imgPath + getLowres());
      paths.add(imgPath + getMedres());
      paths.add(imgPath + getHighres()); 
      //A.log("getOrigAndDerivPaths() paths:" + paths);
      return paths;     
    }

    public String getThumbview() {
        if (code == null || code.equals("") || shot == null || shot.equals("")) return null;
        return image_root + "/" + code + "/" + code + "_" + shot + "_" + number + "_thumbview.jpg";
    }
    public String getHighres() {
        if (code == null || code.equals("") || shot == null || shot.equals("")) return null;
        return image_root + "/" + code + "/" + code + "_" + shot + "_" + number + "_high.jpg";
    }
    public String getMedres() {
        if (code == null || code.equals("") || shot == null || shot.equals("")) return null;
        return image_root + "/" + code + "/" + code + "_" + shot +  "_" + number + "_med.jpg";
    }
    public String getLowres() {
        if (code == null || code.equals("") || shot == null || shot.equals("")) return null;
        return image_root + "/" + code + "/" + code + "_" + shot +  "_" + number + "_low.jpg";
    }

    public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
	

    public void setMetadata() {
	  	String docRoot = Utility.getDocRoot();
	  	
    	String imageName = docRoot + getMedres();
    	Formatter formatter = new Formatter();
    	 
    	Jpeg jpeg = new Jpeg();
    	try {
			jpeg.read(new FileInputStream(imageName));
		} catch (FileNotFoundException e) {
			s_log.error("setMetadata() 1 e:" + e + " imageName:" + imageName);
			AntwebUtil.logStackTrace(e);
		} catch (IOException e) {
			s_log.error("setMetadata() 2 e:" + e + " imageName:" + imageName);
			AntwebUtil.logStackTrace(e);
		} catch (JpegException e) {
			s_log.error("setMetadata() 3 e:" + e + " imageName:" + imageName);
			AntwebUtil.logStackTrace(e);
		}
    	
        ExifBlock exif = jpeg.getExifBlock();
    	if (exif != null) {
    		HashMap theFields = exif.getExifFields();
    		if (theFields.containsKey("Artist")) {
    			artist = (String) exif.getExifField("Artist");
                if (artist != null && artist.length() > 0) {
                    artist = artist.trim();
                }
    		}
         
    		if (theFields.containsKey("Copyright")) {
                copyright = (String) exif.getExifField("Copyright");
                if (copyright != null && copyright.length() > 0) {
                    copyright = copyright.trim();
                } 
    		}
    		
    		if (theFields.containsKey("ImageDescription")) {
     			description = (String) exif.getExifField("ImageDescription");
     			if (description != null && description.length() > 0) {
     				description = description.trim();
     			} 
    		}
    		
    		if (theFields.containsKey("DateTime")) {
                String dateTime = (String) exif.getExifField("DateTime");
                if (dateTime != null && dateTime.length() > 0) {
                    dateTime = dateTime.trim();
                    int space = dateTime.indexOf(" ");
                    if (space != -1) {
                        date = dateTime.substring(0, space);
                    } else {
                        date = dateTime;
                    }
                    String[] stuff = date.split(":");
                    date = stuff[1] + "/" + stuff[2] + "/" + stuff[0];
                }          			
    		}
    	}
    }
    
    public String getOrigFileData() {
      String origFileData = null;
      try {
          s_log.debug("origFilePath:" + getOrigPath() + " origFileData:" + origFileData);
          origFileData = FileUtil.getFileAttributesHtml(getOrigPath());
          return origFileData;
      } catch (Exception e) { // java.nio.file.NoSuchFileException is not explicitly thrown
          s_log.warn("getOrigFileData() fileNotFound:" + getOrigPath());
      }
      return null;
    }
    public String getHighResData() {
      return FileUtil.getLastModified(AntwebProps.getDocRoot() + getHighres());    
    }    
    public String getMedResData() {
      return FileUtil.getLastModified(AntwebProps.getDocRoot() + getMedres());    
    }    
    public String getLowResData() {
      return FileUtil.getLastModified(AntwebProps.getDocRoot() + getLowres());    
    }    
    public String getThumbData() {
      //A.log("getThumbData() file:" + AntwebProps.getDocRoot() + getThumbview());
      return FileUtil.getLastModified(AntwebProps.getDocRoot() + getThumbview());    
    }
	public String getShotText() {
		String result = "";
		String shot = getShot();
        if (shot == null) {
            return result;
        } else if (shot.contains("h")) {
            result = "head";
        } else if (shot.contains("p")) {
            result = "profile";
        } else if (shot.contains("l")) {
            result = "label";
        } else if (shot.contains("d")) {
            result = "dorsal";
        } else if (shot.contains("v")) {
            result = "ventral";
        }
		return result;
	}	
	
	public String toString() {
	  return "SpecimenImage(c:" + code + ", s:" + getShot() + ", n:" + getNumber() + ")"; 
	}
    
}
