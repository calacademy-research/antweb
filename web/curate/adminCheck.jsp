
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%
  Login accessLoginCheck = LoginMgr.getAccessLogin(request);
  if (true) {                                                                                                          
        if (!LoginMgr.isLoggedIn(request)) {
            response.sendRedirect(AntwebProps.getDomainApp() + "/notLoggedIn.jsp");
        } else if (!LoginMgr.isAdmin(accessLoginCheck)) {
            org.calacademy.antweb.util.AntwebUtil.log("adminCheck.jsp.  Permission denied for login:" + accessLoginCheck.getName());
            response.sendRedirect(AntwebProps.getDomainApp() + "/permissionDenied.jsp");
        }
        session.removeAttribute("taxon");
  }
%>
