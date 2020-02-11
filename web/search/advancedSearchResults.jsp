<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="showNav" value="search" toScope="request"/>
<% session.removeAttribute("taxon"); 

   if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
%>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Advanced Specimen Search - AntWeb" />
	<tiles:put name="body-content" value="/search/advancedSearchResults-body.jsp" />	
</tiles:insert>
