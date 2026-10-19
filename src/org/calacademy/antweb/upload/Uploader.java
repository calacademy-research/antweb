package org.calacademy.antweb.upload;

    
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.io.*;
import java.util.Date;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.apache.regexp.*;

import org.apache.struts.upload.FormFile;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class Uploader {

    private static final Log s_log = LogFactory.getLog(Uploader.class);
    private static final Log s_antwebEventLog = LogFactory.getLog("antwebEventLog");

    protected final Connection connection;
    
    public Uploader(Connection connection) {
      this.connection = connection;
    }

    // To be overridden;
    static String getZipFileTarget() {
      return "N/A";
    }


    // was 2nd param: String specimenUploadType,
    public UploadDetails uploadSpecimenFile(String operation, String fileName
            , Curator curator, String userAgent, String encoding, boolean isUpload)
            throws SQLException, TestException, AntwebException
    {
        A.log("uploadSpecimenFile() fileName:" + fileName + " encoding:" + encoding);

        UploadDetails uploadDetails = null;
        Date startTime = new Date();
        if ("default".equals(encoding)) encoding = null;
        Group group = curator.getGroup();
        String specimenFileLoc = null;
        String messageStr = null;
        UploadFile uploadFile = null;;

        if (isUpload) {
            //fileName = "specimen" + group.getId() + ".txt";
            uploadFile = new UploadFile(AntwebProps.getWorkingDir(), fileName, userAgent, encoding);
            specimenFileLoc = AntwebProps.getWorkingDir() + fileName;
            A.log("uploadSpecimenFile() isUpload:" + isUpload + " specimenFileLoc:" + specimenFileLoc);
        } else {
            SpecimenUploadDb specimenUploadDb = new SpecimenUploadDb(connection);
            fileName = specimenUploadDb.getBackupDirFile(group.getId());
            if (fileName == null) messageStr = "BackupDirFile not found for group:" + group;
            specimenFileLoc = AntwebProps.getWebDir() + fileName;
            uploadFile = new UploadFile(AntwebProps.getWebDir(), fileName, userAgent, encoding);
            A.log("uploadSpecimenFile() isUpload:" + isUpload + " specimenFileLoc:" + specimenFileLoc);
        }

        if (!uploadFile.correctEncoding(encoding)) messageStr = "The file is not in the chosen encoding (" + Html.esc(uploadFile.getEncoding()) + ").";

        if (messageStr != null) {
            // No further tests necessary.
        } else if (!fileName.contains(".txt") && !fileName.contains(".TXT")) {
            s_log.warn("uploadSpecimenFile() fileName not txt. fileName:" + fileName);
            messageStr = "Specimen File must be a .txt file.";
        } else if (isCurrentSpecimenFormat(specimenFileLoc) != null) {
            messageStr = "Specimen File must be in the most current format. " + isCurrentSpecimenFormat(specimenFileLoc);
        } else if (!Utility.isTabDelimited(specimenFileLoc)) {
            messageStr = "Specimen File must be a tab-delimited file.";
        } else if (!hasSpecimenCodeColumn(specimenFileLoc, uploadFile.getCharset())) {
            // Checked here, before importSpecimens() empties the group.
            messageStr = "Specimen File must have a SpecimenCode column in its header line.";
        }
        if (messageStr != null) {
            s_log.warn("uploadSpecimenFile() " + messageStr);
            uploadDetails = new UploadDetails("specimen", messageStr, "message");
            uploadDetails.setAction(operation);
            return uploadDetails;
        }

        s_log.info("uploadSpecimenFile() specimenFileLoc:" + specimenFileLoc + " isUpload:" + isUpload);
        //s_antwebEventLog.info("backupDirFile:" + backupDirFile;

        UploadHelper.init(uploadFile, group);

        //boolean success = false;  // UploadForm.getWhole() can be deprecated
        //LogMgr.logAntQuery(connection, "projectTaxaCountByProjectRank", "Before specimen upload Proj_taxon worldants counts");

        SpecimenUpload specimenUpload = new SpecimenUpload(connection);
        uploadDetails = specimenUpload.importSpecimens(uploadFile, curator, operation);

        if (uploadFile != null) {
            uploadFile.backup();
            uploadDetails.setBackupDirFile(uploadFile.getBackupDirFile());
        }

        String execTime = HttpUtil.getExecTime(startTime);
        uploadDetails.setExecTime(execTime);

        //A.log("uploadSpecimenFile() action:" + operation + " uploadDetails.operation:" + uploadDetails.getOperation());

        return uploadDetails;
    }


    /** Null if the header line looks like a current specimen file, otherwise what is wrong (never the line itself). */
    protected String isCurrentSpecimenFormat(String fileName) {
        try (BufferedReader in = new BufferedReader(new FileReader(fileName))) {
            return headerProblem(in.readLine());
        } catch (IOException e) {
            s_log.warn("isCurrentSpecimenFormat() e:" + e);
            return "The file could not be read.";
        }
    }

    /** What is wrong with a specimen file's header line, or null. The message never quotes the line. */
    public static String headerProblem(String theLine) {
        if (theLine == null) return "The file is empty.";
        if (theLine.contains("taxonomic history")) {
            return "This specimen file contains taxonomic history.  Is it maybe a species file?";
        }
        if (theLine.contains("/")) {
            return "The header line contains a '/', as in files from before AntWeb 4.13. Please use the current column names.";
        }
        return null;
    }

    /** True if a column of the header line is SpecimenCode (any case), the column every upload needs. */
    static boolean hasSpecimenCodeColumn(String fileLoc, java.nio.charset.Charset charset) {
        try (BufferedReader in = java.nio.file.Files.newBufferedReader(Paths.get(fileLoc),
                charset != null ? charset : java.nio.charset.StandardCharsets.UTF_8)) {
            return hasSpecimenCodeColumn(in.readLine());
        } catch (IOException | RuntimeException e) {
            s_log.warn("hasSpecimenCodeColumn() e:" + e);
            return false;
        }
    }

    public static boolean hasSpecimenCodeColumn(String header) {
        if (header == null) return false;
        for (String col : header.split("\t", -1)) {
            if ("specimencode".equals(col.replace("\uFEFF", "").trim().toLowerCase())) return true;
        }
        return false;
    }


    // Called by TaxonWorksUploader. Not by SpecimenUploader.
    // Takes zipFileTarget (data.tsv, occurrence.txt) from the top level of the uploaded zip into outName
    // (a full path), with SafeUnzip. Returns null when done, or the reason the archive was refused.
    protected static String copyAndUnzipFile(FormFile file, Group group, String outName, String zipFileTarget) throws IOException {
        if (file == null) return "Must choose a file for upload.";
        File zip = new File(outName + ".zip");
        A.log("copyAndUnzipFile() group:" + group.getId() + " outName:" + outName + " zipFileTarget:" + zipFileTarget);
        if (!Utility.copyFormFile(file, zip.getPath(), false)) return "The uploaded zip file could not be saved.";
        try {
            SafeUnzip.extract(zip, SafeUnzip.named(zipFileTarget), zipFileTarget + " file", new File(outName));
            return null;
        } catch (SafeUnzip.Refused r) {
            s_log.warn("copyAndUnzipFile() refused for group:" + group.getId() + " " + r.getMessage());
            return r.getMessage();
        } finally {
            if (!zip.delete()) s_log.warn("copyAndUnzipFile() could not delete " + zip);
        }
    }

}
