<%-- Tiles page of curation.do?taxonName=&geolocaleId= (CuratorAction): "Viewing Contribution Info - AntWeb", body
     curation-body.jsp. Linked from the lists' rows (taxonReportBody.jsp). Tests: tests/e2e/test_request_handling.py. --%><%@ page errorPage = "/error.jsp" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.util.AntwebUtil" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/> 

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
    <tiles:put name="title" value="Viewing Contribution Info - AntWeb" />
    <tiles:put name="body-content" value="/curation-body.jsp" />    
</tiles:insert>
