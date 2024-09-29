<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<!-- viewLogin-body.jsp -->

<div class=right>
<br>
<a href="<%= AntwebProps.getDomainApp() %>/viewLogins.do">Login Manager</a>
<br><a href="<%= AntwebProps.getDomainApp() %>/viewGroups.do">Group Manager</a>
</div>

<%
    String secureDomainApp = AntwebProps.getSecureDomainApp();
    String domainApp = AntwebProps.getDomainApp(); 
    Login accessLogin = LoginMgr.getAccessLogin(request);
    Group accessGroup = GroupMgr.getAccessGroup(request);
    if (accessLogin == null) {
      A.log("viewLogin-body.jsp accessLogin is null");
      return;
    }
    Login thisLogin = (Login) request.getSession().getAttribute("thisLogin");
    if (thisLogin == null) {
      A.log("viewLogin-body.jsp thisLogin is null"); 
      %> Login not found <%
      return;
    }    
    boolean isInvitee = false;
    Boolean isInviteeBool = (Boolean) request.getSession().getAttribute("isInvitee");
    if (isInviteeBool != null) {
      isInvitee = isInviteeBool.booleanValue();
    }

    boolean isSelf = (accessLogin.getId() == thisLogin.getId());
    ArrayList<Group> groups = (ArrayList) request.getSession().getAttribute("antwebGroups");

A.log("viewLogin-body.jsp uploadAs:" + thisLogin.getUploadAs());
%>
 
<div class=admin_left>
<logic:messagesPresent property="error">
    <h2>Sorry, you need to correct the following errors:</h2>
    <font color="red">
    <html:messages property="error" id="errMsg">
        <bean:write name="errMsg"/><br>
    </html:messages>
    </font>
</logic:messagesPresent>

<% if (!isInvitee) { %>
<h2>Viewing Login: <%= thisLogin.getEmail() %></h2>
<% } else { %>
<h2>Welcome to Antweb.org: <%= thisLogin.getEmail() %></h2>
<% } %>

<form action="<%= secureDomainApp %>/saveLogin.do" method="POST">

<br><br>
<h3>Login ID:</h3>
<input type="text" class="input_200" name="id" value="<%= thisLogin.getId() %>" disabled>
<br><br>
<h3>Email:</h3>
<input type="text" class="input_200" name="email" value="<%= thisLogin.getEmail() %>">
<br><br>
<h3>Username:</h3>
<input type="text" class="input_200" name="name" value="<%= thisLogin.getName() %>">
<br><br>

<h3>Password:</h3>
<input type="password" class="input_200" name="password" value="<%= thisLogin.getPassword() %>">
<br><br>

<h3>Retype Password:</h3>
<input type="password" class="input_200" name="retypePassword" value="<%= thisLogin.getPassword() %>">

<% if (accessLogin.isAdmin()) { %>
<div class="action_hint">
NOTE: These will not be modified unless "Changed Password" button is selected.
</div>
<% } %>

<br>

<h3>First Name:</h3>
<input type="text" class="input_200" name="firstName" value="<%= thisLogin.getFirstName() %>">
<br><br>

<h3>Last Name:</h3>
<input type="text" class="input_200" name="lastName" value="<%= thisLogin.getLastName() %>">
<br><br>

<h1>Created: <%= thisLogin.getCreated() %></h>

<% if (accessLogin.isAdmin()) { %>
    <h3>Institution:</h3>
    <select class="input_200" name="groups" size="10">
    <% int groupId = thisLogin.getGroupId();  
       Collections.sort(groups, Group.getGroupNameComparator);  
       for (Group group : groups) {
     %>
         <option <%= (thisLogin.getGroupId() == group.getId()) ? "selected" : "" %> value="<%= group.getName() %>"><%= group.getName() %>
    <% } %> <!-- end groups loop -->
    </select> 
<% } else { %>
     <br><br><h3>Institution: <%= thisLogin.getGroup().getName() %></h3>
<% } %>


<% 
//if (AntwebProps.isDevMode()) AntwebUtil.log("viewLogin-body.jsp login.ProjectNames:" + thisLogin.getProjectNames()); 
%>
<br><br>

<h3>Project Access:</h3>
<select class="input_200" name="projects" multiple size="10" <%= (accessLogin.isAdmin()) ? "" : " disabled" %>>
<option <%= ((thisLogin.getProjectNames() == null) || (thisLogin.getProjectNames().size() == 0))?"selected":"" %> value="none">None

<%        
        int count = 0;
        String name, title;

       // ArrayList<Project> atomicProjects = ProjectMgr.getAtomicProjects();
        ArrayList<Project> antProjects = ProjectMgr.getAllProjects();
        Collections.sort(antProjects, Project.getNameComparator);
        for (Project aProject : antProjects) {
            if (Project.ALLANTWEBANTS.equals(aProject.getName())) continue;
            if (Project.WORLDANTS.equals(aProject.getName())) continue;

            name = aProject.getName();
            title = aProject.getTitle();
%> <option <%= ((thisLogin.getProjectNames() != null) && (thisLogin.getProjectNames().contains(name)))?"selected":"" %> value="<%= name %>"><%= title %> <%
        } //end project loop

