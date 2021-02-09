<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.Login" %>
<%@ page import="org.calacademy.antweb.TeamMember" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<%
    Login accessLogin = LoginMgr.getAccessLogin(request);
%>

<% 
  boolean turnedOn = true;
%>


<jsp:useBean id="curatorList" scope="session" class="java.util.ArrayList" />

<div class=left>
<h1>Curators</h1>
<hr></hr>

<%
    ArrayList curators = (ArrayList) request.getSession().getAttribute("curatorList");
    int displayIndex = 0;
    boolean accessLoginIncluded = false;
    %>
    
<logic:iterate id="teamMember" type="org.calacademy.antweb.TeamMember" name="curatorList" indexId="curatorCount">

<% request.setAttribute("teamMember", teamMember); %>


<% if (((accessLogin != null) && (accessLogin.getId() == teamMember.getId())) && (turnedOn)) { 
     accessLoginIncluded = true;

     AntwebUtil.log("curators-body accessLoginId:" + accessLogin.getId());
%>

  <br> <%@include file="/curate/teamMemberForm.jsp" %>

<% } else { 
      //if (accessLogin!= null) AntwebUtil.log("curators-body 1 accessLoginId:" + accessLogin.getId() + " teamMemberId:" + teamMember.getId());
%>

  <br> <%@include file="/curate/teamMember.jsp" %>

   <% displayIndex = curatorCount.intValue() + 1; %>

<% } %>

</logic:iterate>

<% // if (displayIndex == 1) out.println("No curators"); %>

<% // To Create a new Curator record, if user is a curator and their team_member record does not exist.
   if ((turnedOn) && (accessLogin != null) && (!accessLoginIncluded)) { 
     TeamMember newTeamMember = new TeamMember();
     newTeamMember.init();
     newTeamMember.setId(accessLogin.getId());
     request.setAttribute("teamMember", newTeamMember); 

     AntwebUtil.log("curators-body 2 accessLogin" + accessLogin.getId() + " new teamMember");     
     %>
	     <%@include file="/curate/teamMemberForm.jsp" %>
<% } %>

</div>
