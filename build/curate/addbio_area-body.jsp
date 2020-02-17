<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.AntwebProps" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<%@include file="/curate/adminCheck.jsp" %>


<div class=left>
<h1>Adding a Biogeographic Region</h1>
<hr></hr>

<form action="<%= AntwebProps.getDomainApp() %>/newBioRegion.do" method="POST">
<p><h3>New Biogeographic Region Title:</h3>
<input type=text class=input_450 name="title" value="e.g. the Nearctic">

<p><h3>New Biogeographic Region URL code (no spaces or punctuation):</h3>
<input type=text class=input_450 name="root" value="e.g. nearcticants">

<!-- 

<p><h3>Region Extent:</h3>
<input type=text class=input_450 value="e.g. 10 10 10 10">

<p><h3>Document Name:</h3><br>
<input type=text class=input_450 value="e.g. nearctic.jsp">

<p><h3>Map Image:</h3><br>
<input type=text class=input_450 value="">

<p><h3>Images:</h3>
<b>Image One:</b><br>
<input type=file class=input_450><br>
<b>Image One Links to:</b><br>
<input type=text class=input_450 value=""><br>

<p><b>Image Two:</b><br>
<input type=file class=input_450><br>
<b>Image Two Links to:</b><br>
<input type=text class=input_450 value=""><br>

<p><b>Image Three:</b><br>
<input type=file class=input_450><br>
<b>Image Three Links to:</b><br>
<input type=text class=input_450 value=""><br>

-->

<br><br>
<p align=center><input type=submit class=submit value="Add &#187;">
<a href="<%= domainApp %>/curate.do"><img border=0" src="<%= domainApp %>/image/grey_cancel.gif" width="123" height="36"</a>
</p>

</form> 

</div>
<div class=right>
<br><br>
<div class=green_module><span class=module_header>NOTE:</span></div>

Be sure to create a new project under this area once you've created it.  

</div> 
