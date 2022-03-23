package org.calacademy.antweb.util;

import org.calacademy.antweb.upload.*;

import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;

import java.util.regex.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;    
    
public class FileUtil {

  private static Log s_log = LogFactory.getLog(FileUtil.class);

  public ArrayList grep(String inputPattern, UploadFile uploadFile) {
    ArrayList result = new ArrayList();
    Pattern pattern = Pattern.compile(inputPattern, Pattern.CASE_INSENSITIVE);
    Matcher matcher = pattern.matcher("");

    BufferedReader br = null;
    String line;

    try {
      br = new BufferedReader(new InputStreamReader(new FileInputStream(uploadFile.getFileLoc()), uploadFile.getEncoding()));
    } catch (IOException e) {
      s_log.error("grep() cannot read fileLoc:" + uploadFile.getFileLoc() + " e:" + e.getMessage());
    }

    try {
      while ((line = br.readLine()) != null) {
        matcher.reset(line);
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

  // Delete directory given and all subdirectories and files (i.e. recursively).
//
  static public boolean clearWebUploadDir(File webUploadDir) throws IOException, InterruptedException {

    if (webUploadDir.exists()) {

      // 20210127

      Date today = new Date();
      Calendar cal = Calendar.getInstance(); cal.setTime(today); // don't forget this if date is arbitrary e.g. 01-01-2014

      int year = cal.get(Calendar.YEAR);

      String pattern = "";

      String deleteCommand = "rm -rf " + webUploadDir.getAbsolutePath() + year + "*";
      s_log.debug("clearWebUploadDir() command:" + deleteCommand);
//      Runtime runtime = Runtime.getRuntime();
//      Process process = runtime.exec( deleteCommand );
//      process.waitFor();

      //file.mkdirs(); // Since we only want to clear the directory and not delete it, we need to re-create the directory.

      return true;
    }

    return false;

  }

  public static int getPercentDiskFull() {
    String percent = null;
    String diskFree = FileUtil.getDiskFree();
    Integer num = null;
    try {
      int slashI = diskFree.indexOf(" /");
      if (slashI < 0) return -1;
      int spaceI = diskFree.indexOf(" ", slashI - 8);
      String percentStr = diskFree.substring(spaceI, slashI);
      s_log.debug("isDiskLow() slashI:" + slashI + " spaceI:" + spaceI + " percentStr:" + percentStr);

      int percentI = percentStr.indexOf("%");
      percent = percentStr.substring(0, percentI);
      percent = percent.trim();
      s_log.debug("isDiskLow() percentI:" + percentI + " percent:" + percent);

      num = Integer.valueOf(percent);
    } catch (StringIndexOutOfBoundsException e) {
      s_log.debug("isDiskLow() e:" + e);
      return -2;
    }
    return num;
  }

  public static String getDiskFree() {
    // This is done easily in serverStatus.jsp
    String diskFree = (new AntwebSystem()).launchProcess("df -h", true);
    s_log.warn("DiskFree:" + diskFree);
    return diskFree;
  }

  public static String getDiskStats() {
    File file = new File("/");

    long totalSpaceInMB = file.getTotalSpace() / 1024 / 1024 / 1024;
    long freeSpaceInMB = file.getFreeSpace() / 1024 / 1024 / 1024;
    long usableSpaceInMB = file.getUsableSpace() / 1024 / 1024 / 1024;

    String space = " Total:" + totalSpaceInMB + "GB Free:" + freeSpaceInMB + "GB Usable:" + usableSpaceInMB + "GB";
    double percent = 100 - ((file.getUsableSpace() * 100d) / file.getTotalSpace());
    String stats = "<b>Disk:</b> <font color=red>"+percent +"%</font>.  " + space;
    return stats;
  }


  public static boolean fileExists(String file) {
    return new File(file).exists();
  }

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
  
  public static boolean makeDir(String dirName) {
      boolean success = (new File(dirName)).mkdir();
      return success;
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
        s_log.error("getFileAttributesHtml() e:" + e);
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
        s_log.debug("getLastModified() e:" + e);
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
        s_log.debug("getFileSize() e:" + e);
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