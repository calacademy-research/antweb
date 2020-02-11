<!-- statusDisplayTitle.jsp -->
<% 
if (true) {  // This allows multiple includes of statusDisplay on a single page without duplicated thisStatus declarations.

  // See statusDisplay.jsp  duplicated code
  // See also: web/common/statusDisplayPage.jsp

  // Unfortunate to duplicate this code.  More complicated because of generated pages, and because of titles vs children usage.
  // I am sure we could do better but this is it for now.
  
  //if (AntwebProps.isDevMode()) AntwebUtil.log("statusDisplay.jsp status:" + thisChild.getStatus());
  String thisStatus = taxon.getStatus();
  String statusNote = "";
%> 
  <%@include file="/common/statusDisplayCore.jsp" %>
  
<% } %>
