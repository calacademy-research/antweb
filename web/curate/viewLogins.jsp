<%@ page errorPage = "error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ page import="org.calacademy.antweb.Group" %>


<%@include file="/curate/adminCheck.jsp" %>


<bean:define id="project" value="" toScope="session"/> 
 
<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
    <tiles:put name="title" value="View Users - AntWeb" />
    <tiles:put name="body-content" value="/curate/viewLogins-body.jsp" />    
</tiles:insert>
 