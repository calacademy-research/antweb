package org.calacademy.antweb.upload;

import java.io.File;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.text.*;

import org.calacademy.antweb.util.AntwebProps;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Upload {

    private static final Log s_log = LogFactory.getLog(Upload.class);

    private int id;
    private int uploadId;
    private int loginId;
    private String groupName;
    private int groupId;
    private String logFileName;
    private String backupDirFile;
    private Date created;
    
    private int specimens;
    private int collections;
    private int localities;
    private int subfamilies;
    private int genera;
    private int species;
    private int ungeoreferenced;
    private int flagged;

    // When the numbers above are the group's specimens as the nightly run counted them (GroupCountDb, 2026-10-22), the
    // time of that count; null when they are the upload's own numbers (as written at the upload).
    private Date countedAt;

    public Date getCountedAt() { return countedAt; }
    public void setCountedAt(Date countedAt) { this.countedAt = countedAt; }
    
    public Upload() {
    }

    public int getId() {
      return this.id;
    }
    public void setId(int id) {
      this.id = id;
    }

    public int getUploadId() {
      return this.uploadId;
    }
    public void setUploadId(int uploadId) {
      this.uploadId = uploadId;
    }

    public int getLoginId() {
      return this.loginId;
    }
    public void setLoginId(int loginId) {
      this.loginId = loginId;
    }

    public String getGroupName() {
      return this.groupName;
    }
    public void setGroupName(String groupName) {
      this.groupName = groupName;
    }
    
    public int getGroupId() {
      return this.groupId;
    }
    public void setGroupId(int groupId) {
      this.groupId = groupId;
    }

    public String getUploadDir() {
        String uploadDir = null;
        // Nov 11, 2019 we reimplemented the directory names.
        SimpleDateFormat sdfo = new SimpleDateFormat("yyyyMMdd"); 
        Date d1 = null;
        try {
          d1 = sdfo.parse("20191111"); // Coming from the log file format, for convenience.
        } catch (ParseException e) {
          s_log.warn("ParseException e:" + e);
        }
        if (getCreated() != null && d1 != null && (getCreated().after(d1) || getCreated().equals(d1))) {
          uploadDir = UploadDetails.getLogDir(getLogFileName());
          //if (getLogFileName().contains("pecimen")) uploadDir = "specimen";
          //if (getLogFileName().contains("orldants")) uploadDir = "worldants";
        } else {
          uploadDir = "upload";
        }        
        //A.log("getUploadDir() uploadDir:" + uploadDir + " d1:" + d1 + " created:" + getCreated() + " compare:" + getCreated().after(d1));
        return uploadDir;
    }

    /** The report's path under the web root, e.g. web/log/specimen/20261018-05:31:02-specimen1.html, or null. */
    public String getReportPath() {
        String name = getLogFileName();
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..")) return null;
        return "web/log/" + getUploadDir() + "/" + name;
    }

    /**
     * True if the upload report is still on the server. Reports written before the
     * Nov 2019 move (web/log/upload/) are mostly gone, so pages link a report only when
     * this is true. Answers are cached for an hour: the group pages list hundreds of
     * uploads and web/log may be on network storage.
     */
    public boolean hasReport() {
        return reportExists(AntwebProps.getDocRoot(), getReportPath(), System.currentTimeMillis());
    }

    static final long REPORT_CACHE_MILLIS = 60L * 60 * 1000;
    private static final Map<String, long[]> s_reportExists = new ConcurrentHashMap<>();

    /** docRoot + path exists, using a cached answer younger than REPORT_CACHE_MILLIS. */
    public static boolean reportExists(String docRoot, String path, long now) {
        if (docRoot == null || path == null) return false;
        String full = docRoot + path;
        long[] cached = s_reportExists.get(full);
        if (cached != null && now - cached[0] < REPORT_CACHE_MILLIS) return cached[1] == 1;
        boolean exists = new File(full).isFile();
        s_reportExists.put(full, new long[] {now, exists ? 1 : 0});
        return exists;
    }

    public String getLogFileName() {
      return this.logFileName;
    }

    /** The uploaded file as kept on the server (upload.backup_dir_file), e.g. upload/20220224-11:49:08-specimen2.txt. */
    public String getBackupDirFile() {
      return this.backupDirFile;
    }
    public void setBackupDirFile(String backupDirFile) {
      this.backupDirFile = backupDirFile;
    }

    /**
     * The uploaded file's name in web/upload/ (what uploadFile.do?file= takes), or null when the upload
     * has none or it is not a plain file name. 2026-10-20: the upload list links each upload's file
     * (the reports written before 2026-10-17 link /web/upload/, which is not served).
     */
    public String getUploadedFileName() {
      String name = getBackupDirFile();
      if (name == null || !name.endsWith(".txt")) return null;   // also "null": an upload stopped before its file was kept
      if (name.startsWith("upload/")) name = name.substring("upload/".length());
      return UploadFileAction.isValidName(name) ? name : null;
    }

    /** True if the uploaded file is still on the server (cached for an hour, like the report). */
    public boolean hasUploadedFile() {
      String name = getUploadedFileName();
      return name != null && reportExists(AntwebProps.getDocRoot(), "web/upload/" + name, System.currentTimeMillis());
    }
    public void setLogFileName(String logFileName) {
      this.logFileName = logFileName;
    }

    public Date getCreated() {
        return this.created;
    }
    public void setCreated(Date created) {
        this.created = created;
    }
    
    public String getCountsStr() {
      return "Specimens:" + getSpecimens()
        + " Collections:" + getCollections()
        + " Localities:" + getLocalities()
        + " Subfamilies:" + getSubfamilies()
        + " Genera:" + getGenera()
        + " Species:" + getSpecies()
        + " Ungeoreferenced:" + getUngeoreferenced()
        + " Flagged:" + getFlagged();
    }
    
    public int getSpecimens() {
      return specimens;
    }
    public void setSpecimens(int specimens) {
      this.specimens = specimens;
    }
    public int getCollections() {
      return collections;
    }
    public void setCollections(int collections) {
      this.collections = collections;
    }
    public int getLocalities() {
      return localities;
    }
    public void setLocalities(int localities) {
      this.localities = localities;
    }
    public int getSubfamilies() {
      return subfamilies;
    }
    public void setSubfamilies(int subfamilies) {
      this.subfamilies = subfamilies;
    }
    public int getGenera() {
      return genera;
    }
    public void setGenera(int genera) {
      this.genera = genera;
    }
    public int getSpecies() {
      return species;
    }
    public void setSpecies(int species) {
      this.species = species;
    }
    public int getUngeoreferenced() {
      return ungeoreferenced;
    }
    public void setUngeoreferenced(int ungeoreferenced) {
      this.ungeoreferenced = ungeoreferenced;
    }
    public int getFlagged() {
      return flagged;
    }    
    public void setFlagged(int flagged) {
      this.flagged = flagged;
    }

}
