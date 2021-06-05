package org.calacademy.antweb;

import java.io.*;
import java.text.SimpleDateFormat;
import java.text.Normalizer;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.sql.Timestamp;

import org.apache.regexp.*;

import org.apache.struts.upload.FormFile;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/** Class utility keeps track of the information about a specific taxon */
public class Utility implements Serializable {
  
  private static Log s_log = LogFactory.getLog(Utility.class);

    

  public static boolean isNumber(String number) {
    try {
      int num = Integer.valueOf(number);

      //if (num > 0) 
      return true;

    } catch (NumberFormatException e) {
      //A.log("Utility.isNumber() e:" + e);
    }
    return false;  
  }
      
    public static int compareFloats(float f1, float f2, float delta)
    {
      if (Math.abs(f1 - f2) < delta)
      {
        return 0;
      } else {
        if (f1 < f2)
        {
            return -1;
        } else {
            return 1;
        }
      } 
    }
    public static int compareFloats(float f1, float f2)
    {
      return compareFloats(f1, f2, 0.001f);
    }

        
    public static String customTrim(String text, String toTrim) throws AntwebException {
        String trimText = text;
        try {
          if (text == null) return text;
          if ("".equals(text)) return text;
          if (toTrim.equals(text.substring(0,1))) {
            trimText = trimText.substring(1);
            //A.log("customTrim() cutFirst text:" + text + " trimText:" + trimText);
          }
          if (toTrim.equals(trimText.substring(0,1))) trimText = trimText.substring(1);
            //A.log("customTrim() cutFirstAgain text:" + text + " trimText:" + trimText);
        
          if (toTrim.equals(trimText.substring(trimText.length() - 1))) {
            trimText = trimText.substring(0, trimText.length() - 1);
            //A.log("customTrim() cutLast text:" + text + " trimText:" + trimText);
          }
          if (toTrim.equals(trimText.substring(trimText.length() - 1))) {
            trimText = trimText.substring(0, trimText.length() -1);        
            //A.log("customTrim() cutLastAgain text:" + text + " trimText:" + trimText);
          }
        } catch (StringIndexOutOfBoundsException e) {
          String message = "customTrim() trimText:" + trimText + " toTrim:" + toTrim + " e:" + e;
          throw new AntwebException(message);
        }
        //if (!trimText.equals(text)) A.log("customTrim() text:" + text + " trimText:" + trimText);   
        return trimText;
    }
    
