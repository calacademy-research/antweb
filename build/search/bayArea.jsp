<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="californiaants" toScope="session"/>

<%@include file="/common/antweb-defs.jsp" %>


<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Bay Area Ants Survey - AntWeb" />
	<tiles:put name="body-content" value="/search/bayArea-body.jsp" />	
</tiles:insert>