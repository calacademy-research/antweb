<%@ page language="java" %>
<%@ page import = "org.calacademy.antweb.Formatter" %>
<%@ page import = "org.calacademy.antweb.search.*" %>
<%@ page import = "org.calacademy.antweb.*" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%
     String daysAgo = (String) request.getParameter("daysAgo");
     String group = (String) request.getParameter("group");
     String loggedInGroup = null;
     
     if (daysAgo == null) {
        daysAgo = "30";
     }
   
     Login accessLogin = LoginMgr.getAccessLogin(request);
     Group accessGroup = GroupMgr.getAccessGroup(request);
     if (accessGroup != null) {
	   loggedInGroup = accessGroup.getName();
     }    
     
     boolean ownerPriv = LoginMgr.isLoggedIn(request) || LoginMgr.isAdmin(accessLogin);
	  
     Utility util = new Utility();
     String domainApp = util.getDomainApp();	  
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

function checkDate(selectedDate) {
  if (selectedDate == "") {
    document.getElementById("rangeDiv").style.display = "";
  } else {
    document.getElementById("rangeDiv").style.display = "none";
  }
}
// -->
</script>

<jsp:useBean id="searchResults" scope="session" class="org.calacademy.antweb.search.RecentImageSearchResults" />
<jsp:setProperty name="searchResults" property="*" />

<jsp:useBean id="groups" scope="session" class="java.util.ArrayList" />

<logic:present parameter="sortBy">
  <bean:parameter id="sortBy" name="sortBy"/>
</logic:present>
<logic:notPresent parameter="sortBy">
  <bean:define id="sortBy" value="uploadDate"/>
</logic:notPresent>

<logic:present parameter="project">
  <bean:parameter id="project" name="project"/>
</logic:present>
<logic:notPresent parameter="project">
  <bean:define id="project" value=""/>
</logic:notPresent>
 
<script src="<%= AntwebProps.getDomainApp() %>/openAndFocus.js" type="text/javascript"></script>

<%
java.lang.String offset = "0";
java.lang.String length = "50";

if ((request.getParameter("offset") != null) && (request.getParameter("offset").length() < 8)) {
	offset = request.getParameter("offset");
}
int intOffset = Integer.parseInt(offset);

if ((request.getParameter("length") != null) && (request.getParameter("length").length() < 8)) {
	length = request.getParameter("length");
}
int intLength = Integer.parseInt(length);
%>

<div id="page_contents">
    <h1>Recently Added Specimen Images</h1>
    <div class="clear"></div>
    <div class="page_divider"></div>
</div>

<html:form method="GET" action="recentSearchResults">
<html:hidden property="searchMethod" value="recentImageSearch"/>

View images uploaded by 

<html:select property="group">
<html:option value="">all institutions</html:option>
<logic:iterate collection="<%= groups %>" id="groupy">
<html:option value="<%= (String) groupy %>"><%= (String) groupy %></html:option>
</logic:iterate>
</html:select>

from
<html:select property="daysAgo" value="<%= daysAgo %>" onchange="checkDate(this.options[this.selectedIndex].value);">
<html:option value="1">the past day</html:option>
<html:option value="7">the past week</html:option>
<html:option value="30">the past month</html:option>
<html:option value="100000">all time</html:option>
<html:option value="">date range</html:option>
</html:select>

<script type="text/javascript">
function clickclear(thisfield, defaulttext) {
    if (thisfield.value == defaulttext) {
        thisfield.value = "";
    }
}

function clickrecall(thisfield, defaulttext) {
    if (thisfield.value == "") {
        thisfield.value = defaulttext;
    }
}
</script>

<% if (daysAgo == "") { %>
  <span id="rangeDiv">
<% } else { %>
  <span id="rangeDiv" style="display:none">
<% } %>
 <html:text value="YYYY-MM-DD" onclick="clickclear(this, 'YYYY-MM-DD')" onblur="clickrecall(this,'YYYY-MM-DD')" style="width:95px;" property="fromDate"/>  
 to <html:text value="YYYY-MM-DD" onclick="clickclear(this, 'YYYY-MM-DD')" onblur="clickrecall(this,'YYYY-MM-DD')" style="width:95px;" property="toDate"/> 
</span>
<input type="submit" class="submit" name="recent" value="Update">
</html:form>
<!-- /div -->
<div id="page_data">

