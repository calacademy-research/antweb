<%-- Image Upload Report page (imageUploadReport.do?id=N, ImageUploadReport): one batch of uploaded images.
     Tiles wrapper of imageUploader/imageUploadReport-body.jsp. Docs: docs/flows/image-upload.md (section 7).
     Tests: tests/e2e/test_image_upload.py. --%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Image Upload Report" />
	<tiles:put name="body-content" value="/imageUploader/imageUploadReport-body.jsp" />	
</tiles:insert>
