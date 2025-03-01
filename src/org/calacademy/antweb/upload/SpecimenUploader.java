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

        if (formFileName.contains("zip")) {
            String specimenFileName = "specimen" + group.getId() + ".txt"; // was: workingDir +
            String groupName = "group" + group.getId();
            //            A.log("uploadSpecimenFile() tempDirName:" + tempDirName + " specimenFileName:" + specimenFileName);
            copyAndUnzipFile(theForm.getTheFile(), groupName, specimenFileName);
            //copyAndUnzipFile(theForm.getTheFile(), group, tempDirName, specimenFileName);
        } else {
            // copy from uploader's fileName to the biotaFile name.
            String specimenFileName = workingDir + "specimen" + group.getId() + ".txt";
            A.log("uploadSpecimenFile() theFile:" + theForm.getTheFile() + " specimenFileName:" + specimenFileName);
            Utility.copyFormFile(theForm.getTheFile(), specimenFileName, true); // debugOn
        }

        boolean isUpload = true;
        // was 2nd param: specimenUploadType, 
        A.log("uploadSpecimenFile() action:" + theForm.getAction() + " formFileName:" + formFileName);
        String fileName = "specimen" + group.getId() + ".txt";
        return uploadSpecimenFile(theForm.getAction(), fileName, curator, userAgent, encoding, isUpload);
    }


    public static void copyAndUnzipFile(FormFile file, String groupName, String outName) throws IOException, AntwebException {
        Utility util = new Utility();

        if (file != null) {
            // create a new temp directory
            boolean success = new File(groupName).mkdir();

            // unzip into that directory
            String zippedName = outName + ".zip";
            String fullOutputPath = AntwebProps.getWorkingDir() + zippedName;
            util.copyFile(file, zippedName); //fullOutputPath)
            boolean exists = new File(fullOutputPath).exists();

            String tempDirName = AntwebProps.getWorkingDir() + groupName;

            A.log("copyAndUnzipFile() exists:" + exists + " zippedName:" + zippedName + " tempDirName:" + tempDirName + " outName:" + outName + " fullOutputPath:" + fullOutputPath);
            if (exists) {
                try {
                    String command = "unzip -o -d " + tempDirName + " " + fullOutputPath;  // zippedName;
                    A.log("copyAndUnzip() command:" + command);
                    Process process = Runtime.getRuntime().exec(command);

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        int n = 0;
                        while ((line = reader.readLine()) != null) {
                            ++n;
                            A.log("n:" + n + " line:" + line);
                        }
                        int exitCode = process.waitFor();
                        A.log("n:" + n + " exitCode:" + exitCode + " which:" + Runtime.getRuntime().exec("which unzip") + " which:" + Runtime.getRuntime().exec("unzip -v"));
                    }
                } catch (IOException e) {
                    s_log.error("copyAndUnzipFile() problem unzipping file1 " + zippedName + ": " + e);
                    AntwebUtil.logStackTrace(e);
                } catch (InterruptedException e) {
                    s_log.error("copyAndUnzipFile() problem unzipping file2 " + zippedName + ": " + e);
                    AntwebUtil.logStackTrace(e);
                }
            } else {
                A.log("Not existing:" + fullOutputPath);
                throw new AntwebException("NotFoundError unzipping:" +  zippedName);
            }

            // move the file out of that directory and give it the right name
            File tempDir = new File(tempDirName);
            A.log("copyAndUnzipFile() tempDir: " + tempDir);
            String[] dirListing = tempDir.list();
            A.log("copyAndUnzipFile() tempDir listing has length: " + dirListing.length);
            String fileName = "";
            for (String s : dirListing) {
                A.log("copyAndUnzipFile() tempDir listing shows: *" + s + "*");
                if (!s.equals(".") && !s.equals("..") && !s.contains("__")) {
                    fileName = s;
                }
            }

            String out = null;
            try {
                String in = tempDirName + "/" + fileName;
                out = AntwebProps.getWorkingDir() + "/" + outName;
                A.log("copyAndUnzipFile() in:" + in + " out:" + out);
                util.copyFile(in, out);
            } catch (IOException e) {
                s_log.error("copyAndUnzipFile() couldn't move " + tempDirName + "/" + fileName + " to " + outName);
                AntwebUtil.logShortStackTrace(e);
                throw e;
            }

            // remove the temporary directory
            boolean isDebug = false && AntwebProps.isDevMode();
            if (!isDebug) Utility.deleteDirectory(tempDir);
            A.log("copyAndUnzipFile() deleteDir:" + tempDir + " deleted:" + !isDebug);
        }
    }


    /*
    //Called from this (SpecimenUploader), but not by GBIFUploader or TaxonWorksUploader.
    private static void copyAndUnzipFile(FormFile file, Group group, String outName) throws IOException {

        String tempDirName = AntwebProps.getWorkingDir() + "group" + group.getId();

        Utility util = new Utility();

        boolean success = false;
        if (file != null) {
            // create a new temp directory
            success = new File(tempDirName).mkdir();

            // unzip into that directory
            String zippedName = outName + ".zip";
            util.copyFile(file, zippedName);

            File theFile = new File(AntwebProps.getWorkingDir() + zippedName);

            //A.log("copyAndUnzipFile() zippedName:" + zippedName + " exists:" +  new File(zippedName).exists() + " 2:" + new File(AntwebProps.getWorkingDir() + zippedName).exists() );
            if (theFile.exists()) {
            //if (new File(zippedName).exists()) {

                try {
                    String command = "unzip -d " + tempDirName + " " + zippedName;
                    A.log("copyAndUnzip() command:" + command);
                    Process process = Runtime.getRuntime().exec(command);
                    process.waitFor();
                } catch (IOException e) {
                    s_log.error("copyAndUnzipFile() problem unzipping file1 " + zippedName + ": " + e);
                    AntwebUtil.logStackTrace(e);
                } catch (InterruptedException e) {
                    s_log.error("copyAndUnzipFile() problem unzipping file2 " + zippedName + ": " + e);
                    AntwebUtil.logStackTrace(e);
                }
            } else {
                s_log.error("copyAndUnzipFile() file does not exist. zippedName:" + zippedName);
                // Perhaps, in /usr/local/antweb/workingDir
                //   there is now contained a directory structure: /usr/local/antweb/workingDir
            }

            // move the file out of that directory and give it the right name
            File dir = new File(tempDirName);
            String[] dirListing = dir.list();
            A.log("copyAndUnzipFile() zippedName:" + zippedName + " tempDirName:" + tempDirName + " zippedName:" + zippedName
                    + " dirListing.length:" + dirListing.length + " success:" + success);
            String fileName = "";
            for (String s : dirListing) {
                A.log("copyAndUnzipFile() dir listing shows: *" + s + "*");
                if (!s.equals(".") && !s.equals("..") && !s.contains("__")) {
                    fileName = s;
                }
            }
            try {
                A.log("copyAndUnizpFile() copying... tempDirName:" + tempDirName + "/" + fileName + " outFile:" + outName);
                util.copyFile(tempDirName + "/" + fileName, outName);
            } catch (IOException e) {
                s_log.error("copyAndUnzipFile() couldn't move " + tempDirName + "/" + fileName + " to " + outName);
                AntwebUtil.logShortStackTrace(e);
                throw e;
            }

            util.deleteDirectory(dir);

        } else {
            s_log.error("copyAndUnzipFile() file is null");
        }
    }
*/





}