<%-- Tiles page of museum.do?museumCode= (or code=, name=) (OverviewAction, form overviewForm): a museum's page,
     titled "<title> Ants -  AntWeb" ("Museum" when it has no title), body overview-body.jsp. A direct request of the
     JSP prints "Invalid URL.  Direct JSP calls unsupported." (HttpUtil.isStaticCallCheck). Docs:
     docs/flows/places.md, docs/numbers.md rows 20 to 37. Tests: tests/e2e/test_place_pages.py. --%><%@ page language="java" %>
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

    Overview overview = OverviewMgr.getOverview(request);

    String overviewTitle = overview.getTitle();
	String titleString = overview.getTitle() + " Ants -  AntWeb";
    if (overviewTitle == null) titleString = "Museum";

%>
<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%= titleString %>" />
	<tiles:put name="body-content" value="/overview-body.jsp" />	
</tiles:insert>
