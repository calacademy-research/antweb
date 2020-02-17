<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 
<bean:define id="mode" toScope="request" value="preview"/>

<%@ page import = "org.calacademy.antweb.Group" %>

<%@include file="/curate/adminCheck.jsp" %>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Editing Homepage - AntWeb" />
	<tiles:put name="body-content" value="/curate/project/editHome-body.jsp" />	
</tiles:insert>
