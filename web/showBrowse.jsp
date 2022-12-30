<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<bean:define id="showNav" value="browse" toScope="request" />

<jsp:useBean id="taxon" scope="session" class="org.calacademy.antweb.Taxon" />
<jsp:setProperty name="taxon" property="*" />

<%@include file="/common/antweb-defs.jsp" %>

<%
  Overview overview = OverviewMgr.getOverview(request);
  //AntwebUtil.log("showBrowse project:" + session.getAttribute("project"));

  String titleString, title;
    //AntwebUtil.log("showBrowse.jsp title:" + title + " project:" + project);
  titleString = "Browsing " + taxon.getRank() +  " " + taxon.getPrettyName() + " in " + overview.getTitle() + " - AntWeb";

  String metaString = "<meta name='keywords' content='" + taxon.getPrettyName() + ", AntWeb, ants,ant,formicidae '/>";
  metaString+= "<meta name='description' content='List of " + taxon.getRank() +  " " + taxon.getPrettyName() + " from AntWeb.'/>";
%>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%= titleString %>" />
        <tiles:put name="meta" value="<%= metaString %>" />
	<tiles:put name="body-content" value="/showBrowse-body.jsp" />	
</tiles:insert>
