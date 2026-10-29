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

/**
 * Upload of a curator's AntWeb specimen file (upload.do?action=specimenUpload), run by the background job
 * SpecimenUploadJob: takes the one .txt file out of the saved zip with SafeUnzip (or copies a saved .txt) to
 * workingDir/specimen<groupId>.txt, then runs the shared steps in Uploader.uploadSpecimenFile.
 * Docs: docs/flows/specimen-upload.md (sections 2.1, 3). Tests: tests/e2e/test_specimen_upload_roundtrip.py.
 */
public class SpecimenUploader extends Uploader {

    private static final Log s_log = LogFactory.getLog(SpecimenUploader.class);
    private static final Log s_antwebEventLog = LogFactory.getLog("antwebEventLog");

    public SpecimenUploader(Connection connection) {
      super(connection);
    }

    /**
     * The upload of a file the upload page saved (UploadAction, SpecimenUploadJob): a zip with one
     * .txt file (checked by SafeUnzip on the page), or a .txt file. Called on the background job's
     * thread. originalName is the name the curator's file had.
     */
    public UploadDetails uploadSavedFile(File saved, String originalName, String action, Curator curator, String userAgent, String encoding)
      throws SQLException, IOException, RESyntaxException, TestException, AntwebException
    {
        Group group = curator.getGroup();
        String workingDir = AntwebProps.getWorkingDir();
        FileUtil.makeDir(workingDir);

        String specimenFileName = "specimen" + group.getId() + ".txt";

        if (originalName.toLowerCase().endsWith(".zip")) {
            String refused = unzipSpecimenFile(saved, specimenFileName);
            if (refused != null) {
                s_log.warn("uploadSavedFile() zip refused for group:" + group.getId() + " " + refused);
                UploadDetails refusedDetails = new UploadDetails("specimen", refused, "message");
                refusedDetails.setAction(action);
                return refusedDetails;
            }
        } else {
            java.nio.file.Files.copy(saved.toPath(), new File(workingDir + specimenFileName).toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        A.log("uploadSavedFile() action:" + action + " originalName:" + originalName);
        return uploadSpecimenFile(action, specimenFileName, curator, userAgent, encoding, true);
    }


    /**
     * Takes the one .txt file out of a saved zip as outName (in the working directory), with
     * SafeUnzip. Returns null when done, or the reason the archive was refused (nothing is then written).
     */
    static String unzipSpecimenFile(File zip, String outName) throws IOException {
        if (zip == null) return "Must choose a specimen file for upload.";
        try {
            SafeUnzip.extract(zip, SafeUnzip.ONE_TXT, ".txt file", new File(AntwebProps.getWorkingDir() + outName));
            return null;
        } catch (SafeUnzip.Refused r) {
            return r.getMessage();
        }
    }
}
