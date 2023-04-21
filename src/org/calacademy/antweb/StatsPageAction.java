package org.calacademy.antweb;

import java.util.*;
import java.io.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;

import org.calacademy.antweb.home.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class StatsPageAction extends Action {

    private static final Log s_log = LogFactory.getLog(StatisticsAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

		boolean success = false;

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("StatsPageAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);

            StatisticsDb statisticsDb = new StatisticsDb(connection);
            ImageDb imageDb = new ImageDb(connection);

            HashMap<String, int[]> imageStats = imageDb.getImageStats();
            for (String status : imageStats.keySet()) {
              int[] stats = imageStats.get(status);
              //A.log(" key:" + status + " total:" + stats[0] + " worker:" + stats[1] + " male:" + stats[2] + " queen:" + stats[3] + " other:" + stats[4]); 
            }
            request.setAttribute("imageStats", imageStats);

            HashMap<String, Integer> imageTaxonStats = imageDb.getImageTaxonStats();
            request.setAttribute("imageTaxonStats", imageTaxonStats);

/*
+----------------+----------+--------+-------+-------+-------+
| status         |    total | worker | male  | queen | other |
+----------------+----------+--------+-------+-------+-------+
| valid          |   187252 | 153469 | 12869 | 19056 |    97 |
| morphotaxon    |    29372 |  22516 |  3164 |  2926 |    24 |
| indetermined   |     4001 |   2607 |   940 |   411 |     0 |
| unrecognized   |      412 |    252 |    66 |    64 |     0 |
| unavailable    |      141 |    135 |     6 |     0 |     0 |
| unidentifiable |       29 |     29 |     0 |     0 |     0 |
+----------------+----------+--------+-------+-------+-------+
6 rows in set (0.93 sec)
*/   

            request.setAttribute("extantData", statisticsDb.getExtantData()); // StatSet
            request.setAttribute("fossilData", statisticsDb.getFossilData()); // StatSet
            
            request.setAttribute("bioregionData", statisticsDb.getBioregionData()); // ArrayList<StatSet>

            request.setAttribute("extantMuseumData", statisticsDb.getExtantMuseumData()); // ArrayList<StatSet>
            request.setAttribute("fossilMuseumData", statisticsDb.getFossilMuseumData()); // ArrayList<StatSet>
            
            //request.setAttribute("imageMuseumData", statisticsDb.getImageData()); // ArrayList<StatSet>

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
