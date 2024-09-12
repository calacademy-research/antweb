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

public class GBIFUploader extends Uploader {

    private static final Log s_log = LogFactory.getLog(GBIFUploader.class);
    private static final Log s_antwebEventLog = LogFactory.getLog("antwebEventLog");

    public GBIFUploader(Connection connection) {
      super(connection);
    }


    // This gets called from an upload post.
    public UploadDetails uploadSpecimenFile(UploadForm theForm, Login login, String userAgent, String encoding)
      throws SQLException, TestException, AntwebException
    {
        String zipFileTarget = "occurrence.txt";
        Group group = login.getGroup();
        FormFile theFile = theForm.getTheFile();
        String formFileName = theFile.getFileName();
        String workingDir = AntwebProps.getWorkingDir();
        FileUtil.makeDir(workingDir);
        String fileName = "specimenGBIF" + group.getId() + ".txt";
        String preTransformFileName = "specimenGBIF" + group.getId() + "_temp.txt";
        Path preTransformFilePath = Paths.get(workingDir, preTransformFileName);
        Path specimenFilePath = Paths.get(workingDir, fileName);

        //A.log("uploadSpecimenFile() workingDir:" + workingDir + " group:" + group);
        A.log("uploadSpecimenFile() formFileName:" + formFileName + " preTransform:" + preTransformFilePath.toString());

        if (formFileName.endsWith(".zip")) {
            String errorMsg = null;
            try {
                errorMsg = copyAndUnzipFile(theForm.getTheFile(), group, preTransformFilePath.toString(), zipFileTarget);
            } catch (IOException e) {
                errorMsg = "copyAndUnzipFile() e:" + e.toString();
            }
            if (errorMsg != null) throw new AntwebException(errorMsg);

        } else {
            // copy from uploader's fileName to the biotaFile name.
            boolean success = Utility.copyFile(theForm.getTheFile(), preTransformFileName);
            A.log("uploadSpecimenFile() success:" + success + " theFile:" + theForm.getTheFile() + " preTransformFileName:" + preTransformFileName);
        }

        //A.log("Start GBIF transformFile");
        GBIFTransformer tf = new GBIFTransformer();
//        A.log("uploadSpecimenFile() preTransformFilePath:" + preTransformFilePath + " specimenFilePath:" + specimenFilePath);
        String errMsg = tf.transformFile(preTransformFilePath, specimenFilePath);
        if (errMsg != null) {
            s_log.error("uploadSpecimenFile() errMsg:" + errMsg);
            return new UploadDetails(theForm.getAction(), errMsg);
        }
        //A.log("End GBIF transformFile");

        return uploadSpecimenFile(theForm.getAction(), fileName, login, userAgent, encoding, true);
    }

    static String getZipFileTarget() {
        return "occurence.txt";
    }

}