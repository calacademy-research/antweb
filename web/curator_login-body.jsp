<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%
        Utility util = new Utility();
        String domainApp = util.getDomainApp();
%>

<!-- Body of Text Begins -->

<div class="admin_left">

    <div class="admin_action_module">
        <div class="admin_action_item"><h1>Login to AntWeb</h1></div>
<html:messages id="message" message="true">
<font color="red"><b>
<bean:write name="message"/><br>
</b></font>
</html:messages>
<html:form action="login">
<%
    String query = request.getQueryString();
%>
<input type="hidden" name="redirect_to" value="<%= domainApp %>/<%= query %>">
<%
String target = ((org.calacademy.antweb.LoginForm) request.getAttribute("loginForm")).getTarget();
if (target != null) {
  out.println("<input type=\"hidden\" name=\"target\" value=\"" + target + "\">");
}
%>
        <div class="admin_action_item">
            <div class="action_desc">Username:</div>
            <div class="action_dropdown"><html:text property="userName" size="25"/></div>
            <div class="clear"></div>
        </div>
        <div class="admin_action_item">
            <div class="action_desc">Password:</div>
            <div class="action_dropdown"><html:password property="password" size="25"/></div>
            <div class="clear"></div>
        </div>
        <div class="admin_action_item">
            <div class="align_right"><!-- <html:submit/> --><input border="0" type="image" src="image/grey_submit.png" width="77" height="23" value="Submit"></div>
            <div class="clear"></div>
        </div>
    </div>
<script language="JavaScript">
document.loginForm.userName.focus();
</script>
</html:form>
Forgot your password? <a href="forgot_password.jsp">Click here</a>.
</div>

<div class="admin_right"> </div>

<!-- Body of Text Ends -->
