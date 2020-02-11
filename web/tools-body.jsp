<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<div id="page_contents">
<h1>AntWeb Tools</h1>
<jsp:include page="about_nav.jsp" />
<div class="clear"></div>
<div class="page_divider"></div>
</div>
<!-- /div -->
<div id="page_data">
<div id="overview_data">

AntWeb provides many facilities to aid you in your discovery of the ant world.  Among these are:

<% String domainApp = (new org.calacademy.antweb.Utility()).getDomainApp(); %>
<p>
<h3>Search Tools</h3>
Our <a href="<%= domainApp %>/advSearch.do">advanced search</a> let you find
ants in a variety of ways, including: taxonomy, location, and collector.  You may also search for 
ants which have images, and type specimens.

<h3>Regional Lists</h3>
View regional lists of ant subfamilies, genera and <a href="<%= domainApp %>/taxonomicPage.do?rank=species&project=madagascarants">species.</a>

<h3>In Depth Information</h3>
View <a href="<%= domainApp %>/description.do?subfamily=proceratiinae&genus=proceratium&species=californicum&rank=species&project=californiaants">description pages</a> of ant subfamilies, genera and species to get more details about them.

<h3>Ant Image Comparison Tool</h3>
The AntWeb <a href="<%= domainApp %>/getComparison.do?rank=genus&genus=thaumatomyrmex&project=worldants">image comparison tool</a> lets you compare images of ants at the subfamily, genus, species or specimen level.  You may also specify which types
of images you would like to compare: head, profile, dorsal, or label.

<h3>Web-based Field Guides</h3>
Take AntWeb to the field with you.  Create a field guide from a taxon page, from a regional page, or from a search result.  You may use your browser's "Save Page As..." feature to save the page for offline access.  Here is an example: <a href="<%= domainApp %>/fieldGuide.do?subfamily=myrmicinae&genus=aphaenogaster&project=californiaants">field guide.</a>  

<h3>Maps on AntWeb</h3>
You can pinpoint the location of ants using <a href="<%= domainApp %>/description.do?rank=genus&genus=aphaenogaster&project=californiaants">maps on AntWeb</a> pages.
   

</div>
</div> 
