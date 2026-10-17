<!-- ckEditorForm.jsp -->

<% 
  String newEditAction = Utility.stripParams(thisPageTarget, "editField");
  newEditAction = HttpUtil.trimDomainApp(newEditAction);
  AntwebUtil.log("info", "thisPageTarget:" + thisPageTarget + " newEditAction:" + newEditAction); 
%>
<%-- Not multipart: the token is a hidden field that editSafety.js refreshes before Save, and a refused
     save can show the text again (Csrf.denyEditorSave). --%>
<html:form method="POST" action='<%= org.calacademy.antweb.util.Csrf.stripParam(newEditAction) %>'><%= org.calacademy.antweb.util.Csrf.field(request) %>
<input type="hidden" name="editField" value="<%= Html.escAttr(editField) %>">
<input type="hidden" name="isSaveEditField" value="true">
<input type="hidden" name="rank" value="<%= Html.escAttr(taxon.getRank()) %>">
<input type="hidden" name="genus" value="<%= Html.escAttr(taxon.getGenus()) %>">
<input type="hidden" name="name" value="<%= Html.escAttr(taxon.getName()) %>">

<%@include file="/common/descEdit/ckEditorMinConfigForm.jsp" %>

</html:form>