    public boolean isTabDelimited(String fileName) {
        boolean isTabDelimited = false;
        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            if (in == null) {
              s_log.error("isTabDelimited() BufferedReader is null for file:" + fileName);
              return false;
            }
            
            String theLine = in.readLine();
            if (theLine == null) {
              s_log.error("isTabDelimited() null line.  Perhaps empty file:" + fileName + "?");
              return false;
            }            
            if (theLine.contains("\t")) return true;
        } catch (Exception e) {
            s_log.error("isTabDelimited() fileName:" + fileName + " e:" + e);
        }
        s_log.warn("No tab found in " + fileName);
        return isTabDelimited;
    }

    public static String firstLetters(String theString) {
      // Turn a string like "valid without fossil" into "vwf"
      String firstLetters = "";
      if ((theString == null) || ("".equals(theString))) return ".";
      
      firstLetters = theString.substring(0,1);
      int spaceIndex = theString.indexOf(" ");
      if (spaceIndex < 0) return firstLetters;
      theString = theString.substring(spaceIndex + 1);

      firstLetters += theString.substring(0,1);
      spaceIndex = theString.indexOf(" ");
      if (spaceIndex < 0) return firstLetters;
      theString = theString.substring(spaceIndex + 1);

      firstLetters += theString.substring(0,1);
      spaceIndex = theString.indexOf(" ");
      if (spaceIndex < 0) return firstLetters;
      theString = theString.substring(spaceIndex + 1);

      firstLetters += theString.substring(0,1);
      spaceIndex = theString.indexOf(" ");
      if (spaceIndex < 0) return firstLetters;
      theString = theString.substring(spaceIndex + 1);
	
      return firstLetters;      
    }

   public static boolean isASCII(String input) {
     if (input == null) return true;
     String ascii = getASCII(input);
     if (input.equals(ascii)) return true;
     return false;
   }

   public static String getASCII(String input) {  // returns true, false or the fixed input
      //A.log("isAllASCII string:" + input);
      if (input == null) return "true";
      boolean isASCII = true;
      StringBuffer output = new StringBuffer();
      for (int i = 0; i < input.length(); i++) {
        int c = input.charAt(i);
        //if (input.contains("fastigatus")) s_log.warn("c:" + c);
        if (c > 0x7F) {  // 0x7f is 127
          if (c == 160) {
            s_log.warn("isAllASCII() providing pass on 160 for input:" + input);
            output.append(" ");
            isASCII = false;
          } else {
            s_log.info("isAllASCII() false char:" + c + " or:-" + Character.toString ((char) c) + "- at col:" + i + " of input:" + input);
            return "false";            
          }
        } else {
          output.append(Character.toChars(c));
        }
      }
      if (isASCII) {
        return "true";
      } else {
        return output.toString();
      }
   }

   public static String makeASCII(String input) {
      //A.log("isAllASCII string:" + input);
      if (input == null) return null;
      StringBuffer output = new StringBuffer();
      for (int i = 0; i < input.length(); i++) {
        int c = input.charAt(i);
        if (c == 160) {
          //s_log.warn("makeASCII() fixing 160 for input:" + input);
          output.append(" ");
        } else {
          output.append(Character.toChars(c));
        }
      }
      return output.toString();
   }

    public static String asciiVal(String val) {
	   //This will separate all of the accent marks from the characters. Then, you just need to compare each character against being a letter and throw out the ones that aren't.
       String returnVal = Normalizer.normalize(val, Normalizer.Form.NFD);
       returnVal = returnVal.replaceAll("[^\\p{ASCII}]", "");
       return returnVal;
    }


/*
   public static boolean isAllASCII(String input) {
   A.log("isAllASCII string:" + input);
      if (input == null) return true;
      boolean isASCII = true;
      for (int i = 0; i < input.length(); i++) {
        int c = input.charAt(i);

        //if (input.contains("fastigatus")) s_log.warn("c:" + c);
        if (c > 0x7F) {  // 0x7f is 127
          if (c == 160) {
            s_log.warn("isAllASCII() providing pass on 160 for input:" + input);
          } else {
            isASCII = false;
            s_log.info("isAllASCII() false char:" + c + " or:-" + Character.toString ((char) c) + "- at col:" + i + " of input:" + input);
            break;
          }
        }
      }
      return isASCII;
   }
*/    

   public static String notBlankValue(String value) {
       if (Utility.notBlank(value)) {
         return value;
       } else {
         return "";
       }
   }

   public static String notBlankValue(float value) {
       if (value > 0 || value < 0) {
         return "" + value;
       } else {
         return "";
       }
   }
   
   public static String notBlankValue(Timestamp value) {
       if (value != null) {
         return "" + value;
       } else {
         return "";
       }
   }
      
   public static boolean notBlank(String theTerm) {
      boolean isNotBlank = true;
      if (theTerm == null) return false;
      theTerm = theTerm.trim();
      if ( (theTerm.length() <= 0) 
        || (theTerm.equals("null")) 
        || (theTerm.equals("NULL"))
        || (theTerm.equals("Null"))
        || (theTerm.equals("none"))
        || (theTerm.equals("0.0"))
        ) {
        isNotBlank = false;
      }
      return isNotBlank;
    }
