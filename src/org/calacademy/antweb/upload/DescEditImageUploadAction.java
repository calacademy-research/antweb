package org.calacademy.antweb.upload;

import org.apache.struts.action.*;

import java.io.*;
import java.sql.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


public class DescEditImageUploadAction extends Action {

    //public static int IMG_WIDTH = 500;
    
    private static Log s_log = LogFactory.getLog(DescEditImageUploadAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) {
                   
        // make sure person is logged in first
        HttpSession session = request.getSession();

        ActionForward c = Check.login(request, mapping); if (c != null) return c; 
        Login accessLogin = LoginMgr.getAccessLogin(request);

        String root = request.getSession().getServletContext().getRealPath("") + "/";
        Connection connection = null;
        String query;
        String forwardPage = "";
        
        
        Utility util = new Utility();

        String messageStr = "";
        
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "DescEditImageUploadAction");
          
            connection.setAutoCommit(true);

            String outputFileDir = AntwebProps.getWorkingDir();

            if (form instanceof DescEditImageUploadForm) {
                DescEditImageUploadForm theForm = (DescEditImageUploadForm) form;

                String objectType = "taxon";
                if (theForm.getTarget() != null && ( 
                     theForm.getTarget().contains("project.do") 
                  || theForm.getTarget().contains("adm1.do") 
                  || theForm.getTarget().contains("country.do") 
                  || theForm.getTarget().contains("region.do") 
                  || theForm.getTarget().contains("subregion.do") 
                  || theForm.getTarget().contains("museum.do") 
                   ) 
                ) {
                  objectType = "overview";
                }

                s_log.debug("execute() outputFileDir:" + outputFileDir + " objectType:" + objectType + " editField:" + theForm.getEditField());

                if (theForm.getTheFile2() != null) {
                  // Upload a File to Folder:

                  String theFileName = theForm.getTheFile2().getFileName(); 
                  //logFileName = theFileName;
                  if (!theFileName.equals("")) {
                    String dir = theForm.getHomePageDirectory();
                    
//                    String docBase = request.getRealPath("/");
                    String docBase = AntwebProps.getDocRoot();

                    // Create the curator's directory if it does not already exist.
                    if (dir.equals("curator")) {
                      dir = "web/curator/" + accessLogin.getId();
                      util.makeDirTree(dir);
                    }
                    
                    String fileName = theForm.getTheFile2().getFileName();
                    String dirFileName = dir + "/" + fileName;
                    String outputFileName = theForm.getOutputFileName();

                    String outputFileName2 = docBase + dirFileName;
                    
                    if ((outputFileName != null) 
                      && (!outputFileName.equals(""))) {
                        outputFileName2 = docBase + dir + "/" + outputFileName; 
                    }

                    util.backupFile(outputFileName2);
                    boolean isSuccess = util.copyFile(theForm.getTheFile2(), outputFileName2);
                    if (!isSuccess) {
                      s_log.warn("execute() copyFile failure");
                      return mapping.findForward("error");
                    }

                    String logMessage = "execute() upload file" 
                      + " dir:" + dir
                      + " docBase:" + docBase
                      + " fileName:" + fileName
                      + " dirFileName:" + dirFileName 
                      + " outputFileName:" + outputFileName
                      + " outputFileName2:" + outputFileName2;
                    s_log.debug(logMessage);
 
                    String imageUrl = AntwebProps.getDomainApp() + "/" + dirFileName;
                    messageStr = "Your uploaded file is here: <a href=\"" + imageUrl + "\">" + imageUrl + "</a>";
                    String tag = "";

                    if ( fileName.contains("jpg")
                      || fileName.contains("jpeg")
                      || fileName.contains("JPG")
                      || fileName.contains("png")
                      || fileName.contains("PNG") ) {
                       tag = "<pre>&lt;img src=\"" + imageUrl + "\" &gt;</pre>";
                       messageStr += "<br><br>You may embed this image in a web page with this html tag: " + tag;
                    }
                    
                    s_log.debug("execute() done importing objectType:" + objectType + " file:" + outputFileName2 + " tag:" + tag);
                    request.setAttribute("imageUrl", imageUrl);
                    request.setAttribute("descEditImageUploadForm", theForm);
                    if ("taxon".equals(objectType)) {
                      return mapping.findForward("taxonSuccess");   
                    } 
                    if ("overview".equals(objectType)) {
                      return mapping.findForward("overviewSuccess");   
                    }
                    
                    s_log.warn("execute() objectType not found:" + objectType);
                    return mapping.findForward("error");
                    
                  }  
                }    

                messageStr = "You must select an image to be uploaded";
                request.setAttribute("message", messageStr);
                return mapping.findForward("message");  

            }
        } catch (IOException | SQLException e) {
            s_log.error("execute() e:" + e);
            return (mapping.findForward("error"));
        } finally {
            DBUtil.close(connection, this, "DescEditImageUploadAction");
        }

        //this shouldn't happen in this example
        s_log.error("execute() returning null");
        return null;
    }

}



/* This is what the form looks like embedded in the taxon page.  The page that this class returns should mimic.

<form name="browseForm" method="POST" action="/antweb/description.do?genus=gracilidris&name=&rank=species" enctype="multipart/form-data">
<input type="hidden" name="org.apache.struts.taglib.html.TOKEN" value="29bd146eb30ac0daf9b7654aefde6086">
<input type="hidden" name="editField" value="images">
<input type="hidden" name="isSaveEditField" value="true">
<input type="hidden" name="rank" value="species">
<input type="hidden" name="genus" value="gracilidris">
<input type="hidden" name="name" value="">

<textarea rows="20" cols="80" name="contents" id="contents">
Add your content here.
</textarea>

<input border="0" type="image" src="images/orange_done.gif" width="98" height="36" value="Save">
<a href="description.do?genus=gracilidris&name=&rank=species"><img border="0" src="images/grey_cancel.gif" width="123" height="36"></a>
</form>
*/
