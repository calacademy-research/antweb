<%-- Upload groups list (viewGroups.do, ManageGroupsAction), admins only (adminCheck.jsp). Tiles wrapper of
     curate/viewGroups-body.jsp. Docs: docs/guides/adding-an-uploader.md. Tests: tests/e2e/test_add_uploader.py,
     test_admin_staging.py. --%>
<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>


<%@include file="/curate/adminCheck.jsp" %>


<bean:define id="project" value="" toScope="session"/> 
 
<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
    <tiles:put name="title" value="Upload groups - AntWeb" />
    <tiles:put name="body-content" value="/curate/viewGroups-body.jsp" />    
</tiles:insert>
