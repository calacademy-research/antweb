<%-- Tiles page "AntWeb - User Guide", requested directly (on the JSP allow list in docker/caddy/Caddyfile; linked
     from about_nav.jsp), body user_guide-body.jsp. Removes the session attribute taxon. --%><%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/>
<%
        session.removeAttribute("taxon");
%>


<%@include file="common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="AntWeb - User Guide" />
	<tiles:put name="body-content" value="/user_guide-body.jsp" />	
</tiles:insert>
