package org.calacademy.antweb.upload;

import java.util.Map;
import java.util.*;
import java.io.*;
import java.sql.Connection;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.home.ProjectDb;
import org.calacademy.antweb.curate.OperationDetails;
import javax.servlet.http.*;
import org.apache.struts.action.ActionForward;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;    

public class UploadDetails extends OperationDetails {

    private static Log s_log = LogFactory.getLog(UploadDetails.class);

    MessageMgr messageMgr = null;

    private boolean runStatistics = false;

    public ArrayList preUploadStatistics = null;
    public ArrayList postUploadStatistics = null;

    int countInsertedSpecies = 0;
    int countUpdatedSpecies = 0;
    int countInsertedSpecimens = 0;

    private String logFileName = null;
    private String backupDirFile = null;


    private boolean offerRunCountCrawlLink = false;
    public boolean isOfferRunCountCrawlLink() {
        return offerRunCountCrawlLink;
    }
    public void setOfferRunCountCrawlLink(boolean offerRunCountCrawlLink) {
        this.offerRunCountCrawlLink = offerRunCountCrawlLink;
    }

    public UploadDetails() {
      super();
      messageMgr = new MessageMgr();
    }
        
    public UploadDetails(String operation) {
      super(operation);
      messageMgr = new MessageMgr();            
    }

    public UploadDetails(String operation, String message) {
      super(operation, message);
      messageMgr = new MessageMgr();
    }

    // This is a convenience constructer for returning an error message.
    public UploadDetails(String operation, String message, HttpServletRequest request) {
      super(operation, message);
      messageMgr = new MessageMgr();
    }

    public UploadDetails(String operation, String message, String forwardPage) {
      super(operation, message, forwardPage);
      // The presence of a forward implies it is a message, and that was not successful execution.
      messageMgr = new MessageMgr();
    }
    
    public String toString() {
      return "{operation:" + getOperation() + " c:" + getRecordCount() + " forwardPage:" + getForwardPage() 
      + " logFileName:" + getLogFileName() + " message:" + getMessage() + "}";
    }

    public String getLogFileDir() {
      if (getLogFileName() != null) {
        if (getLogFileName().contains("orldants")) return "worldants";
        if (getLogFileName().contains("pecimen")) return "specimen";
      }
      return "upload";
    }

    public void setLogFileName(String logFileName) {
      this.logFileName = logFileName; 
    }    
    public String getLogFileName() {
        if (this.logFileName != null) return this.logFileName;
        
        String dateString = (new Utility()).getDateForFileName(getStartTime());
        return dateString + "-" + getOperation() + getLogExt();
    }
    
    public void setBackupDirFile(String backupDirFile) {
        this.backupDirFile = backupDirFile; 
    }    
    public String getBackupDirFile() {
        return backupDirFile;
    }
        
    // JSP log business.
    public static final String appendLogExt = ".html";
    public static final String appendLogExtJsp = ".jsp";
    public static boolean isLogJsp() {
      boolean jspTurnedOn = true;
      jspTurnedOn = false;
      if (!jspTurnedOn) return false;
      return true;
    }    
    public static String getLogExt() {
      if (UploadDetails.isLogJsp()) {
        return appendLogExtJsp;
      } else {
        return appendLogExt;
      }
    }

    public String getLogDirFile() {
      String dir = null;
      if (getOperation().contains("specimen")) dir = "specimen";
      if (getOperation().contains("orldants")) dir = "worldants";
      
      return dir + "/" + getLogFileName(); //java.net.URLEncoder.encode(getLogFileName());
    }

    public String getLogFileAnchor() {
        String logFileAnchor = "<a href=\"" + AntwebProps.getDomainApp() + "/web/log/" + getLogDirFile() + "\">" + getLogDirFile() + "</a>";            
        return logFileAnchor;
    }
    
