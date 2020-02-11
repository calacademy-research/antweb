<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.Login" %>
<%@ page import="org.calacademy.antweb.AncFile" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@include file="/curate/curatorCheck.jsp" %>

<%
    Login accessLogin = LoginMgr.getAccessLogin(request);
    AncFile ancFile = (AncFile) request.getSession().getAttribute("ancFile");

    ArrayList<String> projList = accessLogin.getProjectNames();
    String ancTitle = "";
    if (ancFile.getTitle() != null) {
        ancTitle = ancFile.getTitle();
    } else {
        ancTitle = "";
    }

    String dirFileStr = (ancFile.getDirectory() != null) ? " in Directory " + ancFile.getDirectory() : "";
%>

<div class="in_admin"><h1>Creating a New Ancillary Page<%= dirFileStr %></h1></div>
<div class="admin_left">

<% String domainApp = (new Utility()).getDomainApp();  %>

<form method="POST" action="<%= domainApp %>/newAncPage.do" enctype="multipart/form-data">

<h2>File Name (no spaces or punctuation please)</h2>
<input type="text" name="fileName">
<html:messages id="message" message="true">
<font color="red"><b>
<bean:write name="message"/><br>
</b></font>
</html:messages>

<h2>Web Page Title</h2>
<input type="text" name="title" value="<%= ancTitle %>">

<h2>Content</h2>

<% //if (LoginMgr.isAdmin(accessLogin)) { %>
<!-- if id = editor1 use ckEditor.  If id = contents use Yahoo editor -->
<textarea rows="20" cols="80" name="contents" id="editor1">
<%= ((ancFile.getContents()==null)?"Add your content here.":ancFile.getContents()) %>
</textarea>
<%@ include file="/common/descEdit/ckEditorInclude.jsp" %>	

</div>

<div class="admin_right">
</div>

<div class="clear"></div>
<br />
<br />
<div class="msg in_admin">
    <div class="msg_actions" align="center">
<input border="0" type="image" src="<%= domainApp %>/image/orange_preview.gif" width="137" height="36" value="Preview">
<a href="<%= domainApp %>/curate.do"><img border=0" src="<%= domainApp %>/image/grey_cancel.gif" width="123" height="36"</a>
</form>
    </div>
</div>

</form>


