package org.calacademy.antweb.imageUploader;

import java.util.*;
import java.io.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;


import java.io.*;
//import java.util.*;
import org.apache.commons.fileupload.*;
//import org.apache.commons.fileupload.disk.*;
//import org.apache.commons.fileupload.servlet.*;
import org.apache.commons.io.output.*;

import org.im4java.core.*;
import org.im4java.process.*;
//import org.im4java.core.ETOperation

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class ImageUploaded {

  private static Log s_log = LogFactory.getLog(ImageUploaded.class);

  public static String imagesDir = AntwebProps.getDocRoot() + "images/";

  public static String tempDir = imagesDir + "/temp";
  public static String backupDir = imagesDir + "backup/";

  private int id = 0;
  private String fileName = null;  
  private String code = null;  
  private String shot = null;  
  private int number = 0;
  private String ext = null;
  private Date created = null;
  boolean reUploaded = false;
  private String errorMessage = null;
  boolean isSpecimenDataExists = false;
  
  private ImageUpload imageUpload = null;

  public ImageUploaded() { 
  }
    
  public void setFileItem(FileItem fileItem) throws Exception {
      
      if (!getIsContinueUpload()) return;
      
      // Get the uploaded file parameters
      String fieldName = fileItem.getFieldName();
      String fileName = fileItem.getName();
      boolean isInMemory = fileItem.isInMemory();
      long sizeInBytes = fileItem.getSize();

      //A.log("execute() fieldName:" + fieldName + " fileName:" + fileName + " isInMemory:" + isInMemory + " sizeInBytes:" + sizeInBytes);

      // Write the file
      String specimenDir = imagesDir + getCode() + "/";
      FileUtil.makeDir(specimenDir);
      String name = null;
      if( fileName.lastIndexOf("\\") >= 0 ) {                       
         name = fileName.substring(fileName.lastIndexOf("\\"));
      } else {
         name = fileName.substring(fileName.lastIndexOf("\\")+1);
      }
      String fullName = specimenDir + name;
      File file = new File(fullName);
      //s_log.warn("execute() fullName:" + fullName);

      try {
        if (file.exists()) {
          // move the file to the backupdir.
          FileUtil.makeDir(backupDir);
          (new Utility()).copyFile(fullName, backupDir + name);
          setIsReUploaded(true);
        }
      } catch (FileNotFoundException e) {
        A.log(" setFileItem() e:" + e + " backupDir:" + backupDir);
      }
      fileItem.write(file);
  }

  public static String getTestString(String fileName) {
      ImageUploaded iu = new ImageUploaded();
      iu.init(fileName);
      String val = "fileName:" + iu.getFileName() + " getCode:" + iu.getCode() + " shot:" + iu.getShot() + " number:" + iu.getNumber() + " ext:" + iu.getExt();
      return val;
  }

  // From the original file uploaded we derive the code, the shot and number.
  public void init(String fileName) {
    int u1 = 0;
    int u2 = 0;
    int period = 0;
    try {
        if (fileName == null) {
          setErrorMessage("Null filename");
          return;
        }
        setFileName(fileName);
        u1 = fileName.indexOf("_");
        if (u1 <= 0) {
          setErrorMessage("_ and shot type required.");         
          return;
        }

        // handle underscore that could be early in the name.
        period = fileName.indexOf(".");
        if (period - u1 > 5) {
            u1 = fileName.indexOf("_", u1 + 1);
        }

        // handle underscore that could be early in the name.
        if (period - u1 > 5) {
            u1 = fileName.indexOf("_", u1 + 1);
        }

        setCode(fileName.substring(0, u1).toLowerCase());

        String beforePeriod = fileName.substring(0, fileName.indexOf("."));
        if (!beforePeriod.equals(beforePeriod.toUpperCase())) {
          setErrorMessage("Filename must be uppercase"); 
          return;
        }
        
/*
        u2 = fileName.indexOf("_", u1 + 1);
        period = fileName.indexOf(".");
        if (u2 <= 0) {
          setNumber(1);
          String shot = fileName.substring(u1 + 1, period).toLowerCase();
          if (!Arrays.asList(new String[]{"d", "p", "h", "l", "v"}).contains(shot)){
             setErrorMessage("Unsupported shot type");
             return;
          }
          setShot(shot);              
        } else {
          setShot(fileName.substring(u1 + 1, u2).toLowerCase());  
          String num = fileName.substring(u2 + 1, period);
          if (num != null) setNumber(new Integer(num).intValue());
        }
*/                        
        

//        setShot(shot.toLowerCase());         
        
        u2 = fileName.indexOf("_", u1 + 1);
        period = fileName.indexOf(".");
        if (u2 <= 0) {
          setNumber(1);
          String shot = fileName.substring(u1 + 1, period);
          if (shot.contains(" ")){
             setErrorMessage("Shot contains space");
             return;
          }
          if (!Arrays.asList(new String[]{"D", "P", "H", "L", "V"}).contains(shot)){
             setErrorMessage("Unsupported shot type");
             return;
          }
          setShot(shot.toLowerCase());              
        } else {
          setShot(fileName.substring(u1 + 1, u2).toLowerCase());  
          String num = fileName.substring(u2 + 1, period);
          if (num != null) setNumber(Integer.valueOf(num).intValue());
        }
        String ext = fileName.substring(period + 1).toLowerCase();
        ext = ext.toLowerCase();
        //String[] extensions = new String[]{"jpg","tif"};
        if (!Arrays.asList(new String[]{"jpg", "tif", "png"}).contains(ext)) setErrorMessage("Unsupported file type");
        setExt(ext);
        //A.log("populate() fileName:" + fileName + " u1:" + u1 + " u2:" + u2 + " period:" + period + " code:" + getCode() + " shot:" + getShot() + " number:" + getNumber() + " ext:" + ext);

        return;
    } catch (NumberFormatException e) {
      s_log.warn("populate() e:" + e);
    } catch (StringIndexOutOfBoundsException e) {
      s_log.warn("populate() e:" + e);
    }  
    setErrorMessage("Invalid filename");
  }

  public String genImages() {
        String message = "";
        String specimenDir = imagesDir + getCode();
        File specimenDirFile = new File(specimenDir);
        if (!specimenDirFile.exists()) {
          specimenDirFile.mkdir();
          AntwebUtil.log("genImage() creating:" + specimenDir);
        }

        //addMetaData($newFileName, $copyright, $artist, $license, $specimen, $date);
        //AntwebUtil.log("genImage() 1:

        message += genImage(specimenDir, "thumbview", 400, 300);
        message += genImage(specimenDir, "low", 800, 600);
        message += genImage(specimenDir, "med", 1200, 900);
        message += genImage(specimenDir, "high", 2000, 1600);
        return message;
    }

    private String genImage(String specimenDir, String type, int width, int height) {
        String message = "";
        String imageName = null;
        String imagePath = null;
        String tags = "";
        try {
            imageName = getCode() + "_" + getShot() + "_" + getNumber() + "_" + type + ".jpg";

            IMOperation op = new IMOperation();
            op.addImage(imagesDir + getCode() + "/" + getFileName());
            op.resize(height, width);
            imagePath = specimenDir + "/" + imageName;
            op.addImage(imagePath);

            // This would be JMagick. Doesn't work on Mac.
            //setExifData(specimenDir, imageName, code);

            //tags = setExifData(imagePath);

            message = "<br>" + imageName;
            if (!"".equals(tags)) message += " created with tags:" + tags;

            // create and execute the command
            ConvertCmd cmd = new ConvertCmd();
            cmd.run(op);
        } catch (org.im4java.core.CommandException e) {
            AntwebUtil.log("im4java test e:" + e + " imageName:" + imageName + " imagePath:" + imagePath + " tags:" + tags);
        } catch (IOException e) {
            AntwebUtil.log("im4java test 2e:" + e);
        } catch (InterruptedException e) {
            AntwebUtil.log("im4java test 3e:" + e);
        } catch (IM4JavaException e) {
            AntwebUtil.log("im4java test 4e:" + e);
        }  
        return message;
    }
    
/*    
    private String setExifData(String imagePath) {

     //exiftool -comment='this is a new comment' casent0005904_d_1_low.jpg
     //exiftool -comment=wow casent0005904_d_1_low.jpg
     //    1 image files updated
     //exiftool -a -u -g1 casent0005904_d_1_low.jpg

     String tags = "";
     try {
        ETOperation op = new ETOperation();

        op.setTags("Copyright='" + getCopyright() + "'", "Artist=" + getArtist());
        op.setTags("Document='" + getCode() + "'");
        op.setTags("License='" + getLicense() + "'");
        op.setTags("Created='" + getCreated() + "'");

    //Document Name	casent0844155
    //Modify Date	2019:04:18 16:55:27

//        op.getTags("FileName", "ImageWidth", "ImageHeight", "FNumber", "ExposureTime", "iso", "copyright", "License");

        op.addImage();

        // setup command and execute it (capture output)
        ArrayListOutputConsumer output = new ArrayListOutputConsumer();
        ExiftoolCmd et = new ExiftoolCmd();
        et.setOutputConsumer(output);
        et.run(op, imagePath);

        // for debugging. See: /usr/local/tomcat/bin/et.sh
        et.createScript("et.sh",op);

        ArrayList<String> cmdOutput = output.getOutput();
        for (String line:cmdOutput) {
          AntwebUtil.log("setExifData() line:" + line);
          tags += "<br>" + line;
        }
      } catch (IOException e) {
        AntwebUtil.log("setExifData() 1 e:" + e);
      } catch (InterruptedException e) {
        AntwebUtil.log("setExifData() 2 e:" + e);
      } catch (IM4JavaException e) {
        AntwebUtil.log("setExifData() 3 e:" + e);
      }
      return tags;
    }
*/

    public ImageUpload getImageUpload() {
      return imageUpload;
    }
    public void setImageUpload(ImageUpload imageUpload) {
      this.imageUpload = imageUpload;
    }

    public int getId() {
      return id;
    }
    public void setId(int id) {
      this.id = id;  
    }
    
    public String getFileName() {
      return fileName;
    }
    public void setFileName(String fileName) {
      this.fileName = fileName;
    }

    public String getDisplayName() {
      //return getCode() + "_" + getShot() + "_" + getNumber();
      return getFileName();
    }

    
    public String getCode() {
      return code;
    }
    public void setCode(String code) {
      this.code = code;
    }
  
    public String getShot() {
      return shot;
    }
    public void setShot(String shot) {
      this.shot = shot;
    }
  
    public int getNumber() {
      return number;
    }
    public void setNumber(int number) {
      this.number = number;  
    }

    public String getExt() {
      return ext;
    }
    public void setExt(String ext) {
      this.ext = ext;
    }
    
    public int hasTiff() {
      if ("tif".equals(ext) || "tiff".equals(ext)) return 1;
      return 0;
    }
  
    public Date getCreated() {
      return created;
    }  
    public void setCreated(Date created) {
      this.created = created;
    }   
    
    public boolean getIsReUploaded() {
      return reUploaded;
    }
    public void setIsReUploaded(boolean reUploaded) {
      this.reUploaded = reUploaded;
    }

    public boolean getIsContinueUpload() {
      return getErrorMessage() == null && getIsSpecimenDataExists();
    }
    
    public String getErrorMessage() {
      return errorMessage;
    }
    public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
    }

    public boolean getIsSpecimenDataExists() {
      return isSpecimenDataExists;
    }
    public void setIsSpecimenDataExists(boolean isSpecimenDataExists) {
      this.isSpecimenDataExists = isSpecimenDataExists;
    }
}


