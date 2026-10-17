
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%
  Login accessLoginCheck = LoginMgr.getAccessLogin(request);
  if (true) {                                                                                                          
        if (!LoginMgr.isLoggedIn(request)) {
            response.sendRedirect(AntwebProps.getDomainApp() + "/notLoggedIn.jsp");
            return;
        } else if (!LoginMgr.isAdmin(accessLoginCheck)) {
            org.calacademy.antweb.util.AntwebUtil.log("adminCheck.jsp.  Permission denied for login:" + accessLoginCheck.getName());
            response.sendRedirect(AntwebProps.getDomainApp() + "/permissionDenied.jsp");
            return;
        }
        session.removeAttribute("taxon");
  }
%>
