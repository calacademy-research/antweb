<%@ page errorPage = "error.jsp" %>
<%@ page import = "org.calacademy.antweb.util.*" %>

<!-- resetPassword-body.jsp (ResetPasswordAction) -->
<br><br>
<%
    String secureDomainApp = AntwebProps.getSecureDomainApp();
    String resetState = (String) request.getAttribute("resetState");
    String resetToken = (String) request.getAttribute("resetToken");
    String resetError = (String) request.getAttribute("resetError");
%>
<div class="admin_left">

    <div class="admin_action_module">
        <div class="admin_action_item"><h1>Set a New Password</h1></div>

<% if ("done".equals(resetState)) { %>
<p><b>Password set. Please log in with your new password.</b></p>
<% if (request.getAttribute("reactivated") != null) { %>
<p>Your account is active again.</p>
<% } %>
<p>For your security, any other sessions on this account have been ended.</p>
<p><a href="<%= secureDomainApp %>/login.do">Log in</a></p>

<% } else if ("form".equals(resetState) && resetToken != null) { %>

<% if (resetError != null) { %>
<p><font color="red"><b><%= Html.esc(resetError) %></b></font></p>
<% } %>

<p>Your new password must:</p>
<ul>
<li>be at least <%= PasswordPolicy.MIN_LENGTH %> characters long (a few words strung together is a good choice),</li>
<li>not be your username or email address,</li>
<li>not be a very common password.</li>
</ul>

<form name="resetPasswordForm" method="POST" action="<%= secureDomainApp %>/resetPassword.do"><%= org.calacademy.antweb.util.Csrf.field(request) %>
<input type="hidden" name="token" value="<%= Html.escAttr(resetToken) %>">
<p>New password:<br>
<input type="password" name="password" value="" autocomplete="new-password" style="border: 1px solid #B9B9B9; font-family: inherit; padding: 2px; width: 260px;">
</p>
<p>New password again:<br>
<input type="password" name="retypePassword" value="" autocomplete="new-password" style="border: 1px solid #B9B9B9; font-family: inherit; padding: 2px; width: 260px;">
</p>
<p><input type="submit" class="tool_label" style="float:none;" value="Set password"></p>
</form>

<% } else { %>
<% if (resetError != null) { %>
<p><font color="red"><b><%= Html.esc(resetError) %></b></font></p>
<% } %>
<p><b>This link is not valid.</b> It may have expired, or it has already been used. Ask for a new link below: it arrives within a minute.</p>
<p><a href="<%= secureDomainApp %>/forgotPassword.do">Ask for a new link</a></p>
<% } %>

    </div>
</div>

<div class="admin_right"> </div>
