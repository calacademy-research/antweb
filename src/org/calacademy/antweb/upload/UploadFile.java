package org.calacademy.antweb.upload;

import java.io.*;
import java.util.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.*;

import org.calacademy.antweb.util.UriEncoder;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class UploadFile {
    private static final Log s_log = LogFactory.getLog(UploadFile.class);

    String encoding;
    String userAgent;
    String base;
    String fileName;
    String root;
    boolean isBioRegion = false;
    
    // Usually this file encapsulates details of an actual file uploaded.
    boolean isReload = false;

    public boolean getIsReload() {
      return isReload;
    }
    public void setIsReload(boolean reload) { 
      isReload = reload; 
    }
        
    public static final String s_speciesListTail = "_speciesList.txt";
    public static String getSpeciesListTail() { 
        return s_speciesListTail; 
    }
    // deprecated.  In Species list home pages.  Do not remove.
    public static String getSpeciesListFileTail() { 
      return getSpeciesListTail();
    }
    
    
    public UploadFile() {
    }

    public UploadFile(String base, String fileName, String userAgent, String encoding) {

        if (false && AntwebProps.isDevMode()) {
            A.log("constructing UploadFile() 1 base:" + base + " fileName:" + fileName + " userAgent:" + userAgent);
            AntwebUtil.logShortStackTrace();
        }

        this.userAgent = userAgent;  // we don't actually use this, at all.

        this.base = base;
        this.fileName = fileName;
        String fileLoc = base + fileName;

        figureEncoding(fileLoc, encoding);
    }
    
    private void figureEncoding(String fileLoc, String encoding) {
        // Always UTF-8 (2026-10-28): a file that is not is refused (firstNonUtf8Line); a chosen encoding is ignored.
        setEncoding("UTF-8");
    }

    /*
      //encoding = "ISO8859_1"; // No hyphen?  Not: ISO-8859-1?
      
      // These files are Jack Longino's (Utah).
      //if (fileLoc.contains("specimen2.txt")) {
          // this.encoding = "UTF-8";
      //    encoding = "MacRoman"; // Was, up until Dec 29, 2015.  
          // this.encoding = "ISO-8859-1";
      //} else {
            
      //  if (true) { //UploadFile.isValidUTF8(fileLoc)) {
          encoding = "UTF-8";
          A.log("figureEncoding() fileLoc:" + fileLoc + " isValidUTF8:" + fileLoc);
      //  }        
      //}

      //if (isWorldAnts()) encoding = "UTF-8";

      //if (correctEncoding(encoding) == false) {
      //  setEncoding("ISO8859_1");
      //}  
*/


    // To be ported towards use of UploadFile
    // This is used for Project upload, but not of Worldants

    public boolean isWorldAnts() {
        return fileName != null && fileName.contains("worldants");
    }

    public static boolean isValidUTF8(String filePath) {
      //final byte[] bytes) {
      boolean isValid = false;
      Path path = null;
      boolean isAllBytesRead = false;
      try {
      
        path = Paths.get(filePath);

        // Dying on this line in dev.  Not throwing exception.  Not returning.  Just ending.  WTF.
        byte[] bytes = Files.readAllBytes(path);

        isAllBytesRead = true;

        try {
          Charset.availableCharsets().get("UTF-8").newDecoder().decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException e) {
          s_log.error("isValidUTF8() e:" + e);
          return false;
        }

        isValid = true;

      } catch (IOException e) {
        s_log.warn("isValidUTF8() iIOException:" + e + " sValid:" + isValid + " path:" + path) ;
        isValid = false;
      } finally {
        if (!isAllBytesRead) s_log.error("isValidUTF8() *** finally isAllBytesRead:" + isAllBytesRead + " filePath:" + filePath + " isValid:" + isValid);
      }

      return isValid;
    }
    
    public String getEncoding() {
      return encoding;
    }    
    public void setEncoding(String encoding) {
      this.encoding = encoding;
    }

    public Charset getCharset() {
        return Charset.forName(encoding);
    }

    /** The note shown with the upload form and in the refusal (Brian Fisher, 2026-10-28). */
    public static final String UTF8_NOTE = "Files must be UTF-8 (Excel: Save As, CSV UTF-8).";

    /**
     * Upload files are read as UTF-8 only (Brian Fisher, 2026-10-28; the encoding chosen on the old upload form was
     * ignored, and the old check passed whenever it failed). Returns the number of the first line (the header is
     * line 1) that is not valid UTF-8, or 0 when the whole file is. A UTF-8 byte order mark is valid.
     */
    public static int firstNonUtf8Line(InputStream in) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        BufferedInputStream bin = new BufferedInputStream(in, 1 << 16);
        ByteArrayOutputStream line = new ByteArrayOutputStream(512);
        int lineNum = 1;
        int b;
        while ((b = bin.read()) != -1) {
            if (b == '\n') {
                if (!isUtf8(decoder, line)) return lineNum;
                line.reset();
                ++lineNum;
            } else {
                line.write(b);
            }
        }
        if (line.size() > 0 && !isUtf8(decoder, line)) return lineNum;
        return 0;
    }

    public static int firstNonUtf8Line(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return firstNonUtf8Line(in);
        }
    }

    private static boolean isUtf8(CharsetDecoder decoder, ByteArrayOutputStream line) {
        try {
            decoder.reset();
            decoder.decode(ByteBuffer.wrap(line.toByteArray()));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    /** The refusal of a file that is not UTF-8 (line: firstNonUtf8Line). */
    public static String notUtf8Message(int line) {
        return "The file is not UTF-8: line " + line + " (the header is line 1) has a character that is not valid UTF-8. " + UTF8_NOTE;
    }


    // From: usr/local/antweb/web/upload/20220627-17:55:08-specimen31.txt   to: specimen31.txt
    private String m_fileNameBase = null;
    public String getFileNameBase() {
        if (m_fileNameBase == null) {
            String base = getFileLoc();
            if (base.contains("specimen")) base = base.substring(base.indexOf("specimen"));
            if (base.contains("worldants")) base = base.substring(base.indexOf("worldants"));

            //A.log("getFileNameBase() fileLoc:" + getFileLoc() + " base:" + base);
            m_fileNameBase = base;
        }
        return m_fileNameBase;
    }


    // Used for the Taxon source during specimen upload. Something like specimen1.txt
    // From: usr/local/antweb/web/upload/20220627-17:55:08-specimen31.txt   to: 20220627-17:55:08-specimen31.txt
    private String m_shortFileName = null;
    public String getShortFileName() {
      if (m_shortFileName == null) {
          String shortFileName = getFileLoc();
          while (shortFileName.contains("/")) {
              shortFileName = shortFileName.substring(shortFileName.indexOf("/") + 1);
          }

          //if (!shortFileName.equals(getFileName())) s_log.error("getShortFileName() shortFileName:" + shortFileName + " does not equal fileName:" + getFileName());
          // And it should.  When they are proven equal, we may get rid of this method.

          A.log("getShortFileName() fileLoc:" + getFileLoc() + " shortFileName:" + shortFileName);
          m_shortFileName = shortFileName;
      }
      return m_shortFileName;
    }

    public String getFileLoc() {
      return this.base + this.fileName;
    }
    
    public String getFileName() {
      return this.fileName;
    }
    
    public boolean isUTF8() {
      return encoding.equals("UTF-8");
    }
    public boolean isMacRoman() {
      return encoding.equals("MacRoman");
    }
    public boolean isIso() {
      return encoding.equals("ISO8859_1");
    }

    public String getRoot() {
      return this.root;
    }
    public void setRoot(String root) {
      this.root = root;
    }
    
    public boolean getIsBioRegion() {
        return isWorldAnts() || this.isBioRegion;
    }
    public void setIsBioRegion(boolean isBioRegion) {
      this.isBioRegion = isBioRegion;
    }
 
    public boolean exists() {
      File f = new File(getFileLoc());    
      return f.exists();
    }
    
    public void backup() {
      File file = new File(getFileLoc());
      s_log.info("backup() exists:" + exists() + " fileLoc:" + getFileLoc() + " exists:" + file.exists());

      if (!exists()) {
        s_log.warn("backup() fileLoc does not exist:" + getFileLoc());
      } else {
          String fullWebDir = Utility.getDocRoot() + "web";
          String fullWebUploadDir = fullWebDir + "/upload";
          //String backupWorkingDir = util.getInputFileHome() + "/backup";
          Utility.makeDirTree(fullWebUploadDir);
          s_log.info("backup() makeDirTree:" + fullWebUploadDir);

          String tempBackupDirFile = fullWebUploadDir + "/" + getBackupFileName();
          A.log("backup() date:" + Utility.getDateForFileName() + " base:" + getFileNameBase());   // + " short:" + getShortFileName()
          try {
            s_log.info("backup() " + getFileLoc() + " to " + tempBackupDirFile);
            Utility.copyFile(getFileLoc(), tempBackupDirFile);
          } catch (IOException e) {
            s_log.error("backup() e:" + e);
          }
      }
    }

    String backupFileName = null;
    public String getBackupFileName() {
      if (backupFileName == null) {
          backupFileName = Utility.getDateForFileName() + "-" + getFileNameBase();  //getShortFileName();
      }
      return backupFileName;
    }

    // This is what gets persisted in the specimen table.
    public String getBackupDirFile() {
        return "upload/" + getBackupFileName();
    }


}
