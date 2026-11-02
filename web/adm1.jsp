<%-- Tiles page of adm1.do (OverviewAction, form overviewForm): an adm1's page, titled "Adm1: <name>, <country> -
     Antweb", body overview-body.jsp. Prints "Overview not found" without a place. A direct request of the JSP prints
     "Invalid URL.  Direct JSP calls unsupported." (HttpUtil.isStaticCallCheck). Docs: docs/flows/places.md,
     docs/numbers.md rows 20 to 37. Tests: tests/e2e/test_place_pages.py. --%><%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>

<%@ page import="org.calacademy.antweb.Overview" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%
    session.removeAttribute("taxon");
        
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;

    Overview overview = OverviewMgr.getOverview(request);
    
    if (overview == null) {
      out.println("Overview not found");
      return;
    }

    String titleStr = "Adm1: " + overview.getTitle() + " - Antweb";
%>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%= titleStr %>" />
	<tiles:put name="body-content" value="/overview-body.jsp" />	
</tiles:insert>

