<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Project" %>

<% 
    Project thisProject = (Project) request.getAttribute("thisProject"); %>                        

<%@include file="/curate/curatorCheck.jsp" %>

<%
    Login accessLogin = LoginMgr.getAccessLogin(request);

    if (!LoginMgr.isAdmin(accessLogin) && !accessLogin.getProjectNames().contains(thisProject)) {
        org.calacademy.antweb.util.AntwebUtil.log("editProject.jsp. Login:" + accessLogin + " does not have access to " + thisProject);        
        response.sendRedirect("permissionDenied.jsp");
    }
%>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
    <tiles:put name="title" value="Edit Project - AntWeb" />
    <tiles:put name="body-content" value="/curate/project/editProject-body.jsp" />    
</tiles:insert>
