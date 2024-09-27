package org.calacademy.antweb.util;

import java.io.*;
import java.util.*;

import java.text.*;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.*;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


import org.apache.struts.action.*;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.AddressException;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.Utility;
import org.calacademy.antweb.util.AntwebUtil;

import java.util.concurrent.ThreadLocalRandom;

public abstract class AntwebUtil {

  private static final Log s_log = LogFactory.getLog(AntwebUtil.class);

  private static final Log s_antwebEventLog = LogFactory.getLog("antwebEventLog");

  private static final HashMap<String, Integer> s_logHash = new HashMap<>();

  public static void main(String[] args) {
    // To execute:    ant antwebUtil
    /*
    String t = "Dlussky, Wappler &amp; Wedmann, 2009<br>";
    String t1 = java.net.URLEncoder.encode(t);
    String t2 = null;
    try { 
      t2 = org.apache.commons.httpclient.util.URIUtil.encodePath(t, "ISO-8859-1");
    } catch (org.apache.commons.httpclient.URIException e) {
      AntwebUtil.log("main() e:" + e);
    }
    AntwebUtil.log("AntwebUtil - t1:" + t1 + " t2:" + t2);
    */
    /*
    AntwebUtil.reLog("Testing 1");
    AntwebUtil.reLog("Testing 1");
    AntwebUtil.reLog("Testing 2");    
    AntwebUtil.reLogDone();
    */
    DateUtil.runTests();
  }


  public static boolean isEmpty(String value) {
    if (value == null || "".equals(value) || "null".equals(value)) return true;
    return false;
  }

  public static String getAsciiSum(String text) {
    if (text == null) return "0";
    int charSum = 0;
    for(char c : text.toCharArray()) {
      int ascii = c;
      charSum = charSum + ascii;
    }
    //s_log.warn("getAsciiSum() charSum:" + charSum + " text:" + text);
    String sum = Integer.valueOf(charSum).toString();
    return sum;
  }

    public static int getCaseNumber() {
      return AntwebUtil.getRandomNumber();
    }             

    private static long s_uniqueNum = 0;
    public static long getUniqueNumber() {
      s_uniqueNum = s_uniqueNum + 1;
      return s_uniqueNum;
    }

    /*
   // Get Random Number
    private static long s_lastMillis = 0;
	public static int getRandomNumber() {
      long millis = System.currentTimeMillis();
      if (millis == s_lastMillis) {
        s_log.error("Millis was not unique. millis:" + millis);
        millis = millis + 1;
      }
      s_lastMillis = millis;
      return (int) (millis % Integer.MAX_VALUE);
	}
*/
   public static int getRandomNumber() {
     int random = ThreadLocalRandom.current().nextInt();
     //A.log("getRandomNumber() random:" + random);
     return random;
   }

    private static int s_secureCode = 0;
    public static int getSecureCode() {
      s_secureCode = getRandomNumber();
      return s_secureCode;
    }
    public static boolean isSecureCode(int secureCode) {
      if (s_secureCode == 0) return false;
      return s_secureCode == secureCode;
    }
    public static void resetSecureCode() {
      s_secureCode = 0;
    }

    //public static void adminAlert(String message, Connection connection) { // use AdminAlertMgr.add(String, connection);
    //  AdminAlertMgr.add(message, connection);
    //}		  	
  
  
    public static void eventLog(String message) {  
      s_antwebEventLog.info(message);
    }

    public static void log(String message) {
      s_log.info(message);
    }
  
  public static void log(String level, String message) {
    /* Convenience method to facillitate error messages in logs from JSPs 
       sample usage:
       
       AntwebUtil.log("error", "paypalError.jsp error: " + errorMessage);

    */
    if (level == null) {
      s_log.error("null AntwebUtil.log() level");
      return;
    }

    if (level.equals("debug")) {
      s_log.debug(message);        
      return;
    }

    if (level.equals("info")) {
      s_log.info(message);
      return;
    }
    
    if (level.equals("warn")) {
      s_log.warn(message);        
      return;
    }
    
    if (level.equals("error")) {
      s_log.error(message);        
      return;
    }

    s_log.error("Unsupported AntwebUtil.log() level: " + level + " " + message);
  }


