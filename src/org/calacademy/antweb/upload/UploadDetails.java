package org.calacademy.antweb.upload;

import java.util.Map;
import java.util.*;
import java.sql.SQLException;
import java.sql.Connection;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.curate.OperationDetails;
import jakarta.servlet.http.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;    

/**
 * The result of one upload or AntCat (WorldAnts) import: counts (records, inserted and updated species), the
 * report sections (MessageMgr), museums seen, the report and backup file names. finish() writes the HTML
 * report to web/log/<specimen|worldants|upload>/<date>-<operation>.html and, for an AntCat import, updates
 * worldants_upload. Returned to SpecimenUploadJob (status page, email), TaxonWorksNightly and the admin data
 * jobs. Docs: docs/flows/specimen-upload.md (section 3, step 11), docs/numbers.md.
 * Tests: UploadReportCountsTest, tests/e2e/test_numbers_uploads.py.
 */
public class UploadDetails extends OperationDetails {

    private static final Log s_log = LogFactory.getLog(UploadDetails.class);

    final MessageMgr messageMgr;

    private boolean runStatistics = false;

    public ArrayList<String> preUploadStatistics;
    public ArrayList<String> postUploadStatistics;

    // The species and subspecies names this upload wrote to the taxon table (report "Inserted Species", "Updated
    // Species"; docs/numbers.md row 95): each name once, as inserted (a new row) or as updated (an existing row
    // rewritten), never both. Until 2026-10-27 a new name was counted as updated (B60) and a name was counted again
    // each time its lines came back after another name's (B61).
    private final Set<String> insertedSpecies = new HashSet<>();
    private final Set<String> updatedSpecies = new HashSet<>();
    int countInsertedSpecimens = 0;

    /** The rule of the report's counts (Record Count, Inserted and Updated Species), written into the report so
     *  tests can tell a report of today's rule from an older one (reports are files, never rewritten). */
    public static final String COUNTS_RULE = "2026-10-27";

    private String logFileName;
    private String backupDirFile;

    public static final String appendLogExt = ".html";
    public static final String appendLogExtJsp = ".jsp";

    String action;
    String messageLogFile;
    boolean hasMessages = false;
    int buildLineTotal = 0;
    int processLineTotal = 0;
    private int recordCount = 0;

    // Maintain a set of museums.  Could have been query but this done for performance.
    private final Map<String, Integer> museumMap = new HashMap<>();

    private boolean offerRunCountCrawlLink = false;

    // The upload_id of the upload's row in the upload table (SpecimenUploadDb.recordUpload), 0 until written.
    private int uploadId = 0;
    public void setUploadId(int uploadId) { this.uploadId = uploadId; }
    public int getUploadId() { return uploadId; }

