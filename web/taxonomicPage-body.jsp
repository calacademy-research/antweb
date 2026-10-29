<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.sort.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;

    Login accessLogin = LoginMgr.getAccessLogin(request);
    Group accessGroup = GroupMgr.getAccessGroup(request);
    
//  Feb2020    
//    TaxaPage taxaPage = (TaxaPage) session.getAttribute("taxaPage");     
      TaxaPage taxaPage = (TaxaPage) request.getAttribute("taxaPage");     

    // An overview is a Museum, Bioregion, Geolocale, Project.
    Overview overview = OverviewMgr.getOverview(request);
    boolean isMuseum = overview instanceof Museum;
    boolean isGeolocale = overview instanceof Geolocale;
    boolean isProject = overview instanceof Project;
    boolean isBioregion = overview instanceof Bioregion;

    String pageRank = request.getParameter("rank");
    //String pageRank = taxaPage.getRank();
    //String georank = taxaPage.getGeorank();
    //A.log("taxonomicPage-body.jsp pageRank:" + pageRank);
    
    String pageType = "taxaPage";

    boolean displayGlobal = !isProject && (Rank.SPECIES.equals(pageRank)); // was: isGeolocale && 
    //A.log("taxonomicPage-body.jsp displayGlobal:" + displayGlobal + " pageRank:" + pageRank);
    //A.log("taxonomicPage-body.jsp isMuseum:" + isMuseum + " str:" + HttpUtil.getParamsString("museumCode", request) + " overview:" + overview.getClass());
     
    
    String title = "";
    String thisTarget = HttpUtil.getTarget(request);
    
    boolean isOnlyShowUnImaged = false;
    String isImaged = request.getParameter("isImaged");    
    if ((isImaged != null) && (isImaged.equals("false"))) {
      isOnlyShowUnImaged = true;
//      AntwebUtil.log("taxonomicPage-body.jsp 3 isImaged:" + isImaged);
    }
%>

<bean:define id="showNav" value="taxonomic"/>
<input id="for_print" type="text" value="AntWeb. Available from: <%= Html.escAttr(HttpUtil.getFullJspTarget(request)) %>">
<input id="for_web" type="text" value="URL: <%= Html.escAttr(HttpUtil.getFullJspTarget(request)) %>">

<div id="page_contents">

<%@ include file="/common/overviewHeading.jsp" %>

<% if (LoginMgr.isAdmin(request)) { %>
  <% if (LoginMgr.isCurator(request)) { %>
  <%= overview.getChildrenListDisplay("valid", "list", pageRank) %>
  <% } else { %>
  <%= overview.getChildrenListDisplay("live", "list", pageRank) %>
  <% } %>
<% } %>

</div>

<div class="page_divider taxonomic"></div>

<%@include file="/common/taxonSetHeaderNotes.jsp" %>

<%
	// To avoid ConcurrentModificationException
	ArrayList<Taxon> childrenList = taxaPage.getChildren();
	int childrenCount = childrenList.size();
	Taxon[] childrenArray = new Taxon[childrenCount];
	childrenList.toArray(childrenArray);
    ArrayList<Taxon> children = new ArrayList<Taxon>(childrenList);

   String statusStr = "";
   if (!"all".equals(taxaPage.getStatusSetStr())) statusStr = Formatter.initCap(taxaPage.getStatusSetStr()) + " ";

    //A.log("taxonomicPage-body.jsp 4 childrenCount:" + childrenCount);

	String childSizeStr = Formatter.commaFormat(childrenCount) + " " + statusStr + Rank.getPluralRank(pageRank);

	// Q12 (Brian Fisher, 2026-10-22): "Unimaged" = no photo of the ant anywhere in AntWeb; a name whose specimens have only
	// label photos is unimaged (TaxaPage.findUnimaged, the rule of the API's /unimagedGeolocaleTaxa). Until then it meant
	// no image of the name from this place, labels included, and the header fell back to the list size when none (B21).
	java.util.Set<String> unimagedNames = taxaPage == null ? null : taxaPage.getUnimagedNames();
	String unImagedNote = "";
	if (isOnlyShowUnImaged && unimagedNames != null) {
	    int unImagedCount = 0;
	    for (Taxon child : childrenArray) {
	        if (unimagedNames.contains(child.getTaxonName())) ++unImagedCount;
	    }
	    childSizeStr = "<span class=\"numbers\">" + unImagedCount + "</span> Unimaged " + Rank.getPluralRank(pageRank) + " (out of <span class=\"numbers\">" + childrenCount + "</span>)";
	    unImagedNote = "<p id=\"unimaged-note\">Unimaged: no photo of the ant anywhere in AntWeb, from any place. A name whose specimens have only label photos counts as unimaged.</p>";
    }
