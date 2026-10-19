<%@ page errorPage = "/error.jsp" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%@include file="/curate/adminCheck.jsp" %>

<% String domainApp = AntwebProps.getDomainApp(); %>

<div class="admin_left">
<h1>Technical page</h1>
<p><a href="<%= domainApp %>/curate.do">Curator Tools</a></p>

<p>Tools for administrators. Every link that runs a job first shows a confirmation page with a <b>Run it</b> button; nothing runs on the first click.</p>

<h3>Reload the in-memory lists</h3>
<p>AntWeb keeps these lists in memory. After a change made directly in the database, reload the list concerned (or all of them).</p>
<ul>
  <li><a href="<%= domainApp %>/util.do?action=reloadAntwebMgr">Reload all lists</a>
  <li>One list:
    <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=group">groups</a>,
    <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=login">logins</a>,
    <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=project">projects</a>,
    <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=bioregion">bioregions</a>,
    <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=museum">museums</a>,
    <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=geolocale">countries and adm1s (geolocales)</a>,
    <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=taxon">taxa</a>,
    <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=taxonProp">taxon properties</a>,
    <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=allAntweb">the all-AntWeb taxa list</a>,
    <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=upload">uploads</a>,
    <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=artist">photographers</a>,
    <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=adminAlert">admin alerts</a>
</ul>

<h3>Regenerate</h3>
<ul>
  <li><a href="<%= domainApp %>/utilData.do?action=genRecentContent">Recent content on the home page</a> (also done at every start-up)
  <li><a href="<%= domainApp %>/upload.do?action=runStatistics">Statistics</a> (the AntWeb Statistics panel and page)
  <li><a href="<%= domainApp %>/utilData.do?action=regenerateAllAntweb">The all-AntWeb ants project list</a> (a repair tool; not part of the nightly run)
  <li><a href="<%= domainApp %>/util.do?action=logQueryStats">Database query timings</a>, written to queryStats.log (see the Log Manager)
</ul>

<h3>Run the nightly run now</h3>
<p>The nightly run starts at 05:00 Pacific by itself. Running it by hand puts a heavy load on the server for a long time: use it only when a set failed in the night (see compute.log in the <a href="<%= domainApp %>/logMgr.do?action=list">Log Manager</a>).</p>
<ul>
  <li><a href="<%= domainApp %>/schedule.do?action=run&num=1">Set 1</a>: AntCat names, then the CAS specimens from TaxonWorks (up to about 2 hours)
  <li><a href="<%= domainApp %>/schedule.do?action=run&num=2">Set 2</a>: countries and adm1s: clean-up, species lists from specimens, counts (about 45 minutes)
  <li><a href="<%= domainApp %>/schedule.do?action=run&num=3">Set 3</a>: projects, museums and bioregions: lists and counts, current valid names, type specimens (about 15 minutes)
  <li><a href="<%= domainApp %>/schedule.do?action=run&num=4">Set 4</a>: image counts (about 15 minutes)
  <li><a href="<%= domainApp %>/schedule.do?action=run&num=5">Set 5</a>: maps and checks (about 50 minutes)
  <li><a href="<%= domainApp %>/schedule.do?action=run">All five sets</a> (several hours)
</ul>

<h3>Debugging</h3>
<ul>
  <li><a href="<%= domainApp %>/serverDebug.do">Server Debug</a>: show or set the server debug flag
  <li><a href="<%= domainApp %>/sessionProps.do">Session Properties</a>: what is stored in your own session
</ul>
</div>
