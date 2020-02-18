package org.calacademy.antweb.util;

import java.io.*;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import org.apache.struts.action.*;
import java.sql.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.AntwebUtil;
import org.calacademy.antweb.upload.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class ShowLogAction extends Action {

    private static Log s_log = LogFactory.getLog(ShowLogAction.class);

    public ActionForward execute(
        ActionMapping mapping,
        ActionForm form,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException, ServletException {

        // Extract attributes we will need
        Locale locale = getLocale(request);
        HttpSession session = request.getSession();

        HttpUtil.setUtf8(request, response); 

        DynaActionForm df = (DynaActionForm) form;
        String action = (String) df.get("action");                
        String fileName = (String) df.get("file");
        String line = (String) df.get("line");
        String grep = (String) df.get("grep");
        String code = (String) df.get("code");

		int lineNum = 0;
		if (line != null) {
            try {
              lineNum = (new Integer(line)).intValue();
            } catch(NumberFormatException e) {
              //A.log("execute() e:" + e);
            }
        }
        UploadLine uploadLine = null;
        String message = null;
        if ("uploadLog".equals(action)) { // DEPRECATED. Used the old upload/ without worldants or specimen.
          message = getUploadLog(fileName, lineNum); 
        } else if ("specimenDetails".equals(action)) {
          message = getSpecimenDetails(request, code);
          A.log("execute() specimenDetails:" + message);
        } else if ("uploadLine".equals(action)) {
		  uploadLine = getUploadLine(request, fileName, lineNum); 
          message = uploadLine.getLine();
        }

        if (message != null) {
            request.setAttribute("message", message);
            return (mapping.findForward("success"));
        }

        String log = null;
        String tomcatDir = AntwebProps.getProp("site.tomcat");
        if (action.equals("tomcatLog")) {
            log = tomcatDir + AntwebProps.getProp("site.tomcatLog");   
        } else if (action.equals("apacheLog")) {
            log = tomcatDir + AntwebProps.getProp("site.apacheLog");   
        } else if (action.equals("antwebLog")) {
            log = tomcatDir + AntwebProps.getProp("site.antwebLog");   
        } else if (action.equals("antwebInfoLog")) {
            log = tomcatDir + AntwebProps.getProp("site.antwebInfoLog");   
        } else if (action.equals("queryStatsLog")) {
            log = AntwebProps.getProp("site.docroot") + AntwebProps.getProp("site.queryStatsLog");   
        }

        if (log == null) {
            message = "Must enter a log name for action:" + action;
            request.setAttribute("message", message);
            return (mapping.findForward("message"));
        }

        String command = null;
        if (grep != null && !"".equals(grep)) {
            command = "grep " + grep + " " + log;
            A.log(" command:" + command);
        } else {  
            String linesOption = " --lines ";
            if (AntwebProps.isProp("isMac")) linesOption = " -n ";   
            String lines = linesOption + " 2000";  
            command = "tail " + lines + " " + log;
        }
        message = (new AntwebSystem()).launchProcess(command, true);
        String logMessage = "";
        if (message != null && message.length() > 100) logMessage = message.substring(100) + "...";
        //A.log("command:" + command + " results:" + logMessage);

        request.setAttribute("message", message);
        return (mapping.findForward("success"));
    }


    private String getSpecimenDetails(HttpServletRequest request, String code) {
        String specimenDetailXml = null;
        Specimen specimen = null;
        java.sql.Connection connection = null;
        try {
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "getSpecimenDetails");

            specimenDetailXml = (new SpecimenDb(connection)).getSpecimenDetailXML(code);
            specimen = (new SpecimenDb(connection)).getSpecimen(code);
            specimen.getDetailHash();

        } catch (SQLException e) {
            s_log.error("getSpecimenDetails() e:" + e);
        } finally {
            DBUtil.close(connection, this, "getSpecimenDetails");
        }

        String specimenDetail = parseXMLIntoHtmlMessage(specimen, specimenDetailXml);
        return specimenDetail;
    }

    private String parseXMLIntoHtmlMessage(Specimen specimen, String theXML) {
        Hashtable detailHash = Specimen.getDetailHash(specimen.getCode(), theXML);

        String message = "<H2>Specimen Description for <a href='" + AntwebProps.getDomainApp() + "/specimen.do?code=" + specimen.getCode() + "'>" + specimen.getCode() + "</a></h2><br>";

        message += "<br><br>The XML: " + specimen.getDetailHash() + "</br></br>";

        message += "<table border=1>";

        Vector v = new Vector(detailHash.keySet());
        Collections.sort(v);
        Iterator keys = v.iterator();

        int i = 0;
        while (keys.hasNext()) {
            String key = (String) keys.next();
            String value = (String) detailHash.get(key);

            try {
                message += "<tr><td>" + key + "</td><td>" + value + "</td></tr>";
                ++i;
            } catch (java.lang.ArrayIndexOutOfBoundsException e) {
                A.log("parseXMLIntoHtmlMessage.getUploadLog() i:" + i + " key:" + key + " e:" + e);
            }
        }
        message += "</table>";

        return message;
    }

    private UploadLine getUploadLine(HttpServletRequest request, String fileName, int lineNum) {
        UploadLine uploadLine = null;
        
		java.sql.Connection connection = null;
		try {
			javax.sql.DataSource dataSource = getDataSource(request, "mediumConPool");
			connection = DBUtil.getConnection(dataSource, "getUploadLine");
            
            UploadDb uploadDb = new UploadDb(connection);
            uploadLine = uploadDb.getUploadLine(fileName, lineNum);
            
            return uploadLine;
		} catch (SQLException e) {
			s_log.error("getUploadLine() 2 e:" + e);
		} finally {
			DBUtil.close(connection, this, "getUploadLine");
		}

		return null;
    }
          
    private String getUploadLog(String fileName, int lineNum) {
	    String filePathName = AntwebProps.getWebDir() + "upload/" + fileName;
	    String message = "Line:<b>" + lineNum + "</b> of file:<b>" + fileName + "</b><br><br>";

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
			  message += "<br><br>Line:" + lineNum + " not found in " + filePathName;     		    
			}
		} catch (FileNotFoundException e) {
			message += "<br><br>Unable to find the file:" + filePathName;
		} catch (IOException e) {
			message += "<br><br>Unable to read the file:" + filePathName;
		}

		if (gotIt) {
			String[] headerCols = header.split("\t");
			String[] lineCols = theLine.split("\t");

			message += "<table border=1>";

			int i = 0;
			for (String col : headerCols) {
			  try {
			    if ("SpecimenCode".equals(col)) {
			    String codeUrl = "<a href='" + AntwebProps.getDomainApp() + "/specimen.do?code=" + lineCols[i] + "'>" + lineCols[i] + "</a>";
  				  message += "<tr><td>" + col + "</td><td>" + codeUrl + "</td></tr>";			    
			    } else {
  				  message += "<tr><td>" + col + "</td><td>" + lineCols[i] + "</td></tr>";
				}
				++i;
			  } catch (java.lang.ArrayIndexOutOfBoundsException e) {
				A.log("ShowLogAction.getUploadLog() i:" + i + " col:" + col + " e:" + e);
			  }			      
			}
			message += "</table>";

			message += "<br><br><b>raw text:</b>";
			message += "<br><pre>" + header + "</pre>";
			message += "<br><pre>" + theLine + "</pre>";
		}            
		return message;  
    }      
}
