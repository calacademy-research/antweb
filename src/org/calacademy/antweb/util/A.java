package org.calacademy.antweb.util;

import java.io.*;
import java.net.*;
import java.util.*;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import static java.nio.file.StandardCopyOption.*;

import javax.servlet.http.*;
import javax.servlet.*;

import java.text.*;
import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;

import org.calacademy.antweb.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class A {

  /* This lovely little class is for convenience.  Something like this:
    A.log("aJsp.jsp logMessage");
    
      will effectively be this:
      
    A.log("aJsp.jsp logMessage");
  */

  private static final Log s_log = LogFactory.getLog(A.class);
  
  public static void log(String message) {
    if (AntwebProps.isDevMode()) {
      //s_log.warn(message);   

      // This is nice. Can we get the calling method and drop the package info? That would be nice.
      
      String fullClassName = getCallerCallerClassName();
      String className = null;
      String include = "";
      if (fullClassName != null) {
        className = fullClassName.substring(fullClassName.lastIndexOf(".") + 1);

        //s_log.warn("className:" + className);
      }
      if (className.contains("_jsp")) include = " (or include)";
	  s_log.warn(className + include + "." + message);   
    }
  }
  
  // Short for stg log.
  public static void slog(String message) {
    if (AntwebProps.isDevOrStageMode()) {
      s_log.warn(message);      
    }
  }
  
  private static int loopCounter = 0;
  public static boolean loopCount() {
    return loopCount(5000);
  }
  public static boolean loopCount(int divNum) {
    loopCounter = loopCounter + 1;
    if (loopCounter == 1 || (loopCounter % divNum) == 0) {
      return true;    
    }
    return false;
  }
  
  private static int[] logCounter = new int[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
  public static void iLog(String message) {
    iLog(0, message, 1000);
  }
  public static void iLog(int log, String message) {
    iLog(log, message, 5000);
  }
  public static void iLog(String message, int divNum) {
    iLog(0, message, divNum);
  }
  public static void iLog(int log, String message, int divNum) { 
    if (!AntwebProps.isDevOrStageMode()) return;

    logCounter[log] = logCounter[log] + 1;
    if (logCounter[log] == 1 || (logCounter[log] % divNum) == 0) {
      s_log.warn("log:" + log + " " + message + " i:" + logCounter[log]);      
    }
  }


  // This one will log on the live site for developers.
  public static void log(String message, Login login) {
    if (AntwebProps.isDevMode() || LoginMgr.isDeveloper(login)) {
      s_log.warn(message);      
    }
  }
  
  public static boolean dev() {
    return AntwebProps.isDevMode();
  }

  public static void p(String str) {
    System.out.println(str);
  }


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
   
	public static String commaFormat(String num) {
	  if (num == null) return "0";
	  return commaFormat((new Long(num)).intValue());
	}	
	
	public static String commaFormat(long num) {
	  if (num == 0) return "0";
	  return commaFormat((new Long(num)).intValue());
	}
	
	public static String commaFormat(int num) {
	  if (num == 0) return "0";
      return NumberFormat.getNumberInstance(Locale.US).format(num);		
	}

}


class KDebug {
    public static String getCallerClassName() { 
        StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
        for (int i=1; i<stElements.length; i++) {
            StackTraceElement ste = stElements[i];
            if (!ste.getClassName().equals(KDebug.class.getName()) && ste.getClassName().indexOf("java.lang.Thread")!=0) {
                //AntwebUtil.log("method:" + ste.getMethodName());
                return ste.getClassName();
            }
        }
        return null;
     }
}
