<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@ page import = "org.calacademy.antweb.Group" %>

<%@include file="/curate/curatorCheck.jsp" %>

<bean:define id="mode" toScope="request" value="preview"/>

<%@include file="/common/antweb-defs.jsp" %>

<%
   Project project = ProjectMgr.getProject(request);
   String includeFile = project.getPreviewPage(); 
   org.calacademy.antweb.util.AntwebUtil.log("warn", "projectPreview.jsp includeFile:" + includeFile);
%>
<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
    <tiles:put name="title">
         <%= project.getTitle() %> Ants - AntWeb
    </tiles:put>
    <!-- tiles :put name="body-content" beanName="projectInfo" beanProperty="previewPage"/ -->
</tiles:insert>