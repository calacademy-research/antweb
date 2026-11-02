<%-- Tiles page of the global forward message (struts-config.xml; about 290 findForward("message") calls): title from
     the request attribute header (else message...), body common/message-body.jsp. Removes the session attribute
     taxon. Tests: tests/e2e/test_message_log.py. --%><%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 
<%
        session.removeAttribute("taxon");
%>

<%@include file="/common/antweb-defs.jsp" %>

<% 
    String header = (String) request.getAttribute("header"); 
    if (header == null) header = "message...";
%>    
    
<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%= header %>" />
	<tiles:put name="body-content" value="/common/message-body.jsp" />	
</tiles:insert>
