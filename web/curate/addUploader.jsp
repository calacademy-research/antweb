<%-- Add an uploader page (addUploader.do, AddUploaderAction), admins only (adminCheck.jsp): find or invite the
     login, choose or create the upload group, give upload rights. Tiles wrapper of curate/addUploader-body.jsp.
     Docs: docs/guides/adding-an-uploader.md. Tests: tests/e2e/test_add_uploader.py. --%>
<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@include file="/curate/adminCheck.jsp" %>

<bean:define id="project" value="" toScope="session"/> 

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
    <tiles:put name="title" value="Add an uploader - AntWeb" />
    <tiles:put name="body-content" value="/curate/addUploader-body.jsp" />    
</tiles:insert>
