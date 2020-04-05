package org.calacademy.antweb.util;

import org.calacademy.antweb.*;
import org.calacademy.antweb.upload.*;

import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.file.attribute.*;

import java.util.regex.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;    
    
public class FileUtil {

    private static Log s_log = LogFactory.getLog(FileUtil.class);

    public ArrayList grep(String inputPattern, UploadFile uploadFile) {
      ArrayList result = new ArrayList();
      Pattern pattern = Pattern.compile (inputPattern,Pattern.CASE_INSENSITIVE);
      Matcher matcher = pattern.matcher ("");

      BufferedReader br = null;
      String line;

      try {
        br = new BufferedReader(new InputStreamReader(new FileInputStream(uploadFile.getFileLoc()), uploadFile.getEncoding()));
      } catch (IOException e) {
        s_log.error("grep() cannot read fileLoc:" + uploadFile.getFileLoc() + " e:" + e.getMessage());
      }

      try {
        while ((line = br.readLine()) != null) {
          matcher.reset (line); 
          if (matcher.find()) {
            result.add(line);
          }
        }
        br.close();
      } catch (IOException e) {
        s_log.error("grep e:" + e);
      }
      if (result.size() > 0) {
        return result;
      } else {
        return null;
      }
    }
    

  public static int getPercentDiskFull() {
    String percent = null;
    String diskFree =  FileUtil.getDiskFree();
    Integer num = null;
    try {
      int slashI = diskFree.indexOf(" /");    
      if (slashI < 0) return -1;  
      int spaceI = diskFree.indexOf(" ", slashI - 8);
      String percentStr = diskFree.substring(spaceI, slashI);
      A.log("isDiskLow() slashI:" + slashI + " spaceI:" + spaceI + " percentStr:" + percentStr);

      int percentI = percentStr.indexOf("%");
      percent = percentStr.substring(0, percentI);
      percent = percent.trim();
      A.log("isDiskLow() percentI:" + percentI + " percent:" + percent);

      num = Integer.valueOf(percent);
    } catch(java.lang.StringIndexOutOfBoundsException e) {
      A.log("isDiskLow() e:" + e);
      return -2;
    }
    return num.intValue();
  }    
 
  public static String getDiskFree() {
    // This is done easily in serverStatus.jsp
    String diskFree = (new AntwebSystem()).launchProcess("df -h", true);
    s_log.warn("DiskFree:" + diskFree);
    return diskFree;
  }	

  public static boolean fileExists(String file) {
    return new File(file).exists();
  }
  
/*
  public static boolean fileExists(String file) {
    String command = "ls " + file;
    String lsResults = (new AntwebSystem()).launchProcess(command, true);
    // AntwebUtil.log("fileExists() file:" + file + " results: " + lsResults);

    A.log("fileExists() exists:" + lsResults.contains(file));

    if (lsResults.contains(file)) {
      return true;
    } else { 
      return false;
    }
  }
*/

  
  public static boolean fileExistsAndCurrent(String file) {
    // This could be implemented as above, but with -al, get the date, compare it to the
    // present and delete accordingly.  This could functionally outmode the curate-body
    // Delete Caches link.
    return fileExists(file);
  }
  
  
// Designed to replace code like this:         <!-- jsp:include page="/web/genInc/statistics.jsp" flush="true"/ -->  
// Which does not check for missing files.
  // Dir file should look like web/sitewarning.jsp web/genInc/statistics.jsp ... 
  //   It should start with a / ? That sucks... Lets'change it.
  public static String getContent(String dirFile) {
      String fullDirFile = AntwebProps.getDocRoot() + dirFile;
      // example: /data/antweb/ + web/genInc/statistics.jsp
      // or
      //          /usr/local/tomcat/webapps/antweb/ + web/genInc/statistics.jsp

      //A.log("getContent(" + fullDirFile + ") exists.");
      String content = AntwebUtil.readFile(fullDirFile);
      if (content == null) content = "&nbsp;" + dirFile + " unavailable";
      return content;
  }
  
  public static void makeDir(String dirName) {
      boolean success = (new File(dirName)).mkdir();
  }

    public static String getFileAttributesHtml(String path) {
      String fileData = "";
      try {
        Path file = Paths.get(path);    
        BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
        fileData += "<b>creationTime:</b> " + attr.creationTime();
        fileData += "<br><b>lastAccessTime:</b> " + attr.lastAccessTime();
        fileData += "<br><b>lastModifiedTime:</b> " + attr.lastModifiedTime();
        fileData += "<br><b>isDirectory:</b> " + attr.isDirectory();
        fileData += "<br><b>isOther:</b> " + attr.isOther();
        fileData += "<br><b>isRegularFile:</b> " + attr.isRegularFile();
        fileData += "<br><b>isSymbolicLink:</b> " + attr.isSymbolicLink();
        fileData += "<br><b>size:</b> " + attr.size();
        return fileData;
      } catch (IOException e) {
        A.log("getFileAttributesHtml() e:" + e);
      }
      //A.log("getFileAttributesHtml() fileData:" + fileData);
      return fileData;
    }      
      
    public static String getLastModified(String path) {
      String fileData = "";
      try {
        Path file = Paths.get(path);    
        BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
        fileData += "<b>lastModifiedTime:</b> " + attr.lastModifiedTime();
        return fileData;
      } catch (IOException e) {
        A.log("getLastModified() e:" + e);
      }
      //A.log("getFileAttributesHtml() fileData:" + fileData);
      return fileData;
    }    

    public static int getFileSize(String path) {
      int fileSize = 0;
      try {
        Path file = Paths.get(path);    
        BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
        fileSize = Long.valueOf(attr.size()).intValue();
        //fileSize = new Integer(size).intValue();
      } catch (IOException e) {
        A.log("getFileSize() e:" + e);
      }
      //A.log("getFileAttributesHtml() fileData:" + fileData);
      return fileSize;
    }

  public static void set775Permission(String fullPath) {
      setPermission(new File(fullPath));
  }
  public static void setPermission(File file) {
      try {
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);

        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.GROUP_WRITE);
        perms.add(PosixFilePermission.GROUP_EXECUTE);

        perms.add(PosixFilePermission.OTHERS_READ);
        //perms.add(PosixFilePermission.OTHERS_WRITE);
        //perms.add(PosixFilePermission.OTHERS_EXECUTE);

        Files.setPosixFilePermissions(file.toPath(), perms);
      } catch (IOException e) {
        // Do nothing because nothing to do.
      }
  }

  public static String makeReportName(String coreName) {
    return "Antweb" + coreName + DateUtil.getFormatDateStr() + ".txt";
  }
}