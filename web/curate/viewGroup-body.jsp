<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<%@include file="/curate/adminCheck.jsp" %>

<%
	    Group thisGroup = (Group) request.getSession().getAttribute("thisGroup");
	    if (thisGroup == null) {
	      A.log("viewGroup-body.jsp group is null");
	      return;
	    }
%>

<div class=right>
    <br>
    <a href="<%= AntwebProps.getDomainApp() %>/viewLogins.do">Login Manager</a>
    <br><a href="<%= AntwebProps.getDomainApp() %>/viewGroups.do">Group Manager</a>
</div>

<div class="admin_left">
    <h1>Group Form</h1>

    <logic:messagesPresent property="error">
        <h2>Sorry, you need to correct the following errors:</h2>
        <font color="red">
        <html:messages property="error" id="errMsg">
            <bean:write name="errMsg"/><br>
        </html:messages>
        </font>
    </logic:messagesPresent>

    <p><p><b>Name:</b> <%= thisGroup.getName() %>

    <form action="<%= domainApp %>/saveGroup.do" method="POST">

    <p><p><b>Institution ID:</b>
    <input type="text" class="input_200" name="id" value="<%= thisGroup.getId() %>">

    <p><p><b>Institution Name:</b>
    <input type="text" class="input_200" name="name" value="<%= thisGroup.getName() %>">

    <p><p><b>Institution Abbreviation:</b>
    <input type="text" class="input_200" name="abbrev" value="<%= thisGroup.getAbbrev() %>">



<%
    String selected = null;
%>
    <p><b>Group Admin Curator:</b>
      <select name="adminLoginId">  <!-- %= disabled % -->
        <%
           selected = "";
           if (thisGroup.getAdminLoginId() == 0) selected = " selected"; else selected = "";
    %>    <option value="0"<%= selected %>>None</option> <%
           ArrayList<Curator> curatorList = LoginMgr.getCurators();
           Collections.sort(curatorList, Curator.CuratorNameComparator);
           for (Curator curator: curatorList) {
             if (curator.getId() == thisGroup.getAdminLoginId()) {
               //A.log("viewGroup-body.jsp curator:" + curator.getId() + " this:" + thisGroup.getAdminLoginId());
               selected = "selected";
             } else {
               selected = "";
             }
         %>
          <option value='<%= curator.getId() %>' <%= selected %>><%= curator.getDisplayName() %></option>
        <% } %>	
      </select>

    <br><br>



    <input type="hidden" name="id" value="< %= thisGroup.getId() % >">

    <logic:present parameter="isNewGroup">
    <input type="hidden" name="newGroup" value="true">
    </logic:present>

    <p><b>Curator List:</b>
    <% 
     ArrayList<Curator> curators = thisGroup.getCurators()  ;
     if (curators != null) {
       for (Login curator : thisGroup.getCurators()) { 
         String name = curator.getFullName();
         if (Utility.isBlank(name)) name = curator.getEmail();
      %>
      <br>&nbsp;&nbsp;&nbsp;<a href='<%= AntwebProps.getDomainApp() %>/viewLogin.do?id=<%= curator.getId() %>'><%= name %></a>
    <% } 
     }
    %>

</div>

<div class=admin_right>
</div> 

<div class="clear"></div>
<br><br>

<div class="msg in_admin">
    <div class="msg_actions" align="center">
        <input border="0" type="image" src="<%= domainApp %>/image/orange_done.gif" width="98" height="36" name="done" value="Done">
        <a href="<%= domainApp %>/viewGroups.do"><img border=0" src="<%= domainApp %>/image/grey_cancel.gif" width="123" height="36"></a>

        <!-- input border="0" type="image"  width="98" height="36" name="delete" value="Delete" -->
        <html:submit property="step"> 
        <bean:message key="button.delete"/> 
        </html:submit>


        <br><br>
        <a href="<%= domainApp %>/listUploads.do?groupId=<%= thisGroup.getId() %>">Upload Logs</a>
    </div>
</div>


</form>
