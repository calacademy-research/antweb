<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>



<!-- Body of Text Begins -->

<% String domainApp = (new org.calacademy.antweb.Utility()).getDomainApp(); %>
<div class="admin_left">

    <div class="admin_action_module">
        <div class="admin_action_item"><h1>Forgot Your Password?</h1></div>
<html:messages id="message" message="true">
<font color="red"><b>
<bean:write name="message"/><br>
</b></font>
</html:messages>
<br><br><br><br>
If you've forgotton your password, please <a href="mailto:antweb@calacademy.org?subject=Password Recovery">send an email to us</a> and we'll get back to you...

<p>If you're not currently a curator, would you like to join us?  Curators can edit the home page of the geographic section they curate, upload specimen data and authority files, and control a number of other aspects of their project. Learn how to <a href=<%= domainApp %>/documentation.do> submit data to Antweb</a>.

<p>If you would like to join us, contact us at - <a href="mailto:antweb@calacademy.org">antweb@calacademy.org</a>.

</div>

<div class="admin_right"> </div>

<!-- Body of Text Ends -->