<% if (searchResults.getResults().size() <= intLength) { %>

<h2><%= searchResults.getResults().size() %> Images</h2>


<% } else { 

  String beginning = (new Integer(intOffset + 1)).toString();
  String ending = "";
  int intEnding = intOffset + intLength;
  if (intEnding < searchResults.getResults().size()) {
    ending = (new Integer(intEnding)).toString();
  } else {
    ending = (new Integer(searchResults.getResults().size())).toString();
  }
  
%>

<h2>Showing records <span class="numbers"><%=beginning%></span> to <span class="numbers"><%=ending%></span> of <span class="numbers"><%= searchResults.getResults().size() %></span> records</h2>

<% } %>


<span class="right"><b><a href="<%= AntwebProps.getDomainApp() %>/query.do?name=imageData">Download All</a></b></span>



<div class="clear"></div>

<% 
  if (daysAgo == null) {
     daysAgo = "1";
  }
  if (group == null) {
     group = "";
  }
%>
<html:form action="deleteImages">
<input type="hidden" name = "daysAgo" value = "<%= daysAgo %>" />
<input type="hidden" name="group" value = "<%= group %>" />
<br />

<div class="adv_search_header <logic:equal name="sortBy" value="code">sorted </logic:equal>recent_image">
<%   if (ownerPriv && (searchResults.getResults().size() > 0)) {   %>
<input type="checkbox" name="selectall" onClick="selectAll(document.deleteImagesForm);">
<% } %>
<a href="<%= domainApp %>/recentSearchResults.do?searchMethod=recentImageSearch&sortBy=code">Image</a>
<logic:equal name="sortBy" value="code"><img border="0" src="<%= domainApp %>/image/sort_arrow_down.png" width="13" height="7" alt="Sort"></logic:equal>
</div>

<div class="adv_search_header <logic:equal name="sortBy" value="shotType">sorted </logic:equal>shot"><a href="<%= domainApp %>/recentSearchResults.do?searchMethod=recentImageSearch&sortBy=shotType">Shot</a>
<logic:equal name="sortBy" value="shotType"><img border="0" src="<%= domainApp %>/image/sort_arrow_down.png" width="13" height="7" alt="Sort"></logic:equal>
</div>

<div class="adv_search_header <logic:equal name="sortBy" value="shotNumber">sorted </logic:equal>number"><a href="<%= domainApp %>/recentSearchResults.do?searchMethod=recentImageSearch&sortBy=shotNumber">Number</a>
<logic:equal name="sortBy" value="shotNumber"><img border="0" src="<%= domainApp %>/image/sort_arrow_down.png" width="13" height="7" alt="Sort"></logic:equal>
</div>


<% // What is going on here?  Name doesn't display %>
<div class="adv_search_header <logic:equal name="sortBy" value="name">sorted </logic:equal>name"><a href="<%= domainApp %>/recentSearchResults.do?searchMethod=recentImageSearch&sortBy=name">Name</a>
<logic:equal name="sortBy" value="name"><img border="0" src="<%= domainApp %>/image/sort_arrow_down.png" width="13" height="7" alt="Sort"></logic:equal>
</div>

<div class="adv_search_header <logic:equal name="sortBy" value="artist">sorted </logic:equal>author"><a href="<%= domainApp %>/recentSearchResults.do?searchMethod=recentImageSearch&sortBy=artist">Author</a>
<logic:equal name="sortBy" value="artist"><img border="0" src="<%= domainApp %>/image/sort_arrow_down.png" width="13" height="7" alt="Sort"></logic:equal>
</div>

<div class="adv_search_header <logic:equal name="sortBy" value="group">sorted </logic:equal>institution"><a href="<%= domainApp %>/recentSearchResults.do?searchMethod=recentImageSearch&sortBy=group">Institution</a>
<logic:equal name="sortBy" value="group"><img border="0" src="<%= domainApp %>/image/sort_arrow_down.png" width="13" height="7" alt="Sort"></logic:equal>
</div>

<div class="adv_search_header <logic:equal name="sortBy" value="uploadDate">sorted </logic:equal>upload_date"><a href="<%= domainApp %>/recentSearchResults.do?searchMethod=recentImageSearch&sortBy=uploadDate">Upload Date</a>
<logic:equal name="sortBy" value="uploadDate"><img border="0" src="<%= domainApp %>/image/sort_arrow_down.png" width="13" height="7" alt="Sort"></logic:equal>
</div>


<div class="clear"></div>


<% if (searchResults.getResults().size() < 1) { %>

Sorry, nothing matches your request.

<% } else if (searchResults.getResults().size() > 0) { %>
<hr />

<% } %>

