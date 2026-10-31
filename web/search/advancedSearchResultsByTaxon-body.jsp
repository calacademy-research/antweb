<%-- Body of the results by subfamily, genus or species: "N Genera (from M specimens)" (session resultSetModifier,
     or the Prepare action's count), the session's taxonList with a checkbox each (row numbers used by
     compareResults.do, mapResults.do and the field guide results page through common/specimen_list.js), and for a search by
     species the line on specimens not identified to genus (B54) with a link to the specimen view. Docs:
     docs/flows/search.md. Tests: tests/e2e/test_numbers_search.py, tests/unit/test_numbers_search_fixes.py. --%><%@ page language="java" %>
<%@ page import = "org.calacademy.antweb.Formatter" %>
<%@ page import = "org.calacademy.antweb.*" %>
<%@ page import = "org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.search.*" %>

<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<!-- page:advancedSearchResultsByTaxon-body.jsp -->

<%
    Utility util = new Utility();
%>

<script type="text/javascript">
<!--
 
function selectAll(thisForm) {

  var count = thisForm.chosen.length;
  var checkedVal = thisForm.selectall.checked;
  for (var loop = 0; loop < count; loop++) {
    thisForm.chosen[loop].checked = checkedVal;
  }
}

// -->
</script>

<jsp:useBean id="advancedSearchResults" scope="session" class="org.calacademy.antweb.search.AdvancedSearchResults" />
<%-- No jsp:setProperty property="*" here (2026-10-29): the action fills the bean; request values must not overwrite it. --%>

<!-- jsp:useBean id="map" scope="session" class="org.calacademy.antweb.Map" / -->
<!-- jsp:setProperty name="map" property="*" / -->


<logic:present parameter="mode">
  <bean:parameter id="mode" name="mode"/>
</logic:present>
<logic:notPresent parameter="mode">
  <bean:define id="mode" value=""/>
</logic:notPresent>

<%
    String resultRank = (java.lang.String) session.getAttribute("resultRank");
    if (resultRank == null) resultRank = "";   // session expired: no NullPointerException below (2026-10-29)
%>

<!-- Body of Text Begins -->
<div id="page_contents">
    <h1>Advanced Search <%= Html.esc((new Formatter()).capitalizeFirstLetter(resultRank)) %> Results</h1>
    <div class="links"></div>
    <div id="antcat_view"></div>
    <div class="clear"></div>
</div>


<%
	java.util.ArrayList taxonList = (java.util.ArrayList) session.getAttribute("taxonList");  //advancedSearchResults.getSpeciesList();

    String resultSetModifier = Utility.notBlankValue((String) session.getAttribute("resultSetModifier"));
    // After Compare or Map of a search by species the list holds the imaged or georeferenced species only, and the
    // preparation counts them (request attribute); the session still has the search's count (B50, 2026-10-27).
    String preparedModifier = (String) request.getAttribute("resultSetModifier");
    if ("species".equals(resultRank) && preparedModifier != null && preparedModifier.trim().length() > 0) resultSetModifier = preparedModifier.trim();
%>

<logic:equal name="mode" value="">
</logic:equal>

<logic:equal name="mode" value="compare">
<input type=button class="adv_top_submit" onClick="javascript:history.back();" value="Back to Search Results &#187;">
</logic:equal>
<logic:equal name="mode" value="map">
<input type=button class="adv_top_submit" onClick="javascript:history.back();" value="Back to Search Results &#187;">
</logic:equal>

<logic:equal name="mode" value="map">
<form action="<%= AntwebProps.getDomainApp() %>/mapResults.do" name="taxaFromSearchForm" method="POST"><%= org.calacademy.antweb.util.Csrf.field(request) %>
<input type="hidden" name="resultRank" value="species"/>
</logic:equal>
<logic:equal name="mode" value="compare">
<form action="<%= AntwebProps.getDomainApp() %>/compareResults.do" name="taxaFromSearchForm" method="POST"><%= org.calacademy.antweb.util.Csrf.field(request) %>
<input type="hidden" name="resultRank" value="species"/>
</logic:equal>

<logic:equal name="mode" value="map">
Select All
<input type="checkbox" name="selectall" onClick="selectAll(document.taxaFromSearchForm);">
<input type="submit" class="submit" value="Map Selected &#187;" align="right"/>
<hr></hr>
</logic:equal>
<logic:equal name="mode" value="compare"> 
Select All
<input type="checkbox" name="selectall" onClick="selectAll(document.taxaFromSearchForm);">
<input type="submit" class="submit" value="Compare Selected &#187;" align="right"/>
<hr></hr>
</logic:equal>

<% if (advancedSearchResults.getResults().size() < 1) { %>
<div class="page_divider"></div>
<div class="clear"></div>
<!-- /div -->
<%-- A Compare or Map preparation that leaves nothing still says its count ("0 georeferenced species (from 0
     specimens)"), as when it leaves some (B50, B75; 2026-10-27). --%>
<% if ("species".equals(resultRank) && preparedModifier != null && preparedModifier.trim().length() > 0) { %>
<h2 class="display_count"><%= preparedModifier.trim() %></h2>
<% } %>
<div id="page_data">
Sorry, nothing matches your request.
</div>
<% } else if (advancedSearchResults.getResults().size() > 0) { %>

<logic:equal name="mode" value="compare">
<div class="adv_search_checkbox">&nbsp;</div>
</logic:equal>
<logic:equal name="mode" value="map">
<div class="adv_search_checkbox">&nbsp;</div>
</logic:equal>

<div class="page_divider project"></div>
<div id="totals_and_tools_container">    
    <div id="totals_and_tools">
        <h2 class="display_count"><%= resultSetModifier %></span></h2>
        <div id="totalcount" style="display:none;"><%= resultSetModifier %></div>
        <div id="imagecount" style="display:none;"><%= resultSetModifier %></div>
    <%@ include file="/common/pageToolsTaxonSearch.jsp" %>
    <%@ include file="/search/download_results.jsp" %>

    </div>
</div>
<!-- /div -->

<div id="domain" style="display:none;"><%= AntwebProps.getDomainApp() %></div>
<%
    String use_thumb = new String();
    String the_cookie = "thumbs";
    Cookie the_cookies [] = request.getCookies ();
    Cookie reallyCookie = null;
       if (the_cookies != null) {
           for (Cookie theCookie : the_cookies) {
               if (theCookie.getName().equals(the_cookie)) {
                   reallyCookie = theCookie;
                   break;
               }
           }
    }
    if (reallyCookie == null) {
       use_thumb = "h";   
    } else {
       use_thumb = reallyCookie.getValue();   
    }
    String choice_is = use_thumb;
    String profile = "p";
    String dorsal = "d";    
    String label = "l";
    String ventral = "v";
     
    boolean useShot = true;
    /* Shot being in the request is what determines which page to return in CompareResultsAction.java.
       single image page(multiTaxaOneView-body.jsp) or a multi image page (multiTaxaComparison-body.jsp)(h, p, d , l).
       This code is triplicated (Luke, this is your legacy) Also found in specimenReport.jsp, taxonChildImages.jsp.
       Was that if a user was logged in they would see the all. Still that way, for some reason on the live site. 
       On the local machine the use_thumb was "a" when users not logged in. On the live site it is "d". 
       Comes from reallyCookie? Do not understand this.       
    */    
    if (use_thumb.equals(profile)) {
        choice_is = "Profile";
    } else if (use_thumb.equals(dorsal)) {
        choice_is = "Dorsal";
    } else if (use_thumb.equals(ventral)) {
        choice_is = "Ventral";    
    } else if (use_thumb.equals(label)) {
        choice_is = "Label";    
    } else if (use_thumb.equals("a")) {
        useShot = false;
    } else {
        choice_is = "Head";    
    }
    
    //A.log("advancedSearchResultsByTaxon-body.jsp use_thumb:" + use_thumb);
    
%>
<div id="page_data">
<form id="theform" name="taxaFromSearchForm" method="POST"><%= org.calacademy.antweb.util.Csrf.field(request) %>
    <input type="hidden" name="resultRank" value="species"/>
<% if (useShot) { %>
    <input id="thumb_choice" type="hidden" name="shot" value="<%= Html.escAttr(choice_is) %>" checked> 
<% } %>

<%-- aw-list-scroll / aw-list: see responsive.css (narrow screens only). --%>
<div class="aw-list-scroll"><div class="aw-list">
    <logic:iterate id="row" collection="<%=taxonList%>" indexId="index">

		<logic:notEmpty name="row" property="pageParams">
			<div class="specimen_layout">
				<div class="sd_checkbox">
					<span class="sdcb"><input type="checkbox" name="chosen" value="<bean:write name="index"/>"/></span>
				</div>
            </div>
			<logic:equal name="mode" value="map">
				<div class="adv_search_checkbox">
				<input type="checkbox" name="chosen" value="<bean:write name="index"/>"/>
				</div>
			</logic:equal>
			<logic:equal name="mode" value="compare">
				<div class="adv_search_checkbox">
				<input type="checkbox" name="chosen" value="<bean:write name="index"/>"/>
				</div>
			</logic:equal>

			<div class="sd_data">
				<div class="sd_name">
				<% 
				  //A.log("advancedSearchResultsByTaxno-body.jsp row:" + ((ResultItem)row).getStatus());
				%>  
				  <%@include file="/common/statusDisplayRow.jsp" %>
				<%
				  if (resultRank.equals("species")) { %>
				<bean:define id="prettyname" name="row" property="fullName"/>
				<a href="<%= AntwebProps.getDomainApp() %>/description.do?<bean:write name="row" property="pageParams"/>"><%= org.calacademy.antweb.util.Html.esc(new Formatter().capitalizeFirstLetter((String) prettyname)) %></a>
				<% } else if (resultRank.equals("genus")) { %>
				<bean:define id="genus" name="row" property="genus"/>
				<a href="<%= AntwebProps.getDomainApp() %>/description.do?name=<%= org.calacademy.antweb.util.Html.escAttr(org.calacademy.antweb.util.Html.url(genus)) %>&rank=genus"><%= org.calacademy.antweb.util.Html.esc(new Formatter().capitalizeFirstLetter((String) genus)) %></a>
				<% } else if (resultRank.equals("subfamily")) { %>
				<bean:define id="subfamily" name="row" property="subfamily"/>
				<a href="<%= AntwebProps.getDomainApp() %>/description.do?name=<%= org.calacademy.antweb.util.Html.escAttr(org.calacademy.antweb.util.Html.url(subfamily)) %>&rank=subfamily"><%= org.calacademy.antweb.util.Html.esc(new Formatter().capitalizeFirstLetter((String) subfamily)) %></a>
				<% } else {
				  A.log("advancedSearchResultsByTaxon-body.jsp rank not found:" + resultRank);
				} %>
				</div>
			</div>
			<div class=clear></div>
			<!-- /div -->
		</logic:notEmpty>


	</logic:iterate>
</div></div><%-- /aw-list, /aw-list-scroll --%>
</form>
<%
    // B54, 2026-10-27: names not identified to genus are not species (PlaceTotals.isIdentifiedToGenus) and are not in
    // the list above; their specimens are still in the search and in its "(from N specimens)". Search page only (not
    // the Compare or Map preparation of it). There is no search filter for "not identified to genus", so the link is
    // the specimen view of the same search.
    Integer notIdentified = (Integer) request.getAttribute("specimensNotIdentifiedToGenus");
    String specimenViewQuery = (String) request.getAttribute("specimenViewQuery");
    if ("species".equals(resultRank) && preparedModifier == null && notIdentified != null && notIdentified > 0 && specimenViewQuery != null) {
%>
<div id="not_identified_to_genus" class="not_identified_to_genus"><%= notIdentified %> <%= notIdentified == 1 ? "specimen" : "specimens" %> not identified to genus (not counted as species), listed in the <a href="<%= AntwebProps.getDomainApp() %>/advancedSearch.do?<%= org.calacademy.antweb.util.Html.escAttr(specimenViewQuery) %>">specimen view</a> of this search.</div>
<% } %>

</div>

<% } %>
