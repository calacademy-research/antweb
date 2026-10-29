package org.calacademy.antweb.util;

import java.util.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


/**
 * Times the slow public requests (taxon pages, specimen and collection pages, advanced, recent-image and field
 * guide searches, specimen list downloads): profile() adds to Profiler's shared in-memory table; report()
 * appends it to web/log/queryProfile.log. Shown on the server busy report (DBStatus) and the Server Status
 * page.
 */
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


