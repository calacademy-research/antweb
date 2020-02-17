<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<% String domainApp = (new Utility()).getDomainApp(); %>


<%@include file="/curate/adminCheck.jsp" %>


<div class="msg in_admin">
    <div class="msg_alert"></div>
    <div class="msg_type">NOTE</div>
    <div class="msg_pipe"></div>
    <div class="msg_copy">
You may use HTML in your page copy.
<b>Images must be already on the AntWeb server.</b> <br />
To Upload A File, go to the <a href="/curate.do" target="new">File Upload Page</a>
    </div>
</div>


<div class=admin_left>
<h1>Adding a Subhomepage</h1>
<hr></hr>

<form action=editsubpreview.jsp>
<p><h3>Biogeographic Region:</h3>
<select class=input_450>
<option>--Choose the Biogeographic Area for this Project--
<option>Nearctic
<option>Malagasy
</select>

<p><h3>Project Name:</h3>
<input type=text class=input_450 value="e.g. californiaants">

<p><h3>Project Title Phrase:</h3>
<input type=text class=input_450 value="e.g. California">

<p><h3>Project Extent:</h3>
<input type=text class=input_450 value="e.g. 10 10 10 10">

<p><h3>Locality:</h3><br>
<input type=text class=input_450 value="e.g. adm1='california'">

<p><h3>Document Name:</h3><br>
<input type=text class=input_450 value="e.g. california.jsp">

<p><h3>Map Image:</h3><br>
<input type=text class=input_450 value="">

<p><h3>Author:</h3>
<input name="author" type=text class=input_450 value="">
<p>
<h3>Page Copy:</h3>
<textarea name="contents">

</textarea>

<p><h3>Images:</h3>
<b>Image One:</b><br>
<input name="specimenImage1" type=file class=input_450 value=""><br>
<b>Image One Links to:</b><br>
<input name="specimenImage1Link" type=text class=input_450 value=""><br>

<p><b>Image Two:</b><br>
<input name="specimenImage2" type=file class=input_450 value=""><br>
<b>Image Two Links to:</b><br>
<input name="specimenImage2Link" type=text class=input_450 value=""><br>

<p><b>Image Three:</b><br>
<input name="specimenImage3" type=file class=input_450 value=""><br>
<b>Image Three Links to:</b><br>
<input name="specimenImage3Link" type=text class=input_450 value=""><br>

<p><h3>Author Image:</h3>
<input name="authorImage" type=file class=input_450 value=""><br>

<p><h3>Author Bio:</h3>
<textarea name="authorBio" class=biotextarea>
</textarea>

<br><br>

</div>
<div class=admin_right>

</div> 

<div class="clear"></div>
<br />
<br />
<div class="msg">
    <div class="msg_actions" align="center">
<input border="0" type="image" src="<%= domainApp %>/image/orange_preview.gif" width="137" height="
36" value="Preview"><a href="<%= domainApp %>/curate.do"><img border=0" src="<%= domainApp %>/image/grey_cancel.gif" width="123" height="36"</a>
</form>
    </div>
</div>
</form>

