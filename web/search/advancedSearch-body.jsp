<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.calacademy.antweb.search.AdvancedSearchForm" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Group" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<jsp:useBean id="advancedSearchForm" scope="session" class="org.calacademy.antweb.search.AdvancedSearchForm" />
<jsp:setProperty name="advancedSearchForm" property="*" />
    
<% 
Login accessLogin = LoginMgr.getAccessLogin(request);
%>
   
<% // for autocomplete %>

   
<script type="text/javascript">

var sections = new Array("coll", "type", "geo", "spec");
window.onload=setSectionOpens;

function toggleSection(section_name) {
  var the_element = eval("document.advancedSearchForm." + section_name + "GroupOpen");
  var section = document.getElementById(section_name);
  if (section.style.display == "") {
    section.style.display = "none";
    the_element.value = "none";
  } else {
    section.style.display = "";
    the_element.value = "";
  }
}
  
function setSection(section, value) {
  var this_section = document.getElementById(section);
  this_section.style.display = value;
}

function setSectionOpens() {
  setSection("coll", "<%= advancedSearchForm.getCollGroupOpen() %>");
  setSection("spec", "<%= advancedSearchForm.getSpecGroupOpen() %>");
  setSection("geo", "<%= advancedSearchForm.getGeoGroupOpen() %>");
  setSection("type", "<%= advancedSearchForm.getTypeGroupOpen() %>");
//  setSection("other", "< %= advancedSearchForm.getOtherGroupOpen() % >");
}
	
</script>




<div id="page_contents">

	<br><br><br>

<% // for autocomplete 
  if (false) { %>

    <% // for autocomplete %>
    <link rel="stylesheet" type="text/css" href="https://code.jquery.com/ui/1.12.1/themes/base/jquery-ui.css" />
    <link rel="stylesheet" type="text/css" href="<%= AntwebProps.getDomainApp() %>/search/autocomplete.css"/>
    <script src="https://code.jquery.com/jquery-1.12.4.js"></script>
    <!-- script src="https://code.jquery.com/jquery-3.2.1.js"></script -->
    <script src="https://code.jquery.com/ui/1.12.1/jquery-ui.js"></script>
    <script src="https://code.jquery.com/jquery-migrate-1.2.1.js"></script>
    <script src="<%= AntwebProps.getDomainApp() %>/search/autocomplete.js"></script>

	<html:form method="GET" action="description">
	  	<input type="hidden" name="project" value="worldants">
	<table>
	  <tr>
	    <td>
   	      <h1>Taxon Name Search:</h1>
	    </td>
	    <td>
		  <table border=1 bordercolor=lightgrey>
		    <tr><td>
	  	 	  <input type="text" class="input_200" id="taxonName" name="taxonName" size=40/>
		    </td></tr>
		  </table>
        </td>
        <td>
          <input src="<%= AntwebProps.getDomainApp() %>/image/magnifyGlassButton.png" border="0" type=image value="Search &#187;">
        </td>	
	  </tr>
	</table>

	<script>
		$("#taxonName").autocomplete("<%= AntwebProps.getDomainApp() %>/search/autoCompleteKeys.jsp"); 
	</script>
		
    </html:form>
<% } %>


<br><br>
	<h1>Basic Search</h1>
	<br>
	<%@ include file="/search/searchBox3.jsp" %>

	<html:form method="GET" action="advancedSearch">
	<input type="hidden" name="searchMethod" value="advancedSearch">
	<input type="hidden" name="advanced" value="true">
	<input type="hidden" name="isIgnoreInsufficientCriteria" value="false">
	<input type="hidden" name="sortBy" value="taxonname">
	
	<html:hidden property="collGroupOpen"/>
	<html:hidden property="specGroupOpen"/>
	<html:hidden property="geoGroupOpen"/>
	<html:hidden property="typeGroupOpen"/>
	<!-- html: hidden property="otherGroupOpen"/ -->

	<html:hidden property="typeGroupOpen"/>

<br><br>

	<h1>Advanced Search</h1>
    <div class="clear"></div>
    <div class="page_divider"></div>
</div>
<!-- /div -->

<div id="page_data">
    <div id="overview_data">

