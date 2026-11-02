<%-- Tiles page of country.do and island.do (and adm1.do's island forward) (OverviewAction, form overviewForm): a
     country's or island's page, titled "Country <name> - Antweb" or "Island <name> - Antweb", body
     overview-body.jsp. A direct request of the JSP prints "Invalid URL.  Direct JSP calls unsupported."
     (HttpUtil.isStaticCallCheck). Docs: docs/flows/places.md, docs/numbers.md rows 20 to 37. Tests:
     tests/e2e/test_place_pages.py. --%><%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@ page import="org.calacademy.antweb.Overview" %>
<%@ page import="org.calacademy.antweb.geolocale.Geolocale" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%
    session.removeAttribute("taxon");
                
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;

  Overview overview = OverviewMgr.getOverview(request);

  //A.log("country.jsp overview:" + overview);

  String titleStr = overview.getHeading() + " " + overview.getTitle() + " - Antweb";

  //A.log("country.jsp " + heading + OverviewMgr.getOverview(request));
%>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%= titleStr %>"/>
	<tiles:put name="body-content" value="/overview-body.jsp" />	
</tiles:insert>
