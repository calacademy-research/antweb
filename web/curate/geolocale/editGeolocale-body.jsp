<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.curate.geolocale.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>


<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<% String domainApp = (new Utility()).getDomainApp(); %>

<%@include file="/curate/curatorCheck.jsp" %>

<%	
	Geolocale geolocale = (Geolocale) request.getAttribute("geolocale");
	
    ArrayList<Geolocale> validChildren = (ArrayList<Geolocale>) request.getAttribute("validChildren");
        
	if (geolocale == null) {
        AntwebUtil.log("editGeolocale-body.jsp geolocale:" + geolocale);
        %> Geolocale Not found.  Back To <a href="<%= AntwebProps.getDomainApp() %>/geolocaleMgr.do">GeoLocale Manager</a><%
	    return;
    }

    EditGeolocaleForm form = (EditGeolocaleForm) request.getAttribute("form");
    
    String georank = geolocale.getGeorank();
    String parentRank = Georank.getParent(georank);
    String parent = geolocale.getParent();
    String childRank = Georank.getChild(georank);
%>

<% // These are necessary for the map to work. Included in layout.jsp elsewhere.
   if (HttpUtil.isOnline()) { %>
<script src="https://cdnjs.cloudflare.com/ajax/libs/jquery/3.7.1/jquery.min.js" integrity="sha512-v2CJ7UaYy4JwqLDIrZUI/4hqeoQieOmAZNXBeQyjo21dadnwR+8ZaIJVT8EE2iyI61OV8e6M8PP2/4hpQINQ/g==" crossorigin="anonymous" referrerpolicy="no-referrer"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/jquery-migrate/3.5.2/jquery-migrate.min.js" integrity="sha512-BzvgYEoHXuphX+g7B/laemJGYFdrq4fTKEo+B3PurSxstMZtwu28FHkPKXu6dSBCzbUWqz/rMv755nUwhjQypw==" crossorigin="anonymous" referrerpolicy="no-referrer"></script>
<% } %>
<script type="text/javascript" src="<%= AntwebProps.getDomainApp() %><%= org.calacademy.antweb.util.Asset.url(application, "/common/jquery.tools.min.js") %>"></script>

<div class="in_admin">
<h1>Edit Geolocale</h1>

<br>&nbsp;&nbsp;&nbsp; <a href="<%= domainApp %>/geolocaleMgr.do?georank=<%= Html.escAttr(Html.url(georank)) %><%= parent == null ? "" : "&parent=" + Html.escAttr(Html.url(parent)) %>#<%= geolocale.getId() %>">Parent List</a>  
  | Public: <%= geolocale.getTag() %>
  <% if ("adm1".equals(georank)) { %>
  | <a href='<%= domainApp %>/adm1Mgr.do?adm1Name=<%= Html.escAttr(Html.url(geolocale.getName())) %>&countryName=<%= Html.escAttr(Html.url(geolocale.getParent())) %>'>Adm1 Mgr</a>
  <% } %>
  <% if (childRank != null) { %>
  | <a href='<%= domainApp %>/geolocaleMgr.do?georank=<%= childRank %>&parent=<%= Html.escAttr(Html.url(geolocale.getName())) %>'>Children List</a>
  <% } %>
</div>

<div class="mgr_help" style="max-width:70em;margin:6px 16px 12px 16px">
All settings of one place. <b>Submit</b> saves every field below at once (administrators only) and
makes the change live at once, unless Fast is ticked. Bioregion decides the bioregion of specimens
collected here (at upload); a place with an Alternate Bioregion lies in two bioregions, so its specimens
get no bioregion from the place. None means no value.
</div>

<%-- Page text (Contents, Author, Author Bio, page images) is edited on the public page itself with the
     page-text editor (TinyMCE, DescriptionAction.saveDescriptionEdit, stored in object_edit). This form
     holds only settings; Admin Notes below is a plain internal note, never shown on the site. --%>
<% String geoPage = geolocale.getAltThisPageTarget(); %>
<div class="pageTextLinks" style="border:1px solid #ccc;padding:8px;margin:8px 0">
<b>Page text</b> is edited on the <%= Html.esc(geolocale.getName()) %> page:
<a href="<%= Html.escAttr(geoPage + "&editField=contents") %>#contents">Contents</a>
| <a href="<%= Html.escAttr(geoPage + "&editField=author") %>#author">Author</a>
| <a href="<%= Html.escAttr(geoPage + "&editField=authorBio") %>#authorBio">Author Bio</a>
| <a href="<%= Html.escAttr(geoPage) %>">Page images</a>
</div>


<% 
   //A.log("XXX:" + geolocale + " tag:" + geolocale.getTag());

   String message = (String) request.getAttribute("message");
   if (message != null) out.println("<br><font color=red>" + message + "</font>");
%>

<!-- For UI stuff see the old: ~/dev/calacademy/antweb/web/curate/manageLogin-body.jsp -->

<!-- form action="< %= domainApp % >/editCountry.do" method="POST" -->
<html:form method="POST" action="editGeolocale"><%= Csrf.field(request) %>
            
<input type="hidden" name="isSubmit" value="true">
            
