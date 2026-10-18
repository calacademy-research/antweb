package org.calacademy.antweb.util;

import java.io.*;
import java.util.*;

import java.sql.*;

import org.calacademy.antweb.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


public abstract class AntwebFunctions {

  private static final Log s_log = LogFactory.getLog(AntwebFunctions.class);
    
    public static void genRecentDescEdits(Connection connection)
      throws SQLException, IOException {

      // This method generates the file here: http://localhost/antweb/recentDescEdits.jsp
      // This will be included by the homepage template.

        int MAX_DESC_EDIT_COUNT = 5;

        String docBase = AntwebProps.getDocRoot();

        String genIncDir = docBase + "/web/genInc/";

        FileUtil.makeDir(genIncDir);

        // .html, not .jsp: a .jsp under the web root would be compiled and run by Tomcat.
        File outputFile = new File(genIncDir + "recentDescEdits.html");
        FileWriter outFile = new FileWriter(outputFile);

        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = connection.createStatement();
          String query = "select taxon_name, code from description_edit" 
            + " where taxon_name in (select taxon_name from taxon)"
            + " and is_manual_entry = 1"
            + " order by created desc limit 100";

          //s_log.warn("getRecentDescEdits() query:" + query);
          rset = stmt.executeQuery(query);

          int count = 0; 
          Vector<String> taxonList = new Vector<>();  // for distinctness.
          String href;
          while (rset.next()) {
            String taxonName = rset.getString("taxon_name");
            String code = rset.getString("code");
            if (taxonList.contains(taxonName) && code == null) {
              // do nothing.  Effectively a distinct.
            } else {   
              if (code == null) {
                taxonList.add(taxonName);
                href = Taxon.getTaxonUrl(connection, taxonName);
              } else {
                href = Specimen.makeLink(code);
              }
              //A.log("getRecentDescEdits() taxonName:" + taxonName + " code:" + code + " href:" + href);
              outFile.write(href + "<br/>\n");
              count++;
              if (count > MAX_DESC_EDIT_COUNT) {
                break;
              }
            }
          }
          outFile.write("<span class=\"right\"><a href=\"" + AntwebProps.getDomainApp() + "/recentDescEdits.do?action=recentDescEdits\">More &#187</a></span>");
          outFile.close();

        } finally {  
          DBUtil.close(stmt, rset, "AntwebFunctions", "genRecentDescEdits");
        }
    }

    


/*
    For this to be run, it needs to be run as root. There must be in the /etc/sudoers file the following:
antweb	ALL=(ALL)	NOPASSWD: /antweb/deploy/bin/admin.sh   
    You will need to "C-x C-q" in order to edit /etc/sudoers even as root.

    Either by Scheduler, or by calling: /schedule.do?action=run&num=14
    A request is fired off to /utilData.do?action=adminTasks&param=allow
    This calls AntwebFunctions.adminTasks()
    Which calls AntwebSystem.exec() with the command below
    
    Now handled by cron
    public static String adminTasks() {
      String command = "sudo " + antwebDir + "/bin/admin.sh";
      //s_log.warn("adminTasks() command:" + command);
      AntwebSystem system = new AntwebSystem();
      //system.launchProcess(command, true);
      //system.exec(command);

      try {
        system.exec("sudo " + antwebDir + "/bin/admin.sh");      
      } catch (Exception e) {
        A.log("adminTasks() e:" + e);
      }

      String message = command;

      // Verified that files are written as antweb:antweb.		
      AntwebUtil.writeDataFile("t.txt", "test");				
      
      return message;
    }

    public static String updateExif() {
      String imgDir = AntwebProps.getDocRoot() + "images/";
      String command = "exiftool -Copyright='California Academy of Sciences 2000-2019' /antweb/images/casent0005904/CASENT0005904_D.tif";  
      A.log("updateExif() command:" + command);
//      (new AntwebSystem()).launchProcess(command); //, true);
      (new AntwebSystem()).launchBuilder(command); //, true);
      String message = command;
      return message;
    }
*/

    

    
}

