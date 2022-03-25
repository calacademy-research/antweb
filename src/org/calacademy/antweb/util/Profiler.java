package org.calacademy.antweb.util;

import java.util.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


public class Profiler {

/* Create a file in the web/log directory called profile.log.  Every hour, write the
   query statistics there.  Also viewable in real time in the serverStatus page.
   */

    protected static Date firstOfPeriodTime;

    protected static HashMap<String, Profile> profiles = new HashMap<>();

    private static final Log s_log = LogFactory.getLog(Profiler.class);

    private String slowestValue;

    public static void profile(String key, Date startTime) {
      Profiler.profile(key, startTime, null);
    }
    public static void profile(String key, Date startTime, String value) {

        if (firstOfPeriodTime == null) {
            firstOfPeriodTime = startTime;
        } else {
            if (AntwebUtil.minsSince(firstOfPeriodTime) > 100) {
                report(true);
                firstOfPeriodTime = startTime;
            }    
        }

        //A.log("min since first:" + AntwebUtil.minsSince(firstOfPeriodTime) + " min since start:" + AntwebUtil.minsSince(startTime) );

        if (profiles.containsKey(key)) {
            Profile profile = profiles.get(key);
            profile.add(startTime, value);
        } else {
            Profile profile = new Profile();
            profile.add(startTime, value);
            profiles.put(key, profile);
        }             
    }
        
    public static String report() {
        return report(false);
    }

    public static String report(boolean clear) {
      return report(clear, "profiler.jsp");
    }
    
    public static String report(boolean clear, String logName) {
        /** Called from AntwebUtil.isServerBusy() and serverStatus.do */
        String report = "";
        Set<String> keySet = profiles.keySet();
        for (String key : keySet) {
          Profile profile = profiles.get(key);
          report += " " + key + " - (count:" + profile.getCount() 
                 + " | avg:" + profile.getTotalTimePassed() / profile.getCount()
                 + " | max:" + profile.getMaxTimePassed() + " | mostSlow:" + profile.getMostSlowValue() + ")<br>";
        }
		LogMgr.appendLog(logName, new Date() + "  " + report + "\r\r" );
        if (clear) {
            profiles.clear();  // empty it out on report.
        }
        return report;
    }
}


class Profile {
    long totalTimePassed = 0;
    int count = 0;
    long maxTimePassed = 0;
    String mostSlowValue;

    int getCount() {
      return count;
    }
    
    long getTotalTimePassed() {
      return totalTimePassed;
    }

    void add(Date startTime, String value) {
      ++count;      
        
      Date endTime = new Date();     
      long timePassed = AntwebUtil.timePassed(startTime, endTime);
      
      totalTimePassed += timePassed;
      if (timePassed > maxTimePassed) {
        mostSlowValue = value;
        maxTimePassed = timePassed;
      }
    }

    String getMostSlowValue() {
      return mostSlowValue;
    }

    long getMaxTimePassed() {
      return maxTimePassed;
    }
}
