<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.util.*" %>

<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<% String domainApp = (new Utility()).getDomainApp(); %>


<%@include file="/curate/adminCheck.jsp" %>

<%
  ArrayList<Group> antwebGroups = (ArrayList) session.getAttribute("antwebGroups");
%>

<div class=left>
<h1>Page Tracker</h1>

<br>Request Details:
<%
java.util.Collection<Tracker> trackers = PageTracker.getTrackers();
Tracker thisTracker = PageTracker.getTracker(request);
if (thisTracker != null) { %>
<br>&nbsp;&nbsp;Target:<%= thisTracker.getTarget() %>
<br>&nbsp;&nbsp;Response time:<%= thisTracker.getSinceStartTime() %>
<% } else { %>
<br>&nbsp;&nbsp;Tracker not found
<% }%>

<br><br>Server Details:
<br>&nbsp;&nbsp;Server Busy Connection Count:<%= DBUtil.getServerBusyConnectionCount() %>

<br>&nbsp;&nbsp;Request Count:<%= PageTracker.getRequestCount() %>
<br>&nbsp;&nbsp;Target list size:<%= trackers.size() %>

<br>&nbsp;&nbsp;Target list:
<%
    String isThisPage = "";
    for (Tracker tracker : trackers) {
      if (tracker.getKey().equals(request.getAttribute("trackerKey"))) isThisPage = "isThisPage: *";
%>
      <br>&nbsp;&nbsp;&nbsp;&nbsp;<%= tracker.getTarget() %> code:<%= tracker.getCode() %> time:<%= tracker.getSinceStartTime() %> <%= isThisPage %>
<%
      isThisPage = "";
    }
     %>

