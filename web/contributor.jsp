<%-- Unused as of 2026-11-02: a tiles page for one contributing group (request attribute group), body
     contributor-body.jsp. No action forwards here (group.do uses group.jsp), and Caddy answers a direct request with
     404 (not on the JSP allow list in docker/caddy/Caddyfile). --%><%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>

<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;

    Group group = (Group) request.getAttribute("group");

	String titleString = "Ant Group" + group.toString();

%>
<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%= titleString %>" />
	<tiles:put name="body-content" value="/contributor-body.jsp" />	
</tiles:insert>
