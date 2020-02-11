package org.calacademy.antweb.util;

import com.zonageek.jpeg.ExifBlock;
import com.zonageek.jpeg.Jpeg;

import java.io.*;
import java.util.HashMap;


public class Exif {

  private boolean exifFound = false;

  private String artist = "";
  private String copyright = "";
  private String date = "";   
  private String exifFields = "";

  public Exif(String imagePath) {

    if (imagePath == null) {
      A.slog("image path null");
      return;
    }

	Jpeg jpeg = new Jpeg();    
	try {
      jpeg.read(new FileInputStream(imagePath));
    } catch (java.io.FileNotFoundException e) {
      LogMgr.appendLog("imageNotFound.txt", imagePath);
      AntwebUtil.log("WSS. Exif() exception:" + e + " on " + imagePath);      
      return;
    } catch (com.zonageek.jpeg.JpegException e) {    
      LogMgr.appendLog("zonageeks.txt", imagePath);
      AntwebUtil.log("WSS. Exif() exception:" + e + " on " + imagePath);
      //String reqInfo = HttpUtil.getRequestInfo(request);
      //AntwebUtil.log("  - Request Info:" + reqInfo);
      return;
    } catch (IOException e) {
      AntwebUtil.log("e:" + e);
      return;
    }

    exifFound = true;
    
    String dateTime = "";
  
    if (jpeg != null) {
      ExifBlock exif = jpeg.getExifBlock();
	  if (exif != null) {
		HashMap theFields = exif.getExifFields();
		exifFields = theFields.toString();
		
		if (theFields.containsKey("Artist")) {
			artist = ((String) exif.getExifField("Artist"));
			if ((artist != null) && (artist.length() > 0)) {
				artist = artist.trim();
			}
		}
     
		if (theFields.containsKey("Copyright")) {
			copyright = ((String) exif.getExifField("Copyright"));
			if ((copyright != null) && (copyright.length() > 0)) {
				copyright = copyright.trim();
			} 
		}
		
		if (theFields.containsKey("DateTime")) {
			dateTime = ((String) exif.getExifField("DateTime"));
			if ((dateTime != null) && (dateTime.length() > 0)) {
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
  }
  

  public boolean isFound() {
    return exifFound;
  }
    
  public String getArtist() {
    return artist;
  }

  public String getCopyright() {
    return copyright;
  }
  
  public String getDate() {
    return date;
  }  
  
  public String getFields() {
    return exifFields;
  }
    
  public String toString() {
    return "exifFields:" + exifFields + " artist:" + artist + " date:" + date + " copyright:" + copyright;
  }
  
}