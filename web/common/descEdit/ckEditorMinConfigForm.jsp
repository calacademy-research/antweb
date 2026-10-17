<%-- The page-text editor: CKEditor 3 on a textarea, with the safety rules of DescEditSafety.java
     and editSafety.js (stored text fingerprint, ready flag, conflict view, browser drafts,
     login check and fresh form token before Save, "Clear this field").
     Included inside the edit forms (ckEditorMinConfig*Form.jsp); uses editField, thisPageTarget,
     accessLogin and request. --%>
<%
  {
    DescEditSafety.Loaded deLoad = DescEditSafety.loaded(request);
    DescEditSafety.Result deResult = DescEditSafety.result(request);
    String deCancel = Utility.stripParams(thisPageTarget, "editField");
    if (deLoad == null || !editField.equals(deLoad.field) || deLoad.failed) { %>
<p class="descEditLoadFailed" style="border:2px solid #b00000;background:#fff3f3;padding:8px;margin:8px 0"><b>The stored text of this field could not be loaded from the database, so the editor is not shown (an empty editor could be saved over the stored text). Please reload the page in a minute.</b></p>
<a href="<%= Html.escAttr(deCancel) %>">Back to the page</a>
<%  } else {
      String deText = DescEditSafety.editorText(deLoad, deResult);
      String deDraftKey = DescEditSafety.draftKey(request, accessLogin, editField);
      boolean deHadText = deLoad.content != null && !DescEditSafety.isBlank(deLoad.content);
      boolean deRefused = deResult != null && !deResult.saved;
      if (deRefused) { %>
<div id="descEditMessage" class="descEditRefused" style="border:2px solid #b00000;background:#fff3f3;padding:8px;margin:8px 0"><b><%= Html.esc(deResult.message) %></b></div>
<%      if (deResult.conflict) { %>
<div class="descEditConflict" style="border:1px solid #888;background:#f8f8f8;padding:8px;margin:8px 0">
  <b>The text stored now (saved by someone else after you opened the editor):</b>
  <div id="descEditStoredText" style="background:#fff;border:1px solid #ccc;padding:6px;margin:6px 0"><%= deLoad.content == null ? "<i>(empty: the field was cleared)</i>" : deLoad.content %></div>
  <details><summary>HTML source of the stored text (to copy)</summary>
  <textarea readonly style="width:100%;height:10em"><%= Html.esc(deLoad.content) %></textarea></details>
</div>
<%      }
      } %>
<div id="descEditSafetyBar" style="display:none;border:2px solid #b07000;background:#fff8e6;padding:8px;margin:8px 0"></div>
<input type="hidden" name="loadedHash" value="<%= Html.escAttr(deLoad.hash) %>">
<input type="hidden" name="editorReady" value="0">
<input type="hidden" name="clearField" value="0">
<!-- name is used by server. The stored HTML is escaped so the box holds exactly the stored text. -->
<textarea id="editor1" name="contents" rows="20" style="width:100%"><%= Html.esc(deText) %></textarea>
<script type="text/javascript" src="<%= AntwebProps.getDomainApp() %>/common/descEdit/editSafety.js"></script>
<script type="text/javascript">
    AntwebEditSafety.watch('editor1', {
        draftKey: '<%= Html.js(deDraftKey) %>',
        loadedHash: '<%= Html.js(deLoad.hash) %>',
        hadText: <%= deHadText %>,
        refused: <%= deRefused %>,
        fieldName: '<%= Html.js(DescEditSafety.prettyField(editField)) %>',
        sessionUrl: '<%= Html.js(AntwebProps.getDomainApp() + "/editSession.do") %>',
        loginUrl: '<%= Html.js(AntwebProps.getDomainApp() + "/login.do") %>'
    });
    if (window.CKEDITOR) {
    CKEDITOR.replace( 'editor1',
    {
		toolbar :
		[
	{ name: 'document', items : [ 'Source','-','NewPage','DocProps','Preview' ] },
	{ name: 'clipboard', items : [ 'Cut','Copy','Paste','PasteText','PasteFromWord','-','Undo','Redo' ] },
	{ name: 'tools', items : [ 'Maximize', 'ShowBlocks','-','About' ] },
	{ name: 'editing', items : [ 'Find','Replace','-','SelectAll','-','SpellChecker', 'Scayt' ] },
	{ name: 'links', items : [ 'Link','Unlink' ] },
	'/',
	{ name: 'basicstyles', items : [ 'Bold','Italic','Underline','Superscript','-','RemoveFormat' ] },
	{ name: 'paragraph', items : [ 'NumberedList','BulletedList','-','Outdent','Indent','-','Blockquote',
	'-','JustifyLeft','JustifyCenter','JustifyRight','JustifyBlock','-','BidiLtr','BidiRtl' ] },
	{ name: 'insert', items : [ 'Image','Table','HorizontalRule','SpecialChar'] }
	    ]
    , enterMode : CKEDITOR.ENTER_P
    , uiColor : '#9AB8F3'	        
    });
    }
</script>
			
<input border="0" type="submit" class="tool_label" style="float:none;" value="Save">
<% if (deLoad.content != null) { %>
<button type="button" class="tool_label descEditClear" style="float:none;<%= deRefused && deResult.offerClear ? "border:2px solid #b00000;" : "" %>">Clear this field</button>
<% } %>
<a href="<%= Html.escAttr(deCancel) %>">Cancel</a>
<%  }
  } %>

<!-- full set of options here:
    {
		toolbar :
		[
	{ name: 'document', items : [ 'Source','-','Save','NewPage','DocProps','Preview','Print','-','Templates' ] },
	{ name: 'clipboard', items : [ 'Cut','Copy','Paste','PasteText','PasteFromWord','-','Undo','Redo' ] },
	{ name: 'editing', items : [ 'Find','Replace','-','SelectAll','-','SpellChecker', 'Scayt' ] },
	'/',
	{ name: 'forms', items : [ 'Form', 'Checkbox', 'Radio', 'TextField', 'Textarea', 'Select', 'Button', 'ImageButton', 
        'HiddenField' ] },
	{ name: 'insert', items : [ 'Image','Flash','Table','HorizontalRule','Smiley','SpecialChar','PageBreak','Iframe' ] },
	'/',
	{ name: 'basicstyles', items : [ 'Bold','Italic','Underline','Strike','Subscript','Superscript','-','RemoveFormat' ] },
	{ name: 'paragraph', items : [ 'NumberedList','BulletedList','-','Outdent','Indent','-','Blockquote','CreateDiv',
	'-','JustifyLeft','JustifyCenter','JustifyRight','JustifyBlock','-','BidiLtr','BidiRtl' ] },
	{ name: 'links', items : [ 'Link','Unlink','Anchor' ] },
	'/',
	{ name: 'styles', items : [ 'Styles','Format','Font','FontSize' ] },
	{ name: 'colors', items : [ 'TextColor','BGColor' ] },
	{ name: 'tools', items : [ 'Maximize', 'ShowBlocks','-','About' ] }  
	    ]
    , enterMode : CKEDITOR.ENTER_P
    , uiColor : '#9AB8F3'	        
    });	  

Text wrapping option info: http://stackoverflow.com/questions/3339710/how-to-configure-ckeditor-to-not-wrap-content-in-p-block
  CKEDITOR.ENTER_P (default), CKEDITOR.ENTER_DIV, or CKEDITOR.ENTER_BR (not recommended);
  CreateDiv is left out of the toolbar on purpose: a save with a div is refused.
-->
