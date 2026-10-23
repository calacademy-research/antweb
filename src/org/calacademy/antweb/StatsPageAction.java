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
     * The page's tables are counted once a day, at the end of the nightly run, after the nightly has updated the numbers
     * (Scheduler calls recount()), and every visit shows that copy (Brian Fisher, 2026-10-23: the page linked from the
     * home page was slow; counting joins every specimen with every image). After a restart there is no copy until the
     * first visit, which counts once; that copy is kept until the next nightly run. The page says when the tables were
     * counted ("statsCountedAt").
     */
    private static java.util.Map<String, Object> s_numbers = null;

    /** The kept tables, or null when there are none yet (after a restart). */
    static synchronized java.util.Map<String, Object> keptNumbers() {
        return s_numbers;
    }

    /** Count the tables now and keep them (the nightly run, at its end). Never throws. */
    public static String recount(Connection connection) {
        try {
            long start = System.currentTimeMillis();
            synchronized (StatsPageAction.class) {
                s_numbers = count(connection);
            }
            return "statistics page tables counted in " + (System.currentTimeMillis() - start) / 1000 + " s";
        } catch (Exception e) {
            s_log.warn("recount() e:" + e);
            return "statistics page tables not counted: " + e;
        }
    }

    /** The kept tables, or (none yet) counted now and kept. One count at a time. */
    static synchronized java.util.Map<String, Object> numbers(Connection connection) throws SQLException {
        if (s_numbers == null) s_numbers = count(connection);
        return s_numbers;
    }

    private static java.util.Map<String, Object> count(Connection connection) throws SQLException {
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
        numbers.put("statsCountedAt", new java.util.Date());
        return Collections.unmodifiableMap(numbers);
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
