<%@ page errorPage = "/error.jsp" %>

<%@ page import="org.calacademy.antweb.util.*" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@page pageEncoding="UTF-8"%>

<bean:define id="project" value="" toScope="session"/>

<%
    session.removeAttribute("taxon");
    
    AntwebProps.resetSessionProperties(session);

    //A.log("index.jsp target:" + HttpUtil.getTarget(request));
%>

<%@include file="/common/antweb-defs.jsp" %>
<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="AntWeb" />
	<tiles:put name="meta" value="<meta name='keywords' content='ants,ant,formicidae'/> <meta name='description' content='AntWeb illustrates the incredible diversity of ants (Family Formicidae) by providing both information and over 112,000 high quality color ant images, of over 26,000 specimens, representing over 11,877 species.'/>"/>
	<tiles:put name="body-content" value="/index-body.jsp" />	
</tiles:insert>
