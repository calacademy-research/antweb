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


import org.apache.struts.upload.FormFile;

public class SpecimenUploader extends Uploader {

    private static final Log s_log = LogFactory.getLog(SpecimenUploader.class);
    private static final Log s_antwebEventLog = LogFactory.getLog("antwebEventLog");

    public SpecimenUploader(Connection connection) {
      super(connection);
    }

    // This gets called from an upload post.
    public UploadDetails uploadSpecimenFile(UploadForm theForm, Curator curator, String userAgent, String encoding)
      throws SQLException, IOException, RESyntaxException, TestException, AntwebException
    {
        Group group = curator.getGroup();
        //A.log("uploadSpecimenFile() encoding:" + encoding);    

        String formFileName = theForm.getTheFile().getFileName();
        //String specimenUploadType = theForm.getSpecimenUploadType();

        String workingDir = AntwebProps.getWorkingDir();
        FileUtil.makeDir(workingDir);

        String specimenFileName;

        if (formFileName.endsWith(".zip")) {  // was: contains("zip")
            specimenFileName = "specimen" + group.getId() + ".txt"; // was: workingDir +
            String refused = unzipSpecimenFile(theForm.getTheFile(), specimenFileName);
            if (refused != null) {
                s_log.warn("uploadSpecimenFile() zip refused for group:" + group.getId() + " " + refused);
                UploadDetails refusedDetails = new UploadDetails("specimen", refused, "message");
                refusedDetails.setAction(theForm.getAction());
                return refusedDetails;
            }
        } else {
            // copy from uploader's fileName to the biotaFile name.
            specimenFileName = workingDir + "specimen" + group.getId() + ".txt";
            A.log("uploadSpecimenFile() theFile:" + theForm.getTheFile() + " specimenFileName:" + specimenFileName);
            Utility.copyFormFile(theForm.getTheFile(), specimenFileName, true); // debugOn
        }

        boolean isUpload = true;
        // was 2nd param: specimenUploadType, 
        A.log("uploadSpecimenFile() action:" + theForm.getAction() + " formFileName:" + formFileName);
        String fileName = specimenFileName;  // was: "specimen" + group.getId() + ".txt"
        return uploadSpecimenFile(theForm.getAction(), fileName, curator, userAgent, encoding, isUpload);
    }


    /**
     * Saves the uploaded zip in the working directory and takes its one .txt file out as
     * outName (in the working directory), with SafeUnzip. Returns null when done, or the
     * reason the archive was refused (nothing is then written).
     */
    static String unzipSpecimenFile(FormFile file, String outName) throws IOException {
        if (file == null) return "Must choose a specimen file for upload.";
        String workingDir = AntwebProps.getWorkingDir();
        File zip = new File(workingDir + outName + ".zip");
        if (!Utility.copyFormFile(file, zip.getPath())) return "The uploaded zip file could not be saved.";
        try {
            SafeUnzip.extract(zip, SafeUnzip.ONE_TXT, ".txt file", new File(workingDir + outName));
            return null;
        } catch (SafeUnzip.Refused r) {
            return r.getMessage();
        } finally {
            if (!zip.delete()) s_log.warn("unzipSpecimenFile() could not delete " + zip);
        }
    }
}
