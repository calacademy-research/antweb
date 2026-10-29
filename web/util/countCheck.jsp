<%-- Check the counts page (countCheck.do, CountCheckAction; the action allows admins only, this page checks for
     a curator): compares the counts on list pages with the database for a sample of places. Tiles wrapper of
     util/countCheck-body.jsp. Docs: docs/numbers.md. Tests: tests/e2e/test_count_check.py. --%>
<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/>

<%@include file="/curate/curatorCheck.jsp" %>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
        <tiles:put name="title" value="Check the counts" />
        <tiles:put name="body-content" value="/util/countCheck-body.jsp" />
</tiles:insert>
