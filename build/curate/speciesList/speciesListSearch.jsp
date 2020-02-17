<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.curate.speciesList.*" %>


<jsp:useBean id="toolForm" scope="session" class="org.calacademy.antweb.curate.speciesList.SpeciesListToolForm" />
<jsp:setProperty name="toolForm" property="*" />

<%
    SpeciesListToolProps toolProps = (SpeciesListToolProps) session.getAttribute("speciesListToolProps");

    String refSpeciesListName = (String) session.getAttribute("refSpeciesListName");

    Login accessLogin = LoginMgr.getAccessLogin(request);
    ArrayList<SpeciesListable> speciesListList = accessLogin.getSpeciesListList();
        
    String mapSpeciesList1Name = toolProps.getMapSpeciesList1Name();    
    String mapSpeciesList2Name = toolProps.getMapSpeciesList2Name();   
    String mapSpeciesList3Name = toolProps.getMapSpeciesList3Name();
%> 

<div id="page_contents">
<h2>Add Species from Species List to Reference Taxa List</h2>
<div class="clear"></div>
<div class="page_divider"></div>

<div id="page_data">
<div id="overview_data">

<form id="refSpeciesListForm" action="<%= AntwebProps.getDomainApp() %>/speciesListTool.do">
      <input type="hidden" id="mapSpeciesList1Name" name="mapSpeciesList1Name" value="<%= mapSpeciesList1Name %>"/>
      <input type="hidden" id="mapSpeciesList2Name" name="mapSpeciesList2Name" value="<%= mapSpeciesList2Name %>"/>
      <input type="hidden" id="mapSpeciesList3Name" name="mapSpeciesList3Name" value="<%= mapSpeciesList3Name %>"/>
      <input type="hidden" id="refSpeciesListType" name="refSpeciesListType" value="speciesList"/>

      <!-- Though included in this form, this is printed above. -->
<!-- b>Choose Reference Taxa List:</b -->
      <select name="refSpeciesListName" id="refSpeciesList_select">
        <option id="refSpeciesListName" value="none" selected>None</option>

<% //for (String speciesListName : speciesListList) { 
     for (SpeciesListable speciesList : speciesListList) { 
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
        <option id="refSpeciesListName" value="<%= listKey %>" <% if (listKey.equals(refSpeciesListName)) out.print("selected"); %>><%= spacer + displayName %></option>
<% } %>
        //AntwebUtil.log("speciesListSearch-body.jsp speciesListNameIter:" + speciesListNameIter + " refSpeciesListName:" + refSpeciesListName);
     } %>
     
      </select>
      <br><input type="submit" name="go" value="go">      



</form>

</div>
</div>
