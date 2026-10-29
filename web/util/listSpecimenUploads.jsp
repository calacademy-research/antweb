<%-- Specimen Upload Reports page (listSpecimenUploads.do, also /listUploads.do; ListUploadsAction): one row per
     specimen upload (table upload) with its report and kept file, for one group or all groups; curators and
     admins (curatorCheck.jsp). Tiles wrapper of util/listSpecimenUploads-body.jsp.
     Docs: docs/flows/specimen-upload.md (section 3, step 11). Tests: tests/e2e/test_upload_list_views.py, test_upload_report_links.py. --%>
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


