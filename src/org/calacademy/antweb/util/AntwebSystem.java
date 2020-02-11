
package org.calacademy.antweb.util;

import java.io.*;
import java.net.*;
import java.util.Formatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.Executors;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import static java.nio.file.StandardCopyOption.*;

import javax.servlet.http.*;
import javax.servlet.*;

import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;

import org.calacademy.antweb.*;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class AntwebSystem {

  private static final Log s_log = LogFactory.getLog(AntwebSystem.class);
  
/*  
  public static String archiveLogs() {
    // All log files in file system at: /data/antweb/web/log
    // Should be archived daily to here: /data/antweb/web/log/bak/2016...
    // So current files should be short and web accessible here: https://www.antweb.org/web/log
      
    String[] filesToMove = { "accessLog.txt", "getUrl.txt", "imageNotFound.txt", "logins.txt"
      , "longRequest.log", "messages.txt", "moveTaxonAndSupportingTaxa.log", "nonWorldAntsDeleted.txt"
      , "noExists.txt", "notFound.txt", "profile.log", "queryStats.log", "searches.txt"
      , "serverBusy.html", "serverBusy.log", "speciesListLog.txt", "invalid.log", "hacks.log" }; 
  
    String dateStr = AntwebUtil.getWebFormatDateTimeStr();
  
    String yearStr = dateStr.substring(0,4);
  
    String logDir = "/data/antweb/web/log/";
    String bakDir = logDir + "bak/";
    String backupDir = bakDir + yearStr + "/" + dateStr + "/";
    (new Utility()).makeDirTree(backupDir);

    String webBackupDir = AntwebProps.getDomainApp() + "/web/log/bak/" + dateStr + "/";    
    
    AntwebSystem.make777(bakDir);     
    AntwebSystem.make777(backupDir);
    
    String textFiles = logDir + "*.txt";
    String logFiles = logDir + "*.log";
    String htmlFiles = logDir + "*.html";
  
    for (String fileToMove : filesToMove) {
      AntwebSystem.moveFile(logDir, fileToMove, backupDir);
    }

    String message = "files backed up here:" + webBackupDir;
    A.log("archiveLogs() message:" + message);
    return message;
  }

  public static void moveFile(String sourceDir, String fileName, String destDir) {

    Path source = FileSystems.getDefault().getPath(sourceDir, fileName);
    Path dest = FileSystems.getDefault().getPath(destDir);

    try {
      Files.move(source, dest.resolve(source.getFileName()), REPLACE_EXISTING);
    } catch (IOException e) {
      //s_log.info("moveFile() e:" + e);
    }   
  }
  
  public static void make777(String file) {
    try {
        //using PosixFilePermission to set file permissions 777
        Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>();
        //add owners permission
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        //add group permissions
        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.GROUP_WRITE);
        perms.add(PosixFilePermission.GROUP_EXECUTE);
        //add others permissions
        perms.add(PosixFilePermission.OTHERS_READ);
        perms.add(PosixFilePermission.OTHERS_WRITE);
        perms.add(PosixFilePermission.OTHERS_EXECUTE);
         
        Files.setPosixFilePermissions(Paths.get(file), perms);    
    } catch (IOException e) {
        s_log.warn("e:" + e);
    }    
  }
*/

/*
  public String getDiskFree() {  
    String command = "df -h"; 
    String results = launchProcess(command, true);  
    return results;
  }
*/
    
  public String getErrorLogData() {  
    String logFile = AntwebProps.getProp("site.errorLog");

    String command = "tail --lines 2000 " + logFile; 
    String logData = launchProcess(command, true);  
    return logData;
  }
    
  public static int countLines(String fileName) {
    String command = "wc " + fileName;
    String countLinesStr = (new AntwebSystem()).launchProcess(command, true);
    if (countLinesStr.length() <= 4) {
      s_log.warn("countLines() countlines too short:" + countLinesStr + " for command:" + command);
      s_log.warn("Warning this should only happen on Mac! returning 0!");
      return 0;
    }
    countLinesStr = countLinesStr.substring(4).trim();
    //s_log.warn("countLines:" + countLinesStr);

    int count = 0;
  
    String countStr = null;
    //int wcLineLoc = 7;
    int spaceIndex = countLinesStr.indexOf(" "); //, wcLineLoc);
    try {
      countStr = countLinesStr.substring(0, spaceIndex);
      //s_log.warn("countLines:" + countStr);
    
      count = (new Integer(countStr)).intValue();
    } catch (NumberFormatException e) {
      s_log.error("countLines e:" + e + " countStr:" + countStr);
    } catch (StringIndexOutOfBoundsException e) {
      s_log.error("countLines e:" + e + " countStr:" + countStr);
    }
    s_log.warn("countLines:" + countStr + " return:" + count + " spaceIndex:" + spaceIndex);
    return count;
  }	


// These might only work with Java 1.8 as it does in dev.
/*
  public void exec(String command) {
	String homeDirectory = System.getProperty("user.home");
	Process process = null;
	//process = Runtime.getRuntime().exec(String.format("sh -c ls %s", homeDirectory));
	try {
  	  process = Runtime.getRuntime().exec(command);
	} catch (IOException e) {
	  s_log.error("exec() e:" + e);	
	}
	StreamGobbler streamGobbler = 
	  new StreamGobbler(process.getInputStream(), System.out::println);
	Executors.newSingleThreadExecutor().submit(streamGobbler);
	int exitCode = 0;
	try {
	  exitCode = process.waitFor();
	} catch (InterruptedException e) {
	  s_log.error("exec() e:" + e);
	}
	assert exitCode == 0;    
  }
*/

/* NP# on getInputStream? */
  public void launchBuilder(String command) {  
	ProcessBuilder builder = new ProcessBuilder();
	builder.command(command); //"sh", "-c", "ls"
	
	builder.directory(new File(System.getProperty("user.home")));
	Process process = null; 
	try {
	  process = builder.start();
	} catch (IOException e) {
	  s_log.error("exec() e:" + e);	
	}	
//	StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), System.out::println);
	StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), "error");
	Executors.newSingleThreadExecutor().submit(streamGobbler);
	int exitCode = 0;
	try {
	  exitCode = process.waitFor();
	} catch (InterruptedException e) {
	  s_log.error("exec() e:" + e);
	}
	assert exitCode == 0;
  }

  
  public void exec(String command) {
    try {
      s_log.warn("Running command:" + command);
      Process p = Runtime.getRuntime().exec(command);  
      //s_log.warn("  Process:" + p.toString() + " exitValue:" + p.exitValue());
    } catch (IOException e) {
      s_log.error("exec() e:" + e);
    }
  }    
 

  public void execPipe(String command) {
    try {
      s_log.warn("Running command:" + command);
      Runtime.getRuntime().exec(new String[]{"sh", "-c", command}, null, null);
      //s_log.warn("  Process:" + p.toString() + " exitValue:" + p.exitValue());
    } catch (IOException e) {
      s_log.error("exec() e:" + e);
    }
  }    
 
  public void launchProcess(String command) {	
    launchProcess(command, false);
  }
  
  public String launchProcess(String command, boolean getRetVal) {
    String retVal = "";
    try {
      //A.log("launchProcess() Running command: " + command);
      //s_log.warn("launchProcess() Running command: " + command);
      Process p = Runtime.getRuntime().exec(command);
      if (! getRetVal) {
        handleStdOut(p);
      } else {
        //if (command.contains("sed ")) {
        //  retVal = returnStdOut(p);
        //} else {
          retVal = returnStdOutAsHtml(p);
        //}
      }
      
      //A.log("launchProcess() retVal:" + retVal + " getRetVal:" + getRetVal);
      
      String someData = retVal;
      if (retVal != null && retVal.length() > 30) someData = retVal.substring(0, 30) + "...";
      
      A.log("AntwebSystem.launchProcess() Running command: " + command + " retVal:" + someData);

      p.waitFor();
    } catch (Exception e) {
      s_log.error("AntwebSystem e:" + e);
    }
    return retVal;
  }
	
  private void handleStdOut(Process p) 
    throws IOException 
  {
    BufferedReader stdOut = new BufferedReader(new InputStreamReader(p.getInputStream()));
    String result = "";
    String s = null;
    int count = 0;
    int i = 0;
    while (!procDone(p)) {
      count = count + 1;
      while ((s = stdOut.readLine()) != null) {
        i = i + 1;
        s_log.info(s);
      }
    } 
    //s_log.warn("handleStdOut() count:" + count + " i:" + i);
  }	

