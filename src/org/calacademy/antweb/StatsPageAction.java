package org.calacademy.antweb;

import java.util.*;
import java.io.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;

import org.calacademy.antweb.home.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class StatsPageAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(StatsPageAction.class);

    /*
     * The page's numbers are counted once and kept (2026-10-23, Brian Fisher: the page linked from the home page was
     * slow). Counting them joins every specimen with every image (a few seconds of database work) and they change only
     * with uploads and the nightly run, so they are recounted at most every MAX_AGE_MS, at the first visit after a
     * restart, and at the first visit after the nightly run (Scheduler calls clearCache()). The page says when they were
     * counted ("statsCountedAt").
     */
    public static final long MAX_AGE_MS = 6L * 60 * 60 * 1000;
    private static java.util.Map<String, Object> s_numbers = null;
    private static long s_countedAt = 0;

    /** Forget the kept numbers: the next visit counts them again (after the nightly run). */
    public static synchronized void clearCache() {
        s_numbers = null;
    }

    /** True when numbers counted at countedAt may still be shown at now. */
    public static boolean isFresh(long countedAt, long now, long maxAgeMs) {
        return countedAt > 0 && now >= countedAt && now - countedAt < maxAgeMs;
    }

    /** The kept numbers while fresh, otherwise null (then the caller opens a connection and calls numbers()). */
    static synchronized java.util.Map<String, Object> keptNumbers() {
        return s_numbers != null && isFresh(s_countedAt, System.currentTimeMillis(), MAX_AGE_MS) ? s_numbers : null;
    }

    /** The kept numbers, or newly counted ones when there are none or they are too old. One count at a time. */
    static synchronized java.util.Map<String, Object> numbers(Connection connection) throws SQLException {
        long now = System.currentTimeMillis();
        if (s_numbers != null && isFresh(s_countedAt, now, MAX_AGE_MS)) return s_numbers;
        java.util.Map<String, Object> numbers = new HashMap<>();
        StatisticsDb statisticsDb = new StatisticsDb(connection);
        ImageDb imageDb = new ImageDb(connection);
        numbers.put("imageStats", imageDb.getImageStats());
        numbers.put("imageTaxonStats", imageDb.getImageTaxonStats());
        numbers.put("extantData", statisticsDb.getExtantData()); // StatSet
        numbers.put("fossilData", statisticsDb.getFossilData()); // StatSet
        numbers.put("bioregionData", statisticsDb.getBioregionData()); // ArrayList<StatSet>
        numbers.put("extantMuseumData", statisticsDb.getExtantMuseumData()); // ArrayList<StatSet>
        numbers.put("fossilMuseumData", statisticsDb.getFossilMuseumData()); // ArrayList<StatSet>
        numbers.put("statsCountedAt", new java.util.Date(now));
        s_numbers = Collections.unmodifiableMap(numbers);
        s_countedAt = now;
        s_log.info("numbers() counted in " + (System.currentTimeMillis() - now) + " ms");
        return s_numbers;
    }

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

		boolean success = false;

        java.util.Map<String, Object> kept = keptNumbers();
        if (kept != null) {
            for (java.util.Map.Entry<String, Object> e : kept.entrySet()) request.setAttribute(e.getKey(), e.getValue());
            return mapping.findForward("success");
        }

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("StatsPageAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);

            java.util.Map<String, Object> numbers = numbers(connection);
            for (java.util.Map.Entry<String, Object> e : numbers.entrySet()) request.setAttribute(e.getKey(), e.getValue());
            success = true;            
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return mapping.findForward("error");
        } finally { 		
            DBUtil.close(connection, this, dbMethodName);
        }        
        
		if (success) {
		    return mapping.findForward("success");
		} else {
			return mapping.findForward("failure");
		}
    }


}
