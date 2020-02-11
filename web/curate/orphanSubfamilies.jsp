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
<h1>Orphaned Subfamilies</h1>

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a> | <a href="<%= domainApp %>/orphanMgr.do">Orphan Manager</a><br><br><br>

<hr></hr>
Listed here are subfamilies that are believed to be "orphans".  In this case that means that
{ edit: it has no specimen records, and no description edits, and it's source is not a project list.  
Deleting this taxon will delete it from the taxon table, and delete any associated 
favorite_images records. }

<% String source = ""; %>

<logic:iterate id="orphan" name="orphans" indexId="orphanCount">
<% String lastSource = source;
   source = ((Taxon) orphan).getSource();
   if (!source.equals(lastSource)) { %>
   <hr>
   <h3>Source: <%= source %></h3>
     Delete all subfamilies (listed below) for this source (edit: that don't have specimens and don't have description edits): <a href="<%= domainApp %>/orphanSubfamilies.do?source=<%= source %>&action=delete"><img border=0" src="<%= domainApp %>/image/delete.png"></a>
   <hr>
<% } %>

<% int orphan_index = orphanCount.intValue() + 1; %>
<bean:write name="orphan" property="created"/>
<a href="<%= domainApp %>/orphanSubfamilies.do?taxonName=<%= ((Taxon) orphan).getTaxonName() %>&action=delete"><img border=0" src="<%= domainApp %>/image/delete.png"></a>
&nbsp;&nbsp;&nbsp; <bean:write name="orphan" property="rank"/>:
 <%= orphan_index %>. <a href="<bean:write name="orphan" property="url"/>"><bean:write name="orphan" property="taxonName"/></a> 
<hr></hr>
</logic:iterate>


<hr></hr>

</div>
