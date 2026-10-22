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

<%-- Layout (Brian Fisher, 2026-10-22, second pass): every section is a full-width card, left-aligned
     (admLayout.jsp centers its content; .cp in common/curate_page.css sets it back to the left). Curator
     sections first (upload specimen data, upload images, your uploads, names and checks, read-only tools,
     your account), the administrator cards after them. Plain buttons instead of the grey image buttons.
     Who sees what is unchanged. Tested in tests/e2e/test_curate_page_layout.py and
     tests/e2e/test_curate_page_clarity.py. --%>
<link href="<%= domainApp %><%= org.calacademy.antweb.util.Asset.url(application, "/common/curate_page.css") %>" rel="stylesheet" type="text/css">

<div class="cp" id="curate-page">

<p id="curate-help" class="cp-help">Need help? See the <a href="<%= domainApp %>/documentation.do" target="new">Curator Tool Documentation</a>.</p>

<html:messages id="message" message="true">
<div class="cp-msg"><bean:write name="message"/></div>
</html:messages>

<%
   if (AntwebMgr.hasServerMessage()) { %>
<div class="cp-msg" id="curate-server-message"><%= AntwebMgr.getServerMessage() %></div>
<% } %>

<!-- Upload specimen data -->
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
<div class="cp-card" id="curate-specimen-upload">
  <h2>Upload specimen data</h2>
  <p class="cp-lead">Send your group's specimen file (tab-delimited, in a ZIP file). The upload replaces your group's specimen records on AntWeb.</p>

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
        <html:form styleClass="cp-form" method="POST" action='<%= org.calacademy.antweb.util.Csrf.action(request, "upload.do") %>' enctype="multipart/form-data" onsubmit="return validateZipUpload(this);"><%= org.calacademy.antweb.util.Csrf.field(request) %>

            <div class="cp-row">
                <label class="cp-label" for="curate-zip-file">ZIP file</label>
                <html:file property="theFile" accept=".zip" styleId="curate-zip-file" />
            </div>

            <%
            if (LoginMgr.isAdmin(accessLogin)) { %>
            <div class="cp-row">
                <label for="curate-upload-type">Upload type</label>
                <select name="action" id="curate-upload-type">
                    <option value="specimenUpload" selected>Antweb
                    <option value="taxonWorksUpload">TaxonWorks
                </select>
            </div>
            <div class="cp-row">
                <label for="curate-upload-as">Upload as</label>
                <select name="uploadAs" id="curate-upload-as" aria-label="Upload as" aria-describedby="upload-as-warning">
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
            </div>
            <div class="cp-warn" id="upload-as-warning"><%= org.calacademy.antweb.curate.UploadAsOptions.WARNING %></div>
         <% } else { %>
            <input type="hidden" name="action" value="specimenUpload" />
            <input type="hidden" name="specimenUploadLoginId" value="<%= accessLogin.getId() %>" />
         <% } %>

            <div class="cp-actions">
                <input id="upload-submit-btn" class="cp-btn" type="submit" value="Upload" <%= active %>>
            </div>

            <div id="upload-processing-msg" class="cp-progress">
                Sending your file&hellip; please wait. The next page shows the progress of your upload.
            </div>

            <div class="cp-note">
                <ol>
                  <li>Only ZIP files are accepted.</li>
                  <li>Large files take several minutes; you will see the progress on the next page
                      (and later under <a href="<%= domainApp %>/jobStatus.do">Upload status</a>).</li>
                </ol>
            </div>

            <div class="cp-note upload_optional_columns">
                Optional column <b>previousDeterminations</b>: earlier determinations, newest first, separated by &quot; | &quot;.
                Example: <code>Nylanderia: US_CA01 by Ward on 2025-12-10 | Nylanderia: US_CA01 by Fisher on 2000-11-10</code>
            </div>
        </html:form>

     <% if (session.getAttribute("museumMap") != null) {  %>
        <html:form styleClass="cp-form cp-sub" method="POST" action='<%= org.calacademy.antweb.util.Csrf.action(request, "upload.do") %>' enctype="multipart/form-data"><%= org.calacademy.antweb.util.Csrf.field(request) %>
            <input type="hidden" name="action" value="museumCalc" />
            <input type="hidden" name="updateAdvanced" value="no" />
            <input type="hidden" name="updateFieldGuide" value="none" />
            <input type="hidden" name="images" value="no" />
            <input type="hidden" name="outputFileName" value="" />
            <input type="hidden" name="successkey" value="null" />
            <p><b>Recalculate Your Museums</b>: recount the museums in your last upload now, instead of waiting for tonight's run.</p>
            <div class="cp-actions"><input class="cp-btn cp-btn-secondary" type="submit" value="Recalculate"></div>
        </html:form>
     <% } %>
