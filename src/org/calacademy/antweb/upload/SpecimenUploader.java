package org.calacademy.antweb.upload;

    
import java.sql.*;
import java.io.*;
import java.util.Date;

import org.apache.regexp.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class SpecimenUploader extends Uploader {

    private static final Log s_log = LogFactory.getLog(SpecimenUploader.class);
    private static final Log s_antwebEventLog = LogFactory.getLog("antwebEventLog");

    public SpecimenUploader(Connection connection) {
      super(connection);
    }

    // This gets called from an upload post.
    public UploadDetails uploadSpecimenFile(UploadForm theForm, Login login, String userAgent, String encoding)
      throws SQLException, IOException, RESyntaxException, TestException, AntwebException
    {
        Group group = login.getGroup();
        //A.log("uploadSpecimenFile() encoding:" + encoding);    

        String formFileName = theForm.getTheFile().getFileName();
        //String specimenUploadType = theForm.getSpecimenUploadType();

        Utility util = new Utility();      
        String workingDir = AntwebProps.getWorkingDir();
        FileUtil.makeDir(workingDir);
        String specimenFileName = workingDir + "specimen" + group.getId() + ".txt";

        if (formFileName.contains("zip")) {
            util.copyAndUnzipFile(theForm.getTheFile(), workingDir + "group" + group.getId(), specimenFileName);
        } else {
            // copy from uploader's fileName to the biotaFile name.
            A.log("uploadSpecimenFile() theFile:" + theForm.getTheFile() + " specimenFileName:" + specimenFileName);
            Utility.copyFormFile(theForm.getTheFile(), specimenFileName, true); // debugOn
        }

        boolean isUpload = true;
        // was 2nd param: specimenUploadType, 
        A.log("uploadSpecimenFile() action:" + theForm.getAction() + " formFileName:" + formFileName);
        String fileName = "specimen" + group.getId() + ".txt";
        return uploadSpecimenFile(theForm.getAction(), fileName, login, userAgent, encoding, isUpload);
    }
      
}