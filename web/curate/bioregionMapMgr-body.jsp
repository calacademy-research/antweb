<%-- Body of the Bioregion Map Manager ("Genera Native Distribution Tool"): one row per valid extant genus
     (request attribute genusList) with a checkbox per bioregion and totals; sort by subfamily or genus. Admins
     get a save arrow per row (a POST with the form token to bioregionMapMgr.do); curators only look. Docs:
     docs/guides/manager-tools.md (Bioregion Map Manager). --%>
<%@ page import="java.util.*" %>

<%@ page import="org.calacademy.antweb.curate.*" %>

<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>


<%@ page import="org.apache.struts.action.*" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>


<%@include file="/curate/curatorCheck.jsp" %>

<%

    String domainApp = AntwebProps.getDomainApp();

    BioregionMapMgrForm form = (BioregionMapMgrForm) request.getAttribute("form");

    ArrayList<Taxon> genusList = (ArrayList<Taxon>) request.getAttribute("genusList");

    String thisTarget = HttpUtil.getTarget(request);

%>

<div class="admin_left">

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>

<H2>Genera Native Distribution Tool</H2>

<div class="mgr_help" style="max-width:70em;margin:6px 0 12px 0">
The bioregions where each genus is native. A specimen upload flags specimens of a genus collected outside
its native bioregions ("not native to ... Region" in the upload report; species on the introduced list are
not flagged), and the report "Taxa outside their native bioregions" uses the same table. Tick the
bioregions and click the green arrow to save that genus (administrators only; curators can look). Saving
changes only that genus's row and applies to the next upload.
</div>
<br>

<table>
<tr>
<th>%</td>
<th><a href="<%= domainApp %>/bioregionMapMgr.do?orderBy=subfamily">Subfamily</a></th>
<th><a href="<%= domainApp %>/bioregionMapMgr.do?orderBy=genus">Genus</a></th>
<th>Afrotropical</th>
<th>Antarctic</th>
<th>Australasia</th>
<th>Indomalaya</th>
<th>Malagasy</th>
<th>Nearctic</th>
<th>Neotropical</th>
<th>Oceania</th>
<th>Palearctic</th>
<th>Save</th>
</tr>
<tr><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td></tr>

