<%@ page language="java" %>
<%@ page import = "org.calacademy.antweb.Formatter" %>
<%@ page import = "org.calacademy.antweb.*" %>
<%@ page import = "org.calacademy.antweb.util.*" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<jsp:useBean id="showTaxon" scope="session" class="org.calacademy.antweb.Taxon" /> 
<jsp:useBean id="taxon" scope="session" class="org.calacademy.antweb.Taxon" /> 

<jsp:useBean id="specimen" scope="session" class="org.calacademy.antweb.Specimen" />
<jsp:setProperty name="specimen" property="*" />

<script type="text/javascript">
<!--

function submitAndFocus(theForm) {
  // theForm.target="compare";
  // openAndFocus("","compare");
  theForm.submit();
}

function chooseTarget(theForm) {

	var chosenValue = "";
	var select = theForm.shot;
	if (select != null) {
		var chosen = select.selectedIndex;
		chosenValue = select[chosen].value;
	}
	theForm.submit();
	
}

// -->
</script>

<%
    java.util.ArrayList<String> allShots = new java.util.ArrayList<String>();
    allShots.add("h");
    allShots.add("p");
    allShots.add("d");
    allShots.add("l");

    java.util.ArrayList<String> allSpecimenShots = new java.util.ArrayList<String>();
    allSpecimenShots.add("h1");
    allSpecimenShots.add("p1");
    allSpecimenShots.add("d1");
    allSpecimenShots.add("l1");

    java.util.ArrayList<String> shotsToShow;


    String rank = request.getParameter("rank");
    String showRank = request.getParameter("showRank");
    if (showRank == null) { showRank = rank; }

    Overview overview = OverviewMgr.getOverview(request);
    
   if (Rank.SPECIES.equals(showRank) || Rank.SUBSPECIES.equals(showRank)) {
     shotsToShow = allSpecimenShots;
   } else {
     shotsToShow = allShots;
   }

   String dagger = "";
   if (taxon.getIsFossil()) dagger = "&dagger;";
   //org.calacademy.antweb.Map map = taxon.getMap();
   String object = "taxonName";
   String objectName = taxon.getTaxonName();

   if (rank == null) {
     rank = showTaxon.getRank();  // This can happen with the old style urls: /getComparison.do?name=ragusai&genus=hypoponera& ...
     //AntwebUtil.log("comparison-body.jsp Problem with rank?  rank:" + rank + " Request:" + HttpUtil.getTarget(request));
   } 
%>

<bean:define id="prettyrank" name="showTaxon" property="rank"/>

<div id="page_contents">
<h1>
<%= new Formatter().capitalizeFirstLetter((String) prettyrank) %>: <%= dagger %><bean:write name="showTaxon" property="prettyName"/>
<% if (taxon.getIsValid()) { %>
<img src="image/valid_name.png" border="0" title="Valid Name">
  <% } %>
</h1>
	<div class="links">
		<ul>
			<li><a class="clean_url overview" href="description.do?<%=taxon.getBrowserParams()%>">Overview</a></li>
			<li><a class="clean_url list" href="browse.do?<%=taxon.getBrowserParams()%>"><%= Rank.getNextPluralRank(rank) %></a></li>
			<li><a class="clean_url images" href="images.do?<%=taxon.getBrowserParams()%>">Images</a></li>
<%
//String showMapLink = map.getGoogleMapFunction();
//if ((showMapLink != null) && (showMapLink.length() > 0)) {
  if (taxon.hasMap()) {
%>
			<li><a class="clean_url map" href="bigMap.do?<%= object %>=<%= objectName %>&<%= overview.getParams() %>">Map</a></li>
<%
  }
%>
		</ul>
	</div>

	<%@ include file="/common/viewInAntCat.jsp" %>
	
	<div class="clear"></div>
</div>

<%@ include file="/common/taxonomicHierarchy.jsp" %>

