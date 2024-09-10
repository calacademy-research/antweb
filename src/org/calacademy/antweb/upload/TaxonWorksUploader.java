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

public class TaxonWorksUploader extends Uploader {

    private static final Log s_log = LogFactory.getLog(TaxonWorksUploader.class);
    private static final Log s_antwebEventLog = LogFactory.getLog("antwebEventLog");

    public TaxonWorksUploader(Connection connection) {
        super(connection);
    }

    // This gets called from an upload post.
    public UploadDetails uploadSpecimenFile(UploadForm theForm, Login login, String userAgent, String encoding)
      throws SQLException, TestException, AntwebException
    {
        String zipFileTarget = "data.tsv";
        FormFile theFile = theForm.getTheFile();
        Group group = login.getGroup();
        String formFileName = theFile.getFileName();
        String workingDir = AntwebProps.getWorkingDir();
        FileUtil.makeDir(workingDir);
        String fileName = "specimenTW" + group.getId() + ".txt";
        String preTransformFileName = "specimenTW" + group.getId() + "_temp.txt";
        Path preTransformFilePath = Paths.get(workingDir, preTransformFileName);
        Path specimenFilePath = Paths.get(workingDir, fileName);

        //A.log("uploadSpecimenFile() workingDir:" + workingDir);
        A.log("uploadSpecimenFile() formFileName:" + formFileName + " preTransform:" + preTransformFilePath.toString());

        if (formFileName.endsWith(".zip")) {
            boolean success = true;
            try {
                copyAndUnzipFile(theFile, group, preTransformFilePath.toString(), zipFileTarget);
            } catch (IOException e) {
                success = false;
            }
            if (!success) {
                s_log.warn("Do we really want to proceed despite failure?");
                if (AntwebProps.isDevMode()) throw new AntwebException("copyAndUnzip error DEV MODE break.");
            }
        } else {
            // copy from uploader's fileName to the biotaFile name.
            boolean success = Utility.copyFile(theFile, preTransformFileName);
            A.log("uploadSpecimenFile() success:" + success + " taxonWorks:" + theFile + " preTransformFileName:" + preTransformFileName);
        }

        //A.log("Start TaxonWorks transformFile");
        TaxonWorksTransformer tf = new TaxonWorksTransformer();
        A.log("uploadSpecimenFile() preTransformFilePath:" + preTransformFilePath + " specimenFilePath:" + specimenFilePath);
        tf.transformFile(preTransformFilePath, specimenFilePath);
        //A.log("End TaxonWorks transformFile");

        String action = theForm.getAction();
        A.log("uploadSpecimenFile() action:" + action);

        return uploadSpecimenFile(action, fileName, login, userAgent, encoding, true);
    }


    /* This version can be called directly in the case of specimenTest */    
    // was 2nd param: String specimenUploadType, 
    public UploadDetails uploadSpecimenFile(String operation, String fileName
            , Login login, String userAgent, String encoding, boolean isUpload)
            throws SQLException, TestException, AntwebException
    {
        A.log("uploadSpecimenFile() fileName:" + fileName + " encoding:" + encoding);

        UploadDetails uploadDetails;

        Date startTime = new Date();
        if ("default".equals(encoding)) encoding = null;
        Group group = login.getGroup();

        String specimenFileLoc;
        String specimenFileName;

        String messageStr = null;

        UploadFile uploadFile;

        if (isUpload) {
            specimenFileName = fileName;
//            specimenFileName = "specimen" + group.getId() + ".txt";
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
		uploadDetails = specimenUpload.importSpecimens(uploadFile, login, operation);

        if (uploadFile != null) {
            uploadFile.backup();
            uploadDetails.setBackupDirFile(uploadFile.getBackupDirFile());
        }

        String execTime = HttpUtil.getExecTime(startTime);
        uploadDetails.setExecTime(execTime);

        A.log("uploadSpecimenFile() action:" + operation + " uploadDetails.operation:" + uploadDetails.getOperation());

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