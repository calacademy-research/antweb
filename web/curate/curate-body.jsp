<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.upload.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.AntwebProps" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@include file="/curate/curatorCheck.jsp" %>

<% if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) {
  AntwebUtil.log("curate-body.jsp static call:" + HttpUtil.getTarget(request));
  return;
}
%>

<%
    String domainApp = AntwebProps.getDomainApp();
    String secureDomainApp = AntwebProps.getSecureDomainApp();
	
	Login accessLogin = LoginMgr.getAccessLogin(request);
    Group accessGroup = GroupMgr.getAccessGroup(request);
    
	ArrayList<SpeciesListable> projList = accessLogin.getProjects();	
    ArrayList<SpeciesListable> speciesListList = accessLogin.getSpeciesListList();
    
    session.removeAttribute("ancFile");

    String active = "";
    Boolean isServerBusy = DBUtil.isServerOverMaxBusy();
    if (isServerBusy) {
      active = " disabled ";
    }    
%>

Need Help? Check out the <a href="<%= domainApp %>/documentation.do" target="new">Curator Tool Documentation</a> | <a href="<%= domainApp %>/curate.do">Refresh</a>
<div class="admin_left">

<html:messages id="message" message="true">
<font color="red"><b><bean:write name="message"/></b></font><br>
</html:messages>

<!-- Curator Information -->
    <div class="admin_action_module">
        <div class="admin_action_item">
            <div style="float:left;">
              <h2>Curator Information</h2>
            </div>
            <div class="clear"></div>
        </div>
        <div class="admin_action_item">
          <div class="action_hint">
            &nbsp;&nbsp;&nbsp;Your <a href="<%= secureDomainApp %>/curator.do?id=<%= (accessLogin.getId()) %>">Curator Page</a>
          </div>
          <div class="clear"></div>
        </div>

        <div class="admin_action_item">
          <div class="action_hint">
            &nbsp;&nbsp;&nbsp;Manage Your <a href="<%= secureDomainApp %>/viewLogin.do?id=<%= (accessLogin.getId()) %>">Login Information</a>
          </div>
          <div class="clear"></div>
        </div>
        <div class="admin_action_item">
          <div class="action_hint">
            &nbsp;&nbsp;&nbsp;View Your <a href="<%= domainApp %>/listSpecimenUploads.do?groupId=<%= accessGroup.getId() %>">Specimen Upload Reports</a>

            &nbsp;&nbsp;&nbsp;View Your <a href="<%= domainApp %>/listImageUploads.do?groupId=<%= accessGroup.getId() %>">Image Upload Reports</a>

<% if (accessLogin.isAdmin()) { %>
             &nbsp;&nbsp;&nbsp;View <a href="<%= domainApp %>/query.do?name=worldantsUploads">Worldants</a> uploads.       
<% } %>


          </div>
          <div class="clear"></div>
        </div>
        
<% if (accessLogin.isAdmin()) { %>
        <div class="admin_action_item">
          <div class="action_hint">
            <% String curatorDir = "/web/curator/" + (accessLogin.getId());
               String fullCuratorDir = domainApp + curatorDir; %>
            &nbsp;&nbsp;&nbsp;(Admin only) View files in your curator dir: <a href="<%= fullCuratorDir %>/"><%= curatorDir %></a>.
          </div>
          <div class="clear"></div>
        </div>
<% } %>
    </div>


<div>
<% // This warning is to prevent developers (me) from accidentally posting data to the live site.
   if (!AntwebProps.isDevOrStageMode()) {
     if (LoginMgr.isDeveloper(accessLogin)) {
       %><h2><font color=red>Warning: Live Site</font></h2><% 
     }
   }
   //A.log("curate-body.jsp hasServerMessage:" + AntwebMgr.hasServerMessage() + " : " + AntwebMgr.getServerMessage());
   if (AntwebMgr.hasServerMessage()) {
     out.println(AntwebMgr.getServerMessage());
   }
 %>
</div>



<!-- Specimen Data -->

