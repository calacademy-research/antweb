package org.calacademy.antweb.util;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import java.util.Date;

public class QueryStats {

    private static Log s_log = LogFactory.getLog(QueryStats.class);

    private int queryCount = 0;
    private long maxMillis = 0;
    private long minMillis = 0;
    private long minMaxDiff = 0;
    private Date maxMillisTimestamp;
    private long totalMillis = 0;


    public QueryStats() {
    }
    
    public void count(long millis) {
        ++queryCount;
        if (millis > maxMillis) {
          maxMillis = millis;
          maxMillisTimestamp = new Date();
        }
        if (millis < maxMillis) {
          minMillis = millis;
        }

        if (minMillis > 0 && maxMillis > 0) {
          minMaxDiff = (long)((double)minMillis / (double)maxMillis);          
        }
    
        //A.log("count() minMillis:" + minMillis + " maxMillis:" + maxMillis + " minMacDiff:" + minMaxDiff);

        totalMillis = totalMillis + millis;
    }
    
    public String log() {
        return " count:" + queryCount + " totalMillis:" + totalMillis + " minMillis:" + minMillis + " maxMillis:" + maxMillis  + " maxMillisTimestamp:" + maxMillisTimestamp + " minMaxDiff:" + minMaxDiff;        
    }

}