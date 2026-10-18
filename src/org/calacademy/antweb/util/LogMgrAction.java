package org.calacademy.antweb.util;

import java.io.*;
import java.util.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.upload.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

//http://localhost/antweb/logMgr.do?action=list
// https://www.antweb.org/logMgr.do?action=tomcatLog
//http://localhost/antweb/logMgr.do?fileName=compute&ext=log&action=get&lines=50


public final class LogMgrAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(LogMgrAction.class);

    // Only these files may be read with action=get (loc=web is web/log/, loc=data is the data root's log/).
    private static final Set<String> WEB_LOGS = Set.of(
        "searches.txt", "notFound.txt", "taxonSet.log", "adminTask.log", "invalid.log", "logins.txt",
        "accessLog.txt", "getUrl.txt", "profiler.jsp", "badRequest.log", "taxonNotFound.txt", "isRestart.log",
        "getConns.log", "compute.log", "admin.log", "geolocaleTaxonFix.log", "hacks.log", "taxonworksNightly.log",
        "srfExceptions.txt");
    private static final Set<String> DATA_LOGS = Set.of("cCheck.log", "longRequest.log", "messages.txt", "serverBusy.html");

    public ActionForward execute(
        ActionMapping mapping,
        ActionForm form,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException, ServletException {


        // Log files can contain visitor IPs, logins and internal paths: admin only.
        // The specimen page links curators to one line of an upload file, a specimen's
        // stored details and one Worldants line; those are curator data and open to curators.
        String requestedAction = (String) ((DynaActionForm) form).get("action");
        boolean curatorView = requestedAction != null && Set.of("uploadLog", "specimenDetails", "worldants").contains(requestedAction);
        ActionForward c = curatorView ? Check.curator(request, mapping) : Check.admin(request, mapping);
        if (c != null) return c;

        // Extract attributes we will need
        Locale locale = getLocale(request);
        HttpSession session = request.getSession();

        HttpUtil.setUtf8(request, response);

        String log = null;

        String message = null;

        DynaActionForm df = (DynaActionForm) form;
        String action = (String) df.get("action");                
        String file = (String) df.get("file");
        String fileName = (String) df.get("fileName");
        String ext = (String) df.get("ext");
        String line = (String) df.get("line");
        String grep = (String) df.get("grep");
        String code = (String) df.get("code");
        String lines = (String) df.get("lines");
        String list = (String) df.get("list");
        String loc = (String) df.get("loc"); // Either data or web (which is web/log).
        if ("list".equals(action)) {
            message = getLinkList();
        }

		int lineNum = 0;
		if (line != null) {
            try {
              lineNum = Integer.parseInt(line);
            } catch(NumberFormatException e) {
              //A.log("execute() e:" + e);
            }
        }
        UploadLine uploadLine = null;

        if ("uploadLog".equals(action)) { // DEPRECATED. Used the old upload/ without worldants or specimen.
          message = getUploadLog(file, lineNum);
        } else if ("worldants".equals(action)) {
            message = getWorldantsLine(lineNum);
        } else if ("specimenDetails".equals(action)) {
          message = getSpecimenDetails(request, code);
          //A.log("execute() specimenDetails:" + message);
        } else if ("uploadLine".equals(action)) { // Not sure when/if this and supporting method is used.
		  uploadLine = getUploadLine(request, file, lineNum);
          message = uploadLine == null ? "Line not found." : "<pre>" + Html.esc(uploadLine.getLine()) + "</pre>";
        }

        if (message != null) {
            request.setAttribute("message", message);
            return mapping.findForward("success");
        }


        String tomcatDir = AntwebProps.getTomcatDir();
        switch (action) {
            case "tomcatLog":
                log = tomcatDir + AntwebProps.getProp("site.tomcatLog");
                break;
            case "apacheLog":
                log = tomcatDir + AntwebProps.getProp("site.apacheLog");
                break;
            case "antwebLog":
                log = tomcatDir + AntwebProps.getProp("site.antwebLog");
                break;
            case "antwebInfoLog":
                log = tomcatDir + AntwebProps.getProp("site.antwebInfoLog");
                break;
            case "queryStatsLog":
                log = AntwebProps.getDocRoot() + AntwebProps.getProp("site.queryStatsLog");
                break;
            case "get":
                String requested = fileName + "." + ext;
                if (loc == null || "".equals(loc) || loc.equals("web")) {
                    if (WEB_LOGS.contains(requested)) log = AntwebProps.getDocRoot() + "web/log/" + requested;
                } else if (loc.equals("data")) {
                    if (DATA_LOGS.contains(requested)) log = AntwebProps.getDataRoot() + "log/" + requested;
                }
                if (log == null) {
                    request.setAttribute("message", "Log not available: " + Html.esc(requested));
                    return mapping.findForward("message");
                }
                break;
        }

        if (log == null) {
            message = "Must enter a log name for action:" + Html.esc(action);
            request.setAttribute("message", message);
            return mapping.findForward("message");
        }

        if (grep != null && !"".equals(grep) && !grep.matches("[A-Za-z0-9_.:@=/][A-Za-z0-9_.:@=/-]*")) {
            request.setAttribute("message", "Invalid grep term.");
            return mapping.findForward("message");
        }
        if (lines != null && !"".equals(lines) && !lines.matches("[0-9]{1,6}")) lines = null;

        String command = null;
        if (grep != null && !"".equals(grep)) {
            command = "grep " + grep + " " + log;
            s_log.debug(" command:" + command);
              // Must: docker-compose exec antweb bash
              // to see the logs here: /usr/local/antweb/web/log/
        } else {
            String linesOption = " --lines ";
            if (AntwebProps.isProp("isMac")) linesOption = " -n ";

            if (lines == null || "".equals(lines)) lines = "2000";
            String linesParam = linesOption + " " + lines;
            command = "tail" + linesParam + " " + log;
        }
        message = new AntwebSystem().launchProcess(command, true);
        // Log lines include text from visitors (URLs, user agents): show them as text, never as HTML.
        if (message != null && !message.isEmpty()) message = "<pre>" + Html.esc(message) + "</pre>";
        String logMessage = "";
        if (message != null && message.length() > 100) logMessage = message.substring(100) + "...";

        if (message == null || message.equals("")) message = "File empty or not found: " + Html.esc(log);
        //A.log("command:" + command + " results:" + logMessage);

        request.setAttribute("message", message);
        return mapping.findForward("success");
    }

    private String getSpecimenDetails(HttpServletRequest request, String code) {
        String specimenDetailXml = null;
        Specimen specimen = null;
        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("LogMgrAction.getSpecimenDetails()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);

            specimenDetailXml = new SpecimenDb(connection).getSpecimenDetailXML(code);
            specimen = new SpecimenDb(connection).getSpecimen(code);
            specimen.getDetailHash();

        } catch (SQLException e) {
            s_log.error("getSpecimenDetails() e:" + e);
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }

        if (specimen == null) return "Specimen not found: " + Html.esc(code);
        String specimenDetail = parseXMLIntoHtmlMessage(specimen, specimenDetailXml);
        return specimenDetail;
    }

    private String parseXMLIntoHtmlMessage(Specimen specimen, String theXML) {
        Hashtable detailHash = Specimen.getDetailHash(specimen.getCode(), theXML);

        String message = "<H2>Specimen Description for <a href='" + AntwebProps.getDomainApp() + "/specimen.do?code=" + Html.escAttr(Html.url(specimen.getCode())) + "'>" + Html.esc(specimen.getCode()) + "</a></h2><br>";

        message += "<br><br>" + Html.esc(specimen.getDetailHash()) + "<br><br>";

        message += "<table border=1>";

        Vector v = new Vector(detailHash.keySet());
        Collections.sort(v);
        Iterator keys = v.iterator();

        int i = 0;
        while (keys.hasNext()) {
            String key = (String) keys.next();
            String value = (String) detailHash.get(key);

            try {
                message += "<tr><td>" + Html.esc(key) + "</td><td>" + Html.esc(value) + "</td></tr>";
                ++i;
            } catch (ArrayIndexOutOfBoundsException e) {
                s_log.debug("parseXMLIntoHtmlMessage.getUploadLog() i:" + i + " key:" + key + " e:" + e);
            }
        }
        message += "</table>";

        return message;
    }

    private UploadLine getUploadLine(HttpServletRequest request, String fileName, int lineNum) {
        UploadLine uploadLine = null;
        
		Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("LogMgrAction.getUploadLine()");
		try {
			DataSource dataSource = getDataSource(request, "mediumConPool");
			connection = DBUtil.getConnection(dataSource, dbMethodName);
            
            UploadDb uploadDb = new UploadDb(connection);
            uploadLine = uploadDb.getUploadLine(fileName, lineNum);
            
            return uploadLine;
		} catch (SQLException e) {
			s_log.error("getUploadLine() 2 e:" + e);
		} finally {
			DBUtil.close(connection, this, dbMethodName);
		}

		return null;
    }
          
    private String getUploadLog(String fileName, int lineNum) {
        if (fileName == null || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) return "Invalid file name.";
        String path = AntwebProps.getWebDir() + "upload/";
        return getLogLine(path, fileName, lineNum);
    }
    private String getWorldantsLine(int lineNum){
        // The worldants gets copied here before being archived. This is the easy way.
        String path = AntwebProps.getWebDir() + "speciesList/world/";
        String fileName = "worldants_speciesList.txt";

        lineNum = lineNum - 1; // Because of the header.
        return getLogLine(path, fileName, lineNum);
    }
    private String getLogLine(String path, String fileName, int lineNum) {
	    String message = "Line:<b>" + lineNum + "</b> of file:<b>" + Html.esc(fileName) + "</b><br><br>";
        String filePathName = path + fileName;
		BufferedReader br = null;
		String strLine = "";

		String header = "";
		String theLine = "";

		boolean gotIt = false;                
		try {			    
			br = new BufferedReader( new FileReader(filePathName));
	
			// All are off by 1
			int i = -1;          
			
			while( (strLine = br.readLine()) != null){
				++i;
				if (i == 0) {
				  header = strLine;
				}
				if (i == lineNum) {
				  theLine = strLine;
				  gotIt = true;
				  break;
				}
			} 
			if (i < lineNum) {
			  message += "<br><br>Line:" + lineNum + " not found in " + Html.esc(fileName);     		    
			}
		} catch (FileNotFoundException e) {
			message += "<br><br>Unable to find the file:" + Html.esc(fileName);
		} catch (IOException e) {
			message += "<br><br>Unable to read the file:" + Html.esc(fileName);
		}

		if (gotIt) {
			String[] headerCols = header.split("\t");
			String[] lineCols = theLine.split("\t");

			message += "<table border=1>";

			int i = 0;
			for (String col : headerCols) {
			  try {
			    if ("SpecimenCode".equals(col)) {
			    String codeUrl = "<a href='" + AntwebProps.getDomainApp() + "/specimen.do?code=" + Html.escAttr(Html.url(lineCols[i])) + "'>" + Html.esc(lineCols[i]) + "</a>";
  				  message += "<tr><td>" + Html.esc(col) + "</td><td>" + codeUrl + "</td></tr>";			    
			    } else {
  				  message += "<tr><td>" + Html.esc(col) + "</td><td>" + Html.esc(lineCols[i]) + "</td></tr>";
				}
				++i;
			  } catch (ArrayIndexOutOfBoundsException e) {
				s_log.debug("LogMgrAction.getUploadLog() i:" + i + " col:" + col + " e:" + e);
			  }			      
			}
			message += "</table>";

			message += "<br><br><b>raw text:</b>";
			message += "<br><pre>" + Html.esc(header) + "</pre>";
			message += "<br><pre>" + Html.esc(theLine) + "</pre>";
		}            
		return message;  
    }

    private String getLinkList() {
        // directories: detail   worldants   bak   specimen  unclosedConnections  imageCheck
        String message = "";

        message += "<h3>Primary Logs</h3>";
        message += "<ul><li><a href='" + AntwebProps.getDomainApp() + "/listUploads.do?groupId=0'>Specimen Uploads</a>";
        message += "<li><a href='" + AntwebProps.getDomainApp() + "/query.do?name=worldantsUploads'>Worldants Uploads</a>";
        message += "<li><a href='" + AntwebProps.getDomainApp() + "/web/log/srfExceptions.txt'>SRF Exceptions</a>";
        message += "<li><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=tomcatLog'>Tomcat Log</a>";
        message += "<li><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=apacheLog'>Apache Log</a>";
        message += "<li><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=antwebLog'>Antweb Log</a>";
        message += "</ul><br><br>";



        //message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=srfExceptions20191206&ext=jsp'>srfExceptions20191206.jsp</a>";

/*
         accessLog.txt
         getUrl.txt
         imageNotFound.txt

         adminTask.log
         invalid.log
         logins.txt
         messages.txt
         searches.txt
         taxonSet.log

         profileQuery.log
         profiler.jsp

         taxonSetBackup.log


*/

        message += "<h3>Operation Logs</h3>";
        message += "<a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=searches&ext=txt'>searches.txt</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=notFound&ext=txt'>notFound.txt</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=taxonSet&ext=log'>taxonSet.log</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=adminTask&ext=log'>adminTask.log</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=invalid&ext=log'>invalid.log</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=logins&ext=txt'>logins.txt</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=accessLog&ext=txt'>accessLog.txt</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=getUrl&ext=txt'>getUrl.txt</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=profiler&ext=jsp'>profiler.jsp</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&loc=data&fileName=cCheck&ext=log'>cCheck.log</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&loc=data&fileName=longRequest&ext=log'>longRequest.log</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&loc=data&fileName=messages&ext=txt'>Messages.txt</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=badRequest&ext=log'>badRequest.log</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=taxonNotFound&ext=txt'>taxonNotFound.txt</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=isRestart&ext=log'>isRestart.log</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=getConns&ext=log'>getConns.log</a>";

        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=compute&ext=log'>compute.log</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=admin&ext=log'>admin.log</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=geolocaleTaxonFix&ext=log'>geolocaleTaxonFix.log</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=hacks&ext=log'>hacks.log</a>";


        /*
        message += "<br>Deprecated...?";

        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=taxonSetBackup&ext=log'>taxonSetBackup.log</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=insecure&ext=log'>insecure.log</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=speciesListLog&ext=txt'>speciesListLog.txt</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=OTNotFound&ext=txt'>OTNotFound.txt</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=throttle&ext=txt'>throttle.txt</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=deletedImageLog&ext=txt'>deletedImageLog.txt</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=pageTracker&ext=log'>pageTracker.log</a>";

        message += "<h3>Domain Data Logs</h3>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=exifData&ext=txt'>exifData.txt</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=dateDetermined&ext=log'>dateDetermined.log</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=defaultSpecimen&ext=log'>defaultSpecimen.log</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=typeStatusSpeciesNotFound&ext=txt'>typeStatusSpeciesNotFound.txt</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=typeStatusSpeciesFound&ext=txt'>typeStatusSpeciesFound.txt</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=typeStatusHomonym&ext=txt'>typeStatusHomonym.txt</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=typeStatusNoTaxonName&ext=txt'>typeStatusNoTaxonName.txt</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=speciesListTool&ext=txt'>speciesListTool.txt</a>";

        message += "<h3>Dev Logs</h3>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=adminAlerts&ext=log'>adminAlerts.log</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=appCheck&ext=log'>appCheck.log</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=appCheckOutput&ext=log'>appCheckOutput.log</a>";
        message += "<br><a href='" + AntwebProps.getDomainApp() + "/logMgr.do?action=get&fileName=zonageeks&ext=txt'>zonageeks.txt</a>";
*/
        return message;
    }


}
