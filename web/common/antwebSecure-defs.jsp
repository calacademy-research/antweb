<%-- The tiles definition antweb.default for the log-in and account pages (login.jsp, editLogin.jsp,
     resetPassword.jsp): the same parts as common/antweb-defs.jsp (academyHeader, siteNav, copyright) in
     layouts/secureLayout.jsp, which builds its links with AntwebProps.getSecureDomainApp(). --%><%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@ page import="org.calacademy.antweb.util.*" %>

<% //AntwebUtil.devSleep(4); %>

<tiles:definition
	id="antweb.default"
	page="/layouts/secureLayout.jsp"
	scope="request">
		<tiles:put name="academyHeader" value="/common/academyHeader.jsp" />
		<tiles:put name="siteNav" value="/common/siteNav.jsp" />
		<tiles:put name="copyright" value="/common/copyright.jsp" />	
</tiles:definition>
