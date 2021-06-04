
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
    
      count = (Integer.valueOf(countStr)).intValue();
    } catch (NumberFormatException e) {
      s_log.error("countLines e:" + e + " countStr:" + countStr);
    } catch (StringIndexOutOfBoundsException e) {
      s_log.error("countLines e:" + e + " countStr:" + countStr);
    }
    s_log.warn("countLines:" + countStr + " return:" + count + " spaceIndex:" + spaceIndex);
    return count;
  }	

  // NOT WORKING: https://www.antweb.org/utilData.do?action=restart
  public static String restartAntweb(Login accessLogin) {
    String message = "";
    s_log.warn("restartAntweb() invoked by:" + accessLogin.getName());
    if (AntwebProps.isDevMode()) {
      message = "restartAntweb does not run in dev.";
      A.log("restartAntweb() message:" + message);
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
      A.log("launchProcess() Running command: " + command);
      //AntwebUtil.logShortStackTrace(3);

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
    private static double threshold = .9; // 1;
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
             String recipients = "re.mark.johnson@gmail.com";
             String subject = "Antweb " + message;
             String body = ".";
             s_log.warn("cpuCheck() Send " + message + " to recipients:" + recipients);
             //Emailer.sendMail(recipients, subject, body);
           }
          // CpuCheck is called cCheck.log to hide "inner workings to satisfy Joe Russack in IT.
          LogMgr.appendDataLog("cpuCheck.log", message , true);
        }
        return message;
    }

    public static String getTopReport() {
      String report = "<b>Top Report for Mysql and Java:</b>";
      report += "<pre> PID USER PR NI VIRT RES SHR S %CPU %MEM TIME+ COMMAND";
      String javaTop = AntwebSystem.top("java");
      if (javaTop != null) report += "<br>" + javaTop;  // causes extra line: "\r\n<br>"
      String mysqlTop = AntwebSystem.top("mysql");
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
        A.log("top() e:" + e);
      }

      return processLine;
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
