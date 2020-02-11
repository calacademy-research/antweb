<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.Utility" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<div class="admin_left">
<h1>Sorry...</h1>
You don't have permission for this page. Try <a href="<%= domainApp %>/login.do">Logging In</a> to a different account.  
</div>
<div class="admin_right">
</div>
