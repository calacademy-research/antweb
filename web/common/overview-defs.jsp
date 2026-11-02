<%-- Unused as of 2026-11-02: a tiles definition antweb.default (basicLayout.jsp, academyHeader.jsp, menubar
     common/overviewNav.jsp, copyright.jsp) that no JSP includes; layout.jsp inserts siteNav, not menubar. Caddy
     answers a direct request with 404 (not on the JSP allow list in docker/caddy/Caddyfile). --%><%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<tiles:definition
	id="antweb.default"
	page="/layouts/basicLayout.jsp"
	scope="request">
		<tiles:put name="academyHeader" value="/common/academyHeader.jsp" />
		<tiles:put name="menubar" value="/common/overviewNav.jsp" />
		<tiles:put name="copyright" value="/common/copyright.jsp" />	
</tiles:definition>
