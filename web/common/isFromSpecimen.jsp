<%-- The yellow ant mark of a list row (static include of taxonReportBody.jsp, variable thisChild): shown when the
     name is not on the place's species list and comes from specimens collected there (the views Vetted and All
     specimen names add such names, 2026-11-02), else a blank spacer of the same size. --%><% 
  //if (AntwebProps.isDevMode()) AntwebUtil.log("isType.jsp isType:" + thisChild.getIsType());
  if (!thisChild.getTaxonSet().exists()) { %>
  <img src="<%= AntwebProps.getDomainApp() %>/image/yellow_ant.png" width="11" height="12" border="0" title="Not on the place's list: from specimens collected here.">
<% } else { %>
  <img src="<%= AntwebProps.getDomainApp() %>/image/1x1.gif" width="11" height="12" border="0">
<% } %>
