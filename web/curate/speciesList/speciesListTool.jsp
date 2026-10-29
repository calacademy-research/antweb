<%-- Species List Tool page (speciesListTool.do, SpeciesListToolAction; also forward of speciesListToolSearch.do),
     curators (curatorCheck.jsp). Tiles wrapper of curate/speciesList/speciesListTool-body.jsp. Docs:
     docs/guides/curate-page.md (Species List Tool). Tests: tests/e2e/test_curator_species_lists.py. --%>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<% 
request.getSession().setAttribute("project", null);
%>

<%@include file="/curate/curatorCheck.jsp" %>
<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Species List Tool" />
	<tiles:put name="body-content" value="/curate/speciesList/speciesListTool-body.jsp" />	
</tiles:insert>

