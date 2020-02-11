<%@ page import = "org.calacademy.antweb.Formatter" %>
<%@ page import = "org.calacademy.antweb.util.*" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<jsp:useBean id="searchResults" scope="session" class="org.calacademy.antweb.search.BayAreaSearchResults" />
<jsp:setProperty name="searchResults" property="*" />

<logic:present parameter="project">
  <bean:parameter id="project" name="project"/>
</logic:present>
<logic:notPresent parameter="project">
  <bean:define id="project" value=""/>
</logic:notPresent>


<script src="<%= AntwebProps.getDomainApp() %>/openAndFocus.js" type="text/javascript"></script>
<script type="text/javascript">
<!--

function submitAndFocus(theForm) {
  theForm.target="compare";
  openAndFocus("","compare");
  theForm.submit();
}
// -->
</script>

<script type="text/javascript">
<!--
 
function selectAll(thisForm) {

  var count = thisForm.chosen.length;
  var checkedVal = thisForm.selectall.checked;
  for (var loop = 0; loop < count; loop++) {
    thisForm.chosen[loop].checked = checkedVal;
  }

}

// -->
</script>

<%
     String domainApp = (new org.calacademy.antweb.Utility()).getDomainApp();	
%>

<logic:present parameter="mode">
  <bean:parameter id="mode" name="mode"/>
</logic:present>
<logic:notPresent parameter="mode">
  <bean:define id="mode" value=""/>
  <% // AntwebUtil.log("bayAreaSearchResults-body.jsp mode:" + mode); %>
</logic:notPresent>

<!-- Body of Text Begins -->
<td colspan="3">
<table border="0" cellpadding="1" cellspacing="7" width="550">
<tr>
<td bgcolor="#000000">
<logic:equal name="mode" value="map">
	<form action="<%= domainApp %>/mapResults.do" name="taxaFromSearchForm" method="POST">
	<input type="hidden" name="resultRank" value="bayArea"/>
</logic:equal>
<logic:equal name="mode" value="compare">
	<form action="<%= domainApp %>/compareResults.do" name="taxaFromSearchForm" method="POST">
	<input type="hidden" name="resultRank" value="bayArea"/>
</logic:equal>


<table border ="0" cellpadding="4" cellspacing="1" width="550">
<tr>
<td bgcolor="#FFFFFF" colspan="7" valign="top"><font face="tahoma,arial,helvetica" size="2">
<table border="0" cellpadding="4" cellspacing="1" width="800">
<tr>
<td>
	<font face="tahoma,arial,helvetica" size="2"><b>Bay Area Search</b>
</td>
<td align="right">
	<logic:equal name="mode" value="">
		<a href="<%= domainApp %>/prepareCompareResults.do?resultRank=bayArea"><img src="<%= domainApp %>/image/compare.gif" border="0" align="right"></a>
	</logic:equal>
</td>
</tr>
<tr>
<td>
<font face="arial,helvetica" size="1">SEARCH RESULTS  - <%= searchResults.getResults().size() %> records</font>
</td>
<td>
	<logic:equal name="mode" value="map">
		<input type="image" src="<%= domainApp %>/image/map.gif" border="0" align="right"/>
	</logic:equal>
	<logic:equal name="mode" value="compare"> 
		<input type="image" src="<%= domainApp %>/image/compare.gif" border="0" align="right"/>
	</logic:equal>
	<logic:equal name="mode" value="">
		<a href="<%= domainApp %>/prepareMapResults.do?resultRank=bayArea"><img src="<%= domainApp %>/image/map.gif" border="0" align="right"></a></font>
	</logic:equal>
</td>
</tr>
</table>  <!-- ending results overview table -->


</td>
</tr>	

<% if (searchResults.getResults().size() < 1) { %>
                                                                                
<tr><td colspan="7" bgcolor="#FFFFFF">Sorry, nothing matches your request</td></tr>
                                                                                
<% } else if (searchResults.getResults().size() > 0) { %>
                                                                                
<tr>
<td bgcolor="#88B3FB"><font face="arial,helvetica" size="1"><b>SPECIES</b></td>
<td bgcolor="#88B3FB"><font face="arial,helvetica" size="1"><b>COUNTY</b></td>

<logic:equal name="mode" value="map">
<td bgcolor="#88B3FB"><font face="arial,helvetica" size="1"><b>SELECT ALL</b>
<input type="checkbox" name="selectall" onClick="selectAll(document.taxaFromSearchForm);">
</td>
</logic:equal>
<logic:equal name="mode" value="compare">
<td bgcolor="#88B3FB"><font face="arial,helvetica" size="1"><b>SELECT ALL</b>
<input type="checkbox" name="selectall" onClick="selectAll(document.taxaFromSearchForm);">
</td>
</logic:equal>

</tr>

<logic:iterate id="row" name="searchResults" property="results" indexId="index">                                                                                

  <bean:define id="prettyAdm2" type="java.lang.String" value="" />
  <logic:notEmpty name="row" property="adm2">
    <bean:define id="prettyAdm2"  type="java.lang.String" name="row" property="adm2"/>
    <% // AntwebUtil.log("bayAreaSearchResults-body.jsp prettyAdm2:" + prettyAdm2); %>
  </logic:notEmpty>

  <bean:define id="prettyname" value="" type="java.lang.String" />
  <logic:notEmpty name="row" property="name">
    <bean:define id="prettyname"  type="java.lang.String" name="row" property="name"/>
  </logic:notEmpty>

  <tr>
  <td bgcolor="#FFFFFF"><font face="arial,helvetica" size="2">
  <a href="<%= domainApp %>/description.do?<bean:write name="row" property="pageParams"/>">
  <%= new Formatter().capitalizeFirstLetter((String) prettyname) %>
  </a>

  <logic:notEmpty name="row" property="synonym">
  (valid name of 
    <bean:define id="theSyn" name="row" property="synonym"/>
  <% AntwebUtil.log("bayAreaSearchResults-body.jsp synonym:" + theSyn); %>
    <bean:define id="theName" name="theSyn" property="name"/>
  <% AntwebUtil.log("bayAreaSearchResults-body.jsp theName:" + theName); %>
    <%= new Formatter().capitalizeFirstLetter((String) theName) %>)
  </logic:notEmpty>

  <input type="hidden" name="taxa" value="<%= new Formatter().capitalizeFirstLetter((String) prettyname) %>">
  </td>
  <td bgcolor="#FFFFFF"><font face="arial,helvetica" size="1"><b><%= new Formatter().capitalizeFirstLetter((String) prettyAdm2) %></b></td>
  <logic:equal name="mode" value="map">
	<td bgcolor="#FFFFFF"><font face="arial,helvetica" size="2">
		<input type="checkbox" name="chosen" value="<bean:write name="index"/>"/>
	</td>
  </logic:equal>
  <logic:equal name="mode" value="compare">
	<td bgcolor="#FFFFFF"><font face="arial,helvetica" size="2">
			<input type="checkbox" name="chosen" value="<bean:write name="index"/>"/>
	</td>
  </logic:equal>                                                                                
  </tr>                                            
</logic:iterate>
<% } %>

<!--
<tr><td colspan="7" bgcolor="#FFFFFF">
<logic:present parameter="project">
<input type="hidden" name="project" value="<bean:write name="project"/>"/>
</logic:present>
<center><input type="image" src="<%= domainApp %>/image/compare.gif" border="0" onClick="submitAndFocus(this.form); return false;"></center>

</td>
</tr>
-->
</form>
</table>
</td>
</tr>
</table>
<!-- Body of Text Ends -->