<div class="admin_action_module">

<%
    if (accessLogin.isUploadSpecimens()) {

        // HERE is how we can take down the upload services, should we want... Set takeDownUpload to true.
        boolean takeDownUpload = false;
        //takeDownUpload = !(LoginMgr.isAdmin(accessLogin) || accessGroup.getId() == 31 );
        String curatorNote = "";
        String adminNote = "";
        if (takeDownUpload) {
          curatorNote = "<b><font color=blue>Upload services temporarily restricted</font></b>";
          adminNote = "<b><font color=blue>Upload services temporarily restricted for others</font></b>";
          if (LoginMgr.isAdmin(accessLogin)) {
            out.println(adminNote);
          } else {
            out.println(curatorNote);
          }
        }
        if (!takeDownUpload || LoginMgr.isAdmin(accessLogin)) { %>

       <html:form method="POST" action="upload.do" enctype="multipart/form-data">
         <input type="hidden" name="ancFileDirectory" value="none" />
         <input type="hidden" name="action" value="specimenUpload" />
         <input type="hidden" name="updateAdvanced" value="no" />
         <input type="hidden" name="updateFieldGuide" value="none" />
         <input type="hidden" name="images" value="no" />
         <input type="hidden" name="outputFileName" value="" />
         <input type="hidden" name="successkey" value="null" />
         <input type="hidden" name="updateAdvanced" value="yes" />

             <div class="admin_action_item">
                 <div style="float:left;">
                     <h2>Specimen Data</h2>
                 </div>
                 <div class="clear"></div>
             </div>

             <div class="admin_action_item">
                 <br><div class="action_desc"><b>Upload</b> Specimen File:<br>&nbsp;&nbsp;&nbsp;(tab-delimited .txt file)</div>
                 <div class="action_dropdown"></div>
                 <div class="action_browse">
                   <html:file property="theFile" />
                 </div>
                 <div class="clear"></div>

          <% if (AntwebProps.isDevMode()) {
               if (true) { %>
                     <div class="align_left">
                       <select name="specimenUploadType">
                         <option value="full" selected>Full
                         <option value="incremental">Incremental
                         <option value="diff">Diff
                         <option value="augment">Augment
                       </select>
                     </div>
            <% } else { %>
                 <div class="admin_action_item">
                     <html:checkbox property="whole" value="true"/> Update entire the biota file
                 </div>
            <% } %>
          <% } else { %>
               <input type="hidden" name="whole" value="true" />
          <% } %>

          <% if (false && LoginMgr.isCurator(accessLogin)) { // || accessLogin.getId() == 16) { //  || accessLogin.getId() == 338   %>
                     <div class="align_left">
                       <select name="encoding">
                         <option value="default" selected>Default
                         <option value="UTF-8">UTF-8
                         <option value="MacRoman">MacRoman
                         <option value="ISO8859_1">ISO8859_1
                       </select>
                     </div>
          <% } %>

To calculate the taxon children counts run the <a href='<%= domainApp %>/utilData.do?action=runCountCrawls' title="If taxon children counts are not calculated subsequent to the upload, it will happen nightly.">Count Crawls<img src=<%= domainApp%>/image/new1.png width=20></a>
<br>If not returned an upload report, find it in the <a href='<%= domainApp %>/listSpecimenUploads.do?groupId=<%= accessGroup.getId() %>'>Specimen Upload Reports</a>.


                 <div class="align_right"><input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="Submit" <%= active %>></div>
                 <div class="clear"></div>
             </div>

       </html:form>

       <!-- Reload Specimen Data -->

       <div class="admin_action_module">
         <div class="admin_action_item">    
             <div style="float:left;">        
               <br>Reload Specimen Data:
             </div>
             <div class="clear"></div>

             <html:form method="POST" action="upload.do" enctype="multipart/form-data">
                   <input type="hidden" name="action" value="reloadSpecimenList" />
               <div class="align_right">
                   <input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="ReloadSpecimenList" <%= active %>>
               </div>
               <div class="clear"></div>

             </html:form>
         </div>
       </div>

       <!-- Remove Specimen Data -->

       <div class="admin_action_module">
         <div class="admin_action_item">    
             <div style="float:left;">        
             Remove Specimen Data:
             </div>
             <html:form method="POST" action="upload.do" enctype="multipart/form-data">
                   <input type="hidden" name="action" value="removeSpecimenList" />
               <div class="align_right">
                   <input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="RemoveSpecimenList" <%= active %>>
               </div>
             <div class="clear"></div>

             </html:form>
         </div>
       </div>



     <% if (session.getAttribute("museumMap") != null) {  %>
        <html:form method="POST" action="upload.do" enctype="multipart/form-data">
            <input type="hidden" name="action" value="museumCalc" />
            <input type="hidden" name="ancFileDirectory" value="none" />
            <input type="hidden" name="updateAdvanced" value="no" />
            <input type="hidden" name="updateFieldGuide" value="none" />
            <input type="hidden" name="images" value="no" />
            <input type="hidden" name="outputFileName" value="" />
            <input type="hidden" name="successkey" value="null" />
            <div class="admin_action_module">
                <div class="admin_action_item">
                    <div class="action_desc">Recalculate Your <b>Museums</b> </div>
                    <div class="clear"></div>
                    <div class="align_right"><input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="museumCalc"></div>
                    <div class="clear"></div>
                </div>
            </div>
        </html:form>
     <% } %>





        <!-- View Archived Specimen List Files -->
        <% 
            ArrayList<Integer> uploadGroupList = AntwebUtil.getUploadGroupList();
            // A.log("curate-body.jsp uploadGroupList:" + uploadGroupList);

           // To be replaced by uploadGroupList
          //  ArrayList<String> uploadFileKindList = AntwebUtil.getUploadDirKinds();
        %>
            <html:form method="POST" action="uploadHistory.do" enctype="multipart/form-data">
                <input type="hidden" name="ancFileDirectory" value="none" />
                <input type="hidden" name="updateAdvanced" value="no" />
                <input type="hidden" name="updateFieldGuide" value="none" />
                <input type="hidden" name="images" value="no" />
                <input type="hidden" name="outputFileName" value="" />
                <input type="hidden" name="successkey" value="null" />
                <div class="admin_action_module">
                    <div class="admin_action_item">
                        <div class="action_desc"><b>View</b> Archived Specimen List Files: </div>
                        <div class="action_dropdown">
              <html:select property="editSpeciesList">
                <html:option value="none">Select...</html:option>
            <!-- change to java interator 
            Look in projList.  If projlist (lowercased compacted) is in uploadFileKindList, list the projList entry.
            Value to send is the uploadFileKindList entry.
            -->	
            <%  

                for (Integer uploadGroupId : uploadGroupList) {
                  String val = "specimen" + uploadGroupId.toString();
                  if (LoginMgr.isAdmin(accessLogin)) {
                        //if (AntwebProps.isDevMode()) AntwebUtil.log("curate-body.jsp uploadGroupId:" + uploadGroupId);
                        String groupName = "specimen" + uploadGroupId;
                        Group g = GroupMgr.getGroup(uploadGroupId);
                        if (g != null) groupName = g.toString();
                        %>
                      <html:option value="<%= val %>"><%= groupName %></html:option>
                      <%
                  } else {
                      //if (AntwebProps.isDevMode()) AntwebUtil.log("curate-body.jsp uploadGroupId:" + uploadGroupId);
                      if (Integer.valueOf(accessGroup.getId()).equals(uploadGroupId)) {
                      %>
                        <html:option value="<%= val %>"></html:option>
                      <%
                      }

                  }
                }

/*
                for (String uploadFileKind : uploadFileKindList) {
                  if (LoginMgr.isAdmin(accessLogin)) { 
                    if (uploadFileKind.contains("specimen")) {
                      if (AntwebProps.isDevMode()) AntwebUtil.log("curate-body.jsp kind:" + uploadFileKind);
                      %>
                      <html:option value="<%= (String) uploadFileKind %>"></html:option>
                      <%
                    }
                  } else {
                    // only the one that is specimen[curator % ]
                    int specIndex = uploadFileKind.indexOf("specimen") + 8;
                    if (specIndex > 8) {
                      String fileAccessId = uploadFileKind.substring(specIndex);
                      if (AntwebProps.isDevMode()) AntwebUtil.log("curate-body.jsp fileAccessId:" + fileAccessId);
                      if ((new Integer(accessGroup.getId())).toString().equals(fileAccessId)) {
            % >
                        <html:option value="< %= (String) uploadFileKind % >"></html:option>
            < %
                      }
                    }
                  } 
                }
*/
            %>
              </html:select>
                </div>
                <div class="clear"></div>
                <div class="align_right"><input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="EditUploadFileKindList"></div>
                <div class="clear"></div>
                </div>
            </div>
        </html:form>


<!-- TaxonWorks Specimen Zip File Upload -->
    <% if (AntwebProps.isDevMode() || GroupMgr.isCAS(accessGroup)) { %>
       <html:form method="POST" action="upload.do" enctype="multipart/form-data">
         <input type="hidden" name="action" value="taxonWorksUpload" />

             <div class="admin_action_item">
                 <div class="action_desc"><b>Upload</b> TaxonWorks Specimen Zip File:<br>&nbsp;&nbsp;&nbsp;(w/ tab-delimited .tsv file)</div>
                 <div class="action_browse">
                   <html:file property="theFile" />
                 </div>
                 <div class="clear"></div>

             <input type="hidden" name="whole" value="true" />

             <div class="align_right"><input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="Submit" <%= active %>></div>
             <div class="clear"></div>
             </div>
       </html:form>

        <!-- End TaxonWorks Specimen Zip File Upload -->
    <% } %>


<!-- GBIF Specimen Zip File Upload -->
    <% if (AntwebProps.isDevMode() || GroupMgr.isCAS(accessGroup)) { %>
        <!-- GBIF Specimen file or Zip File Upload -->

       <html:form method="POST" action="upload.do" enctype="multipart/form-data">
         <input type="hidden" name="action" value="GBIFUpload" />

             <div class="admin_action_item">
                 <div class="action_desc"><b>Upload</b> GBIF Specimen File or Zip File:<br>&nbsp;&nbsp;&nbsp;</div>
                 <div class="action_browse">
                   <html:file property="theFile" />
                 </div>
                 <div class="clear"></div>

             <input type="hidden" name="whole" value="true" />

             <div class="align_right"><input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="Submit" <%= active %>></div>
             <div class="clear"></div>
             </div>
       </html:form>

        <!-- End GBIF Specimen file or Zip File Upload -->
    <% } %>


     <% } %>  <!-- takeDownUpload -->
 <% } %>  <!-- accessLogin.isUploadSpecimens() -->

