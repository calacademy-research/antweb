<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Taxon" %>
<%@ page import="org.calacademy.antweb.Species" %>
<%@ page import="org.calacademy.antweb.Specimen" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<!-- showBrownse-body.jsp -->
<% //A.log("showBrowse-body.jsp"); %>

<% if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return; 

//A.log("showbrowse.jsp");
%>

<jsp:useBean id="taxon" scope="request" class="org.calacademy.antweb.Taxon" />
<jsp:setProperty name="taxon" property="*" />

<jsp:useBean id="specimen" scope="request" class="org.calacademy.antweb.Specimen" />
<jsp:setProperty name="specimen" property="*" />

<% 

  //if (AntwebProps.isDevMode()) AntwebUtil.log("showBrowse-body.jsp taxon:" + taxon);

  //Login accessLogin = LoginMgr.getAccessLogin(request);

  String mode = "none";  // This parameter is used in the specimenReport.jsp in cases of advancedSearch.

  String thePage = HttpUtil.getTarget(request);
  String pageContainer = "showBrowse";
 
  String pageType = "browse";
 
  Overview overview = OverviewMgr.getOverview(request); 

  org.calacademy.antweb.Formatter formatter = new Formatter();


  String tMuseumCode = HttpUtil.getParamsString("museumCode", request);

   String dagger = "";
   if (taxon.getIsFossil()) dagger = "&dagger;";
 //  org.calacademy.antweb.Map map = taxon.getMap();
   String object = "taxonName";
   String objectName = taxon.getTaxonName();

   // actually childRank. Shared with taxonomicPage which views objects differently.
   String pageRank = Rank.getChildRank(taxon.getRank());
  //A.log("showBrowse pageRank:" + pageRank);    

  boolean isMuseum = overview instanceof Museum;
  boolean isGeolocale = overview instanceof Geolocale;
  boolean isProject = overview instanceof Project;
  boolean isBioregion = overview instanceof Bioregion;

  boolean displayGlobal = !isProject && (Rank.SPECIES.equals(pageRank) || Rank.SPECIMEN.equals(pageRank)); // was: isGeolocale && 
  //A.log("showBrowse-body.jsp displayGlobal:" + displayGlobal + " pageRank:" + pageRank);
  //A.log("showBrowse-body.jsp isMuseum:" + isMuseum + " museumCode:" + tMuseumCode + " str:" + HttpUtil.getParamsString("museumCode", request) + " overview:" + overview.getClass());

  String global = request.getParameter("global");
  boolean isGlobal = "true".equals(global);  

%>
<input id="for_print" type="text" value="AntWeb. Available from: <%= HttpUtil.getFullJspTarget(request)%>">
<input id="for_web" type="text" value="URL: <%= HttpUtil.getFullJspTarget(request)%>">
<div id="page_contents">


<%@ include file="/common/taxonTitle.jsp" %>

	<div class="links">
		<ul>
			<li><a href="<%= AntwebProps.getDomainApp() %>/description.do?<%=taxon.getBrowserParams()%>">Overview</a></li>
			<li><%= Rank.getNextPluralRank(taxon.getRank()) %></li>
			<li><a href="<%= AntwebProps.getDomainApp() %>/images.do?<%=taxon.getBrowserParams()%>">Images</a></li>
	<% 
       //A.log("show-browse.jsp hasMap:" + taxon.hasMap() + " map:" + taxon.getMap());	
       boolean hasMap = false;
       hasMap = taxon.hasMap() || taxon.getMap() != null;
       if (taxon.getChildren().size() > 0) hasMap = true;
       if (hasMap) {
           %>		
			<li><a href="<%= AntwebProps.getDomainApp() %>/bigMap.do?<%= object %>=<%= objectName %>&<%= overview.getParams() %>">Map</a></li>
    <% } // else AntwebUtil.log("info", "showBrowse-body.jsp no map for taxon:" + taxon);  %>
		</ul>	
	</div>

	<%@ include file="/common/viewInAntCat.jsp" %>
	
	<div class="clear"></div>
</div>

<%@include file="/common/taxonomicHierarchy.jsp" %>  
<%@include file="/common/taxonSetHeaderNotes.jsp" %>  

<!-- showBrowse-body.jsp -->

<logic:present name="taxon" property="nextRank">
<bean:define id="taxonChildCount" name="taxon" property="childrenCount" />
<%
int totalImaged = taxon.getUniqueChildImagesCount("p", "h");

//A.log("totalImaged:" + totalImaged + " taxonChildCount:" + taxonChildCount);
String recordCountString = taxonChildCount + " specimens"; 

java.util.ArrayList<Taxon> taxonChildren = taxon.getChildren();

//A.log("showBrowse-body.jsp size:" + taxonChildren.size());
int childrenCount = taxonChildren.size();   
Taxon[] childrenArray = new Taxon[childrenCount];
taxonChildren.toArray(childrenArray);

int imagedSpecimenCount = 0;
for (Taxon t : childrenArray) {
	if (t != null && t.getHasImages()) {
	   ++imagedSpecimenCount;
	}
}

// A.log("showBrowse-body.jsp taxonChildrenSize:" + taxonChildren.size() + " childrenArraySize:" + childrenArray.length + " imagedSpecimenCount:" + imagedSpecimenCount);

%>
<div id="totals_and_tools_container">
    <div id="totals_and_tools">
    
