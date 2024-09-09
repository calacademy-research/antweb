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

    public static boolean copyAndUnzipFile(FormFile file, Group group, String outName, String zipFileTarget) throws IOException {
        boolean success = true;

        String tempDirName = AntwebProps.getWorkingDir() + "group" + group.getId();
        File tempDir = new File(tempDirName);
        A.log("copyAndUnzipFile() file:" + file + " tempDirName:" + tempDirName + " outName:" + outName + " zipFileTarget:" + zipFileTarget);

        if (file != null) {
            // create a new temp directory
            success = tempDir.mkdir();
            A.log("copyAndUnzipFile() tempDirName:" + tempDirName + " mkdirSuccess:" + success);

            // unzip into that directory
            String zippedName = outName + ".zip";
            boolean debugOn = true;
            Utility.copyFormFile(file, zippedName, debugOn);
            if (new File(zippedName).exists()) {
                try {
                    String command = "unzip -d " + tempDirName + " " + zippedName;
                    A.log("copyAndUnzipFile before command:" + command);
                    Process process = Runtime.getRuntime().exec(command);
                    process.waitFor();
                    A.log("copyAndUnzipFile after");
                } catch (InterruptedException e) {
                    s_log.error("copyAndUnzipFile() problem unzipping file2 " + zippedName + ": " + e);
                    AntwebUtil.logStackTrace(e);
                }
            } else {
                s_log.error("copyAndUnzipFile() zip does not exist:" + zippedName);
            }

            // move the file out of that directory and give it the right name
            //File dir = new File(tempDirName);
            String[] dirListing = tempDir.list();
            A.log("copyAndUnzipFile() dir:" + tempDir + " listing has length: " + dirListing.length);

            String target = tempDirName + "/" + zipFileTarget;
            try {
                Utility.copyFile(target, outName);
            } catch (IOException e2) {
                s_log.error("copyAndUnzipFile() couldn't move " + target + " to " + outName + " e:" + e2);
                success = false;
            }

            boolean isDeleted = false;
            // remove the directory
            //if (!AntwebProps.isDevMode())    // Helpful to test, diagnose, but must be off to operate correctly.
            isDeleted = Utility.deleteDirectory(tempDir);
            A.log("copyAndUnzipFile deleteDir:" + tempDir + " success:" + isDeleted);
        }
        return success;
    }
      
}