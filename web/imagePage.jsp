<%@page contentType="text/html; charset=UTF-8" %>

<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<jsp:useBean id="taxon" scope="session" class="org.calacademy.antweb.Taxon" />
<jsp:setProperty name="taxon" property="*" />

<bean:define id="showNav" value="taxonomic" toScope="request"/>

<%
  java.util.Hashtable desc = taxon.getDescription();
        
  String titleString =  " - AntWeb";
  String rankString = new Formatter().capitalizeFirstLetter(taxon.getRank());
  String prettyName = taxon.getPrettyName();
  if (Utility.notBlank(taxon.getFullName())) {
    if (prettyName == null || "".equals(prettyName)) AntwebUtil.log("imagePage.jsp prettyName not found " + HttpUtil.getRequestInfo(request));
	titleString = rankString + ": " + prettyName + titleString;
  } 
   
  String metaString = "<meta name='keywords' content='" + prettyName + ", AntWeb, ants,ant,formicidae '/>";
  metaString+= "<meta name='description' content='Images of " + taxon.getRank() +  " " + prettyName + " from AntWeb.'/>";
%>


<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%=titleString%>" />
        <tiles:put name="meta" value="<%= metaString %>" />
	<tiles:put name="body-content" value="/imagePage-body.jsp" />	
</tiles:insert>