  //Convenience method
  public static ActionForward returnMessage(String message, ActionMapping mapping, HttpServletRequest request) {
    request.setAttribute("message", message);
    return mapping.findForward("message");
  }


  // Useful for logging stack traces. Only need one. Server restart will reset.
  private static boolean isFirstTime = true;
  public static boolean isFirstTime() {
    if (isFirstTime) {
      isFirstTime = false;
      return true;
    }
    return false;
  }
  public static void setIsFirstTime(boolean firstTime) {
    isFirstTime = firstTime;
  }
  private static boolean isFirstStackTrace = true;
  public static void logFirstStackTrace() {
    if (AntwebProps.isDevMode() && AntwebUtil.isFirstStackTrace) {
      isFirstStackTrace = false;
      AntwebUtil.logStackTrace();
    }
  }
  private static boolean isFirstLog = true;
  public static void logFirst(String log) {
    if (AntwebProps.isDevMode() && AntwebUtil.isFirstLog) {
      isFirstLog = false;
      AntwebUtil.log(log);
    }
  }


  // pass in a directoy and get a list of all of the files.  Not recursive.
  static ArrayList<String> getDirFiles(File aFile) {
    ArrayList<String> dirFiles = new ArrayList<>();
    if (aFile.isDirectory()) {
      File[] listOfFiles = aFile.listFiles();
      if(listOfFiles!=null) {
        for (File listOfFile : listOfFiles) dirFiles.add(listOfFile.getName());
      }
    }
    return dirFiles;
  }

  public static String getReleaseNum() {
    String releaseNumJsp = AntwebProps.getDocRoot() + "documentation/releaseNum.jsp";  

    //A.log("getReleaseNum() releaseNumJsp:" + releaseNumJsp);

    String content = AntwebUtil.readFile(releaseNumJsp);
    if (content == null) {
      s_log.warn("getReleaseNum() content is null for releaseNumJsp:" + releaseNumJsp);
      return "8.x";
    }
    int quoteI = content.indexOf("\"") + 1;
    int quoteJ = content.indexOf("\"", quoteI);
    //A.log("getReleaseNum() quoteI:" + quoteI + " QuoteJ:" + quoteJ);
    String releaseNum = content.substring(quoteI, quoteJ);

    //A.log("getReleaseNum() content:" + content + " quoteI:" + quoteI + " QuoteJ:" + quoteJ);
    return releaseNum;
  }


  // http://localhost/antweb/web/upload/
  // Still in use. 
  public static ArrayList<String> getUploadDirFiles() {
    String dir = AntwebProps.getWebDir()+ "upload/";
    s_log.debug("getUploadDirFiles() dir:" + dir);
    File aFile = new File(dir);
    ArrayList<String> dirFiles = getDirFiles(aFile);
    return dirFiles;
  }

  public static ArrayList<String> getUploadDirFiles(String name) {
    ArrayList<String> qualifiedFiles = new ArrayList<>();
    ArrayList<String> files = getUploadDirFiles();
    for (String file : files) {
      String kind = file.substring(18); // everything after the date
      kind = kind.substring(0, kind.indexOf(".txt"));
      if (kind.equals(name)) {
        qualifiedFiles.add(file);  
        //s_log.warn("getUploadDirFiles(" + name + ") file:" + file + " name:" + name);  
      }    
    }
    return qualifiedFiles;
  }