<div id="totals_and_tools_container">
    <div id="totals_and_tools">
		<h2 class="display_count">Comparison within 
		<bean:write name="showTaxon" property="rank" />
		<bean:write name="showTaxon" property="prettyName" />
		</h2>
		<br>
		<div id="thumb_toggle"><a href="<%= util.getDomainApp() %><%= facet %>?<%=taxon.getBrowserParams()%>">Back to <%= new Formatter().capitalizeFirstLetter((String) prettyrank) %> <bean:write name="showTaxon" property="prettyName" /></a></div>
		<div class="clear"></div>

		<!-- comparison-body.jsp -->
		<!-- html :form action="getComparison" -->
		<form id="getComparison" action="<%= AntwebProps.getDomainApp() %>/getComparison.do">

		<input type="hidden" name="name" value="<bean:write name="showTaxon" property="name"/>">

		<% if (AntwebProps.isDevMode()) AntwebUtil.log("comparison-body.jsp subfamily:" + showTaxon.getSubfamily() + " genus:" + showTaxon.getGenus()); %>

		<input type="hidden" name="family" value="<%= showTaxon.getFamily() %>">
		<input type="hidden" name="subfamily" value="<%= showTaxon.getSubfamily() %>">
		<input type="hidden" name="genus" value="<%= showTaxon.getGenus() %>">
		<input type="hidden" name="species" value="<%= showTaxon.getSpecies() %>">
		<input type="hidden" name="subspecies" value="<%= showTaxon.getSubspecies() %>">

		<input type="hidden" name="rank" value="<%= showTaxon.getRank() %>">

		<input type="hidden" name="overview" value="<%= overview.getName() %>">

		<div class="tools" id="compare_tools">
			<div class="tool_select_toggle" title="Click to select all">
				<input type="checkbox" name="selectall" id="selectall">
			</div>
			<div class="tool_text">
			<%
			  String nextRank = taxon.getNextRank();
			%>
			
				Select <%=taxon.getNextRank().toLowerCase()%>, and view, and click "Compare Selected".
				<p>
				View:&nbsp;&nbsp; <input id="head_top" type="radio" name="shot" value="Head" checked> Head
				&nbsp;&nbsp; <input id="profile_top" type="radio" name="shot" value="Profile"> Profile
				&nbsp;&nbsp; <input id="dorsal_top" type="radio" name="shot" value="Dorsal"> Dorsal
				&nbsp;&nbsp; <input id="ventral_top" type="radio" name="shot" value="Ventral"> Ventral
				&nbsp;&nbsp; <input id="label_top" type="radio" name="shot" value="Label"> Label
                &nbsp;&nbsp;&nbsp;&nbsp;<input id="compare_form" class="submit" type="submit" value="Compare Selected">
				</p>
			</div>
			<div class="clear"></div>
		</div>
	</div>
</div>

<div id="page_data">
<% 
  int position = 0; 
  int first = 1; 
  int fourth = 4; 

  // To avoid concurrentModificationException
  ArrayList<Taxon> childrenList = showTaxon.getChildren();
  if (childrenList == null) {
    AntwebUtil.log("comparision-body.jsp ChildrenList is null");
    return;
  }

  int childrenCount = childrenList.size();
  Taxon[] childrenArray = new Taxon[childrenCount];
  childrenList.toArray(childrenArray);

  ArrayList<Taxon> childrenArrayList = new ArrayList(Arrays.asList(childrenArray));
  String orderBy = (String) request.getParameter("orderBy"); // "subgenera";
  if (orderBy != null) {
    Taxon.sortTaxa(orderBy, childrenArrayList);
  }

  for (Taxon child : childrenArrayList) {
    if (child.getHasImages()) { 
      String displayName = child.getFullName(); 
%>

  <div class="data_checkbox" style="float:left; margin-top:2px;"><input type="checkbox" name="chosen" value="<%= child.getFullName() %>"></div>

  <logic:equal name="showTaxon" property="nextRank" value="Specimens"> 
    <div class="ratio_name"><a href="specimen.do?code=<%= child.getCode() %>&<%= overview.getParams() %>"><%= child.getPrettyName() %></a>
    <a href="<%= AntwebProps.getDomainApp() %>/description.do?taxonName=<%= child.getTaxonName() %>&<%= overview.getParams() %>"><%= Taxon.getPrettyTaxonName(child.getParentTaxonName()) %></a> 
    </div>
  </logic:equal>

  <logic:notEqual name="showTaxon" property="nextRank" value="Specimens"> 
    <div class="ratio_name"><a href="description.do?<%= child.getBrowserParams() %>"><%= child.getPrettyName() %></a></div>
  </logic:notEqual>

  <div class="clear"></div>

  <% // if (AntwebProps.isDevMode()) AntwebUtil.log("comparison-body.jsp name:" + child.getPrettyName() + " browserParams:" + child.getBrowserParams()); %>

  <%  for (String shot : shotsToShow) {
        ++position;
        if (position == 5) {
          position = 1;
        } %>

        <logic:iterate id="element" collection="<%= child.getImages() %>">
          <logic:equal value="<%= (String) shot %>" name="element" property="key">

            <div class="slide medium <% if (position == first) { %> first<% } %><% if (position == fourth) { %> last<% } %> ratio">
  
            <bean:define id="imgCode" name="element" property="value.code" />
            <% String bigImgLink = AntwebProps.getDomainApp() + "/bigPicture.do?code=" + imgCode + "&shot=" + ((String) shot).substring(0, 1) + "&number=" + ((String) shot).substring(1) + "&" + overview.getParams(); %>
            <div class="adjust"><img class="medres" src="<%= AntwebProps.getImgDomainApp() %><bean:write name="element" property="value.thumbview" />" onclick="window.location='<%= bigImgLink %>';"></div>
            </div>
          </logic:equal>
        </logic:iterate>

   <% } %>

  <div class="clear"></div>

 <% } // child.getHasImages() %>

<% 
  } // for children 
%>

</form>

<div class="clear"></div>
<div class="page_spacer"></div>

</div>
