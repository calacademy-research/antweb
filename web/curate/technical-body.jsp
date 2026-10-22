<%@ page errorPage = "/error.jsp" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%@include file="/curate/adminCheck.jsp" %>

<% String domainApp = AntwebProps.getDomainApp(); %>

<%-- Tool-page look (common/tools.css, docs/guides/page-style.md; Brian Fisher, 2026-10-22). Every link and
     what it does is unchanged. Tested in tests/e2e/test_tool_pages_style.py. --%>
<%@include file="/common/toolsHead.jsp" %>
<div class="aw-tools" id="technical-page">
<p class="aw-crumbs"><a href="<%= domainApp %>/curate.do">Curator Tools</a></p>
<h1>Technical page</h1>
<div class="aw-intro">
<p>Tools for administrators: reload what AntWeb keeps in memory, rebuild generated content, run the nightly run by hand, and debugging.
Every link that runs a job first shows a confirmation page with a <b>Run it</b> button; nothing runs on the first click.</p>
</div>

<div class="aw-card" id="technical-reload">
<h2>Reload the in-memory lists</h2>
<p class="aw-lead">AntWeb keeps these lists in memory. After a change made directly in the database, reload the list concerned (or all of them).</p>
<ul class="aw-links">
  <li><a href="<%= domainApp %>/util.do?action=reloadAntwebMgr">Reload all lists</a></li>
  <li>One list:
    <ul class="aw-inline">
    <li><a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=group">groups</a></li>
    <li><a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=login">logins</a></li>
    <li><a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=project">projects</a></li>
    <li><a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=bioregion">bioregions</a></li>
    <li><a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=museum">museums</a></li>
    <li><a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=geolocale">countries and adm1s (geolocales)</a></li>
    <li><a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=taxon">taxa</a></li>
    <li><a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=taxonProp">taxon properties</a></li>
    <li><a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=allAntweb">the all-AntWeb taxa list</a></li>
    <li><a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=upload">uploads</a></li>
    <li><a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=artist">photographers</a></li>
    <li><a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=adminAlert">admin alerts</a></li>
    </ul>
  </li>
</ul>
</div>

<div class="aw-card" id="technical-regenerate">
<h2>Regenerate</h2>
<p class="aw-lead">Rebuild content that AntWeb generates from the database.</p>
<ul class="aw-links">
  <li><a href="<%= domainApp %>/utilData.do?action=genRecentContent">Recent content on the home page</a> (also done at every start-up)</li>
  <li><a href="<%= domainApp %>/upload.do?action=runStatistics">Statistics</a> (the AntWeb Statistics panel and page)</li>
  <li><a href="<%= domainApp %>/utilData.do?action=regenerateAllAntweb">The all-AntWeb ants project list</a> (a repair tool; not part of the nightly run)</li>
  <li><a href="<%= domainApp %>/util.do?action=logQueryStats">Database query timings</a>, written to queryStats.log (see the Log Manager)</li>
</ul>
</div>

<div class="aw-card" id="technical-nightly">
<h2>Run the nightly run now</h2>
<p class="aw-lead">The nightly run starts at 05:00 Pacific by itself. Running it by hand puts a heavy load on the server for a long time: use it only when a set failed in the night (see compute.log in the <a href="<%= domainApp %>/logMgr.do?action=list">Log Manager</a>).</p>
<ul class="aw-links">
  <li><a href="<%= domainApp %>/schedule.do?action=run&num=1">Set 1</a>: AntCat names, then the CAS specimens from TaxonWorks (up to about 2 hours)</li>
  <li><a href="<%= domainApp %>/schedule.do?action=run&num=2">Set 2</a>: countries and adm1s: clean-up, species lists from specimens, counts (about 45 minutes)</li>
  <li><a href="<%= domainApp %>/schedule.do?action=run&num=3">Set 3</a>: projects, museums and bioregions: lists and counts, current valid names, type specimens (about 15 minutes)</li>
  <li><a href="<%= domainApp %>/schedule.do?action=run&num=4">Set 4</a>: image counts (about 15 minutes)</li>
  <li><a href="<%= domainApp %>/schedule.do?action=run&num=5">Set 5</a>: maps and checks (about 50 minutes)</li>
  <li><a href="<%= domainApp %>/schedule.do?action=run">All five sets</a> (several hours)</li>
</ul>
</div>

<div class="aw-card" id="technical-debugging">
<h2>Debugging</h2>
<ul class="aw-links">
  <li><a href="<%= domainApp %>/serverDebug.do">Server Debug</a>: show or set the server debug flag</li>
  <li><a href="<%= domainApp %>/sessionProps.do">Session Properties</a>: what is stored in your own session</li>
</ul>
</div>
</div>
