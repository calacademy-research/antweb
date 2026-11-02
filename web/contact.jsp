<%-- Tiles page of contact.do: "Contact AntWeb", body contact-body.jsp. Removes the session attribute taxon. Tests:
     tests/e2e/test_actions.py::test_public_page. --%><%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/>
<%
        session.removeAttribute("taxon");
%>


<%@include file="common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Contact AntWeb" />
	<tiles:put name="body-content" value="/contact-body.jsp" />	
</tiles:insert>