  private static ArrayList<Integer> s_uploadGroupList;
  public static ArrayList<Integer> getUploadGroupList() {
    if (s_uploadGroupList != null) {
      A.log("getUploadGroupList() found:" + s_uploadGroupList.size());
      return s_uploadGroupList;
    }

    Date start = new Date();

    s_uploadGroupList = new ArrayList<>();
    ArrayList<String> files = getUploadDirFiles();
    for (String file : files) {
      if (file != null && file .length() < 18){
        A.log("getUploadGroupList() File is short:" + file);
        continue;
      }
      String kind = file.substring(18); // everything after the date
      kind = kind.substring(0, kind.indexOf(".txt"));

      int specIndex = kind.indexOf("specimen") + 8;
      if (specIndex == 8) {
        // TaxonWorks uploads have the filename specimenTW<id>, so we need to trim the TW out
        if (kind.indexOf("specimenTW") == 0) {
          specIndex = "specimenTW".length();
        }
        if (kind.indexOf("specimenGBIF") == 0) {
          specIndex = "specimenGBIF".length();
        }
        String groupId = null;
        Integer groupIdInteger = 0;
        try {
            groupId = kind.substring(specIndex);
            groupIdInteger = Integer.valueOf(groupId);
            //A.log("groupIdInteger:" + groupIdInteger);
        } catch (NumberFormatException e) {
            s_log.error("getUploadGroupList() kind:" + kind + " specIndex:" + specIndex + " groupId:" + groupId + " e:" + e);
        }
        if (!s_uploadGroupList.contains(groupIdInteger)) s_uploadGroupList.add(groupIdInteger);
      } else {
        if (kind != null && !kind.contains("worldants")) A.log("getUploadGroupList() ! specIndex:" + specIndex + " file:" + file + " kind:" + kind);
      }
    }
    Collections.sort(s_uploadGroupList);

    A.log("getUploadGroupList() done in " + AntwebUtil.reportTime(start));
    return s_uploadGroupList;
  }

    public static void writeXmlFile(Document doc, String fileName) {
      try {
        // Prepare the DOM document for writing
        Source source = new DOMSource(doc);

        // Prepare the output file
        s_log.warn("writeXmlFile() creating:" + fileName);
        File file = new File(fileName);
        
        FileOutputStream fos = null;
        try {
          fos = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
          s_log.warn("writeXmlFile() e:" + e);
          return;
        }
        Result result = new StreamResult(fos);

        Transformer transformer = TransformerFactory.newInstance().newTransformer();

       // transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.ENCODING,"UTF-8"); //ISO-8859-1
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        
        transformer.transform(source, result);
      } catch (TransformerException e) {
        s_log.error("writeXml()  e:" + e);
      }
    }    
    
  public static void writeDataFile(String file, String stringData) {
      String docRoot = AntwebProps.getDocRoot();
      String dataRoot = docRoot + "web/data/";
      String dataFile = dataRoot + file;
      Utility.makeDirTree(dataFile);
      s_log.debug("writeDataFile() 1 dataRoot:" + dataRoot + " file:" + file);
      AntwebUtil.writeFile(dataFile, stringData);
  }

  public static void writeDataFile(String dir, String file, String stringData) {
      String docRoot = AntwebProps.getDocRoot();
      String dataRoot = docRoot + dir;
      Utility.makeDirTree(dataRoot);
      String dataFile = dataRoot + file;
      s_log.warn("writeDataFile() 2 dataRoot:" + dataRoot + " file:" + file);
      AntwebUtil.writeFile(dataFile, stringData);
  }

  public static void writeFile(String file, String stringData) {
    try {
      FileUtils.writeStringToFile(new File(file), stringData);
    } catch (Exception e) {
      s_log.error("writeFile() file:" + file + " e:" + e);
    }
  }

  public static String readFile(String dir, String fileName) {
    String dirFile = AntwebProps.getDocRoot() + dir + fileName;
    return readFile(dirFile);
  }
  
