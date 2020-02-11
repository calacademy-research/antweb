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
  Curator curator = (Curator) request.getAttribute("curator");
  ArrayList<Curation> curations = (ArrayList<Curation>) request.getAttribute("curations");
%>

<div id="page_contents">

<div class="in_admin">
<br><br>
<h2><a href="<%= AntwebProps.getDomainApp() %>/curators.do">Curator:</a> <%= curator.getDisplayName() %></h2>
</div>

<br><br>

<div id="page_data">

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Login ID:</b></li>
        <li><%= curator.getId() %></li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Name:</b></li>
        <li><%= curator.getLink() %></li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Group:</b></li> 
        <li><a href="<%= AntwebProps.getDomainApp() %>/group.do?id=<%= curator.getGroupId() %>"><%= GroupMgr.getGroup(curator.getGroupId()) %></a></li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Email:</b></li>
        <li><%= curator.getEmail() %></li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Admin:</b></li>
        <li><%= curator.isAdmin() %></li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Upload Images:</b></li>
        <li><%= curator.isUploadImages() %></li>
        </ul>
    </div>
    <div id="overview_data" class="plain">
        <ul>
        <li><b>Upload Specimens:</b></li>
        <li><%= curator.isUploadSpecimens() %></li>
        </ul>
    </div>
    <div id="overview_data" class="plain">
        <ul>
        <li><b>Projects:</b></li>
        <li><%= curator.getProjectNamesStr() %></li>
        </ul>
    </div>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Specimen Uploads:</b></li>
        <li><a href='<%= AntwebProps.getDomainApp() %>/listUploads.do?loginId=<%= curator.getId() %>'><%= curator.getSpecimenUploadCount() %></a></li>
        </ul>
    </div>
    
<% Upload lastUpload = curator.getLastUpload(); 
   if (lastUpload != null) {
%>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Last Upload:</b></li>
<li>
<ul>
  <li><a href='<%= AntwebProps.getDomainApp() %>/web/log/upload/<%= lastUpload.getLogFileName() %>'><%= lastUpload.getCreated() %></a></li>
  <li>&nbsp;&nbsp;Specimen: <%= Formatter.commaFormat(lastUpload.getSpecimens()) %></li>
  <li>&nbsp;&nbsp;Ungeoreferenced: <%= Formatter.commaFormat(lastUpload.getUngeoreferenced()) %></li>
  <li>&nbsp;&nbsp;Flagged: <%= Formatter.commaFormat(lastUpload.getFlagged()) %></li>
</ul>
</li>
        </ul>
    </div>
<% } %>

    <div id="overview_data" class="plain">
        <ul>
        <li><b>Image Uploads:</b></li>
        <li><a href='<%= AntwebProps.getDomainApp() %>/listImageUploads.do?curatorId=<%= curator.getId() %>'><%= curator.getImageUploadCount() %></a></li>
        </ul>
    </div>
    <div id="overview_data" class="plain">
        <ul>
        <li><b>Images Uploaded:</b></li>
        <li><%= curator.getImagesUploadedCount() %></li>
        </ul>
    </div>
    
<% if (LoginMgr.isAdmin(request)) { %>

    <div id="overview_data" class="plain">
 <br><b>(Admin only)</b>
        <ul>
        <li><b>Description Edits:</b></li>
        <li><%= curator.getDescEditCount() %></a></li>
        </ul>
    </div>
    <div id="overview_data" class="plain">
        <ul>
        <li><b>Image Uploads:</b></li>
        <li><%= Formatter.commaFormat(curator.getImageUploadCount()) %></li>
        </ul>
    </div>
    <div id="overview_data" class="plain">
        <ul>
        <li><b>Images Uploaded:</b></li>
        <li><%= Formatter.commaFormat(curator.getImagesUploadedCount()) %></li>
        </ul>
    </div>


<% } %>
<% if (false && AntwebProps.isDevMode()) { %>
<br><br>
<h2>Curations</h2><br>
<%
   int i = 0; %>
   <table><tr><th>Subfamily</th><th>Genus</th><th>Species</th><th>Geolocale</th><th>Created</th></tr>
   <%
   for (Curation curation : curations) {
     ++i;
     Taxon taxon = TaxonMgr.getTaxon(curation.getTaxonName());
     Geolocale geolocale = GeolocaleMgr.getGeolocale(curation.getGeolocaleId());
     %>
     <tr><td><%= Formatter.initCap(taxon.getSubfamily()) %></td><td><%= Formatter.initCap(taxon.getGenus()) %></td><td><%= taxon.getSpeciesSubspecies() %></td><td><%= geolocale %></td><td><%= curation.getCreated() %></td></tr>    
<% } %>
</table>
Total:<%= i %>

<% } %>

</div>
</div>