<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.Group" %>

<%@include file="/curate/adminCheck.jsp" %>

<jsp:useBean id="results" scope="session" class="java.lang.String" />
<jsp:useBean id="problems" scope="session" class="java.lang.String" />
<jsp:useBean id="fileName" scope="session" class="java.lang.String" />

<% 
	if (problems.length() == 0) {
		String redirectURL = "worldAuthoritySave.do?mode=save";
 		response.sendRedirect(redirectURL);
	} else {
		out.println(problems);  		    
%>

<a href="worldAuthoritySave.do?mode=save">Save Anyway</a>  <a href="worldAuthoritySave.do?mode=rollback&fileName=<%= fileName %>">Cancel</a>

<%
	}
%>

<!-- Body of Text Ends -->
