<%-- Unused as of 2026-11-02: an old Your password has been successfully changed text. Nothing includes it
     (login/changepasswordsuccess.jsp names /changepasswordsuccess-body.jsp at the web root), and Caddy answers a
     direct request with 404 (not on the JSP allow list in docker/caddy/Caddyfile). --%><%@ page errorPage = "error.jsp" %>
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
