

<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>

<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;

    String thePage = HttpUtil.getTarget(request);

    ArrayList<String> introduced = (ArrayList<String>)request.getAttribute("introduced");       
    Geolocale geolocale = (Geolocale) request.getAttribute("geolocale");

%>

<!-- introduced-body.jsp -->

<input id="for_print" type="text" value="AntWeb. Available from: <%= HttpUtil.getFullJspTarget(request)%>">
<input id="for_web" type="text" value="URL: <%= HttpUtil.getFullJspTarget(request)%>">
<div id="page_contents">


<div id="page_data">
    <div id="overview_data">

<br><h2>Introduced for <%= geolocale.getHeading() %>: <%= geolocale.getName() %> </h2>

<br>Introduced species (and subspecies) are not native to the given <%= geolocale.getName() %>.
<br>
<%
    int count = 0;
    for (String taxonName : introduced) {
      ++count;
      String prettyTaxonName = Taxon.getPrettyTaxonName(taxonName);
      %>
      <br><%= count %>. <a href='<%= AntwebProps.getDomainApp() %>/description.do?taxonName=<%= taxonName %>'><%= prettyTaxonName %></a>
      <%
    }

%>
<br><br>
    </div>
</div>
