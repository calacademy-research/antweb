<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@ page import="org.calacademy.antweb.Overview" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<bean:define id="showNav" value="search" toScope="request"/>
<% session.removeAttribute("taxon"); %>

<%
Overview overview = OverviewMgr.getOverview(request);
%>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Collection Search - AntWeb" />
	<tiles:put name="body-content" value="/collection-body.jsp" />	
</tiles:insert>
