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

<br><br>

<br>
<!-- Create Project -->
<% if (LoginMgr.isAdmin(request)) { %>
<html:form method="POST" action="upload.do" enctype="multipart/form-data">
    <input type="hidden" name="ancFileDirectory" value="none" />
    <input type="hidden" name="updateAdvanced" value="no" />
    <input type="hidden" name="updateFieldGuide" value="none" />
    <input type="hidden" name="images" value="no" />
    <input type="hidden" name="outputFileName" value="" />
    <input type="hidden" name="successkey" value="null" />
    <div class="admin_action_module">
        <div class="admin_action_item">
            <div class="action_desc"><b>Create</b> Project: </div>
            <div class="action_dropdown">

    <input type="text" value="" name="createProject">

            </div>
            <div class="clear"></div>
 (One word, lowercase, no spaces, ending with "ants")
            <div class="align_right"><input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="CreateProject"></div>
            <div class="clear"></div>
        </div>
    </div>
</html:form>
<% } %>


<br>
<!-- Delete Project -->
<% if (LoginMgr.isAdmin(request)) { %>
<html:form method="POST" action="upload.do" enctype="multipart/form-data">
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
      <html:option value="<%= p.getName() %>"><%= p.getTitle() %></html:option>
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
   <br>&nbsp;&nbsp;&nbsp;&nbsp;<a href='<%= AntwebProps.getDomainApp() %>/project.do?name=<%= projectName %>'><%= projectName %></a>
<% } %>

<br><br>
<h3>Not Live:</h3>
<%
   projects = ProjectMgr.getLiveProjectsHash(false);
   projectNames = new ArrayList<String>(projects.keySet());   
   Collections.sort(projectNames); 
   for (String projectName : projectNames) { %>
   <br>&nbsp;&nbsp;&nbsp;&nbsp;<a href='<%= AntwebProps.getDomainApp() %>/project.do?name=<%= projectName %>'><%= projectName %></a>
<% } %>

</div>



