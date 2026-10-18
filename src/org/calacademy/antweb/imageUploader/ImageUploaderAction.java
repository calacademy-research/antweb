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

    public void writeRecentImages(Connection connection) {
        int maxRecent = 5;
        String docBase = Utility.getDocRoot();

        int currItems = 0;
        String domainApp = AntwebProps.getSecureDomainApp();
        Statement stmt = null;
        ResultSet rset = null;
        try {
			String recentImagesList = getRecentImagesList(connection);

            docBase += "web/genInc";
            Utility.makeDirTree(docBase);
            
            // .html, not .jsp: a .jsp under the web root would be compiled and run by Tomcat.
            File outputFile = new File(docBase + "/recentImages_gen_inc.html");
            FileWriter outFile = new FileWriter(outputFile);

            stmt = DBUtil.getStatement(connection, "writeRecentImages()");

            // Can't use distinct because we want it order by a non-distinct column.
			String query = "select s.code, s.genus, s.species, s.subspecies from specimen s, image i " 
			+ " where s.code = i.image_of_id " 
			+ " and code in (" + recentImagesList + ")"
			+ " order by i.modified desc"
			;

            //A.log("writeRecentImages() query:" + query);

            rset = stmt.executeQuery(query);
            String code;
            String theGenus;
            String theSpecies;
            String theSubspecies;
            String lastValue = "";
            int homepageMaxRecent = 5;
            Formatter format = new Formatter();
            ArrayList<String> distinctTaxa = new ArrayList<>();
            while (rset.next() && currItems <= homepageMaxRecent) {
                code = rset.getString("code");
                theGenus = rset.getString("genus");
                theSpecies = rset.getString("species");
                theSubspecies = rset.getString("subspecies");
                String theTaxon = theGenus + " " + theSpecies + " " + theSubspecies;
                if (distinctTaxa.contains(theTaxon)) continue;
                distinctTaxa.add(theTaxon);
                if (code != null && !code.equals("") && !code.equals(lastValue)) {
                    currItems++;
                    lastValue = code;
                    outFile.write("<a class=\"uppercase\" href=\"" + domainApp 
                        + "/specimenImages.do?code=" + Html.url(code) + "\">" + Html.esc(code) + "</a>");
                    if (theGenus != null &&
                            theSpecies != null &&
                            theGenus.length() > 0 &&
                            theSpecies.length() > 0
                       ) {
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

						String taxonHrefStr =  ":  <a  href='" + url + "'>" 
							+ Html.esc(format.capitalizeFirstLetter(theGenus) + " " + theSpecies);
						if (hasSubspecies) {
						  taxonHrefStr += " " + Html.esc(theSubspecies);
						}
						taxonHrefStr += "</a>";
						outFile.write(taxonHrefStr);
						//A.log("writeRecentImages() taxonHrefStr:" + taxonHrefStr);
					}
					outFile.write("<br/>\n");
				}
			}
			outFile.close();
		} catch (Exception e) {
			s_log.error("writeRecentImages() e:" + e);
		} finally {
		    DBUtil.close(stmt, rset, "writeRecentImages()");
		}
	}

    private String getRecentImagesList(Connection connection) {
        String recentImagesList = "";
        Statement stmt = null;
        ResultSet rset = null;
		String query = "select image.image_of_id code from image order by upload_date desc limit 30";
		  // This will be likely to have five unique casents.
        try {
            stmt = DBUtil.getStatement(connection, "getRecentImagesList()");
            rset = stmt.executeQuery(query);
            String code;
            int i = 0;
            while (rset.next()) {
                code = rset.getString("code");
                if (i > 0) recentImagesList += ", ";
                ++i; 
                recentImagesList += "'" + code + "'";
            }
		} catch (Exception e) {
			s_log.error("getRecentImagesList() e:" + e);
		} finally {
		    DBUtil.close(stmt, rset, "getRecentImagesList()");
		}
        return recentImagesList;
	}
}
