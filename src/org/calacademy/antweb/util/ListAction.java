package org.calacademy.antweb.util;

import java.io.IOException;
import java.util.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class ListAction extends AntwebAction {

    // countries, bioregions: Curate page cleanup. getTaxonNames (the autocomplete does this),
    // recentCASPinnedPonerinae and specimensWithMorphoGenera (one-off reports), toUpload (a folder
    // that is not in the container): removed 2026-10-19.
    static final java.util.Set<String> REMOVED_ACTIONS = java.util.Set.of("countries", "bioregions",
        "getTaxonNames", "recentCASPinnedPonerinae", "specimensWithMorphoGenera", "toUpload");

    private static final Log s_log = LogFactory.getLog(ListAction.class);

    public ActionForward execute(
        ActionMapping mapping,
        ActionForm form,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException, ServletException {

        boolean botAttackDefence = HttpUtil.isBotAttackDefence(request);
        if (botAttackDefence) {
          request.setAttribute("message", "Invalid request.");
          return mapping.findForward("message");
        }       

        // Extract attributes we will need
        Locale locale = getLocale(request);
        HttpSession session = request.getSession();
        
        DynaActionForm df = (DynaActionForm) form;
        String action = (String) df.get("action");

        // Removed sub-actions (see REMOVED_ACTIONS).
        if (action != null && REMOVED_ACTIONS.contains(action)) {
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          request.setAttribute("message", "action:" + Html.esc(action) + " not found.");
          return mapping.findForward("message");
        }        
        
        
        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("ListAction.execute()");
        try {
          DataSource dataSource = getDataSource(request, "conPool");
          connection = DBUtil.getConnection(dataSource, dbMethodName);
          
          //dataSource.setZeroDateTimeBehavior("convertToNull");


          // s_log.warn("execute action:" + action);
            // Admin report: refuse others with the log-in page (it was an empty page for them).
            if ("casentDAnomalies".equals(action)) {
                ActionForward denied = Check.admin(request, mapping); if (denied != null) return denied;
            }
            switch (action) {
                case "pictureLikes": {
                    String message = "Antweb Liked Pictures List";
                    request.setAttribute("message", message);
                    ArrayList list = ImageDb.getLikesLinkList(connection);
                    request.setAttribute("list", list);
                    break;
                }
                case "likes": {
                    // Favorites
                    String message = "Antweb Liked Pictures List";
                    request.setAttribute("message", message);
                    ArrayList<LikeObject> list = ImageDb.getLikesObjectList(connection);
                    request.setAttribute("likeObjectList", list);

                    // Override default. Use dracula ant.
                    //OpenGraphMgr.setOGImage(OpenGraphMgr.DRACULA);
                    //OpenGraphMgr.setOGTitle("Favorite images as selected by the curators of Antweb.org.");
                    //OpenGraphMgr.setOGDesc("Witness the great diversity of the over 15,000 species of ants on Earth.");
                    request.setAttribute("ogImage", SpecimenImage.DRACULA);
                    request.setAttribute("ogTitle", "Favorite images as selected by the curators of Antweb.org.");
                    request.setAttribute("ogDesc", "Witness the great diversity of the over 15,000 species of ants on Earth.");

                    return mapping.findForward("likes");
                }
                case "recentDescEdits": {
                    String message = "Antweb Recent Description Edits";
                    request.setAttribute("message", message);
                    session.setAttribute("taxon", null);
                    DescEditDb descEditDb = new DescEditDb(connection);
                    ArrayList<DescEdit> descEdits = descEditDb.getRecentDescEdits();
                    request.setAttribute("descEdits", descEdits);
                    break;
                }
                default:


                    if (LoginMgr.isAdmin(request)) {
                        if (action.equals("usrAdm")) {
                            String message = "Antweb user Admin List";
                            request.setAttribute("message", message);
                            ArrayList<String> list = LoginDb.getUsrAdmList(connection);
                            //A.log("ListAction.execute() accessGroup:" + accessGroup + " isAdmin:" + accessGroup.isAdmin() + " size:" + list.size());
                            request.setAttribute("list", list);
                            return mapping.findForward("list");
                        }
                        if (action.equals("usrAdmLastLogin")) {
                            String message = "Antweb Last Login List";
                            request.setAttribute("message", message);
                            ArrayList<String> list = LoginDb.getUsrAdmLastLoginList(connection);
                            request.setAttribute("list", list);
                            return mapping.findForward("list");
                        }
                        if (action.equals("casentDAnomalies")) {
                            List<SpecimenDb.PartGroup> groups = new SpecimenDb(connection).getPartsWithDifferentNames();
                            Integer groupId = PartsReport.parseGroupId(request.getParameter("groupId"));
                            if ("csv".equals(request.getParameter("format"))) {
                                byte[] body = PartsReport.csv(groups, groupId, AntwebProps.getDomainApp())
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                                response.setContentType("text/csv; charset=UTF-8");
                                response.setHeader("Content-Disposition", "attachment; filename=\"" + PartsReport.csvFileName(groupId) + "\"");
                                response.setHeader("Cache-Control", "no-store");
                                response.setHeader("X-Content-Type-Options", "nosniff");
                                response.setContentLength(body.length);
                                response.getOutputStream().write(body);
                                response.getOutputStream().flush();
                                return null;
                            }
                            request.setAttribute("message", PartsReport.html(groups, groupId, AntwebProps.getDomainApp()));
                            return mapping.findForward("adminHtmlMessage");
                        }


                    }

                    if (LoginMgr.isCurator(request)) {

                        if (action.equals("introducedSpecimen")) {
                            String message = "Introduced Specimen";
                            request.setAttribute("message", message);
                            SpecimenDb specimenDb = new SpecimenDb(connection);
                            Object o = df.get("groupId");
                            if (o == null) {
                                request.setAttribute("message", "Must enter &groupId= parameter");
                                return mapping.findForward("message");
                            }
                            int groupId = (Integer) o;
                            ArrayList<String> list = specimenDb.getIntroducedByGroup(groupId);
                            //A.log("ListAction list:" + list);
                            request.setAttribute("listTable", list);
                            return mapping.findForward("listTable");
                        }

                        if (action.equals("multiBioregionTaxaList")) {
                            int groupId = (Integer) df.get("groupId");
                            String message = "Non-introduced taxa in multiple bioregions for groupId:" + groupId;
                            request.setAttribute("message", message);
                            SpecimenDb specimenDb = new SpecimenDb(connection);
                            ArrayList<ArrayList<String>> listOfLists = specimenDb.getMultiBioregionTaxaList(groupId);
                            //A.log("ListAction listOfLists:" + listOfLists);
                            request.setAttribute("listOfLists", listOfLists);
                            return mapping.findForward("listOfLists");
                        }
                    }
                    request.setAttribute("message", "action:" + Html.esc(action) + " not found. Be sure you are logged in.");
                    return mapping.findForward("message");
            }
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return mapping.findForward("error");
        } finally { 		
            DBUtil.close(connection, this, dbMethodName);
        }             
        
        return mapping.findForward("success");
    }
    /** The "Specimen parts (-D codes) with different names" page, all groups (see PartsReport). */
    public static String partsReport(List<SpecimenDb.PartGroup> groups) {
        return PartsReport.html(groups, null, AntwebProps.getDomainApp());
    }
}
