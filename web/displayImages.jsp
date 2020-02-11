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

<% 
    java.util.ArrayList allShots = new java.util.ArrayList();
    allShots.add("h");
    allShots.add("p");
    allShots.add("d");
    allShots.add("v");
    allShots.add("l");

    java.util.ArrayList headers = new java.util.ArrayList();
    headers.add("head");
    headers.add("profile");
    headers.add("dorsal");
    headers.add("ventral");
    headers.add("label");

    java.util.Hashtable labelMap = new java.util.Hashtable();
    labelMap.put("head","h");
    labelMap.put("profile","p");
    labelMap.put("dorsal","d");
    labelMap.put("ventral","v");
    labelMap.put("label","l");
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

<logic:present name="taxon" property="species">
<h1>Species: <bean:write name="taxon" property="species" /> </h1>
</logic:present>

<table>
<!-- print the headers -->
<tr>
<th><bean:write name="taxon" property="nextRank" /></th>

<logic:iterate id="header" collection="<%= headers %>">
  <th><bean:write name="header" /></th>
</logic:iterate>

</tr>

<!-- print the rest -->
<jsp:useBean id="taxon" scope="session" class="org.calacademy.antweb.Taxon" />
<jsp:setProperty name="taxon" property="*" />

<logic:iterate id="child" collection="<%= taxon.getChildren() %>" type="org.calacademy.antweb.Taxon" >

 <bean:define id="thisChild" name="child" type="org.calacademy.antweb.Taxon" />
 
 <logic:equal value="true" name="child" property="hasImages">
 
  <tr>
  <td><bean:write name="child" property="prettyName"/></td>

    <logic:iterate id="shot" collection="<%= allShots %>" >
      <td>
      <logic:iterate id="element" collection="<%= thisChild.getImages() %>">
        <logic:equal value="<%= (String) shot %>" name="element" property="key">
          <img src="<bean:write name="element" property="value.lowres" />">
        </logic:equal>
      </logic:iterate>
      </td>
    </logic:iterate>
   </tr>
 </logic:equal>
</logic:iterate>

<html:link href="chooseComparison.jsp">compare these images</html:link>
<jsp:include page="common/copyright.jsp" flush="true"/>

</body>
</html:html>
