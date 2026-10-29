<%-- Museum Manager page (manageMuseum.do, ManageMuseumsAction), admins only (adminCheck.jsp). Tiles wrapper of
     curate/manageMuseums-body.jsp. Docs: docs/guides/manager-tools.md. Tests: tests/e2e/test_manager_tools.py,
     test_curate_cleanup.py. --%>
<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>

<%@include file="/curate/adminCheck.jsp" %>

<bean:define id="project" value="" toScope="session"/> 
 
<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
    <tiles:put name="title" value="Manage Museums - AntWeb" />
    <tiles:put name="body-content" value="/curate/manageMuseums-body.jsp" />    
</tiles:insert>
