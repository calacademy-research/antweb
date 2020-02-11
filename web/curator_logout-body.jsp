<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>



<!-- Body of Text Begins -->

<div class="admin_left">

    <div class="admin_action_module">
        <div class="admin_action_item"><h1>You are now logged out</h1></div>
<html:messages id="message" message="true">
<font color="red"><b>
<bean:write name="message"/><br>
</b></font>
</html:messages>
You are now logged out. <a href="login.do">Click here</a> to log back in.

</div>

<div class="admin_right"> </div>

<!-- Body of Text Ends -->
