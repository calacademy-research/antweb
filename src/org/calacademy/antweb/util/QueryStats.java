package org.calacademy.antweb.util;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import java.util.Date;

/**
 * Running statistics of one named database query: count, total, minimum and maximum milliseconds and when the
 * maximum happened. DBUtil keeps one per query name; util.do?action=logQueryStats writes them to the log
 * (DBUtil.logQueryStats). In memory only. Tests: test/org/calacademy/antweb/audit/CoreUtilFixesTest.java.
 */
public class QueryStats {

    private static final Log s_log = LogFactory.getLog(QueryStats.class);

    private int queryCount = 0;
    private long maxMillis = 0;
    private long minMillis = 0;
    private long minMaxDiff = 0;
    private Date maxMillisTimestamp;
    private long totalMillis = 0;


    public QueryStats() {
    }
    
    public synchronized void count(long millis) {
        ++queryCount;
        if (millis > maxMillis) {
          maxMillis = millis;
          maxMillisTimestamp = new Date();
        }
        if (queryCount == 1 || millis < minMillis) {  // the smallest so far (was: the latest below the max)
          minMillis = millis;
        }

        minMaxDiff = maxMillis - minMillis;  // a difference, as named (the ratio cut to a long was always 0)
    
        //A.log("count() minMillis:" + minMillis + " maxMillis:" + maxMillis + " minMacDiff:" + minMaxDiff);

        totalMillis = totalMillis + millis;
    }
    
    public String log() {
        return " count:" + queryCount + " totalMillis:" + totalMillis + " minMillis:" + minMillis + " maxMillis:" + maxMillis  + " maxMillisTimestamp:" + maxMillisTimestamp + " minMaxDiff:" + minMaxDiff;        
    }

}