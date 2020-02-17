<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="java.util.*" %>

<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>


<jsp:useBean id="speciesListToolForm" scope="session" class="org.calacademy.antweb.curate.speciesList.SpeciesListToolForm" />
<jsp:setProperty name="speciesListToolForm" property="*" />

<%
    String refSpeciesListName = (String) session.getAttribute("refSpeciesListName");
    ArrayList<String> refListList = (ArrayList<String>) request.getAttribute("refListList");
        
    String mapSpeciesList1Name = (String) session.getAttribute("mapSpeciesList1Name");    
    String mapSpeciesList2Name = (String) session.getAttribute("mapSpeciesList2Name");   
    String mapSpeciesList3Name = (String) session.getAttribute("mapSpeciesList3Name");   
    String mapSpeciesList4Name = (String) session.getAttribute("mapSpeciesList4Name");   
    String mapSpeciesList5Name = (String) session.getAttribute("mapSpeciesList5Name"); 
%>

<div id="page_contents">
<h2>Add Species from Specimen List to Reference Taxa List</h2>
<div class="clear"></div>
<div class="page_divider"></div>

<div id="page_data">
<div id="overview_data">
<form id="refSpeciesListForm" action="<%= AntwebProps.getDomainApp() %>/speciesListTool.do">
      <input type="hidden" id="mapSpeciesList1Name" name="mapSpeciesList1Name" value="<%= mapSpeciesList1Name %>"/>
      <input type="hidden" id="mapSpeciesList2Name" name="mapSpeciesList2Name" value="<%= mapSpeciesList2Name %>"/>
      <input type="hidden" id="mapSpeciesList3Name" name="mapSpeciesList3Name" value="<%= mapSpeciesList3Name %>"/>
      <input type="hidden" id="mapSpeciesList4Name" name="mapSpeciesList4Name" value="<%= mapSpeciesList4Name %>"/>
      <input type="hidden" id="mapSpeciesList5Name" name="mapSpeciesList5Name" value="<%= mapSpeciesList5Name %>"/>

      <input type="hidden" id="action" name="action" value="changeRefSpeciesList"/>

      <!-- Though included in this form, this is printed above. -->
<!-- b>Choose Reference Taxa List:</b -->
      <select name="refSpeciesListName" id="refSpeciesList_select">
        <option id="refSpeciesListName" value="none" selected>None</option>

  <% for (String refSpeciesListNameIter : refListList) { %>        
        <option id="refSpeciesListName" value="<%= refSpeciesListNameIter %>" <% if (refSpeciesListNameIter.equals(refSpeciesListName)) out.print("selected"); %>><%= Project.getPrettyName(refSpeciesListNameIter) %></option>
  <% 
        //AntwebUtil.log("speciesListTool-body.jsp refSpeciesListNameIter:" + refSpeciesListNameIter + " refSpeciesListName:" + refSpeciesListName);
     } %>
     
      </select>
      <br><input type="submit" name="go" value="go">      

</form>


</div>
</div>
