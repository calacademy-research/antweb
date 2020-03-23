<%@ page import="org.calacademy.antweb.Formatter" %>

<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.curate.speciesList.*" %>

<jsp:useBean id="toolForm" scope="request" class="org.calacademy.antweb.curate.speciesList.SpeciesListToolForm" />
<jsp:setProperty name="toolForm" property="*" />

<%
/*
  All properties are in the form except for
    session
      accessGroup
    request
      message

*/

    SpeciesListToolProps toolProps = (SpeciesListToolProps) session.getAttribute("speciesListToolProps");

	if (toolProps == null) {
        AntwebUtil.log("speciesListTool-body.jsp No toolProps.");
        %> Tool Props not found...<%
	    return;
    }
    
    /* toolProps (SpeciesListToolProps) should have links to speciesListables. 
       From that we could call getName, or getTitle(), or getList(). */
    String mapSpeciesList1Name = toolProps.getMapSpeciesList1Name();    
    String mapSpeciesList2Name = toolProps.getMapSpeciesList2Name();   
    String mapSpeciesList3Name = toolProps.getMapSpeciesList3Name();   

    String refSpeciesListName = toolProps.getRefSpeciesListName();
    String refSpeciesListType = toolProps.getRefSpeciesListType();
    
    String displaySubfamily = toolProps.getDisplaySubfamily();
    
    Login accessLogin = LoginMgr.getAccessLogin(request);
    // This will be used in the included taxonEditField.jsp.      
    ArrayList<SpeciesListable> speciesListList = accessLogin.getSpeciesListList();

    ArrayList<String> refListList = toolProps.getRefListList();
    ArrayList<String> noPassWorldantsSpeciesList = toolProps.getNoPassWorldantsSpeciesList();
    ArrayList<String> refListSubfamilies = toolProps.getRefListSubfamilies();
    
    //A.log("speciesListTool-body.jsp form:" + toolForm.toString());    
    //A.log("speciesListTool-body.jsp props:" + toolProps.toString());    

    //A.log("speciesListTool-body.jsp mapSpeciesList2Name:" + mapSpeciesList2Name);    

    String message = (String) request.getAttribute("message");
    if (message != null && !"".equals(message)) out.println("<b>Message: </b>" + message + "<br><br><hr>");

    String emptyTag = "<img src=\"" + AntwebProps.getDomainApp() + "/image/1x1.gif\" width=10>";
    String yellowAntTitle = "A specimen records exists for this species in this region";
    String yellowAntTag = "<img src=\"" + AntwebProps.getDomainApp() + "/image/yellow_ant.png\" width=10 title='x1 - " + yellowAntTitle + "'>";
    String redDotTitle = "This species is not checked.";
    String redDotTag = "<img src=\"" + AntwebProps.getDomainApp() + "/image/redDot.jpg\" width=10 title='x2 - " + redDotTitle + "'>"; 
    String redXTitle = "This name is not in the selected species list history.";
    String redXTag = "<img src=\"" + AntwebProps.getDomainApp() + "/image/redX.png\" width=10 title='x3 - " + redXTitle + "'><font color=white>x3</font>"; 
%>

<h1>Species List Tool </h1>
<%= toolProps.getPermaLinkTag() %>
<br><br>

<table>
<tr><td><%= yellowAntTag %>&nbsp;&nbsp;Yellow ant indicates a specimen records exists for that species in the given region. Search on x1.</td></tr>
<tr><td><%= redDotTag %>&nbsp;&nbsp;Red dot highlights those names that are not checked. Search on x2.</td></tr>
<% if ("speciesListHistory".equals(refSpeciesListType)) { %>
<tr><td><%= redXTag %>Red X indicates that a name is not in the species list history. Search on x3.</td></tr>
<% } %>
</table>
<br><br>

The SpeciesListTool log is here: <a href="<%= AntwebProps.getDomainApp() %>/web/log/speciesListTool.txt">log</a>

<br><br>
<hr><br>

