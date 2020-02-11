<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<!-- was invalid "bean:present"  Mark Aug 28, 2011 -->
<logic:present parameter="project">
<bean:parameter id="project" name="project"/>
</logic:present>

<html:html locale="true">
<head>
<title>Choose Ants - AntWeb</title>
<html:base/>
</head>
<body bgcolor="white">

<% 
    java.util.ArrayList allShots = new java.util.ArrayList();
    allShots.add("h");
    allShots.add("p");
    allShots.add("d");
    allShots.add("l");
    allShots.add("v");

    java.util.ArrayList headers = new java.util.ArrayList();
    headers.add("head");
    headers.add("profile");
    headers.add("dorsal");
    headers.add("label");
    headers.add("ventral");

    java.util.Hashtable labelMap = new java.util.Hashtable();
    labelMap.put("head","h");
    labelMap.put("profile","p");
    labelMap.put("dorsal","d");
    labelMap.put("label","l");
    labelMap.put("ventral","v");
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

<html:form action="/chooseComparison">
<logic:iterate id="child" collection="<%= taxon.getChildren() %>" >
 <bean:define name="child" id="thisChild" type="org.calacademy.antweb.Taxon" />
 <logic:equal value="true" name="child" property="hasImages">
  <tr>
  <td><bean:write name="child" property="prettyName"/></td>

    <logic:iterate id="shot" collection="<%= allShots %>" >
      <td>
      <logic:iterate id="element" collection="<%=thisChild.getImages()%>">
        <logic:equal value="<%= (String) shot %>" name="element" property="key">
          <img src="<bean:write name="element" property="value.lowres" />">
          <input type="checkbox" name="chosen" 
                 value="<bean:write name="element" property="value.medres" />">
        </logic:equal>
      </logic:iterate>
      </td>
    </logic:iterate>
   </tr>
 </logic:equal>
</logic:iterate>
<input type="hidden" name="name" value="<bean:write name="taxon" property="name" />">
<input type="hidden" name="rank" value="<bean:write name="taxon" property="rank" />">
<input type="hidden" name="project" value="<bean:write name="taxon" property="rank" />">
<html:submit />
</html:form>
<jsp:include page="common/copyright.jsp" flush="true"/>

</body>
</html:html>
