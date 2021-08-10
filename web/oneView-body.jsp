<%@ page language="java" %>
<%@ page import = "org.calacademy.antweb.Formatter" %>
<%@ page import = "org.calacademy.antweb.*" %>
<%@ page import = "org.calacademy.antweb.util.*" %>
<%@ page import = "org.calacademy.antweb.util.AntwebProps" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
    
<%
  if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
%>    
<jsp:useBean id="taxon" scope="session" class="org.calacademy.antweb.Taxon" />

<jsp:useBean id="specimen" scope="session" class="org.calacademy.antweb.Specimen" />
<jsp:setProperty name="specimen" property="*" />

<logic:present parameter="shot">
  <bean:parameter id="shot" name="shot" />
</logic:present>
<logic:notPresent parameter="shot">
  <bean:define id="shot" value="Head" scope="page"/>
</logic:notPresent>

<%
if (AntwebProps.isDevMode()) AntwebUtil.log("oneView-body.jsp");

    java.util.Hashtable labelMap = new java.util.Hashtable();
    labelMap.put("Head","h");
    labelMap.put("Profile","p");
    labelMap.put("Dorsal","d");
    labelMap.put("Label","l");
    labelMap.put("Ventral","v");
 
    String shot = (String) pageContext.getAttribute("shot");

if (AntwebProps.isDevMode()) AntwebUtil.log("oneView-body.jsp shot:" + shot); 

    String shortHand = (String) labelMap.get(shot);
    String preShortHand = shortHand;
    String rank = request.getParameter("rank");
    String showRank = request.getParameter("showRank");
    if (showRank == null) { showRank = rank; }
    // this hack is because specimens have their shots as h1, p1....
    if (Rank.SPECIES.equals(showRank) || Rank.SUBSPECIES.equals(showRank)) { // ADDED
      shortHand = shortHand + "1";
    }
    pageContext.setAttribute("shortHand",shortHand, PageContext.REQUEST_SCOPE);

    String dagger = "";
    if (taxon.getIsFossil()) dagger = "&dagger;";
    // org.calacademy.antweb.Map map = taxon.getMap();
    String object = "taxonName";
    String objectName = taxon.getTaxonName();
%>

<script type="text/javascript">
function changeView(newView, linkParams, showRank) {
  window.location = "getComparison.do?shot=" + newView + "&" + linkParams +"&showRank=" + showRank;
}
</script>
<!-- was window.location = "oneView.do?shot=" + newView + "&showRank=" + showRank;  -->

<!-- oneView-body.jsp -->

<jsp:useBean id="showTaxon" scope="session" class="org.calacademy.antweb.Taxon" />
<jsp:setProperty name="taxon" property="*" />

<!-- jsp:useBean id="mykids" scope="session" class="java.util.ArrayList"/ -->

<bean:define id="prettyrank" name="showTaxon" property="rank"/>

<div id="page_contents">
<%@ include file="/common/taxonTitle.jsp" %>

<%
    Overview overview = OverviewMgr.getOverview(request);

    String linkParams = taxon.getTaxonomicBrowserParams(overview);        
    //if (AntwebProps.isDevMode()) AntwebUtil.log("oneView-body.jsp linkParams:" + linkParams);
%>
	<div class="links">
		<ul>
			<li><a class="clean_url overview" href="description.do?<%=linkParams%>">Overview</a></li>
			<li><a class="clean_url list" href="browse.do?<%=linkParams%>"><%= Rank.getNextPluralRank(rank) %></a></li>
			<li><a class="clean_url images" href="images.do?<%=linkParams%>">Images</a></li>

<% if (taxon.hasMap()) { %>
			<li><a class="clean_url map" href="bigMap.do?<%= object %>=<%= objectName %>&<%= overview.getParams() %>">Map</a></li>
<% } %>
		</ul>

   </div>

	<%@ include file="/common/viewInAntCat.jsp" %>
	
	<div class="clear"></div>
</div>

<%@ include file="/common/taxonomicHierarchy.jsp" %>