<%
  //out.println("Display Subfamily:");
  if (!"none".equals(displaySubfamily) && !"".equals(displaySubfamily)) {
    out.println("<a href='" + AntwebProps.getDomainApp() + "/speciesListTool.do?displaySubfamily=none'>All</a>");
  } else {
    out.println("<u><b>All</b></u>");
  }
  for (String subfamily : refListSubfamilies) {
    if (subfamily.equals(displaySubfamily)) {
      out.println("<b><font size=3 color=green><u>" + Formatter.initCap(subfamily) + "</u></font></b>");  
    } else {
      out.println("&nbsp;&nbsp;<a href='" + AntwebProps.getDomainApp() + "/speciesListTool.do?displaySubfamily=" + subfamily + "'>" + Formatter.initCap(subfamily) + "</a>&nbsp;");
    } 
  }
%>

<br><br>

<!-- headers -->

<table>

<tr>
<th>Reference Species List</th>
<%
  String header1 = "Species List 1";
  if (mapSpeciesList1Name != null && !"none".equals(mapSpeciesList1Name)) { 
    SpeciesListable sl = SpeciesListMgr.getSpeciesList(mapSpeciesList1Name);
if (sl == null) A.log("speciesListTool-body.jsp sl is null for mapSpeciesList1Name:" + mapSpeciesList1Name);
    String publicListLink = "<a href='" + sl.getListLink() + "'><img src='" + AntwebProps.getDomainApp() + "/image/upRight.png' width='11' height='12' border='0' title='Go to " + sl.getTitle() + " Ant List'></a>";
    header1 = "<a href='" + AntwebProps.getDomainApp() + "/speciesListHistory.do?speciesListName=" + mapSpeciesList1Name + "'>Species List 1</a>" + publicListLink;
  }
  String header2 = "Species List 2";
  if (mapSpeciesList2Name != null && !"none".equals(mapSpeciesList2Name)) {
    SpeciesListable sl = SpeciesListMgr.getSpeciesList(mapSpeciesList2Name);
    String publicListLink = "<a href='" + sl.getListLink() + "'><img src='" + AntwebProps.getDomainApp() + "/image/upRight.png' width='11' height='12' border='0' title='Go to " + sl.getTitle() + " Ant List'></a>";
    header2 = "<a href='" + AntwebProps.getDomainApp() + "/speciesListHistory.do?speciesListName=" + mapSpeciesList2Name + "'>Species List 2</a>" + publicListLink;
  }
  String header3 = "Species List 3";
  if (mapSpeciesList3Name != null && !"none".equals(mapSpeciesList3Name)) {
    SpeciesListable sl = SpeciesListMgr.getSpeciesList(mapSpeciesList3Name);
    String publicListLink = "<a href='" + sl.getListLink() + "'><img src='" + AntwebProps.getDomainApp() + "/image/upRight.png' width='11' height='12' border='0' title='Go to " + sl.getTitle() + " Ant List'></a>";
    header3 = "<a href='" + AntwebProps.getDomainApp() + "/speciesListHistory.do?speciesListName=" + mapSpeciesList3Name + "'>Species List 3</a>" + publicListLink;
  }
%>
<th><%= header1 %></th><th><%= header2 %></th><th><%= header3 %></th>
</tr>

<tr>
<th>
<form id="refSpeciesListForm" action="<%= AntwebProps.getDomainApp() %>/speciesListTool.do">
      <input type="hidden" id="mapSpeciesList1Name" name="mapSpeciesList1Name" value="<%= mapSpeciesList1Name %>"/>
      <input type="hidden" id="mapSpeciesList2Name" name="mapSpeciesList2Name" value="<%= mapSpeciesList2Name %>"/>
      <input type="hidden" id="mapSpeciesList3Name" name="mapSpeciesList3Name" value="<%= mapSpeciesList3Name %>"/>

      <input type="hidden" id="action" name="action" value="changeRefSpeciesList"/>
      
       <% if (mapSpeciesList1Name != null) { %> 
        <input type="hidden" id="speciesListName" name="speciesListName" value="<%= mapSpeciesList1Name %>"/>  
       <% } %>
      
<!-- /th -->
      <!-- Though included in this form, this is printed above. -->
