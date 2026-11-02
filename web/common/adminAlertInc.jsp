<%-- Statically included by common/academyHeader.jsp for administrators: when AdminAlertMgr holds alerts, a red Admin
     Alert line linking to adminAlert.do. --%><%@ page import="java.util.Set" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<% 
  // A.log("adminAlert() adminAlerts:" + adminAlerts);
  if (AdminAlertMgr.getAdminAlerts().size() > 0) {
	%>
      <font color=red><b>Admin Alert</b>: <a href="<%= AntwebProps.getDomainApp() %>/adminAlert.do">here</a></font>      
    <% 
  }
%>