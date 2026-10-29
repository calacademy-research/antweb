
package org.calacademy.antweb.util;

import java.io.*;
import java.util.concurrent.Executors;

import org.calacademy.antweb.*;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class AntwebSystem {

  private static final Log s_log = LogFactory.getLog(AntwebSystem.class);

  public String getErrorLogData() {  
    String logFile = AntwebProps.getProp("site.errorLog");

    String command = "tail --lines 2000 " + logFile; 
    String logData = launchProcess(command, true);  
    return logData;
  }
    
  /**
   * The number of lines of a file, as "wc -l" counts them (newline characters); 0 when it cannot be read.
   * Read in Java since 2026-10-29: through "wc" in a child process, the output was sometimes read before
   * the process wrote it, giving 0 (the AntCat import's low-count guard then refused a good file).
   */
  public static int countLines(String fileName) {
    if (fileName == null) return 0;
    int count = 0;
    byte[] buffer = new byte[1 << 16];
    try (java.io.InputStream in = java.nio.file.Files.newInputStream(java.nio.file.Paths.get(fileName))) {
      int n;
      while ((n = in.read(buffer)) > 0) {
        for (int i = 0; i < n; i++) if (buffer[i] == '\n') ++count;
      }
    } catch (java.io.IOException | java.nio.file.InvalidPathException e) {
      s_log.error("countLines() could not read " + fileName + " e:" + e);
      return 0;
    }
    return count;
  }

  private static boolean s_isRestart = false;
  public static boolean getIsRestart() {
    return s_isRestart;
  }

  public static void setIsRestart(boolean isRestart) {
    s_isRestart = isRestart;
  }
  public static boolean isRestart() {
    boolean retVal = false;
    // Never restart in the middle of an upload (a curator's, or the nightly TaxonWorks
    // upload in set1): the restart would kill it and roll it back. reboot_checker.sh asks
    // again every 5 minutes, so the restart just waits until the upload has finished.
    if (org.calacademy.antweb.upload.UploadAction.isInUploadProcess()) return false;
    // Nor while an image upload is not finished: the Image Uploader sends one file per request, so
    // between two files the flag above is clear (2026-10-26, a restart cut an upload short).
    if (BusyStatus.isImageUploadInProgress()) return false;
    // Nor while a job started from a page (an upload, an admin data job) is running (BackgroundJobs).
    // Jobs still waiting in the queue do not stop a restart: they show as "interrupted".
    if (BackgroundJobs.isJobRunning()) return false;
    if (Scheduler.isStuckInSet1()) return true;
    if (getIsRestart()) return true;
    return false;
  }

  // NOT WORKING: https://www.antweb.org/utilData.do?action=restart
  public static String restartAntweb(Login accessLogin) {
    String message = "";
    s_log.warn("restartAntweb() invoked by:" + accessLogin.getName());
    if (AntwebProps.isDevMode()) {
      message = "restartAntweb does not run in dev.";
      s_log.debug("restartAntweb() message:" + message);
    } else {
      // None of these have worked...
      // NOPE: (new AntwebSystem()).exec("systemctl restart tomcat");
      // NOPE: (new AntwebSystem()).launchProcess("systemctl restart tomcat");
      // restart();
      // (new AntwebSystem()).launchProcess("reboot now");
      message = "restarting...";
    }
    return message;
  }
/*
  public static String restart() {
    String processLine = "";
    try {
      ProcessBuilder builder = new ProcessBuilder("systemctl restart tomcat", "-b", "-n", "1");
      Process proc = builder.start();

      try (BufferedReader stdin = new BufferedReader(
              new InputStreamReader(proc.getInputStream()))) {
        String line;
        // Blank line indicates end of summary.
        while ((line = stdin.readLine()) != null) {
          if (line.isEmpty()) {
            break;
          }
        }
        // Skip header line.
        if (line != null) {
          line = stdin.readLine();
        }
        if (line != null) {
          while ((line = stdin.readLine()) != null) {
              processLine += line;
          }
        }
      }
    } catch (IOException e) {
      A.log("restart() e:" + e);
    }

    return processLine;
    }
*/

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
	  s_log.error("launchBuilder() e:" + e);
	}	
