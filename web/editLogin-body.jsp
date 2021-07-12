<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<!-- editLogin-body.jsp -->

<% 
    String secureDomainApp = AntwebProps.getSecureDomainApp();
    String domainApp = AntwebProps.getDomainApp();
    Login accessLogin = LoginMgr.getAccessLogin(request);
    Login thisLogin = (Login) session.getAttribute("thisLogin");
    if (thisLogin == null) {
         out.println("No Login.");
         //AntwebUtil.logStackTrace();
         return;            
    }

    String message = (String) request.getAttribute("message");
    if (message != null) out.println("<h2><font color=green>" + message + "</font></h2><br>");
%>
 
<div class=admin_left>
<logic:messagesPresent property="error">
    <h2>Sorry, you need to correct the following errors:</h2>
    <font color="red">
    <html:messages property="error" id="errMsg">
        <bean:write name="errMsg"/><br>
    </html:messages>
    </font>
</logic:messagesPresent>

<br>
<h2>Antweb User: <%= thisLogin.getDisplayName() %></h2>
<br>

<form name="editLoginForm" action="<%= secureDomainApp %>/editLogin.do?id=<%= thisLogin.getId() %>" method="POST">
<p>

<input type="hidden" name="isSubmit" value="true">

<h3>Username:
<br>
<input type="text" class="login_input" name="name" value="<%= thisLogin.getName() %>">
</h3>
<p>

<h3>Password:
<br>
<input type="password" class="login_input" name="password" value="<%= thisLogin.getPassword() %>">
</h3>
<p>

<h3>Retype Password:
<br>
<input type="password" class="login_input" name="retypePassword" value="<%= thisLogin.getPassword() %>">
</h3>
<p>

<h3>Email:
<br>
<input type="text" class="login_input" name="email" value="<%= thisLogin.getEmail() %>">
</h3>
<p>

<h3>First Name:
<br>
<input type="text" class="login_input" name="firstName" value="<%= thisLogin.getFirstName() %>">
</h3>
<p>

<h3>Last Name:
<br>
<input type="text" class="login_input" name="lastName" value="<%= thisLogin.getLastName() %>">
</h3>
<p>
<p>
<p>
<br><br>

<h1>
<input border="0" type="image" src="<%= secureDomainApp %>/image/orange_done.gif" width="98" height="36" value="Done">
<a href="<%= domainApp %>/"><img border=0" src="<%= secureDomainApp %>/image/grey_cancel.gif" width="123" height="36"></a>

</form>




