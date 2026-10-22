package org.calacademy.antweb.util;

import java.util.*;
import java.util.Date;

import java.sql.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


public abstract class QueryManager {

    private static final Log s_log = LogFactory.getLog(QueryManager.class); 

// ---------------

    public static String getQueryList(String listName) {
      String html = "";
      ArrayList<String> queries = null;
      if ("Geolocale".equals(listName)) queries = Queries.getGeolocaleQueries();    
      if ("Login".equals(listName)) queries = Queries.getLoginQueries();    
      if ("Groups".equals(listName)) queries = Queries.getGroupQueries();    
      int i = 0;
      if (queries == null) {
        s_log.warn("QueryList:" + listName + " not found.");
        return null;
      }
      for (String query : queries) {
        if (i == 0) html += "<ul align=left>";
        ++i;
        html += "<li><a href='" + AntwebProps.getDomainApp() + "/query.do?action=curiousQuery&name=" + query +"'>" + query + "</a>";
        if (i == queries.size()) html += "</ul>";
      }
      return html;
    }

// ---------------- 

    public static String adminAlerts(Connection connection) throws SQLException {
        String message = "";
        ArrayList<NamedQuery> queryList = Queries.getAdminCheckQueries();       
        // Above, due to scrappy groups/login design, it is possible to not specify a admin_login for a group, and when the group is created
        // and used for insertion of records (SpecimenUpload.java:309) there is no access_login created, causing problems at loadAllSpecimenFiles times.
        s_log.debug("Running adminAlerts:" + queryList);
        for (NamedQuery namedQuery : queryList) {
          String results = runNamedQueryHtml(namedQuery, connection);
          //A.log("namedQuery:" + namedQuery + " results:" + results);
          if (results != null)
            if ("geolocaleTaxaPresent".equals(namedQuery.getName())) {
              if (results.contains("records missing")) {
                  s_log.warn("Investigate: " + results);
                  message += results;
              }
            } else{
                if (!results.contains("rowCount:</b>0"))
                    message += results;
            }
        }
        if ("".equals(message)) message = "Success";
        return message;        
    }

    /**
     * The Integrity Check page: a plain-language explanation, then every check in its own section.
     * A check that finds nothing says "None"; a check that fails says so and the others still run.
     */
    public static String checkIntegrity(Connection connection) throws SQLException {
        StringBuilder message = new StringBuilder("</b><h1>Integrity Check</h1>");
        message.append(ReportHelp.INTEGRITY);
        for (NamedQuery namedQuery : Queries.getIntegrityQueries()) {
            message.append(checkSection(namedQuery, connection));
        }
        return message.toString();
    }

    /** One check of a report page: its rows, "None." when it finds nothing, or the error it ran into. */
    static String checkSection(NamedQuery namedQuery, Connection connection) {
        String desc = namedQuery.getDesc() == null ? "" : namedQuery.getDesc();
        String head = "<hr><br><b>" + Html.esc(namedQuery.getName()) + "</b>: " + desc;
        try {
            String results = runNamedQueryHtml(namedQuery, connection);
            if (results == null || results.contains("<br><b>rowCount:</b>0\n")) {
                return head + "<br><br><b>None.</b><br><br>\n";
            }
            return results;
        } catch (Exception e) {
            s_log.warn("checkSection() " + namedQuery.getName() + " e:" + e);
            return head + "<br><br><b>This check could not run:</b> " + Html.esc(String.valueOf(e.getMessage())) + "<br><br>\n";
        }
    }

    public static String runDevIntegrityQueries(Connection connection) throws SQLException {
        String message = "</b>";
        message += "<h1>Dev Integrity</h1>";
        message += " * - These AntWeb issues imply issues for Antweb developers to resolve.";
        ArrayList<NamedQuery> queryList = Queries.getNamedQueryList(Queries.getDevIntegrityNames());       
        for (NamedQuery namedQuery : queryList) {
          String results = runNamedQueryHtml(namedQuery, connection);
          if (results != null)
            message += results;
        }
        if ("".equals(message)) message = "Success";
        return message;        
    } 

