<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix=	"html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<jsp:useBean id="toolForm" scope="session" class="org.calacademy.antweb.curate.speciesList.SpeciesListToolForm" />
<jsp:setProperty name="toolForm" property="*" />


<div id="page_contents">
	<h2>Add Search Results to Reference Taxa List</h2>
	<div class="clear"></div>
	<div class="page_divider"></div>
	<br><br>
	<div id="page_data">
		<div id="overview_data">

			<html:form method="POST" action="speciesListTool">

			<input type="hidden" id="doSearch" name="doSearch" value="searchResults"/>

Each text box can take multiple entries, separated by commas or spaces. 
<br>Search criteria containing spaces, such as species or location names, should 
<br>&nbsp;&nbsp;be surrounded by double quotes (e.g., "Camponotus modoc", "San Francisco").

			<!-- Scientific Name -->
			<p>

			<div class=adv_mid_col>
			<h3>&nbsp;&nbsp;Scientific Name:</h3>
			</div>

			<br clear=all>

			<div class=adv_mid_col>
			&nbsp;&nbsp;&nbsp;Taxonomic name:
			</div>

			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="searchType">
			<html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>

			<div class=adv_mid_col>
			<html:text styleClass="input_150" property="name" />
			</div>

			<br clear=all>

			<div class=adv_mid_col>
			&nbsp;&nbsp;&nbsp;Subfamily:
			</div>

			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="subfamilySearchType">
			<html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>

			<div class=adv_mid_col>
			<html:text styleClass="input_150" property="subfamily" />
			</div>

			<br clear=all>

			<div class=adv_mid_col>
			&nbsp;&nbsp;&nbsp;Genus:
			</div>

			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="genusSearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>

			<div class=adv_mid_col>
			<html:text styleClass="input_150" property="genus" />
			</div>

			<br clear=all>

			<div class=adv_mid_col>
			&nbsp;&nbsp;&nbsp;Species:
			</div>

			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="speciesSearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>

			<div class=adv_mid_col>
			<html:text styleClass="input_150" property="species" />
			</div>

			<br clear=all>

			<div class=adv_mid_col>
			&nbsp;&nbsp;&nbsp;Subspecies:
			</div>

			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="subspeciesSearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>

			<div class=adv_mid_col>
			<html:text styleClass="input_150" property="subspecies" />
			</div>
			<br clear=all>

			<!-- Geographic Distribution -->

			<div class=adv_mid_col>
			<br><br>
			<h3>&nbsp;&nbsp;Geographic&nbsp;Distribution:</h3>
			</div>

			<br clear=all>

			<div class=adv_mid_col>
			&nbsp;&nbsp;&nbsp;Biogeographic region:
			</div>
			<div class=adv_mid_col>
			<%= request.getAttribute("bioregionGenInc") %>
			</div>
			<br clear=all>

			<div class=adv_mid_col>
			&nbsp;&nbsp;&nbsp;Country:
			</div>
			<div class=adv_mid_col>
			<%= request.getAttribute("countryGenInc") %>
			</div>

			<br clear=all>

			<div class=adv_mid_col>
			&nbsp;&nbsp;&nbsp;Primary administrative division:
			</div>
			<div class=adv_mid_col>
			<%= request.getAttribute("adm1GenInc") %>
			</div>

			<br clear=all>

			<div class=adv_mid_col>
			&nbsp;&nbsp;&nbsp;Secondary adm. division:
			</div>
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="adm2SearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>
			<div class=adv_mid_col>
			<html:text styleClass="input_150" property="adm2"/>
			</div>

			<br clear=all> 

			<div class=adv_mid_col>
			&nbsp;&nbsp;&nbsp;Location:
			</div>
			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="localityNameSearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>
			<div class=adv_mid_col>
			<html:text styleClass="input_150" property="localityName"/>
			</div>

			<br clear=all>

			<!-- Specimen Information -->

			<div class=adv_mid_col>
			<br><br>
			<h3>&nbsp;&nbsp;Specimen&nbsp;Information:</h3>
			</div>

			<br clear=all>


			<div class=adv_mid_col>
			&nbsp;&nbsp;&nbsp;Specimen code:
			</div>

			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="specimenCodeSearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>

			<div class=adv_mid_col>
			<html:text styleClass="input_150" property="specimenCode"/>
			</div>

			<br clear=all>

			<div class=adv_mid_col>
			&nbsp;&nbsp;&nbsp;Located at:
			</div>

			<div class=adv_mid_col>
			<html:select styleClass="input_150" property="locatedAtSearchType">
			  <html:option value="equals">Equals</html:option>
			  <html:option value="contains">Contains</html:option>
			</html:select>
			</div>

			<div class=adv_mid_col>
			<html:text styleClass="input_150" property="locatedAt"/>
			</div>

			<br clear=all>




			<br><br><p align=center><input class=submit type=submit value="Search &#187;"></p>
			 <s:reset>

			</html:form>

	</div>
</div>
