<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/>

<%@include file="common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Thanks for Donating to Antweb" />
	<tiles:put name="meta" value="<meta name='keywords' content='ants,ant,hymenoptera'/>" />
	<tiles:put name="body-content" value="/thanks-body.jsp" />	
</tiles:insert>
