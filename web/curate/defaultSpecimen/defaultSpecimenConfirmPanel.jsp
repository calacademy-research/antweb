<%-- Static include of specimen-body.jsp, inside an if block it closes and reopens: the "select as default
     specimen" form (POST to defaultSpecimen.do) is never shown (if (false && ...)), but the second part shows and
     clears the session attribute "message" that DefaultSpecimenAction leaves after a redirect. --%><!-- defaultSpecimenConfirmPanel.jsp -->

<div id="page_data">

<form action="<%= domainApp %>/defaultSpecimen.do" method="POST"><%= org.calacademy.antweb.util.Csrf.field(request) %>

Select as the default specimen for taxon<b>: <%= org.calacademy.antweb.util.Html.esc(Taxon.getPrettyTaxonName(defaultSpecimenTaxon)) %></b>

<input type="hidden" name="taxonName" value="<%= org.calacademy.antweb.util.Html.escAttr(defaultSpecimenTaxon) %>" ><input type="hidden" name="command" value="post">
<input type="hidden" name="specimenCode" value="<%= org.calacademy.antweb.util.Html.escAttr(specimen.getCode()) %>" >
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
