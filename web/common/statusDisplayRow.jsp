<%-- The status icon of a search result row: thisStatus from the including page's row (ResultItem), then
     common/statusDisplayCore.jsp. Statically included by search/advancedSearchResultsByTaxon-body.jsp. --%><% 

if (true) {  // This allows multiple includes of statusDisplay on a single page without duplicated thisStatus declarations.

  // See titleStatusDisplay.jsp  duplicated code
  // See also: web/common/statusDisplayPage.jsp
  
  //if (AntwebProps.isDevMode()) AntwebUtil.log("statusDisplay.jsp status:" + thisChild.getStatus());

  String thisStatus = ((ResultItem) row).getStatus();
  String statusNote = "Source = statusDisplayRow.jsp";
%>
  
  <%@include file="/common/statusDisplayCore.jsp" %>

<% 
} %>
