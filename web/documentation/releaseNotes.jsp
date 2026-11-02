<%-- Tiles page Release Notes, requested directly (on the JSP allow list in docker/caddy/Caddyfile), body
     documentation/release.jsp. Removes the session attribute taxon. Uses bean:define without the bean taglib, so
     that tag is written to the page as text. Docs: docs/RELEASING.md. --%>
<bean:define id="project" value="" toScope="session"/> 
<%
        session.removeAttribute("taxon");
%>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Release Notes" />
	<tiles:put name="body-content" value="/documentation/release.jsp" />	
</tiles:insert>



