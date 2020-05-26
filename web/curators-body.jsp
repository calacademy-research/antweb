<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="java.sql.Timestamp" %>
<%@ page import="java.net.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.upload.Upload" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<!-- contributors-body.jsp -->

<%
    ArrayList<Curator> curators = (ArrayList<Curator>) request.getAttribute("curators");
  	if (curators == null) { %>
	  <br><br><b>Unable to process request. Server initializing?</b>
	  
 <%   return;
    }

    String sortBy = (String) request.getParameter("sortBy");
    if (sortBy == null || "".equals(sortBy)) sortBy = "name";

    if ("name".equals(sortBy)) {
	   Collections.sort(curators, Curator.CuratorNameComparator);
    }    
    if ("group".equals(sortBy)) {
	   Collections.sort(curators, Curator.CuratorGroupNameComparator);
    }    
    if ("specimenUploadCount".equals(sortBy)) {
	   Collections.sort(curators, Curator.CuratorSpecimenUploadComparator);
    }    
    if ("descEditCount".equals(sortBy)) {
	   Collections.sort(curators, Curator.CuratorDescEditComparator);
    }    
%>



<div class="in_admin">
<br>
<h1>Curators:</h1>
</div>

<br>

<table>

<tr>
<th></th>
<th><a href='<%= AntwebProps.getDomainApp() %>/curators.do?sortBy=name'>Name</a></th>
<th><a href='<%= AntwebProps.getDomainApp() %>/curators.do?sortBy=group'>Group</a></th>
<th><a href='<%= AntwebProps.getDomainApp() %>/curators.do?sortBy=specimenUploadCount'>Specimen Uploads</a></th>
<th><a href='<%= AntwebProps.getDomainApp() %>/curators.do?sortBy=descEditCount'>Description Edits</a></th>
<% if (LoginMgr.isDeveloper(request)) { %>
<th>Image Uploads</th>
<th>Images Uploaded</th>
<% } %>
<th>Projects</th>
</tr>

<% String hrLine = "<tr><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td>";
   if (LoginMgr.isDeveloper(request)) {
     hrLine += "<td><hr></td><td><hr></td>"; 
   }
   hrLine += "<td><hr></td></tr>"; %>
<%= hrLine %>

<% 
   int i = 0;
   for (Curator curator : curators) { 
     if ("Mark Johnson".equals(curator.getFullName())) continue;
     //A.log("curators-body curator:" + curator.getName());
     ++i;
   %>   
     <tr><td><%= i %></td><td><%= curator.getLink() %></td><td><%= curator.getGroup().getLink() %></td><td><%= curator.getSpecimenUploadCount() %></td><td><%= curator.getDescEditCount() %></td>
<% if (LoginMgr.isDeveloper(request)) { %>
     <td><%= curator.getImageUploadCount() %></td><td><%= curator.getImagesUploadedCount() %></td>
<% } %>
     <td><%= curator.getProjectNamesShort() %> </td></tr>
<% } 


%>

</table>

<br>