/*
   public static boolean notBlank2(String theTerm) {

      theTerm = theTerm.trim();

      boolean isNotBlank = Utility.notBlank(theTerm);

      s_log.warn("notBlank2() theTerm:" + theTerm + "-");

      return isNotBlank;
    }
*/
   
   public static boolean isBlank(String theTerm) {
     return Utility.blank(theTerm);
   }
   public static boolean blank(String theTerm) {
           return !Utility.notBlank(theTerm);
   }

   // by toggling this flag we can display empty fields on the specimen, collection and locality pages
   private static boolean isDisplayEmpty = true;
   public static boolean displayEmptyOrNotBlank(String theTerm) {
     if (isDisplayEmpty || (Utility.notBlank(theTerm))) { 
       return true;
     }
     return false;
   }
   
   public static String andify(ArrayList theList) {
           return Utility.andify(theList, " and ");
   }
    
   public static String andify(ArrayList theList, String theJoin) {
      StringBuffer theString = new StringBuffer();
      Iterator iter = theList.iterator();

      while (iter.hasNext()) {
          theString.append((String) iter.next());
          if (iter.hasNext()) {
              theString.append(theJoin);
          }
      }
      return theString.toString();        
   }   
   
    public static String stripParams(String browserParams, String term) {
        String[] parts = browserParams.split("&");
        StringBuffer newParams = new StringBuffer();
        for (int loop=0; loop < parts.length; loop++) {
            if (!parts[loop].startsWith(term)) {
                newParams.append("&");
                newParams.append(parts[loop]);
            }
        }
        if (newParams.length() != 0) {
            newParams.deleteCharAt(0);
        }
        return newParams.toString();        
    }   
   

    public boolean badFileName(String fileName) {
       RE badFileCharacter;
       boolean result = false;
       if ((fileName == null) || (fileName.equals(""))) {
           result = true;
       } else {
           try {
               badFileCharacter = new RE("\\W");
               result = badFileCharacter.match(fileName);
           } catch (RESyntaxException e1) {
               s_log.error("badFileName() e:" + e1);
               e1.printStackTrace();
           }
       }
       return result;
    }
   
    public void fixNewLines(String fileName) {
        if (fileName != null) {
            s_log.info("fixNewLines() fixing new lines for " + fileName);
            try {
                RE nlSub = new RE("s/\r/\n/g");
                BufferedReader br = new BufferedReader(new FileReader(fileName));
                PrintStream  bw = new PrintStream(new FileOutputStream(fileName + ".tmp"));
 
                String line = null;
 
                while ((line = br.readLine()) != null) {
                    line = nlSub.subst(line,"\n");
                    bw.println(line);
                }
                br.close();
                bw.close();
                copyFile(fileName + ".tmp", fileName);
            } catch (IOException e) {
                s_log.error("fixNewLines() problem1 fileName:" + fileName + ": " + e);
                org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
            } catch (RESyntaxException e) {
                s_log.error("fixNewLines() problem2 fileName:" + fileName + ": " + e);
                org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
            }                                 
        }
    }
         
    public boolean fileContains(String fileName, String theString) {
        if (fileName != null) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(fileName)); 
                String line = null;
                while ((line = br.readLine()) != null) {
                    if (line.contains(theString)) return true;
                }
                br.close();
            } catch (IOException e) {
                s_log.error("fileContains() fileName:" + fileName + ": " + e);
            }                                 
        }
        return false;
    }

    public String unzipFile(String zipName, String unzipDir) {
        String command = "";
        if (zipName != null) {
            // create a new temp directory
            boolean success = (new File(unzipDir)).mkdir();
            
            if (new File(zipName).exists()) {
                try {
                    command = "unzip -d " + unzipDir + " " + zipName + "";
                    Process process = Runtime.getRuntime().exec(command);
                    //process.waitFor();
                    s_log.warn("unzipFile() command:" + command);  // exitValue:" + process.exitValue() + " 
                } catch (IOException e) {
                    s_log.error("problem unzipping file " + zipName + ": " + e);
                    org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
               // } catch (InterruptedException e) {
               //     s_log.error("problem unzipping file " + zipName + ": " + e);
               //     org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
               }
            }
        }
        return command;
    }
   
    public void copyAndUnzipFile(FormFile file, String tempDirName, String outName) {
        
        if (file != null) {
            // create a new temp directory
            boolean success = (new File(tempDirName)).mkdir();
            
            // unzip into that directory
            String zippedName = outName + ".zip";
            copyFile(file, zippedName);
            if (new File(zippedName).exists()) {
                try {
                    Process process = Runtime.getRuntime().exec(
                            "unzip -d " + tempDirName + " " + zippedName);
                    process.waitFor();
                } catch (IOException e) {
                    s_log.error("copyAndUnzipFile() problem unzipping file1 " + zippedName + ": " + e);
                    org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
                } catch (InterruptedException e) {
                    s_log.error("copyAndUnzipFile() problem unzipping file2 " + zippedName + ": " + e);
                    org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
                }
            }
            
            // move the file out of that directory and give it the right name
            File dir = new File(tempDirName);
            String dirListing[] = dir.list();
            s_log.info("copyAndUnzipFile() dir listing has length: " + dirListing.length);
            String fileName = "";
            for (int loop = 0; loop < dirListing.length; loop++) {
                s_log.info("copyAndUnzipFile() dir listing shows: *" + dirListing[loop] + "*");
                if(!(dirListing[loop].equals(".")) && !(dirListing[loop].equals("..")) && !(dirListing[loop].indexOf("__")!=-1)) {
                    fileName = dirListing[loop];
                }
            }
            try {
                copyFile(tempDirName + "/" + fileName, outName);
            } catch (IOException e) {
                s_log.error("copyAndUnzipFile() couldn't move " + tempDirName + "/" + fileName + " to " + outName);
                org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
            }
            
            // remove the directory
            deleteDirectory(dir);
        }
        
    }

    public boolean directoryExists(String directory) {
        return (new File(directory)).exists();
    }

    public static boolean makeDirTree(String dirTree) {
       boolean debug = false;
      boolean isSuccess = true;
      String[] splitDirTree = dirTree.split("/");
      //s_log.warn("splitDirTree:" + splitDirTree + " 1:" + splitDirTree[0] + " 2:" + splitDirTree[2]);
      String thisDir = "";
      for (int i = 1; i < splitDirTree.length; i++) {
        thisDir = thisDir += "/" + splitDirTree[i];
        File dirFile = new File(thisDir);
        if (!dirFile.exists()) {

          if (dirFile.toString().contains(".")) { 
            // This was contains.  Now equals.  If it is equals, then cached items will be
            // created as directories (See cacheItem()).  Wish I could remember why I made it equals.

            //s_log.warn("makeDirTree(" + dirTree + ") Contains period. dirFile:" + dirFile);       
            return true;

          }

          //if (dirTree.contains("2017")) AntwebUtil.logStackTrace();

          try {
            isSuccess = (dirFile).mkdir();
            if (isSuccess) {
                if (debug) A.log("makeDirTree() Success creating dir:" + thisDir);
            } else {
                //if (debug)
                s_log.warn("makeDirTree() Failure creating dir:" + thisDir);
            }
          } catch (Exception e1) {
             s_log.error("makeDirTree() Exception dir:" + thisDir + " e:" + e1);
             isSuccess = false;
          }
        } else {
            if (debug) {
                A.log("makeDirTree() already exists:" + thisDir);
            }
        }
      }
      return isSuccess;
    }
    
    
    public boolean createDirectory(String dirName) {
      // This one is relative to docRoot
        boolean isSuccess = false;
        String docRoot = this.getDocRoot();
        String directoryName = docRoot + dirName;

        File dirFile = new File(directoryName);

        if (dirFile.exists()) {
          //A.log("createDirectory() dirFile:" + dirFile.toString() + " already exists.  Returning success.");
          return true;
        }
        // Create a directory; all ancestor directories must exist
        try {
            isSuccess = (dirFile).mkdir();
            if (isSuccess) {
                s_log.warn("createDirectory() Success creating dir:" + directoryName);   
            } else {
                s_log.warn("createDirectory() Failure creating dir:" + directoryName);
            }
        } catch (Exception e1) {
            s_log.error("createDirectory() Exception dir:" + directoryName + " e:" + e1);
        }
        return isSuccess;
    }
    
    
    public boolean deleteDirectory(File dir) {
        
        s_log.info("deletingDirectory() " + dir.getName());
        
        if ((dir.exists() && (dir.getName().length() > 1))) {
            File[] files = dir.listFiles();
            for (int i=0; i < files.length; i++) {
                if(files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                } else {
                    files[i].delete();
                }
            }
        }
        return (dir.delete());
    }
    

    public boolean deleteFile(String fileName) {
        File file = new File(fileName);
        return deleteFile(file);
    }
    
    public boolean deleteFile(File file) {
        boolean result = false;
        if (file.exists()) {
            result = file.delete();
        }
        return result;
    }
    
    public boolean copyFile(FormFile file, String outName) {
        boolean returnVal = false;
        String data = null;

        if (file != null) {
            try {
                //retrieve the file data
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                InputStream stream = file.getInputStream();

                //write the file to the file specified
                OutputStream bos = new FileOutputStream(outName);
                int bytesRead = 0;
                byte[] buffer = new byte[8192];
                while ((bytesRead = stream.read(buffer, 0, 8192)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                }
                bos.close();

                //close the stream
                stream.close();
                returnVal = true;
            } catch (FileNotFoundException fnfe) {
                s_log.error("copyFile() " + fnfe);
            } catch (IOException ioe) {
                s_log.error("copyFile() " + ioe);
            }
        } else {
          s_log.error("Can not copy null file to outName:" + outName);
        }
        return returnVal;
    }

    public void backupFile(String src) throws IOException {
        File f = new File(src);
        if (f.exists()) {
            copyFile(src, src + ".bak");
        }
    }
    
    public boolean rollbackFile(String src) throws IOException {
        boolean success = false;
        String backup = src + ".bak";
        File f = new File(backup);
        if (f.exists()) {
            copyFile(backup, src);
            success = true;
        }
        return success;
    }
    
    
    public void moveFile(String src, String dst) throws IOException {
        copyFile(src, dst);
        deleteFile(src);
    }
    
    public void copyFile(String src, String dst) throws IOException {
      //A.log("copyFile(" + src + ", " + dst + ")");
      InputStream in = new FileInputStream(new File(src));
      OutputStream out = new FileOutputStream(new File(dst));

      // Transfer bytes from in to out
      byte[] buf = new byte[1024];
      int len;
      while ((len = in.read(buf)) > 0) {
        out.write(buf, 0, len);
      }
      in.close();
      out.close();
    }

    // To be deprecated
    public String getDocRoot() { 
      // Something like site.docroot=/usr/local/tomcat/webapps/antweb/
      return AntwebProps.getDocRoot(); 
    }
    public String getInputFileHome() { return AntwebProps.getInputFileHome(); }
    public String getGoogleKey() { return AntwebProps.getGoogleMapKey(); }
    public String getDomain() { return AntwebProps.getDomain(); }
    public String getDomainApp() { return AntwebProps.getDomainApp(); }    
    //public String getSiteUrl() { return AntwebProps.getSiteUrl(); }
    
    
    
    public String getDateForFileName() {
        return getDateForFileName(new Date());
    }
    
    public String getDateForFileName(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HH:mm:ss");
        String dateString = dateFormat.format(date);
        return dateString;    
    }
    
    public String getCurrentDateAndTimeString() {
        return getCurrentDateAndTimeString(new Date());
    }
    
    public String getCurrentDateAndTimeString(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateString = dateFormat.format(date);
        return dateString;
    }
   
    public static String getSimpleDate(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String dateString = dateFormat.format(date);
        return dateString;
    }
       
    public String getCurrentDate(Date date) {
        if (date == null) return "";
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyy");
        String dateString = dateFormat.format(date);
        return dateString;
    }   
    
    public void saveStringToFile(String theString, String theFile) {
        File outputFile = new File(theFile);
        try {
            FileWriter outFile = new FileWriter(outputFile);
            outFile.write(theString);
            outFile.close();
        } catch (IOException e) {
            s_log.error("saveStringToFile() problem saving to file:" + theFile + " e:" + e);
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
        }
    }

    public static boolean sameList(ArrayList<String> list1, String[] list2) {
      if (list1.size() != list2.length) return false;
      int i = 0;
      for (String list1Item : list1) {
        if (!list1Item.equals(list2[i])) {
          s_log.warn("sameList() listItem:" + list1Item + " listItem2:" + list2[i]);
          return false;
        }
        ++i;
      }
      return true;
    }  

}
