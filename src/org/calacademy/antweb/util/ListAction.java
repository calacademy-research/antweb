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

    static final java.util.Set<String> REMOVED_ACTIONS = java.util.Set.of("countries", "bioregions");

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

        // Removed lists (Curate page cleanup): the public Countries and Bioregions pages replace them.
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
                case "toUpload": {
                    // Server file listing: administrators only.
                    ActionForward denied = Check.admin(request, mapping); if (denied != null) return denied;
                    String toUploadDir = AntwebProps.getProp("site.toUpload");
                    String message = new AntwebSystem().launchProcess("ls -al " + toUploadDir, true);
                    request.setAttribute("message", message);
                    return mapping.findForward("message");
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
                case "getTaxonNames": {
                    String taxonName = (String) df.get("taxonName");
                    String message = "Taxon Names matching:" + Html.esc(taxonName);
                    request.setAttribute("message", message);
                    TaxonDb taxonDb = new TaxonDb(connection);
                    List<String> list = taxonDb.getTaxonNames(taxonName, true);
                    //A.log("ListAction listOfLists:" + listOfLists);
                    request.setAttribute("listTable", list);
                    return mapping.findForward("listTable");
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
                            request.setAttribute("message", partsReport(groups));
                            return mapping.findForward("adminHtmlMessage");
                        }

                        if (action.equals("recentCASPinnedPonerinae")) {
                            SpecimenDb specimenDb = new SpecimenDb(connection);
                            QueryReport queryReport = specimenDb.getRecentCASPinnedPonerinaeQueryReport();
                            request.setAttribute("queryReport", queryReport);
                            return mapping.findForward("queryReport");
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

                        if (action.equals("specimensWithMorphoGenera")) {
                            String message = "Specimen with Morpho Genera";
                            request.setAttribute("message", message);
                            SpecimenDb specimenDb = new SpecimenDb(connection);
                            int groupId = (Integer) df.get("groupId");
                            ArrayList<String> list = specimenDb.getSpecimensWithMorphoGenera(groupId);
                            s_log.debug("ListAction list:" + list);
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
    /** The "Specimen parts (-D codes) with different names" page: explanation, then one row per base specimen. */
    public static String partsReport(List<SpecimenDb.PartGroup> groups) {
        String domainApp = AntwebProps.getDomainApp();
        StringBuilder b = new StringBuilder("</b><h1>Specimen parts (-D codes) with different names</h1>");
        b.append(ReportHelp.PARTS);
        b.append("<p><b>").append(groups.size()).append(groups.size() == 1 ? " specimen" : " specimens")
         .append("</b> with parts under different names.</p>");
        if (groups.isEmpty()) {
            b.append("<p>None: every specimen's parts carry the same name.</p>");
            return b.toString();
        }
        b.append("<table cellpadding=6 border=1 style='border-collapse:collapse'><tr><th>#</th><th>Specimen</th><th>Part</th><th>Taxon name</th></tr>");
        int i = 0;
        for (SpecimenDb.PartGroup g : groups) {
            ++i;
            int rows = g.parts.size();
            boolean first = true;
            for (String[] p : g.parts) {
                b.append("<tr>");
                if (first) {
                    b.append("<td rowspan=").append(rows).append(">").append(i).append(".</td>")
                     .append("<td rowspan=").append(rows).append("><a href=\"").append(Html.escAttr(domainApp + "/specimen.do?code=" + Html.url(g.base)))
                     .append("\">").append(Html.esc(g.base)).append("</a></td>");
                    first = false;
                }
                b.append("<td><a href=\"").append(Html.escAttr(domainApp + "/specimen.do?code=" + Html.url(p[0]))).append("\">")
                 .append(Html.esc(p[0])).append("</a></td><td>").append(Html.esc(p[1] == null ? "" : p[1])).append("</td></tr>");
            }
        }
        b.append("</table>");
        return b.toString();
    }
}
