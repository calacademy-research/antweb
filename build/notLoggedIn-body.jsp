<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>


<%@ page import="org.calacademy.antweb.Utility" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<div class="admin_left">
<br>
<b>Please <a href="<%= domainApp %>/login.do">log in</a> to access this resource.</b>
</div>
<div class="clear"></div>
<br />
<br />
<br />
<!-- Body of Text Ends -->
