<%@ page isErrorPage="true" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.*" %>
<%@ page import="java.lang.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.upload.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.*" %>


<%@ page import="java.util.regex.Matcher" %>
<%@ page import="java.util.regex.Pattern" %>


<% 

    String message = (String) request.getAttribute("message"); 
    //A.log("message-body.jsp message:" + message);
    LogMgr.appendDataLog("messages.txt", DateUtil.getFormatDateTimeStr(new java.util.Date()) + " - " + message + " " + HttpUtil.getTarget(request));
%>
<%-- Inside the admin layout (layouts/admLayout.jsp sets awAdminLayout): the tool-page look (common/tools.css).
     In the public layout (common/unboldMessage.jsp, e.g. the Query Manager) the page is as it was. --%>
<% if (Boolean.TRUE.equals(request.getAttribute("awAdminLayout"))) { %>
<%@include file="/common/toolsHead.jsp" %>
<div class="aw-tools" id="admin-message-page">
<p class="aw-crumbs"><a href="<%= AntwebProps.getDomainApp() %>/curate.do">Curator Tools</a></p>
<div class="aw-card aw-result" id="admin-message"><%= message %></div>
</div>
<% } else { %>
<% // See web/common/antweb_style.css:266  %>
<div class="wide_left">
<br>
<br>
<%= message %>
</div>
<% } %>

