<%-- The global forward bareMessage (used by SearchListAction): writes only the request attribute message, without
     layout. --%><%@ page errorPage = "/error.jsp" %>

<% 
    String message = (String) request.getAttribute("message"); 
%>
<%= message %>