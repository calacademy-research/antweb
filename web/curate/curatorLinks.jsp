	<div class="clear"></div>

<% if (accessLogin.isAdmin()) { %>
    <h3 align=left>Manager Tools</h3>
    <ul align=left>
      <li><a href="<%= domainApp %>/viewLogins.do">Login Manager</a> (also: all logins with their permissions, logins by last login)
      <li><a href="<%= domainApp %>/viewGroups.do">Group Manager</a>
      <li><a href="<%= domainApp %>/manageMuseum.do">Museum Manager</a>
      <li><a href="<%= domainApp %>/geolocaleMgr.do">Geolocale Manager</a> (countries and adm1s; also the georegion and bioregion tables)
      <li><a href="<%= domainApp %>/projectMgr.do">Project Manager</a>
      <li><a href="<%= domainApp %>/artists.do">Photographers</a> (image credits)
      <li><a href="<%= domainApp %>/adminAlert.do">Admin Alert Manager</a>
      <li><a href="<%= domainApp %>/bioregionMapMgr.do">Bioregion Map Manager</a> (where a species is native)
      <li><a href="<%= domainApp %>/introducedMapMgr.do">Introduced Map Manager</a> (where a species is introduced)
      <li><a href="<%= domainApp %>/query.do">Query Manager</a> (ready-made database checks; they only read data)
      <li><a href="<%= domainApp %>/logMgr.do?action=list">Log Manager</a> (compute.log is the nightly run, admin.log the AntCat imports and scheduler runs; also the profiler log)
    </ul>

    <h3 align=left>Administrator Functions</h3>
    <ul align=left>
      <li><a href="<%= domainApp %>/statistics.do">Antweb Statistics</a>
      <li><a href="<%= domainApp %>/serverStatus.do">Server Status</a> (connections, running jobs, upload down time, pages being served; links to database status and restart status)
      <li><a href="<%= domainApp %>/technical.do">Technical page</a> (reload in-memory lists, regenerate content and statistics, run a nightly set, debugging)
<%
      String content = AntwebUtil.readFile("/data/antweb/web/siteWarning.txt");
      if (content == null || "null".equals(content)) content = "";
%>
      <li>Site Warning: the banner shown at the top of every page. Simple formatting (bold, colour, line breaks, links) is kept.
        <form method="post" action="<%= domainApp %>/utilData.do" style="margin:4px 0"><%= org.calacademy.antweb.util.Csrf.field(request) %>
          <input type="hidden" name="action" value="siteWarning">
          <textarea name="text" rows="2" style="width:95%" aria-label="Site warning text"><%= org.calacademy.antweb.util.Html.esc(content) %></textarea>
          <br><input type="submit" value="Save">
        </form>
        <form method="post" action="<%= domainApp %>/utilData.do" style="margin:0 0 4px 0"><%= org.calacademy.antweb.util.Csrf.field(request) %>
          <input type="hidden" name="action" value="siteWarning">
          <input type="hidden" name="text" value="">
          <input type="submit" value="Clear the warning">
        </form>
      <% if (AntwebUtil.minsSince(SessionRequestFilter.getInitTime()) >= 10) { %>
      <li><a href="<%= domainApp %>/utilData.do?action=setIsRestart&param=true">Restart server now</a>: the AntWeb application restarts within 5 minutes (never during an upload); the site shows an error page for 2 to 4 minutes while it starts. You are asked to confirm first.
     <% } %>
    </ul>

    <h3 align=left>Reports</h3>
    <ul align=left>
      <li><a href="<%= domainApp %>/query.do?action=checkIntegrity">Integrity Check</a> (automatic checks of names, species lists and specimen data, including the name checks against AntCat; each says what to fix)
      <li><a href="<%= domainApp %>/query.do?action=homonyms">Homonym Report</a> (names spelled like another ant name; specimens may be filed under the wrong one)
      <li><a href="<%= domainApp %>/query.do?action=events">Curator activity log</a> (edits to the images, videos and overview text of taxon pages)
      <li><a href="<%= domainApp %>/taxonomicPage.do?rank=species&project=worldants&isImaged=false">Species without images</a> (AntCat species with no image on AntWeb)
      <li><a href="<%= domainApp %>/recentDescEdits.do?action=recentDescEdits">Recent Description Edits</a>
      <li><a href="<%= domainApp %>/list.do?action=casentDAnomalies">Specimen parts (-D codes) with different names</a> (usually an identification to correct in TaxonWorks or the upload file)
      <li><a href="<%= domainApp %>/query.do?action=taxaOutsideOfNativeBioregion">Taxa outside their native bioregions</a> (a list to check for misidentifications or introductions; it only reads data)
      <li><a href="<%= domainApp %>/orphanedImages.do">Orphaned images</a> (images whose specimen record no longer exists)
    </ul>

<% } %>
