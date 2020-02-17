<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<% 
   if (LoginMgr.isDeveloper(request)) { %>
     <br><br>


<br>=== Developer Details ===

<%@ include file="/util/pageTracker.jsp" %>

<% }
   PageTracker.remove(request); 
%>
