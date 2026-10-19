package org.calacademy.antweb.data;

import java.io.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.regexp.*;

import org.apache.struts.action.*;

import java.sql.*;
import java.util.ArrayList;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public final class AntWikiDataAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(AntWikiDataAction.class);

    private static final Log s_antwebEventLog = LogFactory.getLog("antwebEventLog");
 
	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {
		ActionForward c = Check.admin(request, mapping); if (c != null) return c;
		
		String message = "";
    /*
        Admin only. Called as:
          /antWikiData.do?action=checkForUpdates
        (The AntWiki Regional Taxon List import was removed in cleanup batch 3.)
    */

        DynaActionForm df = (DynaActionForm) form;
        String action = (String) df.get("action");

        if ("checkForUpdates".equals(action)) {
        
			Connection connection = null;
            String dbMethodName = DBUtil.getDbMethodName("AntwikiDataAction.execute()");
			try {
				DataSource dataSource = getDataSource(request, "conPool");
				connection = DBUtil.getConnection(dataSource, dbMethodName);

				message = AntWikiDataAction.checkForUpdates(connection);

			} catch (SQLException | ClassCastException e) {
				s_log.error("execute() e:" + e);
			} finally {
			    DBUtil.close(connection, this, dbMethodName);
			}        
		
        } else {
          message = "action not found:" + Html.esc(action);
        }
        request.setAttribute("message", message);
        return mapping.findForward("message");
    }
    
// ---------- Antwiki Species Update -------------
    
    // This is checked by the scheduler. Notify Brian if new Species found.

    public static String checkForUpdates(Connection connection) {
      String message = "";
      message += AntWikiDataAction.checkForValidSpeciesListUpdate(connection);
      message += ".  " + AntWikiDataAction.checkForFossilSpeciesListUpdate(connection);  
      return message;    
    }

    public static String checkForValidSpeciesListUpdate(Connection connection) {
      String upDate = AntWikiDataAction.getValidSpeciesListUpDate();
      if (upDate == null) return "Valid Species List update date not found on AntWiki";

      // Check in database. Is this the last update date?
      AntwebDb antwebDb = new AntwebDb(connection);
      boolean isCurrent = antwebDb.isCurrentInLookup(connection, "validSpeciesListUpDate", upDate);

      //boolean isCurrent = antwebDb.isCurrentInLookup(connection, "validSpeciesListUpDate", upDate);
      // Update lookup table. set upDate to validSpeciesUpDate      
      
      s_log.debug("isCurrent:" + isCurrent + " upDate:" + upDate);
       
      String prefix = "";
      if (!isCurrent) {
        // create an Admin message.  
        prefix = "Updated!  ";    
        s_log.debug("checkForValidSpeciesListUpdate() upDate:" + upDate);
        AdminAlertMgr.add("Antwiki Valid Species List Upload:" + upDate, connection);
      }
          
      return prefix + "Last Valid Species List update:" + upDate;
    }
        
    private static String getValidSpeciesListUpDate() {
       // String url = "http://antwiki.org/wiki/Species_Accounts";
        String url = "https://antwiki.org/wiki/Species_Accounts";
        String output = "";
        try {
          output = HttpUtil.getUrl(url);
        } catch (IOException e) {
          s_log.warn("getValidSpeciesListUpDate() e:" + e);
        }

        String validSpeciesListUpDate = dateAfter(output, "List of valid species (names in use)", 69);
        s_log.debug("AntWikiDataAction.getValidSpeciesListUpDate() validSpeciesUpDate:" + validSpeciesListUpDate);
        return validSpeciesListUpDate;
    }

    public static String checkForFossilSpeciesListUpdate(Connection connection) {

      String upDate = AntWikiDataAction.getFossilSpeciesListUpDate();
      if (upDate == null) return "Fossil Species List update date not found on AntWiki";
      
      //upDate = "test";
      
      // Check in database. Is this the last update date?
      AntwebDb antwebDb = new AntwebDb(connection);
      boolean isCurrent = antwebDb.isCurrentInLookup(connection, "fossilSpeciesListUpDate", upDate);
      
      String prefix = "";
      if (!isCurrent) {
        prefix = "Updated!  ";
        // create an Admin message.      
        s_log.debug("checkForFossilSpeciesListUpdate() upDate:" + upDate);
        AdminAlertMgr.add("Antwiki Fossil Species List Upload:" + upDate, connection);
      }
          
      return prefix + "Last Fossil Species List update:" + upDate;
    }
        
    private static String getFossilSpeciesListUpDate() {
      String url = "https://antwiki.org/wiki/Species_Accounts";
      String output = "";
      try {
        output = HttpUtil.getUrl(url);
      } catch (IOException e) {
        s_log.warn("getFossilSpeciesListUpDate() e:" + e);
      }
      String fossilSpeciesListUpDate = dateAfter(output, "List of valid fossil species (names in use)</a> (tab-delimited text) (Date: ", 76);
      s_log.debug("AntWikiDataAction.getFossilSpeciesListUpDate() validSpeciesUpDate:" + fossilSpeciesListUpDate);
 
      return fossilSpeciesListUpDate;
    }

    /** The 10 characters at offset after marker in page, if they are a date (else null). */
    public static String dateAfter(String page, String marker, int offset) {
        if (page == null) return null;
        int i = page.indexOf(marker);
        if (i < 0 || i + offset + 10 > page.length()) return null;
        return validDate(page.substring(i + offset, i + offset + 10));
    }

    private static final String[] DATE_FORMATS = {"uuuu-MM-dd", "dd-MM-uuuu", "dd/MM/uuuu", "MM/dd/uuuu", "dd.MM.uuuu"};

    /** The text, trimmed, when it is a real calendar date in one of DATE_FORMATS; otherwise null. */
    public static String validDate(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (!t.matches("[0-9]{1,4}[-/.][0-9]{1,2}[-/.][0-9]{1,4}")) return null;
        for (String f : DATE_FORMATS) {
            try {
                java.time.LocalDate.parse(t, java.time.format.DateTimeFormatter.ofPattern(f)
                    .withResolverStyle(java.time.format.ResolverStyle.STRICT));
                return t;
            } catch (java.time.format.DateTimeParseException e) {
                // try the next format
            }
        }
        return null;
    }
}
