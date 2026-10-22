<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<%@include file="/curate/adminCheck.jsp" %>

<%
	    Group thisGroup = (Group) request.getSession().getAttribute("thisGroup");
	    if (thisGroup == null) {
	      A.log("viewGroup-body.jsp group is null");
	      return;
	    }
%>

<div class=right>
    <br>
    <a href="<%= AntwebProps.getDomainApp() %>/viewLogins.do">Login Manager</a>
    <br><a href="<%= AntwebProps.getDomainApp() %>/viewGroups.do">Upload groups</a>
    <br><a href="<%= AntwebProps.getDomainApp() %>/addUploader.do">Add an uploader</a>
</div>

<div class="admin_left">
<%
    boolean isNewGroup = request.getAttribute("isNewGroup") != null || "true".equals(request.getParameter("newGroup"));  // group 0 ("Default Group") is an existing group
%>
    <h1><%= isNewGroup ? "New upload group" : "Upload group: " + Html.esc(thisGroup.getName()) %></h1>

    <logic:messagesPresent property="error">
        <h2>Sorry, you need to correct the following errors:</h2>
        <font color="red">
        <html:messages property="error" id="errMsg">
            <bean:write name="errMsg"/><br>
        </html:messages>
        </font>
    </logic:messagesPresent>

    <div style="border:1px solid #ccc;background:#f7f7f7;padding:8px 12px;margin:10px 0;max-width:760px">
    <p><b>What an upload group is:</b> the institution or lab that owns specimen records on AntWeb (a museum, a university lab, or one curator's collection). Every specimen record belongs to one upload group, and the logins in the group can upload its records. <b>An upload replaces all specimens of the uploader's upload group.</b> (In the database and older pages: "group", table ant_group.)</p>
    <p><b>Adding someone who wants to upload:</b> use <a href="<%= domainApp %>/addUploader.do">Add an uploader</a>; it creates the upload group, the login and the rights in one step.</p>
    <p><b>Where these values show:</b> the group's name on specimen pages and the list of contributors; the short name in reports and summaries where space is short (upload reports, the name update summary in compute.log).</p>
    </div>

    <form action="<%= domainApp %>/saveGroup.do" method="POST"><%= org.calacademy.antweb.util.Csrf.field(request) %>

    <p><b>Upload group number</b><br>
    <% if (isNewGroup) { %>given when you click Done (the next free number)<% } else { %><%= thisGroup.getId() %><% } %>
    <input type="hidden" name="id" value="<%= thisGroup.getId() %>">
    <br><span style="color:#666">AntWeb's number for this upload group (every specimen record of the group refers to it). It cannot be changed. Example: <code>62</code>.</span></p>

    <p><b>Full name</b> (Institution Name)<br>
    <input type="text" class="input_200" name="name" value="<%= Html.escAttr(thisGroup.getName()) %>">
    <br><span style="color:#666">Required. The name people see on specimen pages and in the list of contributors: the institution, the lab, or "Curator" and the person's name for a personal collection. Examples: <code>Archbold Biological Station</code>, <code>Economo Ant Lab</code>, <code>Curator Kiko Gomez</code>.</span></p>

    <p><b>Short name</b> (Institution Abbreviation)<br>
    <input type="text" class="input_200" name="abbrev" value="<%= Html.escAttr(thisGroup.getAbbrev() == null ? "" : thisGroup.getAbbrev()) %>">
    <br><span style="color:#666">Used in reports and summaries where the full name does not fit. Use the institution's code from <a href="https://antcat.org/institutions" target="_blank">AntCat's list of institutions</a> when there is one; otherwise the institution's own abbreviation, or the person's surname for a personal collection. Letters only, no group number. Examples: <code>ABS</code> (Archbold Biological Station), <code>MEM</code> (Mississippi Entomological Museum), <code>OIST</code>, <code>Gomez</code>.</span></p>

<%
    String selected = null;
%>
    <p><b>Admin curator</b> of the upload group<br>
      <select name="adminLoginId">  <!-- %= disabled % -->
        <%
           selected = "";
           if (thisGroup.getAdminLoginId() == 0) selected = " selected"; else selected = "";
    %>    <option value="0"<%= selected %>>None</option> <%
           ArrayList<Curator> curatorList = new ArrayList<>(LoginMgr.getCurators());  // a copy: the shared list is never sorted
           Collections.sort(curatorList, Curator.CuratorNameComparator);
           for (Curator curator: curatorList) {
             if (curator.getId() == thisGroup.getAdminLoginId()) {
               //A.log("viewGroup-body.jsp curator:" + curator.getId() + " this:" + thisGroup.getAdminLoginId());
               selected = "selected";
             } else {
               selected = "";
             }
         %>
          <option value='<%= curator.getId() %>' <%= selected %>><%= Html.esc(curator.getDisplayName()) %></option>
        <% } %>	
      </select>
    <br><span style="color:#666">The curator responsible for this upload group's records; AntWeb contacts this person about the group's uploads. The list shows logins that already belong to an upload group: for a new group choose None, then add the person with <a href="<%= domainApp %>/addUploader.do">Add an uploader</a> (it makes them the admin curator of a group that has none). Example: the lab head or collection manager.</span></p>

    <br>



    <% if (isNewGroup) { %>
    <input type="hidden" name="newGroup" value="true">
    <% } %>

    <p><b>Logins in this upload group</b> <span style="color:#666">(they upload this group's specimens; change a login's group on its page in the Login Manager)</span>
    <% if (!isNewGroup) { %><br>&nbsp;&nbsp;&nbsp;<a href="<%= domainApp %>/addUploader.do?groupId=existing&amp;existingGroupId=<%= thisGroup.getId() %>">Add an uploader to this upload group</a><% } %>
    <% 
     ArrayList<Curator> curators = thisGroup.getCurators()  ;
     if (curators != null) {
       for (Login curator : thisGroup.getCurators()) { 
         String name = curator.getFullName();
         if (Utility.isBlank(name)) name = curator.getEmail();
      %>
      <br>&nbsp;&nbsp;&nbsp;<a href='<%= AntwebProps.getDomainApp() %>/viewLogin.do?id=<%= curator.getId() %>'><%= Html.esc(name) %></a>
    <% } 
     }
    %>

</div>

<div class=admin_right>
</div> 

<div class="clear"></div>
<br><br>

<div class="msg in_admin">
    <div class="msg_actions" align="center">
        <input border="0" type="image" src="<%= domainApp %>/image/orange_done.gif" width="98" height="36" name="done" value="Done">
        <a href="<%= domainApp %>/viewGroups.do"><img border=0" src="<%= domainApp %>/image/grey_cancel.gif" width="123" height="36"></a>

        <!-- input border="0" type="image"  width="98" height="36" name="delete" value="Delete" -->
        <% if (!isNewGroup) { %>
        <%-- Refused by SaveGroupAction while the group has logins or specimens. --%>
        <input type="submit" name="step" value="delete" onclick="return confirm('Delete this upload group? This is refused while it has logins or specimens.')">
        <% } %>


        <br><br>
        <a href="<%= domainApp %>/listUploads.do?groupId=<%= thisGroup.getId() %>">Upload Logs</a>
    </div>
</div>


</form>
