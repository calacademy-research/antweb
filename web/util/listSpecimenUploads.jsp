<%@ page errorPage = "error.jsp" %>
<%@ page import = "org.calacademy.antweb.Group" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/>

<%@include file="/curate/curatorCheck.jsp" %>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
        <tiles:put name="title" value="Specimen Upload Reports" />
        <tiles:put name="body-content" value="/util/listSpecimenUploads-body.jsp" />
</tiles:insert>


