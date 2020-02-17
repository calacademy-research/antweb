<%@ page isErrorPage="true" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.*" %>
<%@ page import="java.lang.*" %>
<%@ page import="org.calacademy.antweb.util.AntwebUtil" %>

<div class="left">

<% 
    AntwebUtil.log("info", "tempDown-body.jsp target:" + HttpUtil.getTarget(request));
//    AntwebUtil.log("warn", "tempDown-body.jsp requestUrl:" + request.getRequestURL());
%>

This functionality is temporarily down.

</div>
<div class="right">
</div>
