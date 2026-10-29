<%-- "Choose Image" link to defaultSpecimen.do for curators (variable taxon). Not used: the include in
     imagePage-body.jsp is commented out. --%><!-- defaultSpecimen.jsp -->

<% if (LoginMgr.isCurator(request)) { %>

	<div id="default_specimen_view">
	  <a href="<%= AntwebProps.getDomainApp() %>/defaultSpecimen.do?taxonName=<%= org.calacademy.antweb.util.Html.escAttr(org.calacademy.antweb.util.Html.url(taxon.getTaxonName())) %>">Choose Image</a>
	</div>

<% } %>