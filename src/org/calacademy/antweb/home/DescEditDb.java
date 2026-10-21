package org.calacademy.antweb.home;

import java.util.*;
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

        Hashtable<String, String> description = new DescriptionTable();

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
            // Never pass a failed read off as empty fields: the page says the text could not be
            // loaded and offers no editor (an empty editor could be saved over the stored text).
            description = DescriptionTable.failed();
        } finally {
            DBUtil.close(stmt, rset, this, "getDescEdits()");
        }
        return description;
    }

    /**
     * The stored text of one page field, exactly as in the database (no dequote, no https
     * rewrite), for the editor and for the save checks. Null if the field has no text.
     * Taxon pages: description_edit by taxon_name (code is null). Specimen pages: description_edit
     * by code (as the specimen page shows it). Overview pages: object_edit by object_key.
     * forUpdate locks the row until the transaction ends. Throws on a database error: the caller
     * must not treat a failed read as an empty field.
     */
    public static String readFieldForEdit(Connection connection, Describable describable, String title, boolean forUpdate)
        throws SQLException {
        String sql;
        String key;
        if (describable instanceof Specimen) {
            sql = "select content from description_edit where code = ? and title = ? order by created desc, edit_id desc limit 1";
            key = ((Specimen) describable).getCode();
        } else if (describable instanceof Taxon) {
            sql = "select content from description_edit where taxon_name = ? and title = ? and code is null limit 1";
            key = ((Taxon) describable).getTaxonName();
        } else {
            sql = "select content from object_edit where object_key = ? and title = ? limit 1";
            key = describable.getName();
        }
        if (forUpdate) sql += " for update";
        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = connection.prepareStatement(sql);
            stmt.setString(1, key);
            stmt.setString(2, title);
            rset = stmt.executeQuery();
            if (rset.next()) return rset.getString("content");
            return null;
        } finally {
            if (rset != null) try { rset.close(); } catch (SQLException e) { /* ignore */ }
            if (stmt != null) try { stmt.close(); } catch (SQLException e) { /* ignore */ }
        }
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
                descEdit.setCreated(rset.getTimestamp("created"));
                // descEdit.setTaxonId(rset.getInt("taxon_id"));
                descEdit.setIsManualEntry(rset.getBoolean("is_manual_entry"));
                descEdit.setAccessGroupId(rset.getInt("access_group"));
                descEdit.setAccessLoginId(rset.getInt("access_login"));

                Login accessLogin = new LoginDb(getConnection()).getLogin(descEdit.getAccessLoginId());  // quickload
                descEdit.setAccessLogin(accessLogin);
                
                if (descEdit.getCode() == null) {
                  Taxon taxon = new TaxonDb(getConnection()).getTaxon(descEdit.getTaxonName());
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
        Hashtable<String, String> description = new DescriptionTable();
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
            description = DescriptionTable.failed();  // see getDescEdits()
        } finally {
            DBUtil.close(stmt, rset, this, "DescEditDb.getDescription()");
        }

        return description;
    }

}
