<%@ page errorPage = "error.jsp" %>
<%@ page import = "org.calacademy.antweb.Group" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/>

<%@include file="/curate/curatorCheck.jsp" %>

<%
    session.removeAttribute("taxon");

    Login accessLogin = LoginMgr.getAccessLogin(request);
    if (!(accessLogin.isUploadImages())) {
        org.calacademy.antweb.util.AntwebUtil.log("imageUpload.jsp.  ImageUpload denied for login:" + accessLogin);
        response.sendRedirect("permissionDenied.jsp");
    }
%>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
        <tiles:put name="title" value="Images Uploaded" />
        <tiles:put name="body-content" value="/imageUploader/imagesUploaded-body.jsp" />
</tiles:insert>
