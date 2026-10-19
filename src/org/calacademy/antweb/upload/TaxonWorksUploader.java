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
    public UploadDetails uploadSpecimenFile(UploadForm theForm, Curator curator, String userAgent, String encoding)
      throws SQLException, TestException, AntwebException
    {
        String zipFileTarget = "data.tsv";
        FormFile theFile = theForm.getTheFile();
        Group group = curator.getGroup();
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
            String errorMsg = null;
            try {
                errorMsg = copyAndUnzipFile(theFile, group, preTransformFilePath.toString(), zipFileTarget);
                //A.log("uploadSpecimenFile() post unzip");
            } catch (IOException e) {
                s_log.error("uploadSpecimenFile() copyAndUnzipFile e:" + e);
                errorMsg = "The zip file could not be read.";
            }
            if (errorMsg != null) return new UploadDetails(theForm.getAction(), errorMsg, "message"); // archive refused: stop and show why

        } else {
            // copy from uploader's fileName to the biotaFile name.
            boolean success = Utility.copyFile(theFile, preTransformFileName);
            A.log("uploadSpecimenFile() success:" + success + " taxonWorks:" + theFile + " preTransformFileName:" + preTransformFileName);
        }

        //A.log("Start TaxonWorks transformFile");
        TaxonWorksTransformer tf = new TaxonWorksTransformer();
        //A.log("uploadSpecimenFile() preTransformFilePath:" + preTransformFilePath + " specimenFilePath:" + specimenFilePath);
        String errMsg = tf.transformFile(preTransformFilePath, specimenFilePath);
        if (!tf.getSkippedRecords().isEmpty()) {
            s_log.warn("uploadSpecimenFile() skipped " + tf.getSkippedRecords().size() + " malformed records: " + tf.getSkippedRecords());
        }
        if (errMsg != null) {
            s_log.error("uploadSpecimenFile() errMsg:" + errMsg);
            return new UploadDetails(theForm.getAction(), errMsg, "message"); // stop here and show the message
        }
        //A.log("End TaxonWorks transformFile");

        return uploadSpecimenFile(theForm.getAction(), fileName, curator, userAgent, encoding, true);
    }


    
    
      

    /**
     * Unzip data.tsv from a TaxonWorks DwC archive and transform it into an AntWeb
     * specimen file, without a web form (used by the nightly TaxonWorks job).
     * Returns the transformed file, or throws with the reason.
     */
    public static Path prepareFromArchive(Path zipFile, Group group) throws AntwebException {
        String workingDir = AntwebProps.getWorkingDir();
        FileUtil.makeDir(workingDir);
        Path preTransform = Paths.get(workingDir, "specimenTW" + group.getId() + "_temp.txt");
        Path specimenFile = Paths.get(workingDir, "specimenTW" + group.getId() + ".txt");
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            ZipEntry entry = zip.getEntry("data.tsv");
            if (entry == null) throw new AntwebException("The TaxonWorks archive has no data.tsv");
            try (InputStream in = zip.getInputStream(entry); OutputStream out = new FileOutputStream(preTransform.toFile())) {
                IOUtils.copy(in, out);
            }
        } catch (IOException e) {
            throw new AntwebException("Could not read the TaxonWorks archive: " + e);
        }
        TaxonWorksTransformer transformer = new TaxonWorksTransformer();
        String errMsg = transformer.transformFile(preTransform, specimenFile);
        if (errMsg != null) throw new AntwebException(errMsg);
        s_lastSkipped = new java.util.ArrayList<>(transformer.getSkippedRecords());
        return specimenFile;
    }

    private static java.util.List<String> s_lastSkipped = new java.util.ArrayList<>();

    /** Records prepareFromArchive() left out because their column count was wrong. */
    public static java.util.List<String> getLastSkipped() {
        return s_lastSkipped;
    }

    /** Upload a specimen file made by prepareFromArchive(), as the given curator. */
    public UploadDetails uploadPreparedFile(Path specimenFile, Curator curator)
      throws SQLException, TestException, AntwebException {
        return uploadSpecimenFile("taxonWorksUpload", specimenFile.getFileName().toString(), curator
            , "AntWeb nightly TaxonWorks job", null, true);
    }
}