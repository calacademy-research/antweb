<% 

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
