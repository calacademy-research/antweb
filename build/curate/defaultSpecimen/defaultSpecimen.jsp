<!-- defaultSpecimen.jsp -->

<% if (LoginMgr.isCurator(request)) { %>

	<div id="default_specimen_view">
	  <a href="<%= AntwebProps.getDomainApp() %>/defaultSpecimen.do?taxonName=<%= taxon.getTaxonName() %>">Choose Image</a>
	</div>

<% } %>