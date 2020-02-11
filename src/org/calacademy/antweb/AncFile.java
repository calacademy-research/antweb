package org.calacademy.antweb;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Date;
import java.util.Calendar;
import java.util.ResourceBundle;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

public class AncFile {

/*
    This doc is rough as it is created by Mark.  Anc means ancillary.  There is an ancillary table.

    An ancillary file is a page on antweb that a curator can create to add more information, so that
    they don't have to run their own website and publish related data there.  The page is created
    through the curators page, using the Yahoo GUI text widget.  The ancillary page is stored in the
    database and a jsp file is written to the antweb directory such as /data/antweb/arizona/ with the
    appropriate specified filename.

    This is from production database:
    
mysql> select id, title, fileName, directory, last_changed, project_name from ancillary;
+----+-------------------------------------------+-----------------+-------------+---------------------+-----------------+
| id | title                                     | fileName        | directory   | last_changed        | project_name    |
+----+-------------------------------------------+-----------------+-------------+---------------------+-----------------+
|  4 | test                                      | test            | netherlands | 2009-05-05 00:00:00 | netherlandsants | 
|  5 | Nonestablished ants of the Netherlands    | introduced      | netherlands | 2009-05-05 00:00:00 | netherlandsants | 
|  6 | Key to Odontomachus species of Madagascar | KeyOdontomachus | madagascar  | 2010-03-26 00:00:00 | madants         | 
|  7 | AntWeb Staff                              | staff           | homepage    | 2010-04-02 00:00:00 | homepage        | 
|  8 | AntWeb Staff                              | staff           | homepage    | 2010-04-02 00:00:00 | homepage        | 
|  9 | AntWeb Staff                              | staff           | homepage    | 2010-07-14 00:00:00 | homepage        | 
| 10 | AntWeb Documentation                      | documentation   | homepage    | 2010-06-11 00:00:00 | homepage        | 
+----+-------------------------------------------+-----------------+-------------+---------------------+-----------------+
*/

    private static Log s_log = LogFactory.getLog(AncFile.class);

    protected String fileName;
    protected String directory;
    protected String title;
    protected String contents;
    protected Connection connection;
    protected String previewPage;
    protected int id;
    protected Date lastChanged;
    protected String project;
    protected int accessLoginId;
    
    public AncFile() {
        super();
        //id = -1;
    }
    
