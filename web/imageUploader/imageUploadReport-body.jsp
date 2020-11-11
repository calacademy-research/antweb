<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="java.sql.Timestamp" %>
<%@ page import="java.net.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.imageUploader.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.home.*" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%
    ImageUpload imageUpload = (ImageUpload) request.getAttribute("imageUpload");
  	if (imageUpload == null) { %>
	  <br><br><b>Unable to find imageUpload.</b>	  
 <%   return;
    }

    Artist artist = ArtistMgr.getArtist(imageUpload.getArtistId());
    Group group = GroupMgr.getGroup(imageUpload.getGroupId());
    Curator curator = LoginMgr.getCurator(imageUpload.getCuratorId());
    if (curator == null) {
        AntwebUtil.log("Curator is null for curatorId:" + imageUpload.getCuratorId());
    }
%>


<div id="page_contents">

<div class="in_admin">

<br><a href='<%= AntwebProps.getDomainApp() %>/imageUploader.do'><< Back</a> to Image Uploader

<br><br>
<h2>Image Upload Report: <a href='<%= AntwebProps.getDomainApp() %>/imageUploadReport.do?id=<%= imageUpload.getId() %>'/><%= imageUpload.getId() %></a></h2>
</div>

<br><br>

<div id="page_data">

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Curator: </b></li>
        <li><%= curator.getLink() %> - <a href="<%= AntwebProps.getDomainApp() %>/listImageUploads.do?curatorId=<%= (curator.getId()) %>">[Reports]</a>
</li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Group: </b></li>
        <li><%= group.getLink() %> - <a href="<%= AntwebProps.getDomainApp() %>/listImageUploads.do?groupId=<%= (group.getId()) %>">[Reports]</a>
</li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Artist: </b></li>
        <li><%= artist.getLink() %></li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>License: </b></li>
        <li>Attribution-ShareAlike (BY-SA) Creative Commons License and GNU Free Documentation License (GFDL)</li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Copyright: </b></li>
        <li><%= imageUpload.getCopyright().getCopyright() %> </li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Created: </b></li>
        <li><%= imageUpload.getCreated() %></li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Upload Completed: </b></li>
        <li><%= imageUpload.getIsComplete() %></li>
        </ul>
    </div>
  
<%
    int totalImagesUploaded = 0;
    ArrayList<String> imageDirs = new ArrayList<String>();
    ArrayList<String> noImageDirs = new ArrayList<String>();
    String uniqueCasentSearch = "";
    for (ImageUploaded imageUploaded : imageUpload.getImages()) { 
        if (imageUploaded.getIsContinueUpload()) ++totalImagesUploaded;
        if (imageUploaded.getErrorMessage() != null) continue;
        String code = imageUploaded.getCode();        
        if (imageUploaded.getIsSpecimenDataExists()) {
          if (imageUploaded.getIsContinueUpload()) {
            if (!imageDirs.contains(code)) {
                uniqueCasentSearch += "+" + code;
                imageDirs.add(code);
            }

            //A.log("imageUploadReport-body.jsp imageUploaded:" + imageUploaded.getIsContinueUpload() + imageUploaded.getErrorMessage());
          }
        } else {
            if (!noImageDirs.contains(code)) {
                noImageDirs.add(code);
            }        
        }
    }         
%>   
 
    <div id="overview_data" class="plain">
        <ul>
        <li><b>Uploaded Images Search: </b></li>
        <li><a href='<%= AntwebProps.getDomainApp() %>/advancedSearch.do?searchMethod=advancedSearch&advanced=true&isIgnoreInsufficientCriteria=true&sortBy=name&specimenCodeSearchType=contains&specimenCode=<%= uniqueCasentSearch %>'>here</a></li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Recently Added Images: </b></li>
        <li><a href='<%= AntwebProps.getDomainApp() %>/recentSearchResults.do?searchMethod=recentImageSearch&daysAgo=30'>here</a></li>
        </ul>
    </div>
    
    <div id="overview_data" class="plain">
        <ul>
        <li><b>Total Images Uploaded: </b></li>
        <li><%= totalImagesUploaded %></li>
        </ul>
    </div>
    
