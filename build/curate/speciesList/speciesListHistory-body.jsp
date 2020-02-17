<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.curate.speciesList.*" %>

<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
 
<%@ page import="org.calacademy.antweb.curate.speciesList.SpeciesListToolForm" %>

<jsp:useBean id="historyForm" scope="request" class="org.calacademy.antweb.curate.speciesList.SpeciesListToolForm" />
<jsp:setProperty name="historyForm" property="*" />

<% 
/*
  All properties are in the form except for
  session
    accessGroup
  request
    message
    curatorList
*/

  Login accessLogin = LoginMgr.getAccessGroup(request);

  ArrayList<SpeciesListable> speciesListList = accessLogin.getSpeciesListList();

  ArrayList<Login> curatorList = (ArrayList<Login>) request.getAttribute("curatorList");
  String speciesListName = historyForm.getSpeciesListName();   
 
  boolean isProject = Project.isProjectName(speciesListName);
  //if (AntwebProps.isDevMode()) AntwebUtil.log("speciesListHistory-body.jsp speciesListName:" + speciesListName);
%>

<a href="<%= AntwebProps.getDomainApp() %>/speciesListTool.do"><< Back to Species List Tool</a>

<h1>Species List History </h1>
<br>
<br>
    <form id="historyForm" action="<%= AntwebProps.getDomainApp() %>/speciesListHistory.do">    
      Species List:<select name="speciesListName" id="SpeciesList_select">
        <option id="speciesListName" value="none" <%= (speciesListName == null ? " selected" : "") %>>None</option>      
 <% for (SpeciesListable speciesList : speciesListList) { 
      String listKey = speciesList.getKey();
      String listName = speciesList.getName();
      String prettySpeciesListName = SpeciesListMgr.getPrettyName(listKey);  
       String value = null;
       if (speciesList.getIsUseChildren()) value = "";
       value = listKey;
       String spacer = "";
	   if (SpeciesListable.ADM1.equals(speciesList.getType())) {
	     spacer = "&nbsp;&nbsp;&nbsp;";
	   } 
	   int maxStrLength = 30;
       String displayName = prettySpeciesListName;
       if (prettySpeciesListName != null && prettySpeciesListName.length() > maxStrLength) displayName = prettySpeciesListName.substring(0, maxStrLength) + "...";      
      %>        
        <option id="speciesListName" value="<%= value %>" <% if (listKey.equals(speciesListName)) out.print("selected"); %>><%= spacer + prettySpeciesListName %></option> 
 <% } %>
      </select>


<br>
 <% 
    String projLogIdVal = "";
    if (historyForm.getProjLogId() > 0) projLogIdVal = "" + historyForm.getProjLogId();
    String geoLogIdVal = "";
    if (historyForm.getGeoLogId() > 0) geoLogIdVal = "" + historyForm.getGeoLogId();
    if (Project.isProjectName(speciesListName)) { %>
<br>Log Id less than or equal to:<input type="text" name="projLogId" id="projLogId" size=5 value="<%= projLogIdVal %>"/>
 <% } else { %>
<br>Log Id less than or equal to:<input type="text" name="geoLogId" id="geoLogId" size=5 value="<%= geoLogIdVal %>"/>
 <% } %>
      <br><br><input type="submit" name="go" value="go">
    </form>
<br><br>
<hr>
    <%

