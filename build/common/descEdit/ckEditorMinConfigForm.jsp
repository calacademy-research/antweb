<!-- was with yahoo:  textarea rows="20" cols="80" name="contents" id="contents" -->
<!-- name is used by server. -->
<textarea id="editor1" name="contents"><%= ((desc.get(editField)==null) ? guiDefaultContent : desc.get(editField)) %></textarea>
<script type="text/javascript">
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
	{ name: 'paragraph', items : [ 'NumberedList','BulletedList','-','Outdent','Indent','-','Blockquote','CreateDiv',
	'-','JustifyLeft','JustifyCenter','JustifyRight','JustifyBlock','-','BidiLtr','BidiRtl' ] },
	{ name: 'insert', items : [ 'Image','Table','HorizontalRule','SpecialChar'] }
	    ]
    , enterMode : CKEDITOR.ENTER_P
    , uiColor : '#9AB8F3'	        
    });
</script>
			
<input border="0" type="submit" class="tool_label" style="float:none;" value="Save">  <a href="<%= Utility.stripParams(thisPageTarget, "editField") %>">Cancel</a>



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
-->
