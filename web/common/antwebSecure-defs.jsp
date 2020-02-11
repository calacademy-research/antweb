<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<tiles:definition
	id="antweb.default"
	page="/layouts/secureLayout.jsp"
	scope="request">
		<tiles:put name="academyHeader" value="/common/academyHeader.jsp" />
		<tiles:put name="menubar" value="/common/siteNav.jsp" />
		<tiles:put name="copyright" value="/common/copyright.jsp" />	
</tiles:definition>
