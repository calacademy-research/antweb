package org.calacademy.antweb.util;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Date;

import javax.servlet.http.*;
import javax.servlet.*;

import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;

import org.calacademy.antweb.*;
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
        html += "<li><a href='" + AntwebProps.getDomainApp() + "/util.do?action=curiousQuery&name=" + query +"'>" + query + "</a>";
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
        for (NamedQuery namedQuery : queryList) {
          String results = runNamedQuery(namedQuery, connection);
          if (results != null)
            if (!results.contains("rowCount:</b>0"))
              message += results;
        }
        if ("".equals(message)) message = "Success";
        return message;        
    }

    public static String checkIntegrity(Connection connection) throws SQLException {
        String message = "</b>";
        message += "<h1>Integrity Check</h1>";
        message += " * - Ideally none of these queries return results, or the queries are modified to correctly display data integrity.";
        ArrayList<NamedQuery> queryList = Queries.getIntegrityQueries();       
        // Above, due to scrappy groups/login design, it is possible to not specify a admin_login for a group, and when the group is created
        // and used for insertion of records (SpecimenUpload.java:309) there is no access_login created, causing problems at loadAllSpecimenFiles times.
        for (NamedQuery namedQuery : queryList) {
          String results = runNamedQuery(namedQuery, connection);
          if (results != null && !results.contains("<br><b>rowCount:</b>0"))
              message += results;
        }
        if ("".equals(message)) message = "Success";
        return message;        
    }
    
    public static String runCurateAntcatQueries(Connection connection) throws SQLException {
        String message = "</b>";
        message += "<h1>Curate AntCat</h1>";
        message += " * - These AntWeb issues imply that there are changes to be made to AntCat.";
        ArrayList<NamedQuery> queryList = Queries.getNamedQueryList(Queries.getCurateAntcatNames());       
        //A.log("runCurateAntcatQueries");
        // Above, due to scrappy groups/login design, it is possible to not specify a admin_login for a group, and when the group is created
        // and used for insertion of records (SpecimenUpload.java:309) there is no access_login created, causing problems at loadAllSpecimenFiles times.
        for (NamedQuery namedQuery : queryList) {
          String results = runNamedQuery(namedQuery, connection);
          if (results != null)
            message += results;
        }
        if ("".equals(message)) message = "Success";
        return message;        
    }    

    public static String runDevIntegrityQueries(Connection connection) throws SQLException {
        String message = "</b>";
        message += "<h1>Dev Integrity</h1>";
        message += " * - These AntWeb issues imply issues for Antweb developers to resolve.";
        ArrayList<NamedQuery> queryList = Queries.getNamedQueryList(Queries.getDevIntegrityNames());       
        for (NamedQuery namedQuery : queryList) {
          String results = runNamedQuery(namedQuery, connection);
          if (results != null)
            message += results;
        }
        if ("".equals(message)) message = "Success";
        return message;        
    } 

    public static ArrayList<NamedQuery> getBattery(String name) {
        ArrayList<NamedQuery> battery = new ArrayList<NamedQuery>();

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

        String message = "</b><h1>Query: " + name + "</h1>";

        String queryBatter = null;
        if ("projectTaxonCounts".equals(name)) {
          battery = getBattery("projectTaxonCounts");
          
        } else {
          return "Query battery:" + name + " not found";
        }

        for (NamedQuery query : battery) {
          message += runNamedQuery(query, connection);
        }

        return message;
    }
    
    public static String curiousQuery(String name, Connection connection) throws SQLException {
        String message = "</b><h1>Query: " + name + "</h1>";

        NamedQuery namedQuery = null;
        for (NamedQuery query : Queries.getNamedQueries()) {
          if (query.getName().equals(name)) {
            namedQuery = query;        
            //A.log("curiousQuery() name:" + name);
            break;
          }
        }
        if (namedQuery == null) return "Query not found:" + name;
        String result = runNamedQuery(namedQuery, connection);
        if (result != null) message += result;
        return message;
    }
    
    public static String curiousQueries(Connection connection) throws SQLException {
        String message = "";
        for (NamedQuery namedQuery : Queries.getCuriousQueries()) {
          String results = runNamedQuery(namedQuery, connection);
          if (results != null)
            message += results;
        }
        if ("".equals(message)) message = "Success";
        return message;        
    }

    public static String runQueryWithParam(String queryName, String param, Connection connection) throws SQLException {
        NamedQuery namedQuery = QueriesWithParams.getNamedQueryWithParam(queryName, param);
        String result = "";
        String message = "";
        if (namedQuery == null) return "Query not found:" + queryName + " with param:" + param;
        result = runNamedQuery(namedQuery, connection);
        if (result != null) message += result;
        return message;
    }

    public static String runNamedQuery(NamedQuery namedQuery, Connection connection) throws SQLException {
        StringBuffer message = new StringBuffer();
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(connection, "runNamedQuery()");

            Date startTime = new Date();            

            String query = namedQuery.getQuery();
            if (query.contains("delete from")) {
              int result = stmt.executeUpdate(query);
              return "query executed result:" + result;
            }

		    message.append("<hr><br><b>Name: </b><a href='" + AntwebProps.getDomainApp() + "/util.do?action=curiousQuery&name=" + namedQuery.getName() + "'>" + namedQuery.getName() + "</a>\n");
            if (namedQuery.getDesc() != null)
                message.append("<br><br><b>Description:</b>" + namedQuery.getDesc() + "\r");

A.log("namedQuery:" + namedQuery.getDesc());

            message.append("<br><br><b>Query:</b> " + namedQuery.getQuery() + "\n");
		    message.append("<br><br><table>");
		    if (namedQuery.getHeader() != null) 
			  message.append("<tr>" + namedQuery.getHeader() + "</tr>\n");
            
            rset = stmt.executeQuery(query);
            int i = 0;
            while (rset.next()) {
              ++i;
              int columnCount = rset.getMetaData().getColumnCount();
              for (int j=1 ; j <= columnCount ; ++j) {
                String val = rset.getString(j);
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
                message.append("<br><br><b>Detail Query: </b><a href='" + AntwebProps.getDomainApp() + "/util.do?action=curiousQuery&name=" + namedQuery.getDetailQuery() + "'>" + namedQuery.getDetailQuery() + "</a>\n");
              }
            }

            message.append("<br><br>");
            stmt.close();
        } catch (SQLException e) {
            s_log.error("integrityQuery() e:" + e + " query:" + namedQuery.getQuery());
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "runNamedQuery()");
        }
        if ("".equals(message)) return null;
        return message.toString();
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
            message = "<br><br><b>Query:</b> " + theQuery + " <br><b>returns:</b><br><pre>";          
            while (rset.next()) {
              ++i;
              message += "\r\r";
              int columnCount = rset.getMetaData().getColumnCount();
              String val1 = rset.getString(1);
              message += val1;
              String val2, val3, val4, val5, val6 = "";
              if (columnCount > 1) {
                 message += ", " + rset.getString(2);
              }
              if (columnCount > 2) {
                message += ", " + rset.getString(3);
              }
              if (columnCount > 3) {
                message += ", " + rset.getString(4);
              }
              if (columnCount > 4) {
                message += ", " + rset.getString(5);
              }
              if (columnCount > 5) {
                message += ", " + rset.getString(6);       
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