/*
  private String returnStdOut(Process p) 
    // This works particular to our log files.  Only returns errors (+ 3 lines after)
    throws IOException 
  {
    String[] command={"sed", "-i", "'s/\\^@\\^/\\|/g'", "/tmp/part-00000-00000"};
	ProcessBuilder pb = new ProcessBuilder(command);
	pb.redirectErrorStream(true);
	Process process = pb.start();
	process.waitFor();
	if (process.exitValue() > 0) {
		String output = // get output form command
		throw new Exception(output);
	}
  }	
  */
  
  // This code works with tail, but not with sed.
  private String returnStdOutAsHtml(Process p) 
    // This works particular to our log files.  Only returns errors (+ 3 lines after)
    throws IOException 
  {
    // This method does not seem to able to build a return value greater than 64K.
    StringBuffer strBuf = new StringBuffer();
    BufferedReader stdOut = new BufferedReader(new InputStreamReader(p.getInputStream()));
    String result = "";
    String s = null;
    int count = 0;
    while (!procDone(p)) {
      //int afterErrorCount = 0;
      while ((s = stdOut.readLine()) != null) {
        strBuf.append("<br>" + s);  
      }
    } 
    return strBuf.toString();
  }	
  
  private boolean procDone(Process p) {
    // This is wonky.  Bad.  Exception expected.  :(
    try {
      int v = p.exitValue();
      return true;
    } catch (IllegalThreadStateException e) {
      A.log("procDone() e:" + e);
      //if (AntwebProps.isDevOrStageMode()) s_log.warn("procDone() e:" + e);
      return false; 
    }
  }  
     
    public static long getFreeSpace() {
        
        long freeSpace = (new java.io.File("/")).getFreeSpace();
        return freeSpace;
    }
        
    public static String getCpuLoad() {
		OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
		// What % CPU load this current JVM is taking, from 0.0-1.0
		String cpuLoad = "processCpuLoad:" + osBean.getProcessCpuLoad() + " systemCpuLoad:" + osBean.getSystemCpuLoad();        
  
        return cpuLoad;    
    }

    private static boolean messageSent = false;
    private static double threshold = 1;
    public static String cpuCheck() {
		OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        double cpuLoad = osBean.getSystemCpuLoad(); 
        if (AntwebProps.isDevMode()) threshold = 0;
        if (cpuLoad > threshold && !messageSent) {
            messageSent = true;
			String recipients = "re.mark.johnson@gmail.com";
			String subject = "CPU on Antweb at " + cpuLoad;
			String body = ".";
			Emailer.sendMail(recipients, subject, body);   
			A.log("cpuCheck() message sent"); 
        }
        return "cpuCheck:" + cpuLoad;
    }
    
}

class StreamGobbler extends Thread
{
    InputStream is;
    String type;
    
    StreamGobbler(InputStream is, String type)
    {
        this.is = is;
        this.type = type;
    }
    
    public void run()
    {
        try
        {
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line=null;
            while ( (line = br.readLine()) != null)
                System.out.println(type + ">" + line);    
            } catch (IOException ioe)
              {
                ioe.printStackTrace();  
              }
    }
}

/*
class StreamGobbler implements Runnable {
	private InputStream inputStream;
	private Consumer<String> consumer;

	public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
		this.inputStream = inputStream;
		this.consumer = consumer;
	}

	@Override
	public void run() {
		new BufferedReader(new InputStreamReader(inputStream)).lines()
		  .forEach(consumer);
	}
}    
*/