    public String getBackupFileAnchor() {
        String backupFileAnchor = "";
        if (getBackupDirFile() != null) {
          backupFileAnchor = "<a href=\"" + AntwebProps.getDomainApp() + "/web/"
            + getBackupDirFile() + "\">" + getBackupDirFile() + "</a>";
        }
        return backupFileAnchor;    
    }

	
    public void logMessagesToFile(Login accessLogin, HttpServletRequest request) {
        logMessagesToFile(UploadHelper.getUploadFile(), accessLogin, request);
    }

    public void logMessagesToFile(UploadFile uploadFile, Login accessLogin, HttpServletRequest request) {
        // Generate the log file for this operation
        Group accessGroup = null;
        
        if (accessLogin != null) {
          accessGroup = accessLogin.getGroup();
        } else {
          // A little sloppy. To prevent an NPE. Should probably fetch upstream.
          accessLogin = LoginMgr.getAdminLogin();
          accessGroup = GroupMgr.getAdminGroup();
        }

        getMessageMgr().compileMessages(accessGroup);

        //String serverDir = null;
        
        String encoding = "";

        A.log("logMessagesToFile() backupDirFile:" + getBackupDirFile());
                
        if (uploadFile != null) {
          encoding = uploadFile.getEncoding();
        }

        s_log.warn("logMessagesToFile() uploadFile:" + uploadFile 
          //+ " serverDir:" + getServerDir() + " backupFileName:" + backupFileName 
          + " accessLogin:" + accessLogin);

        boolean hasMessages =! getMessageMgr().getMessages().isEmpty();
        
        String hasErrorString = "";
        if (hasMessages) hasErrorString = ": <font color=red>Check Your Data Errors</font>";
        String logString = "";

        String warning = "";
        if (getMessage() != null) warning = "<h3><font color=red>" + getMessage() + "</font></h3><br>";
        A.log("logMessagesToFile() warning:" + warning);
        
        logString +=
            "<head><title>" + getLogFileName() + "</title>" 
            + "<link rel='shortcut icon' href='<%= domainApp %>/image/favicon.ico' />"
            + "<meta charset=\"utf-8\"/></head><body>"
            + "<h2>Upload Report" + hasErrorString + "</h2>"
            + "<a href = \"" + AntwebProps.getDomainApp() + "\">Home</a> | <a href = \"" 
            + AntwebProps.getDomainApp() + "/curate.do\">Curator Tools</a><br><br>"
            + warning
            + "<h3>Upload Details:</h3>"
            + "&nbsp;&nbsp;&nbsp;<b>Upload ID:</b> <a href='" + AntwebProps.getDomainApp() + "/uploadReport.do?uploadId=" + AntwebMgr.getNextSpecimenUploadId() + "'>" + AntwebMgr.getNextSpecimenUploadId() + "</a>"
            + "<br>&nbsp;&nbsp;&nbsp;<b>This Log File:</b> " + getLogFileAnchor() 
            + "<br>&nbsp;&nbsp;&nbsp;<b>Date:</b> " + (new Date()).toString()     
            + "<br>&nbsp;&nbsp;&nbsp;<b>Encoding:</b> " + encoding   
            + "<br>&nbsp;&nbsp;&nbsp;<b>Antweb version:</b> " + AntwebUtil.getReleaseNum()  
            + "<br>&nbsp;&nbsp;&nbsp;<b>Uploaded File:</b> " + getBackupFileAnchor();

        // AccessGroup should have a getCurator link. Will always be a curator.
        // Should load properly in the first place.
        Curator curator = LoginMgr.getCurator(accessLogin.getId());

        logString += "<br>&nbsp;&nbsp;&nbsp;<b>Login Id:</b> " + curator.getLink();
        logString += "<br>&nbsp;&nbsp;&nbsp;<b>Group Id:</b> " + accessGroup.getLink();

        logString += "<br>&nbsp;&nbsp;&nbsp;<b>Record Count:</b> " + getRecordCount();

		logString += "<br>&nbsp;&nbsp;&nbsp;<b>Parsed:</b> " + getBuildLineTotal();
		logString += "<br>&nbsp;&nbsp;&nbsp;<b>Processed:</b> " + getProcessLineTotal();
		logString += "<br>&nbsp;&nbsp;&nbsp;<b>Museums:</b> <a href='' title='" +  getMuseumMap().toString() + "'>" + getMuseumMap().size() + "</a>";

        logString += "<br>&nbsp;&nbsp;&nbsp;<b>Inserted Specimens:</b> " + countInsertedSpecimens;        
        logString += "<br>&nbsp;&nbsp;&nbsp;<b>Red Flagged Specimens:</b> " + getMessageMgr().getRedFlagCount();

        logString += "<br>&nbsp;&nbsp;&nbsp;<b>Advanced Search:</b><a href=\"" + AntwebProps.getDomainApp() + "/advancedSearch.do?"
                + "searchMethod=advancedSearch&advanced=true&uploadId=" + AntwebMgr.getNextSpecimenUploadId()
                //+ "&groupName=" + accessGroup.getName()
                + "\"> This Upload</a><a href='' title='Could be affected/limited by subsequent uploads. No red flagged specimen included in results.'>*</a>";

        logString += "<br>&nbsp;&nbsp;&nbsp;<b>Exec Time:</b> " + getExecTime() + getExecTimeMin();
        
        logString += "\r\r<br><br>";
        logString += "<h3>Statistics</h3>";

        if ((preUploadStatistics != null) && (postUploadStatistics != null) ) {
        //if (!getPreUploadStats().equals(getPostUploadStats())) {
          if (preUploadStatistics.equals(postUploadStatistics)) {
            logString += "<br>Prior to Upload:<font color=green>Data set unchanged</font>";  
          } else {
            logString += "<br>Prior to Upload:<br>" + ProjectDb.getProjectTableHeader() +  ProjectDb.getProjectStatisticsHtml(preUploadStatistics, false) + "</table>";                                       
          }  
        } else {
          s_log.warn("logMessagesToFile pre:" + preUploadStatistics + " post:" + postUploadStatistics);
        }

        s_log.info("logMessagesToFile pre:" + preUploadStatistics + " post:" + postUploadStatistics);


        if (postUploadStatistics != null) {

          if (countInsertedSpecies > 0) logString += "<br>&nbsp;&nbsp;&nbsp;<b>Inserted Species:</b> " + countInsertedSpecies + ".";
          if (countUpdatedSpecies > 0) logString += "<br>&nbsp;&nbsp;&nbsp;<b>Updated Species:</b> " + countUpdatedSpecies + ".";        

          logString += "<br><br>Current Taxa Counts:<br>" + ProjectDb.getProjectTableHeader() + ProjectDb.getProjectStatisticsHtml(postUploadStatistics, true) + "</table>"; 
        }
                
        logString += getMessageMgr().getMessagesReport();
        logString += "</body>";

        s_log.info("logMessagesToFile() logFileDir:" + getLogFileDir() + " logFileName:" + getLogFileName());
        LogMgr.appendLog(getLogFileDir(), getLogFileName(), logString);
        
        String messageStr = getMessageMgr().getMessageStr();
        
        //A.log("logMessagesToFile() messageStr:" + messageStr + " request:" + request);
        if (messageStr != null) {
          if (request != null) request.setAttribute("messageStr", messageStr);
        }
        if (request != null) {   
          // It was a post   well be web/log/log/upload if in dev?
          String messageLogFile = getLogFileDir() + "/" + getLogFileName();
          //request.setAttribute("messageLogFile", messageLogFile);

          setMessageLogFile(messageLogFile);
          // s_log.warn("logMessages() " + hasMessages);

          setHasMessages(hasMessages);
          //request.setAttribute("hasMessages", hasMessages);
          //s_log.warn("logMessagesToFile() logDir:" + logDir + " logFileName:" + logFileName + " messageLogFile:" + messageLogFile + " hasMessages:" + hasMessages + " logString.length():" + logString.length());

          request.setAttribute("uploadDetails", this);
        }
    }

