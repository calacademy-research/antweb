<%@ page language="java" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.Login" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<bean:define id="project" value="" toScope="session"/>

<% 
    if (!AntwebMgr.isPopulated()) { %>
      <b>Server Initializing</b>
<%      return;
    }

    if ((request.getRequestURI().indexOf("login.jsp") != -1) || (request.getRequestURI().indexOf("notLoggedIn.jsp") != -1))  { %>

<div id="admin_header">
    <div class="admin_header_logo"></div>
    <div class="admin_header_login_out"></div>
    <div class="admin_header_login_out"></div>
</div>

<div class="clear"></div>

 <% } else { %>

<%
        Login accessLogin = LoginMgr.getAccessLogin(request);
        Group accessGroup = GroupMgr.getAccessGroup(request);
        if (accessLogin == null) {
        
                // Markj.  Jul 19 2010.
                //Cannot forward after response has been committed
         %>     <!-- jsp :forward page="notLoggedIn.jsp" / -->  
         
         Page has expired <a href="<%= domainApp %>/login.do?target=<%= HttpUtil.getTarget(request) %>">Login</a>         
         
         <%        
                // This does not work because some data has already been written to outputStream.
                //response.sendRedirect("notLoggedIn.jsp"); 
               //out.println("You must re-login <a href=" + siteURL + "/login.jsp>Login</a>");
               // org.calacademy.antweb.util.AntwebUtil.log("adminHeader.jsp - manual redirect to loggin.jsp.");
            return;
        }

        String email = null;
        String groupName = accessGroup.getName();
        if (accessLogin != null) {
            email = accessLogin.getEmail();
        }        
        ArrayList<String> projList = accessLogin.getProjectNames();
%>

<div id="admin_header">
    <div class="admin_header_logo"></div>
    <div class="admin_header_login_out"></div>
    <div class="admin_header_login_out"><b><%= accessLogin.getDisplayName() %></b> | <a href="<%= domainApp %>/logout.do">Log out</a> | <a href="<%= domainApp %>/curate.do">Curate</a> | <a href="<%= domainApp %>/">Home</a></div>
</div>

<div class="clear"></div>

 <% } %>
