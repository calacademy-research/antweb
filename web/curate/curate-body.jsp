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
            <br>&nbsp;&nbsp;&nbsp;View the <a href="<%= domainApp %>/query.do?name=worldantsUploads">World (AntCat) upload reports</a> (the nightly AntCat name import: names added, changed and removed each night)
<% } %>



          </div>
          <div class="clear"></div>
        </div>
        
        <div class="admin_action_item">
          <div class="action_hint">
            &nbsp;&nbsp;&nbsp;<a href="<%= domainApp %>/curatorFiles.do">Images you uploaded for taxon page text</a> (your curator folder).
          </div>
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
            var uploadReportUrl = '<%= domainApp %>/listSpecimenUploads.do?groupId=<%= accessGroup.getId() %>';
            var uploadTimeoutId = null;

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
                // Cloudflare times out at 100s — redirect to upload report after 85s
                // The upload continues processing server-side even after a Cloudflare timeout
                uploadTimeoutId = setTimeout(function() {
                    document.getElementById('upload-processing-msg').innerHTML =
                        '<b>This is taking a while.</b> Your data is still being processed on the server.<br>' +
                        'You will be redirected to your <a href="' + uploadReportUrl + '">Upload Report</a> shortly, ' +
                        'where you can check the result.';
                    setTimeout(function() { window.location.href = uploadReportUrl; }, 5000);
                }, 85000);
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
                    Uploading and processing your file&hellip; please wait.
                </div>

                <div style="color: red; font-weight: bold; margin: 10px 0;">
                    1. Only ZIP Files Allowed<br>
                    2. If you see a Cloudflare timeout, your data is still processing &mdash;
                       check your <a href="<%= domainApp %>/listSpecimenUploads.do?groupId=<%= accessGroup.getId() %>">Upload Report</a>.
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
                    <select name="uploadAs">
                        <option value="<%= accessLogin.getId() %>" selected><%= Html.esc(accessLogin.getName()) %>
                <%
                String uploadAs = accessLogin.getUploadAs();
                if (uploadAs != null && !"".equals(uploadAs)) {
                    List<String> curatorList = new ArrayList<String>(Arrays.asList(uploadAs.split(",")));
                    for (String curatorIdStr : curatorList) {
                      int curatorId = 0;
                      Login curator = null;
                      try {
                        curatorId = Integer.parseInt((curatorIdStr.trim()));
                        curator = LoginMgr.getCurator(curatorId);
                        if (curator != null) { %>
                            <option value="<%= curator.getId() %>"><%= Html.esc(curator.getName()) %>
                     <% } else {
                         //A.log("curate-body.jsp curator:null for " + curatorList + " curatorIDStr:" + curatorIdStr + " curatorId:" + curatorId + " curator:" + curator);
                        }
                      } catch (NullPointerException e) {
                         // Why is this not getting trapped outside/above?
                         AntwebUtil.log("NullPointerException for curatorList:" + curatorList + " curatorIDStr:" + curatorIdStr + " curatorId:" + curatorId + " curator:" + curator);
                      } catch (NumberFormatException e) {
                         AntwebUtil.log("NumberFormatException for curatorID:" + curatorIdStr);
                      }
                    }
                } %>

                    </select>
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
                    <div class="action_desc"><a href="<%= domainApp %>/uploadHistory.do?editSpeciesList=specimen<%= accessGroup.getId() %>">Your past upload files</a> (the specimen files your group uploaded; each can be downloaded)</div>
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
                        <div class="action_desc">Past upload files of: </div>
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
            &nbsp;&nbsp;&nbsp;<a href="<%= AntwebProps.getDomainApp() %>/specimenListLookup.do">Specimen List Lookup</a>
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