    String messageLogFile = null;
    public String getMessageLogFile() {
        return messageLogFile;
    }
    public void setMessageLogFile(String messageLogFile) {
        this.messageLogFile = messageLogFile;
    }

    boolean hasMessages = false;
    public boolean isHasMessages() {
        return hasMessages;
    }
    public void setHasMessages(boolean hasMessages) {
        this.hasMessages = hasMessages;
    }

    private boolean offerRunCountCrawlsLink = false;
    public void setOfferRunCountCrawlsLink(boolean offer) {
        offerRunCountCrawlsLink = offer;
    }
    public boolean isOfferRunCountCrawlsLink() {
      return offerRunCountCrawlsLink;
    }

    public void augment(UploadDetails uploadDetails) {
      if (!uploadDetails.getMessage().equals("success")) {
        setMessage(getMessage() + uploadDetails.getMessage());
      }
    }

    int buildLineTotal = 0;
    public void setBuildLineTotal(int buildLineTotal) {
      this.buildLineTotal = buildLineTotal;
    }
    public int getBuildLineTotal() {    
     return buildLineTotal;
    }
        
    int processLineTotal = 0;
    public void setProcessLineTotal(int processLineTotal) {
      this.processLineTotal = processLineTotal;
    }
    public int getProcessLineTotal() {    
     return processLineTotal;
    }
    
    
    public void finish(Login accessLogin, HttpServletRequest request, Connection connection) {
        A.log("finish() operation:" +  getOperation());
        String execTime = HttpUtil.finish(request, getStartTime());
        setExecTime(execTime);

        if (getOperation().contains("orldants")) {
            (new WorldantsUploadDb(connection)).updateWorldantsUpload(this); 
        }
        
        if (getForwardPage() == null) {
          logMessagesToFile(accessLogin, request);
  		  setForwardPage("uploadResults");	
        }
    }  
    