<!-- b>Choose Reference Taxa List:</b -->
      <select name="refSpeciesListType" id="refSpeciesList_select">
        <option id="refSpeciesListType" value="none" selected>None</option>

  
  <% String listTitle = "Add from Advanced Search";
     if ("advSearch".equals(refSpeciesListType)) { %>        
        <option id="refSpeciesListType" value="advSearch" selected><%= listTitle %></option>
  <% } else { %>
        <option id="refSpeciesListType" value="advSearch"><%= listTitle %></option>
  <% }
     listTitle = "Add Antcat names";
     if ("antcatNames".equals(refSpeciesListType)) { %>        
        <option id="refSpeciesListType" value="antcatNames" selected><%= listTitle %></option>
  <% } else { %>
        <option id="refSpeciesListType" value="antcatNames"><%= listTitle %></option>
  <% }
  
  /*
     listTitle = "Add from a species list";
     if ("speciesList".equals(refSpeciesListType) || ((refSpeciesListName != null) && refSpeciesListName.contains("ants"))) { % >        
        <option id="refSpeciesListType" value="speciesList" selected>< %= listTitle % ></option>
  < % } else { % >
        <option id="refSpeciesListType" value="speciesList">< %= listTitle % ></option>
  < % }
    */
  
     listTitle = "Add from species list history"; 
     boolean refListIsHistory = false;
     if ("speciesListHistory".equals(refSpeciesListType)) {
        refListIsHistory = true; %> 
        <option id="refSpeciesListType" value="speciesListHistory" selected><%= listTitle %></option>
  <% } else if (mapSpeciesList1Name != null) { %>
        <option id="refSpeciesListType" value="speciesListHistory"><%= listTitle %></option>
  <% }
     
     //A.log("speciesListTool-body.jsp refSpeciesListType:" + refSpeciesListType + " refSpeciesListName:" + refSpeciesListName);
   %>
     
      </select>
      <br><% if (AntwebProps.isDevOrStageMode() || LoginMgr.isDeveloper(accessLogin)) out.println(toolProps.getDisplayRefSpeciesListParams()); %>
      <input type="submit" name="go" value="go">      

</form>

</th> <!-- If this was abouve the select, the drop down would appear above the Save button -->

<th> <!-- mapSpeciesList1 drop down list-->
    <form id="SpeciesListForm" action="<%= AntwebProps.getDomainApp() %>/speciesListTool.do">
      <input type="hidden" name="action" value="" />      
      <select name="mapSpeciesList1Name" id="SpeciesList_select">
        <option id="mapSpeciesList1Name" value="none" <%= (mapSpeciesList1Name == null ? " selected" : "") %>>None</option>      

<%
   boolean found = false;
   String selectedStr = "";
   
   for (SpeciesListable speciesList : speciesListList) {
     String speciesListKey = speciesList.getKey();
     String speciesListName = speciesList.getName();
     String prettySpeciesListName = SpeciesListMgr.getPrettyName(speciesListKey); 
     String value = null;
     if (speciesList.getIsUseChildren()) value = "";
     value = speciesListKey;
     String spacer = "";
	 if (SpeciesListable.ADM1.equals(speciesList.getType())) {
	   spacer = "&nbsp;&nbsp;&nbsp;";
	 }     
	 int maxStrLength = 30;
     String displayName = prettySpeciesListName;
     if (prettySpeciesListName != null && prettySpeciesListName.length() > maxStrLength) displayName = prettySpeciesListName.substring(0, maxStrLength) + "...";	    
       //A.log("speciesListTool-body.jsp speciesListKey:" + speciesListKey);	   

     
     if (speciesListKey.equals(mapSpeciesList1Name)) {
       selectedStr = "selected"; 
       found = true;
     } else {
       selectedStr = "";
     }
     
 %>        
        <option id="mapSpeciesList1Name" value="<%= value %>" <%= selectedStr %>><%= spacer + displayName %></option>
<% } 
   if (!found) { 
      Geolocale adminEditGeolocale = GeolocaleMgr.getGeolocale(mapSpeciesList1Name);
      if (adminEditGeolocale != null) {
   %>
        <option id="mapSpeciesList1Name" value="<%= mapSpeciesList1Name %>" selected><%= adminEditGeolocale.getName() %></option>   
<%    }
   } %>

      </select>
      <br><input type="submit" name="go" value="go"> 
    </form>
</th>

