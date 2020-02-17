<%@ page language="java" %>
<html><head><title>Antweb Memory Usage</title></head>
<body>
<%
out.println("freeMemory : " +Runtime.getRuntime().freeMemory());
out.println("totalMemory : " +Runtime.getRuntime().totalMemory() );
%>
</body>
</html>