  public static String readFile(String fileName) {

   //A.log("readFile:" + fileName);

   StringBuilder text = new StringBuilder();
   try {
    String NL = System.getProperty("line.separator");
     try (Scanner scanner = new Scanner(new FileInputStream(fileName))) {
       while (scanner.hasNextLine()) {
         text.append(scanner.nextLine()).append(NL);
       }
     }
   } catch(FileNotFoundException e) {
     return null;
   } catch(Exception e) {
     s_log.error("readFile() fileName:" + fileName + " e:" + e);
   }
   return text.toString();
  }
  
  public static void remove(String fullFileName) {
    File file = new File(fullFileName);
    //s_log.warn("remove fileName:" + fullFileName);
    //file.renameTo(new File(fullFileName + "X"));    
    file.delete();
  }
  
    public static boolean fileFound(String fileLoc) {
  	  File f = new File(fileLoc);
      return f.exists();
    }   
    
    public static boolean webFileFound(String fileLoc) {
      String webFileLoc = AntwebProps.getDocRoot() + fileLoc;
      s_log.debug("webFileLoc:" + webFileLoc);
      return AntwebUtil.fileFound(webFileLoc);
    }
  
  public static boolean isExpired(String dir, String fileName, long expirePeriod) {
    //s_log.warn("isExpired() docRoot:" + AntwebProps.getDocRoot() + " dir:" + dir + " fileName:" + fileName);

    boolean isExpired = false;
    String fullFileName = AntwebProps.getDocRoot() + dir + fileName;
    File file = new File(fullFileName);
    long lastModified = 0;
    long expireTime = 0;
    boolean fileExists = file.exists();
    if (fileExists) {
      lastModified = file.lastModified();
      expireTime = lastModified + expirePeriod;
      if (new Date().getTime() > expireTime) {
        isExpired = true;        
        AntwebUtil.remove(fullFileName);
      }
      s_log.debug("isExpired() expired:" + isExpired + " fullFileName:" + fullFileName + " expireTime:" + expireTime + " lastModified:" + lastModified);
    } else {
      s_log.debug("isExpired() " + fullFileName + " exists: false");
    }
    return isExpired;
  }
  

  public static void infoStackTrace()
  {
    Exception e = new StackTraceException();
    String trace = getStackTrace(e);
    s_log.info("infoStackTrace() trace:" + trace);
  }
    
  public static void logStackTrace()
  {
    AntwebUtil.warnStackTrace();
  }
  
  public static void warnStackTrace()
  {
    Exception e = new StackTraceException();
    String trace = getStackTrace(e);
    s_log.warn("warnStackTrace() trace:" + trace);
  }
  
  public static void errorStackTrace()
  {
    Exception e = new StackTraceException();
    String trace = getStackTrace(e);
    s_log.error("errorStackTrace() trace:" + trace);
  }
  
  public static void logStackTrace(Exception e)
  {
    AntwebUtil.errorStackTrace(e);
  }
  
  public static void infoStackTrace(Exception e)
  {
    String trace = getStackTrace(e);
    s_log.info("infoStackTrace(e) trace:" + trace);
  }
  
  
  public static void errorStackTrace(Exception e)
  {
    String trace = getStackTrace(e);
    s_log.error("errorStackTrace(e) trace:" + trace);
  }
  
  public static void printStackTrace()
  {
    Exception e = new StackTraceException();
    e.printStackTrace(System.out);
  }

  public static String getStackTrace() {
    return AntwebUtil.getStackTrace(new AntwebException(""));
  }

  public static String getStackTrace(Throwable e)
  {
    ByteArrayOutputStream os = new ByteArrayOutputStream(1000);
    PrintWriter printWriter = new PrintWriter(os);
    e.printStackTrace(printWriter);
    printWriter.flush();
    return os.toString();
  }

  public static String getStackTrace(ArrayList<String> stackTrace) {
    return stackTrace.toString();
  }

  public static void logAntwebStackTrace() {
    s_log.warn(getAntwebStackTrace());
  }
  public static void logAntwebStackTrace(Exception e) {
    s_log.warn(getAntwebStackTrace(e));
  }

