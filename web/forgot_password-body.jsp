<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import = "org.calacademy.antweb.util.*" %>

<!-- forgot_password-body.jsp (ForgotPasswordAction) -->
<br><br>
<%
    String secureDomainApp = AntwebProps.getSecureDomainApp();
    String resetState = (String) request.getAttribute("resetState");
    String resetError = (String) request.getAttribute("resetError");
%>
<div class="admin_left">

    <div class="admin_action_module">
        <div class="admin_action_item"><h1>Forgot Your Password?</h1></div>

<% if ("sent".equals(resetState)) { %>
<p><b>If an account exists for what you entered, we've sent a link to its email address.</b></p>
<p>The link works once and expires in 24 hours. If no email arrives within a few minutes, please check your spam folder, or try your other username or email address.</p>
<p><a href="<%= secureDomainApp %>/login.do">Back to log in</a></p>
<% } else { %>

<% if (resetError != null) { %>
<p><font color="red"><b><%= Html.esc(resetError) %></b></font></p>
<% } %>

<p>Enter your AntWeb username or email address. If an account exists, we will email it a link to set a new password.</p>

<form name="forgotPasswordForm" method="POST" action="<%= secureDomainApp %>/forgotPassword.do"><%= org.calacademy.antweb.util.Csrf.field(request) %>
<p>Username or email address:<br>
<input type="text" name="identifier" value="" maxlength="255" autocomplete="username" style="border: 1px solid #B9B9B9; font-family: inherit; padding: 2px; width: 260px;">
</p>
<p><input type="submit" class="tool_label" style="float:none;" value="Email me a link"></p>
</form>

<p>If your account has no email address, or the email does not arrive, please write to <a href="mailto:antweb@calacademy.org?subject=Password%20Recovery">antweb@calacademy.org</a>.</p>
<% } %>

<br><br>
<p>If you're not currently a curator, would you like to join us?  Curators can edit the home page of the geographic section they curate, upload specimen data and authority files, and control a number of other aspects of their project. Learn how to <a href="<%= AntwebProps.getDomainApp() %>/documentation.do">submit data to AntWeb</a>.

<p>If you would like to join us, contact us at <a href="mailto:antweb@calacademy.org">antweb@calacademy.org</a>.

    </div>
</div>

<div class="admin_right"> </div>

<!-- Body of Text Ends -->