<%
    String disabled;
 	if (geolocale.isUn()) disabled = "disabled"; else disabled = "";
 	boolean isDisabled = geolocale.isUn(); 	  
    String selected = null;
%>

            <div class="clear"></div>
<br><br>
            
<% if (LoginMgr.isAdmin(request)) { %>
<input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="Submit">
<% } %>
            
            <div class="clear"></div>
<br><br>

<div class="admin_left">

<h3>Id:</h3>
<input type="text" class="input_200" name="id" value="<%= geolocale.getId() %>" disabled>
<input type="hidden" class="input_200" name="id" value="<%= geolocale.getId() %>">
<br><br>

<h3>Name:</h3>
<input type="text" class="input_200" name="name" value="<%= Html.escAttr(geolocale.getName()) %>">
<br><br>

<% if ("country".equals(georank)) { %>
<h3>ISO Code:</h3>
<input type="text" class="input_200" name="isoCode" value="<%= Formatter.formDisplay(geolocale.getIsoCode()) %>">
<br><br>

<h3>ISO 3 Code:</h3>
<input type="text" class="input_200" name="iso3Code" value="<%= Formatter.formDisplay(geolocale.getIso3Code()) %>">
<br><br>
<% } %>

<h3>Geo Rank:</h3>
  <select name="georank" <%= disabled %>>
	<% 
%> 
  <option value="region"<%= ("region".equals(georank) ? "selected" : "") %>>Region</option>
  <option value="subregion"<%= ("subregion".equals(georank) ? "selected" : "") %>>Subregion</option>
  <option value="country"<%= ("country".equals(georank) ? "selected" : "") %>>Country</option>
  <option value="adm1"<%= ("adm1".equals(georank) ? "selected" : "") %>>adm1</option>
  </select>
<% if (isDisabled) { %><input type="hidden" class="input_200" name="georank" value="<%= Html.escAttr(georank) %>"><% } %>
<br><br>

<h3>Valid:</h3>
Mostly the UN geolocales, with overrides. 
<br><input type="checkbox" name="isValid" <%= (geolocale.getIsValid() == true)?"checked":"" %>>
<br><br>

<h3>Is UN:</h3>
<input type="checkbox" name="isUn" <%= (geolocale.isUn() == true)?"checked":"" %> disabled>
<input type="hidden" class="input_200" name="isUn" value="<%= (geolocale.isUn())?"true":"false" %>">
<br><br>

<h3>Source:</h3>
<%= Html.esc(geolocale.getSource()) %>
<input type="hidden" class="input_200" name="source" value="<%= Html.escAttr(geolocale.getSource()) %>">
<br><br>

<% //if (AntwebProps.isDevMode()) AntwebUtil.log("editGeolocale-body.jsp georank:" + georank); %>

<h3>Valid Name:</h3>
<% if (!geolocale.getIsValid()) { %>

<%@include file="/curate/geolocale/validSelect.jsp" %>
<%
    if (isOther) out.println("&nbsp;&nbsp;(Other:&nbsp;" + Html.esc(geolocale.getValidName()) + ")");
 } else { %>
 <%= "N/A" %>
<%
 } %>    
<br><br>


<% if (!"region".equals(georank)) { %>
<h3><%= Georank.getParentHeading(georank) %></h3>
  <select name="parent">  <!-- %= disabled % -->
	<% 
       selected = "";
       if (geolocale.getParent() == null) selected = " selected"; else selected = "";
%>    <option value="none"<%= selected %>>None</option> <% 
       for (Geolocale validGeolocale : GeolocaleMgr.getGeolocales(Georank.getParent(georank), true)) {
	     if (validGeolocale.getName().equals(geolocale.getParent())) selected = "selected"; else selected = "";
 	 %>
      <option value='<%= Html.escAttr(validGeolocale) %>' <%= selected %>><%= Html.esc(validGeolocale) %></option>
	<% } %>	
  </select>
  
<br><br>
<% } %>

<h3>Bioregion:</h3>
  <select name="bioregion">  <!-- %= disabled % -->
	<%
       selected = "";
       if (geolocale.getBioregion() == null) selected = " selected"; else selected = "";
%>    <option value="none"<%= selected %>>None</option> <%
       for (Bioregion bioregion : BioregionMgr.getBioregions()) {
	     if (bioregion.getName().equals(geolocale.getBioregion())) selected = "selected"; else selected = "";
 	 %>
      <option value='<%= bioregion.getName() %>' <%= selected %>><%= bioregion.getName() %></option>
	<% } %>	
  </select>

<br><br>

<% // if (AntwebProps.isDevMode()) { %> 

<h3>Alternate Bioregion:</h3>
  <select name="altBioregion">  <!-- %= disabled % -->
	<%
       selected = "";
       if (geolocale.getAltBioregion() == null) selected = " selected"; else selected = "";
%>    <option value="none"<%= selected %>>None</option> <%
       for (Bioregion bioregion : BioregionMgr.getBioregions()) {
	     if (bioregion.getName().equals(geolocale.getAltBioregion())) selected = "selected"; else selected = "";
 	 %>
      <option value='<%= bioregion.getName() %>' <%= selected %>><%= bioregion.getName() %></option>
	<% } %>
  </select>
