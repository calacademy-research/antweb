<%@ page language="java" %>
<%@ page import = "org.calacademy.antweb.Formatter" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<jsp:useBean id="orphanedImages" scope="session" class="java.util.ArrayList" />

<!-- Body of Text Begins -->
<td colspan="3">
<table border="0" cellpadding="1" cellspacing="7" width="800">
<tr>
<td>
Found  <%= orphanedImages.size() %> orphaned images
<p>
<ul>

<logic:iterate id="row" name="orphanedImages">
<li>
<a href="images/<bean:write name="row"/>">
<bean:write name="row"/>
</a>

</logic:iterate>

</ul>
</td>
</tr>
</table>
<!-- Body of Text Ends -->
