package org.calacademy.antweb.util;

import java.util.*;
import java.util.Date;

import java.sql.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


public abstract class QueryManager {

    private static final Log s_log = LogFactory.getLog(QueryManager.class); 

    /**
     * No named query may run longer than this (Statement.setQueryTimeout); the page says so. Below Cloudflare's
     * 100 seconds, so the query stops before the visitor's request is dropped.
     */
    public static final int QUERY_TIMEOUT_SECS = 90;

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
        String head = "<hr><br><b>" + Html.esc(namedQuery.getName()) + "</b>: " + Html.esc(desc);
        try {
            String results = runNamedQueryHtml(namedQuery, connection);
            if (results == null || results.contains("<br><b>rowCount:</b>0\n")) {
                return head + "<br><br><b>None.</b><br><br>\n";
            }
            return results;
        } catch (Exception e) {
            s_log.warn("checkSection() " + namedQuery.getName() + " e:" + e);
            return head + "<br><br><b class=\"query-error\">This check could not run:</b> " + Html.esc(String.valueOf(e.getMessage())) + "<br><br>\n";
        }
    }

    public static String runDevIntegrityQueries(Connection connection) throws SQLException {
        String message = "</b>";
        message += "<h1>Dev Integrity</h1>";
        message += " * - These AntWeb issues imply issues for Antweb developers to resolve.";
        ArrayList<NamedQuery> queryList = Queries.getNamedQueryList(Queries.getDevIntegrityNames());
        for (NamedQuery namedQuery : queryList) {
          message += checkSection(namedQuery, connection);
        }
        if ("".equals(message)) message = "Success";
        return message;        
    } 

    public static ArrayList<NamedQuery> getBattery(String name) {
        ArrayList<NamedQuery> battery = new ArrayList<>();

        if ("projectTaxonCounts".equals(name)) {
          battery.addAll(Queries.getNamedQueryList(Queries.getBatteryNames()));
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
          message += runSection(query, connection);
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
        message += runSection(namedQuery, connection);
        return message;
    }

    /**
     * One named query on a page: its rows, or the error it ran into (shown, so a broken query is seen; the
     * end-to-end test of every query, tests/e2e/test_named_queries.py, looks for it).
     */
    static String runSection(NamedQuery namedQuery, Connection connection) {
        try {
            String result = runNamedQueryHtml(namedQuery, connection);
            return result == null ? "" : result;
        } catch (Exception e) {
            s_log.warn("runSection() " + namedQuery.getName() + " e:" + e);
            String desc = namedQuery.getDesc() == null ? "" : namedQuery.getDesc();
            // The database's error text names tables and columns: only for the queries only administrators run
            // (public and curator pages link to a few, QueryAction.namedQueryAccess).
            String detail = "admin".equals(QueryAction.namedQueryAccess(namedQuery.getName()))
                ? Html.esc(String.valueOf(e.getMessage())) : "please try again later; the administrators can see the error in the log.";
            return "<hr><br><b>Name: </b>" + Html.esc(namedQuery.getName()) + "<br><br><b>What it shows:</b> " + Html.esc(desc)
                + "<br><br><b class=\"query-error\">This query could not run:</b> " + detail + "<br><br>\n";
        }
    }
    
    public static String curiousQueries(Connection connection) throws SQLException {
        String message = "";
        for (NamedQuery namedQuery : Queries.getCuriousQueries()) {
          message += runSection(namedQuery, connection);
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
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECS);

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
        return runSection(namedQuery, connection);
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
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECS);

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
                message.append("<br><br><b>What it shows:</b> " + Html.esc(namedQuery.getDesc()) + "\n");

            //A.log("namedQuery:" + namedQuery.getDesc());

            message.append("<br><br><b>Query:</b> " + Html.esc(namedQuery.getQuery()) + "\n");
		    message.append("<br><br><table>");
		    if (namedQuery.getHeaderHtml() != null)
			  message.append("<tr>" + namedQuery.getHeaderHtml() + "</tr>\n");
            
            rset = stmt.executeQuery(query);
            int columns = rset.getMetaData().getColumnCount();
            int headings = namedQuery.getHeadingCount();
            int i = 0;
            while (rset.next()) {
              ++i;
              int columnCount = columns;
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
            // Read by tests/e2e/test_named_queries.py: every query has one heading per column.
            message.append("<span class=\"query-check\" data-columns=\"" + columns + "\" data-headings=\"" + headings + "\"></span>");
            if (columns != headings) {
              message.append("<br><b class=\"query-error\">The headings do not match the columns:</b> " + headings + " headings, " + columns + " columns.\n");
            }
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

