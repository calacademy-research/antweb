<%-- The type-specimen mark of a list row (has_type_status_icon.png when thisChild.getIsType(), else a spacer).
     Statically included by taxonReportBody.jsp. --%><% 
  //A.log("isType.jsp isType:" + thisChild.getIsType());
  if (thisChild.getIsType()) { %>
  <img src="<%= AntwebProps.getDomainApp() %>/image/has_type_status_icon.png" width="11" height="12" border="0" title="Has type specimen">
<% } else { %>
  <img src="<%= AntwebProps.getDomainApp() %>/image/1x1.gif" width="11" height="12" border="0">
<% } %>
