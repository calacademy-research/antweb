<%-- Page layout of common/antwebSecureAdmin-defs.jsp: sets domainApp to AntwebProps.getSecureDomainApp() and
     statically includes layouts/admLayout.jsp. Docs: docs/guides/page-style.md. --%><%@ page import="org.calacademy.antweb.util.*" %>

<% 
   String domainApp = AntwebProps.getSecureDomainApp();
%>

<%@ include file="/layouts/admLayout.jsp" %>

