<%-- Public Imaging progress page (imagingProgress.do, ImagingProgressAction; Brian Fisher, 2026-11-08). Tiles wrapper of
     util/imagingProgress-body.jsp. Docs: docs/numbers.md rows 124 to 128. Tests: tests/e2e/test_imaging_progress.py. --%>
<%@page contentType="text/html; charset=UTF-8" %>
<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%
  String titleString = "Imaging progress - AntWeb";
  String metaString = "<meta name='keywords' content='AntWeb,ants,ant,formicidae,images,imaging progress'/>";
  metaString += "<meta name='description' content='How many of the known living ant species have a photo on AntWeb, by bioregion, country and genus.'/>";
%>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%=titleString%>" />
	<tiles:put name="meta" value="<%= metaString %>" />
	<tiles:put name="body-content" value="/util/imagingProgress-body.jsp" />
</tiles:insert>
