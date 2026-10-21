<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.home.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<%@include file="/curate/adminCheck.jsp" %>

<%
    Login accessLogin = LoginMgr.getAccessLogin(request);
	ArrayList<SpeciesListable> projList = accessLogin.getProjects();    
%>

<div class=admin_left>
<a href="<%= domainApp %>/curate.do">&nbsp;&nbsp;<< Back to Curator Tools </a>
<br>
<h1>Project Manager</h1>

<div class="mgr_help" style="max-width:60em;margin:6px 0 12px 0">
Projects are AntWeb's named species lists with their own page (regional faunas such as malagasyants,
and lists such as introducedants and fossilants). <b>Settings</b> opens a project's title, live flag,
map and coordinates; saving there changes only those settings. A project's species are edited in the
Species List Tool, its page text on its public page. <b>Delete</b> removes the project and its species
list (not the specimens); there is no undo.
</div>

<br>
<!-- Create Project: opens the settings form for the new key; the project is created when that form is saved. -->
<% if (LoginMgr.isAdmin(request)) { %>
<form method="GET" action="<%= domainApp %>/editProject.do">
    <div class="admin_action_module">
        <div class="admin_action_item">
            <div class="action_desc"><b>Create</b> Project: </div>
            <div class="action_dropdown">

    <input type="text" value="" name="projectName">

            </div>
            <div class="clear"></div>
 (One word, lowercase, no spaces, ending with "ants". Opens the settings form; the project is created when you save it.)
            <div class="align_right"><input type="submit" value="Create"></div>
            <div class="clear"></div>
        </div>
    </div>
</form>
<% } %>


<br>
<!-- Delete Project -->
<% if (LoginMgr.isAdmin(request)) { %>
<html:form method="POST" action='<%= org.calacademy.antweb.util.Csrf.action(request, "upload.do") %>' enctype="multipart/form-data" onsubmit="return confirm('Delete this project and its species list? There is no undo.')"><%= org.calacademy.antweb.util.Csrf.field(request) %>
    <input type="hidden" name="ancFileDirectory" value="none" />
    <input type="hidden" name="updateAdvanced" value="no" />
    <input type="hidden" name="updateFieldGuide" value="none" />
    <input type="hidden" name="images" value="no" />
    <input type="hidden" name="outputFileName" value="" />
    <input type="hidden" name="successkey" value="null" />
    <div class="admin_action_module">
        <div class="admin_action_item">
            <div class="action_desc"><b>Delete</b> Project: </div>
            <div class="action_dropdown">
  <html:select property="deleteProject">
	<html:option value="none">Select...</html:option>

  <% for (SpeciesListable p : projList) { %>
      <html:option value="<%= p.getName() %>"><%= org.calacademy.antweb.util.Html.esc(p.getTitle()) %> (<%= org.calacademy.antweb.util.Html.esc(p.getName()) %>)</html:option>
  <% } %>
         
  </html:select>
            </div>
            <div class="clear"></div>
            <div class="align_right"><input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="DeleteProject"></div>
            <div class="clear"></div>
        </div>
    </div>
</html:form>
<% } %>

<br>

<hr>
<hr>
<h3>Live:</h3>
<%
   HashMap<String, Project> projects = ProjectMgr.getLiveProjectsHash(true);
   List<String> projectNames = new ArrayList<String>(projects.keySet());  
   Collections.sort(projectNames); 
   for (String projectName : projectNames) { %>
   <br>&nbsp;&nbsp;&nbsp;&nbsp;<a href='<%= AntwebProps.getDomainApp() %>/project.do?name=<%= Html.escAttr(Html.url(projectName)) %>'><%= Html.esc(projectName) %></a>
   (<a href='<%= AntwebProps.getDomainApp() %>/editProject.do?projectName=<%= Html.escAttr(Html.url(projectName)) %>'>Settings</a>)
<% } %>

<br><br>
<h3>Not Live:</h3>
<%
   projects = ProjectMgr.getLiveProjectsHash(false);
   projectNames = new ArrayList<String>(projects.keySet());   
   Collections.sort(projectNames); 
   for (String projectName : projectNames) { %>
   <br>&nbsp;&nbsp;&nbsp;&nbsp;<a href='<%= AntwebProps.getDomainApp() %>/project.do?name=<%= Html.escAttr(Html.url(projectName)) %>'><%= Html.esc(projectName) %></a>
   (<a href='<%= AntwebProps.getDomainApp() %>/editProject.do?projectName=<%= Html.escAttr(Html.url(projectName)) %>'>Settings</a>)
<% } %>

</div>



