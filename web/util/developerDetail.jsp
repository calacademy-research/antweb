<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<% 
   if (LoginMgr.isDeveloper(request)) { %>
     <br><br>


<br>=== Developer Details ===

<%@ include file="/util/pageTracker.jsp" %>

<% }

   // Not sure why this would be here. There is a finally in SessionRequestFilter.
   // PageTracker.remove(request);
%>