</div>


<!-- Upload Specimen Images -->
<% if (accessLogin.isUploadImages()) { %>  
    <div class="admin_action_module">
        <div class="admin_action_item">
        <h2>Upload Specimen Images</h2>
        </div>
        <div class="admin_action_item">
          <div class="action_desc"> <!-- Insecure -->
  <% if (true) { %>
           <!-- was: &nbsp;&nbsp;&nbsp;<a href="< %= AntwebProps.getDomainApp() % >/imageUpload.do">Specimen Image Uploader</a> -->
          &nbsp;&nbsp;&nbsp;<a href="<%= AntwebProps.getDomainApp() %>/imageUploader.do">Specimen Image Upload</a>
  <% } else { %>
       Image Upload is down for maintenance.
  <% } %>  
          </div>  
          <div class="clear"></div>
        </div>
    </div>
<% } %>


<% if (LoginMgr.isAdmin(request)) { %>
<!-- Create Ancillary Pages -->
<html:form method="POST" action="upload.do" enctype="multipart/form-data">
    <div style="display:none;">
    <input type="file" name="theFile" value="" />
    <input type="file" name="biota" value="" />
    </div>
    <input type="hidden" name="whole" value="false" />
    <input type="hidden" name="projectFile" value="" />
    <input type="hidden" name="homePageDirectory" value="" />
    <input type="hidden" name="updateAdvanced" value="no" />
    <input type="hidden" name="updateFieldGuide" value="none" />
    <input type="hidden" name="images" value="no" />
    <input type="hidden" name="outputFileName" value="" />
<!--
    <input type="file" name="theFile2" value="" />
    <input type="hidden" name="successkey" value="null" />
-->
    <div class="admin_action_module">
        <div class="admin_action_item"><h2>Create a New Ancillary Page</h2></div>
        <div class="admin_action_item">
            <div class="action_desc">Create an ancillary page for:</div>
            <div class="action_dropdown">
  <html:select property="ancFileDirectory">
	<html:option value="curator">Curator Dir</html:option>
 <% if (accessLogin.isAdmin()) { %>  
     <html:option value="homepage">AntWeb Home Page</html:option>
 <% } 
 
    if (projList != null) {
      for (SpeciesListable project : projList) { %>
         <html:option value="<%= project.getName() %>"><%= project.getTitle() %></html:option>
  <%  } 
    } else {
      AntwebUtil.log("curate-body.jsp projList is null for:" + HttpUtil.getTarget(request));
    }
  %>

  </html:select>
            </div>
            <div class="clear"></div>

            <div class="align_right"><input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="Submit"></div>
            <div class="clear"></div>
        </div>
    </div>
</html:form>

<% } %>

