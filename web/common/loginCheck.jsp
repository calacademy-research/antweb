<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.*" %>

<%                                                                                                                   
    Login accessLogin2 = LoginMgr.getAccessLogin(request);
    
    if (accessLogin2 == null) {
        AntwebUtil.log("loginCheck.jsp notLoggedIn redirection:" + HttpUtil.getRequestInfo(request));          
        
        response.sendRedirect("notLoggedIn.jsp");
        return;
    } else { 
        //AntwebUtil.log("info", "curatorCheck.jsp: accessGroupId:" + accessGroup1.getId());
    }
%>
