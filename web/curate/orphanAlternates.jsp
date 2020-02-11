<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="java.util.*" %>

<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<%@include file="/curate/curatorCheck.jsp" %>

<bean:define id="project" value="" toScope="session"/> 
<jsp:useBean id="subfamilies" scope="request" class="java.util.ArrayList" />
<% ArrayList<String> antwebSubfamilies = (ArrayList<String>) request.getAttribute("antwebSubfamilies"); %>
 
<%@include file="/common/antweb_admin-defs.jsp" %>

<jsp:useBean id="uploads" scope="request" class="java.util.ArrayList" />
<jsp:useBean id="orphans" scope="request" class="java.util.ArrayList" />

<div class=left>
<h1>Orphaned Alternates</h1>

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a> | <a href="<%= domainApp %>/orphanMgr.do">Orphan Manager</a><br><br><br>


<%
  String statusMessage = (String) request.getAttribute("statusMessage"); 
  if (statusMessage != null) out.print(statusMessage);
%>

<hr></hr>
Listed here are taxa "orphans" with invalid parents and no specimens.  The taxa was created during the specimen
upload process but the taxa name has changed, and it was not automatically moved during a specimen file upload.
This tool will move any non-conflicting supporting data (proj_taxa, description_edits, etc...) or 
remove them in the case of conflict.  This is not reversible.

<% String source = "";
   int i = 0;
 %>

<logic:iterate id="orphan" name="orphans" indexId="orphanCount">
<% String lastSource = source;
   source = ((Taxon) orphan).getSource();
   if (!source.equals(lastSource)) { %>
   <hr>
   <h3>Source: <%= source %></h3>
   <hr>
<% } 
   String antwebSubfamily = antwebSubfamilies.get(i);
%>


<html:form method="POST" action="orphanAlternates.do" enctype="multipart/form-data">
    <input type="hidden" name="action" value="moveOrDelete" />
    <input type="hidden" name="taxonName" value="<%= ((Taxon) orphan).getTaxonName() %>" />
    <div class="admin_action_module">
        <div class="admin_action_item">
            <div class="action_dropdown">

<% int orphan_index = orphanCount.intValue() + 1; %>
<bean:write name="orphan" property="created"/>
&nbsp;&nbsp;&nbsp; Move species:
 <%= orphan_index %>. <a href="<bean:write name="orphan" property="fullUrl"/>"><bean:write name="orphan" property="taxonName"/></a> 

&nbsp;&nbsp;&nbsp;to &nbsp;&nbsp;Subfamily:<html:select property="subfamily" value="<%= antwebSubfamily %>" >
  	<logic:iterate id="subfamily" collection="<%= subfamilies %>">
    	<html:option value='<%= (String) subfamily %>'><%= (String) subfamily %></html:option>
    </logic:iterate>
  </html:select>
   
<html:submit property="browse"> 
browse
</html:submit>
&nbsp;&nbsp;&nbsp;<input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="Submit">
            </div>
        </div>
    </div>
</html:form>
 
   
<hr></hr>

<%    ++i;
 %>
</logic:iterate>


<hr></hr>

</div>