<!--
Ancillary Files
  Create
  View  [Curator (ex:bfisher) or All should be options]
    (DB query driven)

Upload Curator File
  Upload to curator folder
  View All -files in curator folder (link directly to the curator directory)
  * When creating html links to files in your curator directory use this format: /bfisher/filename.ext
-->


<!-- Projects -->                
<!-- Download Species List -->   

<% if (accessLogin.isDeveloper()) { %>  <!-- was isAdmin() -->

        <div class="admin_action_item">
            <div style="float:left;">
                <h2>Species Lists</h2>
            </div>
            <div class="clear"></div>
        </div>


<html:form method="POST" action="upload.do" enctype="multipart/form-data">
    <input type="hidden" name="ancFileDirectory" value="none" />
    <input type="hidden" name="updateAdvanced" value="no" />
    <input type="hidden" name="updateFieldGuide" value="none" />
    <input type="hidden" name="images" value="no" />
    <input type="hidden" name="outputFileName" value="" />
    <input type="hidden" name="successkey" value="null" />
    <div class="admin_action_module">

        <div class="admin_action_item">
            <div class="action_desc"><b>Download</b> Species List for:<br></div>
            <div class="action_dropdown">
  <html:select property="downloadSpeciesList">
	<html:option value="none">Select...</html:option>

       <html:option value="worldants">Bolton World Catalog</html:option>

	<% if (projList != null) {
	     for (SpeciesListable p : projList) { %>
     <%    if (!p.getName().equals("globalants")) { %>
           <html:option value="<%= p.getName() %>"><%= p.getTitle() %></html:option>
      <%   } 
	     }
	   } 
	%>

  </html:select>
            </div>
              <div class="clear"></div>
            <div class="align_right"><input type="image" border="0" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="DownloadSpeciesList"></div>            
            <div class="clear"></div>	
        </div>  
    </div>
</html:form>

<!-- Upload Species List -->   
<html:form method="POST" action="upload.do" enctype="multipart/form-data">
    <input type="hidden" name="action" value="uploadSpeciesList" />
    <input type="hidden" name="ancFileDirectory" value="none" />
    <input type="hidden" name="updateAdvanced" value="no" />
    <input type="hidden" name="updateFieldGuide" value="none" />
    <input type="hidden" name="images" value="no" />
    <input type="hidden" name="outputFileName" value="" />
    <input type="hidden" name="successkey" value="null" />
    <div class="admin_action_module">
        <div class="admin_action_item">
            <div class="action_desc"><b>Upload</b> Species List for:<br>(tab-delimited .txt file) </div>
            <div class="action_dropdown">
  <html:select property="projectFile">
	<html:option value="none">Select...</html:option>

      <html:option value="worldants">Bolton World Catalog</html:option>

	<!-- % for (Project p : nonMappableSpeciesList) { % >
      <html :option value="< %= p.getName() % >">< %= p.getTitle() % ></html :option>
	< % } % -->
		    
  </html:select>
            </div>
            <div class="action_browse">
  <html:file property="theFile" />
            </div>
            <div class="clear"></div>

            <div class="align_right"><input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="Submit"></div>
            <div class="clear"></div>
            
            <input type=checkbox name=recrawl >Recrawl for counts	            
        </div>
    </div>
</html:form>

<% } %>

