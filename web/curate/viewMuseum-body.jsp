<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.Museum" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<%@include file="/curate/adminCheck.jsp" %>

<%
	    Museum thisMuseum = (Museum) request.getAttribute("museum");
	    if (thisMuseum == null) thisMuseum = new Museum();
%>

<div class=left>
<h1>&nbsp;Museum Form</h1>
</div>


<div class=right>
<br>
<a href="<%= AntwebProps.getDomainApp() %>/manageMuseum.do">Museum Manager</a>
</div>

<div class=admin_left>

<% String message = (String) request.getAttribute("message");
   if (message != null) { %>
     <font color=red><%= message %></font>
<% } %>

    <logic:messagesPresent property="error">
        <h2>Sorry, you need to correct the following errors:</h2>
        <font color="red">
        <html:messages property="error" id="errMsg">
            <bean:write name="errMsg"/><br>
        </html:messages>
        </font>
    </logic:messagesPresent>

    <form action="<%= domainApp %>/viewMuseum.do" method="POST">

    <p><p><b>Museum Code:</b>
    <input type="text" class="input_200" name="code" value="<%= thisMuseum.getCode() %>">

    <p><p><b>Museum Name:</b>
    <input type="text" class="input_200" name="name" value="<%= thisMuseum.getName() %>">

    <p><p><b>Museum Title:</b>
    <input type="text" class="input_200" name="title" value="<%= thisMuseum.getTitle() %>">

    <p><b>Active:</b>
    <input type="checkbox" name="isActive" <%= (thisMuseum.getIsActive() == true)?"checked":"" %>>

    <div class="msg_actions" align="center">
<input border="0" type="image" src="<%= domainApp %>/image/orange_done.gif" width="98" height="36" name="done" value="Done">
<a href="<%= domainApp %>/manageMuseum.do"><img border=0" src="<%= domainApp %>/image/grey_cancel.gif" width="123" height="36"></a>

<!-- input border="0" type="image"  width="98" height="36" name="delete" value="Delete" -->
<html:submit property="step"> 
<bean:message key="button.delete"/> 
</html:submit>


    </div>


</form>
</div>