</div>
     <% } %>  <%-- takeDownUpload --%>
 <% } %>  <%-- accessLogin.isUploadSpecimens() --%>

<!-- Upload specimen images -->
<% if (accessLogin.isUploadImages()) { %>
<div class="cp-card" id="curate-image-upload">
  <h2>Upload specimen images</h2>
  <ul class="cp-links">
  <% if (true) { %>
    <li><a href="<%= AntwebProps.getDomainApp() %>/imageUploader.do">Specimen Image Upload</a>: opens the Image Uploader. Name each file by specimen code and view; you get a report and an email at the end.</li>
  <% } else { %>
    <li>Image Upload is down for maintenance.</li>
  <% } %>
  </ul>
</div>
<% } %>

<%-- Three groups of links, one link per line with a plain explanation (Brian Fisher, 2026-10-20). Who sees
     what is unchanged: the World (AntCat) upload reports and the count check are for administrators.
     Tested in tests/e2e/test_curate_page_clarity.py. --%>
<div class="cp-card curate-group" id="curate-uploads">
  <h2>Your uploads</h2>
  <ul class="cp-links">
    <li><a href="<%= domainApp %>/listSpecimenUploads.do?groupId=<%= accessGroup.getId() %>">Specimen upload reports</a>: one report per specimen upload of your group, newest first.</li>
    <li><a href="<%= domainApp %>/listImageUploads.do?groupId=<%= accessGroup.getId() %>">Image upload reports</a>: one report per batch of uploaded images.</li>
    <li><a href="<%= domainApp %>/jobStatus.do">Upload status</a>: uploads waiting or in progress, and their results.</li>
  </ul>
</div>

<div class="cp-card curate-group" id="curate-names">
  <h2>Names and checks</h2>
  <ul class="cp-links">
<% if (accessLogin.isAdmin()) { %>
    <li><a href="<%= domainApp %>/query.do?name=worldantsUploads">World (AntCat) upload reports</a>: the nightly import of AntCat names (names added, changed and removed each night).</li>
<% } %>
    <li><a href="<%= domainApp %>/nameUpdates.do">Name update report</a>: specimens moved to the current valid name after a name changed in AntCat.</li>
<% if (LoginMgr.isAdmin(accessLogin)) { %>
    <li><a href="<%= domainApp %>/countCheck.do">Check the counts</a>: recounts, from the specimen records, the numbers shown on a few sample country, project, museum and bioregion pages (specimens, species, genera, images) and lists any that differ from what the site shows. Usually nothing to do: the nightly run corrects them.</li>
<% } %>
  </ul>
</div>

<% if (accessLogin.isCurator()) { %>
<div class="cp-card" id="curate-readonly">
  <h2>Read-only tools</h2>
  <p class="cp-lead">These tools never change anything on AntWeb.</p>
  <ul class="cp-links">
    <li><a href="<%= AntwebProps.getDomainApp() %>/validateSpeciesList.do">Check names against AntCat</a>: upload a list of species names (a text file) to see which are current valid names in AntCat, with a report and a corrected copy to download. Nothing is saved on AntWeb.</li>
    <li><a href="<%= AntwebProps.getDomainApp() %>/specimenListLookup.do">Specimen List Lookup</a>: upload a text file of specimen codes (one per line) to get AntWeb's data for those specimens as a tab-delimited file; codes that are not in AntWeb are listed. Nothing is changed on AntWeb.</li>
  </ul>
</div>
<% } %>

