<%-- Country Documentation page (countryDoc.do, a forward with no action class; admins only, by adminCheck.jsp in
     the body). Tiles wrapper of curate/geolocale/countryDoc-body.jsp. Linked from the Geolocale Manager. --%>
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
	<tiles:put name="title" value="Country Documentation" />
	<tiles:put name="body-content" value="/curate/geolocale/countryDoc-body.jsp" />	
</tiles:insert>



