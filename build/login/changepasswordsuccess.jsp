<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>



<%@include file="/curate/curatorCheck.jsp" %>


<bean:define id="project" value="" toScope="session"/> 

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Success!" />
	<tiles:put name="body-content" value="/changepasswordsuccess-body.jsp" />	
</tiles:insert>
