<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.curate.login.LoginEditPage" %>

<!-- viewLogin-body.jsp: the page is built (and every value escaped) by org.calacademy.antweb.curate.login.LoginEditPage. -->
<%
    Login accessLogin = LoginMgr.getAccessLogin(request);
    if (accessLogin == null) return;
    Login thisLogin = (Login) request.getSession().getAttribute("thisLogin");
    LoginEditPage.PageData pageData = (LoginEditPage.PageData) request.getSession().getAttribute(LoginEditPage.SESSION_ATTR);
    String domainApp = AntwebProps.getDomainApp();
    String secureDomainApp = AntwebProps.getSecureDomainApp();
%>
<% if (accessLogin.isAdmin()) { %>
<div class="right">
<br>
<a href="<%= domainApp %>/viewLogins.do">Login Manager</a>
<br><a href="<%= domainApp %>/viewGroups.do">Upload groups</a>
<br><a href="<%= domainApp %>/addUploader.do">Add an uploader</a>
</div>
<% } %>

<div class="admin_left loginEditPage">
<logic:messagesPresent property="error">
    <h2>Please correct the following:</h2>
    <font color="red">
    <html:messages property="error" id="errMsg">
        <bean:write name="errMsg"/><br>
    </html:messages>
    </font>
</logic:messagesPresent>
<%
    if (thisLogin == null || pageData == null || pageData.state.id != thisLogin.getId()) {
%>
<p>This login could not be shown. Open it again from the <a href="<%= domainApp %><%= accessLogin.isAdmin() ? "/viewLogins.do" : "/curate.do" %>"><%= accessLogin.isAdmin() ? "Login Manager" : "Curator Tools" %></a>.</p>
<%
    } else if (accessLogin.isAdmin()) {
%>
<%= LoginEditPage.renderAdmin(pageData, accessLogin.getId(), Csrf.field(request), domainApp, secureDomainApp) %>
<%
    } else if (accessLogin.getId() == thisLogin.getId()) {
%>
<%= LoginEditPage.renderSelf(pageData, Csrf.field(request), domainApp, secureDomainApp) %>
<%
    }
%>
</div>
<div class="clear"></div>