<!-- Fetch and Reload Worldants List -->
<% if (accessLogin.isAdmin()) {  %>
<html:form method="POST" action="upload.do" enctype="multipart/form-data">
    <input type="hidden" name="action" value="fetchAndReloadWorldants" />
    <input type="hidden" name="reloadSpeciesList" value="worldants" />
    <input type="hidden" name="ancFileDirectory" value="none" />
    <input type="hidden" name="updateAdvanced" value="no" />
    <input type="hidden" name="updateFieldGuide" value="none" />
    <input type="hidden" name="images" value="no" />
    <input type="hidden" name="outputFileName" value="" />
    <input type="hidden" name="successkey" value="null" />
    <div class="admin_action_module">
        <div class="admin_action_item">
            <% String reloadStr = "Reload";
               // This happens on the live server, so must be able to test. Antweb is invoked to pull down the fetch and reload from antcat (ibis...).
               // This enables us to test that upload process. 
               reloadStr = "<a href='" + AntwebProps.getDomainApp() + "/utilData.do?action=worldantsReload'>Reload</a>";
               String fetchStr = "<a title=" + org.calacademy.antweb.curate.speciesList.SpeciesListUploader.fetchWorldantsUrl + ">Source</a> | ";
               fetchStr += "<a href='" + AntwebProps.getDomainApp() + "/web/workingdir/worldants_speciesList.txt'>Local</a><br>";
            %>
            <div class="action_desc"><%= reloadStr %> <b>Worldants</b> &nbsp;&nbsp;&nbsp;<%= fetchStr %> </div>
            <div class="clear"></div>
            <div class="align_right">
                <input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="fetchAndReloadWorldants">
              </div>
            <div class="clear"></div>
        </div>
    </div>
</html:form>

 
<!-- Edit Species List - Mapping Tool -->
<% 
if (AntwebProps.isDevOrStageMode()) {
  //AntwebUtil.log("curate-body.jsp speciesList:" + speciesList);
}

if (speciesListList != null && !speciesListList.isEmpty()) { %>
<html:form method="POST" action="upload.do" enctype="multipart/form-data">
    <input type="hidden" name="ancFileDirectory" value="none" />
    <input type="hidden" name="updateAdvanced" value="no" />
    <input type="hidden" name="updateFieldGuide" value="none" />
    <input type="hidden" name="images" value="no" />
    <input type="hidden" name="outputFileName" value="" />
    <input type="hidden" name="successkey" value="null" />
    <input type="hidden" name="isFresh" value="true" />
    <div class="admin_action_module">
        <div class="admin_action_item">
            <div class="action_desc"><b>Edit</b> Species List: </div>
            <div class="action_dropdown">

       <% ArrayList<SpeciesListable> groupSpeciesList = speciesListList; %>   

  <html:select property="editSpeciesList">
	<html:option value="none">Select...</html:option>
	<% for (SpeciesListable s : groupSpeciesList) { 
	     String spacer = ""; 
	     if (SpeciesListable.ADM1.equals(s.getType())) {
	       spacer = "&nbsp;&nbsp;&nbsp;";
	     }
	     String value = s.getKey();

         //A.log("curate-body.jsp key:" + s.getKey() + " title:" + s.getTitle());
	     if (s.getIsUseChildren()) {
	       value = "";           
         }
	%>
         <option value="<%= value %>" disable><%= spacer + s.getTitle() %></option>
	<% } %>
  </html:select>
  
       <!-- % @include file="/curate/groupSpeciesListSelect.jsp" % -->     

            </div>
            <div class="clear"></div>
            <div class="align_right"><input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="EditSpeciesList"></div>
            <div class="clear"></div>
        </div>
    </div>
</html:form>
<% }
// } 
%>

<% } %>


