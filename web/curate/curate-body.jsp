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
    

    ArrayList<SpeciesListable> speciesListList = accessLogin.getSpeciesListList();
    
    session.removeAttribute("ancFile");

    String active = "";
    Boolean isServerBusy = DBUtil.isServerOverMaxBusy();
    if (isServerBusy) {
      active = " disabled ";
    }    
%>

Need Help? Check out the <a href="<%= domainApp %>/documentation.do" target="new">Curator Tool Documentation</a>
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
<%-- Three short groups, one link per line with a plain explanation (Brian Fisher, 2026-10-20: the
     section was "very congested"). Who sees what is unchanged: the World (AntCat) upload reports and
     the count check are for administrators. Tested in tests/e2e/test_curate_page_clarity.py. --%>
        <div class="admin_action_item curate-group" id="curate-account">
          <h3 style="margin:6px 0 2px 0">Your account</h3>
          <ul style="list-style:none;margin:0 0 8px 0;padding-left:12px">
            <li><a href="<%= secureDomainApp %>/curator.do?id=<%= (accessLogin.getId()) %>">Your curator page</a>: your public page on AntWeb (your uploads, images and page-text edits).</li>
            <li><a href="<%= secureDomainApp %>/viewLogin.do?id=<%= (accessLogin.getId()) %>">Your login information</a>: change your name, email address or password.</li>
            <li><a href="<%= domainApp %>/curatorFiles.do">Images you uploaded for taxon page text</a>: the image files in your curator folder, for use in taxon page text.</li>
          </ul>
          <div class="clear"></div>
        </div>

        <div class="admin_action_item curate-group" id="curate-uploads">
          <h3 style="margin:6px 0 2px 0">Your uploads</h3>
          <ul style="list-style:none;margin:0 0 8px 0;padding-left:12px">
            <li><a href="<%= domainApp %>/listSpecimenUploads.do?groupId=<%= accessGroup.getId() %>">Specimen upload reports</a>: one report per specimen upload of your group, newest first.</li>
            <li><a href="<%= domainApp %>/listImageUploads.do?groupId=<%= accessGroup.getId() %>">Image upload reports</a>: one report per batch of uploaded images.</li>
            <li><a href="<%= domainApp %>/jobStatus.do">Upload status</a>: uploads waiting or in progress, and their results.</li>
          </ul>
          <div class="clear"></div>
        </div>

        <div class="admin_action_item curate-group" id="curate-names">
          <h3 style="margin:6px 0 2px 0">Names and checks</h3>
          <ul style="list-style:none;margin:0 0 8px 0;padding-left:12px">
<% if (accessLogin.isAdmin()) { %>
            <li><a href="<%= domainApp %>/query.do?name=worldantsUploads">World (AntCat) upload reports</a>: the nightly import of AntCat names (names added, changed and removed each night).</li>
<% } %>
            <li><a href="<%= domainApp %>/nameUpdates.do">Name update report</a>: specimens moved to the current valid name after a name changed in AntCat.</li>
<% if (LoginMgr.isAdmin(accessLogin)) { %>
            <li><a href="<%= domainApp %>/countCheck.do">Check the counts</a>: recounts, from the specimen records, the numbers shown on a few sample country, project, museum and bioregion pages (specimens, species, genera, images) and lists any that differ from what the site shows. Usually nothing to do: the nightly run corrects them.</li>
<% } %>
          </ul>
          <div class="clear"></div>
        </div>
    </div>


<div>
<%
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

             <div class="admin_action_item">
                 <div style="float:left;">
                     <h2>Specimen Data</h2>
                 </div>
                 <div class="clear"></div>
             </div>

