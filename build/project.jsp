<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>

<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%
    session.removeAttribute("taxon");
        
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;

    // if (AntwebProps.isDevMode()) AntwebUtil.log("project.jsp overview:" + OverviewMgr.getOverview(request));

%>

<%@include file="/common/antweb-defs.jsp" %>

<%
    Overview overview = OverviewMgr.getOverview(request);

	String titleString = overview.getTitle() + " Ants -  AntWeb";
%>
<!-- What was this for? It is messing up the formatting of the menu
  c:set var="mytitle" value="${story.title}"/ -->

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%= titleString %>" />
	<tiles:put name="body-content" value="/overview-body.jsp" />	
</tiles:insert>
