<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.home.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>

<%@ page import="java.util.*" %>

<%@include file="/curate/curatorCheck.jsp" %>

<% 
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
    String domainApp = AntwebProps.getDomainApp();    
 %>

<%@include file="/common/antweb-defs.jsp" %>

<div class=left>

<p><p><a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>

<h2>Old Query Manager</h2>

    <h3 align="left">Integrity Queries</h3>
	  Execute All:<a href="<%= domainApp %>/query.do?action=checkIntegrity">here</a>
    <%
      ArrayList<String> integrityQueries = Queries.getIntegrityNamesArray();    
      int i = 0;
      for (String name : integrityQueries) {
        if (i == 0) out.println("<ul align=left>");
        ++i;
        out.println("<li><a href='" + domainApp + "/query.do?name=" + name +"'>" + name + "</a>");
        if (i == integrityQueries.size()) out.println("</ul>");
      }
    %>

    <h3 align="left">Curate AntCat Queries</h3>
	  Execute All:<a href="<%= domainApp %>/query.do?action=curateAntcat">here</a>
    <%
      ArrayList<String> queries = Queries.getCurateAntcatNamesArray();   
      i = 0; 
      for (String name : queries) {
        if (i == 0) out.println("<ul align=left>");
        ++i;
        out.println("<li><a href='" + domainApp + "/query.do?name=" + name +"'>" + name + "</a>");
        if (i == queries.size()) out.println("</ul>");
      }
    %>
    
    <h3 align="left">Dev Queries</h3>
	  Execute All:<a href="<%= domainApp %>/query.do?action=devQueries">here</a>
    <%
      queries = Queries.getDevIntegrityNamesArray();   
      i = 0; 
      for (String name : queries) {
        if (i == 0) out.println("<ul align=left>");
        ++i;
        out.println("<li><a href='" + domainApp + "/query.do?&name=" + name +"'>" + name + "</a>");
        if (i == queries.size()) out.println("</ul>");
      }
    %>

    
    <h3 align="left">Curious Queries</h3>
	  Execute All:<a href="<%= domainApp %>/query.do?action=curiousQueries">here</a>
    <%
      ArrayList<String> curiousQueries = Queries.getCuriousNamesArray();   
      i = 0; 
      for (String name : curiousQueries) {
        if (i == 0) out.println("<ul align=left>");
        ++i;
        out.println("<li><a href='" + domainApp + "/query.do?name=" + name +"'>" + name + "</a>");
        if (i == curiousQueries.size()) out.println("</ul>");
      }
    %>

    <h3 align="left">Various Queries</h3>
    <%
      ArrayList<String> variousQueries = Queries.getNames();   
      i = 0; 
      for (String name : variousQueries) {
        if (i == 0) out.println("<ul align=left>");
        ++i;
        out.println("<li><a href='" + domainApp + "/query.do?name=" + name +"'>" + name + "</a>");
        if (i == variousQueries.size()) out.println("</ul>");
      }
    %>

    <h3 align="left">Assorted Queries</h3>
      <a href='<%= AntwebProps.getDomainApp() %>/list.do?action=multiBioregionTaxaList&groupId=1'>Link</a>
      <br><br>

    <h3 align="left">Geolocale Queries</h3>
    <%
      ArrayList<String> geolocaleQueries = Queries.getGeolocaleQueries();   
      i = 0; 
      for (String name : geolocaleQueries) {
        if (i == 0) out.println("<ul align=left>");
        ++i;
        out.println("<li><a href='" + domainApp + "/query.do?action=query&name=" + name +"'>" + name + "</a>");
        if (i == geolocaleQueries.size()) out.println("</ul>");
      }
    %>

    <h3 align="left">Login Queries</h3>
    <%
      ArrayList<String> loginQueries = Queries.getLoginQueries();   
      i = 0; 
      for (String name : loginQueries) {
        if (i == 0) out.println("<ul align=left>");
        ++i;
        out.println("<li><a href='" + domainApp + "/query.do?name=" + name +"'>" + name + "</a>");
        if (i == loginQueries.size()) out.println("</ul>");
      }
    %>

        

</div > 
