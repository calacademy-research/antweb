package org.calacademy.antweb;

import java.io.IOException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import java.sql.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class SlideShowAction extends Action {

    private static Log s_log = LogFactory.getLog(SlideShowAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        boolean botAttackDefence = HttpUtil.isBotAttackDefence(request);
        if (botAttackDefence) {
          request.setAttribute("message", "Invalid request.");
          return (mapping.findForward("message"));
        }       

        HttpSession session = request.getSession();
        SlideShowForm slideShowForm = ((SlideShowForm) form);
        String rank = slideShowForm.getRank();
        String sessionRank = (String) session.getAttribute("rank");
        ArrayList slides = (ArrayList) session.getAttribute("slides");
        SlideShowSearch search = new SlideShowSearch();
        int index = 0;

        if (slideShowForm.getIndex() != null) {
            index = Integer.parseInt(request.getParameter("index"));
        }
        if ((rank == null) || (rank.length() == 0)) {
            rank = "genus";
        }

        // Added by Mark Jun 19, 2012  
        java.util.Hashtable labelMap = new java.util.Hashtable();
        labelMap.put("Head","h");
        labelMap.put("Profile","p");
        labelMap.put("Dorsal","d");
        labelMap.put("Ventral", "v");
        labelMap.put("Label","l");
        //String shot = (String) pageContext.getAttribute("shot");
        String shot = "Head";
        String shortHand = (String) labelMap.get(shot);

        Taxon taxon = null;
        java.sql.Connection connection = null;
        javax.sql.DataSource dataSource = getDataSource(request, "conPool");
        try {
            connection = DBUtil.getConnection(dataSource, "SlideShowAction.execute()");

            search.setRank(rank);
            search.setConnection(connection);

            // if there are no slides, or the rank has changed, get slides
            //
            if (!rank.equals(sessionRank) || (slides == null)) {
                slides = search.getSlides();
            }

            if (index >= slides.size()) {
                index = 0;
            }

            if (index < 0) {
                index = slides.size() - 1;
            }

            // now get the taxon.  SlideShowAction does a lot of work to get the taxon of the slideshow!  
            // Added by Mark Jun 19, 2012
            // If the taxon does not have the desired image, skip.
            while (taxon == null) {
                taxon = getNextTaxon(slides, index, connection, rank);
                if ((taxon.getImages() != null) && 
                    (taxon.getImages().containsKey(shortHand)) && 
                    (!taxon.getTaxonName().equals("myrmicinaeelectromyrmex")) && // These have mis-sized images
                    (!taxon.getTaxonName().equals("ponerinaeharpegnathos")) &&
                    (!taxon.getTaxonName().equals("myrmeciinaeprionomyrmex"))
                    //ecitoninae
                    ) {
                  continue;
                } else {
                  slides.remove(index);
                  --index;
                  taxon = null;
                }                
                
                //++index;
                //if (index > slides.size()) return (mapping.findForward("failure"));
            }  
        } catch (java.lang.IndexOutOfBoundsException e) {            
            s_log.warn("execute() index:" + index + " e: " + e);
            String message = "";  // We add no error message so that error is not evident.  "Slide Show Unavailable";
            request.setAttribute("message", message);
            return (mapping.findForward("justMessage"));            
        } catch (SQLException e) {
            s_log.warn("execute() e: " + e);
        } finally {
            DBUtil.close(connection, this, "SlideShowAction.execute()");
        }

        String indexString = Integer.toString(index);

        if ("request".equals(mapping.getScope())) {
            request.setAttribute("slides", slides);
            request.setAttribute("rank", rank);
            request.setAttribute("taxon", taxon);
            request.setAttribute("showTaxon", taxon);
            request.setAttribute("index", indexString);
        } else {
            session.setAttribute("slides", slides);
            session.setAttribute("rank", rank);
            session.setAttribute("taxon", taxon);
            session.setAttribute("showTaxon", taxon);
            session.setAttribute("index", indexString);
        }

        // Set a transactional control token to prevent double posting
        saveToken(request);

        if ((slides != null) && (slides.size() == 0)) {
            return (mapping.findForward("failure"));
        } else {
            return (mapping.findForward("success"));
        }
    }
    
    private Taxon getNextTaxon(ArrayList slides, int index, Connection connection, String rank) throws SQLException {
        Taxon taxon = Taxon.getTaxonOfRank(rank);
        taxon.setConnection(connection);
        if (index >= slides.size()) return taxon;
        //taxon.setName((String) slides.get(index));
        taxon.setGenus((String) slides.get(index));
        taxon.setTaxonomicInfo(); //project.getName()
        Project worldants = ProjectMgr.getProject(Project.WORLDANTS);
        taxon.setImages(worldants);
        String theParams = "genus=" + (String) slides.get(index) + "&rank=genus";
        taxon.setConnection(null);
        taxon.setBrowserParams(theParams);
        taxon.setMap(new Map(taxon, worldants, connection));
        return taxon;
    }
}
