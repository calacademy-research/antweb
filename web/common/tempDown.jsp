<%-- Unused as of 2026-11-02: the Temporarily Down page that comments in struts-config.xml suggest forwarding an
     action to while it is switched off; no action does. Its body common/tempDown-body.jsp did not compile and was
     removed on that date. Caddy answers a direct request with 404 (not on the JSP allow list in
     docker/caddy/Caddyfile). --%><%@ page errorPage = "/error.jsp" %>

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
