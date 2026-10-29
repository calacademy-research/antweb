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

/**
 * TaxonWorks uploads: the manual one (upload.do?action=taxonWorksUpload, the curate page's Upload Type,
 * run by SpecimenUploadJob) and the nightly CAS job (TaxonWorksNightly calls prepareFromArchive, then
 * uploadPreparedFile). Takes data.tsv from the archive, converts it with TaxonWorksTransformer to
 * workingDir/specimenTW<groupId>.txt and runs the shared steps in Uploader.uploadSpecimenFile.
 * Docs: docs/flows/specimen-upload.md (sections 2.2, 5). Tests: TaxonWorksNightlyDecisionsTest.
 */
public class TaxonWorksUploader extends Uploader {

    private static final Log s_log = LogFactory.getLog(TaxonWorksUploader.class);
    private static final Log s_antwebEventLog = LogFactory.getLog("antwebEventLog");

    public TaxonWorksUploader(Connection connection) {
        super(connection);
    }

    /**
     * The upload of a TaxonWorks file the upload page saved (UploadAction, SpecimenUploadJob): a
     * Darwin Core archive (zip, its data.tsv is used) or a data.tsv file. Called on the background
     * job's thread. originalName is the name the curator's file had.
     */
    public UploadDetails uploadSavedFile(File saved, String originalName, String action, Curator curator, String userAgent, String encoding)
      throws SQLException, TestException, AntwebException
    {
        String zipFileTarget = "data.tsv";
        Group group = curator.getGroup();
        String workingDir = AntwebProps.getWorkingDir();
        FileUtil.makeDir(workingDir);
        String fileName = "specimenTW" + group.getId() + ".txt";
        Path preTransformFilePath = Paths.get(workingDir, "specimenTW" + group.getId() + "_temp.txt");
        Path specimenFilePath = Paths.get(workingDir, fileName);

        A.log("uploadSavedFile() originalName:" + originalName + " preTransform:" + preTransformFilePath);

        try {
            if (originalName.toLowerCase().endsWith(".zip")) {
                String errorMsg = unzipSavedFile(saved, group, preTransformFilePath.toString(), zipFileTarget);
                if (errorMsg != null) return new UploadDetails(action, errorMsg, "message"); // archive refused: stop and show why
            } else {
                java.nio.file.Files.copy(saved.toPath(), preTransformFilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            s_log.error("uploadSavedFile() e:" + e);
            return new UploadDetails(action, "The zip file could not be read.", "message");
        }

        org.calacademy.antweb.util.BackgroundJobs.step("Converting the TaxonWorks file to an AntWeb specimen file.");
        TaxonWorksTransformer tf = new TaxonWorksTransformer();
        String errMsg = tf.transformFile(preTransformFilePath, specimenFilePath);
        if (!tf.getSkippedRecords().isEmpty()) {
            s_log.warn("uploadSavedFile() skipped " + tf.getSkippedRecords().size() + " malformed records: " + tf.getSkippedRecords());
        }
        if (errMsg != null) {
            s_log.error("uploadSavedFile() errMsg:" + errMsg);
            return new UploadDetails(action, errMsg, "message"); // stop here and show the message
        }

        return uploadSpecimenFile(action, fileName, curator, userAgent, encoding, true);
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