%>

<div id="totals_and_tools_container">
    <div id="totals_and_tools">
        <h2 class="display_count"><%= childSizeStr %></h2>
        <%= unImagedNote %>

<%
      String statusSet = taxaPage.getStatusSetStr();
      String statusSetSize = taxaPage.getStatusSetSize();
      //A.log("taxonomicPage-body.jsp statusSetStr:" + statusSet + " statusSetSize:" + statusSetSize);
%>
        <%@ include file="/common/statusesDisplay.jsp" %>
<%
  	 // Edit Species List   |   Show Specimen Taxa     
	  // if isMappable and curator is curator there.  
	boolean canShowSpeciesListTool = false;
	boolean canShowSpecimenTaxa = false;
	if (overview instanceof Geolocale || overview instanceof Project) {
      canShowSpeciesListTool = overview.isCanShowSpeciesListTool(accessLogin) || LoginMgr.isAdmin(accessLogin);
      //A.log("taxonomicPage-body.jsp overview:" + overview.getName() + " isCanShowSpeciesListTool:" + canShowSpeciesListTool + " parent:" + ((Adm1)overview).getParent());
      //A.log("taxonomicPage-body.jsp geolocales:" + accessLogin.getGeolocales());
      thisTarget = HttpUtil.removeParam(thisTarget, "specimen");
      if (canShowSpeciesListTool) { 
        String searchCriteria = "";
        if (overview instanceof Geolocale) {
          // Encoded: a place name such as Cote d'Ivoire must not end the link early.
          if (overview instanceof org.calacademy.antweb.geolocale.Country) searchCriteria = "&refSpeciesListType=advSearch&doSearch=searchResults&country=" + Html.url(overview.getName());
          if (overview instanceof Adm1) searchCriteria = "&refSpeciesListType=advSearch&doSearch=searchResults&country=" + Html.url(((Adm1) overview).getParent()) + "&adm1=" + Html.url(overview.getName());
        }
        String mapSpeciesList1Name = (overview instanceof Adm1) ? "" + ((Adm1) overview).getId() : overview.getName();
     %>
<a href='<%= Html.escAttr(AntwebProps.getDomainApp() + "/speciesListTool.do?mapSpeciesList1Name=" + Html.url(mapSpeciesList1Name) + searchCriteria + "&isFreshen=true") %>'>Edit Species List</a>
  <%

        thisTarget = HttpUtil.removeParam(thisTarget, "specimen");
        //A.log("taxonomicPage-body.jsp thisTarget:" + thisTarget);
      }

	  canShowSpecimenTaxa = overview.isCanShowSpecimenTaxa();
      if (canShowSpeciesListTool && canShowSpecimenTaxa) { %>
&nbsp; | &nbsp;
   <% }
      if (canShowSpecimenTaxa) {
        //A.log("taxonomicPage-body.jsp taxaPage:" + taxaPage + " thisTarget:" + thisTarget);

        if (!taxaPage.isWithSpecimen()) { %>
<a href='<%= Html.escAttr(thisTarget + "&specimen=true") %>' title='Show additional taxa from specimen data.'>Show Specimen Taxa</a>
     <% } else { 
          thisTarget = HttpUtil.removeParam(thisTarget, "specimen");
    %>
<a href='<%= Html.escAttr(thisTarget + "&specimen=false") %>' title='hide additional taxa from specimen data'>Hide Specimen Taxa</a>
     <% }
      }
      // Public, as on the overview page (Brian 2026-10-20): the specimen records of this place, any name.
      if (overview instanceof LocalityOverview && !HttpUtil.isBot(request)) {
        String listCriteria = ((LocalityOverview) overview).getSearchCriteria();
        if (listCriteria != null && !"".equals(listCriteria)) { %>
&nbsp; | &nbsp;<a rel='nofollow' href='<%= AntwebProps.getDomainApp() %>/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=formicidae&<%= listCriteria %>&resultRank=specimen' title='Every specimen record collected here, under any name'>List all specimens collected in <%= Html.esc(overview.getName()) %></a>
     <% }
      }
    } %>

    </div>
    <div class="clear"></div>
</div>
<%
  
  boolean isWithSpecimen = taxaPage.isWithSpecimen();
  // 2026-10-28: the place's specimens identified to genus only (genus rows, the line under the species list)
  org.calacademy.antweb.home.GenusOnly genusOnly = taxaPage.getGenusOnly();
%>

    <%@ include file="/taxonReportBody.jsp" %>

