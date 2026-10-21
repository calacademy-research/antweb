<%@ page errorPage = "/error.jsp" %>
<%@include file="/curate/adminCheck.jsp" %>
<%-- The page is built (and every value escaped) by org.calacademy.antweb.curate.group.AddUploaderAction. --%>
<div class="admin_left">
<%= request.getAttribute(org.calacademy.antweb.curate.group.AddUploaderAction.PAGE) == null ? "" : request.getAttribute(org.calacademy.antweb.curate.group.AddUploaderAction.PAGE) %>
</div>
<div class="right">
<br>
<a href="<%= org.calacademy.antweb.util.AntwebProps.getDomainApp() %>/viewGroups.do">Upload groups</a>
<br><a href="<%= org.calacademy.antweb.util.AntwebProps.getDomainApp() %>/viewLogins.do">Login Manager</a>
</div>
<div class="clear"></div>
