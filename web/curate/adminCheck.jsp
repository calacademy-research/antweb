<%-- Admin check included (static include) at the top of admin pages: not logged in goes to notLoggedIn.jsp, a
     login that is not an admin to permissionDenied.jsp (logged); otherwise removes the session's taxon. Defines
     accessLoginCheck. Writes nothing to the page. --%>
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
