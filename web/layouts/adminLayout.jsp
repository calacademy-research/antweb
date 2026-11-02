<%-- Page layout of common/antweb_admin-defs.jsp (admin and curator tool pages): sets domainApp to
     AntwebProps.getDomainApp() and statically includes layouts/admLayout.jsp. Docs: docs/guides/page-style.md. --%><%@ page import="org.calacademy.antweb.util.*" %>

<% 
   String domainApp = AntwebProps.getDomainApp();
%>

<%@ include file="/layouts/admLayout.jsp" %>

