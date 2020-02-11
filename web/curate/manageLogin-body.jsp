<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<!-- manageLogin-body.jsp -->

<%
    String secureDomainApp = AntwebProps.getSecureDomainApp();
    String domainApp = AntwebProps.getDomainApp(); 
    Login accessLogin = LoginMgr.getAccessLogin(request);
    Group accessGroup = GroupMgr.getAccessGroup(request);
    
    if (accessLogin == null) {
      A.log("manageLogin-body.jsp accessGroup is null");
      return;
    }

    Login thisLogin = (Login) request.getSession().getAttribute("thisLogin");
    if (thisLogin == null) {
      A.log("manageLogin-body.jsp thisLogin is null"); 
      %> Login not found <%
      return;
    }    
    boolean isInvitee = false;
    Boolean isInviteeBool = (Boolean) request.getSession().getAttribute("isInvitee");
    if (isInviteeBool != null) {
      isInvitee = isInviteeBool.booleanValue();
    }

    if (accessLogin == null) {
      A.log("manageLogin-body.jsp accessGroup.getLogin() is null");
      return;
    }    
    boolean isSelf = (accessLogin.getId() == thisLogin.getId());
    ArrayList<Group> groups = (ArrayList) request.getSession().getAttribute("antwebGroups");

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
<h2>Viewing Login:<%= thisLogin.getEmail() %></h2>
<% } else { %>
<h2>Welcome to Antweb.org: <%= thisLogin.getEmail() %></h2>
<% } %>

<form action="<%= secureDomainApp %>/saveLogin.do" method="POST">

<h3>Login ID:</h3>
<input type="text" class="input_200" name="id" value="<%= thisLogin.getId() %>" disabled>

<h3>Email:</h3>
<input type="text" class="input_200" name="email" value="<%= thisLogin.getEmail() %>">

<h3>Username:</h3>
<input type="text" class="input_200" name="name" value="<%= thisLogin.getName() %>">

<h3>Password:</h3>
<input type="password" class="input_200" name="password" value="<%= thisLogin.getPassword() %>">

<h3>Retype Password:</h3>
<input type="password" class="input_200" name="retypePassword" value="<%= thisLogin.getPassword() %>">

<% if (LoginMgr.isAdmin(request)) { %>
<div class="action_hint">
NOTE: These will not be modified unless "Changed Password" button is selected.
</div>
<% } %>

<h3>First Name:</h3>
<input type="text" class="input_200" name="firstName" value="<%= thisLogin.getFirstName() %>">

<h3>Last Name:</h3>
<input type="text" class="input_200" name="lastName" value="<%= thisLogin.getLastName() %>">

<!-- h3>Created:</h3>
<input type="text" class="input_200" name="created" value="< %= thisLogin.getCreated() % >" disabled -->
 
<% if (LoginMgr.isAdmin(request)) { %>
    <h3>Institution:</h3>
    <select class="input_200" name="groups" size="10">
    <% int groupId = thisLogin.getGroupId();
       Iterator groupIter = groups.iterator();
       Group group = null;
       while (groupIter.hasNext()) {
         group = (Group) groupIter.next();
     %>
         <option <%= (thisLogin.getGroupId() == group.getId()) ? "selected" : "" %> value="<%= group.getName() %>"><%= group.getName() %>
    <% } %> <!-- end bioregion loop -->
    </select> 
<% } else { %>
     <br><br><h3>Institution: <%= thisLogin.getGroup().getName() %></h3>
<% } %>

<br />

<% if (AntwebProps.isDevMode()) AntwebUtil.log("manageLogin-body.jsp login.ProjectNames:" + thisLogin.getProjectNames()); %>

<h3>Project Access:</h3>
<select class="input_200" name="projects" multiple size="10" <%= (LoginMgr.isAdmin(request)) ? "" : " disabled" %>>
<option <%= ((thisLogin.getProjectNames() == null) || (thisLogin.getProjectNames().size() == 0))?"selected":"" %> value="none">None

