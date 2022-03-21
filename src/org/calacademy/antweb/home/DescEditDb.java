package org.calacademy.antweb.home;

import java.util.*;
import java.util.Collection;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


import org.calacademy.antweb.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;

public class DescEditDb extends AntwebDb{

    private static final Log s_log = LogFactory.getLog(DescEditDb.class);
    //private Connection connection = null;
    
    public DescEditDb(Connection connection) {
      super(connection);
    }

    // To be overridden by HomonymDescEditDb.java
    public String getTableName() {
      return "description_edit";
    }
            
// setDescription() was setDescription(false);
// What was Taxon.setDescription(isManualEntry) is now 
    public Hashtable<String, String> getDescEdits(Taxon taxon, boolean isManualEntry) {
		// We have removed project from description_edit table.  This method should work fine with this 
		// property removed.  There will still be a collection of description records per taxon (title).
		// We have aimed this method against description_edit instead of description.
		// To do: Remove taxon_name from query and replace with id.  Include into this class.

        Hashtable<String, String> description = new Hashtable<>();

        String taxonName = null;
        Formatter formatter = new Formatter();
        String theQuery = "";
        Statement stmt = null;
        ResultSet rset = null;
        try {
            taxonName = AntFormatter.escapeQuotes(taxon.getTaxonName());

            theQuery = "select * from " + getTableName() + " where taxon_name= '" + taxonName + "'";
            
            if (isManualEntry) {
              theQuery += " and is_manual_entry = 1";
            }
            if ("description_edit".equals(getTableName())) theQuery += " and code is null";

            stmt = DBUtil.getStatement(getConnection(), "DescEditDb.getDescEdits()");
            rset = stmt.executeQuery(theQuery);

            String key = null;
            String value = null;
            int recordCount = 0;
            while (rset.next()) {
                recordCount++;
                key = rset.getString("title");
                //s_log.warn("setDescription() key:" + key + " 1:" + rset.getString("1"));
                value = rset.getString("content");

                if ("images".equals(key))
                    value = httpSecurify(value);

                /*
Before:
 <br><a href="http://www.antweb.org/web/curator/67/Adetomyrma17j-L.jpg"><img class="taxon_page_img" src="http://www.antweb.org/web/curator/67/Adetomyrma17j-L.jpg"></a>
<br>
A pair of <i>Adetomyrma goblin</i> workers grooms a larva. Madagascar; captive lab colony photographed at the California Academy of Sciences.
<br>
Image © <a href="http://www.alexanderwild.com/" target="new">Alex Wild</a>.  |

After:
<br><a href="https://www.antweb.org/web/curator/67/Adetomyrma17j-L.jpg"><img class="taxon_page_img" src="https://www.antweb.org/web/curator/67/Adetomyrma17j-L.jpg"></a>
<br>
A pair of <i>Adetomyrma goblin</i> workers grooms a larva. Madagascar; captive lab colony photographed at the California Academy of Sciences.
<br>
Image © <a href="https://www.alexanderwild.com/" target="new">Alex Wild</a>.
                 */


                //key = AntFormatter.unescapeCharacters(key);
                //value = AntFormatter.unescapeCharacters(value);
                value = Formatter.dequote(value);
                description.put(key, value);
 
                if (false)
                  if (AntwebProps.isDevOrStageMode())
                    if ("pseudomyrmecinaetetraponera rufonigra".equals(taxonName))
                      if ("taxonomictreatment".equals(key))
                        s_log.warn("getDescEdits() key:" + key + " value:" + value);
            }

            s_log.debug("setDescription() recordCount:" + recordCount + " query:" + theQuery);
            
        } catch (SQLException e) {
            s_log.error("getDescEdits() for taxonName:" + taxonName + " exception:" + e + " theQuery:" + theQuery);
            // project:" + project + " 
        } finally {
            DBUtil.close(stmt, rset, this, "getDescEdits()");
        }
        return description;
    }

    private static String httpSecurify(String value) {
      if (value == null) return null;
      if (value.contains("http://")) {
          int initSize = value.length();
          int fixes = 0;
          int notFixes = 0;
          String httpsString = "https://";
          while (value.contains("http://")) {
              int indexOfHttp = value.indexOf("http://");
              String delimiter = value.substring(indexOfHttp - 1, indexOfHttp);
              String beginString = value.substring(0, indexOfHttp);

              String newEndString = httpsString + value.substring(indexOfHttp + 7);  // 7 is the length of http://
              String newUrl = newEndString.substring(0, newEndString.indexOf(delimiter));

              // For debugging
              if (AntwebProps.isDevMode()) {
                  boolean newExists = HttpUtil.urlExists(newUrl);
                  if (!newExists) {
                      ++notFixes;
                      String oldEndString = value.substring(indexOfHttp);  // 7 is the length of http://
                      String oldUrl = oldEndString.substring(0, oldEndString.indexOf(delimiter));
                      boolean oldExists = HttpUtil.urlExists(oldUrl);

                      s_log.debug("httpSecurify() newExists:" + newExists + " oldExists:" + oldExists + " oldUrl:" + oldUrl);
                  } else {
                      ++fixes;
                      s_log.debug("httpSecurify() newExists:" + newExists);
                  }
              }

              value = beginString + newEndString;
          }
          s_log.debug("httpSecurify() contains http. fixes:" + fixes + " notFixes:" + notFixes + " initSize:" + initSize + " postSize:" + value.length());
      }
      return value;
    }

