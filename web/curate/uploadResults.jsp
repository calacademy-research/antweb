<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.util.Vector" %>
<%@ page import="org.calacademy.antweb.util.FileUtil" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.upload.AntwebUpload" %>
<%@ page import="org.calacademy.antweb.upload.UploadDetails" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<html:html locale="true">
<head>
<title>Thanks</title>
<html:base/>
<link rel="shortcut icon" href="<%= domainApp %>/image/favicon.ico" />
</head>
<body bgcolor="white">

<logic:notPresent name="org.apache.struts.action.MESSAGE" scope="application">
  <font color="red">
    ERROR:  Application resources not loaded -- check servlet container
    logs for error messages.
  </font>
</logic:notPresent>

<h1>Successful Upload.</h1>
<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>

<p><b>Current Statistics:</b><br>
<%= FileUtil.getContent("web/genInc/statistics.jsp") %>

<% 
// if there is a message log file, link to it
UploadDetails uploadDetails = (UploadDetails) request.getAttribute("uploadDetails");
//Object messageLogFileObj = request.getAttribute("messageLogFile");
String messageLogFile = uploadDetails.getMessageLogFile();
//if (messageLogFileObj != null) {
//  String messageLogFile = (String) messageLogFileObj;
//  Boolean hasMessages = (Boolean) request.getAttribute("hasMessages");

  String hasIssues = "";
  if (uploadDetails.isHasMessages()) hasIssues = "<font color=red> has issues</font>";
%>
  <br><br><h2>Your upload log<%= hasIssues %>: <a href="<%= domainApp %>/web/log/<%= messageLogFile %>"><%= messageLogFile %></a></h2>
<%
   if (uploadDetails.isOfferRunCountCrawlLink()) { %>
      <br><br><h2>Run Count Crawls: <a href="<%= domainApp %>/utilData.do?action=runCountCrawls">Crawls</a></h2>
      * Optional step. These are calculated nightly.
<% }
%>


</body>
</html:html>
