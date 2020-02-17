<%@ page import="java.util.*" %>

<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>


<%@ page import="org.apache.struts.action.*" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>


<%@include file="/curate/curatorCheck.jsp" %>

<%
    Login accessLogin = LoginMgr.getAccessLogin(request);

    String domainApp = AntwebProps.getDomainApp();

	DynaActionForm df = (DynaActionForm) request.getAttribute("form");
	String adm1 = (String) df.get("adm1Name");
	String country = (String) request.getAttribute("country");
    ArrayList<Geolocale> list = (ArrayList<Geolocale>) request.getAttribute("list"); 
    String codeStr = (String) request.getAttribute("codes");     

    boolean isValidMatch = false;    
    boolean isInvalidMatch = false;    
    String useValidName = null;
    for (Geolocale geolocale : list) {
      if (geolocale.getName().equals(adm1)) {
        if (geolocale.getIsValid()) {
          isValidMatch = true;
        } else {
          isInvalidMatch = true;
          useValidName = geolocale.getValidName();
        }
        break;
      }
    }

    String isFound = " did not match any of the adm1";
    String matched = " unmatched";
    if (isValidMatch) {
        isFound = " matched a valid adm1";
        matched = " matched";    
    }
    if (isInvalidMatch) {
        isFound = " matched an invalid adm1";
        matched = "";    
    }

%>

<div class="admin_left">

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br>

<H2>Adm1 Manager</H2>
<br>

<h3>Country: <a href='<%= domainApp %>/geolocaleMgr.do?georank=adm1&parent=<%= country %>'><%= country %></a></h3>

<br>
<h3>Adm1: <b><%= adm1 %></b><%= isFound %>.</h3>

<%
    String message = (String) request.getAttribute("message"); 
    if (message != null) out.println("<h3>" + message + "</h3>");
%>

<%
   String encodedAdm1 = java.net.URLEncoder.encode(adm1);
   String encodedCountry = java.net.URLEncoder.encode(country);     
%>
<br>For reference: <a href='http://geonames.nga.mil/namesgaz/'>Geonames Search</a>
<%
   if (LoginMgr.isAdmin(accessLogin)) { 
     if (!isValidMatch && !isInvalidMatch) { 
%>
<br>As an Antweb Admin, you may <b><a href='<%= domainApp %>/editGeolocale.do?georank=adm1&parent=<%= encodedCountry %>&isCreate=true&isSubmit=1&name=<%= encodedAdm1 %>&source=adm1Mgr'>Create</a></b> this ADM1.
<%   } else if (isInvalidMatch){ %>
<br>As an Antweb Admin, you may <b><a href='<%= domainApp %>/editGeolocale.do?georank=adm1&parent=<%= encodedCountry %>&name=<%= encodedAdm1 %>'>Edit</a></b> this ADM1.
<%   }
   }
   
   if (isInvalidMatch && useValidName != null) { %>
<br><br>Because the invalid adm1 (<b><%= adm1 %></b>) has a Valid Name (<b><%= useValidName %></b>) this name will be used when the specimen file uploaded.
<% } %>

   

<br><br>
<h3>Valid Adm1</h3>
<table><tr>
<th>Count</th>
<th>Mgr</th>
<th width=250>Name</th>
<th>Source</th>
</tr>
</tr><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><tr>
<%

    int count = 0;
    boolean lastRecord = true;
    for (Geolocale geolocale : list) {
      ++count;
%> <tr> <%
      if (count == 1000) {
        break;
      } else { %>

<%          if (lastRecord == true && !geolocale.getIsValid()) {
                lastRecord = false; %>
                </tr></table>
<% if (!LoginMgr.isAdmin(accessLogin)) break; %>
                <br><br><h3>Non-valid Adm1</h3>
                <table><tr>
				<th>Count</th>
				<th>Mgr</th>
				<th>Name</th>
				<th>Source</th>
				<th>Valid Name</th>
                </tr><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><tr>
         <% } %>             
      
        <td><%= count %></td>
        <td><a href="<%= domainApp %>/adm1Mgr.do?adm1Name=<%= geolocale.getName() %>&countryName=<%= geolocale.getParent() %>"><img src="<%= domainApp %>/image/view_icon.png"></a></td>        
        <td><a href='<%= domainApp %>/editGeolocale.do?id=<%= geolocale.getId() %>'><%= geolocale.getName() %></a></td>
        <td><%= geolocale.getSource() %></td>
<%      if (!geolocale.getIsValid()) { %>
          <td><%= geolocale.getValidName() %></td>
<%      }
      } %>
   </tr>
<%  } %>
   </table>

    <br><br>
    <h3>
    These codes contain the<%= matched %> adm1:<%= codeStr %>.
    </h3>
    <% if (!isValidMatch && codeStr != null && !"".equals(codeStr)) { 
         if (useValidName != null) { %>
    <br>(You may update these specimen in your specimen upload file to refer to ADM1:<%= useValidName %> or Antweb will automatically update them at specimen upload time).
    <%   } else { %>
    <br>(Please update your specimen upload file to refer to a valid adm1 or discuss with <%= AntwebUtil.getAdminEmail() %>).
    <%   }
       } %>
</div>
