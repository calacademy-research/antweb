<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>


<div class="admin_left">


<br><br><br>
<h2>Session Attributes:</h2>

<% 
     for (Enumeration e = session.getAttributeNames() ; e.hasMoreElements() ;) {
       String name = (String) e.nextElement();
       if (!"dummyForm".equals(name) && !"statistics".equals(name)) {
         out.println("<br><b>" + name + "</b>: " + session.getAttribute(name));
       } else {
         out.println("<br><b>" + name + "</b>");
         A.log("serverStatus-body.jsp dummyForm:" + session.getAttribute(name));
       }
     }
%>


<br><br>
<h2>Request Attributes:</h2>
<%
     out.println("<br><b>User Agent: </b>" + UserAgentTracker.getUserAgent(request));

%>

</div>