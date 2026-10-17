<!-- ckEditorForm.jsp -->

<% String newEditAction = Utility.stripParams(thisPageTarget, "editField"); %>
<% 
   newEditAction = HttpUtil.trimDomainApp(newEditAction);
   AntwebUtil.log("info", "thisPageTarget:" + thisPageTarget + " newEditAction:" + newEditAction); 
%>
<html:form method="POST" action='<%= org.calacademy.antweb.util.Csrf.action(request, (newEditAction)) %>' enctype="multipart/form-data"><%= org.calacademy.antweb.util.Csrf.field(request) %>
<input type="hidden" name="editField" value="<%= editField %>">
<input type="hidden" name="isSaveEditField" value="true">

<%@include file="/common/descEdit/ckEditorMinConfigForm.jsp" %>

</html:form>

