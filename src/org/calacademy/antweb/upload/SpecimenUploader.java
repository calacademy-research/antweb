package org.calacademy.antweb.upload;

    
import java.sql.*;
import java.io.*;
import java.util.Date;

import javax.servlet.http.*;

import org.apache.struts.action.*;

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

    public UploadDetails uploadSpecimenFile(UploadForm theForm, Login login, String userAgent, String encoding)
      throws SQLException, IOException, RESyntaxException, TestException, AntwebException
    {
        Group group = login.getGroup();
        //A.log("uploadSpecimenFile() encoding:" + encoding);    

        String formFileName = theForm.getBiota().getFileName();
 
        //String specimenUploadType = theForm.getSpecimenUploadType();

        Utility util = new Utility();      
        String outputFileDir = util.getInputFileHome();
        String specimenFileName = outputFileDir + "specimen" + group.getId() + ".txt";

        if (formFileName.indexOf("zip") != -1) {
            util.copyAndUnzipFile(theForm.getBiota(), outputFileDir + "group" + group.getId(), specimenFileName);
        } else {
            // copy from uploader's fileName to the biotaFile name.
            util.copyFile(theForm.getBiota(), specimenFileName);
        }

        // was 2nd param: specimenUploadType, 
        return uploadSpecimenFile("uploadSpecimenFile", formFileName, login, userAgent, encoding);
    }

    /* This version can be called directly in the case of specimenTest */    
    // was 2nd param: String specimenUploadType, 
    public UploadDetails uploadSpecimenFile(String operation, String formFileName
      , Login login, String userAgent, String encoding) 
      throws SQLException, IOException, RESyntaxException, TestException, AntwebException
    {
        if ("default".equals(encoding)) encoding = null;

        Group group = login.getGroup();
        
        //A.log("uploadSpecimenFile() specimenFileName:" + formFileName);
        
        UploadDetails uploadDetails = null;
        Utility util = new Utility();      
        String outputFileDir = AntwebProps.getInputFileHome();

        String specimenFileName = "specimen" + group.getId() + ".txt";
        String specimenFileLoc = outputFileDir + specimenFileName;

        UploadFile uploadFile = new UploadFile(outputFileDir, specimenFileName, userAgent, encoding);
        String backupDirFile = uploadFile.backup();

        A.log("uploadSpecimenFile() specimenFileName:" + specimenFileName + " specimenFileLoc:" + specimenFileLoc + " backupDirFile:" + backupDirFile);
        //s_antwebEventLog.info("backupDirFile:" + backupDirFile;

        String messageStr = null;
        if ((formFileName.indexOf(".txt") < 0) && (formFileName.indexOf(".TXT") < 0)) {
            s_log.warn("uploadSpecimenFile() theFileName not txt.  formFileName:" + formFileName);
            messageStr = "Specimen File must be a .txt file.";
        } else if (!uploadFile.correctEncoding(encoding)) {
            messageStr = "Encoding not validated for file:" + uploadFile.getFileLoc() + " encoding:" + uploadFile.getEncoding();
        } else if (!isCurrentSpecimenFormat(specimenFileLoc)) {
            messageStr = "Specimen File must be in the most current format.";
        } else if (!util.isTabDelimited(specimenFileLoc)) {
            messageStr = "Specimen File must be a tab-delimited file.";
        }
        if (messageStr != null) {
            s_log.warn("uploadSpecimenFile() " + messageStr);
            return new UploadDetails("specimen", messageStr, "message");
		}

        UploadHelper.init(uploadFile, group);

        //boolean success = false;  // UploadForm.getWhole() can be deprecated
        //LogMgr.logAntQuery(connection, "projectTaxaCountByProjectRank", "Before specimen upload Proj_taxon worldants counts");

        SpecimenUpload specimenUpload = new SpecimenUpload(connection);
		uploadDetails = specimenUpload.importSpecimens(uploadFile, login);
        //uploadDetails.setRequest(request);
        uploadDetails.setBackupDirFile(backupDirFile);


        //(new SpecimenDb(connection)).updateSpecimenStatus();
                   
		//s_log.warn("uploadSpecimenFile() specimenPostProcess = TRUE");
		                    
        //uploadDetails.setLogFileName(group.getAbbrev() + "SpecimenUpload.html");
		                    
        return uploadDetails;     
    }
    
    private boolean isCurrentSpecimenFormat(String fileName) {
        boolean isCurrentFormat = true;
        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            if (in == null) {
              s_log.error("isCurrentSpecimenFormat() BufferedReader is null for file:" + fileName);
              return false;
            }
            
            String theLine = in.readLine();
            if (theLine == null) {
              s_log.error("isCurrentSpecimenFormat() null line.  Perhaps empty file:" + fileName + "?");
              return false;
            }            

            //s_log.warn("isCurrentSpecimenFormat() testLine:" + theLine);
           
           if (theLine.contains("/")) {
              s_log.error("Line contains /.  Must be pre-Antweb 4.13.  The header:" + theLine);
              return false;
            } if (theLine.indexOf("taxonomic history") >= 0) {
              s_log.warn("This specimen file contains taxonomic history.  Is it maybe a species file?");
              return false;
            }
            // This line will determine if Antweb4.12            
            //if (theLine.contains(SpecimenNotes)) return true;

        } catch (Exception e) {
            s_log.error("isCurrentSpecimenFormat() fileName:" + fileName + " e:" + e);
            return false;
        }
        //s_log.warn("isCurrentFormat() mustContainStr:" + mustContainStr + " not found in file:" + fileName);
        return isCurrentFormat;
    }     
    
    
      
}