<%-- Body of the Session Properties page (sessionProps.do): the names and values of the attributes in the
     admin's own session, escaped (dummyForm and statistics by name only). The "Request Attributes" heading has
     nothing under it. --%>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>


<div class="admin_left">


<br><br><br>
<h2>Session Attributes:</h2>

<% 
     for (Enumeration e = session.getAttributeNames() ; e.hasMoreElements() ;) {
       String name = (String) e.nextElement();
       if (!"dummyForm".equals(name) && !"statistics".equals(name)) {
         out.println("<br><b>" + Html.esc(name) + "</b>: " + Html.esc(session.getAttribute(name)));
       } else {
         out.println("<br><b>" + Html.esc(name) + "</b>");
         A.log("serverStatus-body.jsp dummyForm:" + session.getAttribute(name));
       }
     }
%>


<br><br>
<h2>Request Attributes:</h2>
<%
     //out.println("<br><b>User Agent: </b>" + UserAgentTracker.getUserAgent(request));

%>

</div>