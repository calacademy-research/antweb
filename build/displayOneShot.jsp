<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<html:html locale="true">
<head>
<title>Display Images - AntWeb</title>
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
  String shortHand="fungo";
  String shot = request.getParameter("shot");
  if ("head".equals(shot)) {
    shortHand="h";
  } else if ("profile".equals(shot)) {
    shortHand="p";
  } else if ("dorsal".equals(shot)) {
    shortHand="d";
  } else if ("ventral".equals(shot)) {
    shortHand="v";
  } else if ("label".equals(shot)) {
    shortHand="l";
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


<table>
<!-- print the header -->
<tr>
<th><bean:write name="taxon" property="nextRank" /></th>
<th><bean:write name="shot" /></th>
</tr>

<!-- print the rest -->
<jsp:useBean id="taxon" scope="session" class="org.calacademy.antweb.Taxon" />
<jsp:setProperty name="taxon" property="*" />

<logic:iterate id="child" collection="<%= taxon.getChildren() %>" >
 <bean:define id="thisChild" name="child" type="org.calacademy.antweb.Taxon" />
 <logic:equal value="true" name="child" property="hasImages">
  <tr>
  <td><bean:write name="child" property="prettyName"/></td>
  <logic:iterate id="element" collection="<%=thisChild.getImages()%>">
    <logic:equal value="<%= shortHand %>" name="element" property="key">
        <td><img src="<bean:write name="element" property="value.lowres" />"></td>
    </logic:equal>
  </logic:iterate>
  </tr>
 </logic:equal>
</logic:iterate>
<jsp:include page="common/copyright.jsp" flush="true"/>

</body>
</html:html>
