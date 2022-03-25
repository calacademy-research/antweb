package org.calacademy.antweb;

import java.io.*; 
import java.util.*;
import javax.servlet.http.*;
import org.apache.struts.action.*;
import java.sql.*;
import java.util.Date;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class DescriptionAction extends Action {

    private static final Log s_log = LogFactory.getLog(DescriptionAction.class);
      
    protected boolean saveDescriptionEdit(DescriptionForm form, Describable editObject, Login accessLogin, HttpServletRequest request, Connection connection) 
        throws SQLException {            
        boolean success = true;
        if (accessLogin != null) {
            // The descriptionEdit.do allows for the posting and saving of Description Edit records.
            String editField = form.getEditField();
            boolean isSaveEditField = form.getIsSaveEditField();

            //A.log("saveDescriptionEdit() editField:" + editField + " is:" + isSaveEditField);          

            if (isSaveEditField && editField != null) {
              String contents = form.getContents();
              String imageUrl = form.getImageUrl();
              String guiDefaultContent = AntwebProps.getProp("gui.default.content");
                  
              s_log.debug("saveDescriptionEdit() descriptionEdit.  contents:" + contents + " imageUrl:" + imageUrl);
                  
              if (contents.equals(guiDefaultContent) && imageUrl == null) {
                // The user hit Done but did not modify the default text
                s_log.warn("saveDescriptionEdit() User hit done to save the gui default content.  Returning success.");
                return true;  // was a success forward
              }

              if (imageUrl != null && !"".equals(imageUrl)) {
                if (contents.equals(guiDefaultContent) || "null".equals(contents)) contents = "";
                // A.log("saveDescriptionEdit() 1 contents:" + contents);
                contents = HttpUtil.decode(contents);
                s_log.debug("saveDescriptionEdit() 2 contents:" + contents);
                String tag = "<a href=\"" + imageUrl + "\"><img class=\"taxon_page_img\" src=\"" + imageUrl + "\"></a>";
                s_log.debug("saveDescriptionEdit() 3 tag:" + tag);
                contents += "<br>" + tag;
                s_log.debug("saveDescriptionEdit() 4 editField:" + editField + " contents:" + contents);
                //A.log("saveDescriptionEdit() editField:" + editField + " imageUrl:" + imageUrl + " contents:" + contents);
              }

              //s_log.info("saveDescriptionEdit() New contents: " + contents);
              success = saveOrUpdateDescription(editObject, request, connection, editField, contents, accessLogin);
              
              AntwebCacheMgr.reCacheItem(connection, request);              
          }
        }  
        return success;
    }         
    
    protected boolean saveOrUpdateDescription(Describable describable, HttpServletRequest request, Connection connection, String title
      , String contents, Login accessLogin) throws SQLException {

        Group accessGroup = accessLogin.getGroup();

        if (describable instanceof Taxon) 
          return saveOrUpdateDescription((Taxon) describable, request, connection, title, contents, accessLogin);

        // if Project or Museum, ...
          //saved = saveOrUpdateDescription(editObject, request, connection, title, contents, accessGroup);
//        if (editObject instanceof Museum) 
//          saved = saveOrUpdateDescription((Museum) editObject, request, connection, title, contents, accessGroup);
        

        if (contents.contains("<div")) {
          String message = "Error: contains div tag.  Edit source to remove.  describable:" + describable.getName() + " title:" + title;
          s_log.warn(message);
          request.setAttribute("message", message);          
          return false;
        }

        String accessGroupStr = accessGroup.getName();
        //String taxonName = new Utility().makeNameFromTaxon(taxon);
        String currentDML = "";  // for debugging.

        Statement stmt = null;
        try {
            int rowCount = 0;

            // To avoid MySQLSyntaxErrorException
            contents = contents.replaceAll("'", "''");

            String whereClause = " where object_key ='" + describable.getName() + "' and title ='" + title + "'"; // + "' and " + codeClause;
            
            String backupCommand = "insert into object_hist (object_key, title, content, edit_id, created, access_group, access_login) " 
              + " select object_key, title, content, edit_id, created, access_group, access_login " //\'" + accessGroupStr + "\', " + accessGroup.getLogin().getId() 
              + " from object_edit " + whereClause;
            currentDML = backupCommand;
            //insert into description_hist (taxon_name, title, content, edit_id, access_group) select taxon_name, title, content, edit_id, 'me' from description_edit where taxon_name = "formicinaecamponotus atriceps" and title = "textauthor";
            stmt = connection.createStatement();
            rowCount = stmt.executeUpdate(backupCommand);
            s_log.debug("saveOrUpdateDescription() insert hist backup:" + rowCount + " for backupString" + backupCommand);

            if ("".equals(contents)) {
              // delete from description_edit.
              //s_log.info("empty contents, so deleting description_edit record.");
              String deleteString = "delete from object_edit " + whereClause;
              currentDML = deleteString;            
              rowCount = stmt.executeUpdate(deleteString);
              //s_log.info("deleteString:" + deleteString + " rowCount:" + rowCount);            
            } else {
              // update description_edit.
              String updateString = "update object_edit set content = '" + contents + "', is_manual_entry = 1" 
                + " , access_group = " + accessGroup.getId() + " , access_login = " + accessLogin.getId()
                + " , created = now() "
                + whereClause;
              currentDML = updateString;
                // Could we use edit_id here?  
                // May need to escape content properly?
              rowCount = stmt.executeUpdate(updateString);
              s_log.debug("saveOrUpdateDescription() updateString:" + updateString + " rowCount:" + rowCount);
            
              if (rowCount == 0) {
                // record does not exist.  So no record backed up.  Let's insert instead...
                //if (code != null) code = "'" + code + "'";
                String insertString = "insert into object_edit (object_key, title, content, is_manual_entry, access_group, access_login) "
                  + " values ('" + describable.getName() + "', '" + title + "', '" + contents + "', 1, " + accessGroup.getId() + "," + accessLogin.getId() + ")";

                s_log.debug("saveOrUpdateDescription() insertString is:" + insertString);
                currentDML = insertString;
                stmt.executeUpdate(insertString);
              }
            }

            //A.log("saveOrUpdateDescription() taxon:" + taxon);
            describable.getDescription().put(title, contents);
            
        } catch (SQLException e) {
            s_log.error("saveOrUpdateDescription() Name:" + describable.getName() + " Title:" + title + " e:" + e  + " currentDML:" + currentDML);
           throw e;
        } finally {
           stmt.close();
        }
        return true;
    }

    protected boolean saveOrUpdateDescription(Taxon taxon, HttpServletRequest request, Connection connection, String title
      , String contents, Login accessLogin) throws SQLException {
        // See Plazi.java:135 for description table insertion
            
        Group accessGroup = accessLogin.getGroup();
            
        if (contents.contains("<div")) {
          String message = "Error: contains div tag.  Edit source to remove.  taxon:" + taxon.getTaxonName() + " title:" + title;
          s_log.warn(message);
          request.setAttribute("message", message);          
          return false;
        }

        if (Event.TAXON_PAGE_IMAGES.equals(title)
         || Event.TAXON_PAGE_VIDEOS.equals(title)
         || Event.TAXON_PAGE_OVERVIEW.equals(title)
           ) {
          EventDb eventDb = new EventDb(connection);
          s_log.debug("saveOrUpdateDescription() "
            + " taxonName:" + taxon.getTaxonName()
            + " title:" + title + " curatorId:" + accessLogin.getId());

          eventDb.addEvent(new Event(title, accessLogin.getId(), taxon.getTaxonName(), new Date()));
        }
        
        String accessGroupStr = accessGroup.getName();
        //String taxonName = new Utility().makeNameFromTaxon(taxon);
        String taxonName = taxon.getTaxonName();
        String currentDML = "";  // for debugging.

        Statement stmt = null;
        try {
            int rowCount = 0;

            // To avoid MySQLSyntaxErrorException
            contents = contents.replaceAll("'", "''");
            
            String code = null;
            String codeClause = " code is null";
            if (taxon instanceof Specimen) {
              code = taxon.getCode();
              codeClause = " code = '" + code + "'";
            }

            String whereClause = " where taxon_name='" + taxonName + "' and title='" + title + "' and " + codeClause;
            
            String backupCommand = "insert into description_hist (taxon_name, title, code, content, edit_id, created, access_group, access_login) " 
              + " select taxon_name, title, code, content, edit_id, created, access_group, access_login " //\'" + accessGroupStr + "\', " + accessGroup.getLogin().getId() 
              + " from description_edit " + whereClause;
            currentDML = backupCommand;
            //insert into description_hist (taxon_name, title, content, edit_id, access_group) select taxon_name, title, content, edit_id, 'me' from description_edit where taxon_name = "formicinaecamponotus atriceps" and title = "textauthor";
            stmt = connection.createStatement();
            rowCount = stmt.executeUpdate(backupCommand);
            s_log.debug("saveOrUpdateDescription() insert hist backup:" + rowCount + " for backupString" + backupCommand);

            if ("".equals(contents)) {
              // delete from description_edit.
              //s_log.info("empty contents, so deleting description_edit record.");
              String deleteString = "delete from description_edit " + whereClause;
              currentDML = deleteString;            
              rowCount = stmt.executeUpdate(deleteString);
              //s_log.info("deleteString:" + deleteString + " rowCount:" + rowCount);            
            } else {
              // update description_edit.
              String updateString = "update description_edit set content = '" + contents + "', is_manual_entry = 1" 
                + " , access_group = " + accessGroup.getId() + " , access_login = " + accessLogin.getId()
                + " , created = now() "
                + whereClause;
              currentDML = updateString;
                // Could we use edit_id here?  
                // May need to escape content properly?
              rowCount = stmt.executeUpdate(updateString);
              s_log.debug("saveOrUpdateDescription() updateString:" + updateString + " rowCount:" + rowCount);
            
              if (rowCount == 0) {
                // record does not exist.  So no record backed up.  Let's insert instead...
                if (code != null) code = "'" + code + "'";
                String insertString = "insert into description_edit (taxon_name, title, code, content, is_manual_entry, access_group, access_login) "
                  + " values ('" + taxonName + "', '" + title + "', " + code + ", '" + contents + "', 1, " + accessGroup.getId() + "," + accessLogin.getId() + ")";

                s_log.debug("saveOrUpdateDescription() insertString is:" + insertString);
                currentDML = insertString;
                stmt.executeUpdate(insertString);
              }
            }              

            // New contents.  Let's regenerate the recent content list...
            //s_log.warn("genRecentDescEdits.");
            try {
              AntwebFunctions.genRecentDescEdits(connection);
            } catch (IOException e) {
              s_log.error("saveOrUpdateDescription() taxonName:" + taxonName + " Title:" + title + " e:" + e);
              return false;
            }
            
            s_log.debug("saveOrUpdateDescription() taxon:" + taxon);
            taxon.getDescription().put(title, contents);
            
        } catch (SQLException e) {
            s_log.error("saveOrUpdateDescription() taxonName:" + taxonName + " Title:" + title + " e:" + e  + " currentDML:" + currentDML);
           throw e;
        } finally {
           stmt.close();
        }
        return true;
    }
    
    protected void getDescEditHistory(Taxon taxon, Connection connection, HttpServletRequest request)
      throws SQLException {
        /* Get all edits and hists for a given taxon where the access_login is known.  (Added late March, 2012) */
        
      String codeClause = " and code is null";
      if (taxon instanceof Specimen) {
        codeClause = " and code = '" + taxon.getCode() + "'";
      }
        
      String query = "select distinct de.created, l.first_name, l.last_name, l.name, title from description_edit de" 
          + " join login l on access_login = l.id "
          + " where taxon_name = \"" + taxon.getTaxonName() + "\""
          + codeClause
          + " and is_manual_entry = 1";
      query += " union select distinct dh.created, l.first_name, l.last_name, l.name, title from description_hist dh" 
          + " join login l on access_login = l.id "
          + " where taxon_name = \"" + taxon.getTaxonName() + "\""
          + codeClause
          + " order by created desc ";         

      Statement stmt = null;
      ResultSet rset = null;
      try {
          stmt = DBUtil.getStatement(connection, "getDescEditHistory()");
          rset = stmt.executeQuery(query);

          //A.log("getDescEditHistory() query:" + query);
 
          ArrayList<String> arrayList = new ArrayList<>();
          while (rset.next()) {

            String created = DateUtil.getFormatDateTimeStr(rset.getTimestamp("created"));        
            //s_log.warn("getDescEditHistory 1 created:" + created);
 
            String firstName = rset.getString("first_name");
            String lastName = rset.getString("last_name");
            String name = rset.getString("name");
            String title = DescEdit.getPrettyTitle(rset.getString("title"));
            String editor = firstName + " " + lastName;
            if (" ".equals(editor)) editor = name;
            String record = "On " + created + " " + editor + " modified " + title;
            if (!arrayList.contains(record)) {          
              arrayList.add(record);
            }
            //A.log("getDescEditHistory() created: " + created + " " + firstName + " " + lastName);
          }
          //A.log("getDescEditHistory() arrayList:" + arrayList);
          request.setAttribute("descEditHist", arrayList);
      } finally {
          DBUtil.close(stmt, rset, this, "getDescEditHistory()");
      }
    }
}
