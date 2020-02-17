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


<!-- jsp:useBean id="antwebGroups" scope="session" class="java.util.ArrayList<Group>" / -->
<%
  ArrayList<Group> antwebGroups = (ArrayList) session.getAttribute("antwebGroups");
%>

<div class=left>
<h1>Page Tracker</h1>

<%@ include file="/util/pageTracker.jsp" %>

</div > 