    public static ArrayList<NamedQuery> getBattery(String name) {
        ArrayList<NamedQuery> battery = new ArrayList<>();

        if ("projectTaxonCounts".equals(name)) {
          battery.add(Queries.getNamedQuery("projectTaxaCount"));
          battery.add(Queries.getNamedQuery("projectTaxaCountByProject"));
          battery.add(Queries.getNamedQuery("projectTaxaCountByProjectRank"));
        }
        return battery;
    }

    // Invoke like: http://localhost/antweb/query.do?action=queryBattery&name=projectTaxonCounts
    public static String queryBattery(String name, Connection connection) throws SQLException {
        ArrayList<NamedQuery> battery = null;

        String message = "</b><h1>Query: " + Html.esc(name) + "</h1>";

        String queryBatter = null;
        if ("projectTaxonCounts".equals(name)) {
          battery = getBattery("projectTaxonCounts");
          
        } else {
          return "Query battery:" + Html.esc(name) + " not found";
        }

        for (NamedQuery query : battery) {
          message += runNamedQueryHtml(query, connection);
        }

        return message;
    }
    
    public static String curiousQuery(String name, Connection connection) throws SQLException {
        String message = "</b><h1>Query: " + Html.esc(name) + "</h1>";

        NamedQuery namedQuery = null;
        for (NamedQuery query : Queries.getNamedQueries()) {
          if (query.getName().equals(name)) {
            namedQuery = query;        
            //A.log("curiousQuery() name:" + name);
            break;
          }
        }
        if (namedQuery == null) return "Query not found:" + Html.esc(name);
        if ("worldantsUploads".equals(name)) message += ReportHelp.WORLDANTS_UPLOADS;
        String result = runNamedQueryHtml(namedQuery, connection);
        if (result != null) message += result;
        return message;
    }
    
    public static String curiousQueries(Connection connection) throws SQLException {
        String message = "";
        for (NamedQuery namedQuery : Queries.getCuriousQueries()) {
          String results = runNamedQueryHtml(namedQuery, connection);
          if (results != null)
            message += results;
        }
        if ("".equals(message)) message = "Success";
        return message;        
    }

    public static NamedQuery runQueryWithParam(String queryName, String param, Connection connection) throws SQLException {
        NamedQuery namedQuery = QueriesWithParams.getNamedQueryWithParam(queryName, param);
        String result = "";
        String message = "";
        if (namedQuery == null) {
            return null;  // was a NullPointerException; callers treat null as not found
        }
        runNamedQuery(namedQuery, connection);

        return namedQuery;
    }

    // different from runNamedQueryHtl. Results attached to namedQuery. Better way. Eventually both...
    public static void runNamedQuery(NamedQuery namedQuery, Connection connection) throws SQLException {
        StringBuffer message = new StringBuffer();
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "runNamedQuery()");

            Date startTime = new Date();

            String query = namedQuery.getQuery();
            if (!isReadOnly(query)) {
                namedQuery.setResult("Refused: named queries only read data.");
                return;
            }

            message.append(namedQuery.getHeader() + "\n");

            rset = stmt.executeQuery(query);
            int i = 0;
            while (rset.next()) {
                ++i;
                int columnCount = rset.getMetaData().getColumnCount();
                for (int j=1 ; j <= columnCount ; ++j) {
                    String val = rset.getString(j);
                    if (j == 1) {
                        message.append(val);
                    } else if (j == columnCount) {
                        message.append("\t" + val + "\n");
                    } else {
                        message.append("\t" + val);
                    }
                }
            } // end while

            namedQuery.setResult(message.toString());
            namedQuery.setRowCount(i);

            long timePassed = AntwebUtil.minsSince(startTime);
            String note = "min: " + timePassed + "\n";
            if (timePassed < 3) {
                timePassed = AntwebUtil.secsSince(startTime);
                note = "secs: " + timePassed + "\n";
            }
            namedQuery.setTimePassedNote(note);

