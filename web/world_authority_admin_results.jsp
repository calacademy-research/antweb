<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>


<%@include file="/curate/adminCheck.jsp" %>

<bean:define id="showNav" value="search" toScope="request"/>

<%@include file="common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="World Authority Results - AntWeb" />
	<tiles:put name="body-content" value="/world_authority_admin_results-body.jsp" />	
</tiles:insert>