    public ArrayList<DescEdit> getRecentDescEdits() throws SQLException {
        // Used in the Description Edit Report, linked off the home page.
        
        ArrayList<DescEdit> descEdits = new ArrayList<>();
        
        Statement stmt = null;
        ResultSet rset = null;
        try {
            String theQuery = "select taxon_name, title, code, content, edit_id, created, taxon_id, is_manual_entry, access_group, access_login "
               + " from " + getTableName()
               + " where is_manual_entry = 1 "
               + " and title != 'taxanomicnotes' "
               + " and access_login is not null "  // ?
               + " order by created desc ";

//    select taxon_name, title, code content, edit_id, created, taxon_id, is_manual_entry, access_group, access_login from description_edit where is_manual_entry = 1 order by created desc

            stmt = DBUtil.getStatement(getConnection(), "DescEditDb.getRecentDescEdits()");
            rset = stmt.executeQuery(theQuery);

            int i = 0;
            while (rset.next()) {
                ++i;
                DescEdit descEdit = new DescEdit();
              
               String taxonName = rset.getString("taxon_name");
               descEdit.setTaxonName(taxonName);

               String theCode = rset.getString("code");
               if (!"NULL".equals(theCode)) descEdit.setCode(theCode);

               //if (i < 10) s_log.warn("getRecentDescEdits() taxonName:" + taxonName + " theCode:" + theCode);
                
                descEdit.setTitle(rset.getString("title"));
                // descEdit.setContent(rset.getString("content"));
                descEdit.setEditId(rset.getInt("edit_id"));
                descEdit.setCreated(rset.getDate("created"));
                // descEdit.setTaxonId(rset.getInt("taxon_id"));
                descEdit.setIsManualEntry(rset.getBoolean("is_manual_entry"));
                descEdit.setAccessGroupId(rset.getInt("access_group"));
                descEdit.setAccessLoginId(rset.getInt("access_login"));

                Login accessLogin = (new LoginDb(getConnection())).getLogin(descEdit.getAccessLoginId());  // quickload
                descEdit.setAccessLogin(accessLogin);
                
                if (descEdit.getCode() == null) {
                  Taxon taxon = (new TaxonDb(getConnection())).getTaxon(descEdit.getTaxonName());
                  if (taxon != null) {
                    //s_log.warn("getRecentDescEdits() taxonName:" + descEdit.getTaxonName() + " prettyName:" + infoInstance.getPrettyName() 
                    //  + " accessLoginId:" + descEdit.getAccessLoginId() + " title:" + descEdit.getTitle());                
                    descEdit.setTaxonPrettyName(taxon.getPrettyName());
                    descEdits.add(descEdit);
                  } else {
                    // Expected.  We should not display these.
                    //s_log.warn("getRecentDescEdits() no taxon found:" + descEdit.getTaxonName());
                  } 
                } else {
                  descEdits.add(descEdit);
                }
            }
        } catch (SQLException e) {
            s_log.error("getRecentDescEdits() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "DescEditDb", "getRecentDescEdits()");
        }
        return descEdits;
    }
    
    // Used for Project, Museum, etc...
    public Hashtable<String, String> getDescription(String objectName) {

        Formatter formatter = new Formatter();
        Hashtable<String, String> description = new Hashtable<>();
        String theQuery = "";
        Statement stmt = null;
        ResultSet rset = null;

        try {
            objectName = Formatter.escapeQuotes(objectName);
            theQuery = "select * from object_edit where object_key ='" + objectName + "'";

            stmt = DBUtil.getStatement(getConnection(), "DescEditDb.getDescription()");            
    
            rset = stmt.executeQuery(theQuery);

            String key = null;
            String value = null;
            int recordCount = 0;
            while (rset.next()) {
                recordCount++;
                key = rset.getString("title");
                value = rset.getString("content");
                value = Formatter.dequote(value);
                description.put(key, value);
            }
            //if (AntwebProps.isDevMode()) s_log.info("setDescription() recordCount:" + recordCount + " query:" + theQuery);
            
        } catch (SQLException e) {
            s_log.error("getDescription() for object:" + objectName + " exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, this, "DescEditDb.getDescription()");
        }

        return description;
    }    
    
    
    public String populateObjectEdit() {
      // Object Edits are like Description Edits but for any object. 
      // Either similar to, or fully replacing the description edit feature.
      // 
      // The Object_edit database table.

      emptyObjectEdit();
      
      HashMap<String, Project> projectsHashMap = (new ProjectDb(getConnection())).getAllProjects();
      Collection<Project> values = projectsHashMap.values();
      int i = 0;
      for (Project project : values) {
        ++i;

        //if (!"comorosants".equals(project.getName())) continue;

      // project_name, root,
      // specimenImage1, specimenImage2, specimenImage3, authorImage, authorbio, 
      // specimenImage1Link, specimenImage2Link specimenImage3Link, author

        insertObjectEdit(project, "contents", project.getContents());
        insertObjectEdit(project, "specimenImage1", project.getSpecimenImage1(), project.getSpecimenImage1Link());        
        insertObjectEdit(project, "specimenImage2", project.getSpecimenImage2(), project.getSpecimenImage2Link());        
        insertObjectEdit(project, "specimenImage3", project.getSpecimenImage3(), project.getSpecimenImage3Link());
        insertObjectEdit(project, "authorImage", project.getAuthorImage());
        insertObjectEdit(project, "author", project.getAuthor());
        insertObjectEdit(project, "authorBio", project.getAuthorBio());

        // map?      
      }
      return "Populated object_edit table with " + i + " projects, " + insertions + " insertions, " + exceptions + " exceptions.";
    }

    private void insertObjectEdit(Project project, String title, String imageName, String hrefLink) {
        String projectName = project.getName();
        if (imageName == null || "".equals(imageName)) return;

        String content = Formatter.escapeQuotes(imageName);
        
        if ("specimenImage1".equals(title) || "specimenImage2".equals(title) || "specimenImage3".equals(title)) {
          // <img src="http://www.antweb.org/web/speciesList/comoros/CASENT0101243_H.jpg">
          content = "<a href=\"" + hrefLink + "\"><img src=\"" + AntwebProps.getImgDomainApp() + "/" + Project.getSpeciesListDir() + project.getRoot() + "/" + imageName + "\"></a>";
// <a href="http://www.antweb.org/description.do?rank=species&genus=cataulacus&name=voeltzkowi&project="><img src="http://www.antweb.org/web/speciesList/comoros/CASENT0101243_H.jpg"></a>
        }

        String insert = "insert into object_edit (" 
          + "object_key, title, content, edit_id, is_manual_entry, access_group, access_login" 
          + ") values (" 
          + "'" + projectName + "', '" + title + "', '" + content + "', 0, 0, 0, 0)";

        insert(insert);

        // s_log.warn("populateObjectEdit() project:" + projectName + " title:" + title + " value:" + value);      
    }

    private void insertObjectEdit(Project project, String title, String value) {
        String projectName = project.getName();
        if (value == null || "".equals(value)) return;

        String content = Formatter.escapeQuotes(value);

        if ("authorImage".equals(title)) {
          content = "<img src=\"" + AntwebProps.getImgDomainApp() + "/" + Project.getSpeciesListDir() + project.getRoot() + "/" + value + "\">";
        }

        String insert = "insert into object_edit (" 
          + "object_key, title, content, edit_id, is_manual_entry, access_group, access_login" 
          + ") values (" 
          + "'" + projectName + "', '" + title + "', '" + content + "', 0, 0, 0, 0)";

        insert(insert);

        // s_log.warn("populateObjectEdit() project:" + projectName + " title:" + title + " value:" + value);      
    }


    private static int exceptions = 0;
    private static int insertions = 0;

    
    private void insert(String insert) {
      Statement stmt = null;
               
      try {
        stmt = DBUtil.getStatement(getConnection(), "DescEditDb.insert()");
        stmt.executeUpdate(insert);   
        ++insertions;     
      } catch (SQLException e) {
        ++exceptions;
        s_log.error("insert() e:" + e + " insert:" + insert);
      } finally {
        DBUtil.close(stmt, "DescEditDb.insert()");
      }   
    }

    private void emptyObjectEdit() {
        
      String delete = null;
      ResultSet rset1 = null;
      Statement stmt1 = null;
      try {
        stmt1 = DBUtil.getStatement(getConnection(), "emptyObjectEdit()");

        delete = "delete from object_edit "; 
        stmt1.executeUpdate(delete);
        
      } catch (SQLException e) {
        s_log.error("emptyObjectEdit() e:" + e);
      } finally {
        DBUtil.close(stmt1, rset1, "this", "emptyObjectEdit()");
      }        
        
    }

}
