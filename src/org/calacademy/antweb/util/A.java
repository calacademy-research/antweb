package org.calacademy.antweb.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.home.ServerDb;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;

public class A {

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
    if (AntwebProps.isDevMode()) {
      //s_log.warn(message);   

      // This is nice. Can we get the calling method and drop the package info? That would be nice.
      
      String fullClassName = AntwebUtil.getCallerCallerClassName();
      String className = null;
      String include = "";
      if (fullClassName != null) {
        className = fullClassName.substring(fullClassName.lastIndexOf(".") + 1);

        //s_log.warn("className:" + className);
      }
      if (className.contains("_jsp")) include = " (or include)";
	  AntwebUtil.log(className + include + "." + message);
    }
  }


  private static final HashMap<String, Integer> s_logiSet = new HashMap<>();
  //Log only once. Enter a unique i. Best to use a contstant.
  public static void logi(String key, String message) {
      logi(key, 1, message);
  }
  // Optional second parameter i indicates the number of times we would like to log this message.
  public static void logi(String key, int i, String message) {
      int nth = 1;
      // Apparently we have already logged this message.
      if (s_logiSet.containsKey(key)) {
         nth = s_logiSet.get(key);
         ++nth;
         if (nth > i) return;  // done logging that!
         else s_logiSet.put(key, nth);  // increment it.
      } else {
         s_logiSet.put(key, nth);
      }
      s_log.debug("A.logi(" + key + ") nth:" + nth + " " + message);
  }

  
  // Short for stg log.
  public static void slog(String message) {
    if (AntwebProps.isDevOrStageMode()) {
      s_log.info(message);
    }
  }
  
  private static int loopCounter = 0;
  public static boolean loopCount() {
    return loopCount(5000);
  }
  public static boolean loopCount(int divNum) {
    loopCounter = loopCounter + 1;
      return loopCounter == 1 || loopCounter % divNum == 0;
  }
  
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
    if (!AntwebProps.isDevOrStageMode()) return;

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
  
  public static boolean dev() {
    return AntwebProps.isDevMode();
  }

  public static void p(String str) {
    System.out.println(str);
  }

   
	public static String commaFormat(String num) {
	  if (num == null) return "0";
	  return commaFormat(Long.valueOf(num).intValue());
	}	
	
	public static String commaFormat(long num) {
	  if (num == 0) return "0";
	  return commaFormat(Long.valueOf(num).intValue());
	}
	
	public static String commaFormat(int num) {
	  if (num == 0) return "0";
      return NumberFormat.getNumberInstance(Locale.US).format(num);		
	}

}

/*
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
*/