<% 
   //Set<String> taxonNames = genusBioregionMap.keySet();

    //BioregionMapMgrForm bioregionMapMgrForm = (BioregionMapMgrForm) form;
    String orderBy = form.getOrderBy();
   //String orderBy = request.getParameter("orderBy");
           
   //Collections.sort(taxonNames);
   String lastSubfamily = "";
   
   //A.log("bioregionMapMgr-body.jsp keys:" + taxonNames);
   int i = 0;
   
   int neoTot = 0;
   int antTot = 0;
   int afroTot = 0;
   int malaTot = 0;
   int ausTot = 0;
   int oceanTot = 0;
   int indoTot = 0;
   int paleaTot = 0;
   int nearcTot = 0;
   
   //for (String taxonName : taxonNames) {
   for (Taxon genus : genusList) {
     String taxonName = genus.getTaxonName();
     String genusName = org.calacademy.antweb.Formatter.initCap(Taxon.getGenusFromName(taxonName));

     boolean isAfrotropical = false;
     boolean isAntarctica = false;
     boolean isAustralasia = false;
     boolean isIndomalaya =  false;
     boolean isMalagasy = false;
     boolean isNearctic =  false;
     boolean isNeotropical = false;
     boolean isOceania = false;
     boolean isPalearctic = false;
     
     String bioregionMap = (String) genus.getBioregionMap();     

     isAfrotropical = TaxonPropMgr.isMapped(bioregionMap, "Afrotropical");
     if (isAfrotropical) ++afroTot;
     isAntarctica = TaxonPropMgr.isMapped(bioregionMap, "Antarctica");
     if (isAntarctica) ++antTot;
     isAustralasia = TaxonPropMgr.isMapped(bioregionMap, "Australasia");
     if (isAustralasia) ++ausTot;
     isIndomalaya = TaxonPropMgr.isMapped(bioregionMap, "Indomalaya");     
     if (isIndomalaya) ++indoTot;
     isOceania = TaxonPropMgr.isMapped(bioregionMap, "Oceania");     
     if (isOceania) ++oceanTot;
     isMalagasy = TaxonPropMgr.isMapped(bioregionMap, "Malagasy");
     if (isMalagasy) ++malaTot;
     isNearctic = TaxonPropMgr.isMapped(bioregionMap, "Nearctic");     
     if (isNearctic) ++nearcTot;
     isNeotropical = TaxonPropMgr.isMapped(bioregionMap, "Neotropical");
     if (isNeotropical) ++neoTot;
     isPalearctic = TaxonPropMgr.isMapped(bioregionMap, "Palearctic");     
     if (isPalearctic) ++paleaTot;

     //if (taxonName.contains("erapachys")) A.log("bioregionMapMgr-body.jsp taxonName:" + taxonName + " bioregionMap:" + bioregionMap);
     //if (taxonName.contains("erapachys")) A.log("bioregionMapMgr-body.jsp isNeotropical:" + isNeotropical + " isIndomalaya:" + isIndomalaya);
     //A.log("bioregionMapMgr-body.jsp key:" + key + " bioregionMap:" + bioregionMap);
     ++i;
%>

<tr>

<form id="bioregionMgrForm" action="<%=domainApp %>/bioregionMapMgr.do#<%= i %>" method="POST"><%= org.calacademy.antweb.util.Csrf.field(request) %>

<td><a name="<%= i %>"></a>
<%= i %></td>

<%
     String thisSubfamily = Taxon.getSubfamilyFromName(taxonName);
     if (!lastSubfamily.equals(thisSubfamily)) {
       lastSubfamily = thisSubfamily;
       out.println("<td><b><a href='" + domainApp + "/description.do?taxonName=" + lastSubfamily + "'>" + org.calacademy.antweb.Formatter.initCap(lastSubfamily) + "</a></b></td>");
       //A.log("bioregionMapMgr-body lastSubfamily:" + lastSubfamily + " thisSubfamily:" + thisSubfamily + ".");
     } else {
       out.println("<td></td>");
     }
     
     String genusDisplay = "<a href='" + domainApp + "/description.do?taxonName=" + taxonName + "'>" + genusName + "</a>";
%>

<td><%= genusDisplay %></td>
<input type="hidden" class="input_200" name="taxonName" value="<%= Html.escAttr(taxonName) %>">
<input type="hidden" class="input_200" name="orderBy" value="<%= Html.escAttr(orderBy) %>">
<td><a title="Afrotropical">At</a>:<input type="checkbox" name="isAfrotropical" <%= isAfrotropical ? "checked":"" %>></td>
<td><a title="Antarctica">A</a>:<input type="checkbox" name="isAntarctica" <%= isAntarctica ? "checked":"" %>></td>
<td><a title="Australasia">AA</a>:<input type="checkbox" name="isAustralasia" <%= isAustralasia ? "checked":"" %>></td>
<td><a title="Indomalaya">I</a>:<input type="checkbox" name="isIndomalaya" <%= isIndomalaya ? "checked":"" %>></td>
<td><a title="Malagasy">M</a>:<input type="checkbox" name="isMalagasy" <%= isMalagasy ? "checked":"" %>></td>
<td><a title="Nearctic">N</a>:<input type="checkbox" name="isNearctic" <%= isNearctic ? "checked":"" %>></td>
<td><a title="Neotropical">Nt</a>:<input type="checkbox" id="isNeotropical" name="isNeotropical" <%= isNeotropical ? "checked":"" %>></td>
<td><a title="Oceania">O</a>:<input type="checkbox" name="isOceania" <%= isOceania ? "checked":"" %>></td>
<td><a title="Palearctic">P</a>:<input type="checkbox" name="isPalearctic" <%= isPalearctic ? "checked":"" %>></td>
<td><% if (LoginMgr.isAdmin(request)) { %><input border="0" type="image" src="<%= domainApp %>/image/upGreen.jpg" height="23" value="Submit" title="Save this genus"><% } %></td>
</form>
</tr>
    

<% } %>
<tr><td></td><td></td><td></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td></td></tr>
<%-- Same order as the columns: Afrotropical, Antarctica, Australasia, Indomalaya, Malagasy, Nearctic, Neotropical, Oceania, Palearctic. --%>
<tr><td></td><td></td><td><b>Totals:</b></td><td><%= afroTot %></td><td><%= antTot %></td><td><%= ausTot %></td><td><%= indoTot %></td><td><%= malaTot %></td><td><%= nearcTot %></td><td><%= neoTot %></td><td><%= oceanTot %></td><td><%= paleaTot %></td><td></td></tr>
</table>

</div>