<% if (request.getParameter("sortBy") != null) {
		searchResults.sortBy(request.getParameter("sortBy")); 
	}
%>

<logic:iterate id="row" name="searchResults" property="results" indexId="index" length="<%= length %>" offset="<%= offset %>" >

<%
  String prettycode = ((ResultItem) row).getCode();
  String prettyshot = ((ResultItem) row).getShotType();  
  String prettynumber = ((ResultItem) row).getShotNumber();
  String prettyname = ((ResultItem) row).getName();
  String prettyartist = ((ResultItem) row).getArtist();
  String prettygroup = ((ResultItem) row).getGroup();
  String prettyGroupName = ((ResultItem) row).getGroupName();
  String prettygenus = ((ResultItem) row).getGenus();
  String prettyspecies = ((ResultItem) row).getSpecies();
  String prettysubspecies = ((ResultItem) row).getSubspecies();
  String prettyuploaddate = ((ResultItem) row).getUploadDate();

  //if (AntwebProps.isDevMode()) AntwebUtil.log("recentSearchResults-body.jsp group:" + prettyGroupName);
%>

<!-- bean :define id="prettycode" name="row" property="code"/>
<bean :define id="prettyshot" name="row" property="shotType"/>
<bean :define id="prettynumber" name="row" property="shotNumber"/>
<bean :define id="prettyname" name="row" property="name"/>
<bean :define id="prettyartist" name="row" property="artist"/>
<bean :define id="prettygroup" name="row" property="group"/>
<bean :define id="prettygenus" name="row" property="genus"/>
<bean :define id="prettyspecies" name="row" property="species"/>
<bean :define id="prettyuploaddate" name="row" property="uploadDate"/ -->


<div class="adv_search_recent_image">
<logic:notEmpty name="row" property="code">

<%   if (ownerPriv) {   %>
<input type="checkbox" name="chosen" value="<bean:write name="index"/>"/>
<% } %>

<!-- <a href="specimen.do?name=<bean:write name="row" property="code"/>"> -->
<a href="<%= domainApp %>/bigPicture.do?name=<%= ((String) prettycode) %>&number=<logic:notEmpty name="row" property="shotNumber"><%= ((String) prettynumber) %></logic:notEmpty>&shot=<logic:notEmpty name="row" property="shotType"><%= prettyshot %></logic:notEmpty>">
<b><%= ((String) prettycode).toUpperCase() %></b>
</a>
</logic:notEmpty>
&nbsp;
</div>


<div class="adv_search_shot">
<logic:notEmpty name="row" property="shotType">
<%= prettyshot %>
</logic:notEmpty>
&nbsp;
</div>

<div class="adv_search_number">
<logic:notEmpty name="row" property="shotNumber">
<%= prettynumber %>
</logic:notEmpty>
&nbsp;
</div>

<div class="adv_search_name">
<a href="<%= AntwebProps.getDomainApp() %>/description.do?taxonName=<%= ((ResultItem) row).getName() %>">
<logic:notEmpty name="row" property="genus">
<%= new Formatter().capitalizeFirstLetter((String) prettygenus) %>
</logic:notEmpty>

<logic:notEmpty name="row" property="species">
<%= prettyspecies %>
</logic:notEmpty>

<logic:notEmpty name="row" property="subspecies">
<%= prettysubspecies %>
</logic:notEmpty>
</a>
&nbsp;
</div>

<div class="adv_search_author">
<logic:notEmpty name="row" property="artist">
<%= prettyartist %>
</logic:notEmpty>
&nbsp;
</div>

<div class="adv_search_institution">
<logic:notEmpty name="row" property="group">
<%= prettygroup %>
</logic:notEmpty>
&nbsp;
</div>

<div class="adv_search_upload_date">
<logic:notEmpty name="row" property="uploadDate">
<%= prettyuploaddate %>
</logic:notEmpty>
&nbsp;
</div>

<logic:notEmpty name="row" property="code">
&nbsp;
</logic:notEmpty>

<div class=clear></div>
</logic:iterate>

