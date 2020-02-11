<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%@ page import="org.calacademy.antweb.Taxon" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<%@include file="/curate/curatorCheck.jsp" %>

<h1>Orphan Manager</h1>

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>

These tools are designed to enable administrators to directly modify data in the Antweb
database to correct for outstanding design flaws.  Caution should be exercised because
there is no undo capabilities built into these tools, and in cases this is the primary
store of this data.  Furthermore, these tools are under development and not at the level
of testing as most other aspects of the site.

<ul>
	  <li>View <a href="<%= domainApp %>/orphanSpecies.do">Orphan Species</a> (Species from a specimen file upload with no correlated specimens or description edits)
	  <li>View <a href="<%= domainApp %>/orphanGenera.do">Orphan Genera</a> (Genera with no correlated species or description edits)
	  <li>View <a href="<%= domainApp %>/dupedGenera.do">Duplicate Genera</a> (Genera found in multiple subfamilies)
	  <li>View <a href="<%= domainApp %>/orphanSubfamilies.do">Orphan Subfamilies</a> (Sufamilies with no correlated genera or description edits)
	  <li>View <a href="<%= domainApp %>/orphanDescEdits.do">Orphan Description Edits</a>
	  <li>View <a href="<%= domainApp %>/orphanAlternates.do">Orphan Alternates</a>
</ul>

<hr>

<h1>Deprecated Tools</h1>
<ul>
 	  <li>View <a href="<%= domainApp %>/orphanTaxons.do">Old Orphan Taxons</a> (Taxons older than the last specimen upload)
</ul>