<%-- Page layout of the tiles definition antweb.default in common/antweb-defs.jsp (and the unused overview-defs.jsp):
     sets domainApp to AntwebProps.getDomainApp() and statically includes layouts/layout.jsp, which uses it. Docs:
     docs/guides/page-style.md. Tests: tests/e2e/test_tool_pages_style.py. --%><%@ page import="org.calacademy.antweb.util.*" %>

<% 
   String domainApp = AntwebProps.getDomainApp();
%>
   <%@ include file="/layouts/layout.jsp" %>
