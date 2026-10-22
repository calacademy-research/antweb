<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%-- Log Manager (LogMgrAction checks who may see what: admins, or curators for their own upload lines).
     Until 2026-10-22 this page had no layout at all; it now uses the admin layout and the tool-page look
     (util/logMgr-body.jsp, common/tools.css). --%>
<bean:define id="project" value="" toScope="session"/>

<%@include file="/common/antweb_admin-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
        <tiles:put name="title" value="Log Manager - AntWeb" />
        <tiles:put name="body-content" value="/util/logMgr-body.jsp" />
</tiles:insert>
