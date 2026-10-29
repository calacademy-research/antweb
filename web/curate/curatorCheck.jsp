<%-- Curator check included (static include) at the top of curator pages: without a curator login (admins are
     curators too) the visitor goes to notLoggedIn.jsp (logged). Defines accessLoginCheck. Writes nothing to the
     page. --%><%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.Login" %>

<%                            
    Login accessLoginCheck = LoginMgr.getAccessLogin(request);
    //A.log("curatorCheck.jsp accessLoginCheck:" + accessLoginCheck);
    
    if (accessLoginCheck == null || !accessLoginCheck.isCurator()) {
        AntwebUtil.log("curatorCheck.jsp notLoggedIn redirection:" + HttpUtil.getRequestInfo(request));          
        
        // Absolute: a page under /curate/ would otherwise go to /curate/notLoggedIn.jsp, which does not exist.
        response.sendRedirect(AntwebProps.getDomainApp() + "/notLoggedIn.jsp");
        return;
    } else { 
        //AntwebUtil.log("info", "curatorCheck.jsp: accessLogin:" + accessLogin);
    }
%>
