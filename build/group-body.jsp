<%@ page language="java" %>
<%@ page import = "java.util.ResourceBundle" %>
<%@ page import = "java.util.ArrayList" %>
<%@ page import = "org.calacademy.antweb.*" %>
<%@ page import = "java.util.Date" %>
<%@ page import = "org.calacademy.antweb.util.*" %>
<%@ page import = "org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.upload.Upload" %>

<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<% 
    Group group = (Group) request.getAttribute("group");
    
    Map map = (Map) request.getAttribute("map");    
    
    String the_page = HttpUtil.getTarget(request);
    String pageContainer = "showBrowse";
%>

<div class=right>
<br><b><a href="<%= AntwebProps.getDomainApp() %>/groups.do">Groups</a></b>
</div>


<input id="for_print" type="text" value="AntWeb. Available from: <%= HttpUtil.getFullJspTarget(request)%>">
<input id="for_web" type="text" value="URL: <%= HttpUtil.getFullJspTarget(request)%>">
<div class="page_contents">
<br><br>
    <h1><a href="<%= AntwebProps.getDomainApp() %>/groups.do">Groups</a>: <%= group.getName() %></h1>
</div>

<div class="page_divider"></div>

<div id="page_data">
    <div id="overview_data" class="plain">
		<ul>

		<li><b>Id:</b></li>
		<li><%= group.getId() %></li>
		</ul>

		<ul>
		<li><b>Name:</b></li>
		<li><%= group.getLink() %></li>
		</ul>

		<ul>
		<li><b>Abbrev:</b></li>
		<li><%= group.getAbbrev() %></li>
		</ul>

        <%
          String loginLinks = "";
          loginLinks = group.getCuratorLinks();
        %>		
		<ul>
		<li><b>Curators:</b></li>
		<li><%= loginLinks %></li>
		</ul>
		
		<ul>
		<li><b>First Upload:</b></li>
		<% String firstUpload = "";
		   if (group.getFirstUploadDate() != null) {
		     firstUpload = Utility.getSimpleDate(group.getFirstUploadDate());
		   } %>
		<li><%= firstUpload %></li>
		</ul>		

<%
  Upload lastUpload = group.getLastUpload();
  if (lastUpload != null) {
	A.log("group-body.jsp upload:" + lastUpload.getCountsStr());
%>

		<ul>
		<li><b>Last Upload:</b></li>
		<% String lastUploadStr = "";
	       String lastUploadAnchor = "";
		   if (group.getLastUploadDate() != null) {
		     lastUploadStr = Utility.getSimpleDate(group.getLastUploadDate());
		     String logFile = AntwebProps.getDomainApp() + "/web/log/upload/" + lastUpload.getLogFileName();
  		     lastUploadAnchor = "<a href='" + logFile + "'>" + lastUploadStr + "</a>";
		   }
		   %>
		<li><%= lastUploadAnchor %></li>
		</ul>	
		
        <br>

		<ul>
		<li><b>Uploads:</b></li>
		<li><a href='<%= AntwebProps.getDomainApp() %>/listUploads.do?groupId=<%= group.getId() %>'><%= group.getUploadCount() %></a></li>
		</ul>		
				
		<ul>
		<li><b>Specimens:</b></li>
		<li><a href="<%= AntwebProps.getDomainApp() %>/advancedSearch.do?searchMethod=advancedSearch&advanced=true&familySearchType=equals&family=Formicidae&groupName=<%= group.getName() %>&statusSet=all"><%= Formatter.commaFormat(lastUpload.getSpecimens()) %></a></li>
		</ul>

		<ul>
		<li><b>Ungeoreferenced:</b></li>
		<li><%= lastUpload.getUngeoreferenced() %></li>
		</ul>

		<ul>
		<li><b>Flagged:</b></li>
		<li><%= lastUpload.getFlagged() %></li>
		</ul>
				
		<ul>
		<li><b>Collections:</b></li>
		<li><%= Formatter.commaFormat(lastUpload.getCollections()) %></li>
		</ul>

		<ul>
		<li><b>Localities:</b></li>
		<li><a href='<%= AntwebProps.getDomainApp() %>/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=Formicidae&groupName=<%= group.getName() %>&output=mapLocality'><%= Formatter.commaFormat(lastUpload.getLocalities()) %></a></li>
		</ul>

		<ul>
		<li><b>Subfamilies:</b></li>
		<li><%= lastUpload.getSubfamilies() %></li>
		</ul>
		<ul>
		<li><b>Genera:</b></li>
		<li><%= lastUpload.getGenera() %></li>
		</ul>
		<ul>
		<li><b>Species:</b></li>
		<li><%= lastUpload.getSpecies() %></li>
		</ul>

<%
  }
  
	  
  if (LoginMgr.isAdmin(request)) { %>
	<div class="clear"></div>

    <br><hr>
    <h3>Admin Area</h3>
    <a href = "<%= AntwebProps.getDomainApp() %>/utilData.do?action=genGroupObjectMap&num=<%= group.getId() %>">Generate Group Map</a>
<%
  }
%>
  <div id="page_contents">
	<div class="clear"></div>
	<div class="page_divider"></div>

    <div class="left">
      <div class="small_map">
      <% String object = "dynamic";
         String objectName = null;
         Overview overview = null;
         String mapType = "group";
       %>
		<%@include file="/maps/googleMapPreInclude.jsp" %>         
		<%@include file="/maps/googleMapInclude.jsp" %>  
	  </div>
    </div>    
  </div> 

           
<br><br>

    </div>

</div>