<th> <!-- mapSpeciesList2 drop down list-->
    <form id="SpeciesListForm" action="<%= AntwebProps.getDomainApp() %>/speciesListTool.do">
      <input type="hidden" name="action" value="" />        
      <select name="mapSpeciesList2Name" id="SpeciesList_select">
        <option id="mapSpeciesList2Name" value="none" <%= (mapSpeciesList2Name == null ? " selected" : "") %>>None</option>      
<%
     for (SpeciesListable speciesList : speciesListList) {
       String speciesListKey = speciesList.getKey();     
       String speciesListName = speciesList.getName();
       String prettySpeciesListName = SpeciesListMgr.getPrettyName(speciesListKey); 
       String value = null;
       if (speciesList.getIsUseChildren()) value = "";
       value = speciesListKey;
       String spacer = "";
	   if (SpeciesListable.ADM1.equals(speciesList.getType())) {
	     spacer = "&nbsp;&nbsp;&nbsp;";
	   } 
	   int maxStrLength = 30;
       String displayName = prettySpeciesListName;
       if (prettySpeciesListName != null && prettySpeciesListName.length() > maxStrLength) displayName = prettySpeciesListName.substring(0, maxStrLength) + "...";	   	   
%>        
        <option id="mapSpeciesList2Name" value="<%= value %>" <% if (speciesListKey.equals(mapSpeciesList2Name)) out.print("selected"); %>><%= spacer + displayName %></option>
<%   } %>
      </select>
      <br><input type="submit" name="go" value="go"> 
    </form>
</th>

<th> <!-- mapSpeciesList3 drop down list-->
    <form id="SpeciesListForm" action="<%= AntwebProps.getDomainApp() %>/speciesListTool.do">
      <input type="hidden" name="action" value="" />   
      <select name="mapSpeciesList3Name" id="SpeciesList_select">
        <option id="mapSpeciesList3Name" value="none" <%= (mapSpeciesList3Name == null ? " selected" : "") %>>None</option>      
<%
     for (SpeciesListable speciesList : speciesListList) {
       String speciesListKey = speciesList.getKey();     
       String speciesListName = speciesList.getName();
       String prettySpeciesListName = SpeciesListMgr.getPrettyName(speciesListKey); 
       String value = null;
       if (speciesList.getIsUseChildren()) value = "";
       value = speciesListKey;
       String spacer = "";
	   if (SpeciesListable.ADM1.equals(speciesList.getType())) {
	     spacer = "&nbsp;&nbsp;&nbsp;";
	   } 
	   int maxStrLength = 30;
       String displayName = prettySpeciesListName;
       if (prettySpeciesListName != null && prettySpeciesListName.length() > maxStrLength) displayName = prettySpeciesListName.substring(0, maxStrLength) + "...";	   	   
%>        
        <option id="mapSpeciesList3Name" value="<%= value %>" <% if (speciesListKey.equals(mapSpeciesList3Name)) out.print("selected"); %>><%= spacer + displayName %></option>
<%   } %>
      </select>
      <br><input type="submit" name="go" value="go"> 
    </form>
</th>
</tr>
<html:form method="POST" action="speciesListTool" enctype="multipart/form-data">
    <input type="hidden" name="mapSpeciesList1Name" value="<%= mapSpeciesList1Name %>"/>
    <input type="hidden" name="mapSpeciesList2Name" value="<%= mapSpeciesList2Name %>"/>
    <input type="hidden" name="mapSpeciesList3Name" value="<%= mapSpeciesList3Name %>"/>
    <input type="hidden" name="displaySubfamily" value="<%= displaySubfamily %>"/>
    <input type="hidden" name="action" value="save"/>
    <input type="hidden" name="refSpeciesListType" value="<%= refSpeciesListType %>" />
    <input type="hidden" name="refSpeciesListName" value="<%= refSpeciesListName %>"/>
    <input type="submit" name="save" value="Save Changes"> <br><br>    