<!-- Universal Specimen Upload -->
        <!-- Antweb or TaxonWorks Specimen (Zip File) Upload -->
        
        <script type="text/javascript">
            // The upload runs on the server in the background: once the file has been sent, the
            // page answers with the upload's status page (jobStatus.do), which follows its progress.
            function validateZipUpload(form) {
                var fileInput = form.elements['theFile'];
                if (!fileInput || !fileInput.value) {
                    alert('Please select a ZIP file to upload.');
                    return false;
                }
                if (!fileInput.value.toLowerCase().endsWith('.zip')) {
                    alert('Error: Only ZIP files are allowed.');
                    return false;
                }
                document.getElementById('upload-submit-btn').disabled = true;
                document.getElementById('upload-processing-msg').style.display = 'block';
                return true;
            }
        </script>
        <html:form method="POST" action='<%= org.calacademy.antweb.util.Csrf.action(request, "upload.do") %>' enctype="multipart/form-data" onsubmit="return validateZipUpload(this);"><%= org.calacademy.antweb.util.Csrf.field(request) %>

            <div class="admin_action_item">
            <br>
                <div class="action_desc"><b>Upload</b> Zip File:<br>&nbsp;&nbsp;&nbsp;</div>
                <div class="action_browse">
                    <html:file property="theFile" accept=".zip" />
                </div>
                <div class="clear"></div>

                <div id="upload-processing-msg" style="display:none; color: blue; font-weight: bold; margin: 10px 0;">
                    Sending your file&hellip; please wait. The next page shows the progress of your upload.
                </div>

                <div style="color: red; font-weight: bold; margin: 10px 0;">
                    1. Only ZIP Files Allowed<br>
                    2. Large files take several minutes; you will see the progress on the next page
                       (and later under <a href="<%= domainApp %>/jobStatus.do">Upload status</a>).
                </div>

                <div class="upload_optional_columns" style="margin: 0 0 10px 0;">
                    Optional column <b>previousDeterminations</b>: earlier determinations, newest first, separated by &quot; | &quot;.
                    Example: <code>Nylanderia: US_CA01 by Ward on 2025-12-10 | Nylanderia: US_CA01 by Fisher on 2000-11-10</code>
                </div>

            <%
            if (LoginMgr.isAdmin(accessLogin)) { %>

                <div class="align_left">
                   &nbsp;&nbsp;Upload Type:
                    <select name="action">
                        <option value="specimenUpload" selected>Antweb
                        <option value="taxonWorksUpload">TaxonWorks
                    </select>

                    &nbsp;&nbsp;Upload as:
                    <select name="uploadAs" aria-label="Upload as" aria-describedby="upload-as-warning">
                <%
                // Each choice names the group the upload goes into (an upload replaces that group's specimens);
                // the value stays the login id. org.calacademy.antweb.curate.UploadAsOptions.
                List<org.calacademy.antweb.curate.UploadAsOptions.Option> uploadAsOptions = new ArrayList<>();
                uploadAsOptions.add(org.calacademy.antweb.curate.UploadAsOptions.of(accessLogin));
                String uploadAs = accessLogin.getUploadAs();
                if (uploadAs != null && !"".equals(uploadAs)) {
                    for (String curatorIdStr : uploadAs.split(",")) {
                      try {
                        Login curator = LoginMgr.getCurator(Integer.parseInt(curatorIdStr.trim()));
                        if (curator != null) uploadAsOptions.add(org.calacademy.antweb.curate.UploadAsOptions.of(curator));
                      } catch (RuntimeException e) {
                         AntwebUtil.log("curate-body.jsp uploadAs: " + e + " for curatorID:" + curatorIdStr);
                      }
                    }
                } %>
                <%= org.calacademy.antweb.curate.UploadAsOptions.optionsHtml(uploadAsOptions, accessLogin.getId()) %>
                    </select>
                    <span id="upload-as-warning" style="color:red;font-weight:bold"><%= org.calacademy.antweb.curate.UploadAsOptions.WARNING %></span>
                </div>
                <div class="clear"></div>

         <% } else { %>
                  <input type="hidden" name="action" value="specimenUpload" />
                  <input type="hidden" name="specimenUploadLoginId" value="<%= accessLogin.getId() %>" />
         <% } %>

             <div class="clear"></div>

                <div class="align_right">
                    <input id="upload-submit-btn" border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="Submit" <%= active %>>
                </div>

             <div class="clear"></div>




            </div>
            <div class="clear"></div>
        </html:form>

        <!-- End Specimen file or Zip File Upload -->





     <% if (session.getAttribute("museumMap") != null) {  %>
        <html:form method="POST" action='<%= org.calacademy.antweb.util.Csrf.action(request, "upload.do") %>' enctype="multipart/form-data"><%= org.calacademy.antweb.util.Csrf.field(request) %>
            <input type="hidden" name="action" value="museumCalc" />
            <input type="hidden" name="updateAdvanced" value="no" />
            <input type="hidden" name="updateFieldGuide" value="none" />
            <input type="hidden" name="images" value="no" />
            <input type="hidden" name="outputFileName" value="" />
            <input type="hidden" name="successkey" value="null" />
            <div class="admin_action_module">
                <div class="admin_action_item">
                    <div class="action_desc">Recalculate Your <b>Museums</b>: recount the museums in your last upload now, instead of waiting for tonight's run. </div>
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
        <% if (!LoginMgr.isAdmin(accessLogin)) { %>
            <div class="admin_action_module">
                <div class="admin_action_item">
                    <div class="action_desc"><a href="<%= domainApp %>/uploadHistory.do?editSpeciesList=specimen<%= accessGroup.getId() %>">Your past upload files</a>: copies of the specimen data files your group sent to AntWeb, newest first. Click one to download it.</div>
                    <div class="clear"></div>
                </div>
            </div>
        <% } else { %>
            <html:form method="POST" action='<%= org.calacademy.antweb.util.Csrf.action(request, "uploadHistory.do") %>' enctype="multipart/form-data"><%= org.calacademy.antweb.util.Csrf.field(request) %>
                <input type="hidden" name="updateAdvanced" value="no" />
                <input type="hidden" name="updateFieldGuide" value="none" />
                <input type="hidden" name="images" value="no" />
                <input type="hidden" name="outputFileName" value="" />
                <input type="hidden" name="successkey" value="null" />
                <div class="admin_action_module">
                    <div class="admin_action_item">
                        <div class="action_desc" id="past-upload-files"><b>Specimen files uploaded before</b>: choose a group and click Submit to list the specimen data files it sent to AntWeb (its uploads, and TaxonWorks or GBIF files), newest first; click a file to download it.<br>Group: </div>
                        <div class="action_dropdown">
              <html:select property="editSpeciesList">
                <html:option value="none">Choose a group...</html:option>
            <%
                // Admins only (curators get the link above): every group with a file in web/upload/, by name.
                List<String[]> uploadGroupOptions = new ArrayList<>();
                for (Integer uploadGroupId : uploadGroupList) {
                    String groupName = "group " + uploadGroupId;
                    Group g = GroupMgr.getGroup(uploadGroupId);
                    if (g != null) groupName = g.toString();
                    uploadGroupOptions.add(new String[] {"specimen" + uploadGroupId, groupName});
                }
                uploadGroupOptions.sort((optionA, optionB) -> optionA[1].compareToIgnoreCase(optionB[1]));
                for (String[] option : uploadGroupOptions) { %>
                      <option value="<%= org.calacademy.antweb.util.Html.escAttr(option[0]) %>"><%= org.calacademy.antweb.util.Html.esc(option[1]) %></option>
            <%  } %>
              </html:select>
                </div>
                <div class="clear"></div>
                <div class="align_right"><input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="EditUploadFileKindList"></div>
                <div class="clear"></div>
                </div>
            </div>
        </html:form>
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





<!-- Projects -->                
<!-- Download Species List -->   

<% if (accessLogin.isCurator()) { %>
    <div class="admin_action_module">
        <div class="admin_action_item">
            <div style="float:left;">
                <h2>Read-Only Tools</h2>
            </div>
            <div class="clear"></div>
        </div>
        <div class="admin_action_item">
          <div class="action_desc">
            &nbsp;&nbsp;&nbsp;<a href="<%= AntwebProps.getDomainApp() %>/validateSpeciesList.do">Check names against AntCat</a>: upload a list of species names (a text file) to see which are current valid names in AntCat, with a report and a corrected copy to download. Nothing is saved on AntWeb.
          </div>
          <div class="clear"></div>
        </div>
        <div class="admin_action_item">
          <div class="action_desc">
            &nbsp;&nbsp;&nbsp;<a href="<%= AntwebProps.getDomainApp() %>/specimenListLookup.do">Specimen List Lookup</a>: upload a text file of specimen codes (one per line) to get AntWeb's data for those specimens as a tab-delimited file; codes that are not in AntWeb are listed. Nothing is changed on AntWeb.
          </div>
          <div class="clear"></div>
        </div>
    </div>
<% } %>


<!-- Fetch and Reload Worldants List -->
<% if (accessLogin.isAdmin()) {  %>
<html:form method="POST" action='<%= org.calacademy.antweb.util.Csrf.action(request, "upload.do") %>' enctype="multipart/form-data"><%= org.calacademy.antweb.util.Csrf.field(request) %>
    <input type="hidden" name="action" value="fetchAndReloadWorldants" />
    <input type="hidden" name="reloadSpeciesList" value="worldants" />
    <input type="hidden" name="updateAdvanced" value="no" />
    <input type="hidden" name="updateFieldGuide" value="none" />
    <input type="hidden" name="images" value="no" />
    <input type="hidden" name="outputFileName" value="" />
    <input type="hidden" name="successkey" value="null" />
    <div class="admin_action_module">
        <div class="admin_action_item">
            <div class="action_desc"><b>Import AntCat names now</b>: downloads the AntCat export and loads the names, as the nightly run does at 05:00 Pacific. Use it only when the nightly AntCat import failed (an admin alert or compute.log says so). Takes several minutes.</div>
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
<html:form method="POST" action='<%= org.calacademy.antweb.util.Csrf.action(request, "upload.do") %>' enctype="multipart/form-data"><%= org.calacademy.antweb.util.Csrf.field(request) %>
    <input type="hidden" name="updateAdvanced" value="no" />
    <input type="hidden" name="updateFieldGuide" value="none" />
    <input type="hidden" name="images" value="no" />
    <input type="hidden" name="outputFileName" value="" />
    <input type="hidden" name="successkey" value="null" />
    <input type="hidden" name="isFresh" value="true" />
    <div class="admin_action_module">
        <div class="admin_action_item">
            <div class="action_desc">Open the <b>Species List Tool</b> for: </div>
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
         <option value="<%= org.calacademy.antweb.util.Html.esc(value) %>" disable><%= spacer + org.calacademy.antweb.util.Html.esc(s.getTitle()) %></option>
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
