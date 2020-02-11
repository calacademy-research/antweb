<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@ page import = "org.calacademy.antweb.Project" %>

<bean:define id="project" toScope="session" value=""/>
<%
 session.removeAttribute("taxon");
 session.setAttribute("project", Project.ALLANTWEBANTS);
%>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Search Results - AntWeb" />
	<tiles:put name="body-content" value="/search/basicResults2-body.jsp" />	
</tiles:insert>
