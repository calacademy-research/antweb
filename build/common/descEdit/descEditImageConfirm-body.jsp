
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.upload.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.Taxon" %>

<div class="left">

<br>
<H2>Image Upload Confirmation Page</H2>
<br>
<%
//String newEditAction = "/description.do?genus=gracilidris&name=&rank=species";
String thisPageTarget = "thisPageTarget";

String imageUrl = (String) request.getAttribute("imageUrl");
String tag = "&lt;img src=\"" + imageUrl + "\"&gt;";

DescEditImageUploadForm descForm = (DescEditImageUploadForm) request.getAttribute("descEditImageUploadForm");
%>

Your uploaded file is here: <a href="<%= imageUrl %>"><%= imageUrl %></a>
<br>You may embed this image in a web page with this html tag: <pre><%= tag %></pre>
<br>

<%
  String newEditAction = descForm.getTarget();
  newEditAction = HttpUtil.trimDomainApp(newEditAction);
  AntwebUtil.log("info", "descEditImageConfirm-body.jsp newEditAction:" + newEditAction); 
%>
<html:form method="POST" action="<%= newEditAction %>" enctype="multipart/form-data">
<input type="hidden" name="editField" value="<%= descForm.getEditField() %>">
<input type="hidden" name="isSaveEditField" value="true">
<input type="hidden" name="name" value="<%= descForm.getName() %>">
<input type="hidden" name="contents" value="<%= descForm.getContents() %>">
<input type="hidden" name="imageUrl" value="<%= imageUrl %>">

<input border="0" type="image" src="<%= AntwebProps.getDomainApp() %>/image/orange_done.gif" width="98" height="36" value="Save">
<!-- a href="<%= Utility.stripParams(thisPageTarget, "editField") %>"><img border="0" src="image/grey_cancel.gif" width="123" height="36"></a -->
</html:form>

<br><br>

<img class="taxon_page_img" src="<%= imageUrl %>">

</div>
<div class="right">
</div>
