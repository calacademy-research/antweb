<%-- Message page in the admin layout: global forward adminMessage (struts-config.xml), used for example by
     GeolocaleMgrAction for the georegion and bioregion tables. Shows the request attribute "message"
     (common/message-body.jsp) under the title in attribute "header". --%>
<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 
<%
        session.removeAttribute("taxon");
%>

<%@include file="/common/antweb_admin-defs.jsp" %>

<% 
    String header = (String) request.getAttribute("header"); 
    if (header == null) header = "message...";
%>    
    
<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%= header %>" />
	<tiles:put name="body-content" value="/common/message-body.jsp" />	
</tiles:insert>

