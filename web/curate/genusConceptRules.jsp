<%-- Old genus concepts page (genusConceptRules.do, GenusConceptRulesAction; Brian Fisher, 2026-11-03): proposals from
     AntCat's data, approve or reject, and the rules decided (table genus_concept_rule). Administrators only.
     Tiles wrapper of curate/genusConceptRules-body.jsp. Docs: docs/flows/antcat-to-antweb.md section 7 (Old genus concepts). --%>
<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="project" value="" toScope="session"/>

<%@include file="/curate/adminCheck.jsp" %>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
        <tiles:put name="title" value="Old genus concepts" />
        <tiles:put name="body-content" value="/curate/genusConceptRules-body.jsp" />
</tiles:insert>
