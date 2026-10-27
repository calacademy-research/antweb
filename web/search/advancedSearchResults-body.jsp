<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.Taxon" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.search.*" %>

<!-- page:advancedSearchResults-body.jsp -->

<% 
    Utility util = new Utility(); 
    Login accessLogin = LoginMgr.getAccessLogin(request);
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

<%
AdvancedSearchResults advancedSearchResults = (AdvancedSearchResults) session.getAttribute("advancedSearchResults");
if (advancedSearchResults == null) {
  String message = "No advanced search results in session.";
  //A.log(message);
  out.println("<br><b>" + message + "</b>");
  return;
}
//A.log("advancedSearchResults-body.jsp advancedSearchResults:" + advancedSearchResults);
%>

<jsp:setProperty name="advancedSearchResults" property="*" />

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

<bean:define id="mode" name="mode"/>
<% //AntwebUtil.log("advancedSearchResults-body.jsp mode:" + mode); %>
<% //AntwebUtil.log("advancedSearchResults-body.jsp mode:" + request.getParameter("mode")); %>
<% // String searchMode = request.getParameter("mode"); %>

<%
  // Paged list (SearchPaging, 2026-10-21): 200 specimens a page. After a specimen search
  // (advancedSearch.do) the session holds one page, read from the database, and searchPaging has the
  // total. Otherwise (Map or Compare views of a search, older session results) the session list is
  // shown 200 at a time.
  String pageContainer = "advancedSearchResults";
  SearchPaging searchPaging = (SearchPaging) request.getAttribute("searchPaging");
  int listSize = advancedSearchResults.getResults().size();
  int listFirst = 0;        // first row of the session list shown
  int numberOffset = 0;     // row number of that row in the whole result
  String showingText = null;
  String pagerPrevUrl = null;
  String pagerNextUrl = null;
  if (searchPaging != null) {
    numberOffset = searchPaging.getOffset();
    showingText = searchPaging.getShowingText();
    pagerPrevUrl = (String) request.getAttribute("searchPagingPrevUrl");
    pagerNextUrl = (String) request.getAttribute("searchPagingNextUrl");
  } else {
    int listPage = SearchPaging.pageFromParam(request.getParameter(SearchPaging.PAGE_PARAM));
    if (listPage > SearchPaging.lastPage(listSize)) listPage = SearchPaging.lastPage(listSize);
    listFirst = SearchPaging.offset(listPage);
    numberOffset = 0;
    showingText = SearchPaging.showingText(listPage, listSize);
    String listPath = new Utility().getDomain() + HttpUtil.getRequestURI(request);
    String listQuery = HttpUtil.getQueryStringNoQuestionMark(request);
    if (listPage > 1) pagerPrevUrl = SearchPaging.pageUrl(listPath, listQuery, listPage - 1);
    if (listPage < SearchPaging.lastPage(listSize)) pagerNextUrl = SearchPaging.pageUrl(listPath, listQuery, listPage + 1);
  }
  request.setAttribute("specimenReportFirst", listFirst);
  request.setAttribute("specimenReportNumberOffset", numberOffset);
  // Formatted here, so not in a class="numbers" span: antweb.jquery.js keeps only the digits of such a span
  // ("Showing 1–200 of 141,728" was shown as "1,200,141,728" until 2026-10-23).
  String showingHtml = Html.esc(showingText).replace("\u2013", "&ndash;");
  String resultSetModifier = Utility.notBlankValue((String) session.getAttribute("resultSetModifier"));
%>    

<div id="page_contents">
<h1>Advanced Search Results</h1>
<% 
  String note = "";
  if (!HttpUtil.isOnline()) note = "<h2>&nbsp;&nbsp;&nbsp;<font color=red>Warning.</font>  Offline.  Javascript features will not work.</h2>";
%>
<%= note %>
    <div class="links"></div>
    <div id="antcat_view"></div>
    <div class="clear"></div>
</div>

