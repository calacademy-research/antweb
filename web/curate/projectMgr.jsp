<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import = "org.calacademy.antweb.Group" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" toScope="session" value=""/>

<%@include file="/curate/adminCheck.jsp" %>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
    <tiles:put name="title" value="Project Manager - AntWeb" />
    <tiles:put name="body-content" value="/curate/projectMgr-body.jsp" />    
</tiles:insert>
