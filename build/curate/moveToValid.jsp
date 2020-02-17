<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.curate.moveToValid.*" %>

<%@ page import="java.util.*" %>

<%@include file="/curate/curatorCheck.jsp" %>

<bean:define id="project" value="" toScope="session"/> 
 
<%@include file="/common/antweb_admin-defs.jsp" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<% ArrayList<MoveToValid> movesToValid = (ArrayList<MoveToValid>) request.getAttribute("movesToValid"); 
AntwebUtil.log("moveToValid.jsp");
%>

<div class=left>

<h1>Move To Valid</h1>
<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>

<%
    String message = (String) request.getAttribute("message"); 
    if (message != null) out.println("<font color=green>" + message + "</font><br>");

    int i = 0;    
    for (MoveToValid moveToValid : movesToValid) {
      if (i == 0) { %>
        <table>
        <tr><th></th><th>Project Name</th><th>Taxon Name</th><th>Rank</th><th>Status</th><th>Current Valid Name</th><th></th></tr>
<%      }
      ++i; %>
      <tr>
      <td><%= i %></td>
      <td><%= moveToValid.getProjectName() %></td>
      <td><a href="<%= AntwebProps.getDomainApp() + "/description.do?taxonName=" + moveToValid.getTaxonName() %>"><%= moveToValid.getTaxonName() %></a></td>
      <td><%= moveToValid.getRank() %></td>
      <td><%= moveToValid.getStatus() %></td>
 <% if (moveToValid.getCurrentValidName() != null) { %>
      <td><a href="<%= AntwebProps.getDomainApp() + "/description.do?taxonName=" + moveToValid.getCurrentValidName() %>"><%= moveToValid.getCurrentValidName() %></a></td>
   <% if (AntwebProps.isDevMode()) { %>   
      <td><A href="<%= AntwebProps.getDomainApp() %>/speciesListMove.do?fromTaxonName=<%= moveToValid.getTaxonName() %>&toTaxonName=<%= moveToValid.getCurrentValidName() %>">Move</a></td>
   <% } else { %>
      <td></td>   
   <% } %>
 <% } else { %>
    <td></td><td></td>
 <% } %>
      </tr>

 <% } %>
</table>
<hr></hr>

<br><br>
</div > 
