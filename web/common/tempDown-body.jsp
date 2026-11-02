<%-- The body of common/tempDown.jsp: "This functionality is temporarily down." Used when an action is switched off by
     forwarding it to /common/tempDown.jsp (procedure in the comments of WEB-INF/struts-config.xml, e.g. taxonomicPage,
     mapComparison); logs the request target. The HttpUtil import was missing until 2026-11-02, so the page did not
     compile and the procedure showed an error. --%><%@ page isErrorPage="true" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.*" %>
<%@ page import="java.lang.*" %>
<%@ page import="org.calacademy.antweb.util.AntwebUtil" %>
<%@ page import="org.calacademy.antweb.util.HttpUtil" %>

<div class="left">

<% 
    AntwebUtil.log("info", "tempDown-body.jsp target:" + HttpUtil.getTarget(request));
//    AntwebUtil.log("warn", "tempDown-body.jsp requestUrl:" + request.getRequestURL());
%>

This functionality is temporarily down.

</div>
<div class="right">
</div>
