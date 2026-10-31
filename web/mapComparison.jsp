<%-- Tiles page of mapComparison.do (BrowseAction with getChildMaps, form browseForm in session scope; for logged-in
     visitors): one small map per child taxon, "Map Comparison - AntWeb", body mapComparison-body.jsp. Sets the
     request bean project from the project parameter ("" without one). Also on the list of JSPs Caddy lets through
     when requested directly (docker/caddy/Caddyfile). Docs: docs/flows/places.md (Compare tools). Tests:
     tests/e2e/test_field_guide_compare.py. --%><%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<logic:present parameter="project">
        <bean:parameter id="tempProject" name="project" />
        <bean:define id="project" name="tempProject" toScope="request" />
</logic:present>
<logic:notPresent parameter="project">
  <bean:define id="project" value="" toScope="request"/>
</logic:notPresent>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
        <tiles:put name="title" value="Map Comparison - AntWeb" />
        <tiles:put name="body-content" value="/mapComparison-body.jsp" />
</tiles:insert>