  public static String getAntwebStackTrace() {
    String trace = getStackTrace();
    ArrayList<String> array = getAntwebStackTraceArray(trace);
    return getStackTrace(array);
  }


  public static ArrayList<String> getAntwebStackTraceArray() {
    String trace = getStackTrace();
    ArrayList<String> array = getAntwebStackTraceArray(trace);
    return array;
  }

  public static String getAntwebStackTrace(Exception e) {
    String trace = getStackTrace(e);
    ArrayList<String> traceArray = getAntwebStackTraceArray(trace);
    return traceArray.toString();
  }

  public static ArrayList<String> getAntwebStackTraceArray(String trace) {
    ArrayList<String> traceLines = new ArrayList<String>();
    //traceLines.add("\r\n");
    String character = "at ";
    int i = 0;
    while (trace.contains(character)) {
      ++i;
      int nIndex = trace.indexOf(character);
      String line = trace.substring(0, nIndex);
      trace = trace.substring(nIndex + 3);

      if ((line.contains("org.apache.jsp") || line.contains("org.calacademy.antweb")) && !line.contains("StackTrace")) { // || line.contains("AntwebException")
        //A.log("i:" + i + " nIndex:" + nIndex + " line:" + line);
        traceLines.add("at " + line);
      }

      if (i > 100) break;
    }
    //A.log("TraceLines:" + traceLines);
    return traceLines;
  }

  public static String getAntwebStackTraceHtml(Exception e) {
    String trace = getAntwebStackTrace(e);
    return getAntwebStackTraceHtml(trace);
  }
  public static String getAntwebStackTraceHtml(String trace) {
    String returnVal = "";
    ArrayList<String> traceLines = getAntwebStackTraceArray(trace);
    int size =  traceLines.size();
    for (int i=0; i < size; i++) {
      returnVal += "<br>" + traceLines.get(i);
    }
    return returnVal;
  }

  public static String getAntwebStackLine() {
    ArrayList<String> stackArray = AntwebUtil.getAntwebStackTraceArray();
    String line = stackArray.get(1);
    if (line != null && line.contains("DBUtil")) {
      line = stackArray.get(2);
      if (line != null && line.contains("DBUtil")) {
        line = stackArray.get(3);
        if (line != null && line.contains("DBUtil")) {
          line = stackArray.get(4);
        }
      }
    }
    line = line.trim();
    return line;
  }

  private static final int s_shortStackLines = 7  ;

  public static String getShortStackTrace() {
    return AntwebUtil.getShortStackTrace(s_shortStackLines);
  }
  public static String getShortStackTrace(int lines) {
    Exception e = new StackTraceException();
    String stackTrace = AntwebUtil.getShortStackTrace(e, lines);
    return stackTrace;
  }    
  public static String getShortStackTrace(Throwable e)
  {
    return AntwebUtil.getShortStackTrace(e, s_shortStackLines);
  }

  public static void logShortStackTrace() {
    AntwebUtil.logShortStackTrace(s_shortStackLines);
  }
  public static void logShortStackTrace(int lines) {
    Exception e = new StackTraceException();
    String stackTrace = AntwebUtil.getShortStackTrace(e, lines);
    s_log.warn("AntwebUtil.logShortStackTrace(" + lines + ") - " + stackTrace);    
  }    
  public static void logShortStackTrace(Throwable e) {
    int lines = s_shortStackLines;
    String stackTrace = AntwebUtil.getShortStackTrace(e, lines);
    s_log.warn("AntwebUtil.logShortStackTrace(e) lines:" + lines + " - " + stackTrace);    
  }    
  public static void logShortStackTrace(Throwable e, int lines) {
    String stackTrace = AntwebUtil.getShortStackTrace(e, lines);
    s_log.warn("AntwebUtil.logShortStackTrace(e, i) lines:" + lines + " - " + stackTrace);    
  }   

