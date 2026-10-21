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


<input id="for_print" type="text" value="AntWeb. Available from: <%= Html.escAttr(HttpUtil.getFullJspTarget(request)) %>">
<input id="for_web" type="text" value="URL: <%= Html.escAttr(HttpUtil.getFullJspTarget(request)) %>">
<div class="page_contents">



<br><br>
    <h1><a href="<%= AntwebProps.getDomainApp() %>/artists.do">Artist:</a> <%= Html.esc(artist.getName()) %></h1>
<% String artistMessage = (String) request.getAttribute("message");
   if (artistMessage != null && !artistMessage.isEmpty()) { %><p><%= artistMessage %></p><% } %>
<% if (isOwner || LoginMgr.isAdmin(request)) { %>
<div class="mgr_help" style="max-width:60em;margin:6px 0 12px 0">
The photographer credited on images. <b>Save changes</b> renames the photographer on every image credited
to them at once (the images keep pointing to this photographer). <b>Delete</b> with another photographer's ID
first moves all images to that photographer (use this to merge two entries for one person), then deletes this
one; without an ID it is refused while images are credited. Only the curator who added the photographer and
administrators may rename or delete.
</div>
<% } %>
</div>

<div class="page_divider"></div>

<div id="page_data">
    <div id="overview_data" class="plain">

      <html:form method="POST" action="artist"><%= Csrf.field(request) %>
      <input type="hidden" name="isEdit" value="true">
      <input type="hidden" name="id" value="<%= artist.getId() %>">

		<ul>

		<li><b>Id:</b></li>
		<li><%= artist.getId() %></li>
		</ul>

        <ul>
		<li><b>Name:</b></li>
        <li><input type="text" class="input_200" name="name" value="<%= Html.escAttr(artist.getName()) %>"></li>
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
<html:form method="POST" action="artist"><%= Csrf.field(request) %>
<input type="hidden" name="isRemove" value="true">
<input type="hidden" name="id" value="<%= artist.getId() %>">
Delete this photographer. If images are credited to them, first enter the ID of the photographer the images move to (the ID is shown on that photographer's page and in the list).<br>Move images to ID: <input border="1" type="text" style="border:1px solid" name="moveTo" value="">
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

