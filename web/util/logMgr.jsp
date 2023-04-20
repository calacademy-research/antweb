<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.util.*" %>

<%@ page import="java.util.*" %>


<%@include file="/common/antweb-defs.jsp" %>

<%
    String domainApp = AntwebProps.getDomainApp();
%>

<h1>Log Mgr</h1>

<div class=left>
<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>

<%
    String message = (String) request.getAttribute("message"); 
    if (message != null) out.println(message);
%>
</div > 