  // This is the generic version.  
  public static String getShortStackTrace(Throwable e, int lines)
  {
    // This will return the first couple of lines of the stacktrace.  
    String stackTrace = AntwebUtil.getStackTrace(e);
    int i = 0; // This will count the character position.  StackTrace contains newlines in a string.   
    for (int loop = 0; loop <= lines; loop++ ) {
      i = stackTrace.indexOf("\n", i + 1);
    }
    try {
      String shortStackTrace = stackTrace.substring(0, i);
      shortStackTrace = stackTrace.substring(0, i);
      return shortStackTrace;
    } catch ( StringIndexOutOfBoundsException e2) {
      s_log.warn("getShortStackTrace() e:" + e2 + " Returning full stacktrace.");
      return stackTrace;
    }
  }



  public static void throwUndeclaredException() {
    s_log.error("Throwing undeclared exception.  - for testing");  
    String t = null;
    t.toString();
  }

  public static String getMemoryStats() {
    String memory = "MemoryStats:";

    try {
      String maxStr = Formatter.formatMB(Runtime.getRuntime().maxMemory());
      String freeStr = Formatter.formatMB(Runtime.getRuntime().freeMemory());
      String totalStr = Formatter.formatMB(Runtime.getRuntime().totalMemory());

      memory = "max:" + maxStr + " free:" + freeStr + " total:" + totalStr;
    } catch (Exception e) {
      s_log.error("getMemoryStats()");
    }

    return memory;
  }

  public static void sleep(double seconds) {
    double doubleMillis = seconds * 1000;
    int millis = Double.valueOf(doubleMillis).intValue();
    try {
      Thread.sleep(millis);
    } catch(InterruptedException e) {
      s_log.warn("Exception in AntwebUtil.sleep(): " + e);
    }   
  }

  public static void sleep(int seconds) {
    int millis = seconds * 1000;
    try {
      Thread.sleep(millis);
    } catch(InterruptedException e) {
      s_log.warn("Exception in AntwebUtil.sleep(): " + e);
    }   
  }

  public static void devSleep(int seconds) {
    if (AntwebProps.isDevMode()) {
      AntwebUtil.logAntwebStackTrace();
      AntwebUtil.sleep(seconds);
    }
  }

  public static String timedGC() {  
      long startTime = new Date().getTime(); 
      System.gc();
      long duration = new Date().getTime() - startTime ;
     // s_log.warn("GarbageCollected:" + duration);
      return Long.valueOf(duration).toString();
  }
  
  public static long millisSince(Date date) {

    Date now = new Date();
    long millisSince = now.getTime() - date.getTime();
    return millisSince;
  }
  public static long millisUntil(Date date) {
    Date now = new Date();
    long millisUntil = date.getTime() - now.getTime();
    return millisUntil;
  }

  public static long secsSince(Date date) {
    long millisSince = AntwebUtil.millisSince(date);
    long secsSince = millisSince / 1000;
    return secsSince;
  }
  public static long secsUntil(Date date) {
    long millisUntil = AntwebUtil.millisUntil(date);
    long secsUntil = millisUntil / 1000;
    return secsUntil;
  }

  public static long minsSince(Date date) {
    long secsSince = AntwebUtil.secsSince(date);
    long minsSince = secsSince / 60;
    return minsSince;
  }
  public static double doubleMinsSince(Date date) {
    long secsSince = AntwebUtil.secsSince(date);
    double doubleSecsSince = secsSince;
    double minsSince = doubleSecsSince / 60;        
    return minsSince;
  }

  public static long minsUntil(Date date) {
    long secsUntil = AntwebUtil.secsUntil(date);
    long minsUntil = secsUntil / 60;
    return minsUntil;
  }
   
