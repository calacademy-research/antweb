<%-- Tiles page of about.do: "About AntWeb", body about-body.jsp. Removes the session attribute taxon. On the JSP
     allow list in docker/caddy/Caddyfile. Tests: tests/e2e/test_actions.py::test_public_page,
     test_curate_page_layout.py. --%><%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/>
<%
        session.removeAttribute("taxon");
%>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="About AntWeb" />
	<tiles:put name="body-content" value="/about-body.jsp" />	
</tiles:insert>
