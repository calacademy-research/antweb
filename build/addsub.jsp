<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>

<%@include file="/curate/adminCheck.jsp" %>

<bean:define id="project" value="" toScope="request"/> 

<%@include file="common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Adding a  Subhomepage - AntWeb" />
	<tiles:put name="body-content" value="/addsub-body.jsp" />	
</tiles:insert>
