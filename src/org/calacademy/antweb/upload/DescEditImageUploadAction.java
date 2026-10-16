package org.calacademy.antweb.upload;

import org.apache.struts.action.*;

import java.io.*;
import java.sql.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


public class DescEditImageUploadAction extends AntwebAction {

    //public static int IMG_WIDTH = 500;
    
    private static final Log s_log = LogFactory.getLog(DescEditImageUploadAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) {
        return doExecute(mapping, form, request, response);
    }

    private static final java.util.regex.Pattern SAFE_NAME = java.util.regex.Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,150}$");
    private static final java.util.Set<String> CURATOR_TYPES = java.util.Set.of(
        "jpg", "jpeg", "png", "gif", "tif", "tiff", "pdf", "txt", "csv", "tsv", "xls", "xlsx", "doc", "docx");
    private static final java.util.Set<String> NEVER_TYPES = java.util.Set.of(
        "jsp", "jspx", "jspf", "jsw", "jsv", "tag", "tagx", "war", "class", "jar", "sh", "properties", "xml");

    public static boolean isSafeDir(String dir) {
        if (dir == null) return false;
        if (dir.contains("..") || dir.contains("\\") || dir.contains(":") || dir.startsWith("/")) return false;
        return dir.matches("[A-Za-z0-9_/-]+");
    }

    public static boolean isSafeFileName(String name, boolean isAdmin) {
        if (name == null || !SAFE_NAME.matcher(name).matches() || name.contains("..")) return false;
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        if (NEVER_TYPES.contains(ext)) return false;
        return isAdmin || CURATOR_TYPES.contains(ext);
    }

    private ActionForward doExecute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) {
                   
        // make sure person is logged in first
        HttpSession session = request.getSession();

        ActionForward c = Check.curator(request, mapping); if (c != null) return c; 
        Login accessLogin = LoginMgr.getAccessLogin(request);

        String root = request.getSession().getServletContext().getRealPath("") + "/";
        Connection connection = null;
        String query;
        String forwardPage = "";


        String messageStr = "";
        String dbMethodName = DBUtil.getDbMethodName("DescEditImageUploadAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
          
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
                    boolean isAdminUser = LoginMgr.isAdmin(accessLogin);
                    // Curators may only write into their own curator folder.
                    if (!isAdminUser || dir == null || dir.isEmpty()) dir = "curator";
                    if (!isSafeDir(dir)) {
                      s_log.warn("execute() rejected dir:" + dir + " login:" + accessLogin.getId());
                      return mapping.findForward("permissionDenied");
                    }
                    String requestedName = theForm.getOutputFileName();
                    if (requestedName == null || requestedName.isEmpty()) requestedName = theForm.getTheFile2().getFileName();
                    if (!isSafeFileName(requestedName, isAdminUser)) {
                      request.setAttribute("message", "That file name or file type is not allowed. Use letters, digits, dot, dash or underscore, and an image, PDF, text or spreadsheet file.");
                      return mapping.findForward("message");
                    }
                    
//                    String docBase = request.getRealPath("/");
                    String docBase = AntwebProps.getDocRoot();

                    // Create the curator's directory if it does not already exist.
                    if (dir.equals("curator")) {
                      dir = "web/curator/" + accessLogin.getId();
                      Utility.makeDirTree(dir);
                    }
                    
                    String fileName = theForm.getTheFile2().getFileName();
                    String dirFileName = dir + "/" + fileName;
                    String outputFileName = theForm.getOutputFileName();

                    String outputFileName2 = docBase + dirFileName;
                    
                    if (outputFileName != null
                      && !outputFileName.equals("")) {
                        outputFileName2 = docBase + dir + "/" + outputFileName; 
                    }

                    Utility.backupFile(outputFileName2);
                    boolean isSuccess = Utility.copyFile(theForm.getTheFile2(), outputFileName2);
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
                    messageStr = "Your uploaded file is here: <a href=\"" + Html.escAttr(imageUrl) + "\">" + Html.esc(imageUrl) + "</a>";
                    String tag = "";

                    if ( fileName.contains("jpg")
                      || fileName.contains("jpeg")
                      || fileName.contains("JPG")
                      || fileName.contains("png")
                      || fileName.contains("PNG") ) {
                       tag = "<pre>&lt;img src=\"" + Html.esc(imageUrl) + "\" &gt;</pre>";
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
            return mapping.findForward("error");
        } finally {
            DBUtil.close(connection, this, dbMethodName);
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
