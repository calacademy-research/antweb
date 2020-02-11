<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 

<%@include file="common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="AntWeb - Password Retrieval" />
	<tiles:put name="body-content" value="/forgot_password-body.jsp" />	
</tiles:insert>
