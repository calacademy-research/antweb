<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.Login" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.AncFile" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<jsp:useBean id="ancFile" scope="session" class="org.calacademy.antweb.AncFile" />
<jsp:setProperty name="ancFile" property="*" />


<%@include file="/curate/curatorCheck.jsp" %>

<%	
	Login accessLogin = LoginMgr.getAccessLogin(request);
	String groupName = accessLogin.getGroup().getName();
	ArrayList<String> projList = accessLogin.getProjectNames();
%>

<div class="in_admin"><h1>Editing Ancillary Page  <%= ancFile.getTitle() %></h1></div>

<div class="admin_left">

<form action="<%= domainApp %>/ancPagePreview.do" method="POST" enctype="multipart/form-data">

<h2>File Name: <%= ancFile.getFileName() %></h2>

<h2>Web Page Title</h2>
<input type="text" name="title" value="<%= ancFile.getTitle() %>">

<h2>Content</h2>
<% //if (LoginMgr.isAdmin(accessGroup)) { %>
<!-- if id = editor1 use ckEditor.  If id = contents use Yahoo editor -->
<textarea rows="20" cols="80" name="contents" id="editor1">
<%= ancFile.getContents() %>
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
    </div>
</div>

</form>