<%
    //if (AntwebProps.isDevMode() && "".equals(mode)) mode = "compare";

    //A.log("advancedSearchResults-body.jsp mode:" + mode); // + " formVal:" + thisForm.getResultRank()
    
    if ("map".equals(mode)) {
%>
<!-- 0 -->
<form action="<%= util.getDomainApp() %>/mapResults.do" name="taxaFromSearchForm" method="POST"><%= org.calacademy.antweb.util.Csrf.field(request) %>
<!-- 1 -->
<%
      String resultRank = (String) session.getAttribute("resultRank");
      A.log("advancedSearchResults-body.jsp resultRank:" + resultRank);
      if (resultRank != null) { %>
<input type="hidden" name="resultRank" value="<%= resultRank %>"/>
   <% } else { %>
<input type="hidden" name="resultRank" value="specimen"/>
   <% } 
      A.slog("advancedSearchResults-body.jsp resultRank:" + resultRank);
    } // end map mode

    if ("compare".equals(mode)) {
%>
<form action="<%= util.getDomainApp() %>/compareResults.do" name="taxaFromSearchForm" method="POST"><%= org.calacademy.antweb.util.Csrf.field(request) %>
<input type="hidden" name="resultRank" value="specimen"/>
<%  }

    if (("map".equals(mode)) || ("compare".equals(mode))) {
      //AntwebUtil.log("advancedSearchResults-body.jsp mode:" + mode);
%>
Select All
<input type="checkbox" name="selectall" onClick="selectAll(document.taxaFromSearchForm);">
<input type="submit" class="submit" value="<%= (new Formatter()).capitalizeFirstLetter((String) mode) %> Selected &#187;" align="right"/>
<hr></hr>
<%
    }

int size = advancedSearchResults.getResults().size();
if (size < 1) { %>
    <div class="page_divider"></div>
    <div class="clear"></div>
    <div id="page_data">
        Sorry, nothing matches your request.
    </div>
<% } else if (size > 0) { %> 

<%
    // A paged search comes sorted from the database (the sort applies before paging).
    if (searchPaging == null && request.getParameter("sortBy") != null) {
      advancedSearchResults.sortBy(request.getParameter("sortBy"), request.getParameter("sortOrder"));
    }

    session.setAttribute("taxon", advancedSearchResults.getResultsAsTaxon());
    Taxon taxon = (Taxon) session.getAttribute("taxon");
    //A.log("advancedSearchResults-body.jsp taxon.class:" + taxon.getClass());
    java.util.ArrayList<Taxon> taxonChildren = taxon.getChildren();
    int imagedSpecimenCount = 0;
    for (Taxon t : taxonChildren) {
      if (t.getHasImages()) {
        //A.log("advancedSearchResults-body.jsp code:" + ((Specimen)t).getCode() + " groupName:" + ((Specimen)t).getGroupName());
        ++imagedSpecimenCount;
      }
    }
%>
<!-- 3 -->

<div class="page_divider project"></div>
<div id="totals_and_tools_container">
    <div id="totals_and_tools">
        <h2 class="display_count"><%= showingHtml %> Specimens<% if (searchPaging != null) { %> (<span class="numbers"><%= imagedSpecimenCount %></span> imaged on this page)<% } else if (imagedSpecimenCount > 0) { %> (<span class="numbers"><%= imagedSpecimenCount %></span> imaged)<% } %></h2>
        <div id="totalcount" style="display:none;"><%= showingHtml %> Specimens</div>
        <div id="imagecount" style="display:none;"><span class="numbers"><%= imagedSpecimenCount %></span> Imaged Specimens</div>
<%
   // Rows listFirst .. listFirst + 199 of the session list (specimenReport.jsp: index <= limit).
   int limit = listFirst + SearchPaging.PAGE_SIZE - 1;
%>
<%@ include file="/search/searchPager.jsp" %>

    <%@ include file="/common/pageToolsSearch.jsp" %>
    <%@ include file="/search/download_results.jsp" %>
    </div>
</div>
    <%@include file="/specimenReport.jsp" %> 
<%@ include file="/search/searchPager.jsp" %>
<% } %> 


<logic:equal name="mode" value="map">
</form>
</logic:equal>
<logic:equal name="mode" value="compare">
</form>
</logic:equal>


