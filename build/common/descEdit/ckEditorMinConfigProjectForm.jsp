<!-- ckEditorForm.jsp -->

<% 
 String newEditAction = Utility.stripParams(thisPageTarget, "editField"); 
 newEditAction = HttpUtil.trimDomainApp(newEditAction);

 AntwebUtil.log("info", "ckEditorMinConfigProjectForm.jsp thisPageTarget:" + thisPageTarget + " newEditAction:" + newEditAction + " editField:" + editField); %>
<html:form method="POST" action="<%= newEditAction %>" enctype="multipart/form-data">
<input type="hidden" name="editField" value="<%= editField %>">
<input type="hidden" name="isSaveEditField" value="true">

<%@include file="/common/descEdit/ckEditorMinConfigForm.jsp" %>

</html:form>