<logic:notPresent name="project"> 
Search the AntWeb database for world ants, including types. 
</logic:notPresent> 
Click <b>SHOW | HIDE</b> to add more criteria to your search.

<jsp:include page="/search/instruct.jsp" flush="true"/>

<p><h3>Scientific Name:</h3>

         <div class=adv_left_col>
		Taxonomic name:
        </div>

		<div class=adv_mid_col>
		<html:select styleClass="input_150" property="searchType">
		<html:option value="equals">Equals</html:option>
		  <html:option value="contains">Contains</html:option>
		</html:select>
		</div>

		<div class=adv_right_col>
		<html:text styleClass="input_150" property="name" />
		</div>

		<br clear=all>

		<div class=adv_left_col>
		Family:
		</div>
		
		<div class=adv_mid_col>
		<html:select styleClass="input_150" property="familySearchType" value="equals">
          <html:option value="equals">Equals</html:option>
          <html:option value="notEquals">Not Equals</html:option>
		  <html:option value="contains">Contains</html:option>
		</html:select>
		</div>
		
		<div class=adv_right_col>
		<html:text styleClass="input_150" property="family" value="Formicidae"/>
		</div>

		<br clear=all>

		<div class=adv_left_col>
		Subfamily:
		</div>
		
		<div class=adv_mid_col>
		<html:select styleClass="input_150" property="subfamilySearchType" value="equals">
		<html:option value="equals">Equals</html:option>
		  <html:option value="contains">Contains</html:option>
		</html:select>
		</div>
		
<% if (LoginMgr.isAdmin(request)) { %>
		<div class=adv_mid_col>
		<html:select styleClass="input_150" property="subfamily">
		  <html:option value="none">&nbsp;</html:option>
		  <html:option value="agroecomyrmecinae">Agroecomyrmecinae</html:option>
		  <html:option value="amblyoponinae">Amblyoponinae</html:option>
		  <html:option value="aneuretinae">Aneuretinae</html:option>
		  <html:option value="apomyrminae">Apomyrminae</html:option>
		  <html:option value="dolichoderinae">Dolichoderinae</html:option>
		  <html:option value="dorylinae">Dorylinae</html:option>
		  <html:option value="ectatomminae">Ectatomminae</html:option>
		  <html:option value="formicinae">Formicinae</html:option>
		  <html:option value="heteroponerinae">Heteroponerinae</html:option>
		  <html:option value="leptanillinae">Leptanillinae</html:option>
		  <html:option value="martialinae">Martialinae</html:option>
		  <html:option value="myrmeciinae">Myrmeciinae</html:option>
		  <html:option value="myrmicinae">Myrmicinae</html:option>
		  <html:option value="paraponerinae">Paraponerinae</html:option>
		  <html:option value="ponerinae">Ponerinae</html:option>
		  <html:option value="proceratiinae">Proceratiinae</html:option>
		  <html:option value="pseudomyrmecinae">Pseudomyrmecinae</html:option>
		</html:select>
		</div>
<% } else { %>				
		<div class=adv_right_col>
		<html:text styleClass="input_150" property="subfamily" />
		</div>
<% } %>
		<br clear=all>

		<div class=adv_left_col>
		Genus:
		</div>
		
		<div class=adv_mid_col>
		<html:select styleClass="input_150" property="genusSearchType" value="equals">
		  <html:option value="equals">Equals</html:option>
		  <html:option value="contains">Contains</html:option>
		</html:select>
		</div>
		
