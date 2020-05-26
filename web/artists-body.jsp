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

<%
	ArrayList<Artist> artistArray = (ArrayList<Artist>) request.getAttribute("artists");
	if (artistArray == null) { %>
	  <br><br><b>Unable to process request. Server initializing?</b>	  
 <%   return;
    }

    String message = (String) request.getAttribute("message");
    if (message != null) { %>
      <br><br><%= message %>      
 <% }

    String sortBy = (String) request.getParameter("sortBy");
    if (sortBy == null || "".equals(sortBy)) sortBy = "imageCount";
    
    
    if ("name".equals(sortBy)) {
	   Collections.sort(artistArray, Artist.ArtistNameComparator);
    }    
    if ("imageCount".equals(sortBy)) {
	   Collections.sort(artistArray, Artist.ArtistImageCountComparator);
    }    
    if ("specimenCount".equals(sortBy)) {
	   Collections.sort(artistArray, Artist.ArtistSpecimenCountComparator);
    }    
%>

<div class="in_admin">
<br>
<h1>Artists (Photographers):</h1>
</div>

<div class="admin_left">
<br>

<%
    if (LoginMgr.isCurator(request)) {
%>
<html:form method="GET" action="artist">
<input type="hidden" name="isCreate" value="true">
Create artist with name: <input type="text" style="border:1px solid" name="name" value="">
<input border="0" type="image" src="<%= AntwebProps.getDomainApp() %>/image/addIcon.png" height="23" value="Submit">
</html:form>
<br>

<%
    }
    if (LoginMgr.isAdmin(request)) {
        int j = 0;
        String nonUtf8 = "";
        for (Artist artist : artistArray) {
          if (Formatter.hasSpecialCharacter(artist.getName())) {
             ++j;
             if (j > 1) nonUtf8 += "<br>";
             nonUtf8 += " " + j + ":" + artist.getName() + ":" + artist.getId();
          }
        }
        if (!nonUtf8.equals("")) out.println("<br><br><h3><u>Non Utf8 (Admin only)</u></h3>" + nonUtf8 + "<br>");
    }
%>

<br>

<table>

<tr>
<th>#</th>
<th width=40>ID</th>
<th><a href='<%= AntwebProps.getDomainApp() %>/artists.do?sortBy=name'>Name</a></th>
<th><a href='<%= AntwebProps.getDomainApp() %>/artists.do?sortBy=imageCount'>Images</a></th>
<th><a href='<%= AntwebProps.getDomainApp() %>/artists.do?sortBy=specimenCount'>Specimen</a></th>
</tr>

<% String hrLine = "<tr><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td></tr>"; %>

<%= hrLine %>

<%
if (artistArray.size() == 0) {
  out.println("Artists data loading...");
  return;
}
A.log("artists-body.jsp artistArray.size:" + artistArray.size());


int i = 0;
for (Artist artist : artistArray) {
   ++i;
   if (artist.getImageCount() == 0 && !LoginMgr.isCurator(request)) {
       continue;
   }
    %>
       <tr><td><%= i %>.</td><td><%= artist.getId() %></td><td><%= artist.getLink() %></td><td><%= Formatter.commaFormat(artist.getImageCount()) %></td><td><%= Formatter.commaFormat(artist.getSpecimenCount()) %></td></tr>
<% } %>
</table>

<br>

</div>