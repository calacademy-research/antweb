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


    /* This version can be called directly in the case of specimenTest */
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

        if (!uploadFile.correctEncoding(encoding)) messageStr = "Encoding not validated for file:" + uploadFile.getFileLoc() + " encoding:" + uploadFile.getEncoding();

        if (messageStr != null) {
            // No further tests necessary.
        } else if (!fileName.contains(".txt") && !fileName.contains(".TXT")) {
            s_log.warn("uploadSpecimenFile() fileName not txt. fileName:" + fileName);
            messageStr = "Specimen File must be a .txt file.";
        } else if (isCurrentSpecimenFormat(specimenFileLoc) != null) {
            messageStr = "Specimen File must be in the most current format. " + isCurrentSpecimenFormat(specimenFileLoc);
        } else if (!Utility.isTabDelimited(specimenFileLoc)) {
            messageStr = "Specimen File must be a tab-delimited file.";
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


    protected String isCurrentSpecimenFormat(String fileName) {
        String error = null;
        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            if (in == null) {
                error = "BufferedReader is null for file:" + fileName;
                //s_log.error("isCurrentSpecimenFormat() error:" + error);
            }

            String theLine = in.readLine();
            if (theLine == null) {
                error = "null line.  Perhaps empty file:" + fileName + "?";
                //s_log.error("isCurrentSpecimenFormat() error:" + error);
            }

            //s_log.warn("isCurrentSpecimenFormat() testLine:" + theLine);

            if (theLine.contains("/")) {
                error = "Line contains /.  Must be pre-Antweb 4.13.  The header:" + theLine;
            } if (theLine.contains("taxonomic history")) {
                error = "This specimen file contains taxonomic history.  Is it maybe a species file?";
                //s_log.warn(error);
            }
            // This line will determine if Antweb4.12
            //if (theLine.contains(SpecimenNotes)) return true;

        } catch (Exception e) {
            error = "fileName:" + fileName + " e:" + e;
            //s_log.error("isCurrentSpecimenFormat() error:" + error);
        }
        //s_log.warn("isCurrentFormat() mustContainStr:" + mustContainStr + " not found in file:" + fileName);
        return error;
    }


    // Called by TaxonWorksUploader and GBIFUploader. Not by SpecimenUploader.
    protected static String copyAndUnzipFile(FormFile file, Group group, String outName, String zipFileTarget) throws IOException {
        String errorMsg = null;

        String tempDirName = AntwebProps.getWorkingDir() + "group" + group.getId();
        File tempDir = new File(tempDirName);
        A.log("copyAndUnzipFile() file:" + file + " tempDirName:" + tempDirName + " outName:" + outName + " zipFileTarget:" + zipFileTarget);

        if (file != null) {
            // create a new temp directory
            boolean success = tempDir.mkdir();
            //A.log("copyAndUnzipFile() tempDirName:" + tempDirName + " mkdirSuccess:" + success);

            // unzip into that directory
            String zippedName = outName + ".zip";
            boolean debugOn = true;
            Utility.copyFormFile(file, zippedName, debugOn);
            if (new File(zippedName).exists()) {
                try {
                    String command = "unzip -d -o " + tempDirName + " " + zippedName;
                    //A.log("copyAndUnzipFile() before command:" + command);
                    Process process = Runtime.getRuntime().exec(command);
                    process.waitFor();
                    //A.log("copyAndUnzipFile() after");
                } catch (InterruptedException e) {
                    errorMsg = "copyAndUnzipFile() problem unzipping file2 " + zippedName + ": " + e;
                    s_log.error(errorMsg);
                    return errorMsg;
                }
            } else {
                return ("copyAndUnzipFile() zip does not exist:" + zippedName);
            }

            // move the file out of that directory and give it the right name
            //String[] dirListing = tempDir.list();
            //A.log("copyAndUnzipFile() tempDir:" + tempDir + " dirListing.length: " + dirListing.length);

            String target = tempDirName + "/" + zipFileTarget;
            //A.log("copyAndUnzipFile() target:" + target + " zipFileTarget:" + zipFileTarget);
            try {
                //A.log("copyAndUnzipFile() target:" + target + " outName:" + outName);
                Utility.copyFile(target, outName);
            } catch (IOException e2) {
                return "copyAndUnzipFile() couldn't move target:" + target + " to " + outName + " e:" + e2;
            }

            // remove the temporary directory
            boolean isDebug = false && AntwebProps.isDevMode();
            if (!isDebug) Utility.deleteDirectory(tempDir);
            A.log("copyAndUnzipFile() deleteDir:" + tempDir + " deleted:" + !isDebug);
        }
        return errorMsg;
    }



}