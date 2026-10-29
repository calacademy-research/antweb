<%-- Project settings page (editProject.do, EditProjectAction), admins only (curatorCheck.jsp, then an admin check
     that redirects to permissionDenied.jsp). Tiles wrapper of curate/project/editProject-body.jsp. Docs:
     docs/guides/manager-tools.md (Project settings). Tests: tests/e2e/test_manager_tools.py. --%>
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

    // Project settings are for administrators (EditProjectAction says so too). Until 2026-10-27 this compared
    // project names with a Project (never equal) and went on building the page after the redirect.
    if (!LoginMgr.isAdmin(accessLogin)) {
        org.calacademy.antweb.util.AntwebUtil.log("editProject.jsp. Login:" + accessLogin + " does not have access to " + thisProject);        
        response.sendRedirect(AntwebProps.getDomainApp() + "/permissionDenied.jsp");
        return;
    }
%>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
    <tiles:put name="title" value="Edit Project - AntWeb" />
    <tiles:put name="body-content" value="/curate/project/editProject-body.jsp" />    
</tiles:insert>
