package org.calacademy.antweb.util;

import java.io.*;

import java.util.*;

import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

public class ImageUtil {

    private static final Log s_log = LogFactory.getLog(ImageUtil.class);
    
    private static int fileFoundCount = 0;
    private static int fileNotFoundCount = 0;
    
    private static Counter counter = new Counter();
    
    private static ArrayList<SpecimenImage> notTifList = new ArrayList<>();
    
    public static ArrayList<SpecimenImage> getNotTifList() {
      return notTifList;
    }
    public static int hasTifCorrected = 0;
    
    public static void execute(String command, Connection connection) throws InterruptedException, IOException {
      counter.init();
      LogMgr.emptyLog("OJNotOT.txt");
      
      fileFoundCount = 0;
      fileNotFoundCount = 0;

      ImageDb imageDb = new ImageDb(connection);
      ArrayList<SpecimenImage> images = null;
      try {
        images = imageDb.getExifImages();
      } catch (SQLException e) {
        AntwebUtil.log("execute() e:" + e);
        return;
      }

      int successes = 0;
      int failures = 0;
      int errorCode = 0;
      for (SpecimenImage image: images) {

        errorCode = handleImage(command, image);

        if (errorCode == 0) {
          ++successes;
        } else {
          ++failures; 
        }
        //  A.log("execute() image:" + image + " errorCode:" + errorCode + " success:" + successes + " failure:" + failures);
      }

      imageDb.updateHasTif(getNotTifList());
      //AntwebUtil.log("execute() notTifList:" + getNotTifList());

      s_log.warn("execute() fileFoundCount:" + fileFoundCount + " fileNotFoundCount:" + fileNotFoundCount + " success:" + successes + " failure:" + failures);
      s_log.warn("execute() command:" + command + " counter:" + counter.toString() + " hasTifCorrected:" + hasTifCorrected);
    }

    private static int handleImage(String command, SpecimenImage image) throws InterruptedException, IOException {
        ArrayList<String> paths = image.getOrigAndDerivPaths();
        //if ("antweb1038462".equals(image.getCode())) A.log("handleImage() paths:" + paths);
        int i = 0;
        for (String path : paths) {
          ++i;
          if (i % 10000 == 0) s_log.warn("handleImage() i:" + i);
          if (new File(path).exists()) {
            ++fileFoundCount;
  
            if ("exifUpdate".equals(command)) {
              runExifProcess(path, image);
            }
            counter.count(true, path, image);              
          } else {
            ++fileNotFoundCount;
            counter.count(false, path, image);
            //A.log("handleImage() file does not exist:" + path);
          }
        }   
        return 0;
    }

    private static int runExifProcess(String path, SpecimenImage image)  throws InterruptedException, IOException {
        int errorCode = 0;
        ProcessBuilder pb = new ProcessBuilder("exiftool" 
          , "-Copyright=z" + image.getCopyright() //"-Copyright=California Academy of Sciences 2000-2018"
          , path
        );
        Process process = pb.start();
        //errorCode = process.waitFor();
        if (errorCode != 0) {
          s_log.debug("runExifProcess() path:" + path + " errors:" + (errorCode == 0 ? "No" : "Yes")); // + " output:\n" + output(process.getInputStream()));
        }    
        return errorCode;
    }
 
     
    // ot:origTif, oj:origJpg h:high.jpg, m:med.jpg, l:low.jpg, t:thumbview.jpg
    static String getFileRes(String path) {
      if (path.contains(".tif")) return "OT";
      else if (path.contains("high.jpg")) return "H";
      else if (path.contains("med.jpg")) return "M";
      else if (path.contains("low.jpg")) return "L";
      else if (path.contains("thumbview.jpg")) return "T";
      else if (path.contains(".jpg")) return "OJ";
      return "x";
    }
    
    private static String output(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(System.getProperty("line.separator"));
            }
        }
        return sb.toString();
    }
}


class Counter {
    int foundOT = 0;
    int foundOJ = 0;
    int foundT = 0;
    int foundL = 0;
    int foundM = 0;
    int foundH = 0;
    int notFoundOT = 0;
    int notFoundOJ = 0;
    int notFoundT = 0;
    int notFoundL = 0;
    int notFoundM = 0;
    int notFoundH = 0;
    
    void init() {
      foundOJ = 0;
      foundT = 0;
      foundL = 0;
      foundM = 0;
      foundH = 0;
      notFoundOT = 0;
      notFoundOJ = 0;
      notFoundT = 0;
      notFoundL = 0;
      notFoundM = 0;
      notFoundH = 0;
    }

    void count(boolean found, String path, SpecimenImage image) {         
      String fileRes = ImageUtil.getFileRes(path);
      if ("OT".equals(fileRes)) {
        if (found) ++foundOT; else {
          ++notFoundOT;
          LogMgr.appendLog("OTNotFound.txt", path);
        }
      }
      if ("OJ".equals(fileRes)) {
        if (found) ++foundOJ; else ++notFoundOJ;
      }
      if ("T".equals(fileRes)) {
        if (found) ++foundT; else ++notFoundT;
      }
      if ("L".equals(fileRes)) {
        if (found) ++foundL; else ++notFoundL;
      }
      if ("M".equals(fileRes)) {
        if (found) ++foundM; else ++notFoundM;
      }
      if ("H".equals(fileRes)) {
        if (found) ++foundH; else ++notFoundH;
      }
    }

    public String toString() {
      return "OT:" + foundOT + " OJ:" + foundOJ + " T:" + foundT + " L" + foundL + " M:" + foundM + " H:" + foundH 
        + " !OT:" + notFoundOT + " !OJ:" + notFoundOJ + " !T:" + notFoundT + " !L:" + notFoundL + " !M:" + notFoundM + " !H:" + notFoundH; 
    }
}
