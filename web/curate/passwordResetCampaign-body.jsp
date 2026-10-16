<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.home.PasswordResetDb" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<!-- passwordResetCampaign-body.jsp (PasswordResetCampaignAction) -->
<%
    String secureDomainApp = AntwebProps.getSecureDomainApp();
    Login accessLogin = LoginMgr.getAccessLogin(request);
    if (!LoginMgr.isAdmin(accessLogin)) return;

    List<PasswordResetDb.Account> accounts = (List<PasswordResetDb.Account>) request.getAttribute("campaignAccounts");
    if (accounts == null) accounts = new ArrayList<>();
    String result = (String) request.getAttribute("campaignResult");
    String status = (String) request.getAttribute("campaignStatus");

    int admins = 0, withEmail = 0, marked = 0;
    for (PasswordResetDb.Account a : accounts) {
        if (a.isAdmin) admins++;
        if (a.hasEmail()) withEmail++;
        if (a.mustReset) marked++;
    }
%>
<div class="admin_left">
<h1>Password Reset Campaign</h1>
<br>

<% if (result != null) { %>
<p><font color="green"><b><%= Html.esc(result) %></b></font></p>
<% } %>
<% if (status != null) { %>
<p><b>Last send:</b> <%= Html.esc(status) %><%= PasswordResetMgr.isCampaignRunning() ? " (reload to refresh)" : "" %></p>
<% } %>

<p>Curators and administrators: <b><%= accounts.size() %></b>
 (administrators <%= admins %>; with an email address <%= withEmail %>; without <%= accounts.size() - withEmail %>;
 already marked must-reset <%= marked %>).</p>

<p><b>1. Mark</b>: every account below (except yours) must set a new password before it can log in again,
and all of its current sessions end now. Someone who then logs in with the old password is sent a reset link automatically.</p>
<form method="POST" action="<%= secureDomainApp %>/passwordResetCampaign.do"><%= org.calacademy.antweb.util.Csrf.field(request) %>
<input type="hidden" name="action" value="mark">
<label><input type="checkbox" name="confirm" value="yes"> Yes, mark these <%= accounts.size() %> accounts</label>
<input type="submit" value="Mark accounts">
</form>
<br>

<p><b>2. Send</b>: email each account with an email address a reset link (subject "<%= Html.esc(PasswordResetMgr.SUBJECT_CAMPAIGN) %>"),
about 10 a second, in the background. Accounts without an email address are skipped. The summary goes to admin.log.</p>
<form method="POST" action="<%= secureDomainApp %>/passwordResetCampaign.do"><%= org.calacademy.antweb.util.Csrf.field(request) %>
<input type="hidden" name="action" value="send">
<label><input type="checkbox" name="confirm" value="yes"> Yes, email <%= withEmail %> accounts</label>
<input type="submit" value="Send emails">
</form>
<br>

<table border="1" cellpadding="3" cellspacing="0">
<tr><th>Login id</th><th>Name</th><th>Email</th><th>Admin</th><th>Must reset</th></tr>
<% for (PasswordResetDb.Account a : accounts) { %>
<tr>
<td><%= a.id %></td>
<td><%= Html.esc(a.getDisplayName()) %></td>
<td><%= a.hasEmail() ? Html.esc(PasswordResetMgr.maskEmail(a.email)) : "<i>none</i>" %></td>
<td><%= a.isAdmin ? "yes" : "" %></td>
<td><%= a.mustReset ? "yes" : "" %></td>
</tr>
<% } %>
</table>
</div>
