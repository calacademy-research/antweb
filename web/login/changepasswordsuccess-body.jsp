<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.Group" %>


<%@include file="/curate/curatorCheck.jsp" %>


<div class=left>
<h1>Success!</h1>
<hr></hr>
Your password has been successfully changed.

<p>To make edits to your project, <a href=admin.jsp>click here</a>.

</div>
<div class=right>
</div> 
