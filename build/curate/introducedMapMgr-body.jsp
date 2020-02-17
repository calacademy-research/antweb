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

    ArrayList<Taxon> speciesList = (ArrayList<Taxon>) request.getAttribute("speciesList");

    String thisTarget = HttpUtil.getTarget(request);

%>

<div class="admin_left">

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>

<H2>Introduced Species Native Bioregion Distribution Tool</H2>
<br>
Of the species that are on the Introduced Species <a href='<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=species&project=introducedants'>list</a>, here is where they are known to be native...
<% if (LoginMgr.isDeveloper(request)) { %>
<br><br><b>Dev:</b>
For species on the introduced list, records will be stored in the Taxon Props table with the introducedMap prop, with a map that looks something like:
<br>&nbsp;&nbsp;&nbsp;Australasia:true Neotropical:true Oceania:true Malagasy:true Nearctic:true Indomalaya:true
<br>&nbsp;&nbsp;&nbsp;Omitted properties default to false.
<br><br>
The values here are used during a scheduled computation process (GeolocaleDb.calcIntroduced()) during which the geolocale_taxon that have 
a taxon with a taxon_prop introducedMap, and a geolocale within that introducedMap will be flagged as is_introduced.
<% } %>
<br>
<br>
<table>
<tr>
<th>%</td>
<th>Genus</td>
<th>Species</th>
<th><img src="<%= domainApp %>/image/redDot.jpg" width="10" title="None selected" /></th>
<th>Afrotropical</th>
<th>Antarctica</td>
<th>Australasia</th>
<th>Indomalaya</th>
<th>Malagasy</th>
<th>Nearctic</th>
<th>Neotropical</th>
<th>Oceania</th>
<th>Palearctic</th>
<th>Save</th>
</tr>
<tr><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td></tr>

