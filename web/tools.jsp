<%-- Tiles page of tools.do: "AntWeb Tools", body tools-body.jsp. Removes the session attribute taxon. Tests:
     tests/e2e/test_actions.py::test_public_page. --%><%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 
<%
        session.removeAttribute("taxon");
%>

<%@include file="common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="AntWeb Tools" />
	<tiles:put name="body-content" value="/tools-body.jsp" />	
</tiles:insert>
