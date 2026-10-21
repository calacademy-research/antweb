package org.calacademy.antweb;

import java.io.*; 
import java.util.*;
import jakarta.servlet.http.*;
import org.apache.struts.action.*;
import java.sql.*;
import java.util.Date;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class DescriptionAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(DescriptionAction.class);

    // The page-text fields a curator may edit (the editField / title column). Anything else
    // is refused: the value goes into SQL and names what is shown on the page.
    public static final Set<String> EDIT_FIELDS = Set.of(
        "author", "authorBio", "authorImage", "biology", "comments", "contents", "distribution",
        "identification", "images", "notes", "overview", "references", "specimenImage1", "specimenImage2",
        "specimenImage3", "taxanomicnotes", "taxonomicnotes", "taxonomichistory", "taxonomictreatment",
        "textauthor", "revdate", "videos");

    private static final String KEPT = " Your text is shown again below and is also kept as a draft in this browser.";

    /*
     * Save a page-text field (taxon, specimen and overview pages). Safety rules (DescEditSafety):
     * - only curators and administrators save; anyone else, including a curator whose session
     *   ended (daily restart, 4 hours idle), gets a message and their text back, never a silent loss;
     * - the editor must have fully loaded the stored text (editorReady=1 and loadedHash);
     * - if the stored text changed since the editor opened (loadedHash differs), nothing is
     *   overwritten: the curator sees their text and the newer stored text;
     * - an empty box is never saved over text; clearing is the separate clearField=1 action;
     * - check, history copy and write run in one transaction with the row locked.
     * A refused save sets a DescEditSafety.Result and returns true, so the page is shown with the
     * curator's text in the editor. Returns false (message page) only for an unknown field or a GET.
     */
    protected boolean saveDescriptionEdit(DescriptionForm form, Describable editObject, Login accessLogin, HttpServletRequest request, Connection connection) 
        throws SQLException {            
        String editField = form.getEditField();
        if (!form.getIsSaveEditField() || editField == null) return true;

        if (!EDIT_FIELDS.contains(editField)) {
          s_log.warn("saveDescriptionEdit() refused editField:" + editField + " login:" + (accessLogin == null ? "-" : accessLogin.getId()));
          request.setAttribute("message", "Unknown page field: " + Html.esc(editField));
          return false;
        }
        // Saving changes data: only from the edit form (POST).
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
          request.setAttribute("message", "Please use the edit form to save page text.");
          return false;
        }

        String contents = form.getContents();
        if (contents == null) contents = "";
        String imageUrl = form.getImageUrl();
        boolean isImage = imageUrl != null && !imageUrl.isEmpty();
        String pretty = DescEditSafety.prettyField(editField);
        String restoreFrom = form.getRestoreFrom();
        boolean isRestore = !isImage && restoreFrom != null && !restoreFrom.isEmpty();

        // Only curators and admins may save page text (not the anonymous "Browse" login or self-registered accounts).
        if (!DescEditSafety.canEdit(accessLogin) && isRestore) {
          DescEditSafety.Result r;
          if (accessLogin == null) {
            r = DescEditSafety.Result.refused(editField, "You are not logged in any more, so nothing was restored in " + pretty + "."
              + " (AntWeb restarts every morning around 8 AM Pacific, and logins end after 4 hours without use.)"
              + " Log in (the link opens a new tab), then open the History of this field again.", null);
            r.loggedOut = true;
          } else {
            r = DescEditSafety.Result.refused(editField, "Your account cannot edit page text, so nothing was restored.", null);
          }
          r.restore = true;
          s_log.warn("saveDescriptionEdit() restore refused, not a curator. field:" + editField
            + " login:" + (accessLogin == null ? "-" : accessLogin.getId()));
          request.setAttribute(DescEditSafety.RESULT_ATTR, r);
          return true;
        }
        if (!DescEditSafety.canEdit(accessLogin)) {
          DescEditSafety.Result r;
          if (accessLogin == null) {
            r = DescEditSafety.Result.refused(editField, "You are not logged in any more, so your text for " + pretty + " was NOT saved."
              + " (AntWeb restarts every morning around 8 AM Pacific, and logins end after 4 hours without use.)"
              + " Your text is shown below and is kept as a draft in this browser. Log in (the link opens a new tab),"
              + " then open this field again with [Edit] and choose Restore, then Save.", isImage ? null : contents);
            r.loggedOut = true;
          } else {
            r = DescEditSafety.Result.refused(editField, "Your account cannot edit page text, so nothing was saved."
              + " Your text is shown below.", isImage ? null : contents);
          }
          s_log.warn("saveDescriptionEdit() not saved, not a curator. field:" + editField
            + " login:" + (accessLogin == null ? "-" : accessLogin.getId()) + " length:" + contents.length());
          request.setAttribute(DescEditSafety.RESULT_ATTR, r);
          return true;
        }

        // The same per-field rule as the page: taxon page "notes" only for administrators and Jack.
        if (!DescEditSafety.canEditField(accessLogin, editObject, editField)) {
          s_log.warn("saveDescriptionEdit() refused, field not editable by login:" + accessLogin.getId() + " field:" + editField);
          DescEditSafety.Result r = DescEditSafety.Result.refused(editField, "Your account cannot edit " + pretty
            + " on this page, so nothing was saved." + (isRestore || isImage ? "" : " Your text is shown below."), isRestore || isImage ? null : contents);
          r.restore = isRestore;
          request.setAttribute(DescEditSafety.RESULT_ATTR, r);
          return true;
        }

        DescEditSafety.Result result;
        if (isImage) {
          result = saveImage(editObject, request, connection, editField, imageUrl, accessLogin);
        } else if (isRestore) {
          result = restoreVersion(form, editObject, request, connection, editField, restoreFrom, accessLogin);
          result.restore = true;
        } else {
          result = saveText(form, editObject, request, connection, editField, contents, accessLogin
            , "1".equals(form.getClearField()), null);
        }
        if (!result.saved) result.reshowInEditor = !isImage && !isRestore;
        request.setAttribute(DescEditSafety.RESULT_ATTR, result);
        return true;
    }

    /*
     * "Restore this version" from the History page (fieldHistory.do): the history row restoreFrom
     * of this page's field is saved through saveText, exactly like a curator's save (sanitizer,
     * conflict check against the fingerprint the History page showed, history copy of the text it
     * replaces, is_manual_entry=1 with the restoring curator as author). The text is read here from
     * the history table; the browser sends only the row's id. A row of another page or field is refused.
     */
    private DescEditSafety.Result restoreVersion(DescriptionForm form, Describable describable, HttpServletRequest request
      , Connection connection, String title, String restoreFrom, Login accessLogin) throws SQLException {
        String pretty = DescEditSafety.prettyField(title);
        int histId;
        try {
          histId = Integer.parseInt(restoreFrom.trim());
        } catch (NumberFormatException e) {
          histId = 0;
        }
        DescHistory.Version version;
        try {
          version = histId > 0 ? DescHistory.readVersion(connection, describable, title, histId) : null;
        } catch (SQLException e) {
          s_log.error("restoreVersion() name:" + describable.getName() + " field:" + title + " histId:" + histId + " e:" + e);
          return DescEditSafety.Result.refused(title, "Nothing was restored: the database did not answer (AntWeb may be busy or restarting)."
            + " Please try again in a minute.", null);
        }
        if (version == null) {
          s_log.warn("restoreVersion() refused, no such version. name:" + describable.getName() + " field:" + title + " restoreFrom:" + restoreFrom);
          return DescEditSafety.Result.refused(title, "Nothing was restored: that version is not in the history of " + pretty
            + " on this page. Open the History of this field again.", null);
        }
        if (version.isClear()) {
          return DescEditSafety.Result.refused(title, "Nothing was restored: that history entry records " + pretty
            + " being cleared, so it has no text. Choose an older version.", null);
        }
        String label = "the version of " + version.date() + " (" + version.who + ")";
        DescEditSafety.Result r = saveText(form, describable, request, connection, title, version.content, accessLogin, false, label);
        if (r.saved) s_log.info("restoreVersion() name:" + describable.getName() + " field:" + title + " histId:" + histId + " login:" + accessLogin.getId() + " " + r.message);
        return r;
    }

    /*
     * Save text through the safety rules. restoreLabel is null for a curator's save, else the
     * version being restored ("the version of <date> (<who>)"): same rules, restore wording, and
     * the text is not shown again on a refusal (it is on the History page).
     */
    private DescEditSafety.Result saveText(DescriptionForm form, Describable describable, HttpServletRequest request
      , Connection connection, String title, String contents, Login accessLogin, boolean clear, String restoreLabel) throws SQLException {
        String pretty = DescEditSafety.prettyField(title);
        String loadedHash = form.getLoadedHash();
        boolean restore = restoreLabel != null;
        if (restore) clear = false;
        String kept = restore ? "" : KEPT;
        String submitted = restore ? null : contents;
        String notDone = restore ? "Nothing was restored: " : "Nothing was saved: ";

        if (!"1".equals(form.getEditorReady()) || loadedHash == null || loadedHash.isEmpty()) {
          s_log.warn("saveText() refused, editor not ready. field:" + title + " login:" + accessLogin.getId());
          if (restore) return DescEditSafety.Result.refused(title, notDone + "the History page was opened before an AntWeb update."
            + " Open the History of " + pretty + " again, then choose Restore.", null);
          return DescEditSafety.Result.refused(title, "Nothing was saved: the editor had not finished loading the stored text of "
            + pretty + " (or this page was opened before an AntWeb update). Check the text below, then press Save again." + KEPT, contents);
        }

        String sanitized = clear ? "" : HtmlSanitizer.sanitize(contents, "videos".equals(title));
        if (!clear && sanitized.contains("<div")) {
          s_log.warn("saveText() refused, div. name:" + describable.getName() + " field:" + title);
          if (restore) return DescEditSafety.Result.refused(title, notDone + restoreLabel + " contains <div> blocks, which AntWeb no longer saves."
            + " Open " + pretty + " with [Edit], copy in the text of that version from the History page (its HTML source),"
            + " remove the <div ...> and </div> tags (keep the text between them), then Save.", null);
          return DescEditSafety.Result.refused(title, "Nothing was saved: the text contains a <div> block (from pasted web text)."
            + " Click Source, remove the <div ...> and </div> tags (keep the text between them), then Save." + KEPT, contents);
        }
        boolean blank = clear || DescEditSafety.isBlank(sanitized);

        boolean autoCommit = connection.getAutoCommit();
        String outcome;
        boolean replaced = false;
        try {
          connection.setAutoCommit(false);
          String current = DescEditDb.readFieldForEdit(connection, describable, title, true);
          replaced = current != null;
          String currentHash = DescEditSafety.hash(current);

          // Nothing would change (for example the same form sent twice, or the editor opened and saved
          // without changes; browsers send a text box's line breaks as CRLF): no write, no conflict.
          if (!blank && DescEditSafety.sameText(sanitized, current)) {
            connection.rollback();
            if (restore) return DescEditSafety.Result.saved(title, pretty + " already has the text of " + restoreLabel + "; nothing changed.");
            return DescEditSafety.Result.saved(title, "Your changes to " + pretty + " were saved (the stored text already matched).");
          }
          if (clear && current == null) {
            connection.rollback();
            return DescEditSafety.Result.saved(title, pretty + " was cleared (it was already empty).");
          }

          if (!currentHash.equals(loadedHash)) {
            connection.rollback();
            s_log.warn("saveText() refused, conflict. name:" + describable.getName() + " field:" + title + " login:" + accessLogin.getId() + (restore ? " (restore)" : ""));
            if (restore) {
              DescEditSafety.Result r = DescEditSafety.Result.refused(title, notDone + pretty + " was changed after you opened its History,"
                + " so it was not replaced. Open the History of " + pretty + " again to see the newest text, then choose Restore again.", null);
              r.conflict = true;
              return r;
            }
            DescEditSafety.Result r = DescEditSafety.Result.refused(title, "Nothing was saved: someone else saved " + pretty
              + " after you opened the editor. Their text (now stored) is shown below; your text is in the editor."
              + " Combine them in the editor if needed, then Save (that replaces the stored text)."
              + " Your text is also kept as a draft in this browser.", clear ? null : contents);
            r.conflict = true;
            return r;
          }

          if (blank && restore) {
            connection.rollback();
            return DescEditSafety.Result.refused(title, notDone + restoreLabel + " has no text left after AntWeb's safety cleaning.", null);
          }
          if (blank && current == null) {
            connection.rollback();
            return DescEditSafety.Result.saved(title, "Nothing to save: the box was empty and " + pretty + " has no text.");
          }
          if (blank && !clear) {
            connection.rollback();
            DescEditSafety.Result r = DescEditSafety.Result.refused(title, "Nothing was saved: the box is empty, and an empty box is never saved over "
              + pretty + ". If you really want to remove all text from this field, use the \"Clear this field\" button.", contents);
            r.offerClear = true;
            return r;
          }

          writeField(describable, connection, title, blank ? null : sanitized, current != null, accessLogin);
          connection.commit();
          outcome = blank ? "cleared" : "saved";
        } catch (SQLException e) {
          try { connection.rollback(); } catch (SQLException e2) { /* ignore */ }
          s_log.error("saveText() name:" + describable.getName() + " field:" + title + " login:" + accessLogin.getId() + " e:" + e);
          return DescEditSafety.Result.refused(title, notDone + "the database did not answer (AntWeb may be busy or restarting)."
            + (restore ? " Please try again in a minute." : " Please try Save again in a minute.") + kept, submitted);
        } finally {
          try { connection.setAutoCommit(autoCommit); } catch (SQLException e) { /* ignore */ }
        }

        afterWrite(describable, request, connection, title, "cleared".equals(outcome) ? null : sanitized, accessLogin);
        if ("cleared".equals(outcome)) {
          s_log.info("saveText() cleared name:" + describable.getName() + " field:" + title + " login:" + accessLogin.getId());
          return DescEditSafety.Result.saved(title, pretty + " was cleared. The old text is kept in the page history.");
        }
        if (restore) return DescEditSafety.Result.saved(title, pretty + " was restored to " + restoreLabel + "."
          + (replaced ? " The text it replaced is kept in the History." : ""));
        return DescEditSafety.Result.saved(title, "Your changes to " + pretty + " were saved.");
    }

    /*
     * The image upload confirmation: add the uploaded image to the field as it is now (taxon page
     * "images": appended; overview page image fields: replaced, as before). The text the upload
     * form saw when the page opened is not used, so a newer edit is never overwritten.
     */
    private DescEditSafety.Result saveImage(Describable describable, HttpServletRequest request, Connection connection
      , String title, String imageUrl, Login accessLogin) throws SQLException {
        String pretty = DescEditSafety.prettyField(title);
        String tag = "<a href=\"" + Html.escAttr(imageUrl) + "\"><img class=\"taxon_page_img\" src=\"" + Html.escAttr(imageUrl) + "\"></a>";
        boolean autoCommit = connection.getAutoCommit();
        String contents;
        try {
          connection.setAutoCommit(false);
          String current = DescEditDb.readFieldForEdit(connection, describable, title, true);
          if ("images".equals(title) && current != null) {
            contents = current + "<br>" + tag;
          } else {
            contents = "<br>" + tag;
          }
          contents = HtmlSanitizer.sanitize(contents, false);
          writeField(describable, connection, title, contents, current != null, accessLogin);
          connection.commit();
        } catch (SQLException e) {
          try { connection.rollback(); } catch (SQLException e2) { /* ignore */ }
          s_log.error("saveImage() name:" + describable.getName() + " field:" + title + " e:" + e);
          return DescEditSafety.Result.refused(title, "The image was uploaded (" + imageUrl + ") but could not be added to "
            + pretty + " because the database did not answer. Please try again in a minute.", null);
        } finally {
          try { connection.setAutoCommit(autoCommit); } catch (SQLException e) { /* ignore */ }
        }
        afterWrite(describable, request, connection, title, contents, accessLogin);
        return DescEditSafety.Result.saved(title, "The image was added to " + pretty + ".");
    }

    /*
     * Copy the stored row to history, then write the new text, or delete the row when newContents
     * is null (clear). A clear also adds a history row with empty text, the time and the person who
     * cleared it, so the history shows who did it. Runs inside the caller's transaction.
     */
    private void writeField(Describable describable, Connection connection, String title, String newContents
      , boolean exists, Login accessLogin) throws SQLException {
        String table, histTable, where, histCols, histSelect;
        List<String> keys = new ArrayList<>();
        if (describable instanceof Specimen) {
          table = "description_edit"; histTable = "description_hist";
          where = " where code = ? and title = ?";
          keys.add(((Specimen) describable).getCode());
        } else if (describable instanceof Taxon) {
          table = "description_edit"; histTable = "description_hist";
          where = " where taxon_name = ? and title = ? and code is null";
          keys.add(((Taxon) describable).getTaxonName());
        } else {
          table = "object_edit"; histTable = "object_hist";
          where = " where object_key = ? and title = ?";
          keys.add(DescEditDb.objectKey(describable));
        }
        keys.add(title);
        boolean isDesc = "description_edit".equals(table);
        String keyCols = isDesc ? "taxon_name, title, code" : "object_key, title";
        int groupId = accessLogin.getGroupId();

        if (exists) {
          // History: the current text, with its author and date.
          histCols = keyCols + ", content, edit_id, created, access_group, access_login";
          execute(connection, "insert into " + histTable + " (" + histCols + ") select " + histCols + " from " + table + where, keys);
        }

        if (newContents == null) {
          if (exists) {
            // History: who cleared the field, and when.
            List<String> params = new ArrayList<>();
            params.add(String.valueOf(groupId));
            params.add(String.valueOf(accessLogin.getId()));
            params.addAll(keys);
            histCols = keyCols + ", content, edit_id, created, access_group, access_login";
            histSelect = keyCols + ", '', edit_id, now(), ?, ?";
            execute(connection, "insert into " + histTable + " (" + histCols + ") select " + histSelect + " from " + table + where, params);
            execute(connection, "delete from " + table + where, keys);
          }
          return;
        }

        List<String> params = new ArrayList<>();
        params.add(newContents);
        params.add(String.valueOf(groupId));
        params.add(String.valueOf(accessLogin.getId()));
        params.addAll(keys);
        int rows = execute(connection, "update " + table + " set content = ?, is_manual_entry = 1, access_group = ?, access_login = ?, created = now()" + where, params);
        if (rows == 0) {
          List<String> ins = new ArrayList<>();
          String sql;
          if (isDesc) {
            sql = "insert into description_edit (taxon_name, title, code, content, is_manual_entry, access_group, access_login) values (?, ?, ?, ?, 1, ?, ?)";
            ins.add(((Taxon) describable).getTaxonName());
            ins.add(title);
            ins.add(describable instanceof Specimen ? ((Specimen) describable).getCode() : null);
          } else {
            sql = "insert into object_edit (object_key, title, content, is_manual_entry, access_group, access_login) values (?, ?, ?, 1, ?, ?)";
            ins.add(DescEditDb.objectKey(describable));
            ins.add(title);
          }
          ins.add(newContents);
          ins.add(String.valueOf(groupId));
          ins.add(String.valueOf(accessLogin.getId()));
          execute(connection, sql, ins);
        }
    }

    private static int execute(Connection connection, String sql, List<String> params) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
          for (int i = 0; i < params.size(); i++) {
            if (params.get(i) == null) stmt.setNull(i + 1, Types.VARCHAR);
            else stmt.setString(i + 1, params.get(i));
          }
          return stmt.executeUpdate();
        }
    }

    // After a committed write: the in-memory page object, events, recent edits list, page cache.
    private void afterWrite(Describable describable, HttpServletRequest request, Connection connection, String title
      , String newContents, Login accessLogin) {
        Hashtable<String, String> desc = describable.getDescription();
        if (desc != null) {
          if (newContents == null) desc.remove(title);
          else desc.put(title, newContents);
        }
        if (describable instanceof Taxon) {
          Taxon taxon = (Taxon) describable;
          try {
            if (Event.TAXON_PAGE_IMAGES.equals(title) || Event.TAXON_PAGE_VIDEOS.equals(title) || Event.TAXON_PAGE_OVERVIEW.equals(title)) {
              new EventDb(connection).addEvent(new Event(title, accessLogin.getId(), taxon.getTaxonName(), new Date()));
            }
            AntwebFunctions.genRecentDescEdits(connection);
          } catch (SQLException | IOException e) {
            s_log.error("afterWrite() taxonName:" + taxon.getTaxonName() + " title:" + title + " e:" + e);
          }
        }
        try {
          AntwebCacheMgr.reCacheItem(connection, request);
        } catch (SQLException e) {
          s_log.warn("afterWrite() reCacheItem e:" + e);
        }
    }

    /*
     * Prepare the editor for the field being edited on this page (GET ?editField=..., or a refused
     * save): read its stored text fresh from the database, with its fingerprint. A failed read
     * shows a message instead of an editor. Also sets the page key used for browser drafts.
     */
    protected void prepareDescriptionEdit(Describable describable, Login accessLogin, HttpServletRequest request, Connection connection) {
        if (describable == null || !DescEditSafety.canEdit(accessLogin)) return;
        request.setAttribute(DescEditSafety.PAGE_KEY_ATTR, DescEditSafety.pageKey(describable));
        String field = DescEditSafety.activeEditField(request);
        if ("none".equals(field) || !EDIT_FIELDS.contains(field)) return;
        DescEditSafety.Loaded load;
        try {
          load = new DescEditSafety.Loaded(field, DescEditDb.readFieldForEdit(connection, describable, field, false), false);
        } catch (SQLException e) {
          s_log.error("prepareDescriptionEdit() name:" + describable.getName() + " field:" + field + " e:" + e);
          load = new DescEditSafety.Loaded(field, null, true);
        }
        request.setAttribute(DescEditSafety.LOAD_ATTR, load);
    }

    protected void getDescEditHistory(Taxon taxon, Connection connection, HttpServletRequest request)
      throws SQLException {
        /* Get all edits and hists for a given taxon where the access_login is known.  (Added late March, 2012) */

      String keyClause = " where taxon_name = ? and code is null";
      String key = taxon.getTaxonName();
      if (taxon instanceof Specimen) {
        keyClause = " where code = ?";
        key = taxon.getCode();
      }
        
      String query = "select distinct de.created, l.first_name, l.last_name, l.name, title from description_edit de" 
          + " join login l on access_login = l.id "
          + keyClause
          + " and is_manual_entry = 1";
      query += " union select distinct dh.created, l.first_name, l.last_name, l.name, title from description_hist dh" 
          + " join login l on access_login = l.id "
          + keyClause
          + " order by created desc ";         

      PreparedStatement stmt = null;
      ResultSet rset = null;
      String dbMethodName = DBUtil.getDbMethodName("getDescEditHistory()");
      try {
          stmt = connection.prepareStatement(query);
          stmt.setString(1, key);
          stmt.setString(2, key);
          rset = stmt.executeQuery();

          ArrayList<String> arrayList = new ArrayList<>();
          while (rset.next()) {

            String created = DateUtil.getFormatDateTimeStr(rset.getTimestamp("created"));        
 
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
          }
          request.setAttribute("descEditHist", arrayList);
      } finally {
          DBUtil.close(stmt, rset, this, dbMethodName);
      }
    }
}