<%
    ArrayList<Taxon> mapSpeciesList1 = (ArrayList<Taxon>) toolProps.getMapSpeciesList1();
    ArrayList<Taxon> mapSpeciesList2 = (ArrayList<Taxon>) toolProps.getMapSpeciesList2();
    ArrayList<Taxon> mapSpeciesList3 = (ArrayList<Taxon>) toolProps.getMapSpeciesList3();
    ArrayList<Taxon> sumSpeciesList = (ArrayList<Taxon>) toolProps.getSumSpeciesList();
    ArrayList<Taxon> refSpeciesList = (ArrayList<Taxon>) toolProps.getRefSpeciesList();

    int mapSpeciesListCount1 = 0;
    int mapSpeciesListCount2 = 0;
    int mapSpeciesListCount3 = 0;
    
    //A.log("speciesListTool-body.jsp refSpeciesList:" + refSpeciesList);
    
    for (Taxon taxon : sumSpeciesList) { 
      String taxonName = taxon.getTaxonName();
      boolean inMapSpeciesList1 = false;      
      boolean inMapSpeciesList2 = false;
      boolean inMapSpeciesList3 = false;

  //A.log("RefListTaxa2 taxonName:" + taxonName + " " + (refSpeciesList != null && refSpeciesList.contains(taxon)) );

      boolean isInRefList = (refSpeciesList != null && refSpeciesList.contains(taxon));

      if (mapSpeciesList1 != null) {
        inMapSpeciesList1 = mapSpeciesList1.contains(taxon);
        if (inMapSpeciesList1) ++mapSpeciesListCount1;
      }
      if (mapSpeciesList2 != null) {
        inMapSpeciesList2 = mapSpeciesList2.contains(taxon);
        if (inMapSpeciesList2) ++mapSpeciesListCount2;
      }
      if (mapSpeciesList3 != null) {
        inMapSpeciesList3 = mapSpeciesList3.contains(taxon);
        if (inMapSpeciesList3) ++mapSpeciesListCount3;
      }
%>

<tr><td>
<%@include file="/common/statusDisplayTitle.jsp" %>
<% 
  if (noPassWorldantsSpeciesList.contains(taxon.getTaxonName())) { %>
  <a href="#<%= taxon.getTaxonName() %>"></a>
  <img src="<%= AntwebProps.getDomainApp() %>/image/redCheck.png" width="11" height="12" border="0" title="Name not in World Ants">
<% } 

   if (isInRefList) out.println("<b>");
%>

<%= Taxon.getPrettyTaxonName(taxon.getTaxonName()) %>&nbsp;

<% if (isInRefList) out.println("</b>"); %>

<a href="<%= AntwebProps.getDomainApp() %>/description.do?taxonName=<%= taxonName %>&project=allantwebants" target=new><img src="<%= AntwebProps.getDomainApp() %>/image/view_icon.png" height="13" width="13" title="View" /></a>

<% if (false && (Taxon.isMorpho(taxonName))) { %>  <!--  || (noPassWorldantsSpeciesList.contains(taxon.getTaxonName())) -->
<a href="<%= AntwebProps.getDomainApp() %>/speciesListMove.do?taxonName=<%= taxonName %>" target=new><img src="<%= AntwebProps.getDomainApp() %>/image/modifyPen.jpeg" height="15" width="15" title="Rename" /></a>
<% } %>

<%

   boolean hasYellowAnt = false;
   boolean hasRedDot = false;
   boolean hasRedX = false;
   if (("speciesListHistory".equals(refSpeciesListType)) && refSpeciesList != null && !refSpeciesList.contains(taxon)) {
     //outMapSpeciesList1 = true;
     out.println(redXTag); 
     hasRedX=true;  
   } 
%>

</td>

<td>
<%

//if (AntwebProps.isDevMode()) AntwebUtil.log("speciesListTool-body.jsp taxonName:" + taxon.getTaxonName() + " mapSpeciesList1:" + (mapSpeciesList1 != null) + " inMapSpeciesList1:" + inMapSpeciesList1 + " countryList:" + taxon.getCountryList() + " locName:" + Project.getLocalityName(mapSpeciesList1Name));
   if (mapSpeciesList1Name != null && !"null".equals(mapSpeciesList1Name) && mapSpeciesList1 != null) { %>
     <input value="<%= taxonName %>" type="checkbox" name="chosen1" <%= (inMapSpeciesList1 ? "checked" : "") %>>
     <% if (!inMapSpeciesList1) { out.println(redDotTag); hasRedDot=true; } else out.println(emptyTag); %>
     <% if (SpeciesListMgr.isDisplayYellowAnt(taxon, mapSpeciesList1Name)) {
            hasYellowAnt=true; 
            out.println(yellowAntTag); 
        } %> 
<% } %> 
</td>

<td>
<% 
   //AntwebUtil.log("speciesListTool-body mapSpeciesList2Name:" + mapSpeciesList2Name + " mapSpeciesList2:" + mapSpeciesList2);
   if (mapSpeciesList2Name != null && !"null".equals(mapSpeciesList2Name) && mapSpeciesList2 != null) { 
   %>
     <input value="<%= taxonName %>" type="checkbox" name="chosen2" <%= (inMapSpeciesList2 ? "checked" : "") %>>
     <% if (!inMapSpeciesList2) { out.println(redDotTag); hasRedDot=true; } else out.println(emptyTag); %>
     <% if (SpeciesListMgr.isDisplayYellowAnt(taxon, mapSpeciesList2Name)) { 
            hasYellowAnt=true; 
            out.println(yellowAntTag); 
        } %> 
<% } %> 
</td>

<td>
<% if (mapSpeciesList3Name != null && !"null".equals(mapSpeciesList3Name) && mapSpeciesList3 != null) { %>
     <input value="<%= taxonName %>" type="checkbox" name="chosen3" <%= (inMapSpeciesList3 ? "checked" : "") %>>
     <% if (!inMapSpeciesList3) { out.println(redDotTag); hasRedDot=true; } else out.println(emptyTag); %>
     <% if (SpeciesListMgr.isDisplayYellowAnt(taxon, mapSpeciesList3Name)) { 
            hasYellowAnt=true; 
            out.println(yellowAntTag); 
        } 
     %> 
<% } %> 
</td>
</tr>
 <% } // for %>
 
