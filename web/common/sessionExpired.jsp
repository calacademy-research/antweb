<%-- Tiles page of the global forward sessionExpired (the compare, map and field guide results actions and
     SpeciesListToolAction, when the session lost their search results): "AntWeb - Please Log In", body
     common/sessionExpired-body.jsp. --%><%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 
<%
        session.removeAttribute("taxon");
%>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="AntWeb - Please Log In" />
	<tiles:put name="body-content" value="/common/sessionExpired-body.jsp" />	
</tiles:insert>

