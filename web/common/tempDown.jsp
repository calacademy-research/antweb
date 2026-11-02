<%-- The Temporarily Down page ("This functionality is temporarily down."): the comments in WEB-INF/struts-config.xml
     say to forward an action here to switch it off (e.g. taxonomicPage, mapComparison); no action does at present.
     Its body common/tempDown-body.jsp did not compile until 2026-11-02 (missing import), when it was fixed. Caddy
     answers a direct request with 404 (not on the JSP allow list in docker/caddy/Caddyfile). --%><%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 
<%
        session.removeAttribute("taxon");
%>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Temporarily Down.." />
	<tiles:put name="body-content" value="/common/tempDown-body.jsp" />	
</tiles:insert>