<div class="clear"></div>
<br>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Images:</b></li>
        <li><%= imageUpload.getImageCount() %></li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
<%  
    //HashSet<String> imageDirs = new HashSet<String>();
    
    boolean backedUpFiles = false;    
    
    int i = 0; 
    for (ImageUploaded imageUploaded : imageUpload.getImages()) { 
      ++i;
      
      String flag = "";
      if (imageUploaded.getIsReUploaded()) {
        backedUpFiles = true;
//        flag = " <a title='Image already existed. It has been moved to the backup directory. See below.'><img width='12' src='" + AntwebProps.getDomainApp() + "/image/redICircle.png'></a>";
        flag = " <a href='" + AntwebProps.getDomainApp() + "/images/backup/" + imageUploaded.getFileName() + "' title='Image already existed. It has been moved to the duplicates directory. See below.'><font color=red>Duplicate</font></a>";
      }
      if (!imageUploaded.getIsSpecimenDataExists()) {
        flag = " <a title='Specimen data does not exist for this image. Upload data and then re-upload images.'><img width='10' src='" + AntwebProps.getDomainApp() + "/image/redX.png'></a>";
      }
      if (imageUploaded.getErrorMessage() != null) {
A.log("imagerUploadReport-body.jsp fileName:" + imageUploaded.getFileName() + " errorMessage:" + imageUploaded.getErrorMessage() + " flag:" + flag);      
        flag = " <font color=red>" + imageUploaded.getErrorMessage() + "</font>";
      }
      String specimenLink = ""; //imageUploaded.getDisplayName();
      String tifLink = imageUploaded.getFileName();
      if (imageUploaded.getIsContinueUpload()) {
        specimenLink = "<a href='" + AntwebProps.getDomainApp() + "/bigPicture.do?code=" + imageUploaded.getCode() + "&shot=" + imageUploaded.getShot() + "&number=" + imageUploaded.getNumber() + "'><img src='" + AntwebProps.getDomainApp() + "/image/upRight.png' width=13></a>";
        tifLink = "<a href='" + AntwebProps.getDomainApp() + "/images/" + imageUploaded.getCode() + "/" + imageUploaded.getFileName() + "'>" + imageUploaded.getFileName() + "</a>";
      }

      %>
      <ul>
        <li><b><%= i %>:</b></li>
        <li><%= tifLink %> <%= specimenLink %> <%= flag %></li>        
      </ul>
 <% } %>
        </ul>
    </div>

<div class="clear"></div>
<br>

<% if (imageDirs.size() > 0) { %>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Uploaded Specimen Set: </b></li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
<%        
   i = 0;
   for (String imgDir : imageDirs) { 
     ++i;
   %>
    <ul>
        <li><b><%= i %>: </b></li>
        <li><a href='<%= AntwebProps.getDomainApp() %>/images/<%= imgDir %>/'><%= imgDir %></a></li>        
    </ul>
<% } %>
        </li>
        </ul>
    </div>

<% } %>        

<div class="clear"></div>
<br>
<% if (noImageDirs.size() > 0) { %>

    <div id="overview_data" class="plain">
        <ul>
        <li><b><a title='Specimen data does not exist for this image. Upload data and then re-upload images.'>
          <img width='12' src='<%= AntwebProps.getDomainApp() %>/image/redX.png'></a> No Specimen Data Set: </b></li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
<%        
   i = 0;
   for (String imgDir : noImageDirs) { 
     ++i;
   %>
    <ul>
        <li><b><%= i %>: </b></li>
        <li><%= imgDir %></li>        
    </ul>
<% } %>
        </li>
        </ul>
    </div>
<% } %>    
        
    
<div class="clear"></div>
<br>

<% if (backedUpFiles) { %>

    <div id="overview_data" class="plain">
        <ul>
        <li><font color=red>Duplicate</font> <b>Images Dir</b></li>
        <li><a href='<%= AntwebProps.getDomainApp() %>/images/backup/' title='Temporary home of copies of images that were pre-existing before recently uploaded images.'>here</a></li>
        </ul>
    </div>

<% } %>    
    
</div>    

</div>
