<%@ page errorPage = "/error.jsp" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.util.AntwebUtil" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 

<%@include file="/common/antwebSecure-defs.jsp" %>

<% // Must be able to edit our own page %>
<!-- % @ include file="/common/loginCheck.jsp" % -->

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
    <tiles:put name="title" value="Edit Login Info - AntWeb" />
    <tiles:put name="body-content" value="/editLogin-body.jsp" />    
</tiles:insert>
