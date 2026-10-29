<%-- One upload group's form (viewGroup.do, ViewGroupAction; newGroup.do, NewGroupAction; saveGroup.do when the
     form is refused), admins only (adminCheck.jsp). Tiles wrapper of curate/viewGroup-body.jsp. Docs:
     docs/guides/adding-an-uploader.md. Tests: tests/e2e/test_add_uploader.py, test_group_form_help.py. --%>
<%@ page errorPage = "/error.jsp" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.util.AntwebUtil" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 

<%@include file="/common/antweb_admin-defs.jsp" %>

<%@include file="/curate/adminCheck.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
    <tiles:put name="title" value="Upload group - AntWeb" />
    <tiles:put name="body-content" value="/curate/viewGroup-body.jsp" />    
</tiles:insert>
