<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>


<div class="admin_left">


<br><br><br>
<h2>Session Attributes:</h2>

<% 
     //HttpSession session = request.getSession();
     for (Enumeration e = session.getAttributeNames() ; e.hasMoreElements() ;) {
       String name = (String) e.nextElement();
       if (!"dummyForm".equals(name) && !"statistics".equals(name)) {
         out.println("<br>name:<b>" + name + "</b> value:" + session.getAttribute(name));
       } else {
         out.println("<br>name:<b>" + name + "</b>");
         A.log("serverStatus-body.jsp dummyForm:" + session.getAttribute(name));
       }
     }
%>     
</div>