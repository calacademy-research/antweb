<%-- editorScripts.jsp: the page-text editor's scripts, for pages that show an edit form
     (statically included by taxonPage-body, specimen-body, overview-body and project-body when a
     field is open for editing). TinyMCE 8 with the shared AntWeb settings, or CKEditor 3 when the
     switch says so (util/PageEditor.java: etc/editor.properties pageEditor=ckeditor, or
     ?pageEditor=ckeditor on the page). --%>
<% if (org.calacademy.antweb.util.PageEditor.isTinyMce(request)) { %>
<script type="text/javascript" src="<%= org.calacademy.antweb.util.AntwebProps.getDomainApp() %><%= org.calacademy.antweb.util.Asset.url(application, org.calacademy.antweb.util.PageEditor.TINYMCE_JS) %>"></script>
<script type="text/javascript" src="<%= org.calacademy.antweb.util.AntwebProps.getDomainApp() %><%= org.calacademy.antweb.util.Asset.url(application, org.calacademy.antweb.util.PageEditor.SETUP_JS) %>"></script>
<% } else { %>
<script type="text/javascript" src="<%= org.calacademy.antweb.util.AntwebProps.getDomainApp() %><%= org.calacademy.antweb.util.Asset.url(application, org.calacademy.antweb.util.PageEditor.CKEDITOR_JS) %>"></script>
<% } %>
