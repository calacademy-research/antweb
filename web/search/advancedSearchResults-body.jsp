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
java.lang.String offset = "0";
java.lang.String length = "2000";  // was 500
// See also specimenReport.jsp which has a hardcoded 2000

if ((request.getParameter("offset") != null) && (request.getParameter("offset").length() < 8)) {
	offset = request.getParameter("offset");
}

if ((request.getParameter("length") != null) && (request.getParameter("offset").length() < 8)) {
	length = request.getParameter("length");
}

int intLength = Integer.parseInt(length);
int intOffset = Integer.parseInt(offset);

  String pageContainer = "advancedSearchResults";
  String recordCountString = "";  
  String more_results = "";  
  String resultSetModifier = Utility.notBlankValue((String) session.getAttribute("resultSetModifier"));
  if (advancedSearchResults.getResults().size() <= intLength) { 
    recordCountString = resultSetModifier;
  } else { 
    String beginning = (new Integer(intOffset + 1)).toString();
    String ending = "";
    int intEnding = intOffset + intLength;
    if (intEnding < advancedSearchResults.getResults().size()) {
      ending = (new Integer(intEnding)).toString();
    } else {
      ending = (new Integer(advancedSearchResults.getResults().size())).toString();
      more_results = " (Showing results " + beginning + " - " + ending + ") "; 
    }  
    // recordCountString = "Showing records " + beginning + " to " + ending + " of " + resultSetModifier;
  } 
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
<form action="<%= util.getDomainApp() %>/mapResults.do" name="taxaFromSearchForm" method="POST">
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
<form action="<%= util.getDomainApp() %>/compareResults.do" name="taxaFromSearchForm" method="POST">
<input type="hidden" name="resultRank" value="specimen"/>
<%  }

    if ("fieldGuide".equals(mode)) {
%>
<form action="<%= util.getDomainApp() %>/fieldGuideResults.do" name="taxaFromSearchForm" method="POST">
<input type="hidden" name="resultRank" value="specimen"/>
<%  }

    if (("map".equals(mode)) || ("compare".equals(mode)) || ("fieldGuide".equals(mode)) ) {
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
    if (request.getParameter("sortBy") != null) {
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
        <h2 class="display_count"><span class="numbers"><%= resultSetModifier %></span> Specimens <%=more_results%><% if (imagedSpecimenCount > 0) { %> (<span class="numbers"><%= imagedSpecimenCount %></span> imaged)<% } %></h2>
        <div id="totalcount" style="display:none;"><span class="numbers"><%= resultSetModifier %></span> Specimens <%=more_results%><% if (imagedSpecimenCount > 0) { %> (<span class="numbers"><%= imagedSpecimenCount %></span> imaged)<% } %></div>
        <div id="imagecount" style="display:none;"><span class="numbers"><%= imagedSpecimenCount %></span> Imaged Specimens (out of <span class="numbers"><%= resultSetModifier %></span> total)</div>

<%
   int limit = Taxon.MAX_DISPLAY;
   String limitNote = "";
   String limitStr = (String) request.getParameter("limit");
   if (limitStr != null) {
     limit = Integer.parseInt(limitStr);
   }
   if (taxonChildren.size() > limit) {
       String allUrl = HttpUtil.getTargetReplaceParam(request, "limit", "limit=10000");
       String allLink = " See all: <a href='" + allUrl + "'>here</a>";
       limitNote = "(Only the first " + limit + " of " + taxonChildren.size() + " displayed - " + allLink + ").";
   }        

   if (!"".equals(limitNote)) { %>
     <div class="clear"></div><font color=red><%= limitNote %></font>
<% } %>

    <%@ include file="/common/pageToolsSearch.jsp" %>
    <%@ include file="/search/download_results.jsp" %>
    </div>
</div>
    <%@include file="/specimenReport.jsp" %> 
<% } %> 


<logic:equal name="mode" value="map">
</form>
</logic:equal>
<logic:equal name="mode" value="compare">
</form>
</logic:equal>

<%= "<!-- AdvancedSearch query:" + AdvancedSearch.getQuery() + "'-->" %>

