<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<logic:present parameter="project">
  <logic:notEqual parameter="project" value=""> 
	<bean:parameter id="tempProject" name="project" />
	<bean:define id="project" name="tempProject" toScope="session" />
  </logic:notEqual>
</logic:present>

<% 
  if (request.getParameter("project") == null) {
	session.removeAttribute("project");  
  }
%>

<bean:define id="showNav" value="search" toScope="request"/>

<%@include file="common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	
	<tiles:put name="title" value="Find Collections in a Locality - AntWeb" />	
	<tiles:put name="body-content" value="/localityToCollecion-body.jsp" />	
</tiles:insert>
