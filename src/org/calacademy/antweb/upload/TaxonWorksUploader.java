package org.calacademy.antweb.upload;

    
import java.sql.*;
import java.io.*;
import java.util.Date;

import org.apache.regexp.*;

import org.apache.struts.upload.FormFile;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class TaxonWorksUploader {

    private static final Log s_log = LogFactory.getLog(SpecimenUploader.class);
    private static final Log s_antwebEventLog = LogFactory.getLog("antwebEventLog");

    private final Connection connection;
    
    public TaxonWorksUploader(Connection connection) {
      this.connection = connection;
    }


    // This gets called from an upload post.
    public UploadDetails uploadSpecimenFile(UploadForm theForm, Login login, String userAgent, String encoding)
      throws SQLException, IOException, RESyntaxException, TestException, AntwebException
    {
        Group group = login.getGroup();
        //A.log("uploadSpecimenFile() encoding:" + encoding);    

        String formFileName = theForm.getTaxonWorks().getFileName();
        //String specimenUploadType = theForm.getSpecimenUploadType();

        Utility util = new Utility();      
        String workingDir = AntwebProps.getWorkingDir();
        FileUtil.makeDir(workingDir);
        String specimenFileName = workingDir + "specimenTW" + group.getId() + ".txt";

        A.log("workingDir:" + workingDir + " specimenFileName:" + specimenFileName + " formFileName:" + formFileName + " group:" + group);

        if (formFileName.contains("zip")) {
            try {
                copyAndUnzipFile(theForm.getTaxonWorks(), workingDir + "group" + group.getId(), specimenFileName);
            } catch (java.io.IOException e) {
                s_log.warn("Trapped IOException. Proceeding as if. e:" + e);
                //s_log.error("copyAndUnzipFile() problem unzipping file1 " + specimenFileName + " e:" + e);
            }
        } else {
            // copy from uploader's fileName to the biotaFile name.
            Utility.copyFile(theForm.getTaxonWorks(), specimenFileName);
        }

        boolean isUpload = true;
        // was 2nd param: specimenUploadType,

        // HARD CODED. Zip needs to work above the create this file.
        specimenFileName = "data.tsv";

        return uploadSpecimenFile(theForm.getAction(), specimenFileName, login, userAgent, encoding, isUpload);
    }


    public static void copyAndUnzipFile(FormFile file, String tempDirName, String outName) throws IOException {

        Utility util = new Utility();
        if (file != null) {
            // create a new temp directory
            boolean success = new File(tempDirName).mkdir();

            // unzip into that directory
            String zippedName = outName + ".zip";
            util.copyFile(file, zippedName);
            if (new File(zippedName).exists()) {
                try {
                    Process process = Runtime.getRuntime().exec(
                            "unzip -d " + tempDirName + " " + zippedName);
                    process.waitFor();
                } catch (InterruptedException e) {
                    s_log.error("copyAndUnzipFile() problem unzipping file2 " + zippedName + ": " + e);
                    AntwebUtil.logStackTrace(e);
                }
            }

            // move the file out of that directory and give it the right name
            File dir = new File(tempDirName);
            String[] dirListing = dir.list();
            s_log.info("copyAndUnzipFile() dir listing has length: " + dirListing.length);
            String fileName = "";
            for (String s : dirListing) {
                s_log.info("copyAndUnzipFile() dir listing shows: *" + s + "*");
                if (!s.equals(".") && !s.equals("..") && !s.contains("__")) {
                    fileName = s;
                }
            }
            try {
                util.copyFile(tempDirName + "/" + fileName, outName);
            } catch (IOException e) {
                s_log.error("copyAndUnzipFile() couldn't move " + tempDirName + "/" + fileName + " to " + outName);
                AntwebUtil.logStackTrace(e);
            }

            // remove the directory
            util.deleteDirectory(dir);
        }
    }


    /* This version can be called directly in the case of specimenTest */    
    // was 2nd param: String specimenUploadType, 
    public UploadDetails uploadSpecimenFile(String operation, String fileName
      , Login login, String userAgent, String encoding, boolean isUpload)
      throws SQLException, IOException, RESyntaxException, TestException, AntwebException
    {
        A.log("uploadSpecimenFile() fileName:" + fileName + " encoding:" + encoding);

        UploadDetails uploadDetails = null;

        Date startTime = new Date();
        if ("default".equals(encoding)) encoding = null;
        Group group = login.getGroup();

        String specimenFileLoc = null;
        String specimenFileName = null;

        String messageStr = null;

        UploadFile uploadFile = null;

        if (isUpload) {
            specimenFileName = "specimen" + group.getId() + ".txt";
            uploadFile = new UploadFile(AntwebProps.getWorkingDir(), specimenFileName, userAgent, encoding);
            specimenFileLoc = AntwebProps.getWorkingDir() + specimenFileName;
            A.log("uploadSppecimenFile() isUpload:" + isUpload + " specimenFileLoc:" + specimenFileLoc);
        } else {
            SpecimenUploadDb specimenUploadDb = new SpecimenUploadDb(connection);
            specimenFileName = specimenUploadDb.getBackupDirFile(group.getId());
            if (specimenFileName == null) messageStr = "BackupDirFile not found for group:" + group;
            specimenFileLoc = AntwebProps.getWebDir() + specimenFileName;
            uploadFile = new UploadFile(AntwebProps.getWebDir(), specimenFileName, userAgent, encoding);
            A.log("uploadSppecimenFile() isUpload:" + isUpload + " specimenFileLoc:" + specimenFileLoc);
        }

        if (!uploadFile.correctEncoding(encoding)) messageStr = "Encoding not validated for file:" + uploadFile.getFileLoc() + " encoding:" + uploadFile.getEncoding();

        if (messageStr != null) {
            // No further tests necessary.
        } else if (!specimenFileName.contains(".txt") && !specimenFileName.contains(".TXT")) {
            s_log.warn("uploadSpecimenFile() theFileName not txt. specimenFileName:" + specimenFileName);
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
		uploadDetails = specimenUpload.importSpecimens(uploadFile, login);
        uploadDetails.setAction(operation);

        if (uploadFile != null) {
            uploadFile.backup();
            uploadDetails.setBackupDirFile(uploadFile.getBackupDirFile());
        }

        String execTime = HttpUtil.getExecTime(startTime);
        uploadDetails.setExecTime(execTime);

		//s_log.warn("uploadSpecimenFile() specimenPostProcess = TRUE");

        return uploadDetails;     
    }
    
    private String isCurrentSpecimenFormat(String fileName) {
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
    
    
      
}