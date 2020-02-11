package org.calacademy.antweb.imageUploader;

import javax.servlet.ServletException;
import javax.servlet.http.*;
import org.apache.struts.action.*;
import java.util.*;

import java.sql.*;

import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.*;
import org.apache.commons.fileupload.servlet.*;
import org.apache.commons.io.output.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class ImageUploaderAction extends Action {

    private static Log s_log = LogFactory.getLog(ImageUploaderAction.class);

    private static final int MAX_FILE_SIZE      = 1024 * 1024 * 199; // 199MB
    private static final int MAX_REQUEST_SIZE   = 1024 * 1024 * 200; // 200MB

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward a = Check.init(request, mapping); if (a != null) return a;
        ActionForward b = Check.busy(getDataSource(request, "conPool"), request, mapping); if (b != null) return b; 
        ActionForward c = Check.login(request, mapping); if (c != null) return c;
        ActionForward d = Check.valid(request, mapping); if (d != null) return d; 
     
        //ActionForward e = Check.admin(request, mapping); if (e != null) return e;     
     
        Login accessLogin = LoginMgr.getAccessLogin(request);
        Group accessGroup = GroupMgr.getAccessGroup(request);
    
        HttpSession session = request.getSession();
        
        HttpUtil.setUtf8(request, response);

        String message = "";
                
        Connection connection = null;
        try {
            javax.sql.DataSource dataSource = getDataSource(request, "longConPool");
            connection = DBUtil.getConnection(dataSource, "ImageUploaderAction.execute()");        

            String action = request.getParameter("action");
            if ("regenerate".equals(action)) {
              writeRecentImages(connection);
              message = "Recent Images page regenerated";
              request.setAttribute("message", message);
              return (mapping.findForward("message"));
            }

            String contentType = request.getContentType(); // Verify the content type
            //A.log("execute() contentType:" + contentType);        
            if (contentType != null && contentType.indexOf("multipart/form-data") >= 0) {

                String artist = null;
                String license = "Attribution-ShareAlike (BY-SA) Creative Commons License and GNU Free Documentation License (GFDL)";
                String group = null;
            
                ImageUpload imageUpload = null;

                UploadAction.setIsInUploadProcess(accessLogin.getName() + ":" + accessGroup.getName());

                FileUtil.makeDir(ImageUploaded.tempDir);  
                // data/antweb/web/temp/
                // was: /var/www/html/imageUpload/toUpload

                ImageUploadDb imageUploadDb = new ImageUploadDb(connection);        
                ImageDb imageDb = new ImageDb(connection);
                SpecimenDb specimenDb = new SpecimenDb(connection);
   
                DiskFileItemFactory factory = new DiskFileItemFactory();
                //factory.setSizeThreshold(MAX_REQUEST_SIZE); // maximum size that will be stored in memory      
                factory.setRepository(new File(ImageUploaded.tempDir)); // Location to save data that is larger than maxMemSize.
  
                ServletFileUpload upload = new ServletFileUpload(factory); // Create a new file upload handler                            
                //upload.setSizeMax(MAX_REQUEST_SIZE); // maximum file size to be uploaded.

                ArrayList<ImageUploaded> images = new ArrayList<ImageUploaded>();
          
                List<FileItem> fileItems = upload.parseRequest(request); // Parse the request to get file items.

                if (fileItems.size() == 0) {
                   request.setAttribute("message", "No files uploaded.");
                   return (mapping.findForward("message"));                    
                }
                // The files and other items come mixed together. Handle all, process later.
                for (FileItem fileItem : fileItems) {
                    if (!fileItem.isFormField()) {
                        ImageUploaded imageUploaded = new ImageUploaded();
                        imageUploaded.init(fileItem.getName()); // parse
                        boolean specimenDataExists = specimenDb.exists(imageUploaded.getCode());
                        imageUploaded.setIsSpecimenDataExists(specimenDataExists); // verify specimen existence.
                        imageUploaded.setFileItem(fileItem); // write to disk.
                        images.add(imageUploaded);
                    } else {
                        if (fileItem.getFieldName().equals("artist")) artist = fileItem.getString();
                        if (fileItem.getFieldName().equals("group")) group = fileItem.getString();
                    }
                }

                imageUpload = new ImageUpload();
                imageUpload.setCuratorId(accessLogin.getId());
                imageUpload.setGroupId(accessGroup.getId());
                if (artist != null) {
                  imageUpload.setArtistId(new Integer(artist).intValue());
                }
                imageUpload.setCreated(new java.util.Date());
                Copyright copyright = (new CopyrightDb(connection)).getCurrentCopyright();
                imageUpload.setCopyright(copyright);
                imageUpload.setLicense(ImageUpload.LICENSE);
                imageUpload.setImages(images);
                imageUpload.setImageCount(images.size());
                imageUpload = imageUploadDb.saveImageUpload(imageUpload); // This will set the ID.

                for (ImageUploaded imageUploaded : imageUpload.getImages()) {
                    imageUploaded.setImageUpload(imageUpload);
                    imageUploadDb.saveImageUploaded(imageUploaded);
                    if (imageUploaded.getIsContinueUpload()) {                    
                      String imagesMessage = imageUploaded.genImages(); // Create derivatives. persist.
                      imageDb.putImage(imageUploaded); // Create image.
                    }
                }
                
                imageUploadDb.completeImageUpload(imageUpload); // Flag as finished.
                                
                writeRecentImages(connection);
                
                request.setAttribute("imageUpload", imageUpload);
                A.log("go to report");    
                return (mapping.findForward("report"));           

            } else {        
                A.log("go to imageUploader");    
                return (mapping.findForward("imageUploader"));         
            }
        } catch (Exception ex) {
          AntwebUtil.logStackTrace(ex);
          AntwebUtil.log("ImageUploaderAction.execute() e:" + ex);
          message = "Error uploading images. ex:" + ex.toString();
          AdminAlertMgr.add(message, connection);
          request.setAttribute("message", message);
          return (mapping.findForward("message")); 
        } finally {
          DBUtil.close(connection, this, "ImageUploaderAction.execute()");
          UploadAction.setIsInUploadProcess(null);          
        }    
	}	

    private void writeRecentImages(java.sql.Connection connection) {
        int maxRecent = 5;
        Utility util = new Utility();
        String docBase = util.getDocRoot();

        int currItems = 0;
        String domainApp = AntwebProps.getSecureDomainApp();
        Statement stmt = null;
        ResultSet rset = null;
        try {
			String recentImagesList = getRecentImagesList(connection);

            docBase += "web/genInc";
            (new Utility()).makeDirTree(docBase);
            
            File outputFile = new File(docBase + "/recentImages_gen_inc.jsp");
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
            String theRank = "species";
            String lastValue = "";
            int homepageMaxRecent = 5;
            Formatter format = new Formatter();
            ArrayList<String> distinctTaxa = new ArrayList<String>();
            while (rset.next() && (currItems <= homepageMaxRecent)) {
                code = rset.getString("code");
                theGenus = rset.getString("genus");
                theSpecies = rset.getString("species");
                theSubspecies = rset.getString("subspecies");
                String theTaxon = theGenus + " " + theSpecies + " " + theSubspecies;
                if (distinctTaxa.contains(theTaxon)) continue;
                distinctTaxa.add(theTaxon);
                if ((code != null) && (!code.equals("")) && (!code.equals(lastValue))) {
                    currItems++;
                    lastValue = code;
                    outFile.write("<a class=\"uppercase\" href=\"" + domainApp 
                        + "/specimenImages.do?code=" + code + "\">" + code + "</a>");
                    if ((theGenus != null) && 
                        (theSpecies != null) &&
                        (theGenus.length() > 0) && 
                        (theSpecies.length() > 0)
                       ) {
						String url = domainApp + "/images.do?" 
						  + "genus=" + theGenus 
						  + "&species=" + theSpecies;
						if (theSubspecies != null) {
						  url += "&subspecies=" + theSubspecies; 
						  theRank = Rank.SUBSPECIES;
						}
						url += "&rank=" + theRank;

						String taxonHrefStr =  ":  <a  href='" + url + "'>" 
							+ format.capitalizeFirstLetter(theGenus) + " " + theSpecies;
						if (theSubspecies != null) {
						  taxonHrefStr += " " + theSubspecies;
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

    private String getRecentImagesList(java.sql.Connection connection) {
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