%>
</select>
<% if (LoginMgr.isAdmin(thisLogin)) { %>
<br>* Admin automatically get access to all projects.
<% } %>
<br><br>

<% //if (AntwebProps.isDevMode()) AntwebUtil.log("viewLogin-body.jsp login.ProjectNames:" + thisLogin.getProjectNames()); %>

<h3>Country Access:</h3>
<select class="input_200" name="countries" multiple size="10" <%= (accessLogin.isAdmin()) ? "" : " disabled" %>>
<option <%= ((thisLogin.getCountryNames() == null) || (thisLogin.getCountryNames().size() == 0))?"selected":"" %> value="none">None

<%
        ArrayList<Geolocale> countries = GeolocaleMgr.getValidCountries();
        //Collections.sort(countries);
        for (Geolocale country : countries) {

          name = country.getName(); 
          //AntwebUtil.log("viewLogin-body.jsp name:" + name + " names:" + thisLogin.getCountryNames());
          %> 

              <option <%= ((thisLogin.getCountryNames() != null) && (thisLogin.getCountryNames().contains(name)))?"selected":"" %> value="<%= name %>"><%= name %> 
    <%      
      
        } //end Geolocale loop

%>
</select>
<% if (LoginMgr.isAdmin(thisLogin)) { %>
<br>* Admin automatically get access to all countries.
<% } %>

<div class="admin_action_item">
<% if (accessLogin.isAdmin()) { %>
<br>
<div class="action_hint">
<b>NOTE:</b> For above lists, to allow access to multiple areas, hold the Ctrl key, or the command key while clicking.
</div>
<% } %>
</div>

<br><br>
<% if (accessLogin.isAdmin()) { %>
<h3>Is Super Admin:
<input type="checkbox" name="isAdmin" <%= (thisLogin.isAdmin() == true)?"checked":"" %> <%= (accessLogin.isAdmin()) ? "" : " disabled" %>>
</h3>
<% } %>

<br>
<h3>Can Upload Specimens:
<input type="checkbox" name="isUploadSpecimens" <%= (thisLogin.isUploadSpecimens() == true)?"checked":"" %> <%= (accessLogin.isAdmin()) ? "" : " disabled" %>>
</h3>
<br>
<h3>Can Upload Images:
<input type="checkbox" name="isUploadImages" <%= (thisLogin.isUploadImages() == true)?"checked":"" %> <%= (accessLogin.isAdmin()) ? "" : " disabled" %>>
</h3>

<% if (LoginMgr.isAdmin(accessLogin)) { %>
<br>
<H3>Upload As (comma separated list of curator IDs):
<input type="text" name="uploadAs" <%= (LoginMgr.isAdmin(accessLogin)) ? "" : " disabled" %> value="<%= thisLogin.getUploadAs() %>">
</h3>
<% } %>

<input type="hidden" name="id" value="<%= thisLogin.getId() %>">
<logic:present parameter="isNewLogin">
<input type="hidden" name="newLogin" value="true">
</logic:present>

<br />
<br />

</div>
<div class=admin_right>
</div> 

<div class="clear"></div>
<br />
<br />
<div class="msg in_admin">
    <div class="msg_actions" align="center">
<input border="0" type="image" src="<%= secureDomainApp %>/image/orange_done.gif" width="98" height="36" value="Done">
<a href="<%= domainApp %><%= (accessLogin.isAdmin()) ? "/viewLogins.do" : "/curate.do" %>"><img border=0" src="<%= secureDomainApp %>/image/grey_cancel.gif" width="123" height="36"></a>

<% if (! isSelf) {   // You can't delete yourself
  //AntwebUtil.log("thisLogin.id:" + thisLogin.getId()); 
 %>


<%   if ((accessLogin.isAdmin()) && (thisLogin.getPassword().equals(""))) { %>
<html:submit property="step"> 
<bean:message key="button.invite"/> 
</html:submit>
<%   } %>

<%   if (accessLogin.isAdmin()) { %>
<html:submit property="changePassword"> 
<bean:message key="button.changePassword"/> 
</html:submit>
<%   } %>

<%   if (accessLogin.isAdmin()) { %>
<html:submit property="delete"> 
<bean:message key="button.delete"/> 
</html:submit>
<%   } %>


<% if (accessLogin.isAdmin()) { %>
&nbsp;&nbsp;&nbsp;Login as: <a href="<%= secureDomainApp %>/login.do?userName=<%= thisLogin.getName() %>&password=<%= thisLogin.getPassword() %>"><%= thisLogin.getName() %></a>
<% } %>


<% } %>
    </div>

</div>

</form>




