<%-- Tiles wrapper of common/descEdit/descEditImageConfirm-body.jsp (title "Image Confirm"). Unused as of
     2026-10-30: no action, include or reference found (descEditImageUpload.do answers with
     taxonImageConfirmForm.jsp or overviewImageConfirmForm.jsp), and Caddy does not serve it directly. --%><%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Image Confirm" />
	<tiles:put name="body-content" value="/common/descEdit/descEditImageConfirm-body.jsp" />	
</tiles:insert>
