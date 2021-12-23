package org.calacademy.antweb.imageUploader;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.Utility;
import org.calacademy.antweb.util.A;
import org.calacademy.antweb.util.AntwebProps;
import org.calacademy.antweb.util.AntwebUtil;
import org.calacademy.antweb.util.FileUtil;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

//import java.util.*;
//import org.apache.commons.fileupload.disk.*;
//import org.apache.commons.fileupload.servlet.*;
//import org.im4java.core.ETOperation

public class ImageUploaded {

  private static Log s_log = LogFactory.getLog(ImageUploaded.class);

  public static String imagesDir = AntwebProps.getDocRoot() + "images/";

  public static Path tempDir = Path.of(AntwebProps.getDocRoot(), "temp", "images");
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
        s_log.debug(" setFileItem() e:" + e + " backupDir:" + backupDir);
      }
      fileItem.write(file);
  }

  public static String getTestString(String fileName) {
      ImageUploaded iu = new ImageUploaded();
      iu.init(fileName);
      return "fileName:" + iu.getFileName() + " code:" + iu.getCode() + " shot:" + iu.getShot() + " number:" + iu.getNumber() + " ext:" + iu.getExt();
  }

  // From the original file uploaded we derive the code, the shot and number.
  public String init(String fileName) {
    String message = "success";
    int u1;
    int u2;
    int period;
    try {
        if (fileName == null) {
          message = "Null filename";
          setErrorMessage(message);
          return message;
        }
        setFileName(fileName);
        u1 = fileName.indexOf("_");
        if (u1 <= 0) {
          message = "_ and shot type required.";
          setErrorMessage(message);
          return message;
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
          message = "Filename must be uppercase";
          setErrorMessage(message);
          return message;
        }

        u2 = fileName.indexOf("_", u1 + 1);
        period = fileName.indexOf(".");
        if (u2 <= 0) {
          setNumber(1);
          String shot = fileName.substring(u1 + 1, period);
          if (shot.contains(" ")){
             message = "Shot contains space";
             setErrorMessage(message);
             return message;
          }
          if (!Arrays.asList(new String[]{"D", "P", "H", "L", "V"}).contains(shot)){
             message = "Unsupported shot type";
             setErrorMessage(message);
             return message;
          }
          setShot(shot.toLowerCase());
        } else {
          setShot(fileName.substring(u1 + 1, u2).toLowerCase());
          String num = fileName.substring(u2 + 1, period);
          setNumber(Integer.parseInt(num));
        }
        String ext = fileName.substring(period + 1).toLowerCase();
        ext = ext.toLowerCase();
        //String[] extensions = new String[]{"jpg","tif"};
//        if (!Arrays.asList(new String[]{"jpg", "tif", "png"}).contains(ext)) setErrorMessage("Unsupported file type");
        Set<String> valid_extensions = Set.of("jpg", "tif", "png");
        if (!valid_extensions.contains(ext)) setErrorMessage("Unsupported file type");
        setExt(ext);
        //A.log("populate() fileName:" + fileName + " u1:" + u1 + " u2:" + u2 + " period:" + period + " code:" + getCode() + " shot:" + getShot() + " number:" + getNumber() + " ext:" + ext);

        return message;
    } catch (NumberFormatException e) {
      s_log.warn("populate() e:" + e);
    } catch (StringIndexOutOfBoundsException e) {
      s_log.warn("populate() fileName:" + fileName + " e:" + e);
    }
    message = "Invalid filename:" + fileName;
    setErrorMessage(message);
    return message;
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

        message += genImage(specimenDir, "thumbview", 400, 300);
        message += genImage(specimenDir, "low", 800, 600);
        message += genImage(specimenDir, "med", 1200, 900);
        message += genImage(specimenDir, "high", 2000, 1600);
        return message;
    }

    private String genImage(String specimenDir, String type, int width, int height) {
        String message = "";
        String imageName = getCode() + "_" + getShot() + "_" + getNumber() + "_" + type + ".jpg";
        String imagePath = specimenDir + "/" + imageName;
        Path tempFile = tempDir.resolve(imageName);
        String tags = "";
        try {
            IMOperation op = new IMOperation();
            op.addImage(imagesDir + getCode() + "/" + getFileName());
            op.flatten();
            op.resize(height, width);
            op.addImage(tempFile.toString());

            // This would be JMagick. Doesn't work on Mac.
            //setExifData(specimenDir, imageName, code);

            //tags = setExifData(imagePath);

            message = "<br>" + imageName;
            if (!"".equals(tags)) message += " created with tags:" + tags;

            // create and execute the command
            //A.log("genImage() path:" + imagePath + " this:" + this.toString());
            //A.log("genImage() 1 EXISTS? " + FileUtil.fileExists("/usr/local/antweb/images/casent0286677/casent0286677_p_1_high-2.jpg"));
            ConvertCmd cmd = new ConvertCmd();
            cmd.run(op);
            //A.log("genImage() 2 EXISTS? " + FileUtil.fileExists("/usr/local/antweb/images/casent0286677/casent0286677_p_1_high-2.jpg"));

            Files.move(tempFile, Path.of(imagePath), REPLACE_EXISTING);

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

    public String toString() {
      return "code:" + code + " number:" + number + " shot:" + shot + " ext:" + ext + " displayName:" + getDisplayName();
    }
}


