<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.home.*" %>

<%  String domainApp = (new Utility()).getDomainApp(); %>
    
<% Project thisProject = (Project) request.getAttribute("thisProject"); 
%>

<%@include file="/curate/adminCheck.jsp" %>

<div class="msg in_admin">
    <div class="msg_alert"></div>
    <div class="msg_type">NOTE</div>
    <div class="msg_pipe"></div>
    <div class="msg_copy"><div class="mgr_help">
These are the project's settings. <b>Save</b> stores all of them at once and reloads the project lists.
The page text is edited on the project page (see Page text below); the project's species in the Species List Tool.
<% if (request.getAttribute("isNewProject") != null) { %>
<br><b>New project:</b> nothing is stored until you click Save.
<% } %>
</div>

    </div>
    <div class="clear"></div>
</div>

<div class=admin_left>
<h1>Editing <%= org.calacademy.antweb.util.Html.esc(thisProject.getTitle()) %> Project</h1>

<html:messages id="message" message="true">
<font color="red"><b>
<bean:write name="message"/><br>
</b></font>
</html:messages>

<hr></hr>

<form method="post" action="<%= domainApp %>/editProject.do?projectName=<%= Html.escAttr(Html.url(thisProject.getName())) %>"><%= org.calacademy.antweb.util.Csrf.field(request) %>

<h3>Key: <%= Html.esc(thisProject.getName()) %></h3>
(The project's permanent name; it cannot be changed.)
<p>

<h3>Display Key:</h3>
<input name="displayKey" type=text class=input_550 value="<%= Html.escAttr(Formatter.formDisplay(thisProject.getDisplayKey())) %>"><br>
Optional other name used in the project page's address (project.do?name=...) after saving; empty means the key.
<p>

<h3>Title:</h3>
<input name="title" type=text class=input_550 value="<%= org.calacademy.antweb.util.Html.esc(thisProject.getTitle()) %>">
<p>

<%-- Page text (Contents, Author, Author Bio, page images) is edited on the project page itself,
     with the page-text editor (TinyMCE, DescriptionAction.saveDescriptionEdit, stored in object_edit).
     The old "Page Copy", author and image boxes here were never loaded or saved (ProjectDb.update
     ignores them), so they were removed in favour of these links. --%>
<% String projectPage = thisProject.getThisPageTarget(); %>
<div class="pageTextLinks" style="border:1px solid #ccc;padding:8px;margin:8px 0">
<h3>Page text</h3>
The text and images shown on the project page are edited on the page itself:
<a href="<%= Html.escAttr(projectPage + "&editField=contents") %>#contents">Contents</a>
| <a href="<%= Html.escAttr(projectPage + "&editField=author") %>#author">Author</a>
| <a href="<%= Html.escAttr(projectPage + "&editField=authorBio") %>#authorBio">Author Bio</a>
| <a href="<%= Html.escAttr(projectPage) %>">Page images</a> (use the [Edit] links next to each image).
</div>

<p><h3>Map (Image):</h3>
<%-- The stored map image (project.map, Project.getMapImage); saved as mapImage (EditProjectForm). The old field
     was named "map" and showed the page's map object, so it was never saved. --%>
<input name="mapImage" type=text class=input_550 value="<%= Html.escAttr(Formatter.formDisplay(thisProject.getMapImage())) %>"><br>
File name of the map image in the project's species list folder (at most 40 characters).

<% if (thisProject.getMapImage() != null && !"".equals(thisProject.getMapImage())) { %>

        <div id="static_map" class="slide medium last"><img src="<%= AntwebProps.getImgDomainApp() %>/<%= Project.getSpeciesListDir() %><%= Html.escAttr(thisProject.getRoot()) %>/<%= Html.escAttr(thisProject.getMapImage()) %>"></div>
<% } %>

<br><br>



<% 
  String disabled = "";
  String selected = "";
%>


<h3>Is Live:</h3>
Will be listed in the Region hierarchy.<br>
<input type="checkbox" name="isLive" <%= (thisProject.getIsLive() == true)?"checked":"" %>>
<br><br>

<h3>Source:</h3>
<%= Html.esc(thisProject.getSource()) %> (kept as it is)
<br><br>

<p><h3>Map centre (coordinates):</h3>
<input id="coords" type="text" name="coords" value="<%= Html.escAttr(Formatter.formDisplay(thisProject.getCoords())) %>"><br />
Enter the center as (latitude, longitude), for example (-18.77, 46.87).

<p><h3>Geographic Extent:</h3>
<input id="extent" name="extent" type=text class=input_550 value="<%= Html.escAttr(Formatter.formDisplay(thisProject.getExtent())) %>"><br>
The map area for the region field guides: four numbers separated by spaces, no commas.





<!--
<br>
<h3>Bioregion Project:</h3>
  <select name="bioregion" < %= disabled % >>
	< % 
       selected = "";
       if (thisProject.getBioregion() == null) selected = " selected"; else selected = "";
% >    <option value="none"< %= selected % >>None</option> < % 
       for (String bioregionName : BioregionMgr.getBioregionNames()) {
       if (bioregionName != null)
	     if (bioregionName.equals(thisProject.getName())) selected = "selected"; else selected = "";
 	 % >
      <option value='< %= bioregionName % >' < %= selected % >>< %= bioregionName % ></option>
	< % } % >	
  </select>  
<br><br>
-->

<!--
<p><h3>Geo Locale:<h3>
<input name="geolocaleId" type=text class=input_550 value="< %= thisProject.getGeolocaleId() % >"><br>
< % if (thisProject.getGeolocaleId() != 0) { % >
<a href="< %= AntwebProps.getDomainApp() % >/editGeolocale.do?id=< %= thisProject.getGeolocaleId() % >">Edit</a>
< % } else { % >
<br><br>

< % } % >

-->



<p>
<h3>Species List Mapping:</h3> 
(true: the project's species list can be changed in the Species List Tool):
<select name="speciesListMappable" id="SpeciesList_select">
  <option id="speciesListMappable" value="true" <%= (thisProject.getSpeciesListMappable() ? "selected" : "") %>>true</option>  
  <option id="speciesListMappable" value="false" <%= (!thisProject.getSpeciesListMappable() ? "selected" : "") %>>false</option>  
</select> 
 
<div class="clear"></div>
<br />
<br />
<div class="msg in_admin">
    <div class="msg_actions" align="center">
<input type="submit" value="Save">
<a href="<%= domainApp %>/projectMgr.do">Cancel</a>
</form>
    </div>
</div>


</div>
<div class=admiin_right>


<% if (LoginMgr.isAdmin(request)) { %>  
<% if (request.getAttribute("isNewProject") == null) { %>
<%= Csrf.postButton(request, domainApp + "/editProject.do?action=delete&projectName=" + Html.url(thisProject.getName()), "Delete", "Delete this project and its species list? There is no undo.") %>
<% } %>
<% } %>

</div> 
