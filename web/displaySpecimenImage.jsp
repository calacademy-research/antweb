<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<html:html locale="true">
<head>
<title>Specimen Image - AntWeb</title>
<html:base/>
</head>
<body bgcolor="white">

<logic:present parameter="shot">
  <bean:parameter id="shot" name="shot" />
</logic:present>

<logic:notPresent parameter="shot">
  <bean:define id="shot" value="head" />
</logic:notPresent>

<%
  String shortHand="";
  String shot = request.getParameter("shot");
  if ("head".equals(shot)) {
    shortHand="h";
  } else if ("profile".equals(shot)) {
    shortHand="p";
  } else if ("label".equals(shot)) {
    shortHand="l";
  } else if ("dorsal".equals(shot)) {
    shortHand="d";
  } else if ("ventral".equals(shot)) {
    shortHand="v";
  } else {
    shortHand="h";
  }
  pageContext.setAttribute("shortHand",shortHand, PageContext.REQUEST_SCOPE);
%>
   

<logic:notPresent name="org.apache.struts.action.MESSAGE" scope="application">
  <font color="red">
    ERROR:  Application resources not loaded -- check servlet container
    logs for error messages.
  </font>
</logic:notPresent>

<logic:present name="taxon" property="subfamily">
  <h1>Subfamily: <bean:write name="taxon" property="subfamily" /> </h1>
</logic:present>

<logic:present name="taxon" property="genus">
<h1>Genus: <bean:write name="taxon" property="genus" /> </h1>
</logic:present>


<!-- print the rest -->
<jsp:useBean id="taxon" scope="session" class="org.calacademy.antweb.Specimen" />
<jsp:setProperty name="taxon" property="*" />

 <bean:write name="taxon" property="hasImages" />
 <logic:equal value="true" name="taxon" property="hasImages">
   <bean:write name="taxon" property="fullName"/>
   <logic:iterate name="taxon" id="image" collection="<%= taxon.getImages() %>">
     <logic:equal value="<%= shortHand %>" name="image" property="key">
       <img src="<bean:write name="image" property="value.highres" />">
    </logic:equal>
  </logic:iterate>
 </logic:equal>
<jsp:include page="common/copyright.jsp" flush="true"/>
</body>
</html:html>