    public void setRunStatistics(boolean runStatistics) {
    this.runStatistics = runStatistics; 
    }
    public boolean getRunStatistics() {
      return this.runStatistics; 
    }
    
    public void setPreUploadStatistics(ArrayList stats) {
      A.log("preUploadStats:" + stats);
      preUploadStatistics = stats;
    }
    public ArrayList getPreUploadStatistics() {
      return preUploadStatistics;
    }
    public void setPostUploadStatistics(ArrayList stats) {
      A.log("postUploadStats:" + stats);
      postUploadStatistics = stats;
    }
    public ArrayList getPostUploadStatistics() {
      return postUploadStatistics;
    }
    
    public void countInsertedSpecies() {
      ++countInsertedSpecies;
    }
    public void countUpdatedSpecies() {
      ++countUpdatedSpecies;
    }
    public void countInsertedSpecimen() {
      ++countInsertedSpecimens;
    }
    
    // Maintain a set of museums.  Could have been query but this done for performance.
    private Map<String, Integer> museumMap = new HashMap<String, Integer>();
    
    public void addToMuseumMap(String element) {
      for (Museum museum : MuseumMgr.getMuseums()) {
        String code = museum.getCode();
        if (element.contains(code)) {
          int count = museumMap.containsKey(code) ? museumMap.get(code) : 0;
          museumMap.put(code, ++count);    
          //A.log("addToMuseumSet() element:" + element + " code:" + code + " count:" + count);      
        }
      }
    }
    public Map<String, Integer> getMuseumMap() {
      return museumMap;
    }
    
    public MessageMgr getMessageMgr() {
      return messageMgr;
    }
    
    private int recordCount = 0;
    public int getRecordCount() {
      return recordCount;
    }
    public void setRecordCount(int recordCount) {
      this.recordCount = recordCount;
    }
      
}


