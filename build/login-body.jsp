<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import = "org.calacademy.antweb.*" %>
<%@ page import = "org.calacademy.antweb.util.*" %>

<%
//A.log("login-body.jsp");
%>

<div id="page_contents">
<h1>Login to AntWeb</h1>
	<div class="clear"></div>
	<div class="page_divider"></div>
</div>

<div id="page_data">
    <div id="overview_data">

<html:messages id="message" message="true">
<logic:notEmpty name="message">
<font color="red"><b><bean:write name="message"/><br></b></font>
</logic:notEmpty>

<% if (AntwebProps.isDevMode()) { %>
<logic:empty name="message">
<br><font color="red"><b>Dev Note: empty message</b></font>
</logic:empty>
<% } %>

</html:messages>

<!-- html:form action="login" -->
<form name="loginForm" method="post" action="<%= AntwebProps.getSecureDomainApp() %>/login.do">

<%
LoginForm loginForm = (LoginForm) request.getAttribute("loginForm");
String target = null;
if (loginForm != null) target = loginForm.getTarget();
if (target == null && !HttpUtil.isPost(request)) target = HttpUtil.getTarget(request);
if (target != null) {
  out.println("<input type=\"hidden\" name=\"target\" value=\"" + target + "\">");
}
%>

<p>Username: 
<!-- html :text property="userName" style="border: 1px solid #B9B9B9; font-family: inherit; padding: 2px; position: relative; top: -1px; width: 140px;" / -->
<input type="text" name="userName" value="" style="border: 1px solid #B9B9B9; font-family: inherit; padding: 2px; position: relative; top: -1px; width: 140px;">
</p>

<p>Password: <!-- html :password property="password" style="border: 1px solid #B9B9B9; font-family: inherit; padding: 2px; position: relative; top: -1px; width: 140px;" / -->
<input type="password" name="password" value="" style="border: 1px solid #B9B9B9; font-family: inherit; padding: 2px; position: relative; top: -1px; width: 140px;">
</p>

<p align="center"><input border="0" name="value" type="submit" class="tool_label" style="float:none;" value="Login">

<input border="0" type="submit" name="value" class="tool_label" style="float:none;" value="Create Account">
</p>

<br><br><br><br>To access full Antweb functionality anonymously: <input border="0" type="submit" name="value" class="tool_label" style="float:none;" value="Browse">  

<script language="JavaScript">
document.loginForm.userName.focus();
</script>

<!-- /html :form -->
</form>

<br>
<hr>
<br>
<p>Forgot your password? <a href="<%= AntwebProps.getDomainApp() %>/forgotPassword.do">Click here</a>.</p>

    </div>
</div>
