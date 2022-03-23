package org.calacademy.antweb.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/*
Development utility. Add a call like this to count occurrences of code visits.

ProfileCounter.add("SpecimenUploadProcess.getTaxon()");

Then it can either be logged as such:

ProfileCounter.report();

or for real time access, visit /serverStatus.do and look for "ProfileCounter:"

------------------

Nice way to figure what bit of code is calling a method, and how many times, is to add this:

        ProfileCounter.add("[methodName]()]" + AntwebUtil.getShortStackTrace());

      ... and then look at the serverStatus.do report for ProfileCounter:

 */

public class ProfileCounter {
    private static HashMap<String, Integer> s_countMap = new HashMap<String, Integer>();

    private final static Log s_log = LogFactory.getLog(ProfileCounter.class);
    
    public static void add(String label) {
        Integer count = s_countMap.get(label);
        if (count == null) {
            s_countMap.put(label, 1);
        } else {
            count = count + 1;
            s_countMap.put(label, count);
        }
    }

    public static void reset() {
        s_countMap = new HashMap<String, Integer>();
    }

    public static void report() {
        String report = getReport();
        s_log.debug(report);
    }

    public static String getReport() {
        String report = "";
        Set<String> keys = s_countMap.keySet();
        for (String key : keys) {
            report += "<br><br>\n\n" + key + ":" + s_countMap.get(key);
        }
        return report;
    }

}