<!-- Output the totals -->
<tr>
<td>
<% if (mapSpeciesList1 != null) { %>
<hr> 
<% } %> 
</td><td>
<% if (mapSpeciesList1 != null) { %>
<hr> 
<% } %> 
</td><td>
<% if (mapSpeciesList2 != null) { %>
<hr> 
<% } %> 
</td><td>
<% if (mapSpeciesList3 != null) { %>
<hr> 
<% } %> 
</td>
</tr>

<tr>
<td>
<% if (mapSpeciesList1 != null) { %>
     <%= sumSpeciesList.size() %>
<% } %> 
</td><td>
<% if (mapSpeciesList1 != null) { %>
     <%= mapSpeciesListCount1 %>
<% } %> 
</td><td>
<% if (mapSpeciesList2 != null) { %>
     <%= mapSpeciesListCount2 %>
<% } %> 
</td><td>
<% if (mapSpeciesList3 != null) { %>
     <%= mapSpeciesListCount3 %>
<% } %> 
</td>

<tr><td></td><td></td><td>
   <br><br> <input type="submit" name="save" value="Save Changes"> 
</td><td></td><td></td></tr>
</tr> 

</html:form>
 
</table>

<%
if (AntwebProps.isDevOrStageMode() || LoginMgr.isDeveloper(accessLogin)) {
%> <br><b>Developer Info</b> <%
  if (toolProps.getRefSpeciesList() != null) {
    out.println("<br>refSpeciesList.size:" + toolProps.getRefSpeciesList().size());
    out.println("<br>params:" + toolProps.getRefSpeciesListParams());
  }
}  
%>


<!-- b>The following are not valid species according to the Bolton Catalog:</b>
<br>
< %
  // AntwebUtil.log("speciesListTool-body.jsp speciesList:" + noPassWorldantsSpeciesList);
  if (!noPassWorldantsSpeciesList.isEmpty()) {
    for (String taxonName : noPassWorldantsSpeciesList) {
      if (taxonName.contains(displaySubfamily)) out.println("<b>"); % >
     <br>    
      <img src="< %= AntwebProps.getDomainApp() % >/image/redCheck.png" width="11" height="12" border="0" title="Name not in World Ants">   
      < %=  Taxon.getPrettyTaxonName(taxonName) % >
      <a href="< %= AntwebProps.getDomainApp() % >/description.do?taxonName=< %= taxonName % >&project=allantwebants" target=new><img src="< %= AntwebProps.getDomainApp() % >/image/view_icon.png" height="13" width="13" title="View" /></a>
      < %
      if (taxonName.contains(displaySubfamily)) out.println("</b>");
    }
  }
% >
-->
