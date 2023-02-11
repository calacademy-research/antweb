package org.calacademy.antweb.util;

import java.util.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


public class QueryProfiler extends Profiler {

/* Create a file in the web/log directory called queryProfile.log.  Every hour, write the
   query statistics there.  Also viewable in real time in the serverStatus page.
   */
    private static final Log s_log = LogFactory.getLog(QueryProfiler.class);

    public static void profile(String query, Date startTime) {
        Profiler.profile(query, startTime);
    }

    public static String report(boolean clear) {
        return report(clear, "queryProfile.log");
    }
}


