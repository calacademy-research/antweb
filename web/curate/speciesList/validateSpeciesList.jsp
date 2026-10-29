<%-- "Check names against AntCat" page (validateSpeciesList.do, ValidateSpeciesListAction), curators
     (curatorCheck.jsp). Tiles wrapper of curate/speciesList/validateSpeciesList-body.jsp. Docs:
     docs/guides/curate-page.md (Read-only tools). Tests: tests/e2e/test_curator.py. --%>
<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@include file="/curate/curatorCheck.jsp" %>
<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Validate Species List" />
	<tiles:put name="body-content" value="/curate/speciesList/validateSpeciesList-body.jsp" />	
</tiles:insert>
