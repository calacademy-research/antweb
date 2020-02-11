<%@ page language="java" %>
<%@ page import = "org.calacademy.antweb.*" %>
<%@ page import = "org.calacademy.antweb.util.*" %>
<%@ page import = "java.util.*" %>

<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<div id="page_contents">
    <h1>Recent Description Edits</h1>
	<div class="clear"></div>
	<div class="page_divider"></div>
</div>
<!-- /div -->
<div id="page_data">

<div class="browse_col_one"><span class="col_header"><b>Ant Page</b></span></div>
<div class="browse_col_two"><span class="col_header"><b>Title</b></span></div>
<div class="browse_col_three"><span class="col_header"><b>Created</b></span></div>
<div class="browse_col_four"><span class="col_header"><b>Curator</b></span></div>
<div class="clear"></div>
<hr></hr>


<%
   String domainApp = AntwebProps.getDomainApp();
   String antPage = "";
   ArrayList<DescEdit> descEdits = (ArrayList<DescEdit>) request.getAttribute("descEdits");
   if (descEdits == null) {
     AntwebUtil.log("descEditReport-body.jsp WSS descEdit not in request.  Return white page?");
     return;
   }
    
   for (DescEdit descEdit : descEdits) {
      
       //if (descEdit.isSpecimen()) AntwebUtil.log("descEditReport-body.jsp antPage:" + antPage + " descEdit.antPage:" + descEdit.getAntPage() + " antLink:" + descEdit.getAntLink());   
      
      
       if (antPage.equals(descEdit.getAntPage())) {
        %>
          <div class="browse_col_one"></div>
<%     } else {
          antPage = descEdit.getAntPage(); %>
          <div class="browse_col_one"><%= descEdit.getAntLink() %></div>
<%     } %>
       <div class="browse_col_two"><%= descEdit.getTitle() %></div>
       <div class="browse_col_three"><%= descEdit.getCreated() %></div>
<%
       if (descEdit.getAccessLogin() != null) { %>
           <div class="browse_col_four"><%= descEdit.getAccessLogin().getName() %></div>
<%        } %>
    <div class="clear"></div>
    <hr></hr>
<% } %>

</div>
