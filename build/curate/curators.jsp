<%@ page errorPage = "/error.jsp" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.util.AntwebUtil" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 

<!-- %@include file="/common/antweb_admin-defs.jsp" % -->
<%@include file="/common/antweb_admin-defs.jsp" %>

<% // String domainApp = (new Utility()).getDomainApp(); %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
    <tiles:put name="title" value="Curators - AntWeb" />
    <tiles:put name="body-content" value="/curate/curators-body.jsp" />    
</tiles:insert>