            stmt.close();
        } catch (SQLException e) {
            s_log.error("runNamedQuery() e:" + e + " query:" + namedQuery.getQuery());
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "runNamedQuery()");
        }
    }

    /** Named queries only read: a query must start with select (or with/show/explain). */
    public static boolean isReadOnly(String query) {
        if (query == null) return false;
        String q = query.trim().toLowerCase();
        while (q.startsWith("(")) q = q.substring(1).trim();
        return (q.startsWith("select ") || q.startsWith("select\n") || q.startsWith("with ")
                || q.startsWith("show ") || q.startsWith("explain "))
            && !q.matches("(?s).*\\b(delete\\s+from|insert\\s+into|update\\s+\\w+\\s+set|drop\\s+table|truncate|alter\\s+table|replace\\s+into|into\\s+outfile)\\b.*");
    }

    public static String runQueryWithParamHtml(String queryName, String param, Connection connection) throws SQLException {
        NamedQuery namedQuery = QueriesWithParams.getNamedQueryWithParam(queryName, param);
        String result = "";
        String message = "";
        if (namedQuery == null) return "Query not found:" + Html.esc(queryName) + " with param:" + Html.esc(param);
        result = runNamedQueryHtml(namedQuery, connection);
        if (result != null) message += result;
        return message;
    }

    /**
     * Most rows a named query shows on its page (2026-10-22). The public imageData query ("Download All"
     * on the recent images page) listed every image, about 13 MB of HTML per view, and every view was
     * written whole to messages.txt. Rows past the limit are counted (rowCount stays the full count) but
     * not shown. The species lists with range data are bounded by the number of species of a place and
     * are shown in full (UNCAPPED).
     */
    public static final int MAX_ROWS_SHOWN = 5000;
    static final Set<String> UNCAPPED = Set.of("bioregionSpeciesListWithRangeData", "geolocaleSpeciesListWithRangeData");

    public static int maxRowsShown(String queryName) {
        return queryName != null && UNCAPPED.contains(queryName) ? Integer.MAX_VALUE : MAX_ROWS_SHOWN;
    }

    /** The note under a table that shows only the first rows. */
    public static String notShownNote(int shown, int total) {
        if (total <= shown) return "";
        return "<br><b>Showing the first " + String.format("%,d", shown) + " of " + String.format("%,d", total)
            + " rows</b> (" + String.format("%,d", total - shown) + " more not shown on this page).\n";
    }

    public static String runNamedQueryHtml(NamedQuery namedQuery, Connection connection) throws SQLException {
        StringBuffer message = new StringBuffer();
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "runNamedQueryHtml()");

            if (stmt == null) {
                s_log.warn("Failed to getStatement: runNamedQueryHtml()");
                return null;
            }

            Date startTime = new Date();            

            String query = namedQuery.getQuery();
            if (!isReadOnly(query)) {
              return "Refused: named queries only read data.";
            }

            String permalink = AntwebProps.getDomainApp() + "/query.do?name=" + namedQuery.getName() ;
            String title = namedQuery.getName();
            if (namedQuery.getParam() != null) {
                permalink += "&param=" + Html.url(namedQuery.getParam());
                title += ": " + namedQuery.getParam();
            }

		    message.append("<hr><br><b>Name: </b><a href='" + Html.escAttr(permalink) + "'>" + Html.esc(title) + "</a>\n");
            if (namedQuery.getDesc() != null)
                message.append("<br><br><b>Description:</b>" + namedQuery.getDesc() + "\r");

            //A.log("namedQuery:" + namedQuery.getDesc());

            message.append("<br><br><b>Query:</b> " + Html.esc(namedQuery.getQuery()) + "\n");
		    message.append("<br><br><table>");
		    if (namedQuery.getHeaderHtml() != null)
			  message.append("<tr>" + namedQuery.getHeaderHtml() + "</tr>\n");
            
            // Stream the rows (MySQL Connector/J: forward-only, read-only, fetch size MIN_VALUE) so rows past
            // the limit are counted without holding the whole result in memory (imageData: every image).
            stmt.setFetchSize(Integer.MIN_VALUE);
            rset = stmt.executeQuery(query);
            int i = 0;
            int maxShown = maxRowsShown(namedQuery.getName());
            while (rset.next()) {
              ++i;
              if (i > maxShown) continue;  // counted, not shown
              int columnCount = rset.getMetaData().getColumnCount();
              for (int j=1 ; j <= columnCount ; ++j) {
                String val = cell(rset.getString(j));
                if (j == 1) {
                  message.append("<tr><td>" + val + "</td>");
                } else if (j == columnCount) {
                  message.append("<td>" + val + "</td></tr>");
                } else {
                  message.append("<td>" + val + "</td>");
                }
              }              
            } // end while

            message.append("</table>\n");
            message.append(notShownNote(maxShown, i));
            message.append("<br><b>rowCount:</b>" + i + "\n");

            long timePassed = AntwebUtil.minsSince(startTime);
            String note = "<br><b>min:</b>" + timePassed + "\n";
            if (timePassed < 3) {
                timePassed = AntwebUtil.secsSince(startTime);
                note = "<br><b>secs:</b>" + timePassed + "\n";
            }
            message.append(note);

            if (i > 0) {               
              if (namedQuery.getDetailQuery() != null) {
                message.append("<br><br><b>Detail Query: </b><a href='" + AntwebProps.getDomainApp() + "/query.do?action=curiousQuery&name=" + namedQuery.getDetailQuery() + "'>" + namedQuery.getDetailQuery() + "</a>\n");
              }
            }

            message.append("<br><br>");
            stmt.close();
        } catch (SQLException e) {
            s_log.error("runNamedQueryHtml() e:" + e + " query:" + namedQuery.getQuery());
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "runNamedQueryHtml()");
        }
        if ("".equals(message)) return null;
        return message.toString();
    }    


    /** A link a named query builds itself: an a tag to an AntWeb page, with a plain text label. */
    private static final java.util.regex.Pattern QUERY_LINK = java.util.regex.Pattern.compile(
        "<a href=(['\"]?)([^'\"<>\\s]+)\\1>([^<>]*)</a>");

    /**
     * One value of a named query's result, for the HTML table. Values are data (specimen fields,
     * names, log text) and are shown as text; only the links that some queries build in SQL
     * (concat('<a href=...', taxon_name, ...)) to AntWeb pages are kept as links.
     */
    public static String cell(String value) {
        return cell(value, AntwebProps.getDomainApp());
    }

    public static String cell(String value, String domainApp) {
        if (value == null) return "null";
        java.util.regex.Matcher m = QUERY_LINK.matcher(value);
        if (m.matches()) {
            String href = m.group(2);
            for (String prefix : new String[] {domainApp + "/", "http://antweb.org/", "https://www.antweb.org/"}) {
                if (prefix != null && !prefix.startsWith("null") && href.startsWith(prefix)) return value;
            }
        }
        return Html.esc(value);
    }

    // This method can be called simply to get html output from a query. Used by UtilAction.java.
    public static String getQueryResults(Connection connection, String theQuery) throws SQLException {

        String message = "";
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "getQueryResults()");
            rset = stmt.executeQuery(theQuery);
            int i = 0;
            message = "<br><br><b>Query:</b> " + Html.esc(theQuery) + " <br><b>returns:</b><br><pre>";          
            while (rset.next()) {
              ++i;
              message += "\r\r";
              int columnCount = rset.getMetaData().getColumnCount();
              String val1 = rset.getString(1);
              message += Html.esc(val1);
              String val2, val3, val4, val5, val6 = "";
              if (columnCount > 1) {
                 message += ", " + Html.esc(rset.getString(2));
              }
              if (columnCount > 2) {
                message += ", " + Html.esc(rset.getString(3));
              }
              if (columnCount > 3) {
                message += ", " + Html.esc(rset.getString(4));
              }
              if (columnCount > 4) {
                message += ", " + Html.esc(rset.getString(5));
              }
              if (columnCount > 5) {
                message += ", " + Html.esc(rset.getString(6));       
              }
            }
            message += "</pre>";
        } catch (SQLException e) {
            s_log.error("getQueryResults() query:" + theQuery + " e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "getQueryResults()");
        }

        if ("".equals(message)) message = "Success";
        return message;        
    }
    
}