<% if (LoginMgr.isAdmin(request)) { %>
		<div class=adv_mid_col>
		<html:select styleClass="input_150" property="genus">
		  <html:option value="none">&nbsp;</html:option>
<%
     for (Genus genus : TaxonMgr.getGenera()) { 
       if (genus.isValid()) { %>
		  <html:option value="<%= genus.getName() %>"><%= Formatter.initCap(genus.getName()) %></html:option>       
  <%   } %>		  
<%   } %>		  
		</html:select>
		</div>
<% } else { %>						
		<div class=adv_right_col>
		<html:text styleClass="input_150" property="genus" />
		</div>
<% } %>


		<br clear=all>

		<div class=adv_left_col>
		Species:
		</div>
		
		<div class=adv_mid_col>
		<html:select styleClass="input_150" property="speciesSearchType">
		  <html:option value="equals">Equals</html:option>
		  <html:option value="contains">Contains</html:option>
		</html:select>
		</div>
		
		<div class=adv_right_col>
		<html:text styleClass="input_150" property="species" />
		</div>

		<br clear=all>

		<div class=adv_left_col>
		Subspecies:
		</div>
		
		<div class=adv_mid_col>
		<html:select styleClass="input_150" property="subspeciesSearchType">
		  <html:option value="equals">Equals</html:option>
		  <html:option value="contains">Contains</html:option>
		</html:select>
		</div>
		
		<div class=adv_right_col>
		<html:text styleClass="input_150" property="subspecies" />
		</div>
		<br clear=all>


		<p><h3>Geographic Distribution: <a class=footer_href href=# onclick="toggleSection('geo'); return false;" title="SHOW | HIDE">SHOW | HIDE</a></h3>
		<div id="geo" style="display:none;">
			<div class=adv_left_col>
			Biogeographic region:
			</div>
		
			<div class=adv_span_col>

			<%  if (false) { %>
			<!-- !AntwebProps.isDevMode() -->
			<html:select styleClass="input_150" property ="bioregion"> 
			<html:option value="">Any!</a></html:option>
			<html:option value="Afrotropical">Afrotropical</html:option>
			<html:option value="Australian">Australian</html:option>
			<html:option value="Eurasian">Eurasian</html:option>
			<html:option value="Indo-Australian">Indo-Australian</html:option>
			<html:option value="Malagasy">Malagasy</html:option>
			<html:option value="Nearctic">Nearctic</html:option>
			<html:option value="Neotropical">Neotropical</html:option>
			<html:option value="Oriental">Oriental</html:option>
			<html:option value="Palearctic">Palearctic</html:option>
			<html:option value="Paleotropical">Paleotropical</html:option>
			</html:select>
			<% } else { %>
			<%= request.getAttribute("bioregionGenInc") %>
			<% } %>

			</div>

			<br clear=all>

			<div class=adv_left_col>
			Country/Island:
			</div>
			<div class=adv_span_col>
			<%= request.getAttribute("countryGenInc") %>
			</div>

			<br clear=all>

			<div class=adv_left_col>
			Primary administrative division:
			</div>
			<div class=adv_span_col>
			<%= request.getAttribute("adm1GenInc") %>
			</div>

			<br clear=all>

			<div class=adv_left_col>
			Secondary administrative division:
			</div>
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="adm2SearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="adm2"/>
			</div>

			<br clear=all>

			<div class=adv_left_col>
			Location:
			</div>
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="localityNameSearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="localityName"/>
			</div>

			<br clear=all>

			<div class=adv_left_col>
			Locality code:
			</div>
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="localityCodeSearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="localityCode"/>
			</div>

			<br clear=all>

			<div class=adv_left_col>
			Habitat:
			</div>
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="habitatSearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="habitat"/>
			</div>

			<br clear=all>

			<div class=adv_left_col>
			Elevation (m):
			</div>
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="elevationSearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="greaterThanOrEqual">Greater than or equal</html:option>
			  <html:option value="lessThanOrEqual">Less than or equal</html:option>
			</html:select>
			</div>
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="elevation"/>
			</div>

			<br clear=all>    
		</div>

		<p><h3>Collection Information: <a class=footer_href href=# onclick="toggleSection('coll'); return false;" title="SHOW | HIDE">SHOW | HIDE</a></h3>
		<div id="coll" style="display:none;">
			<div class=adv_left_col>
			Method:
			</div>
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="methodSearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="method"/>
			</div>

			<br clear=all>

			<div class=adv_left_col>
			Microhabitat:
			</div>
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="microhabitatSearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="microhabitat"/>
			</div>

			<br clear=all>
			<div class=adv_left_col>
			Collected by:
			</div>
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="collectedBySearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="collectedBy"/>
			</div>

			<br clear=all>
			<div class=adv_left_col>
			Collection code:
			</div>
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="collectionCodeSearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="collectionCode"/>
			</div>

			<br clear=all>

			<div class=adv_left_col>
			Date collected:
			</div>
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="dateCollectedSearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="greaterThanOrEqual">On or after</html:option>
			  <html:option value="lessThanOrEqual">On or before</html:option>
			</html:select>
			</div>
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="dateCollected"/> (format yyyy-mm-dd)
			</div>

			<br clear=all>

		</div>

		<p><h3>Specimen Information: <a class=footer_href href=# onclick="toggleSection('spec'); return false;" title="SHOW | HIDE">SHOW | HIDE</a></h3>
		<div id="spec" style="display:none;">
		
			<div class=adv_left_col>
			Specimen code:
			</div>
		
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="specimenCodeSearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>
		
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="specimenCode"/>
			</div>

			<br clear=all>

			<div class=adv_left_col>
			Located at:
			</div>
		
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="locatedAtSearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>
		
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="locatedAt"/>
			</div>

			<br clear=all>

			<div class=adv_left_col>
			Life Stage:
			</div>
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="lifeStageSearchType">
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="lifeStage"/>
			</div>
			<br clear=all>
			
			<div class=adv_left_col>
			Caste/Subcaste:
			</div>
			<div class=adv_span_col>
			<html:hidden property="casteSearchType" value="contains"/>
			<html:select styleClass="input_150" property="caste">
			  <html:option value="">Any</html:option> 
			  <html:option value="male">&nbsp;&nbsp;Male</html:option>
			  <html:option value="ergatoidMale">&nbsp;&nbsp;&nbsp;&nbsp;ergatoid</html:option>
			  <html:option value="alateMale">&nbsp;&nbsp;&nbsp;&nbsp;alate</html:option>
			  <html:option value="queen">&nbsp;&nbsp;Queen</html:option>
			  <html:option value="ergatoidQueen">&nbsp;&nbsp;&nbsp;&nbsp;ergatoid</html:option>
			  <html:option value="alateDealateQueen">&nbsp;&nbsp;&nbsp;&nbsp;alate/dealate</html:option>
			  <html:option value="brachypterous">&nbsp;&nbsp;&nbsp;&nbsp;brachypterous</html:option>
			  <html:option value="worker">&nbsp;&nbsp;Worker</html:option>
			  <html:option value="majorSoldier">&nbsp;&nbsp;&nbsp;&nbsp;major/soldier</html:option>
			  <html:option value="normal">&nbsp;&nbsp;&nbsp;&nbsp;normal</html:option>
			  <html:option value="other">&nbsp;&nbsp;Other</html:option>
			  <html:option value="intercaste">&nbsp;&nbsp;&nbsp;&nbsp;intercaste</html:option>
			  <html:option value="gynandromorph">&nbsp;&nbsp;&nbsp;&nbsp;gynandromorph</html:option>
			  <html:option value="larvaPupa">&nbsp;&nbsp;&nbsp;&nbsp;larva/pupa</html:option>
			</html:select>
			</div>

			<br clear=all>

			<div class=adv_left_col>
			Medium:
			</div>
		
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="mediumSearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>
		
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="medium"/>
			</div>

			<br clear=all>
			<div class=adv_left_col>
			Specimen Notes:
			</div>		
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="specimenNotesSearchType">
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>
		
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="specimenNotes"/>
			</div>
			<br clear=all>

			<div class=adv_left_col>
			DNA Extraction Notes:
			</div>
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="dnaExtractionNotesSearchType">
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="dnaExtractionNotes"/>
			</div>
			
			<br clear=all>
			<div class=adv_left_col>
			Museum:
			</div>
			<div class=adv_span_col>
			<html:hidden property="museumCodeSearchType" value="equals"/>
			<html:select styleClass="input_150" property="museumCode">
			  <html:option value="">Any</html:option> 
			  <%
			    ArrayList<Museum> museums = MuseumMgr.getMuseums();
			    for (Museum museum : museums) { %>
                  <html:option value="<%= museum.getCode() %>">&nbsp;&nbsp;<%= museum.getCode() %></html:option> 
			 <% } %>
			</html:select>
			</div>			
			
			<br clear=all>
			<div class=adv_left_col>
			Owned by:
			</div>
		
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="ownedBySearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>
		
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="ownedBy"/>
			</div>

			<br clear=all>

			<div class=adv_left_col>
			Uploaded:
			</div>
		
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="createdSearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="greaterThanOrEqual">On or after</html:option>
			  <html:option value="lessThanOrEqual">On or before</html:option>
			</html:select>
			</div>
		
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="created"/> (format yyyy-mm-dd)
			</div>

		<br clear=all>

			<div class=adv_left_col>
			Uploaded by:
			</div>
			<div class=adv_span_col>
			<html:select styleClass="input_150" property="groupName">
			  <html:option value="">Any</html:option> 			  
			<% ArrayList<Group> groups = GroupMgr.getUploadGroups(); 
			   for (Group group : groups ) {
				 out.println("<option value='" + group.getName() + "'>" + group.getName() + "</option>");
			   } %>						
			</html:select>
			</div>

		<br clear=all>

			<div class=adv_left_col>
			Upload ID:
			</div>
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="uploadId"/>
			</div>

		<br clear=all>

        </div>

		<p><h3>Type Information: <a class=footer_href href=# onclick="toggleSection('type'); return false;" title="SHOW | HIDE">SHOW | HIDE</a></h3>
		<div id="type" style="display:none;">

			<div class=adv_left_col>
			Type status contains:
			</div>
			<div class=adv_right_col>
			<html:text styleClass="input_150" property="type"/>
			</div>

			<br clear=all>

			<div class=adv_left_col>
				<% 
				//if (AntwebProps.isDevMode()) AntwebUtil.log("images:" + advancedSearchForm.getImages()); 
				String typesOnSelected = "";
				String typesOffSelected = " selected ";
				if (advancedSearchForm.getTypes() != null && advancedSearchForm.getTypes().equals("on")) {
				  typesOnSelected = " selected ";
				  typesOffSelected = "";
				}
				%>
				Types only: 
				<select name="types" class="input_150" property="types">
				  <option value="on" <%= typesOnSelected %>>On</option>
				  <option value="off" <%= typesOffSelected %>>Off</option>
				</select>
			</div>

			<br clear=all>
        </div>

		<p><h3>Other Information:</h3>
			<div class=adv_left_col>
			<% String statusSetDefault = advancedSearchForm.getStatusSet(); %>
			<%@include file="/common/statusSetSelectDisplay.jsp" %>

			<% 
			A.log("images:" + advancedSearchForm.getImagesOnly()); 
			String imagesOnSelected = "";
			String imagesOffSelected = " selected ";
			if (advancedSearchForm.getImagesOnly() != null && advancedSearchForm.getImagesOnly().equals("on")) {
			  imagesOnSelected = " selected ";
			  imagesOffSelected = "";
			}
			%>
			<br clear=all>

			Images only: 
			<select name="imagesOnly" class="input_150" property="imagesOnly">
			  <option value="on" <%= imagesOnSelected %>>On</option>
			  <option value="off" <%= imagesOffSelected %>>Off</option>
			</select>
			</div>

			<br clear=all>

			<!-- p>Images only <html :checkbox property="images"/ -->

			 <!-- <br>Species Only: <html :checkbox property="speciesOnly"/> -->
			<div class=adv_left_col>
			Group by: <html:select styleClass="input_150" property="resultRank" value="specimen">
			<% 
			   if (LoginMgr.isDeveloper(accessLogin)) { %>
            <% } 
               if (LoginMgr.isAdmin(accessLogin)) { %>
			  <html:option value="locality">Locality</html:option>
			<% } %>
			  <html:option value="subfamily">Subfamily</html:option>
			  <html:option value="genus">Genus</html:option>
			  <html:option value="species">Species</html:option>
			  <html:option value="specimen">Specimen</html:option>
			</html:select>
			</div>

			<br clear=all>

			<div class=adv_left_col>
			Output: <html:select styleClass="input_150" property="output" value="list">
			  <html:option value="list">List</html:option>
			  <html:option value="mapSpecimen">Map specimens</html:option>
			  <html:option value="mapLocality">Map localities</html:option>
			</html:select>
			<br>&nbsp;&nbsp;&nbsp;* Map defaults to locality if count > 1000.
			</div>

		    <p align=center><input src="<%= AntwebProps.getDomainApp() %>/image/magnifyGlassButton.png" border="0" type=image value="Search &#187;"></p>
       	    <s:reset>
		
    </div>
</div>

</html:form>

