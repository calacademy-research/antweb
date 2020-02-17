<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.Specimen" %>
<%@ page import="java.util.*" %>
                                   
<jsp:useBean id="results" scope="session" class="java.util.ArrayList" />
<jsp:useBean id="field" scope="session" class="java.lang.String" />
<jsp:useBean id="subfamily" scope="session" class="java.lang.String" />
<jsp:useBean id="genus" scope="session" class="java.lang.String" />
<jsp:useBean id="species" scope="session" class="java.lang.String" />
<jsp:useBean id="value" scope="session" class="java.lang.String" />
<jsp:useBean id="project" scope="session" class="java.lang.String" />

<%
	Formatter formatter = new Formatter();
	String prettyField = formatter.capitalizeFirstLetter(field);
	String prettySubfamily = formatter.capitalizeFirstLetter(subfamily);
	String prettyGenus = formatter.capitalizeFirstLetter(genus);
	
%>

<td colspan="3">
<table border="0" cellpadding="1" cellspacing="7">
<tr>
<td bgcolor="#000000">
<table border ="0" cellpadding="4" cellspacing="1">
<tr>
<td bgcolor="#FFFFFF" colspan="7" valign="top"><font face="tahoma,arial,helvetica" size="2">Specimens of <%= prettySubfamily %> <%= prettyGenus %> <%= species %> where <%= prettyField %> is <%= value %> </td></tr>
                                                                               
<!-- Body of Text Begins -->
<% if ((results == null) || (results.isEmpty())) { %>

<tr><td colspan="7" bgcolor="#FFFFFF">Sorry, nothing matches your request</td></tr>

<% } else { %>

<tr>
<td bgcolor="#88B3FB"><font face="arial,helvetica" size="1"><b>SPECIMEN</b></td>
<td bgcolor="#88B3FB"><font face="arial,helvetica" size="1"><b>IMAGES</b></td>
<td bgcolor="#88B3FB"><font face="arial,helvetica" size="1"><b>SPECIES</b></td>
<td bgcolor="#88B3FB"><font face="arial,helvetica" size="1"><b>LOCALITY</b></td>
<td bgcolor="#88B3FB"><font face="arial,helvetica" size="1"><b>TYPE</b></td>
<td bgcolor="#88B3FB"><font face="arial,helvetica" size="1"><b>TYPE ORIGINAL COMBINATION</b></td>
</tr>

<logic:iterate id="row" name="results">
<bean:define id="taxonGenus" name="row" property="genus"/>
<bean:define id="taxonSpecies" name="row" property="species"/>
<bean:define id="prettycode" name="row" property="code"/>
<bean:define id="prettyname" name="row" property="fullName"/>

<%
	String prettyTaxonGenus = formatter.capitalizeFirstLetter((String) taxonGenus);
%>

<tr>
<td bgcolor="#FFFFFF"><font face="arial,helvetica" size="1">
<logic:notEmpty name="row" property="code">
<a href="specimen.do?name=<bean:write name="row" property="code"/>">
<b><%= ((String) prettycode).toUpperCase() %></b>
</a>
</logic:notEmpty>
</td>

<td bgcolor="#FFFFFF"><font face="arial,helvetica" size="2">
<logic:present name="row" property="images">
	<logic:notEmpty name="row" property="images">
		Yes
	</logic:notEmpty>
	<logic:empty name="row" property="images">
		No
	</logic:empty>
</logic:present>
</td>

<td bgcolor="#FFFFFF"><font face="arial,helvetica" size="2">

<%
	String taxonParams = "description.do?rank=species";
	if (((String) taxonGenus).length() > 0) {
		taxonParams += "&genus=" + taxonGenus;
	}
	if (((String) taxonSpecies).length() > 0) {
		taxonParams += "&name=" + taxonSpecies;
	}
	
	if (project.length() > 0) {
		taxonParams += "&project=" + project;
	}
%> 

<a href="<%=taxonParams %>">
<logic:notEmpty name="row" property="genus">
<%= prettyTaxonGenus %>
</logic:notEmpty>
<logic:notEmpty name="row" property="species">
<bean:write name="row" property="species"/>
</logic:notEmpty>
</a>
</td>
<td bgcolor="#FFFFFF"><font face="arial,helvetica" size="2">
  <logic:present name="row" property="country">
    <logic:notEqual name="row" property="country" value="">
	        <bean:write name="row" property="country"/> :
	      </logic:notEqual>
	    </logic:present>
	    <logic:present name="row" property="adm1">
	      <logic:notEqual name="row" property="adm1" value="">
	        <bean:write name="row" property="adm1"/> :
	       </logic:notEqual>
	    </logic:present>
	    <bean:write name="row" property="localityName"/>
</td>
<td bgcolor="#FFFFFF"><font face="arial,helvetica" size="2">
<logic:notEmpty name="row" property="type">
<bean:write name="row" property="type"/>
</logic:notEmpty>
</td>
<td bgcolor="#FFFFFF"><font face="arial,helvetica" size="2">
<!-- nothing here now -->
</td>
</tr>
</logic:iterate>
 
<%  }   // if has results %>   

</table>
</td>
</tr>
</table>
<!-- Body of Text Ends -->

