<%@ page language="java" %>
<%@ page import = "java.util.ResourceBundle" %>
<%@ page import = "java.util.ArrayList" %>
<%@ page import = "org.calacademy.antweb.*" %>
<%@ page import = "java.util.Date" %>
<%@ page import = "org.calacademy.antweb.util.*" %>
<%@ page import = "org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.upload.Upload" %>

<%@ page errorPage = "error.jsp" %> 
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<% 
    Login accessLogin = LoginMgr.getAccessLogin(request);
    
    Artist artist = (Artist) request.getAttribute("artist");  

    boolean isOwner = artist.getCurator() != null && accessLogin != null && artist.getCurator().getId() == accessLogin.getId();       
%>

<div class=right>
<br><b><a href="<%= AntwebProps.getDomainApp() %>/artists.do">Artists</a></b>
</div>


<input id="for_print" type="text" value="AntWeb. Available from: <%= HttpUtil.getFullJspTarget(request)%>">
<input id="for_web" type="text" value="URL: <%= HttpUtil.getFullJspTarget(request)%>">
<div class="page_contents">



<br><br>
    <h1><a href="<%= AntwebProps.getDomainApp() %>/artists.do">Artist:</a> <%= artist.getName() %></h1>
</div>

<div class="page_divider"></div>

<div id="page_data">
    <div id="overview_data" class="plain">

      <html:form method="put" action="artist">
      <input type="hidden" name="isEdit" value="true">
      <input type="hidden" name="id" value="<%= artist.getId() %>">

		<ul>

		<li><b>Id:</b></li>
		<li><%= artist.getId() %></li>
		</ul>

        <ul>
		<li><b>Name:</b></li>
        <li><input type="text" class="input_200" name="name" value="<%= artist.getName() %>"></li>
		</ul>

		<ul>
		<li><b>Image Count:</b></li>
		<li><%= artist.getDisplayCounts() %></li>
		</ul>
		
		<ul>
		<li><b>Active:</b></li>
		<li><%= artist.getIsActive() %></li>
		</ul>

		<ul>
		<li><b>Created:</b></li>
        <%
          String created = "";
          if (artist.getCreated() != null) created = artist.getCreated().toString();

          A.log("artist-body.jsp created:" + artist.getCreated());
        %>		
                <li><%= created %></li>
                </ul>

                <ul>
                <li><b>Curator:</b></li>
        <% if (artist.getCurator() != null) { %>
                <li><%= artist.getCurator().getLink() %></li>
        <% } %>
		</ul>		

<% if (isOwner || LoginMgr.isAdmin(request)) { %>
<ul>
<br><br>
<li>      Save changes to artist: &nbsp;&nbsp;</li>
<li>      <input border="0" type="image" src="<%= AntwebProps.getDomainApp() %>/image/grey_submit.png" width="77" height="23" value="Submit">
</li>
</ul>
<% } %>

      </html:form>


    </div>

<div class="admin_left">
<% if (isOwner || LoginMgr.isAdmin(request)) { %>
<br>
<font color=green>(For owning curator and administrators only)</font>
<html:form method="GET" action="artist">
<input type="hidden" name="isRemove" value="true">
<input type="hidden" name="id" value="<%= artist.getId() %>">
Delete this artist (If artist has attributed images, must reassign to an other artist) <br>ID: <input border="1" type="text" style="border:1px solid" name="moveTo" value="">
<input border="0" type="image" src="<%= AntwebProps.getDomainApp() %>/image/deleteButton.png" height="23" value="Submit">
</html:form>
</br>
<% } %>

<br><br>

<!--
Would like a query available like:
  select image_of_id, shot_type, shot_number from image, artist where image.artist = artist.id and artist.id = 209;
  
To show the images of an artist.
  
< % if (LoginMgr.isAdmin(request)) {

List of artist's images: <a href='<%= AntwebProps.getDomainApp() %>/query.do?name=specimenOfArtist'>link</a>

< % } % -->

</div>

</div>

