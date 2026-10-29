<%-- Image Upload Reports page (listImageUploads.do, ListUploadsAction): one row per image upload batch (table
     image_upload), for a group or a curator; curators and admins (curatorCheck.jsp). Tiles wrapper of
     imageUploader/listImageUploads-body.jsp. Docs: docs/flows/image-upload.md. Tests: tests/e2e/test_image_upload.py. --%>
<%@ page errorPage = "error.jsp" %>
<%@ page import = "org.calacademy.antweb.Group" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/>

<%@include file="/curate/curatorCheck.jsp" %>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
        <tiles:put name="title" value="Image Upload Reports" />
        <tiles:put name="body-content" value="/imageUploader/listImageUploads-body.jsp" />
</tiles:insert>
