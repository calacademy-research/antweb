<%-- Tiles page of bioregion.do?name= (OverviewAction, form overviewForm): a bioregion's page, titled "Browsing
     <title> Ants - AntWeb", body overview-body.jsp. A direct request of the JSP prints "Invalid URL.  Direct JSP
     calls unsupported." (HttpUtil.isStaticCallCheck). Docs: docs/flows/places.md, docs/numbers.md rows 20 to 37.
     Tests: tests/e2e/test_place_pages.py. --%><%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>

<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>


<%@ page import = "org.calacademy.antweb.util.*" %>

<%
  session.removeAttribute("taxon");
      
  if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;

// if (AntwebProps.isDevMode()) AntwebUtil.log("XXX overview:" + OverviewMgr.getOverview(request));

  Overview overview = OverviewMgr.getOverview(request);

  String titleString = "Browsing " + overview.getTitle() + " Ants - AntWeb";  
%>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%= titleString %>" />
	<tiles:put name="body-content" value="/overview-body.jsp" />	
</tiles:insert>

