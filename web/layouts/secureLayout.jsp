<%-- Page layout of common/antwebSecure-defs.jsp (log-in and account pages): sets domainApp to
     AntwebProps.getSecureDomainApp() and statically includes layouts/layout.jsp. Tests:
     tests/e2e/test_tool_pages_style.py. --%><%@ page import="org.calacademy.antweb.util.*" %>

<% 
   String domainApp = AntwebProps.getSecureDomainApp();
%>

<%@ include file="/layouts/layout.jsp" %>