<div id="totals_and_tools_container">
	<div id="totals_and_tools">
		<h2 class="display_count">Comparing <%= shot %> Views of <%= new Formatter().capitalizeFirstLetter((String) prettyrank) %> <bean:write name="showTaxon" property="prettyName" />.</h2>
		<span id="sub_taxon">Click image for higher resolution.</span>
		<div id="thumb_toggle"><a href="<%= util.getDomainApp() %><%= facet %>?<%=linkParams%>">Back to <%= new Formatter().capitalizeFirstLetter((String) prettyrank) %> <bean:write name="showTaxon" property="prettyName" /></a></div>
	</div>
	<div class="clear"></div>
	<div class="tools" id="compare_tools">
	
	<% 
	if (AntwebProps.isDevMode()) AntwebUtil.log("oneView-body.jsp showRank:" + showRank + " linkParams:" + linkParams); 
	/* Mark Changes, Oct 7th, incomplete.  Add linkParams below.  link_params was changed to linkParams.
	   New changeView method created with three parameters (linkParams added).  Javascript does not seem
	   to be reloading, and so these changes are not tested and very well may not work.  It was not working
	   beforehand, and is hopefully a step in the right direction.  Luke has been pointed to this work.
	   */

	  String chosen = "";
	  chosen = HttpUtil.getParamsStr("chosen", request);
	  if (AntwebProps.isDevMode()) chosen = "";   
	  if (AntwebProps.isDevMode()) AntwebUtil.log("oneView-body.jsp chosen:" + chosen);

	%>   
	<b>Show:&nbsp;&nbsp; <input type="radio" name="view" value="head" onClick="changeView('Head', '<%= linkParams + chosen %>', '<%= showRank %>');" <%= shot.equals("Head")?"checked":"" %>> Head
	&nbsp;&nbsp; <input type="radio" name="view" onClick="changeView('Profile', '<%= linkParams + chosen %>','<%=showRank%>');" value="profile"  <%= shot.equals("Profile")?"checked":"" %>> Profile
	&nbsp;&nbsp; <input type="radio" name="view" value="dorsal" onClick="changeView('Dorsal', '<%= linkParams + chosen %>','<%=showRank %>');"  <%= shot.equals("Dorsal")?"checked":"" %>> Dorsal 
	&nbsp;&nbsp; <input type="radio" name="view" value="label" onClick="changeView('Label', '<%= linkParams + chosen  %>','<%=showRank%>');"  <%= shot.equals("Label")?"checked":"" %>> Label </b>
	</div>
</div>

<div id="page_data">

<html:form action="/chooseComparison">

<%
   java.util.ArrayList theChildren = showTaxon.getChildren();
   //java.util.ArrayList theChildren = mykids;
   java.util.Iterator iterator = theChildren.iterator();
   int index = 1;
   int first = 1;
   int second = 2;
   int total = showTaxon.getChildrenCount();

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
   String all = new String();
   all = "a";

   int rows = (total/3) + 1;
   int loop=0;
   while (loop < rows) {
      int innerloop = 0;
      int position = 1;
      while (iterator.hasNext()) {
        if (position == 3) { position = 1; }
        org.calacademy.antweb.Taxon thisChild = 
             (org.calacademy.antweb.Taxon) iterator.next();
        if (thisChild == null || thisChild.getImages() == null) {
          if (AntwebProps.isDevMode()) AntwebUtil.log("oneView-body.jsp thisChild:" + thisChild.getTaxonName() + " has no images.");
          continue;
        }
        if (thisChild.getImages().containsKey(shortHand)) {
          String picture = ((org.calacademy.antweb.SpecimenImage) thisChild.getImages().get(shortHand)).getThumbview();
          String highres = ((org.calacademy.antweb.SpecimenImage) thisChild.getImages().get(shortHand)).getHighres();
          String code = ((org.calacademy.antweb.SpecimenImage) thisChild.getImages().get(shortHand)).getCode();
        %>
<% if (choice_is.equals(all)) { %>
<div class="slide half <% if (position == first) { %> first<% } %><% if (position == second) { %> last<% } %> ratio">
    <div class="adjust_compare">
        <img src="<%= AntwebProps.getImgDomainApp() %><%= picture %>" onclick="window.location='bigPicture.do?name=<%= code %>&number=1&shot=<%= preShortHand %>'"><br />
        <a href="bigPicture.do?name=<%= code %>&number=1&shot=<%= preShortHand %>"><%= thisChild.getPrettyName() %></a>
</div>
</div>
<% } else { %>
          <div class="slide half<% if (position == first) { %> first<% } %><% if (position == second) { %> last<% } %>" style="background-image:url('<%= AntwebProps.getImgDomainApp() %><%= picture %>')" onclick="window.location='bigPicture.do?name=<%= code %>&number=1&shot=<%= preShortHand %>'">
              <div class="hover half" onclick="window.location='bigPicture.do?name=<%= code %>&number=1&shot=<%= preShortHand %>'"></div>
              <div class="top_gradient half"></div>
              <div class="name half"> 
                  <a href="bigPicture.do?name=<%= code %>&number=1&shot=<%= preShortHand %>"><%= thisChild.getPrettyName() %></a>
              </div>
              <div class="clear"></div>
          </div>
<% } %>
          <% if (innerloop < 2) { %>
          <% } 

          innerloop++;
          position++; 
          if (innerloop == 3) { %>
         <%  } 
       }
     } 
     if (position == 2) {
%>
         <div class="clear"></div>
<%
     } 
     loop++;
   %>

  <% } %>
<div class="clear"></div>
</html:form>

<div class="clear"></div>
<div class="page_spacer"></div>

</div>