  public static long hrsSince(Date date) {
    long minsSince = AntwebUtil.minsSince(date);
    long hrsSince = minsSince / 60;
    return hrsSince;
  }
  public static long hrsUntil(Date date) {
    long minsUntil = AntwebUtil.minsUntil(date);
    long hrsUntil = minsUntil / 60;
    return hrsUntil;
  }

  public static long daysSince(Date date) {
    long minsSince = AntwebUtil.minsSince(date);
    long hrsSince = minsSince / 60;
    long daysSince = hrsSince / 24;
    return daysSince;
  }

    //This returns as a decimal value of a minutes. So 40 seconds is .66 mins.
	public static String getMinsPassed(Date startTime) {
      double timePassed = AntwebUtil.doubleMinsSince(startTime);
      DecimalFormat formatter = new DecimalFormat("#0.00");
      return formatter.format(timePassed) + " mins";
	}

    public static String reportTime(Date startTime) {
      String execTime = "";
      long millis = AntwebUtil.millisSince(startTime);
      int threeMinOfMillis = 3 * 60 * 1000;
      if (millis > threeMinOfMillis) {
        execTime = AntwebUtil.getMinsPassed(startTime);
      } else if (millis < threeMinOfMillis && millis > 1000) {
          execTime = AntwebUtil.secsSince(startTime) + " secs";
      } else {
          execTime = millis + " millis";
      }
      return execTime;
    }

