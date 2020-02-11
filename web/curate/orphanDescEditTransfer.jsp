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

<% String domainApp = (new Utility()).getDomainApp(); %>

<jsp:useBean id="orphanDescEditTaxon" scope="request" class="org.calacademy.antweb.DummyTaxon" />
<jsp:useBean id="subfamilies" scope="request" class="java.util.ArrayList" />
<jsp:useBean id="genera" scope="request" class="java.util.ArrayList" />
<jsp:useBean id="speciesList" scope="request" class="java.util.ArrayList" />

<div class=left>
<h1>Orphan Description Edit Transfer</h1>
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

<hr></hr>
<% 

String taxonHeading = ((Taxon) orphanDescEditTaxon).getLongPrettyTaxonName() + " (no taxon)";
if (!(orphanDescEditTaxon instanceof DummyTaxon)) {
  taxonHeading = "<a href=\"" + domainApp + "/description.do?taxonName=" + ((Taxon) orphanDescEditTaxon).getTaxonName() + "\">" + ((Taxon) orphanDescEditTaxon).getTaxonName() + "</a> (no specimens)";
}
%>
<h3><%= taxonHeading %></h3>

<html:form method="POST" action="orphanDescEdits.do" enctype="multipart/form-data">
    <input type="hidden" name="action" value="transfer" />
    <input type="hidden" name="taxonName" value="<%= ((Taxon) orphanDescEditTaxon).getTaxonName() %>" />
    <div class="admin_action_module">
        <div class="admin_action_item">

            <div class="action_dropdown">
Transfer <%= orphanDescEditTaxon.getLongPrettyTaxonName() %> description edits to taxon:
<br><br><b>Using:</b> 
<table><tr><td>
&nbsp;&nbsp;&nbsp;Taxonomy:

  Subfamily:<html:select property="subfamily">
  	<html:option value=''>none</html:option>
  	<logic:iterate id="subfamily" collection="<%= subfamilies %>">
    	<html:option value='<%= (String) subfamily %>'><%= (String) subfamily %></html:option>
    </logic:iterate>
  </html:select>

  Genus: <html:select property="genus">
  	<html:option value=''>none</html:option>
  	<logic:iterate id="genus" collection="<%= genera %>">
    	<html:option value='<%= (String) genus %>'><%= (String) genus %></html:option>
    </logic:iterate>
  </html:select>

  Species: <html:select property="species">
  	<html:option value=''>none</html:option>
  	<logic:iterate id="species" collection="<%= speciesList %>">
    	<html:option value='<%= (String) species %>'><%= (String) species %></html:option>
    </logic:iterate>
  </html:select>

  <html:submit property="browse"> 
  browse
  </html:submit>

<br>
<b>Or:</b>
<br>&nbsp;&nbsp;&nbsp;enter unique Antweb taxonName identifier: <input type="text" name="toTaxonName" value="" style="border: 1px solid #B9B9B9; font-family: inherit; padding: 2px; position: relative; top: -1px; width: 300px;">
  <html:submit property="browse"> 
  browse
  </html:submit>


 <%@ include file="/curate/suggestedTaxa.jsp" %>
   
</td><td>

&nbsp;&nbsp;&nbsp;<input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="Submit">

</td></tr>
</table>

            </div>
        </div>
    </div>
</html:form>

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

<br><br>

<!--
<H1>Taxon Orphans with Description Edit records</H1>
Listed here are taxons that are "orphans" (specimen file uploaded taxons with no correlated specimens) that DO have Description Edit records.
When these description_edit records are transfered or deleted, these taxons will be found in the Orphaned Taxons tool, ready for deletion.
<hr>
<br>

<% String source = ""; %>
<jsp:useBean id="orphanTaxonWithDescEditList" scope="request" class="java.util.ArrayList" />
<logic:iterate id="descEditOrphan" name="orphanTaxonWithDescEditList" indexId="orphanCount">
<% 
   source = ((Taxon) descEditOrphan).getSource();
%>

<% int orphan_index = orphanCount.intValue() + 1; %>
<bean:write name="descEditOrphan" property="created"/>
&nbsp;&nbsp;&nbsp;<%= source %>
&nbsp;&nbsp;&nbsp; <%= orphan_index %>. <a href="<bean:write name="descEditOrphan" property="url"/>"><bean:write name="descEditOrphan" property="taxonName"/></a> 
<hr></hr>
</logic:iterate>

 -->

</div > 
