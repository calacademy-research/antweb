package org.calacademy.antweb.upload;

    
import java.sql.*;
import java.io.*;

import org.apache.regexp.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class SpecimenUploader {

    private static Log s_log = LogFactory.getLog(SpecimenUploader.class);
    private static final Log s_antwebEventLog = LogFactory.getLog("antwebEventLog");

    private Connection connection = null;
    
    public SpecimenUploader(Connection connection) {
      this.connection = connection;
    }


    // This gets called from an upload post.
    public UploadDetails uploadSpecimenFile(UploadForm theForm, Login login, String userAgent, String encoding)
      throws SQLException, IOException, RESyntaxException, TestException, AntwebException
    {
        Group group = login.getGroup();
        //A.log("uploadSpecimenFile() encoding:" + encoding);    

        String formFileName = theForm.getBiota().getFileName();
        //String specimenUploadType = theForm.getSpecimenUploadType();

        Utility util = new Utility();      
        String workingDir = AntwebProps.getWorkingDir();
        FileUtil.makeDir(workingDir);
        String specimenFileName = workingDir + "specimen" + group.getId() + ".txt";

        if (formFileName.contains("zip")) {
            util.copyAndUnzipFile(theForm.getBiota(), workingDir + "group" + group.getId(), specimenFileName);
        } else {
            // copy from uploader's fileName to the biotaFile name.
            util.copyFile(theForm.getBiota(), specimenFileName);
        }

        boolean isUpload = true;
        // was 2nd param: specimenUploadType, 
        return uploadSpecimenFile(theForm.getAction(), formFileName, login, userAgent, encoding, isUpload);
    }

    /* This version can be called directly in the case of specimenTest */    
    // was 2nd param: String specimenUploadType, 
    public UploadDetails uploadSpecimenFile(String operation, String formFileName
      , Login login, String userAgent, String encoding, boolean isUpload)
      throws SQLException, IOException, RESyntaxException, TestException, AntwebException
    {
        s_log.info("uploadSpecimenFile() specimenFileName:" + formFileName + " encoding:" + encoding);

        UploadDetails uploadDetails = null;

        java.util.Date startTime = new java.util.Date();
        if ("default".equals(encoding)) encoding = null;
        Group group = login.getGroup();
        Utility util = new Utility();

        String specimenFileLoc = null;
        String specimenFileName = null;

        String messageStr = null;

        UploadFile uploadFile = null;

        if (isUpload) {
            specimenFileName = "specimen" + group.getId() + ".txt";
            uploadFile = new UploadFile(AntwebProps.getWorkingDir(), specimenFileName, userAgent, encoding);
            specimenFileLoc = AntwebProps.getWorkingDir() + specimenFileName;
        } else {
            SpecimenUploadDb specimenUploadDb = new SpecimenUploadDb(connection);
            specimenFileName = specimenUploadDb.getBackupDirFile(group.getId());
            if (specimenFileName == null) messageStr = "BackupDirFile not found for group:" + group;
            specimenFileLoc = AntwebProps.getWebDir() + specimenFileName;
            uploadFile = new UploadFile(AntwebProps.getWebDir(), specimenFileName, userAgent, encoding);
        }

        if (!uploadFile.correctEncoding(encoding)) messageStr = "Encoding not validated for file:" + uploadFile.getFileLoc() + " encoding:" + uploadFile.getEncoding();

        if (messageStr != null) {
            // No further tests necessary.
        } else if ((!formFileName.contains(".txt")) && (!formFileName.contains(".TXT"))) {
            s_log.warn("uploadSpecimenFile() theFileName not txt.  formFileName:" + formFileName);
            messageStr = "Specimen File must be a .txt file.";
        } else if (isCurrentSpecimenFormat(specimenFileLoc) != null) {
            messageStr = "Specimen File must be in the most current format. " + isCurrentSpecimenFormat(specimenFileLoc);
        } else if (!util.isTabDelimited(specimenFileLoc)) {
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
            uploadDetails.setBackupDirFile(uploadFile.backup());
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