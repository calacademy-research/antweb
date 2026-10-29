<%-- Body of the Country Documentation page (admins): old notes on maintaining geolocale data, a to-do list and
     developer notes (static text). Read-only. --%>
<%@ page import="org.calacademy.antweb.util.*" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>


<%@include file="/curate/adminCheck.jsp" %>

<%
  String domainApp = AntwebProps.getDomainApp();
%>

<div class="admin_left">

<H1>Antweb Country Documentation</H1>

<hr>

<h3>Deployment and Maintenance Process</h3>

<ul>
<li>(Engineer) Curate the geolocale data (regions, subregions, countries, adm1) in the <a href="<%= domainApp %>/geolocaleMgr.do">Geolocale Mgr</a>.
<li>Country and adm1 species lists are built each night from specimen records (nightly set 2).
<br>The AntWiki Regional Taxon List import and the older country import tools were retired (cleanup batch 3, 2026-10).
</ul>

<br><br>

<H3>Data Files</H3>
<a href="<%= domainApp %>/data/">Data Dir</a>

<br><br>

<H3>To Do (incomplete)</H3>
To Go Live
<ul>
<li>Georegion Menu function.
</ul>
To Be Full Featured
<ul>
<li>Adm1 functionality.  Data from where?
<li>Regions (like Americas) displayed split (North America and South America).  North America is subregions Northern America, Central America and Carribean.
<li>Bioregion pages (new specimen driven Taxonomic page functionality) and menu.
</ul>
To Be Admin Maintainable
<ul>
</ul>

<br>

<h3>Dev Notes</H3>
* taxon_country table seems to be non-functional.  Referenced in code during Species List Upload.  Deprecate.
<br>* Country table is to be replaced/removed.

</div>
