<%-- Specimen List Lookup page (specimenListLookup.do, SpecimenListLookupAction), curators (curatorCheck.jsp).
     Tiles wrapper of curate/specimen/specimenListLookup-body.jsp. Docs: docs/guides/curate-page.md (Read-only
     tools). Tests: tests/e2e/test_curator.py, test_curate_page_clarity.py. --%>
<%@ page language="java" %>
<%@ page errorPage="/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@include file="/curate/curatorCheck.jsp" %>
<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
    <tiles:put name="title" value="Specimen List Lookup" />
    <tiles:put name="body-content" value="/curate/specimen/specimenListLookup-body.jsp" />
</tiles:insert>
