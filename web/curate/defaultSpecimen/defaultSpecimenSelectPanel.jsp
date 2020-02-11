<!-- defaultSpecimenSelectPanel.jsp 

Not currently in use.

-->

<%@ page import="org.calacademy.antweb.util.*" %>
<%
if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;

ArrayList<Taxon> specimenChildren = taxon.getChildren();
%>

<div id="page_data">

<form action="<%= AntwebProps.getDomainApp() %>/defaultSpecimen.do" method="POST">

<b>Choose</b> 

<select name="specimenCode">
    <% String isSelected = ""; %>
    <option value="none" name="specimenCode">None</option>

    <% for (Taxon theTaxon : specimenChildren) { 
         Specimen specChild = ((Specimen) theTaxon);
         //if (specChild.getHasImages() && ! DefaultSpecimenAction.isIn(specChild, favoriteImageList)) {
           String specimenCode = specChild.getCode();
           isSelected = "";
	       if (specimenCode.equals(taxon.getDefaultSpecimen())) isSelected = " selected";   // Deprecated code.
    %>
      <option value="<%= specChild.getCode() %>" name="specimenCode"<%= isSelected %>><%= specChild.getCode() %></option>
      <% // } %>
    <% } %>

</select>

<input type="hidden" name="taxonName" value="<%= defaultSpecimenTaxon %>" >
<input border="0" type="image" src="<%= AntwebProps.getDomainApp() %>/image/selectButton.png" height="24" name="done" value="">

as the representative image for taxon<b>: <%= Taxon.getPrettyTaxonName(defaultSpecimenTaxon) %></b>

or cancel this search: <a href="<%= AntwebProps.getDomainApp() %>/defaultSpecimen.do?command=cancel"><img border=0" src="<%= AntwebProps.getDomainApp() %>/image/grey_cancel.gif" height="24"></a>

</form>

<hr>
</div>