<div class="cp-card curate-group" id="curate-account">
  <h2>Your account</h2>
  <ul class="cp-links">
    <li><a href="<%= secureDomainApp %>/curator.do?id=<%= (accessLogin.getId()) %>">Your curator page</a>: your public page on AntWeb (your uploads, images and page-text edits).</li>
    <li><a href="<%= secureDomainApp %>/viewLogin.do?id=<%= (accessLogin.getId()) %>">Your login information</a>: change your name, email address or password.</li>
    <li><a href="<%= domainApp %>/curatorFiles.do">Images you uploaded for taxon page text</a>: the image files in your curator folder, for use in taxon page text.</li>
  </ul>
</div>

<% if (accessLogin.isAdmin()) {  %>
<!-- Administrators: AntCat names and the Species List Tool -->
<div class="cp-card cp-admin" id="curate-admin-names">
  <h2>AntCat names and species lists<span class="cp-badge">Admin</span></h2>

<html:form styleClass="cp-form" method="POST" action='<%= org.calacademy.antweb.util.Csrf.action(request, "upload.do") %>' enctype="multipart/form-data"><%= org.calacademy.antweb.util.Csrf.field(request) %>
    <input type="hidden" name="action" value="fetchAndReloadWorldants" />
    <input type="hidden" name="reloadSpeciesList" value="worldants" />
    <input type="hidden" name="updateAdvanced" value="no" />
    <input type="hidden" name="updateFieldGuide" value="none" />
    <input type="hidden" name="images" value="no" />
    <input type="hidden" name="outputFileName" value="" />
    <input type="hidden" name="successkey" value="null" />
    <div id="curate-antcat-import">
      <h3>Import AntCat names now</h3>
      <p>Downloads the AntCat export and loads the names, as the nightly run does at 05:00 Pacific. Use it only when the nightly AntCat import failed (an admin alert or compute.log says so). Takes several minutes.</p>
      <div class="cp-actions"><input class="cp-btn" type="submit" value="Import AntCat names now"></div>
    </div>
</html:form>

<%
if (speciesListList != null && !speciesListList.isEmpty()) { %>
<html:form styleClass="cp-form cp-sub" method="POST" action='<%= org.calacademy.antweb.util.Csrf.action(request, "upload.do") %>' enctype="multipart/form-data"><%= org.calacademy.antweb.util.Csrf.field(request) %>
    <input type="hidden" name="updateAdvanced" value="no" />
    <input type="hidden" name="updateFieldGuide" value="none" />
    <input type="hidden" name="images" value="no" />
    <input type="hidden" name="outputFileName" value="" />
    <input type="hidden" name="successkey" value="null" />
    <input type="hidden" name="isFresh" value="true" />
    <div id="curate-species-list-tool">
      <h3>Species List Tool</h3>
      <p>Choose a project or region species list to open it in the Species List Tool (compare it with another list, add or remove species).</p>
      <div class="cp-row">
       <% ArrayList<SpeciesListable> groupSpeciesList = speciesListList; %>
  <html:select property="editSpeciesList" styleId="curate-species-list" title="Species list">
	<html:option value="none">Select...</html:option>
	<% for (SpeciesListable s : groupSpeciesList) {
	     String spacer = "";
	     if (SpeciesListable.ADM1.equals(s.getType())) {
	       spacer = "&nbsp;&nbsp;&nbsp;";
	     }
	     String value = s.getKey();
	     if (s.getIsUseChildren()) {
	       value = "";
         }
	%>
         <option value="<%= org.calacademy.antweb.util.Html.esc(value) %>" disable><%= spacer + org.calacademy.antweb.util.Html.esc(s.getTitle()) %></option>
	<% } %>
  </html:select>
        <input class="cp-btn" type="submit" value="Open the Species List Tool">
      </div>
    </div>
</html:form>
<% } %>
</div>
<% } %>

<%@include file="/curate/curatorLinks.jsp" %>

</div>
