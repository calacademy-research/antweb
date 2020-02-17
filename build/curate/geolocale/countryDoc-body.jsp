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
  
<br>
<b>It is safe to perform the following actions.  Changes made are additive and non-destructive.
<br>If a step is enacted, then enact the following steps.
</b>
<br>
<br>

<li>(Engineer) Curate the geolocale data (regions, subregions, countries, adm1) in the <a href="<%= domainApp %>/geolocaleMgr.do">Geolocale Mgr</a>
<br><br>
 
<li>Load the Antwiki Regional Taxon List:".  

<!-- Data File Upload -->
<% if (LoginMgr.isAdmin(request)) { %>
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

  <br>Hit submit without file chosen for documentation on Regional Taxon Lists.
  <br>Instances of Files are stored in the <a href="<%= domainApp %>/data/">Data Dir</a> directory.  See: AntWiki_Regional_Taxon text files.
  <br>Populates the antwiki_taxon_country table and includes: 
  <br><ul>
    <li><a href="<%= domainApp %>/util.do?action=updateTaxonCountryProject">updateTaxonCountryProject</a> to populate the antwiki_taxon_country project_name field.
  </ul><br><br>

  <li><a href="<%= domainApp %>/util.do?action=finishCountryUpload">Finish</a> Country Upload Process.
  <ul>
    <li>
    <a href="<%= domainApp %>/util.do?action=populateFromCountrySpecimenData">Populate</a> the proj_taxon table from the specimen table country data.
    <br>&nbsp;&nbsp;&nbsp;To <a href="<%= domainApp %>/util.do?action=undoPopulateFromSpecimenData">Undo</a> populate proj_taxon table from Specimen data.
    <br><br>
    <a href="<%= domainApp %>/util.do?action=populateFromAdm1SpecimenData">Populate</a> the proj_taxon table from the specimen table adm1 data.
    <br>&nbsp;&nbsp;&nbsp;To <a href="<%= domainApp %>/util.do?action=undoPopulateFromAdm1SpecimenData">Undo</a> populate proj_taxon table from Adm1 Specimen data.
    <br><br>

    <li><a href="<%= domainApp %>/util.do?action=populateFromAntwikiData">Populate</a> the proj_taxon table from the antwiki_taxon_country table.
    <br>&nbsp;&nbsp;&nbsp;To <a href="<%= domainApp %>/util.do?action=undoPopulateFromAntwikiData">Undo</a> populate proj_taxon table from Antwiki data.
    <br><br>
  
    <li><a href="<%= domainApp %>/util.do?action=introducedSpecimen">Introduced Specimen</a> to populate the specimen is_introduced field.
  </ul>
  
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
<li>Antwiki Regional Taxon List rev system?
</ul>
To Be Admin Maintainable
<ul>
</ul>

<br>

<h3>Dev Notes</H3>
* taxon_country table seems to be non-functional.  Referenced in code during Species List Upload.  Deprecate.
<br>* Country table is to be replaced/removed.

<ul>
      <li><a href="<%= domainApp %>/getAntWikiData.do">AntwikiData test</a>      
</ul>      



<ul>
<h3>Retired Functionality</h3>
<br>
<b>It is not necessarily safe to perform the Engineer actions.</b>
<br><br>


<li>(Engineer) Load the the Antwiki Country data: <a href="<%= domainApp %>/util.do?action=pushCountryData">/util.do?action=pushCountryData</a>.
  <ul>
    <li>Get Country Data</li>
      Originally fetched from here: http://www.antwiki.org/wiki/index.php?title=Countries_by_Regions&action=edit 
      <br>Archived in our source tree here: /web/data/antwikiCountriesByRegions.html
      <br>Loaded into the un_country table.  
    <li>Populate the Geolocale table</li>
      Regions, subregions and countries.
      <br>Modify the bioregion table structure and content.
    <li>Get Country Bounds</li>
      The page of data: http://wiki.openstreetmap.org/wiki/User:Ewmjc/Country_bounds has been massaged and saved in out source tree (here:web/data/countryBounds.txt).
      <br>Populate the extents field of the geolocale table.
  </ul>   
  <br>
  
<li>(Engineer) Push Gelocale Countries to Project
  <br>This can be done in bulk: <a href="<%= domainApp %>/util.do?action=pushCountriesToProjects">/util.do?action=pushCountriesToProjects</a>
  <br>If key conflicts: <a href="<%= domainApp %>/util.do?action=undoPushCountriesToProjects">/util.do?action=undoPushCountriesToProjects</a>
  <br>Or individually by following the [country]ants link at the bottom of the page, and then saving it.
  <br>Create projects for valid countries (not necessarily UN, but valid in Antweb).
  <br><br>

</ul>

</div>
