package org.calacademy.antweb.imageUploader;

import org.apache.struts.action.*;

import java.sql.*;

import java.io.*;
import java.util.*;
import java.util.Date;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.calacademy.antweb.*;
import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class ImageUploaderAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(ImageUploaderAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward a = Check.init(request, mapping); if (a != null) return a;
        //ActionForward b = Check.busy(getDataSource(request, "conPool"), request, mapping); if (b != null) return b;
        ActionForward c = Check.curator(request, mapping); if (c != null) return c;
        ActionForward d = Check.valid(request, mapping); if (d != null) return d;

        Login accessLogin = LoginMgr.getAccessLogin(request);
        Group accessGroup = GroupMgr.getAccessGroup(request);

        // The page itself checks this too (imageUploader.jsp).
        if (accessGroup == null || !(Boolean.TRUE.equals(accessLogin.isUploadImages()) || accessLogin.isAdmin())) {
            request.setAttribute("message", "This login may not upload images.");
            return mapping.findForward("message");
        }

        HttpUtil.setUtf8(request, response);

        // Images are uploaded by the page's JavaScript through imageUploadBatch.do, one file per
        // request. The old one-request form post (without JavaScript) was removed in 2026-10.
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("multipart/form-data")) {
            request.setAttribute("message", NEEDS_JAVASCRIPT);
            return mapping.findForward("message");
        }

        if ("regenerate".equals(request.getParameter("action"))) {
            Connection connection = null;
            String dbMethodName = DBUtil.getDbMethodName("ImageUploaderAction.execute()");
            try {
                DataSource dataSource = getDataSource(request, "conPool");
                connection = DBUtil.getConnection(dataSource, dbMethodName);
                writeRecentImages(connection);
                request.setAttribute("message", "Recent Images page regenerated");
                return mapping.findForward("message");
            } catch (Exception e) {
                s_log.warn("execute() regenerate e:" + e);
                request.setAttribute("message", "Could not regenerate the Recent Images page.");
                return mapping.findForward("message");
            } finally {
                DBUtil.close(connection, this, dbMethodName);
            }
        }

        return mapping.findForward("imageUploader");
	}

    /** Shown when the page is used without JavaScript (imageUploader-body.jsp has the same line). */
    public static final String NEEDS_JAVASCRIPT = "The Image Uploader needs JavaScript. Please enable it in your browser.";

    /**
     * The newest images' specimens, one line per name (at most 5), for the home page (web/genInc/recentImages_gen_inc.html).
     * The list is built first and written to a temporary file that then replaces the include, so an error leaves the
     * old list in place (before, the file was emptied first and an error left the home page section blank).
     */
    public void writeRecentImages(Connection connection) {
        final int homepageMaxRecent = 5;
        String domainApp = AntwebProps.getSecureDomainApp();
        // The specimens of the 30 newest images (likely five names), in one query: no codes are pasted into SQL.
        String query = "select s.code, s.genus, s.species, s.subspecies from specimen s, image i"
            + " where s.code = i.image_of_id"
            + " and s.code in (select image_of_id from (select image_of_id from image order by upload_date desc limit 30) recent)"
            + " order by i.modified desc";
        StringBuilder out = new StringBuilder();
        int currItems = 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rset = stmt.executeQuery(query)) {
            String lastValue = "";
            Formatter format = new Formatter();
            ArrayList<String> distinctTaxa = new ArrayList<>();
            while (rset.next() && currItems < homepageMaxRecent) {
                String code = rset.getString("code");
                String theGenus = rset.getString("genus");
                String theSpecies = rset.getString("species");
                String theSubspecies = rset.getString("subspecies");
                String theTaxon = theGenus + " " + theSpecies + " " + theSubspecies;
                if (distinctTaxa.contains(theTaxon)) continue;
                distinctTaxa.add(theTaxon);
                if (code != null && !code.equals("") && !code.equals(lastValue)) {
                    currItems++;
                    lastValue = code;
                    out.append("<a class=\"uppercase\" href=\"" + domainApp
                        + "/specimenImages.do?code=" + Html.url(code) + "\">" + Html.esc(code) + "</a>");
                    if (theGenus != null && theSpecies != null && theGenus.length() > 0 && theSpecies.length() > 0) {
                        String url = domainApp + "/images.do?"
                          + "genus=" + Html.url(theGenus)
                          + "&amp;species=" + Html.url(theSpecies);
                        // Decide the rank per row; it used to stay "subspecies" for every
                        // row after the first subspecies in the list.
                        boolean hasSubspecies = theSubspecies != null && theSubspecies.length() > 0;
                        String theRank = Rank.SPECIES;
                        if (hasSubspecies) {
                          url += "&amp;subspecies=" + Html.url(theSubspecies);
                          theRank = Rank.SUBSPECIES;
                        }
                        url += "&amp;rank=" + theRank;

                        String taxonHrefStr = ":  <a  href='" + url + "'>"
                            + Html.esc(format.capitalizeFirstLetter(theGenus) + " " + theSpecies);
                        if (hasSubspecies) {
                          taxonHrefStr += " " + Html.esc(theSubspecies);
                        }
                        taxonHrefStr += "</a>";
                        out.append(taxonHrefStr);
                    }
                    out.append("<br/>\n");
                }
            }
        } catch (SQLException e) {
            s_log.error("writeRecentImages() e:" + e);
            return;
        }
        if (currItems == 0) {
            s_log.warn("writeRecentImages() no recent images found; the list is left as it was");
            return;
        }
        try {
            String dir = Utility.getDocRoot() + "web/genInc";
            Utility.makeDirTree(dir);
            // .html, not .jsp: a .jsp under the web root would be compiled and run by Tomcat.
            java.nio.file.Path target = java.nio.file.Path.of(dir, "recentImages_gen_inc.html");
            java.nio.file.Path temp = java.nio.file.Path.of(dir, "recentImages_gen_inc.html." + Thread.currentThread().threadId() + ".tmp");
            try {
                java.nio.file.Files.writeString(temp, out.toString(), java.nio.charset.StandardCharsets.UTF_8);
                try {
                    java.nio.file.Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    java.nio.file.Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                java.nio.file.Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            s_log.error("writeRecentImages() write e:" + e);
        }
    }
}
