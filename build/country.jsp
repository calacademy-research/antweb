<%@ page language="java" %>
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