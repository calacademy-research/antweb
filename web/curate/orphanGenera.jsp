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

<bean:define id="project" value="" toScope="session"/> 
 
<%@include file="/common/antweb_admin-defs.jsp" %>

<jsp:useBean id="uploads" scope="request" class="java.util.ArrayList" />
<jsp:useBean id="orphans" scope="request" class="java.util.ArrayList" />

<div class=left>
<h1>Orphaned Genera</h1>

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a> | <a href="<%= domainApp %>/orphanMgr.do">Orphan Manager</a><br><br><br>

<hr></hr>
Listed here are genera that are believed to be "orphans".  In this case that means that
{ edit: it has no specimen records, and no description edits, and it's source is not a project list.  
Deleting this taxon will delete it from the taxon table, and delete any associated 
favorite_images records. }

<% String source = ""; %>

<logic:iterate id="orphan" name="orphans" indexId="orphanCount">
<% 
   Taxon orphanTaxon = (Taxon) orphan;
   String lastSource = source;
   source = orphanTaxon.getSource();
   if (!source.equals(lastSource)) { %>
   <hr>
   <h3>Source: <%= source %></h3>
     Delete all genera (listed below) for this source (that don't have specimens and don't have description edits): <a href="<%= domainApp %>/orphanGenera.do?source=<%= source %>&action=delete"><img border=0" src="<%= domainApp %>/image/delete.png"></a>
   <hr>
<% } %>

<% int orphan_index = orphanCount.intValue() + 1; 
A.log("orphanGenera.jsp orphan:" + orphan + " taxonName:" + orphanTaxon.getTaxonName() + " subfamily:" + orphanTaxon.getSubfamily() + " genus:" + orphanTaxon.getGenus());
%>
<%= orphan_index %>. <bean:write name="orphan" property="rank"/>:
<a href="<%= orphanTaxon.getUrl() %>"><%= orphanTaxon.getTaxonName() %></a> 
<b>Created:</b><%= orphanTaxon.getCreated() %>
<%
// Handle exceptional cases
if ("(formicidae)".equals(orphanTaxon.getSubfamily()) && "null".equals(orphanTaxon.getGenus())) { %>
  <a href="<%= domainApp %>/orphanGenera.do?taxonName=(formicidae)null&action=delete"><img border=0" src="<%= domainApp %>/image/delete.png"></a>
<%
} else {
  // this is the default! %>
  <a href="<%= domainApp %>/orphanGenera.do?taxonName=<%= orphanTaxon.getTaxonName() %>&action=delete"><img border=0" src="<%= domainApp %>/image/delete.png"></a>
<%
}
%>
<hr></hr>
</logic:iterate>


<hr></hr>

</div>
