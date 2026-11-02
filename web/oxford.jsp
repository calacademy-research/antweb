<%-- Tiles page of oxford.do (World Ant Collections): "AntWeb: World Ants Collections - Oxford", body
     oxford-body.jsp. Removes the session attribute taxon. On the JSP allow list in docker/caddy/Caddyfile; requested
     that way, the body prints only "Invalid URL.  Direct JSP calls unsupported." (HttpUtil.isStaticCallCheck).
     Tests: tests/e2e/test_world_ant_collections.py. --%><%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 
<%
        session.removeAttribute("taxon");
%>


<%@include file="common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="AntWeb: World Ants Collections - Oxford" />
	<tiles:put name="body-content" value="/oxford-body.jsp" />	
</tiles:insert>
