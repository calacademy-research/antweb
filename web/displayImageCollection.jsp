<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<html:html locale="true">
<head>
<title>pft</title>
<html:base/>
</head>
<body bgcolor="white">

<logic:notPresent name="org.apache.struts.action.MESSAGE" scope="application">
  <font color="red">
    ERROR:  Application resources not loaded -- check servlet container
    logs for error messages.
  </font>
</logic:notPresent>

<!-- displayImageCollection.jsp -->

<logic:iterate id="image" name="imageCollection" type="org.calacademy.antweb.SpecimenImage">
  <img src="<bean:write name="image" property="medres"/>">
</logic:iterate>
<jsp:include page="common/copyright.jsp" flush="true"/>

</body>
</html:html>
