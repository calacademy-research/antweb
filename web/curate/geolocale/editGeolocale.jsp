<%-- Edit Geolocale page (editGeolocale.do, EditGeolocaleAction), curators look (curatorCheck.jsp). Tiles wrapper
     of curate/geolocale/editGeolocale-body.jsp. Docs: docs/guides/manager-tools.md (Edit Geolocale). Tests:
     tests/e2e/test_geolocale_edit.py, test_geolocale_edit_page.py. --%>
<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>

<%@include file="/curate/curatorCheck.jsp" %>

<bean:define id="project" value="" toScope="session"/>
<%
        session.removeAttribute("taxon");
%>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Edit Geo Locale" />
	<tiles:put name="body-content" value="/curate/geolocale/editGeolocale-body.jsp" />	
</tiles:insert>
