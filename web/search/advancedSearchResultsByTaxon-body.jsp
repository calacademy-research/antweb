<%@ page language="java" %>
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
<jsp:setProperty name="advancedSearchResults" property="*" />

<!-- jsp:useBean id="map" scope="session" class="org.calacademy.antweb.Map" / -->
<!-- jsp:setProperty name="map" property="*" / -->

<logic:present parameter="project">
  <bean:parameter id="project" name="project"/>
</logic:present>
<logic:notPresent parameter="project">
  <bean:define id="project" value=""/>
</logic:notPresent>

<logic:present parameter="mode">
  <bean:parameter id="mode" name="mode"/>
</logic:present>
<logic:notPresent parameter="mode">
  <bean:define id="mode" value=""/>
</logic:notPresent>

<%
    String resultRank = (java.lang.String) session.getAttribute("resultRank");
%>

<!-- Body of Text Begins -->
<div id="page_contents">
    <h1>Advanced Search <%= (new Formatter()).capitalizeFirstLetter(resultRank) %> Results</h1>
    <div class="links"></div>
    <div id="antcat_view"></div>
    <div class="clear"></div>
</div>


<%
	java.util.ArrayList taxonList = (java.util.ArrayList) session.getAttribute("taxonList");  //advancedSearchResults.getSpeciesList();

    String resultSetModifier = Utility.notBlankValue((String) session.getAttribute("resultSetModifier"));
%>

<logic:equal name="mode" value="">
</logic:equal>

<logic:equal name="mode" value="compare">
<input type=button class="adv_top_submit" onClick="javascript:history.back();" value="Back to Search Results &#187;">
</logic:equal>
<logic:equal name="mode" value="map">
<input type=button class="adv_top_submit" onClick="javascript:history.back();" value="Back to Search Results &#187;">
</logic:equal>
<logic:equal name="mode" value="fieldGuide">
<input type=button class="adv_top_submit" onClick="javascript:history.back();" value="Back to Search Results &#187;">
</logic:equal>

<logic:equal name="mode" value="map">
<form action="<%= AntwebProps.getDomainApp() %>/mapResults.do" name="taxaFromSearchForm" method="POST">
<input type="hidden" name="resultRank" value="species"/>
</logic:equal>
<logic:equal name="mode" value="compare">
<form action="<%= AntwebProps.getDomainApp() %>/compareResults.do" name="taxaFromSearchForm" method="POST">
<input type="hidden" name="resultRank" value="species"/>
</logic:equal>
<logic:equal name="mode" value="fieldGuide">
<form action="<%= AntwebProps.getDomainApp() %>/fieldGuideResults.do" name="taxaFromSearchForm" method="POST">
<input type="hidden" name="resultRank" value="<%= resultRank %>"/>
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
<logic:equal name="mode" value="fieldGuide"> 
Select All
<input type="checkbox" name="selectall" onClick="selectAll(document.taxaFromSearchForm);">
<input type="submit" class="submit" value="Field Guide Selected &#187;" align="right"/>
<hr></hr>
</logic:equal>

<% if (advancedSearchResults.getResults().size() < 1) { %>
<div class="page_divider"></div>
<div class="clear"></div>
<!-- /div -->
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
<logic:equal name="mode" value="fieldGuide">
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
           for (int i = 0; i < the_cookies.length; i++) {
           if (the_cookies [i].getName().equals (the_cookie)) {
               reallyCookie = the_cookies[i];
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
<form id="theform" name="taxaFromSearchForm" method="POST">
    <input type="hidden" name="resultRank" value="species"/>
<% if (useShot) { %>
    <input id="thumb_choice" type="hidden" name="shot" value="<%= choice_is %>" checked> 
<% } %>

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
			<logic:equal name="mode" value="fieldGuide">
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
				<a href="<%= AntwebProps.getDomainApp() %>/description.do?<bean:write name="row" property="pageParams"/>"><%= new Formatter().capitalizeFirstLetter((String) prettyname) %></a>
				<% } else if (resultRank.equals("genus")) { %>
				<bean:define id="genus" name="row" property="genus"/>
				<a href="<%= AntwebProps.getDomainApp() %>/description.do?name=<%= genus %>&rank=genus"><%=  new Formatter().capitalizeFirstLetter((String) genus) %></a>
				<% } else if (resultRank.equals("subfamily")) { %>
				<bean:define id="subfamily" name="row" property="subfamily"/>
				<a href="<%= AntwebProps.getDomainApp() %>/description.do?name=<%= subfamily %>&rank=subfamily"><%=  new Formatter().capitalizeFirstLetter((String) subfamily) %></a>
				<% } %>
				</div>
			</div>
			<div class=clear></div>
			<!-- /div -->
		</logic:notEmpty>


	</logic:iterate>
</form>

</div>

<% } %>
