<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<title>Status Display Page</title>

<h2><u>Antweb Status Display Page</u></h2>

<br><br>
<h3><u>Antcat Taxon Statuses</u></h3>
<img src="<%= AntwebProps.getDomainApp() %>/image/valid_name.png" width="11" height="12" border="0"> <b>Valid</b> - This taxon is described as valid in the Bolton World Catalog.
<p><img src="<%= AntwebProps.getDomainApp() %>/image/homonym.png" width="11" height="12" border="0"> <b>Homonym</b> - This name conflicts with an existing ant's taxon name.  The author and date of the referenced publication distinguishes it from the taxon of the same name.
<p><img src="<%= AntwebProps.getDomainApp() %>/image/synonym.png" width="11" height="12" border="0"> <b>Synonym</b> - An additional name used for a taxon that has a valid name.
<p><img src="<%= AntwebProps.getDomainApp() %>/image/collectiveGroupName.png" width="11" height="12" border="0"> <b>Collective group name</b>
<p><img src="<%= AntwebProps.getDomainApp() %>/image/excludedFromFormicidae.png" width="11" height="12" border="0"> <b>Excluded from Formicidae</b>
<p><img src="<%= AntwebProps.getDomainApp() %>/image/originalCombination.png" width="11" height="12" border="0"> <b>Original Combination</b>
<p><img src="<%= AntwebProps.getDomainApp() %>/image/obsoleteCombination.gif" width="11" height="12" border="0"> <b>Obsolete Combination</b>
<p><img src="<%= AntwebProps.getDomainApp() %>/image/unidentifiable.jpg" width="11" height="12" border="0"> <b>Unidentifiable</b> - (= Nomen dubium). A name used without sufficient information so that currently, it is not possible to determine what taxon the name applies to.
<p><img src="<%= AntwebProps.getDomainApp() %>/image/unavailable.jpg" width="11" height="12" border="0"> <b>Unavailable</b>
<p><img src="<%= AntwebProps.getDomainApp() %>/image/uu.png" width="11" height="12" border="0"> <b>Unavailable Uncategorized</b>
<p><img src="<%= AntwebProps.getDomainApp() %>/image/um.png" width="11" height="12" border="0"> <b>Unavailable Misspelling</b>

<br><br>
<h3><u>Antweb Taxon Statuses</u></h3>
<p><img src="<%= AntwebProps.getDomainApp() %>/image/unrecognized.png" width="11" height="12" border="0"> <b>Unrecognized</b> - This ant is not valid (listed in the Bolton World catalog) and it is not a morphotaxon or indetermined.
<p><img src="<%= AntwebProps.getDomainApp() %>/image/morphoTaxon.gif" width="11" height="12" border="0"> <b>Morphotaxon</b> - Morphospecies codes are used to indicate "indetermined" or "undescribed" species. This taxon comes from a specimen file, and is not a "valid" taxon name.  May be recognized by it's taxon name which will contain at least one of the following (1, 2, 3, 4, 5, 6, 7, 8, 9, 0, "-", "_", "(", ")", ".").  Also included are single letter names.
<p><img src="<%= AntwebProps.getDomainApp() %>/image/indetermined.png" width="11" height="12" border="0"> <b>Indetermined</b> - This taxon comes from a specimen file and has been flagged in it's name as indet (or undet).

<br><br><br><br>
<h3><u>Antweb Status Sets</u></h3>

<p><b>Worldants</b> - Ants described by Barry Bolton and contains in the Bolton World Catalog.  Data curated by Antcat.org.
<p><b>All</b> - All Antweb.  This specification includes all specimen, regardless of status.  Will exclude some taxa without specimen depending on status.
<p><b>Complete</b> - This will include all taxa, regardless of source or status.
<p><b>Valid Without Fossil</b> - All valid taxa that are not fossils.
<a href="#type">
<p><b>Type</b> - Only display taxa that have a type specimen.
</a>
<p><b>All Determined</b> - All valid, unrecognized, not current valid name, and morphotaxa with a name NOT like "(indet)"
<p><b>All Indetermined</b> - All valid, unrecognized, not current valid name, and morphotaxa with a name LIKE "(indet)"