//	StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), System.out::println);
	StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), "error");
	Executors.newSingleThreadExecutor().submit(streamGobbler);
	int exitCode = 0;
	try {
	  exitCode = process.waitFor();
	} catch (InterruptedException e) {
	  s_log.error("launchBuilder() e:" + e);
	}
	assert exitCode == 0;
  }

  /*
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
      s_log.error("execPipe() e:" + e);
    }
  }    
 */
  public void launchProcess(String command) {	
    launchProcess(command, false);
  }
  
  public String launchProcess(String command, boolean getRetVal) {
    String retVal = "";
    try {
      //if (AntwebProps.isDevOrStageMode()) {
        //s_log.info("launchProcess() Running command: " + command);
        //AntwebUtil.logShortStackTrace(9);
      //}
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
      
      //s_log.info("AntwebSystem.launchProcess() Running command: " + command + " retVal:" + someData);

      p.waitFor();
    } catch (Exception e) {
      s_log.error("AntwebSystem e:" + e);
    }
    //s_log.info("AntwebSystem.launchProcess() Returning:" + retVal);
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
  public static final int VIEW_LINE = 2000;
  public static final long VIEW_TOTAL = 5_000_000L;

  /** One output line for the log viewer: at most VIEW_LINE characters, with the full length if cut. */
  public static String viewLine(String s) {
    if (s == null || s.length() <= VIEW_LINE) return s;
    return s.substring(0, VIEW_LINE) + "... [cut, " + String.format("%,d", s.length()) + " characters]";
  }

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
    // The log viewer (logMgr.do) shows each line at most VIEW_LINE characters and at most VIEW_TOTAL
    // characters in all (2026-10-22: messages.txt had lines of ~13 MB; 2,000 of them would not fit in memory).
    long total = 0;
    boolean full = false;
    while (!procDone(p)) {
      //int afterErrorCount = 0;
      while ((s = stdOut.readLine()) != null) {
        if (full) continue;  // read to the end so the process can finish
        String line = viewLine(s);
        total += line.length();
        if (total > VIEW_TOTAL) {
          strBuf.append("<br>... [output cut at " + String.format("%,d", VIEW_TOTAL) + " characters]");
          full = true;
          continue;
        }
        strBuf.append("<br>" + Html.esc(line));
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
      s_log.debug("procDone() e:" + e);
      //if (AntwebProps.isDevOrStageMode()) s_log.warn("procDone() e:" + e);
      return false; 
    }
  }  
     
    public static long getFreeSpace() {
        
        long freeSpace = new File("/").getFreeSpace();
        return freeSpace;
    }
        
    public static String getCpuLoad() {
		OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
		// What % CPU load this current JVM is taking, from 0.0-1.0
		String cpuLoad = "processCpuLoad:" + osBean.getProcessCpuLoad() + " systemCpuLoad:" + osBean.getSystemCpuLoad();
  
        return cpuLoad;    
    }

    private static boolean messageSent = false;
    private static final double threshold = .9; // 1;
    public static String cpuCheck() {
		String message = null;
		OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        double systemCpuLoad = osBean.getSystemCpuLoad();
        double processCpuLoad = osBean.getProcessCpuLoad();
        //A.log("cpuCheck() s:" + systemCpuLoad + " p:" + processCpuLoad);

        //if (AntwebProps.isDevMode()) threshold = 0;
        if (systemCpuLoad > threshold || processCpuLoad > threshold) {
           // SystemCpuLoad is s and processCpuLoad is p to hide "inner workings to satisfy Joe Russack in IT.
           message = "systemCpuLoad:" + systemCpuLoad + " processCpuLoad:" + processCpuLoad;
           if (!messageSent) {
             messageSent = true;
             String recipients = AntwebUtil.getDevEmail();
             String subject = "Antweb " + message;
             String body = ".";
             s_log.warn("cpuCheck() Send " + message + " to recipients:" + recipients);
             Emailer.sendMail(recipients, subject, body);
           }
          // CpuCheck is called cCheck.log to hide "inner workings to satisfy Joe Russack in IT.
          LogMgr.appendDataLog("cCheck.log", message , true);
        }
        return message;
    }

    public static String getTopReport() {
      //String report = "<b>Top Report for Java:</b>";
      String report = "<pre> PID USER PR NI VIRT RES SHR S %CPU %MEM TIME+ COMMAND";
      String javaTop = AntwebSystem.top("java");
      if (javaTop != null) report += "<br>" + javaTop;  // causes extra line: "\r\n<br>"
      String mysqlTop = null; //AntwebSystem.top("mysql");
      if (mysqlTop != null) report += "<br>" + mysqlTop;
      if (javaTop == null && mysqlTop == null) report += "\r<br>Top results not found for mysql and java";
      report += "</pre>";
      return report;
    }

    public static String top(String processName) {
      String processLine = null;
      try {
        ProcessBuilder builder = new ProcessBuilder("top", "-b", "-n", "1");
        Process proc = builder.start();

        try (BufferedReader stdin = new BufferedReader(
                new InputStreamReader(proc.getInputStream()))) {
          String line;
          // Blank line indicates end of summary.
          while ((line = stdin.readLine()) != null) {
            if (line.isEmpty()) {
              break;
            }
          }
          // Skip header line.
          if (line != null) {
            line = stdin.readLine();
          }
          if (line != null) {
            while ((line = stdin.readLine()) != null) {
              if (line.contains(processName)) {   // No need for grep
                processLine = line;
                break;
              }
            }
          }
        }
      } catch (IOException e) {
        s_log.debug("top() e:" + e);
      }

      return processLine;
    }
}

class StreamGobbler extends Thread
{
    final InputStream is;
    final String type;
    
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