    public static long millisUntil8pm() {    
    Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, 0);
        c.set(Calendar.HOUR_OF_DAY, 20);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long millisUntil = c.getTimeInMillis() - System.currentTimeMillis();
        //if (AntwebProps.isDevMode()) AntwebUtil.log("millisUntil:" + millisUntil);
        return millisUntil;
    }
    public static long minUntil8pm() {
        return TimeUnit.MILLISECONDS.toMinutes(millisUntil8pm());
        // return millisUntil() / 1000 / 60;
    }
      
      
  public static Date getDateNWeeksAgo(int numOfWeeks) {
      return AntwebUtil.getDateNWeeksAgo(new Date(), numOfWeeks);
  }  

  public static Date getDateNDaysAgo(Date date, int numOfDays) {      
      long oneDayMillis = 1000 * 60 * 60 * 24;
      long pastMillis = oneDayMillis * numOfDays;
      long pastDateMillis = date.getTime() - pastMillis;
      Date pastDate  = new Date(pastDateMillis);      
      return pastDate;
  }
  
  public static Date getDateNWeeksAgo(Date date, int numOfWeeks) {      
      long oneWeekMillis = 1000 * 60 * 60 * 24 * 7;  // 604,800,000 millis per week
      long pastMillis = oneWeekMillis * numOfWeeks;
      long pastDateMillis = date.getTime() - pastMillis;
      Date pastDate  = new Date(pastDateMillis);      
      return pastDate;
  }
    
  public static Date relativeDate(Date date, long relMillis) {
    long newDateMillis = date.getTime() + relMillis;
    Date newDate = new Date(newDateMillis);
    return newDate;
  }

  public static Date[] getBetweenDates(Date day) {
    Calendar cal = new GregorianCalendar();

    cal.setTime(day);
    cal.set(Calendar.HOUR, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    Date oneDate = cal.getTime();
    cal.add(Calendar.DATE, 1);
    Date twoDate = cal.getTime();
  
    return new Date[] {oneDate, twoDate};  
    }

    public static Vector sort(HashMap hashMap) {
        Vector newVector = new Vector();

        Vector keysVector = AntwebUtil.getSortedKeyVector(hashMap);
        
        String sortedKeyList = "";

      for (Object key : keysVector) {
        sortedKeyList = sortedKeyList + key.toString() + ", ";
        newVector.add(hashMap.get(key));
      }
        //s_log.warn("SortedKeyList: " + sortedKeyList);
        return newVector;  
    }

    public static Vector getSortedKeyVector(HashMap hashMap) {
        Set keys = hashMap.keySet();
        Vector keysVector = new Vector(keys);
        Collections.sort(keysVector);
        return keysVector;
    }
  
    public static long timePassed(Date fromDate, Date toDate) {
        return toDate.getTime() - fromDate.getTime();
    }

    public static boolean validEmail(String email) {
        if (email.equals(""))
            return false;
        if (!email.contains("@") ||
                !email.contains(".") ||
            email.length() < 6) {
            return false;
        }
        try {
            InternetAddress address = new InternetAddress(email);
        } catch (AddressException e) {
            return false;
        }
        return true;
    }   


    public static String getRequestInfo(HttpServletRequest request) {
      return HttpUtil.getRequestInfo(request);
    }
    // The above may be pragmatic to leave.  Below should be deprecated and removed...
    public static void blockFishingAttack(HttpServletRequest request, ActionErrors errors) {
      HttpUtil.blockFishingAttack(request, errors);
    }     
    

    public static final boolean isDeployed = true;
        
    public static boolean isDeployed(HttpServletRequest request) {
      if (isDeployed) return true;

      return LoginMgr.isAdmin(request);
    }


  private static Hashtable<String, Integer> countHash = new Hashtable<>();
  public static void count(String key) {
    if (countHash.containsKey(key)) {
      Integer theCount = countHash.get(key);
      int theCountInt = theCount + 1;
      //A.log("AntwebUtil.count() key:" + key + " count:" + theCountInt);
      countHash.put(key, theCountInt);
    } else {
      countHash.put(key, 1);
    }
  }
  public static int getCount(String key) {
    return countHash.getOrDefault(key, 0);
  }
  public static void logCount() {
    Set<String> keys = countHash.keySet();
    for (String key : keys) {
      Integer count = countHash.get(key);
      //A.log("logCountHash() " + key + ":" + count);
    }
    countHash = new Hashtable<>();
  }
  
  public static String getAdminEmail() {
    return "bfisher@calacademy.org";
  }   
  public static String getAdminName() {
    return "Brian Fisher";
  }   
  public static String getDevEmail() {
    return "re.mark.johnson@gmail.com";
  }

/*
  public static String getCallerCallerClassName() {
    StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
    String callerClassName = null;
    for (int i=1; i<stElements.length; i++) {
      StackTraceElement ste = stElements[i];
      if (!ste.getClassName().equals(KDebug.class.getName())&& ste.getClassName().indexOf("java.lang.Thread")!=0) {
        if (callerClassName==null) {
          callerClassName = ste.getClassName();
        } else if (!callerClassName.equals(ste.getClassName())) {
          return ste.getClassName();
        }
      }
    }
    return null;
  }
*/

  public static String getCallerCallerClassName() {
    return KDebug.getCallerCallerClassName();
  }
}

class KDebug {
  private static final Log s_log = LogFactory.getLog(AntwebUtil.class);

  public static String getCallerCallerClassName() {
    StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
    String callerClassName = null;
    for (int i=1; i<stElements.length; i++) {
      StackTraceElement ste = stElements[i];
      String className = ste.getClassName();

      if (!className.equals(KDebug.class.getName()) && className.indexOf("java.lang.Thread") != 0) {

        //if (AntwebProps.isDevMode()) s_log.info("getCallerCallerClassName() callerClassName:" + callerClassName + " ste.getClassName():" + ste.getClassName());

        if ((className != null)
          && !("org.calacademy.antweb.util.A".equals(className))
          && !("org.calacademy.antweb.util.AntwebUtil".equals(className))) {
          return className;
        }
        //if (callerClassName == null || "org.calacademy.antweb.util.AntwebUtil".equals(callerClassName)) {
        //  callerClassName = ste.getClassName();
        //} else if (!"org.calacademy.antweb.util.AntwebUtil".equals(callerClassName) && !callerClassName.equals(ste.getClassName())) {
        //  return ste.getClassName();
        //}


      }
    }
    return null;
  }
}

class StackTraceException extends Exception
{  
}


