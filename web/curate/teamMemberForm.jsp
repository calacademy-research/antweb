
<% TeamMember teamMemberEdit = (TeamMember) request.getAttribute("teamMember"); %>

<br><br>

<hr>
<br>
<h3>Your Curator Profile...</h3>
      
<% if ((teamMemberEdit != null) && (teamMemberEdit.getId() != 0)) { %>
  <%@ include file="/curate/teamMemberImage.jsp" %>
<% } %>

<form action="<%= domainApp %>/teamMember.do" method="POST"><%= org.calacademy.antweb.util.Csrf.field(request) %>

<input type="hidden" name="id" value="<%= accessLogin.getId() %>">

<h3>Name:</h3>
<input type="text" class="input_200" name="name" value="<%= org.calacademy.antweb.util.Html.escAttr(teamMemberEdit.getName()) %>">

<h3>Role/Org:</h3>
<input type="text" class="input_200" name="roleOrg" value="<%= org.calacademy.antweb.util.Html.escAttr(teamMemberEdit.getRoleOrg()) %>">

<h3>Email:</h3>
<input type="text" class="input_200" name="email" value="<%= org.calacademy.antweb.util.Html.escAttr(teamMemberEdit.getEmail()) %>">

<h3>Short description:</h3>
<input type="textarea" cols="60" rows="15" name="text" value="<%= org.calacademy.antweb.util.Html.escAttr(teamMemberEdit.getText()) %>">


<input type=submit class=submit value="Update &#187;">
</form>

<br>
<hr>