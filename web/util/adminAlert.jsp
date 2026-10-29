<%-- Admin Alerts page (adminAlert.do, AdminAlertAction), admins only (adminCheck.jsp): the unacknowledged rows
     of table admin_alerts (AdminAlertMgr) with a "Dealt with" button each and one for all (POST,
     Csrf.postButton), a Reload button, and the alert queries' results. Docs: docs/catalog/actions.csv. Tests:
     tests/unit/test_admin_alert_page.py, tests/e2e/test_manager_tools.py. --%>

<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.upload.*" %>
<%@ page import="org.calacademy.antweb.home.*" %>

<%
  Utility util = new Utility();
  String domainApp = util.getDomainApp();
%>

<%@include file="/curate/adminCheck.jsp" %>

<title>Admin Alert Mgr</title>

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br>

<h1>Admin Alert Manager</h1>

<div class="mgr_help" style="max-width:60em;margin:6px 0 12px 0">
Problems AntWeb has recorded for the administrators: a failed AntCat import, no AntCat changes for a week. The header of administrators' pages shows how many are open. The "Dealt with" button
marks one alert as dealt with (it stays in the full list below); nothing else changes. Reload re-reads the
alerts and reruns the alert checks (the "Checks" lines below).
</div>

<%
    List<AdminAlert> adminAlerts = AdminAlertMgr.getAdminAlerts();
    if (adminAlerts.size() > 0) { %>
    Alerts: <b><%= adminAlerts.size() %></b>
<%
        for (AdminAlert adminAlert : adminAlerts) {

          // Alert text can quote data (an AntCat import message): shown as text.
          out.println("<br>&nbsp;&nbsp;&nbsp;" + adminAlert.getCreated() + " &nbsp;&nbsp;&nbsp;" + Html.esc(adminAlert.getAlert()) +  " &nbsp;&nbsp;&nbsp;" + Csrf.postButton(request, AntwebProps.getDomainApp() + "/adminAlert.do?action=remove&id=" + adminAlert.getId(), "Dealt with")); 
        }
%>
        <br><br>
        <%-- 2026-10-28 (Brian Fisher): the help line showed a red X that was only a picture, next to the real buttons;
             clicking it did nothing. Each alert now has a "Dealt with" button, and one button marks them all. --%>
        Mark every alert above as dealt with: <%= Csrf.postButton(request, AntwebProps.getDomainApp() + "/adminAlert.do?action=removeAll", "All dealt with", "Mark every admin alert as dealt with?") %>

 <% } else { %>
        <b>No Admin Alerts.</b>
 <% } %>

<br><br>
<hr>

<small>(The link in the header is only visible to administrators)</small>

<br><br>
Reload the alerts and rerun the checks: <%= Csrf.postButton(request, AntwebProps.getDomainApp() + "/adminAlert.do?action=refresh", "Reload") %>

<br><br>
Checks (ready-made queries that should find nothing): <%= AdminAlertMgr.getQueryAlerts() %>

<br><br>
Full list, including alerts already dealt with: <a href='<%= AntwebProps.getDomainApp() %>/query.do?action=curiousQuery&name=adminAlerts'>Admin Alerts</a>
| AntCat import history: <a href='<%= AntwebProps.getDomainApp() %>/query.do?name=worldantsUploads'>worldantsUploads</a>

<br><br>
Admin Alert <a href='<%= AntwebProps.getDomainApp() %>/logMgr.do?action=get&fileName=adminAlerts&ext=log'>Log File</a>
<br>&nbsp;&nbsp;<small>These are more developer oriented... from places in the code that do not have access to a database connection.</small>