<% 
    String orderBy = form.getOrderBy();
   
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
   int noneSelectedTot = 0;
   
   String lastGenusName = "";
   
   for (Taxon species : speciesList) {
     String taxonName = species.getTaxonName();
     boolean isNeotropical = false;
     boolean isAntarctica = false;
     boolean isAfrotropical = false;
     boolean isMalagasy = false;
     boolean isAustralasia = false;
     boolean isOceania = false;
     boolean isIndomalaya =  false;
     boolean isPalearctic = false;
     boolean isNearctic =  false;
        
     String introducedMap = (String) species.getIntroducedMap();     

     isNeotropical = TaxonPropMgr.isMapped(introducedMap, "Neotropical");
     if (isNeotropical) ++neoTot;
     isAfrotropical = TaxonPropMgr.isMapped(introducedMap, "Afrotropical");
     if (isAfrotropical) ++afroTot;
     isAntarctica = TaxonPropMgr.isMapped(introducedMap, "Antarctica");
     if (isAntarctica) ++antTot;
     isMalagasy = TaxonPropMgr.isMapped(introducedMap, "Malagasy");
     if (isMalagasy) ++malaTot;
     isAustralasia = TaxonPropMgr.isMapped(introducedMap, "Australasia");
     if (isAustralasia) ++ausTot;
     isOceania = TaxonPropMgr.isMapped(introducedMap, "Oceania");     
     if (isOceania) ++oceanTot;
     isIndomalaya = TaxonPropMgr.isMapped(introducedMap, "Indomalaya");     
     if (isIndomalaya) ++indoTot;
     isPalearctic = TaxonPropMgr.isMapped(introducedMap, "Palearctic");     
     if (isPalearctic) ++paleaTot;
     isNearctic = TaxonPropMgr.isMapped(introducedMap, "Nearctic");     
     if (isNearctic) ++nearcTot;

     boolean noneSelected = !(isNeotropical || isAntarctica || isAfrotropical || isMalagasy || isAustralasia || isOceania || isIndomalaya || isPalearctic || isNearctic);
     if (noneSelected) ++noneSelectedTot;

 	 if (taxonName.contains("erapachys")) A.log("introducedMapMgr-body.jsp taxonName:" + taxonName + " introducedMap:" + introducedMap);
 	 if (taxonName.contains("erapachys")) A.log("introducedMapMgr-body.jsp isNeotropical:" + isNeotropical + " isIndomalaya:" + isIndomalaya);

     //A.log("introducedMapMgr-body.jsp key:" + key + " introducedMap:" + introducedMap);
     ++i;

%>

<tr>

<form id="introducedMgrForm" action="<%=domainApp %>/introducedMapMgr.do#<%= i %>" method="POST">
<td><a name="<%= i %>"></a><%= i %></td>

<%
     String genusName = org.calacademy.antweb.Formatter.initCap(Taxon.getGenusFromName(taxonName));
     String genusDisplay = "<a href='" + domainApp + "/description.do?taxonName=" + Taxon.getGenusTaxonNameFromName(taxonName) + "'>" + genusName + "</a>";
     if (genusName.equals(lastGenusName)) genusDisplay = "";
     lastGenusName = genusName;

     String speciesName = Taxon.getSpeciesFromName(taxonName);
     String speciesDisplay = "<a href='" + domainApp + "/description.do?taxonName=" + taxonName + "'>" + speciesName + "</a>";
%>

<td><%= genusDisplay %></td>
<td><%= speciesDisplay %></td>

<input type="hidden" class="input_200" name="taxonName" value="<%= taxonName %>">
<input type="hidden" class="input_200" name="orderBy" value="<%= orderBy %>">
<td><% if (noneSelected) { %><img src="<%= domainApp %>/image/redDot.jpg" width="10" title="None selected" /><% } %></td>
<td><a title="Afrotropical">At</a>:<input type="checkbox" name="isAfrotropical" <%= isAfrotropical ? "checked":"" %>></td>
<td><a title="Antarctica">A</a>:<input type="checkbox" name="isAntarctica" <%= isAntarctica ? "checked":"" %>></td>
<td><a title="Australasia">AA</a>:<input type="checkbox" name="isAustralasia" <%= isAustralasia ? "checked":"" %>></td>
<td><a title="Indomalaya">I</a>:<input type="checkbox" name="isIndomalaya" <%= isIndomalaya ? "checked":"" %>></td>
<td><a title="Malagasy">M</a>:<input type="checkbox" name="isMalagasy" <%= isMalagasy ? "checked":"" %>></td>
<td><a title="Nearctic">N</a>:<input type="checkbox" name="isNearctic" <%= isNearctic ? "checked":"" %>></td>
<td><a title="Neotropical">Nt</a>:<input type="checkbox" id="isNeotropical" name="isNeotropical" <%= isNeotropical ? "checked":"" %>></td>
<td><a title="Oceania">O</a>:<input type="checkbox" name="isOceania" <%= isOceania ? "checked":"" %>></td>
<td><a title="Palearctic">P</a>:<input type="checkbox" name="isPalearctic" <%= isPalearctic ? "checked":"" %>></td>
<td><input border="0" type="image" src="<%= domainApp %>/image/upGreen.jpg" height="23" value="Submit"></td>
</form>
</tr>
    

<% } %>
<tr><td></td><td></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td><td><hr></td></tr>
<tr><td></td><td></td><td><b>Totals:</b></td><td><%= noneSelectedTot %></td><td><%= afroTot %></td><td><%= antTot %></td><td><%= ausTot %></td><td><%= indoTot %></td><td><%= malaTot %></td><td><%= nearcTot %></td><td><%= neoTot %></td><td><%= oceanTot %></td><td><%= paleaTot %></td><td></td></tr>
</table>

<% if (LoginMgr.isDeveloper(request)) { %>
     <br><br>Dev only:<a href= "<%= AntwebProps.getDomainApp() %>/introducedMapMgr.do?isRefresh=true">Refresh</a> Introduced Distribution Tool. (Careful! Will reload table from specimen data). 
<% } %>
</div>
