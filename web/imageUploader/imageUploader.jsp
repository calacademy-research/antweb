<%@ page errorPage = "error.jsp" %>
<%@ page import = "org.calacademy.antweb.Group" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/>

<%@include file="/curate/curatorCheck.jsp" %>

<%
    session.removeAttribute("taxon");

    Login accessLogin = LoginMgr.getAccessLogin(request);
    // Same rule as ImageUploaderAction and ImageUploadBatchAction: upload permission, or an administrator.
    if (!(Boolean.TRUE.equals(accessLogin.isUploadImages()) || Boolean.TRUE.equals(accessLogin.isAdmin()))) {
        org.calacademy.antweb.util.AntwebUtil.log("imageUpload.jsp.  ImageUpload denied for login:" + accessLogin);
        response.sendRedirect("permissionDenied.jsp");
        return;
    }
%>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
        <tiles:put name="title" value="AntWeb Image Uploader" />
        <tiles:put name="body-content" value="/imageUploader/imageUploader-body.jsp" />
</tiles:insert>