    /** The upload's id: the one recorded, or (before the record is written) the one its specimens carry. */
    public int getReportUploadId() {
        return uploadId > 0 ? uploadId : AntwebMgr.getNextSpecimenUploadId();
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
	
    // How the upload was made, shown in the report header: null for the upload page,
    // or e.g. "Nightly TaxonWorks job (automatic)" (GitHub #27).
    private String uploadSource;
    public void setUploadSource(String uploadSource) { this.uploadSource = uploadSource; }
    public String getUploadSource() { return uploadSource; }

    public void genUploadReport(Login accessLogin, HttpServletRequest request) {
        genUploadReport(UploadHelper.getUploadFile(), accessLogin, request);
    }

    private Curator curator = null;
    public void setCurator(Curator curator) {
        this.curator = curator;
    }
    public Curator getCurator() {
        return this.curator;
    }
    public void genUploadReport(UploadFile uploadFile, Login accessLogin, HttpServletRequest request) {
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

        String encoding = "";

        if (uploadFile != null) {
          encoding = uploadFile.getEncoding();
        }
        //A.log("genUploadReport() uploadFile:" + uploadFile + " accessLogin:" + accessLogin);

        boolean hasMessages =! getMessageMgr().getMessages().isEmpty();

        // The report page (UploadReportPage, 2026-10-29): the tool pages' look, a summary box with the email's counts,
        // the sections in groups, the checks that found nothing in one line, details and statistics at the bottom.
        String logString = UploadReportPage.render(reportFacts(encoding, accessLogin), getMessageMgr());
        s_log.warn("genUploadReport() logFileDir:" + getLogDir() + " logFileName:" + getLogFileName() + " operation:" + getOperation());
        LogMgr.appendLog(getLogDir(), getLogFileName(), logString);
        
        String messageStr = getMessageMgr().getMessageStr();
        
        //A.log("genUploadReport() messageStr:" + messageStr + " request:" + request);
        if (messageStr != null) {
          if (request != null) request.setAttribute("messageStr", messageStr);
        }
        if (request != null) {   
          // It was a post   well be web/log/log/upload if in dev?
          String messageLogFile = getLogDir() + "/" + getLogFileName();
          //request.setAttribute("messageLogFile", messageLogFile);

          setMessageLogFile(messageLogFile);
          // s_log.warn("logMessages() " + hasMessages);

          setHasMessages(hasMessages);
          //request.setAttribute("hasMessages", hasMessages);
          //s_log.warn("genUploadReport() logDir:" + logDir + " logFileName:" + logFileName + " messageLogFile:" + messageLogFile + " hasMessages:" + hasMessages + " logString.length():" + logString.length());

          request.setAttribute("uploadDetails", this);
        }
    }

    /** What the report page shows besides the sections (genUploadReport). */
    UploadReportPage.Facts reportFacts(String encoding, Login accessLogin) {
        UploadReportPage.Facts f = new UploadReportPage.Facts();
        String app = AntwebProps.getDomainApp();
        f.domainApp = app;
        f.cssHref = app + "/common/tools.css";
        f.title = getLogFileName();
        f.operation = getOperation();
        if (getMessage() != null && !"success".equals(getMessage())) f.warningHtml = getMessage();
        f.uploadId = getReportUploadId();
        f.date = new Date().toString();
        f.encoding = encoding;
        f.version = AntwebUtil.getReleaseNum();
        f.logFileHtml = getLogFileAnchor();
        f.uploadedFileHtml = getBackupFileAnchor();
        String name = getBackupDirFile();
        if (name != null && "specimen".equals(getLogDir())) {
            f.uploadFileName = name.startsWith("upload/") ? name.substring("upload/".length()) : name;
        }
        if (uploadSource != null) {
            f.uploadedBy = uploadSource;
        } else if ("specimenUpload".equals(getOperation()) || "taxonWorksUpload".equals(getOperation())) {
            f.uploadedBy = "Upload page (manual)";
        }

        // AccessGroup should have a getCurator link. Will always be a curator.
        // setCurator() is set during SpecimenUpload.importSpecimens. This curator may be the effective curator and
        // not necessarily the uploader (which could be an admin).
        Curator accessCurator = LoginMgr.getCurator(accessLogin.getId());
        if (accessCurator != null) {
            Curator effective = getCurator() != null ? getCurator() : accessCurator;
            f.groupHtml = effective.getGroup().getLink();
            f.curatorHtml = effective.getLink();
            if (getCurator() != null && accessCurator.getId() != getCurator().getId()) f.adminHtml = accessCurator.getLink();
        } else {
            f.groupHtml = accessLogin.getGroup().getLink();
            f.loginId = String.valueOf(accessLogin.getId());
        }

        f.recordCount = getRecordCount();
        f.countsRule = COUNTS_RULE;
        if ("specimenUpload".equals(getOperation()) || "taxonWorksUpload".equals(getOperation())) {
            f.parsed = getBuildLineTotal();
            f.processed = getProcessLineTotal();
            f.insertedSpecimens = countInsertedSpecimens;
            f.museumsHtml = "<span title='" + Html.escAttr(getMuseumMap().toString()) + "'>" + getMuseumMap().size() + "</span>";
        }
        if (getOperation() != null && getOperation().contains("orldants")) {
            f.showChecks = false;   // as before: a Worldants report lists its errors only
            f.worldantsLinksHtml = "<a href='" + app + "/taxonomicPage.do?rank=subfamily&project=worldants'>Subfamilies</a>, "
                + "<a href='" + app + "/taxonomicPage.do?rank=genus&project=worldants'>Genera</a>, "
                + "<a href='" + app + "/taxonomicPage.do?rank=species&project=worldants'>Species</a>";
            f.errorsHtml = new ArrayList<>(MessageMgr.getErrors());
        }
        f.execTime = getExecTime() + getExecTimeMin();
        f.insertedSpecies = getInsertedSpeciesCount();
        f.updatedSpecies = getUpdatedSpeciesCount();

        String stats = "";
        if (preUploadStatistics != null && postUploadStatistics != null) {
          if (preUploadStatistics.equals(postUploadStatistics)) {
            stats += "<p>Prior to Upload: data set unchanged.</p>";
          } else {
            stats += "<p>Prior to Upload:</p>" + ProjectDb.getProjectTableHeader() + ProjectDb.getProjectStatisticsHtml(preUploadStatistics, false) + "</table>";
          }
        }
        if (postUploadStatistics != null) {
          stats += "<p>Current Taxa Counts:</p>" + ProjectDb.getProjectTableHeader() + ProjectDb.getProjectStatisticsHtml(postUploadStatistics, true) + "</table>";
        }
        f.statisticsHtml = stats.isEmpty() ? null : stats;
        return f;
    }

    public void finish(Login accessLogin, HttpServletRequest request, Connection connection) throws SQLException {
        A.log("finish() operation:" +  getOperation());
        String execTime = HttpUtil.finish(request, getStartTime());
        setExecTime(execTime);

        if (getOperation().contains("orldants")) {
            new WorldantsUploadDb(connection).updateWorldantsUpload(this);
        }

        if (getForwardPage() == null) {
            genUploadReport(accessLogin, request);
            setForwardPage("uploadResults");
        }
    }

    public static boolean isLogJsp() {
        boolean jspTurnedOn = true;
        jspTurnedOn = false;
        return jspTurnedOn;
    }
    public static String getLogExt() {
        if (UploadDetails.isLogJsp()) {
            return appendLogExtJsp;
        } else {
            return appendLogExt;
        }
    }

    public String getLogDir() {
        return getLogDir(getOperation());
    }
    public static String getLogDir(String operation) {
        String logDir = "upload";
        if (operation.contains("specimen")) logDir = "specimen";
        if (operation.contains("orldants")) logDir = "worldants";

        if (operation.contains("taxonWorks")) logDir = "specimen";
        if (operation.contains("GBIF")) logDir = "specimen";

        //A.log("getLogDir() operation:" + operation + " logDir:" + logDir);
        return logDir;
    }

    public String getLogDirFile() {
        String dir = null;
        dir = UploadDetails.getLogDir(getOperation());

        return dir + "/" + getLogFileName(); //java.net.URLEncoder.encode(getLogFileName());
    }

    public String getLogFileAnchor() {
        String logFileAnchor = "<a href=\"" + AntwebProps.getDomainApp() + "/web/log/" + getLogDirFile() + "\">" + getLogDirFile() + "</a>";
        return logFileAnchor;
    }

    public String getBackupFileAnchor() {
        String backupFileAnchor = "";
        if (getBackupDirFile() != null) {
            // web/upload/ is not served; curators download the file through uploadFile.do.
            String name = getBackupDirFile();
            if (name.startsWith("upload/")) name = name.substring("upload/".length());
            backupFileAnchor = "<a href=\"" + AntwebProps.getDomainApp() + "/uploadFile.do?file="
                    + Html.escAttr(Html.url(name)) + "\">" + Html.esc(getBackupDirFile()) + "</a>";
        }
        return backupFileAnchor;
    }


    /**
     * True if the owned-by value names the museum code as a whole word: "CASC" is not counted for CAS too, as it was
     * when any value containing the code counted (2026-10-30). Case as in the museum list.
     */
    public static boolean namesMuseum(String ownedBy, String code) {
        if (ownedBy == null || code == null || code.isEmpty()) return false;
        // no regex: called for every museum on every saved specimen
        for (int i = ownedBy.indexOf(code); i >= 0; i = ownedBy.indexOf(code, i + 1)) {
            int end = i + code.length();
            boolean before = i > 0 && Character.isLetterOrDigit(ownedBy.charAt(i - 1));
            boolean after = end < ownedBy.length() && Character.isLetterOrDigit(ownedBy.charAt(end));
            if (!before && !after) return true;
        }
        return false;
    }

    public void addToMuseumMap(String element) {
        for (Museum museum : MuseumMgr.getMuseums()) {
            String code = museum.getCode();
            if (namesMuseum(element, code)) {
                int count = museumMap.getOrDefault(code, 0);
                museumMap.put(code, ++count);
                //A.log("addToMuseumSet() element:" + element + " code:" + code + " count:" + count);
            }
        }
    }
    public Map<String, Integer> getMuseumMap() {
        return museumMap;
    }


    public String toString() {
        return "{operation:" + getOperation() + " c:" + getRecordCount() + " forwardPage:" + getForwardPage()
                + " logFileName:" + getLogFileName() + " message:" + getMessage() + "}";
    }

    public void setAction(String action) {
        this.action = action;
    }
    public String getAction() {
        return action;
    }

/*
    public void setLogFileName(String logFileName) {
        this.logFileName = logFileName;
    }
 */
    public String getLogFileName() {
        if (this.logFileName != null) return this.logFileName;

        String dateString = Utility.getDateForFileName(getStartTime());
        return dateString + "-" + getOperation() + getLogExt();
    }

    public void setBackupDirFile(String backupDirFile) {
        this.backupDirFile = backupDirFile;
    }
    public String getBackupDirFile() {
        return backupDirFile;
    }

    public String getMessageLogFile() {
        //A.log("getMessageLogFile() messageLogFile:" + messageLogFile);
        //AntwebUtil.logShortStackTrace();
        return messageLogFile;
    }
    public void setMessageLogFile(String messageLogFile) {
        //A.log("setMessageLogFile() messageLogFile:" + messageLogFile);
        //AntwebUtil.logShortStackTrace();
        this.messageLogFile = messageLogFile;
    }

    public boolean isHasMessages() {
        return hasMessages;
    }
    public void setHasMessages(boolean hasMessages) {
        this.hasMessages = hasMessages;
    }

    public boolean isOfferRunCountCrawlLink() {
        return offerRunCountCrawlLink;
    }
    public void setOfferRunCountCrawlLink(boolean offerRunCountCrawlLink) {
        this.offerRunCountCrawlLink = offerRunCountCrawlLink;
    }

    public void augment(UploadDetails uploadDetails) {
      String added = uploadDetails.getMessage();
      if (added != null && !added.equals("success")) {
        String own = getMessage();
        setMessage(own == null || own.equals("success") ? added : own + added);
      }
    }

    public void setBuildLineTotal(int buildLineTotal) {
      this.buildLineTotal = buildLineTotal;
    }
    public int getBuildLineTotal() {    
     return buildLineTotal;
    }

    public void setProcessLineTotal(int processLineTotal) {
      this.processLineTotal = processLineTotal;
    }
    public int getProcessLineTotal() {    
     return processLineTotal;
    }

    public void setRunStatistics(boolean runStatistics) {
      this.runStatistics = runStatistics;
    }
    public boolean getRunStatistics() {
      return this.runStatistics; 
    }
    
    public void setPreUploadStatistics(ArrayList<String> stats) {
      s_log.debug("preUploadStats:" + stats);
      preUploadStatistics = stats;
    }
    public ArrayList<String> getPreUploadStatistics() {
      return preUploadStatistics;
    }
    public void setPostUploadStatistics(ArrayList<String> stats) {
      s_log.debug("postUploadStats:" + stats);
      postUploadStatistics = stats;
    }
    public ArrayList<String> getPostUploadStatistics() {
      return postUploadStatistics;
    }
    
    /** A species or subspecies name this upload inserted into the taxon table (counted once). */
    public void countInsertedSpecies(String taxonName) {
      if (taxonName == null) return;
      insertedSpecies.add(taxonName);
      updatedSpecies.remove(taxonName);
    }
    /** A species or subspecies name whose existing taxon row this upload rewrote (counted once; a name the upload
     *  inserted itself stays inserted). */
    public void countUpdatedSpecies(String taxonName) {
      if (taxonName == null || insertedSpecies.contains(taxonName)) return;
      updatedSpecies.add(taxonName);
    }
    public int getInsertedSpeciesCount() {
      return insertedSpecies.size();
    }
    public int getUpdatedSpeciesCount() {
      return updatedSpecies.size();
    }
    public void countInsertedSpecimen() {
      ++countInsertedSpecimens;
    }
    public int getInsertedSpecimenCount() {
      return countInsertedSpecimens;
    }

    
    public MessageMgr getMessageMgr() {
      return messageMgr;
    }

    public int getRecordCount() {
      return recordCount;
    }
    public void setRecordCount(int recordCount) {
      this.recordCount = recordCount;
    }
      
}


