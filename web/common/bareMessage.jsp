<%@ page errorPage = "/error.jsp" %>

<% 
    // Test this way: http://localhost/antweb/util.do?action=bareMessage
    String message = (String) request.getAttribute("message"); 
%>
<%= message %>