<% int limit = Taxon.MAX_DISPLAY;
   String displayCount = "";
   String limitNote = "";
   String statusStr = "";
   if (!"all".equals(taxon.getStatusSetStr())) statusStr = Formatter.initCap(taxon.getStatusSetStr()) + " ";
   
   if (taxon.getChildrenCount() <= 0) {
     displayCount = "No " + statusStr + taxon.getNextRank();
   } else if (taxon.getChildrenCount() == 1) {
     displayCount = "1 " + statusStr + Rank.getSingularRank(taxon.getNextRank());
   } else {
     displayCount = Formatter.commaFormat(taxon.getChildrenCount()) + " " + statusStr + taxon.getNextRank();

     String limitStr = (String) request.getParameter("limit");
     if (limitStr != null) {
       limit = Integer.parseInt(limitStr);
     }
     if (taxon.getChildrenCount() > limit) {
       String allUrl = HttpUtil.getTargetReplaceParam(request, "limit", "limit=10000");
       String allLink = " See all: <a href='" + allUrl + "'>here</a>";
       limitNote = "(Only the first " + limit + " of " + taxon.getChildrenCount() + " displayed - " + allLink + ").";
     }
   }

   //A.log("showBrowse-body.jsp c:" + taxon.getChildrenCount() + " displayCount:" + displayCount + " nextRank:" + taxon.getNextRank());   

   // 346 Specimens (6 imaged)
%>

    <h2 class="display_count"><%= displayCount %><% if (imagedSpecimenCount > 0) { %> (<span class="numbers"><%= imagedSpecimenCount %></span> imaged)<% } %></h2>

        <div id="totalcount" style="display:none;"><span class="numbers"><%= taxonChildCount %></span> <bean:write name="taxon" property="nextRank" /><% if (imagedSpecimenCount > 0) { %> (<span class="numbers"><%= imagedSpecimenCount %></span> imaged)<% } %></div>
        <div id="imagecount" style="display:none;"><span class="numbers"><%= imagedSpecimenCount %></span> Imaged <bean:write name="taxon" property="nextRank" /> (out of <span class="numbers"><%= taxonChildCount %></span> total)</div>

<%         // AntwebUtil.log("showBrowse-body.jsp taxonChildCount:" +  taxonChildCount + " imagedSpecimenCount:" + imagedSpecimenCount );   // XXX     %>

        <!-- For instance, for formicidae:  480 Genera  21,453 Species  179,000 Specimens   -->
        <span id="sub_taxon">
            <ul>
            <% TaxonSet taxonSet = taxon.getTaxonSet(); 
            %>
                <li><%= taxonSet.getNextSubtaxon(2) %></li>
                <li><%= taxonSet.getNextSubtaxon(3) %></li>
                <li><%= taxonSet.getNextSubtaxon(4) %></li>
                <% if (displayCount.contains("No ") && taxonSet.getSubtaxonCount(1) > 0) { 
                     String showAllLink = HttpUtil.getTargetReplaceParam(request, "statusSet", "statusSet=all");                
                     A.log("showBrowse-body.jsp displayCount:" + displayCount + " subtaxonCount:" + taxonSet.getSubtaxonCount(1) + " target:" + showAllLink);
                %>
                  <li><a href="<%= showAllLink %>">See all</a></li>
                <% } %>

                <% if (displayGlobal) {
                     if (isGlobal) { %>
                       <li><a href="<%= HttpUtil.getTargetReplaceParam(request, "global=true", "") %>">See <%= overview %> only</a></li>
                     <% } else if (!isGlobal) { %>
                       <li><a href="<%= HttpUtil.getTarget(request) + "&global=true" %>">See global set</a></li>                
                <%   }
                   } %>
            </ul>
        </span>
<%
      String statusSet = taxon.getStatusSetStr();
      String statusSetSize = taxon.getStatusSetSize();
      //A.log("showBrowse-body.jsp statusSetStr:" + statusSet + " statusSetSize:" + statusSetSize);   
%>
        <%@ include file="/common/statusesDisplay.jsp" %>
        
        <%  if ("specimen".equals(pageRank) && !"".equals(limitNote)) {
          // Could be implemented for our biggest taxon without too much trouble but it is little over 2000.
          // http://localhost/antweb/browse.do?subfamily=formicinae&genus=camponotus&rank=genus
         %>
           <div class="clear"></div><font color=red><%= limitNote %></font>
         <% }
        
            if (taxon.getChildrenCount() > 0) { %>

            <%@ include file="/common/pageToolLinks.jsp" %>

            <%@ include file="/common/data_download_overlay.jsp" %>

        <%  } %>

        <div class="clear"></div>
    </div>
</div>

<div id="page_data">

<% //if (AntwebProps.isDevMode()) AntwebUtil.log("showBrowse-body.jsp rank:" + taxon.getRank()); %>

<%  //AntwebUtil.log("showBrowse-body.jsp rank:" + taxon.getRank()); 
 if (!"species".equals(taxon.getRank()) && !"subspecies".equals(taxon.getRank())) { %>
    <%@include file="/taxonReport.jsp" %>  
<%  } %>

 <% if (taxon.isBaseTaxon()) { %>
     <% // USED TO HAVE THE TAXONCHILDCOUNT HERE %>

     <%
        String sortBy = (String) request.getParameter("sortBy");
		if (sortBy != null) {
		  if (taxon instanceof Species || taxon instanceof Subspecies) {
			((Species) taxon).sortBy(sortBy);
            A.log("showBrowse-body rank:" + taxon.getRank() + " sortBy:" + sortBy);
          }
		}
		// USED TO HAVE THE RECORDCOUNTSTRING HERE
	  %>
		<input type="hidden" id="project_is" value="<%= overview.getName() %>">

 	 <% if (taxon.getChildrenCount() > 0) {
	 Object child = null;
	 %>
		<%@include file="/specimenReport.jsp" %>  
	 <% } %> 

 <% } %>

</div>
</logic:present>

<div class="clear"></div>
<br /><br />
<a href="mailto:antweb@calacademy.org?subject=Regarding AntWeb page <%= thePage %>">See something amiss? Send us an email.</a>

