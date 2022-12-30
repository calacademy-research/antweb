<%@page contentType="text/html; charset=UTF-8" %>

<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%
//   java.util.Hashtable desc = taxon.getDescription();

  String titleString =  "Endemic - AntWeb";

  //if (AntwebProps.isDevMode()) AntwebUtil.log("endemic.jsp rank:" + taxon.getRank()); 
  
   
  String metaString = "<meta name='keywords' content='Endemics, AntWeb, ants,ant,formicidae '/>";
  metaString+= "<meta name='description' content='Endemics from AntWeb.'/>";
%>


<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%=titleString%>" />
        <tiles:put name="meta" value="<%= metaString %>" />
	<tiles:put name="body-content" value="/endemic-body.jsp" />	
</tiles:insert>

  

