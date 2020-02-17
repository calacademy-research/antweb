<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@ page import="org.calacademy.antweb.util.*" %>

<tiles:definition
	id="antweb.default"
	page="/layouts/basicLayout.jsp"
	scope="request">
		<tiles:put name="academyHeader" value="/common/academyHeader.jsp" />
		<tiles:put name="menubar" value="/common/siteNav.jsp" />	
		<tiles:put name="copyright" value="/common/copyright.jsp" />	

</tiles:definition>
		

