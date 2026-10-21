<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@include file="/curate/adminCheck.jsp" %>

<bean:define id="project" value="" toScope="session"/> 

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
    <tiles:put name="title" value="Add an uploader - AntWeb" />
    <tiles:put name="body-content" value="/curate/addUploader-body.jsp" />    
</tiles:insert>
