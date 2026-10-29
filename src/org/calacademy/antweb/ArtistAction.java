package org.calacademy.antweb;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;
import java.util.*;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class ArtistAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(ArtistAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward a = Check.init(Check.ARTIST, request, mapping); if (a != null) return a;
        //ActionForward a = Check.init(request, mapping); if (a != null) return a;

        Login accessLogin = LoginMgr.getAccessLogin(request);
        Group accessGroup = GroupMgr.getAccessGroup(request);

        HttpUtil.setUtf8(request, response);

		// Extract attributes we will need
		HttpSession session = request.getSession();

        DynaActionForm df = (DynaActionForm) form;
        String name = (String) df.get("name"); // Name could be id or name. We try id first.
        int artistId = 0;
        Integer id = (Integer) df.get("id");
        if (id != null) artistId = id;

        int moveTo = 0;
        Integer moveToInt = (Integer) df.get("moveTo");
        if (moveToInt != null) moveTo = moveToInt;

        boolean isCreate = false;
        boolean isRemove = false;
        boolean isEdit = false;

        boolean allowEdit = LoginMgr.isCurator(request);
        if (allowEdit) {

            Boolean isCreateBool = (Boolean) df.get("isCreate");
            isCreate = false;
            if (isCreateBool != null) isCreate = isCreateBool;

            Boolean isRemoveBool = (Boolean) df.get("isRemove");
            isRemove = isRemoveBool != null && isRemoveBool;

            Boolean isEditBool = (Boolean) df.get("isEdit");
            isEdit = (isEditBool != null) && isEditBool;
        }
        A.log("ArtistAction.execute() name:" + name + " artistId:" + artistId + " isEdit:" + isEdit + " allowEdit:" + allowEdit);

        // Changes only by POST, so the CSRF filter checks the form token.
        if ((isCreate || isEdit || isRemove) && !HttpUtil.isPost(request)) {
            request.setAttribute("message", "Use the form on the artist page to make changes.");
            return mapping.findForward("message");
        }

        if ((name == null || "".equals(name)) && isCreate) {
			request.setAttribute("message", "Enter an Artist Name.");
			return mapping.findForward("message");
        }

        boolean goToArtistManager = false;
        
        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("ArtistAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);

            ArtistDb artistDb = new ArtistDb(connection);

            if (isCreate) {
                if (name != null && artistId == 0) {
                  s_log.debug("Create artist. isCreate:" + isCreate);
                }
                Artist test = ArtistMgr.getArtist(name);
                if (test != null) {
                  request.setAttribute("message", "artist:" + Html.esc(name) + " already exists.");
                  return mapping.findForward("message");
                }

                Curator curator = LoginMgr.getCurator(accessLogin.getId());
                s_log.debug("execute() curator:" + curator + " accessGroup.loginId:" + accessLogin.getId());
                String retVal = artistDb.createArtist(name, curator);
                if (retVal.contains("error")) {
                  request.setAttribute("message", "<font color=red>Artist " + Html.esc(name) + " not added.</font>");                  
                } else {
                  ArtistMgr.populate(connection, true, false);
                  request.setAttribute("message", "<font color=green>" + retVal + "</font>");
                }
                goToArtistManager = true;
            }

            if (isEdit) {
                Artist artist = ArtistMgr.getArtist(artistId);
                if (artist == null) {
                  request.setAttribute("message", "Artist with id:" + artistId + " does not exist.");
                  return mapping.findForward("message");
                }
                if (!mayChange(artist, accessLogin)) {
                  request.setAttribute("message", "Only the artist's curator or an administrator may rename an artist.");
                  return mapping.findForward("message");
                }
                if (Utility.isBlank(name)) {
                  request.setAttribute("message", "Enter an Artist Name.");
                  return mapping.findForward("message");
                }
                name = name.trim();
                Artist sameName = artistDb.getArtistByName(name);
                if (sameName != null && sameName.getId() != artist.getId()) {
                  // Two photographers with one name cannot be told apart in the image credits.
                  request.setAttribute("message", "Nothing was changed: photographer " + sameName.getLink() + " (id " + sameName.getId()
                      + ") already has that name. To merge the two, delete this one and enter " + sameName.getId()
                      + " as the photographer its images move to.");
                  return mapping.findForward("message");
                }
                String oldName = artist.getName();
                artist.setName(name);
                String returnVal = artistDb.saveArtist(artist, accessLogin);
                if (returnVal == null) {
                  request.setAttribute("message", "Artist with id:" + artistId + " not saved.");
                  return mapping.findForward("message");
                }
                ArtistMgr.populate(connection, true, false);
                if (returnVal.isEmpty()) returnVal = "Renamed " + Html.esc(oldName) + " to " + Html.esc(name) + ".";
                request.setAttribute("message", "<font color=green>" + returnVal + "</font>");
                //goToArtistManager = true;
            }

            if (isRemove) {
                Artist artist = ArtistMgr.getArtist(artistId);
                if (artist == null) {
                  request.setAttribute("message", "Artist with id:" + artistId + " does not exist.");
                  return mapping.findForward("message");
                }
                if (!mayChange(artist, accessLogin)) {
                  request.setAttribute("message", "Only the artist's curator or an administrator may remove an artist.");
                  return mapping.findForward("message");
                }
                Artist moveToArtist = ArtistMgr.getArtist(moveTo);
                // The artist the images move to must exist and be another artist (2026-10-29).
                if (moveTo != 0 && (moveToArtist == null || moveTo == artistId)) {
                  request.setAttribute("message", moveTo == artistId
                      ? "Choose another artist to move the images to: an artist cannot take its own images."
                      : "Artist with id:" + moveTo + " (to move the images to) does not exist. Nothing was changed.");
                  return mapping.findForward("message");
                }
                String returnVal = artistDb.removeArtist(artist, moveToArtist, accessLogin);
                if (returnVal == null) {
                  request.setAttribute("message", "Artist with id:" + artistId + " not deleted.");
                  return mapping.findForward("message");
                }
                ArtistMgr.populate(connection, true, false);
                request.setAttribute("message", "<font color=green>" + returnVal + "</font>");
                goToArtistManager = true;
            }

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            String message = "e:" + Html.esc(e);
            request.setAttribute("message", message);
            return mapping.findForward("message");
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }          

        String key = "";
        if (!goToArtistManager && (!Utility.isBlank(name) || artistId != 0)) {
          Artist artist = null;
          if (name != null) {
            key += "name:" + name;
            artist = ArtistMgr.getArtist(name);
          }
          if (artistId != 0) {
            key += "id:" + artistId;
            artist = ArtistMgr.getArtist(artistId);
          }
          if (artist == null) {
            String message = "  Artist not found " + Html.esc(key) + ". Server initializing?";
            request.setAttribute("message", message);
            return mapping.findForward("message");
          }                    
		  request.setAttribute("artist", artist);
		  return mapping.findForward("artist");
        } else {
          // A copy: artists-body.jsp sorts the list, and the shared list must not be sorted by two
          // requests at once (ConcurrentModificationException, the error page; 2026-10-22).
          ArrayList<Artist> shared = ArtistMgr.getArtists();
          ArrayList<Artist> artists = shared == null ? null : new ArrayList<>(shared);

/*          
          s_log.warn("execute() UTF check:" + ArtistMgr.getArtist(221));
          for (Artist artist : artists) {
            String artistName = artist.getName();
            if (Formatter.hasSpecialCharacter(artistName) || Formatter.hasWebSpecialCharacter(artistName) || Formatter.hasTextSpecialCharacter(artistName)) 
              A.log("execute() artist:" + artistName + ":" + artist.getId() + " has:" + Formatter.hasSpecialCharacter(artistName)  + " hasWeb:" + Formatter.hasWebSpecialCharacter(artistName) + " hasText:" + Formatter.hasTextSpecialCharacter(artistName));
          }
*/
		  request.setAttribute("artists", artists);
		  //A.log("ArtistAction.execute() artists:" + artists);
		  return mapping.findForward("artists");
        }
	}

    /** Rename and remove: the artist's curator or an administrator. */
    public static boolean mayChange(Artist artist, Login accessLogin) {
        if (artist == null || accessLogin == null) return false;
        return Boolean.TRUE.equals(accessLogin.isAdmin()) || artist.getCuratorId() == accessLogin.getId();
    }
}
