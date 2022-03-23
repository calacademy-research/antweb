package org.calacademy.antweb;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;
import java.util.*;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class ArtistAction extends Action {

    private static Log s_log = LogFactory.getLog(ArtistAction.class);

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
        if (id != null) artistId = id.intValue();

        int moveTo = 0;
        Integer moveToInt = (Integer) df.get("moveTo");
        if (moveToInt != null) moveTo = moveToInt.intValue();

        Boolean isCreateBool = (Boolean) df.get("isCreate");
        boolean isCreate = false;
        if (isCreateBool != null) isCreate = isCreateBool.booleanValue();

        Boolean isRemoveBool = (Boolean) df.get("isRemove");
        boolean isRemove = (isRemoveBool != null && isRemoveBool);

        Boolean isEditBool = (Boolean) df.get("isEdit");
        boolean isEdit = (isEditBool != null && isEditBool);
        
        //A.log("GroupAction.execute() name:" + name + " groupId:" + groupId);

        if ((name == null || "".equals(name)) && isCreate) {
			request.setAttribute("message", "Enter an Artist Name in the URL bar...");
			return (mapping.findForward("message"));
        }

        boolean goToArtistManager = false;
        
        Connection connection = null;
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "ArtistAction.execute()");

            ArtistDb artistDb = new ArtistDb(connection);

            if (isCreate) {
                if (name != null && artistId == 0) {
                  s_log.debug("Create artist. isCreate:" + isCreate);
                }
                Artist test = ArtistMgr.getArtist(name);
                if (test != null) {
                  request.setAttribute("message", "artist:" + name + " already exists.");
                  return (mapping.findForward("message"));                
                }

                Curator curator = LoginMgr.getCurator(accessLogin.getId());
                s_log.debug("execute() curator:" + curator + " accessGroup.loginId:" + accessLogin.getId());
                String retVal = artistDb.createArtist(name, curator);
                if (retVal.contains("error")) {
                  request.setAttribute("message", "<font color=red>Artist " + name + " added.</font>");                  
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
                  return (mapping.findForward("message"));                
                }
                artist.setName(name);
                String returnVal = artistDb.saveArtist(artist, accessLogin);
                if (returnVal == null) {
                  request.setAttribute("message", "Artist with id:" + artistId + " not saved.");
                  return (mapping.findForward("message"));
                }
                ArtistMgr.populate(connection, true, false);
                request.setAttribute("message", "<font color=green>" + returnVal + "</font>");
                //goToArtistManager = true;
            }

            if (isRemove) {
                Artist artist = ArtistMgr.getArtist(artistId);
                if (artist == null) {
                  request.setAttribute("message", "Artist with id:" + artistId + " does not exist.");
                  return (mapping.findForward("message"));
                }
                Artist moveToArtist = ArtistMgr.getArtist(moveTo);
                String returnVal = artistDb.removeArtist(artist, moveToArtist, accessLogin);
                if (returnVal == null) {
                  request.setAttribute("message", "Artist with id:" + artistId + " not deleted.");
                  return (mapping.findForward("message"));                
                }
                ArtistMgr.populate(connection, true, false);
                request.setAttribute("message", "<font color=green>" + returnVal + "</font>");
                goToArtistManager = true;
            }

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            String message = "e:" + e.toString();
            request.setAttribute("message", message);
            return (mapping.findForward("message"));                
        } finally {
            DBUtil.close(connection, this, "ArtistAction.execute()");
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
            String message = "  Artist not found " + key + ". Server initializing?";
            request.setAttribute("message", message);
            return (mapping.findForward("message"));
          }                    
		  request.setAttribute("artist", artist);
		  return (mapping.findForward("artist"));
        } else {
          ArrayList<Artist> artists = ArtistMgr.getArtists();

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
		  return (mapping.findForward("artists"));        
        }
	}
}
