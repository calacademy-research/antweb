<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import = "org.calacademy.antweb.Group" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<%  String domainApp = (new Utility()).getDomainApp(); %>


<%@include file="/curate/curatorCheck.jsp" %>


<div class="msg in_admin">
    <div class="msg_alert"></div>
    <div class="msg_type">NOTE</div>
    <div class="msg_pipe"></div>
    <div class="msg_copy">
You may use HTML in your page copy.
<b>Images must be already on the AntWeb server.</b> <br />
To Upload A File, go to the <a href="<%= domainApp %>/curate.do" target="new">Antweb Curator Tools</a>
    </div>
</div>

<div class=admin_left>
<h1>Editing the Homepage</h1>
<hr></hr>

<html:form action="previewHomePage.do" method="POST">
<h3>Headline:</h3>
<html:text styleClass="input_550" property="mod1headline"/>
<p>
<h3>Module Copy:</h3>
<html:textarea property="mod1text" styleClass="biotextarea550"/>

<p><h3>Images:</h3>
NOTE: These images must be 499x398.
<b>Image One:</b><br>
<html:text styleClass="input_550" property="mod1image1image"/><br>
<b>Image One Links to:</b><br>
<html:text styleClass="input_550" property="mod1image1link"/><br>

<p><b>Image Two:</b><br>
<html:text styleClass="input_550" property="mod1image2image"/><br>
<b>Image Two Links to:</b><br>
<html:text styleClass="input_550" property="mod1image2link"/><br>

<p><b>Image Three:</b><br>
<html:text styleClass="input_550" property="mod1image3image"/><br>
<b>Image Three Links to:</b><br>
<html:text styleClass="input_550" property="mod1image3link"/><br>

<br><br>

<h1>Editing Homepage Module 2</h1>
<hr></hr>

<h3>Headline:</h3>
<html:text styleClass="input_550" property="mod2headline"/>
<p>
<h3>Module Copy:</h3>
<html:textarea property="mod2text" styleClass="biotextarea550"/>

<p><h3>Images:</h3>
<b>Image One:</b><br>
<html:text styleClass="input_550" property="mod2image1image"/><br>
<b>Image One Links to:</b><br>
<html:text styleClass="input_550" property="mod2image1link"/><br>

<br><br>

<h1>Editing Homepage Module 3</h1>
<hr></hr>

<h3>Headline:</h3>
<html:text styleClass="input_550" property="mod3headline"/>
<p>
<h3>Module Copy:</h3>
<html:textarea property="mod3text" styleClass="biotextarea550"/>


<br><br>

<h1>Editing Homepage Module 4</h1>
<hr></hr>

<h3>Headline:</h3>
<html:text styleClass="input_550" property="mod4headline"/>
<p>
<h3>Module Copy:</h3>
<html:textarea property="mod4text" styleClass="biotextarea550"/>


<br><br>

<h1>Editing Homepage Module 5</h1>
<hr></hr>

<h3>Headline:</h3>
<html:text styleClass="input_550" property="mod5headline"/>
<p>
<h3>Module Copy:</h3>
<html:textarea property="mod5text" styleClass="biotextarea550" />


<br><br>

<h1>Editing Homepage Module 6</h1>
<hr></hr>

<h3>Headline:</h3>
<html:text styleClass="input_550" property="mod6headline"/>
<p>
<h3>Module Copy:</h3>
<html:textarea property="mod6text" styleClass="biotextarea550"/>

</div>
<div class=admin_right>

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


</html:form>

