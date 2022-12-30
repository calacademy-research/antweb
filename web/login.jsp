<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@ page import = "org.calacademy.antweb.util.*" %>

<bean:define id="project" value="" toScope="session"/> 

<%@include file="/common/antwebSecure-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="AntWeb - Please Log In" />
	<tiles:put name="body-content" value="/login-body.jsp" />	
</tiles:insert>
