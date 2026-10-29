<%-- Answer of descEditImageUpload.do (DescEditImageUploadAction) for a file uploaded from a taxon page's text
     editor: the file's link and img tag. Tiles wrapper of common/descEdit/taxonImageConfirmForm-body.jsp.
     Curators; the file is in web/curator/<login id>/. No functional e2e test (docs/catalog/actions.csv). --%>
<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 

<%
        session.removeAttribute("taxon");
%>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Taxon Image Confirm" />
	<tiles:put name="body-content" value="/common/descEdit/taxonImageConfirmForm-body.jsp" />	
</tiles:insert>
