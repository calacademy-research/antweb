<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<logic:present parameter="project">
  <logic:notEqual parameter="project" value=""> 
	<bean:parameter id="tempProject" name="project" />
	<bean:define id="project" name="tempProject" toScope="session" />
  </logic:notEqual>
</logic:present>

<jsp:useBean id="specimen" scope="request" class="org.calacademy.antweb.Specimen" />
<jsp:setProperty name="specimen" property="*" />

<%@include file="common/antweb-defs.jsp" %>

<%
   java.util.Hashtable desc = specimen.getDescription();
   org.calacademy.antweb.Formatter formatter = new Formatter();

   String titleString = "";
   String metaString = "";
   try {
     titleString = specimen.getTitleString(); // ogTitle;
     metaString = specimen.getMetaString();
   } catch (NullPointerException e) {
	 // We should be checking for null.  First we will get some diagnostics so we may replicate/test/fix.
	 AntwebUtil.log("Specimen.jsp WSS. target:" + HttpUtil.getTarget(request) + " titleString:" + titleString + " specimen:" + specimen + " e:" + e);
   }
%>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%= titleString %>" />
        <tiles:put name="meta" value="<%= metaString %>" /> 
	<tiles:put name="body-content" value="/specimen-body.jsp" />	
</tiles:insert>