<br><br>
<% // } %>

<h3>Live:</h3>
<input type="checkbox" name="isLive" <%= (geolocale.getIsLive() == true)?"checked":"" %>>
<br><br>


<h3>Centroid (lat, lon):</h3>
For a new country or adm1, look up the centroid and bounding box and enter them here.<br>
<% String centroidValue = (String) request.getAttribute("centroidInput");
   if (centroidValue == null) centroidValue = geolocale.useCentroid();
   if (centroidValue == null || "null".equals(centroidValue)) centroidValue = "";
%>
<input type="text" class="input_200" name="centroidFixed" value="<%= Html.escAttr(centroidValue) %>" placeholder="-18.77, 46.87">
<br><br>

<h3>Bounding box (west lon, south lat, east lon, north lat):</h3>
<% String boundingBoxValue = (String) request.getAttribute("boundingBoxInput");
   if (boundingBoxValue == null) boundingBoxValue = geolocale.useBoundingBox();
   if (boundingBoxValue == null || "null".equals(boundingBoxValue)) boundingBoxValue = "";
%>
<input type="text" class="input_550" name="boundingBoxFixed" value="<%= Html.escAttr(boundingBoxValue) %>" placeholder="43.22, -25.61, 50.48, -11.95">
<br><br>

<h3>Admin Notes:</h3>
(Plain text, for administrators only; not shown on the site.)<br>
<% String adminNotes = geolocale.getAdminNotes();
   if (adminNotes == null || "null".equals(adminNotes)) adminNotes = "";
%>
<textarea rows="4" cols="50" name="adminNotes" class="biotextarea550"><%= Html.esc(adminNotes) %></textarea>
<br>

<h3>Use Parent Region:</h3>
For some Geolocales it is best to display the region.<br>
<input type="checkbox" name="isUseParentRegion" <%= (geolocale.getIsUseParentRegion() == true)?"checked":"" %>>
<br><br>


<% if ("country".equals(geolocale.getGeorank())) { %>

<h3>Is Island:</h3>
Some countries are treated as countries (like Hawaii, Galapagos Islands) to support our bioregion data.<br>
<input type="checkbox" name="isIsland" <%= (geolocale.getIsIsland() == true)?"checked":"" %>>
<br><br>

  <% if (geolocale.isIsland()) {
       if (!geolocale.isValid()) {
         out.println("Country can be set for valid islands");
       } else { %>
<h3>Country (of an Island):</h3>
An island can have a country set different from its parentage. Galapagos Islands (georank country) is a child of South America
with country set to Ecuador. Hawaii (georank country) is a child of Polynesia with country set to United States.
<%
         String disableCountry = "disabled";
         if (geolocale.getIsIsland()) { disableCountry = ""; }
%>
<input type="text" class="input_20" name="country" value="<%= Html.escAttr(geolocale.getCountry()) %>" <%= disableCountry %> >
<br><br>
    <% } %>
  <% } %>
<% } %>


<h3>Locality:</h3>
<% String locality = geolocale.getLocality();
   if (locality == null || "null".equals(locality)) locality = "";
%><%= Html.esc(locality) %>
<br><br>

<h3>Use Children:</h3>
To indicate that adm1 should be used, for instance, United States, Brazil, China and Australia.<br>
<input type="checkbox" name="isUseChildren" <%= (geolocale.getIsUseChildren() == true)?"checked":"" %>>
<br><br>

<h3>Created:</h3>
<input type="text" class="input_120" name="created" value="<%= geolocale.getCreated() %>" disabled>
<br><br>


</div>
            <div class="clear"></div>

			<br>Fast:<input type="checkbox" name="isFast" <%= (form.getIsFast() == true)?"checked":"" %>> Ticked: save without reloading the list of places in memory (quicker when you change several places); reload it afterwards with <a href='<%= AntwebProps.getDomainApp() %>/util.do?action=reloadAntwebMgr&name=geolocale'>Reload places</a>.<br><br>
<% if (LoginMgr.isAdmin(request)) { %>
			<input border="0" type="image" src="<%= domainApp %>/image/grey_submit.png" width="77" height="23" value="Submit">
<% } else { %>
			<p><b>Only an AntWeb administrator can save these settings.</b> The page text has its own [Edit] links on the page.</p>
<% } %>
            
            <div class="align_left">
            <br><br>&lt; Back To <a href="<%= AntwebProps.getDomainApp() %>/geolocaleMgr.do?georank=<%= Html.escAttr(Html.url(georank)) %>#<%= geolocale.getId() %>">Geolocale Manager</a>

            </div>

            <div class="clear"></div>
            


<!-- /form -->
</html:form>

<%-- Delete is a small POST form with the form token (EditGeolocaleAction refuses it by GET). --%>
<div class="align_left">
<% if (LoginMgr.isAdmin(request)) { %>
<%= Csrf.postButton(request, domainApp + "/editGeolocale.do?id=" + geolocale.getId() + "&isDelete=true", "Delete", "Delete this geolocale and its species list? Refused while places lie under it.") %>
<% } %>
</div>