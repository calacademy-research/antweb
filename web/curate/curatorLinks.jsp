<%-- Administrator cards of the Curate page (included by curate-body.jsp; styles in common/tools.css). --%>
<% if (accessLogin.isAdmin()) { %>
<div class="aw-card aw-admin" id="curate-manager-tools">
    <h2>Manager tools<span class="aw-badge">Admin</span></h2>
    <ul class="aw-links">
      <li><a href="<%= domainApp %>/viewLogins.do">Login Manager</a> (also: all logins with their permissions, logins by last login)</li>
      <li><a href="<%= domainApp %>/addUploader.do">Add an uploader</a> (someone wants to upload specimens: login or invitation, upload group, rights and welcome email in one form)</li>
      <li><a href="<%= domainApp %>/viewGroups.do">Upload groups</a> (the institutions, labs and collections that own specimen records; an upload replaces its uploader's group)</li>
      <li><a href="<%= domainApp %>/manageMuseum.do">Museum Manager</a> (the museum codes of specimen data: name, title, active; add or delete)</li>
      <li><a href="<%= domainApp %>/geolocaleMgr.do">Geolocale Manager</a> (countries and adm1s: valid, live, valid name, bioregion, map; also the georegion and bioregion tables)</li>
      <li><a href="<%= domainApp %>/projectMgr.do">Project Manager</a> (projects such as regional faunas: settings, create, delete)</li>
      <li><a href="<%= domainApp %>/artists.do">Photographers</a> (image credits: add, rename, merge)</li>
      <li><a href="<%= domainApp %>/adminAlert.do">Admin Alert Manager</a> (problems AntWeb recorded for admins, such as a failed AntCat import)</li>
      <li><a href="<%= domainApp %>/bioregionMapMgr.do">Bioregion Map Manager</a> (the bioregions where each genus is native)</li>
      <li><a href="<%= domainApp %>/introducedMapMgr.do">Introduced Map Manager</a> (where each introduced species is native; elsewhere it counts as introduced)</li>
      <li><a href="<%= domainApp %>/query.do">Query Manager</a> (ready-made database checks; they only read data)</li>
      <li><a href="<%= domainApp %>/logMgr.do?action=list">Log Manager</a> (compute.log is the nightly run, admin.log the AntCat imports and scheduler runs; also the profiler log)</li>
    </ul>
</div>

<div class="aw-card aw-admin" id="curate-admin-functions">
    <h2>Administrator functions<span class="aw-badge">Admin</span></h2>
    <ul class="aw-links">
      <li><a href="<%= domainApp %>/statistics.do">Antweb Statistics</a> (statistics tables by project, museum, bioregion and upload)</li>
      <li><a href="<%= domainApp %>/serverStatus.do">Server Status</a> (connections, running jobs, upload down time, pages being served; links to database status and restart status)</li>
      <li><a href="<%= domainApp %>/technical.do">Technical page</a> (reload in-memory lists, regenerate content and statistics, run a nightly set, debugging)</li>
<%
      String content = AntwebUtil.readFile("/data/antweb/web/siteWarning.txt");
      if (content == null || "null".equals(content)) content = "";
%>
      <li id="curate-site-warning"><b>Site Warning</b>: the banner shown at the top of every page. Simple formatting (bold, colour, line breaks, links) is kept.
        <form method="post" action="<%= domainApp %>/utilData.do" id="curate-site-warning-form" style="margin:8px 0 0 0"><%= org.calacademy.antweb.util.Csrf.field(request) %>
          <input type="hidden" name="action" value="siteWarning">
          <textarea name="text" rows="3" style="width:100%;height:4.5em;resize:vertical" aria-label="Site warning text"><%= org.calacademy.antweb.util.Html.esc(content) %></textarea>
        </form>
        <div class="aw-actions">
          <input class="aw-btn" type="submit" value="Save" form="curate-site-warning-form">
          <form method="post" action="<%= domainApp %>/utilData.do" style="display:inline;margin:0"><%= org.calacademy.antweb.util.Csrf.field(request) %>
            <input type="hidden" name="action" value="siteWarning">
            <input type="hidden" name="text" value="">
            <input class="aw-btn aw-btn-secondary" type="submit" value="Clear the warning">
          </form>
        </div>
      </li>
      <% if (AntwebUtil.minsSince(SessionRequestFilter.getInitTime()) >= 10) { %>
      <li><a href="<%= domainApp %>/utilData.do?action=setIsRestart&param=true">Restart server now</a>: the AntWeb application restarts within 5 minutes (never during an upload); the site shows an error page for 2 to 4 minutes while it starts. You are asked to confirm first.</li>
     <% } %>
    </ul>
</div>

<div class="aw-card aw-admin" id="curate-reports">
    <h2>Reports<span class="aw-badge">Admin</span></h2>
    <ul class="aw-links">
      <li><a href="<%= domainApp %>/query.do?action=checkIntegrity">Integrity Check</a> (automatic checks of names, species lists and specimen data, including the name checks against AntCat; each says what to fix)</li>
      <li><a href="<%= domainApp %>/query.do?action=homonyms">Homonym Report</a> (names spelled like another ant name; specimens may be filed under the wrong one)</li>
      <li><a href="<%= domainApp %>/query.do?action=events">Curator activity log</a> (edits to the images, videos and overview text of taxon pages)</li>
      <li><a href="<%= domainApp %>/taxonomicPage.do?rank=species&project=worldants&isImaged=false">Species without images</a> (AntCat species with no image on AntWeb)</li>
      <li><a href="<%= domainApp %>/recentDescEdits.do?action=recentDescEdits">Recent Description Edits</a> (the latest edits to taxon page text, and by whom)</li>
      <li><a href="<%= domainApp %>/list.do?action=casentDAnomalies">Specimen parts (-D codes) with different names</a> (usually an identification to correct in TaxonWorks or the upload file)</li>
      <li><a href="<%= domainApp %>/query.do?action=taxaOutsideOfNativeBioregion">Taxa outside their native bioregions</a> (a list to check for misidentifications or introductions; it only reads data)</li>
      <li><a href="<%= domainApp %>/orphanedImages.do">Orphaned images</a> (images whose specimen record no longer exists)</li>
    </ul>
</div>
<% } %>
