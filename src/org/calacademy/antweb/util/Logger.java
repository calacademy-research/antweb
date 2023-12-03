package org.calacademy.antweb.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.home.ServerDb;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;

public class Logger {

  /* This lovely little class is for convenience.  Something like this:
    A.log("aJsp.jsp logMessage");
    
      will effectively be this:
      
    A.log("aJsp.jsp logMessage");
  */

  private static final Log s_log = LogFactory.getLog(A.class);

  public static boolean isDebug(String option) {
      return ServerDb.isServerDebug(option);
  }

  public static void log(String message) {
      String fullClassName = AntwebUtil.getCallerCallerClassName();
      String className = null;
      String include = "";
      if (fullClassName != null) {
        className = fullClassName.substring(fullClassName.lastIndexOf(".") + 1);

        //s_log.warn("className:" + className);
      }
      if (className.contains("_jsp")) include = " (or include)";
	  //AntwebUtil.log(className + include + "." + message);
      s_log.info(className + include + "." + message);
  }

  
  private static int loopCounter = 0;
  public static boolean loopCount() {
    return loopCount(5000);
  }
  public static boolean loopCount(int divNum) {
    loopCounter = loopCounter + 1;
      return loopCounter == 1 || loopCounter % divNum == 0;
  }

  // For production use. Something like:
  // Logger.iLog(Logger.doFilterSQLNonTransientConnection, message, 30);

  // Call with a constant to insure integrity
  public static final int doFilterSQLNonTransientConnection = 1;
  public static final int dBUtilGetConnection = 2;
  private static final int[] logCounter = new int[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
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
    logCounter[log] = logCounter[log] + 1;
    if (logCounter[log] == 1 || logCounter[log] % divNum == 0) {
      s_log.info("log:" + log + " " + message + " i:" + logCounter[log]);
    }
  }


  // This one will log on the live site for developers.
  public static void logDev(String message, Login login) {
    if (AntwebProps.isDevMode() || LoginMgr.isDeveloper(login)) {
      s_log.info(message);
    }
  }

}
