<!-- ckEditorForm.jsp -->

<% String newEditAction = Utility.stripParams(thisPageTarget, "editField"); %>
<% AntwebUtil.log("info", "ckEditorForm.jsp thisPageTarget:" + thisPageTarget + " newEditAction:" + newEditAction + " editField:" + editField); 
   newEditAction = HttpUtil.trimDomainApp(newEditAction);
%>
<html:form method="POST" action="<%= newEditAction %>" enctype="multipart/form-data">
<input type="hidden" name="editField" value="<%= editField %>">
<input type="hidden" name="isSaveEditField" value="true">
<input type="hidden" name="rank" value="<%= taxon.getRank() %>">
<input type="hidden" name="genus" value="<%= taxon.getGenus() %>">
<input type="hidden" name="name" value="<%= taxon.getName() %>">

<!-- was with yahoo:  textarea rows="20" cols="80" name="contents" id="contents" -->
<!-- name is used by server. -->
<textarea id="editor1" name="contents"><%= ((desc.get(editField)==null) ? guiDefaultContent : desc.get(editField)) %></textarea>
<script type="text/javascript">
    CKEDITOR.replace( 'editor1', 
    {
      uiColor : '#9AB8F3'	    
    });
</script>
			
<input border="0" type="image" src="image/orange_done.gif" width="98" height="36" value="Save">
<a href="<%= Utility.stripParams(thisPageTarget, "editField") %>"><img border="0" src="image/grey_cancel.gif" width="123" height="36"></a>
</html:form>
