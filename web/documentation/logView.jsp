<%-- Unused as of 2026-11-02: a tiles page Release Notes whose body documentation/log.jsp, which did not compile, was
     removed on that date. No action forwards here, no page links it, and Caddy answers a direct request with 404
     (not on the JSP allow list in docker/caddy/Caddyfile). --%>
<bean:define id="project" value="" toScope="session"/> 
<%
        session.removeAttribute("taxon");
%>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Release Notes" />
	<tiles:put name="body-content" value="/documentation/log.jsp" />	
</tiles:insert>



