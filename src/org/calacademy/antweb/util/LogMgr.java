package org.calacademy.antweb.util;

import java.io.*;
import java.net.*;
import java.util.*;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import static java.nio.file.StandardCopyOption.*;

import javax.servlet.http.*;
import javax.servlet.*;

import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class LogMgr {

  private static final Log s_log = LogFactory.getLog(LogMgr.class);
  
  public static void emptyLog(String file) {
    String docRoot = AntwebProps.getDocRoot();
    String logRoot = docRoot + "web/log/";
    file = logRoot + "/" + file;
    AntwebUtil.remove(file); 
  }

  public static void newLog(String file, String data) {
    emptyLog(file);
    appendLog(file, data, false);
  }

  // All 3 are Deprecated. Use appendWebLog() or appendDataLog() as needed.
  public static void appendLog(String file, String data) {
    appendWebLog(file, data, false);
  }
  public static void appendLog(String file, String data, boolean addTimestamp) {
    if (addTimestamp) data = DateUtil.getFormatDateTimeStr(new java.util.Date()) + " " + data;
    appendWebLog(null, file, data);
  }
  public static void appendLog(String dir, String file, String data) {
    appendWebLog(dir, file, data);
  }

  // These should be used for things log to a web accessible directory: /data/antweb/web/log
  public static void appendWebLog(String file, String data) {
    appendWebLog(file, data, false);
  }
  public static void appendWebLog(String file, String data, boolean addTimestamp) {
    if (addTimestamp) data = DateUtil.getFormatDateTimeStr(new java.util.Date()) + " " + data;
    appendWebLog(null, file, data);
  }
  public static void appendWebLog(String dir, String file, String data) {
    /* This method will create and/or append stringData to a file.  If the dir
       parameter is not null, the file will be created in a dir of that name.
       If the nested dir does not exist, it will be created. */
    String logRoot = AntwebProps.getDocRoot() + "web/log/";
    if (dir != null) logRoot += dir + "/";
    file = logRoot + file;
    LogMgr.appendFile(file, data);
  }

  // These should be used for things log to a non-web-accessible directory: /data/antweb/log
  public static void appendDataLog(String file, String data) {
    appendDataLog(file, data, false);
  }
  public static void appendDataLog(String file, String data, boolean addTimestamp) {
    if (addTimestamp) data = DateUtil.getFormatDateTimeStr(new java.util.Date()) + " " + data;
    appendDataLog(null, file, data);
  }
  public static void appendDataLog(String dir, String file, String data) {
    /* This method will create and/or append stringData to a file.  If the dir
       parameter is not null, the file will be created in a dir of that name.
       If the nested dir does not exist, it will be created. */
    String logRoot = AntwebProps.getDataRoot()+ "log/";
    if (dir != null) logRoot += dir + "/";
    file = logRoot + file;
    LogMgr.appendFile(file, data);
  }


  public static void appendFile(String fullPath, String data) {
    Utility.makeDirTree(fullPath);
    try {
      FileWriter fstream = new FileWriter(fullPath, true);
      BufferedWriter out = new BufferedWriter(fstream);
      out.write(data + "\n");
      out.close();
    } catch (Exception e) {
      s_log.error("appendFile() fullPath:" + fullPath + " e: " + e.getMessage());
    }

    FileUtil.set775Permission(fullPath);
  }
  
  public static void startup() {
    emptyLog("insecure.log");
  }  
  
  public static String archiveLogs() {
    // All log files in file system at: /data/antweb/web/log
    // Should be archived daily to here: /data/antweb/web/log/bak/2016...
    // So current files should be short and web accessible here: https://www.antweb.org/web/log
      
    String[] filesToMove = { "accessLog.txt", "getUrl.txt", "imageNotFound.txt", "logins.txt"
      , "longRequest.log", "messages.txt", "moveTaxonAndSupportingTaxa.log", "nonWorldAntsDeleted.txt"
      , "noExists.txt", "notFound.txt", "profile.log", "queryStats.log", "searches.txt"
      , "serverBusy.html", "serverBusy.log", "speciesListLog.txt", "invalid.log", "hacks.log"
      , "badRequest.log", "srfExceptions.jsp"};
  
    String dateStr = DateUtil.getFormatDateTimeStr();
  
    String yearStr = dateStr.substring(0,4);
  
    String logDir = AntwebProps.getDataRoot() + "log/";
    String bakDir = logDir + "bak/";
    String backupDir = bakDir + yearStr + "/" + dateStr + "/";
    Utility.makeDirTree(backupDir);
    s_log.warn("archiveLogs() Make backupDir:" + backupDir);

    //String webBackupDir = AntwebProps.getDomainApp() + "/web/log/bak/" + dateStr + "/";

    LogMgr.make777(bakDir);  
    LogMgr.make777(backupDir);

    String textFiles = logDir + "*.txt";
    String logFiles = logDir + "*.log";
    String htmlFiles = logDir + "*.html";

    StringBuilder logsNotFound = new StringBuilder();
    for (String fileToMove : filesToMove) {
      try {
        LogMgr.moveFile(logDir, fileToMove, backupDir);
      } catch (IOException e) {
        logsNotFound.append(fileToMove).append(", ");
      }
    }

    if (!"".equals(logsNotFound.toString()))
      s_log.warn("archiveLogs() logsNotFound:" + logsNotFound.substring(0, logsNotFound.length() - 2) + ".");

    String message = "files backed up here:" + backupDir;
    A.log("archiveLogs() message:" + message);
    return message;
  }

  private static void moveFile(String sourceDir, String fileName, String destDir) 
    throws IOException {

    Path source = FileSystems.getDefault().getPath(sourceDir, fileName);
    Path dest = FileSystems.getDefault().getPath(destDir);

    Files.move(source, dest.resolve(source.getFileName()), REPLACE_EXISTING);
  }
  
  public static void make777(String file) {
    try {
        //using PosixFilePermission to set file permissions 777
        Set<PosixFilePermission> perms = new HashSet<>();
        //add owners permission
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        //add group permissions
        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.GROUP_WRITE);
        perms.add(PosixFilePermission.GROUP_EXECUTE);
        //add others permissions
        perms.add(PosixFilePermission.OTHERS_READ);
        perms.add(PosixFilePermission.OTHERS_WRITE);
        perms.add(PosixFilePermission.OTHERS_EXECUTE);

        Files.setPosixFilePermissions(Paths.get(file), perms);    
    } catch (IOException e) {
        s_log.warn("e:" + e);
    }    
  }

  public static void logQuery(Connection connection, String note, String query) {
    String results = (new UtilDb(connection)).runQuery(query);
    s_log.warn("logQuery() note:" + note + " results:" + results);  
  }

  // Use this one. Unless you don't want to persist the query in Queries.java.
  public static void logAntQuery(Connection connection, String name, String note) {
    NamedQuery namedQuery = Queries.getNamedQuery(name);
    if (namedQuery != null) {
      String query = namedQuery.getQuery();
      String results = (new UtilDb(connection)).runQuery(query);
      s_log.warn("logAntQuery() note:" + note + " results:" + results);  
    } else {
      s_log.warn("logAntQuery() namedQuery not found:" + name);       
    } 
  }

  public static void logAntBattery(Connection connection, String name, String note) {
    ArrayList<NamedQuery> battery = QueryManager.getBattery(name);
    String results = "";
    int i = 0;
    for (NamedQuery namedQuery : battery) {
      ++i;
      String query = namedQuery.getQuery();
      String retVal = (new UtilDb(connection)).runQuery(query);
      results += "\n+++ query:" + i + ". "+ namedQuery.getName() + retVal;
    }
    s_log.warn("logAntBattery() note:" + note + " results:" + results);  
  }      
}


