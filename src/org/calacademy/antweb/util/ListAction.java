package org.calacademy.antweb.util;

import java.io.IOException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import org.apache.struts.action.*;
import java.sql.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class ListAction extends Action {

    private static Log s_log = LogFactory.getLog(ListAction.class);

    public ActionForward execute(
        ActionMapping mapping,
        ActionForm form,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException, ServletException {

        boolean botAttackDefence = HttpUtil.isBotAttackDefence(request);
        if (botAttackDefence) {
          request.setAttribute("message", "Invalid request.");
          return (mapping.findForward("message"));
        }       

        // Extract attributes we will need
        Locale locale = getLocale(request);
        HttpSession session = request.getSession();
        
        DynaActionForm df = (DynaActionForm) form;
        String action = (String) df.get("action");        
        
        
        Connection connection = null;        
        try {
          javax.sql.DataSource dataSource = getDataSource(request, "conPool");
          connection = DBUtil.getConnection(dataSource, "ListAction");
          
          //dataSource.setZeroDateTimeBehavior("convertToNull");


          // s_log.warn("execute action:" + action);
            switch (action) {
                case "countries": {
                    String message = "Antweb Valid Country List";
                    request.setAttribute("message", message);
                    ArrayList<String> list = GeolocaleMgr.getValidCountryList();
                    request.setAttribute("list", list);
                    break;
                }
                case "bioregions": {
                    String message = "Antweb Valid Biogeographic Region List";
                    request.setAttribute("message", message);
                    ArrayList list = BioregionMgr.getList(connection);
                    request.setAttribute("list", list);
                    break;
                }
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

                    return (mapping.findForward("likes"));
                }
                case "toUpload": {
                    String toUploadDir = AntwebProps.getProp("site.toUpload");
                    String message = (new AntwebSystem()).launchProcess("ls -al " + toUploadDir, true);
                    request.setAttribute("message", message);
                    return (mapping.findForward("message"));
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
                    String message = "Taxon Names matching:" + taxonName;
                    request.setAttribute("message", message);
                    TaxonDb taxonDb = new TaxonDb(connection);
                    List<String> list = taxonDb.getTaxonNames(taxonName, true);
                    //A.log("ListAction listOfLists:" + listOfLists);
                    request.setAttribute("listTable", list);
                    return (mapping.findForward("listTable"));
                }
                default:


                    if (LoginMgr.isAdmin(request)) {
                        if (action.equals("usrAdm")) {
                            String message = "Antweb user Admin List";
                            request.setAttribute("message", message);
                            ArrayList<String> list = LoginDb.getUsrAdmList(connection);
                            //A.log("ListAction.execute() accessGroup:" + accessGroup + " isAdmin:" + accessGroup.isAdmin() + " size:" + list.size());
                            request.setAttribute("list", list);
                            return (mapping.findForward("list"));
                        }
                        if (action.equals("usrAdmLastLogin")) {
                            String message = "Antweb Last Login List";
                            request.setAttribute("message", message);
                            ArrayList<String> list = LoginDb.getUsrAdmLastLoginList(connection);
                            request.setAttribute("list", list);
                            return (mapping.findForward("list"));
                        }
                        if (action.equals("casentDAnamalies")) {
                            String message = "Multiple taxa for single specimen";
                            request.setAttribute("message", message);
                            SpecimenDb specimenDb = new SpecimenDb(connection);
                            ArrayList<String> list = specimenDb.getCasentDAnamalies();
                            request.setAttribute("listTable", list);
                            return (mapping.findForward("listTable"));
                        }

                        if (action.equals("recentCASPinnedPonerinae")) {
                            SpecimenDb specimenDb = new SpecimenDb(connection);
                            QueryReport queryReport = specimenDb.getRecentCASPinnedPonerinaeQueryReport();
                            request.setAttribute("queryReport", queryReport);
                            return (mapping.findForward("queryReport"));
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
                                return (mapping.findForward("message"));
                            }
                            int groupId = (Integer) o;
                            ArrayList<String> list = specimenDb.getIntroducedByGroup(groupId);
                            //A.log("ListAction list:" + list);
                            request.setAttribute("listTable", list);
                            return (mapping.findForward("listTable"));
                        }

                        if (action.equals("specimensWithMorphoGenera")) {
                            String message = "Specimen with Morpho Genera";
                            request.setAttribute("message", message);
                            SpecimenDb specimenDb = new SpecimenDb(connection);
                            int groupId = (Integer) df.get("groupId");
                            ArrayList<String> list = specimenDb.getSpecimensWithMorphoGenera(groupId);
                            A.log("ListAction list:" + list);
                            request.setAttribute("listTable", list);
                            return (mapping.findForward("listTable"));
                        }

                        if (action.equals("multiBioregionTaxaList")) {
                            int groupId = (Integer) df.get("groupId");
                            String message = "Non-introduced taxa in multiple bioregions for groupId:" + groupId;
                            request.setAttribute("message", message);
                            SpecimenDb specimenDb = new SpecimenDb(connection);
                            ArrayList<ArrayList<String>> listOfLists = specimenDb.getMultiBioregionTaxaList(groupId);
                            //A.log("ListAction listOfLists:" + listOfLists);
                            request.setAttribute("listOfLists", listOfLists);
                            return (mapping.findForward("listOfLists"));
                        }
                    }

                    request.setAttribute("message", "action:" + action + " not found.");
                    return (mapping.findForward("message"));

            }

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return (mapping.findForward("error"));
        } finally { 		
            DBUtil.close(connection, this, "ListAction");
        }             
        
        return (mapping.findForward("success"));
    }
}