    public int saveToDb(Connection connection) {
        if (getId() <= 0) {
        
            String safeTitle =  AntFormatter.escapeQuotes(title);
            String safeContents =  AntFormatter.escapeQuotes(contents);       
 
            String theInsert = "";
            theInsert = "insert into ancillary (title, contents, fileName, directory, last_changed, project_name, access_login) values (";
            if ((project != null) && (directory != null)) {
              s_log.warn("saveToDb() proejct:" + project + " AncFile to dir:" + directory);
              theInsert += "'" + safeTitle + "','" + safeContents + "','" + fileName + "','" + directory + "','" + getCurrentSQLDate().toString() + "', '" + project + "', " + accessLoginId + ")";
            } else {
              s_log.warn("saveToDb() curator AncFile");
              theInsert += "'" + safeTitle + "','" + safeContents + "','" + fileName + "', null,'" + getCurrentSQLDate().toString() + "', null, " + accessLoginId + ")";            
            }
            //s_log.info("saving ancfile stmt:" + theInsert);
            try {
                Statement stmt = connection.createStatement();
                stmt.executeUpdate(theInsert, Statement.RETURN_GENERATED_KEYS);
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    setId(rs.getInt(1));
                }
                stmt.close();
                rs.close();
            } catch (SQLException e) {
                s_log.error("error in saving ancillary page to db: " + theInsert);
                org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
            } 
        }
        return getId();
    }
    
    public void updateInDb() {
        if (getId() > 0) {
            String theQuery = "update ancillary set ";
            theQuery += addSet("title", getTitle()) + ",";
            theQuery += addSet("last_changed", getCurrentSQLDate().toString()) + ",";
            theQuery += addSet("contents", getContents()) + ",";
            if (getDirectory() == null) {
              theQuery += " directory = NULL,";
            } else {
              theQuery += addSet("directory", getDirectory()) + ",";           
            }
            theQuery += addSet("fileName", getFileName()) + ",";
            if (getProject() == null) {
              theQuery += " project_name = NULL,";            
            } else {
              theQuery += addSet("project_name", getProject()) + ",";          
            }
            theQuery += " access_login=" + getAccessLoginId();
            theQuery += " where id=" + getId();
            try {
                Statement stmt = connection.createStatement();
                stmt.executeUpdate(theQuery);
                stmt.close();
                //s_log.info("saving ancillary page " + theQuery);
            } catch (SQLException e) {
                s_log.error("error in saving ancillary page to db: " + theQuery);
                org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
            }
        }
    }
    
    public void getFromDb(Connection connection, int id) {
        setConnection(connection);
        setId(id);
        getFromDb();    
        setConnection(null);
    }
    
    public void getFromDb(int id) {
        if (connection != null) {
            setId(id);
            getFromDb();
        }
    }
    
    public void getFromDb() {
        
        String theQuery = "select * from ancillary where id=" + id;
        try {
            Formatter format = new Formatter();
            Statement stmt = connection.createStatement();
            ResultSet rset = stmt.executeQuery(theQuery);
            rset.next();
            setTitle(format.MSSQLunescapeCharacters(rset.getString("title")));
            setLastChanged(rset.getDate("last_changed"));
            setContents(format.MSSQLunescapeCharacters(rset.getString("contents")));
            setFileName(rset.getString("fileName"));
            setDirectory(rset.getString("directory"));
            setProject(rset.getString("project_name"));
            setAccessLoginId(rset.getInt("access_login"));
            stmt.close();
        } catch (SQLException e) {
            s_log.error("error in getting ancillary file from db: " + theQuery);
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
        }        
    }
    
    public String addSet(String field, String value) {
        if (field == null) {
            s_log.error("field is null for value:" + value);
            return "";
        } 
        
        if ((value == null) || (value.equals("null"))) { 
            value = ""; 
        }
        value = AntFormatter.escapeQuotes(value);
        return field + "='" + value + "'";
    }
    
    public void generatePage() {
        
        // get all the information if not already grabbed
        if ((fileName == null) || (fileName.equals(""))) {
            getFromDb();
        }
        
        s_log.info("generatePage():" + toString());
        // open the new file
        try {
            String outFile = getDirLoc() + fileName + "-preview.jsp";
            BufferedWriter out = new BufferedWriter(new FileWriter(outFile));
            
            // grab the template file
            String templateFile = AntwebProps.getDocRoot() + "/anc_template.jsp";
              // AntwebProps.getProp("site.ancpagetemplate");

            A.log("generatePage() reading templateFile:" + templateFile);
            BufferedReader in = new BufferedReader(new FileReader(templateFile));
            String str, prestring, slot;
            Object variable;
            int start;
            int end;

            while ((str = in.readLine()) != null) {
                //do the substitutions one at a time
                if ((str.indexOf("[%") != -1) && (str.indexOf("%]") != -1)) {
                    while (str.length() > 0) {
                        start = str.indexOf("[%");
                        end = str.indexOf("%]");
                        if (start == -1) {
                            out.write(str + "\r\n");
                            str = "";
                        } else {
                            prestring = str.substring(0,start);
                            out.write(prestring);
                            slot = str.substring(start+3, end-1);
                            variable = getSlotValue(slot);
                            if (variable != null) {
                                out.write(variable.toString());
                            }
                            str = str.substring(end+2);
                        }
                    }
                } else {
                    out.write(str + "\r\n");
                }
            }
            out.close();
            in.close();
        } catch (IOException e) {
            s_log.error("problem writing out ancillary page: " + e);
            s_log.error("docLoc:" + getDirLoc() + " fileName:" + fileName + "-preview.jsp");
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
        }
    }
    
    public void generatePage(Connection connection, Integer id) {
        setConnection(connection);
        setId(id);
        generatePage();
        setConnection(null);
    }
    
    public void save(Connection connection) {
        setConnection(connection);
        updateInDb();
        try {
            (new Utility()).moveFile(getDirLoc() + fileName + "-preview.jsp", getDirLoc() + fileName  + ".jsp");
        } catch (IOException e) {
            s_log.error("generate and save home page found error " + e);
        }
        setConnection(null);
    }    
    
    private Object getSlotValue(String slot) {
        if (slot != null) {
            //s_log.info("slot is:" + slot + ".");
        } else {
            //s_log.info("slot is null");
        }
        Object variable = null;
        try {
       //     s_log.info("about to get the field");
            Field thisField = AncFile.class.getDeclaredField(slot);
       //     s_log.info("getting the variable");
            variable = thisField.get(this);
        } catch (SecurityException e) {
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
        } catch (IllegalArgumentException e) {
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
        } catch (NoSuchFieldException e) {
            s_log.error("prolem in getSlotValue for slot *" + slot + "*");
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
        } catch (IllegalAccessException e) {
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
        }
        return variable;
    }
    
    public Date getCurrentSQLDate() {
        Calendar cal = Calendar.getInstance();
        return new Date(cal.getTime().getTime());
    }

    /* Notice also getDirectory(). This is where the page will be saved in case of a curator anc file.  
       That is used in the case of a project specific ancFile.  */
    public String getDirLoc() {
        String docBase = (new Utility()).getDocRoot();
        String outDir = null;
        if (getDirectory() == null) {      
          outDir = docBase + "web/curator/" + getAccessLoginId() + "/";
        } else if (getDirectory().contains("homepage")) {
          outDir = docBase + "web/" + getDirectory() + "/"; 
        } else if (getDirectory().contains("team")) {
          outDir = docBase + "web/team/"; 
        } else {
          outDir = docBase + Project.getSpeciesListDir() + getDirectory() + "/"; 
        }
        return outDir;
    }

    public String getUrlLoc() {
        String outDir = null;
        if (getDirectory() == null) {      
          outDir = AntwebProps.getDomainApp() + "/web/curator/" + getAccessLoginId() + "/";
        } else if (getDirectory().contains("homepage")) {
          outDir = AntwebProps.getDomainApp() + "/web/" + getDirectory() + "/"; 
        } else if (getDirectory().contains("team")) {
          outDir = AntwebProps.getDomainApp() + "/web/" + getDirectory() + "/";
        } else {
          outDir = AntwebProps.getDomainApp() + "/" + Project.getSpeciesListDir() + getDirectory() + "/"; 
        }
        return outDir;
    }

    public String getPreviewPageURL() {
        Utility util = new Utility();
        String previewPageURL = getUrlLoc() + getFileName() + "-preview.jsp";
        s_log.warn("previewPageURL:" + previewPageURL);
        return previewPageURL;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContents() {
        return contents;
    }

    public void setContents(String contents) {
        this.contents = contents;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public void setPreviewPage(String previewPage) {
        this.previewPage = previewPage;
    }

    public Date getLastChanged() {
        return lastChanged;
    }

    public void setLastChanged(Date lastChanged) {
        this.lastChanged = lastChanged;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }
    
    public int getAccessLoginId() {
      return this.accessLoginId;
    }
    
    public void setAccessLoginId(int loginId) {
      this.accessLoginId = loginId;
    }
    
    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
    
    public String toString() {
      return "AncFile - id:" + getId() + " title:" + getTitle() + " dir:" + getDirectory() +
        " fileName:" + getFileName() + " project:" + getProject() + " accessLoginId:" + getAccessLoginId();
    }
}
