<%@ page errorPage = "/error.jsp" %>

<% 
    String message = (String) request.getAttribute("message"); 
%>
<%= message %>