<% if (true) {  // This allows multiple includes of statusDisplay on a single page without duplicated thisStatus declarations.

    // See also: web/common/statusDisplayPage.jsp for documentation
  
    //if (AntwebProps.isDevMode()) AntwebUtil.log("statusDisplay.jsp status:" + thisChild.getStatus());

    String thisStatus = thisChild.getStatus();
    String statusNote = thisChild.getTaxonSet().getSourceStr();
%>
  
  <%@include file="/common/statusDisplayCore.jsp" %>

<% } %>
