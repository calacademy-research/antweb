<%-- Tiles page of getComparison.do's oneView forward (BrowseAction): one view of the children's images, "Comparison
     Tool - AntWeb", body oneView-body.jsp. Puts the project parameter (or "") in the session attribute project.
     Docs: docs/flows/places.md (Compare tools). Tests: tests/e2e/test_field_guide_compare.py. --%><%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<logic:present parameter="project">
        <bean:parameter id="tempProject" name="project" />
        <bean:define id="project" name="tempProject" toScope="session" />
</logic:present>
<logic:notPresent parameter="project">
  <bean:define id="project" value="" toScope="session"/>
</logic:notPresent>

<%@include file="common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
        <tiles:put name="title" value="Comparison Tool - AntWeb" />
        <tiles:put name="body-content" value="/oneView-body.jsp" />
</tiles:insert>