<%
  if (searchResults.getResults().size() > intLength) {
%>
<div class="paging_display">
<%
    int the_page = 0;
    the_page = (intOffset + intLength) / intLength;
    int adjacents = 3;
    int prev = the_page-1;
    int next = the_page+1;

    next = (next*intLength) - intLength;
    prev = (prev*intLength) - intLength;

    Double foo = new Double(searchResults.getResults().size() / intLength);
    int numNexts = (new Double(java.lang.Math.floor(foo))).intValue() + 1;
    int nextOffset = 0;
    int index = 1;
    if (numNexts > 1) {
        if (the_page > 1) {
%>
<span class="not_on_page"><a href="<%= domainApp %>/recentSearchResults.do?searchMethod=recentImageSearch&offset=0&length=<%= length %>&sortBy=<%= request.getParameter("sortBy") %>">&lsaquo; FIRST</a></span> <span class="not_on_page"><a href="<%= domainApp %>/recentSearchResults.do?searchMethod=recentImageSearch&offset=<%= prev %>&length=<%= length %>&sortBy=<%= request.getParameter("sortBy") %>">&#171; PREVIOUS</a></span>
<%
        }
        if (numNexts < 7 + (adjacents * 2)) {
            for (int counter = 1; counter <= numNexts; counter++) {
                if (counter == the_page) {
%>
<span class="on_page"><a href="<%= HttpUtil.getTarget(request) %>"><%=counter %></a></span>
<%
                } else {
%>
      <span class="not_on_page"><a href="<%= domainApp %>/recentSearchResults.do?searchMethod=recentImageSearch&offset=<%= (counter*intLength) - intLength %>&length=<%= length %>&sortBy=<%= request.getParameter("sortBy") %>"><%=counter%></a></span>
<%
                }
            }
        } else if (numNexts >= 7 + (adjacents * 2)) {
            if(the_page < 1 + (adjacents * 3)) {
%>
<%
                for (int counter = 1; counter < 4 + (adjacents * 2); counter++) {
                    if (counter == the_page) {
%>
<span class="on_page"><a href="<%= HttpUtil.getTarget(request) %>"><%=counter %></a></span>
<%
                    } else {
%>
      <span class="not_on_page"><a href="<%= domainApp %>/recentSearchResults.do?searchMethod=recentImageSearch&offset=<%= (counter*intLength) - intLength %>&length=<%= length %>&sortBy=<%= request.getParameter("sortBy") %>"><%=counter%></a></span>
<%
                    }
                }		
            } else if (numNexts - (adjacents * 2) > the_page && the_page > (adjacents * 2)) {
                for (int counter = the_page - adjacents; counter <= the_page + adjacents; counter++) {
                    if (counter == the_page) {
%>
<span class="on_page"><a href="<%= HttpUtil.getTarget(request) %>"><%=counter %></a></span>
<%
                    } else {
%>
      <span class="not_on_page"><a href="<%= domainApp %>/recentSearchResults.do?searchMethod=recentImageSearch&offset=<%= (counter*intLength) - intLength %>&length=<%= length %>&sortBy=<%= request.getParameter("sortBy") %>"><%=counter%></a></span>
<%
                    }
                }
            } else {
                for (int counter = numNexts - (1 + (adjacents * 3)); counter <= numNexts; counter++) {
                    if (counter == the_page) {
%>
<span class="on_page"><%=counter %></span>
<%
                    } else {
%>
      <span class="not_on_page"><a href="<%= domainApp %>/recentSearchResults.do?searchMethod=recentImageSearch&offset=<%= (counter*intLength) - intLength %>&length=<%= length %>&sortBy=<%= request.getParameter("sortBy") %>"><%=counter%></a></span>
<%
                    }
                }
            }
        }
        if (the_page < numNexts-1) {
%>
<span class="not_on_page"><a href="<%= domainApp %>/recentSearchResults.do?searchMethod=recentImageSearch&offset=<%= next %>&length=<%= length %>&sortBy=<%= request.getParameter("sortBy") %>">NEXT &rsaquo;</a></span> <span class="not_on_page"><a href="<%= domainApp %>/recentSearchResults.do?searchMethod=recentImageSearch&offset=<%= (numNexts-1)*intLength %>&length=<%= length %>&sortBy=<%= request.getParameter("sortBy") %>">LAST &#187;</a></span>
<%
        }
    } // end   
%>
</div>
<%  // }  // end for loop
  } // end if 
%>

<%   if (ownerPriv && (searchResults.getResults().size() > 0)) {   %>
<input type="submit" class="submit" name="delete" value="Delete Selected Images">
<% } %>

</html:form> 

<div class="clear"></div>

<!-- form action="mapResults.do" name="taxaFromSearchForm" method="POST" -->
<!-- input type="hidden" name="resultRank" value="specimen"/ -->
<!-- form action="compareResults.do" name="taxaFromSearchForm" method="POST" -->

</div>
