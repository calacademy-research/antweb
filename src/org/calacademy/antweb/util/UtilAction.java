
package org.calacademy.antweb.util;

import java.io.*;
import java.util.*;

import javax.servlet.http.*;

//import org.apache.fop.apps.Driver;
//import org.apache.fop.apps.FOPException;
//import org.apache.fop.messaging.MessageHandler;

import com.zonageek.jpeg.JpegException;
import org.apache.struts.action.*;

import com.zonageek.jpeg.Jpeg;

import java.sql.*;
import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

import com.google.gson.*;


public class UtilAction extends Action {

    private static Log s_log = LogFactory.getLog(UtilAction.class);
    
    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response) {

        HttpSession session = request.getSession();
        HttpUtil.setUtf8(request, response); 

        Connection connection = null;
		ActionForward returnLoc = null;

		UtilForm theForm = (UtilForm) form;
		String action = theForm.getAction();
        String param = theForm.getParam();
        String param2 = theForm.getParam2();
		int num = theForm.getNum();
		String name = theForm.getName();

        //A.log("execute() name:" + name + " param:" + param);
        try {

            DataSource dataSource = getDataSource(request, "longConPool");
            connection = DBUtil.getConnection(dataSource, "UtilAction.execute()");

			if (action != null) {
				if (action.equals("postInstantiate")) {
					AntwebMgr.postInitialize(connection);
					return null;
				}

			  if (action.equals("unlockImageUpload")) {
				AntwebUtil.writeFile("/var/www/html/imageUpload/" + "imageUploadInProcess.txt", "0");
				returnLoc = (mapping.findForward("success"));
			  }
			  if ("testMessage".equals(action)) {
				   String message = "Test Message";
				   s_log.warn("execute() " + message);
				   request.setAttribute("message", message);
				   return (mapping.findForward("message")); 
			  }
			  if ("unboldMessage".equals(action)) {
				   String message = "Test Message";
				   s_log.warn("execute() " + message);
				   request.setAttribute("message", message);
				   return (mapping.findForward("unboldMessage")); 
			  }
			  if ("bareMessage".equals(action)) {
				   String message = "Bare Message";
				   s_log.warn("execute() " + message);
				   request.setAttribute("message", message);
				   return (mapping.findForward("bareMessage")); 
			  }
			  if ("testMobile".equals(action)) {
				   String message = "Test Mobile";
				   s_log.warn("execute() " + message);
				   request.setAttribute("message", message);
				   return (mapping.findForward("testMobile")); 
			  }
			  
			  if (action.equals("checkIntegrity")) {
				String message = QueryManager.checkIntegrity(connection);
				request.setAttribute("message", message);
				returnLoc = mapping.findForward("adminHtmlMessage");
			  }

			  if (action.equals("zonageekTest")) {
				String message = zonageekTest(connection);
				request.setAttribute("message", message);
				returnLoc = mapping.findForward("adminHtmlMessage");
			  }
			  
			  if (action.equals("curiousQueries")) {
				String message = QueryManager.curiousQueries(connection);
				request.setAttribute("message", message);
				returnLoc = mapping.findForward("adminHtmlMessage");
			  }

			  if (action.equals("curiousQuery")) {
				String message = QueryManager.curiousQuery(name, connection);
				request.setAttribute("message", message);
				returnLoc = mapping.findForward("adminHtmlMessage");
			  }

			  if (action.equals("adminAlerts")) {
				String message = QueryManager.adminAlerts(connection);
				request.setAttribute("message", message);
				returnLoc = mapping.findForward("adminHtmlMessage");
			  }

			  // utilAction.do?action=descEdit&field=notes
			  if (action.equals("descEdit")) { 
				String field = theForm.getField();
			
				String query = "select taxon_name, title, content from description_edit where title = \"" + field + "\"";
				String message = QueryManager.getQueryResults(connection, query);
				request.setAttribute("message", message);
				returnLoc = mapping.findForward("message");
			  }

			  if (action.equals("geolocaleIntroduced")) { 
				String field = theForm.getField();
				String query = "select gt.taxon_name, is_introduced, is_endemic from geolocale_taxon gt, taxon t where gt.taxon_name = t.taxon_name and t.taxarank in ('species', 'subspecies') and geolocale_id = " + field + " and (is_introduced = 1 or is_endemic = 0)";
				String message = QueryManager.getQueryResults(connection, query);
				request.setAttribute("message", message);
				returnLoc = mapping.findForward("message");
			  }


			  if (action.equals("logQueryStats")) {
				DBUtil.logQueryStats();
				String message = "Query stats logged to queryStats.log. <a href='" + AntwebProps.getDomainApp() + "/showLog.do?action=queryStatsLog'>Query Stats Log</a>";
				request.setAttribute("message", message);
				returnLoc = (mapping.findForward("adminMessage"));                  
			  }
			  if (action.equals("homonyms")) {
				HomonymDb homonymDb = new HomonymDb(connection);
				ArrayList<Taxon> homonyms = homonymDb.getHomonyms();
				//s_log.error("execute()  homonyms:" + homonyms);
				request.setAttribute("homonyms", homonyms);
				returnLoc = (mapping.findForward("homonyms"));                  
			  }

			  if (action.equals("events")) {
				ArrayList<Event> events = (new EventDb(connection)).getEvents();
				request.setAttribute("events", events);
				HashMap<Integer, Login> curators = (new LoginDb(connection)).getCuratorMap();
				//A.log("execute()  events:" + events + " curators:" + curators);
				request.setAttribute("curators", curators);
				returnLoc = (mapping.findForward("events"));                  
			  }

			  if (action.equals("reloadAntwebMgr")) {
			    boolean forceReload = true;
			    if ("group".equals(name)) {
                    GroupMgr.populate(connection, forceReload);
                    request.setAttribute("message", "GroupMgr Reloaded.");    
			    } else if ("allAntweb".equals(name)) {
                    AllAntwebMgr.populate(connection);
                    request.setAttribute("message", "AllAntwebMgr Reloaded.");    
			    } else if ("login".equals(name)) {
                    LoginMgr.populate(connection, forceReload, true);
                    request.setAttribute("message", "LoginMgr Reloaded.");    
			    } else if ("project".equals(name)) {
                    ProjectMgr.populate(connection, forceReload);
                    request.setAttribute("message", "ProjectMgr Reloaded.");    
			    } else if ("bioregion".equals(name)) {
                    BioregionMgr.populate(connection, forceReload);
                    request.setAttribute("message", "BioregionMgr Reloaded.");    
			    } else if ("museum".equals(name)) {
                    MuseumMgr.populate(connection, forceReload);
                    request.setAttribute("message", "MuseumMgr Reloaded.");    
			    } else if ("geolocale".equals(name)) {
                    GeolocaleMgr.populate(connection, forceReload, true);  // Slow!
                    request.setAttribute("message", "GeolocaleMgr Reloaded.");    
			    } else if ("taxonProp".equals(name)) {
                    TaxonPropMgr.populate(connection, forceReload);
                    request.setAttribute("message", "TaxonPropMgr Reloaded.");    
			    } else if ("taxon".equals(name)) {
                    TaxonMgr.populate(connection, forceReload, true);
                    request.setAttribute("message", "TaxonMgr Reloaded.");    
			    } else if ("upload".equals(name)) {
                    UploadMgr.populate(connection, forceReload);
                    request.setAttribute("message", "UploadMgr Reloaded.");    
			    } else if ("artist".equals(name)) {
                    ArtistMgr.populate(connection, forceReload, true);
                    request.setAttribute("message", "ArtistMgr Reloaded.");    
			    } else if ("adminAlert".equals(name)) {
                    AdminAlertMgr.populate(connection);        
                    request.setAttribute("message", "AdminAlertMgr Reloaded.");
                } else {
                    AntwebMgr.populate(connection, true);
                    request.setAttribute("message", "AntwebMgr Reloaded.");                
                }
                returnLoc = (mapping.findForward("message"));
                return returnLoc;                  
			  }

			  if (action.equals("archiveLogs")) { 
				String message = LogMgr.archiveLogs();

				request.setAttribute("message", message);
				returnLoc = (mapping.findForward("adminMessage"));                  
			  } 


			  if (action.equals("gson")) {
				Geolocale country = GeolocaleMgr.getCountry("Algeria");
				Gson gson = new Gson();
				String message = " country:" + country + " gson:" + gson.toJson(country);                    

				request.setAttribute("message", message);
				returnLoc = (mapping.findForward("adminMessage"));                  
			  }

			  if (action.equals("emailTest")) {
			    String recipients = AntwebUtil.getDevEmail(); // + ", " + AntwebUtil.getAdminEmail();
			    String subject = "Test Message from Antweb";
			    String body = "This is a test message sent from the antweb server.";
				Emailer.sendMail(recipients, subject, body);
				String message = "Message sent";                    
				request.setAttribute("message", message);
				returnLoc = (mapping.findForward("adminMessage"));                  
			  }

			  if (action.equals("cpuCheck")) {
			    String message = AntwebSystem.cpuCheck();              
				request.setAttribute("message", message);
				returnLoc = (mapping.findForward("adminMessage"));                  
			  }

			  if (action.equals("diskFree")) {
			    String message = FileUtil.getDiskFree();              
				request.setAttribute("message", message);
				returnLoc = (mapping.findForward("adminMessage"));                  
			  }

			  if (action.equals("isDiskLow")) {
			    int percent = FileUtil.getPercentDiskFull();              
                String message = "Disk full:" + percent;
				request.setAttribute("message", message);
				returnLoc = (mapping.findForward("adminMessage"));                  
			  }
			  
			  if (action.equals("imageCheck")) {
			    AntwebFunctions.imageCheck();     
			    String message = "imageCheck performed.";         
				request.setAttribute("message", message);
				returnLoc = (mapping.findForward("adminMessage"));                  
			  }	  			 

			  if (returnLoc != null) return returnLoc;
			}               
			request.setAttribute("message", "action not found:" + action);
			return (mapping.findForward("message")); 

        } catch (SQLException e) {
            s_log.error("execute() action:" + action + " e:" + e);
            AntwebUtil.errorStackTrace(e);
		} catch (IOException e) {
			s_log.error("execute() action:" + action + " e:" + e);
			AntwebUtil.errorStackTrace(e);
        } finally {
            DBUtil.close(connection, this, "UploadAction.execute() 1");
        }

        //this shouldn't happen in this example
        s_log.error("execute()  This should not happen");
        return mapping.findForward("failure");
    }
        
    public String zonageekTest(Connection connection) {
      String message = "zonageekTest()";
	  String docRoot = AntwebProps.getDocRoot();

      /* How could we manage to cycle through each taxon, and for each image of each taxon, 	
         test this zonageek business. 
      SpecimenImage theImage = null;
      theImage.getHighres()
      */
	  String imageName = "casent0740806_h_1_high.jpg";
	  String imagePath = docRoot + imageName;
	  s_log.warn("zonageekTest() imagePath:" + imagePath);
	
      if (imagePath.contains("/null")) {
        s_log.error("zonageekTest() WSS.  Image path contains null in bigPicture-body.jsp:" + imagePath); 
        //continue;
      } else {
        Jpeg jpeg = new Jpeg();    
  
        try {
          jpeg.read(new FileInputStream(imagePath));
  
        } catch (JpegException e) {
          message = "WSS. zonageekTest() e:" + e + " on " + imagePath;
          AntwebUtil.log(message);
          
          //continue;
        } catch (IOException e) {
          message = "WSS. zonageekTest() e:" + e + " on " + imagePath;
          AntwebUtil.log(message);
          //continue;
        }
      }
      return message;
    }
    
}