<%        
        int count = 0;
        String name, title;

       // ArrayList<Project> atomicProjects = ProjectMgr.getAtomicProjects();
        ArrayList<Project> antProjects = ProjectMgr.getAllProjects();
        for (Project aProject : antProjects) {
            if (Project.ALLANTWEBANTS.equals(aProject.getName())) continue;
            if (Project.WORLDANTS.equals(aProject.getName())) continue;

            name = aProject.getName();
            title = aProject.getTitle();
%> <option <%= ((thisLogin.getProjectNames() != null) && (thisLogin.getProjectNames().contains(name)))?"selected":"" %> value="<%= name %>"><%= title %> <%
        } //end project loop

%>
</select>

<% //if (AntwebProps.isDevMode()) AntwebUtil.log("manageLogin-body.jsp login.ProjectNames:" + thisLogin.getProjectNames()); %>

<h3>Country Access:</h3>
<select class="input_200" name="countries" multiple size="10" <%= (LoginMgr.isAdmin(request)) ? "" : " disabled" %>>
<option <%= ((thisLogin.getCountryNames() == null) || (thisLogin.getCountryNames().size() == 0))?"selected":"" %> value="none">None

<%
        ArrayList<Geolocale> countries = GeolocaleMgr.getValidCountries();
        //Collections.sort(countries);
        for (Geolocale country : countries) {

          name = country.getName(); 
          //AntwebUtil.log("manageLogin-body.jsp name:" + name + " names:" + thisLogin.getCountryNames());
          %> 

              <option <%= ((thisLogin.getCountryNames() != null) && (thisLogin.getCountryNames().contains(name)))?"selected":"" %> value="<%= name %>"><%= name %> 
    <%      
      
        } //end Geolocale loop

%>
</select>


<div class="admin_action_item">
<% if (LoginMgr.isAdmin(request)) { %>
<div class="action_hint">
NOTE: To allow access to multiple areas, hold the Ctrl key, or the command key while clicking.
</div>
<% } %>
</div>

<br><br>
<% if (LoginMgr.isAdmin(request)) { %>
<h3>Is Admin:</h3>
<input type="checkbox" name="isAdmin" <%= (thisLogin.isAdmin() == true)?"checked":"" %> <%= (LoginMgr.isAdmin(request)) ? "" : " disabled" %>>
<% } %>


<h3>Can Upload Specimens:</h3>
<input type="checkbox" name="isUploadSpecimens" <%= (thisLogin.isUploadSpecimens() == true)?"checked":"" %> <%= (LoginMgr.isAdmin(request)) ? "" : " disabled" %>>

<h3>Can Upload Images:</h3>
<input type="checkbox" name="isUploadImages" <%= (thisLogin.isUploadImages() == true)?"checked":"" %> <%= (LoginMgr.isAdmin(request)) ? "" : " disabled" %>>


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
<a href="<%= domainApp %><%= (LoginMgr.isAdmin(request)) ? "/manageLogins.do" : "/curate.do" %>"><img border=0" src="<%= secureDomainApp %>/image/grey_cancel.gif" width="123" height="36"></a>

<% if (! isSelf) {   // You can't delete yourself
  //AntwebUtil.log("thisLogin.id:" + thisLogin.getId()); 
 %>


<%   if ((LoginMgr.isAdmin(request)) && (thisLogin.getPassword().equals(""))) { %>
<html:submit property="step"> 
<bean:message key="button.invite"/> 
</html:submit>
<%   } %>

<%   if (LoginMgr.isAdmin(request)) { %>
<html:submit property="changePassword"> 
<bean:message key="button.changePassword"/> 
</html:submit>
<%   } %>

<%   if (LoginMgr.isAdmin(request)) { %>
<html:submit property="delete"> 
<bean:message key="button.delete"/> 
</html:submit>
<%   } %>


<% if (LoginMgr.isAdmin(request)) { %>
&nbsp;&nbsp;&nbsp;Login as: <a href="<%= secureDomainApp %>/login.do?userName=<%= thisLogin.getName() %>&password=<%= thisLogin.getPassword() %>"><%= thisLogin.getName() %></a>
<% } %>


<% } %>
    </div>

</div>

</form>




