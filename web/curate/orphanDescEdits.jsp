<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%@ page import="java.util.*" %>

<%@include file="/curate/curatorCheck.jsp" %>

<bean:define id="project" value="" toScope="session"/> 
 
<%@include file="/common/antweb_admin-defs.jsp" %>

<title>Orphan Description Edits</title>
<% String domainApp = (new Utility()).getDomainApp(); 

   ArrayList<Taxon> orphanDescEditTaxons = (ArrayList<Taxon>) request.getAttribute("orphanDescEditTaxons");
%>

<div class=left>
<h1>Orphan Description Edits</h1>
<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a> | <a href="<%= domainApp %>/orphanMgr.do">Orphan Manager</a><br><br><br>

<%
    String message = (String) request.getAttribute("message"); 
    if (message != null) out.println("<font color=green>" + message + "</font><br>");
%>

<hr></hr>
Be careful with this tool.  There is no undo function, and any errors made through
this tool could have lasting deleterious effects.  Direct any question or concerns to Brian
Fisher.  Thank you.
<br><br>
Listed here are Description Edit records that are do not map to any existing taxon.  
Quite likely the taxon has been renamed.  You may delete the Description Edit record (perhaps you 
manually cut and paste it's contents into the appropriate taxon) or you may select another taxon 
to associate with these records.
<br>
<br>
If the taxon name is a hyperlink, then that taxon does exist without correlated specimens.
Transfer or deletion of the description edit will remove it from this tool and enable the taxon to appear and be
deleted via the Orphan Taxon tool.

<br><br>
First the <b><a href="#noSpecimenOrphans">No Specimen Orphans</a></b> are displayed. Followed by the <b><a href="#noTaxonOrphans">No Taxon Orphans</a></b>.
<br>
<hr>
<br>
<br>
<br>
<br>
<a name="noSpecimenOrphans"><h2><u>No Specimen Orphans</u></h2></a>

<% 
int count = 0;
boolean displayNoTaxonOrphans = false;

for (Taxon orphanDescEditTaxon : orphanDescEditTaxons) {

	++count;

	String taxonHeading = null;
	boolean hasNoSpecimens = !(orphanDescEditTaxon instanceof DummyTaxon); 
	if (hasNoSpecimens) {
	  taxonHeading = "<a href=\"" + domainApp + "/description.do?taxonName=" + orphanDescEditTaxon.getTaxonName() 
		+ "\">" + orphanDescEditTaxon.getTaxonNameDisplay() + "</a> (no specimens)";
	} else {
	  if (!displayNoTaxonOrphans) { %>
		<br><br><br><br><a name="noTaxonOrphans"><h2><u>No Taxon Orphans</u></h2></a>
	  <%
		displayNoTaxonOrphans = true; 
	  }
	  taxonHeading = orphanDescEditTaxon.getFullName() + " (no taxon associated)";
	  //if (TaxonMgr.
	}
	%>
	<h3><%= count %>. <%= taxonHeading %></h3>

	<%
if (orphanDescEditTaxon.getCurrentValidName() != null && !"".equals(orphanDescEditTaxon.getCurrentValidName())) {
  A.log("orphanDescEdits.jsp taxonName:" + orphanDescEditTaxon.getTaxonName() + " current:" + orphanDescEditTaxon.getCurrentValidName());
}

	  String deleteAction  = "delete";
	  if (hasNoSpecimens) deleteAction = "deleteTaxon";
	%>
	Delete <a href="<%= domainApp %>/orphanDescEdits.do?taxonName=<%= orphanDescEditTaxon.getTaxonName() %>&action=<%= deleteAction %>">
	   <img border=0" src="<%= domainApp %>/image/delete.png"></a> these description edits
	<b>Or</b> transfer
	  <a href="<%= domainApp %>/orphanDescEdits.do?taxonName=<%= orphanDescEditTaxon.getTaxonName() %>&action=transferPage">
	  <img border=0" src="<%= domainApp %>/image/rightArrow.png"></a> them to another taxon.<br><br>

	<%
	   Hashtable descriptions = ((Taxon) orphanDescEditTaxon).getDescription();
	   boolean hasKey = false;
	   for (Object key : descriptions.keySet() ) { 
		 hasKey = true; %>
		 key:<b><%= key %></b> <br>value: <%= descriptions.get(key) %> <br>
	<% } 
	   if (!hasKey) out.println("<br>No key<br>");
	%>

	<hr></hr>
<%
}
%>

<br><br>

</div > 