<%
    if (accessLogin.isAdmin()) {
%>

<div class="admin_action_item">
    <div style="float:left;">
        <h2>Other Functions</h2>
    </div>
    <div class="clear"></div>
</div>


<% // Edit Region content removed %>

<br>
<!-- Upload a File to a Folder
<html:form method="POST" action="upload.do" enctype="multipart/form-data">
    <input type="hidden" name="ancFileDirectory" value="none" />
    <input type="hidden" name="updateAdvanced" value="no" />
    <input type="hidden" name="updateFieldGuide" value="none" />
    <input type="hidden" name="images" value="no" />
    <input type="hidden" name="outputFileName" value="" />
    <input type="hidden" name="successkey" value="null" />
    <div class="admin_action_module">
        <div class="admin_action_item">
            <div class="action_desc"><b>Upload</b> a File to Folder:</div>
            <div class="action_dropdown">
  <html:select property="homePageDirectory">
	<html:option value="curator">Curator Dir</html:option>
< % if (accessLogin.isAdmin()) { % >
		<html:option value="homepage">AntWeb Home Page</html:option>
< % } % >

	<% if (projList != null) {
	     for (SpeciesListable s : projList) { 
	       Project p = ProjectMgr.getProject(s.getName());
	       if (p == null) {
	         //A.log("curate-body.jsp 2 p is null for:" + s.getName());
	       } else { %>
             <option value="< %= p.getRoot() % >">< %= p.getTitle() % ></option>
	    <% }
	     }
	   } %>
	
  </html:select>
            </div>
            <div class="action_browse">
  <html:file property="theFile2" />
            </div>
            <div class="clear"></div>

            <div class="align_right"><input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="Submit"></div>
            <div class="clear"></div>
        </div>
    </div>
</html:form>
-->


<!-- Data File Upload -->
<% if (accessLogin.isCurator()) { %>
<html:form method="POST" action="upload.do" enctype="multipart/form-data">
    <input type="hidden" name="ancFileDirectory" value="none" />
    <input type="hidden" name="updateAdvanced" value="no" />
    <input type="hidden" name="updateFieldGuide" value="none" />
    <input type="hidden" name="images" value="no" />
    <input type="hidden" name="outputFileName" value="" />
    <input type="hidden" name="successkey" value="null" />
    <div class="admin_action_module">
        <div class="admin_action_item">
            <div class="action_desc"><b>Upload</b> Data File<br>&nbsp;&nbsp;&nbsp;Press submit for documentation</div>
            <div class="action_browse">
  <html:file property="testFile" />
            </div>
            <div class="clear"></div>
            <div class="align_right"><input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="Submit"></div>
            <div class="clear"></div>
        </div>
    </div>
</html:form>
<% } %>


<% }  // isAdmin() %>


</div>

<div class="admin_right">

    <div class="clear"></div>
<br /><br />


    <div class="home_module">
        <div class="home_module two">
            <div class="home_grey_module">
                <div class="home_module_left_curve"></div>
                <div class="home_module_header">AntWeb Statistics</div>
                <div class="home_module_right_curve"></div>
            <div class="clear"></div>
        </div>
        <div class="home_module_contents_admin">

        <%= FileUtil.getContent("web/genInc/statistics.jsp") %>

        </div>
    </div>

</div> <!-- this seems extraneous -->
<br>
<%@include file="/curate/curatorLinks.jsp" %>
	  
</div>