//A.log("speciesListHistory-body.jsp isProject:" + isProject);
    
    if (isProject) {
		ArrayList<ProjTaxonLog> projTaxonLogs = (ArrayList<ProjTaxonLog>) request.getAttribute("projTaxonLogs");
		ArrayList<ProjTaxonLogDetail> masterLog = projTaxonLogs.get(0).getDetails();

		if (projTaxonLogs.size() == 1) {
		  String message = SpeciesListMgr.getPrettyName(speciesListName) + " does not have an edit history";      
		  if (historyForm.getProjLogId() > 0) message += ", for projLogId <= " + historyForm.getProjLogId();
		  message += ".";
		  out.println("<br><br>" + message);
		  return;
		}
	%> <table> <%
		// Print out headings
		for (ProjTaxonLog projTaxonLog : projTaxonLogs) {
		  %> <th><b><%= projTaxonLog.getHeading() %></b></th> <%      
		} %>
		</tr><tr>
		<%
		int col = 0;
		for (ProjTaxonLog projTaxonLog : projTaxonLogs) {
		  ++col;
		  if (col == 1) {
		  %> <th>Add to reference list:</th> <%
		  } else {
		  %> <th>
			   <% if (!projTaxonLog.getIsCurrent()) { %>
				 <a href="<%= AntwebProps.getDomainApp() %>/speciesListTool.do?refSpeciesListType=speciesListHistory&projLogId=<%= projTaxonLog.getLogId() %>">Add</a>
			   <% } %>
			   <br><br><hr>
			 </th> <%
		  }
		} %>
		</tr>
		<br> <%
		int row = 0;
		for (ProjTaxonLogDetail masterDetail : masterLog) {
		  out.println("<tr><td>" + Taxon.getPrettyTaxonName(masterDetail.getTaxonName()) + "</td>");
		  for (int i=1 ; i < projTaxonLogs.size() ; ++i) {
			boolean contains = projTaxonLogs.get(i).getDetails().contains(new ProjTaxonLogDetail(masterDetail.getTaxonName(), speciesListName)) ;
			if (contains) {
			  %> <td><input type="checkbox" <%= (true ? "checked" : "") %> disabled></td> <%
			} else {
			  %> <td><input type="checkbox" <%= (false ? "checked" : "") %> disabled></td> <%
			}
		  }
		  %> </tr> <%
		  ++row;
		} %>
     <tr><td><%= row %></td><td>2</td><td>3</td></tr>
     <%
    } else {  // It is a Geolocale

		ArrayList<GeolocaleTaxonLog> geolocaleTaxonLogs = (ArrayList<GeolocaleTaxonLog>) request.getAttribute("geolocaleTaxonLogs");
		
		if (geolocaleTaxonLogs == null || geolocaleTaxonLogs.get(0) == null) return;
        
//A.log("speciesListHistory-body.jsp geolocaleTaxonLogs:" + geolocaleTaxonLogs);

		if (geolocaleTaxonLogs.size() == 1) {
		  String message = speciesListName + " does not have an edit history";      
		  if (historyForm.getGeoLogId() > 0) message += ", for geoLogId <= " + historyForm.getGeoLogId();
		  message += ".";
		  out.println("<br><br>" + message);
		  return;
		}

		ArrayList<GeolocaleTaxonLogDetail> masterLog = geolocaleTaxonLogs.get(0).getDetails();

	%> <table> <%
		// Print out headings
		for (GeolocaleTaxonLog geolocaleTaxonLog : geolocaleTaxonLogs) {
		  %> <th><b><%= geolocaleTaxonLog.getHeading() %></b></th> <%      
		} %>
		</tr><tr>
		<%
		int col = 0;
		for (GeolocaleTaxonLog geolocaleTaxonLog : geolocaleTaxonLogs) {

//if (AntwebProps.isDevMode()) AntwebUtil.log("speciesListHistory-body.jsp geolocaleTaxonLogs:" + geolocaleTaxonLogs);

		  ++col;
		  if (col == 1) {
		  %> <th>Add to reference list:</th> <%
		  } else {
		  %> <th>
			   <% if (!geolocaleTaxonLog.getIsCurrent()) { %>
				 <a href="<%= AntwebProps.getDomainApp() %>/speciesListTool.do?refSpeciesListType=speciesListHistory&geoLogId=<%= geolocaleTaxonLog.getLogId() %>">Add</a>
			   <% } %>
			   <br><br><hr>
			 </th> <%
		  }
		} %>
		</tr>
		<br> <%
		int row = 0;
        int[] colCounts = new int[geolocaleTaxonLogs.size() + 1];

		for (GeolocaleTaxonLogDetail masterDetail : masterLog) {

		  out.println("<tr><td>" + Taxon.getPrettyTaxonName(masterDetail.getTaxonName()) + "</td>");
		  for (int i=1 ; i < geolocaleTaxonLogs.size() ; ++i) {
	        int geolocaleId = GeolocaleMgr.getGeolocaleId(speciesListName);
			boolean contains = geolocaleTaxonLogs.get(i).getDetails().contains(new GeolocaleTaxonLogDetail(masterDetail.getTaxonName(), geolocaleId)) ;

			if (contains) {
			  colCounts[i] = colCounts[i] + 1;
			  %> <td><input type="checkbox" <%= (true ? "checked" : "") %> disabled></td> <%
//if (AntwebProps.isDevMode()) AntwebUtil.log("speciesListHistory-body.jsp contains:" + contains + " masterLogs:" + masterDetail.getTaxonName() + " geolocaleId:" + geolocaleId);
			} else {
			  %> <td><input type="checkbox" <%= (false ? "checked" : "") %> disabled></td> <%
			}
		  }
		  %> </tr> <%
		  ++row;
		} %>
     <tr>
       <td><hr><%= row %></td>   
      <%
     for (int i = 1 ; i < geolocaleTaxonLogs.size() ; ++i) { %>
       <td><hr><%= colCounts[i] %></td>
  <% } %>
     </tr>
    <%		
    }

   %> </table>
