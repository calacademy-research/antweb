<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%
   String pageRank = request.getParameter("rank");
   Overview overview = OverviewMgr.getOverview(request);
   TaxaPage taxaPage = (TaxaPage) request.getAttribute("taxaPage");
   String titleString = "";
   if (taxaPage != null) {
	   if (overview != null) titleString = overview.getTitle() + " Ants";
	   titleString += " (" + Rank.getPluralRank(pageRank) + ") - ";
   } else {
       AntwebUtil.log("taxonomicPage.jsp taxaPage is null for request:" + HttpUtil.getTarget(request));
       return;   
   }
   titleString += "Antweb";   
   //if (AntwebProps.isDevMode()) AntwebUtil.log(titleString);
%>

<bean:define id="showNav" value="taxonomic" toScope="request" />

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%= titleString %>" />
<% 		
   boolean withImages = ("true".equals(request.getParameter("images")));
   if (withImages) { %>
	<tiles:put name="body-content" value="/taxonomicPageImages-body.jsp" />	
<% } else { 
   //if (AntwebProps.isDevMode()) AntwebUtil.log("taxonomicPage.jsp");
%>
	<tiles:put name="body-content" value="/taxonomicPage-body.jsp" />	
<% } %>
</tiles:insert>