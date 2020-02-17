<!-- defaultSpecimenConfirmPanel.jsp -->

<div id="page_data">

<form action="<%= domainApp %>/defaultSpecimen.do" method="POST">

Select as the default specimen for taxon<b>: <%= Taxon.getPrettyTaxonName(defaultSpecimenTaxon) %></b>

<input type="hidden" name="taxonName" value="<%= defaultSpecimenTaxon %>" >
<input type="hidden" name="specimenCode" value="<%= specimen.getCode() %>" >
<input border="0" type="image" src="<%= domainApp %>/image/selectButton.png" height="24" name="done" value="">

or cancel this search: <a href="<%= domainApp %>/defaultSpecimen.do?command=cancel"><img border=0" src="<%= domainApp %>/image/grey_cancel.gif" height="24"></a>

</form>
<hr>
</div
 <% } 
    String message = (String) session.getAttribute("message");
    session.setAttribute("message", null);
    if (message != null) { %> 
<div id="page_data">
<%= message %>
<hr>
</div
