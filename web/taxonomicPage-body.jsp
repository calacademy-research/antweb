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

<%
	// To avoid ConcurrentModificationException
	ArrayList<Taxon> childrenList = taxaPage.getChildren();
	int childrenCount = childrenList.size();
	Taxon[] childrenArray = new Taxon[childrenCount];
	childrenList.toArray(childrenArray);
    ArrayList<Taxon> children = new ArrayList<Taxon>(childrenList);

    //A.log("taxonomicPage-body.jsp 4 childrenCount:" + childrenCount);

	// Brian Fisher, 2026-10-30: the header says what the list shows, in the words of the place page's numbers
	// ("2,672 Valid species (of 6,960 species and morphospecies)", "116 Genera (valid genera and morphogenera)").
	// StatusSet.listHeading; the "of" number is the place page's Species / subspecies / morphospecies.
	String childHeading = StatusSet.listHeading(taxaPage.getStatusSetStr(), childrenCount, Rank.getPluralRank(pageRank),
	    Rank.getPluralRank(pageRank), overview == null ? -1 : overview.getSpeciesCount(), overview == null ? null : overview.getName());
	// Names from: Specimens collected here (specimen=true, 2026-10-30): the header says the source.
	if (taxaPage.isWithSpecimen()) childHeading = PlaceTaxonNotes.withSpecimensHeading(childHeading, PlaceTaxonNotes.placeName(overview));
	String childSizeStr = Html.esc(childHeading);

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

	// The header in two lines (Brian Fisher, 2026-10-30): line 1 the header with the Species Status and Names from
	// selectors on the right; line 2 small links (List all specimens collected in <place>; Edit Species List for the
	// place's curators and administrators). The notes on the list's sources are under "About this list" (a details
	// element, web/common/taxonSetHeaderNotes.jsp). "Names from" replaces the Show / Hide Specimen Taxa link and the
	// sentence that pointed to it; specimen=true / false as before, so old links keep working.
	boolean canShowSpeciesListTool = false;
	boolean canShowSpecimenTaxa = false;
	String editSpeciesListUrl = null;
	String allSpecimensCriteria = null;
	if (overview instanceof Geolocale || overview instanceof Project) {
      canShowSpeciesListTool = overview.isCanShowSpeciesListTool(accessLogin) || LoginMgr.isAdmin(accessLogin);
      thisTarget = HttpUtil.removeParam(thisTarget, "specimen");
      if (canShowSpeciesListTool) {
        String searchCriteria = "";
        if (overview instanceof Geolocale) {
          // Encoded: a place name such as Cote d'Ivoire must not end the link early.
          if (overview instanceof org.calacademy.antweb.geolocale.Country) searchCriteria = "&refSpeciesListType=advSearch&doSearch=searchResults&country=" + Html.url(overview.getName());
          if (overview instanceof Adm1) searchCriteria = "&refSpeciesListType=advSearch&doSearch=searchResults&country=" + Html.url(((Adm1) overview).getParent()) + "&adm1=" + Html.url(overview.getName());
        }
        String mapSpeciesList1Name = (overview instanceof Adm1) ? "" + ((Adm1) overview).getId() : overview.getName();
        editSpeciesListUrl = AntwebProps.getDomainApp() + "/speciesListTool.do?mapSpeciesList1Name=" + Html.url(mapSpeciesList1Name) + searchCriteria + "&isFreshen=true";
      }
	  canShowSpecimenTaxa = overview.isCanShowSpecimenTaxa();
      // Public, as on the overview page (Brian 2026-10-20): the specimen records of this place, any name.
      if (overview instanceof LocalityOverview && !HttpUtil.isBot(request)) {
        String listCriteria = ((LocalityOverview) overview).getSearchCriteria();
        if (listCriteria != null && !"".equals(listCriteria)) allSpecimensCriteria = listCriteria;
      }
    }
%>

<div id="totals_and_tools_container">
    <div id="totals_and_tools">
      <div id="list-head" class="list-head">
        <h2 class="display_count"><%= childSizeStr %></h2>
        <div id="list-choices" class="list-choices">
<%
      String statusSet = taxaPage.getStatusSetStr();
      String statusSetSize = taxaPage.getStatusSetSize();
      //A.log("taxonomicPage-body.jsp statusSetStr:" + statusSet + " statusSetSize:" + statusSetSize);
%>
        <%@ include file="/common/statusesDisplay.jsp" %>
<%    if (canShowSpecimenTaxa) {
        boolean namesFromSpecimens = taxaPage.isWithSpecimen();
        String namesFromBase = thisTarget + (thisTarget.contains("?") ? "&" : "?");
%>
       <div id="names_from_toggle" class="list-choice">
           <div class="left" title="The place's own list, or the list with the names of every specimen collected here">Names from:</div>
           <div id="change_names_from" class="has_options" data-names-from="<%= namesFromSpecimens ? "specimens" : "list" %>">
               <span id="which_names_from"><span><%= Html.esc(PlaceTaxonNotes.namesFromLabel(namesFromSpecimens)) %></span></span>
               <div id="names_from_choice" class="options">
                   <ul>
                     <li><a data-names-choice="list" href="<%= Html.escAttr(namesFromBase + "specimen=false") %>"><span><%= Html.esc(PlaceTaxonNotes.NAMES_FROM_LIST) %></span></a></li>
                     <li><a data-names-choice="specimens" href="<%= Html.escAttr(namesFromBase + "specimen=true") %>"><span><%= Html.esc(PlaceTaxonNotes.NAMES_FROM_SPECIMENS) %></span></a></li>
                   </ul>
               </div>
           </div>
       </div>
<%    } %>
        </div>
      </div>
      <div class="clear"></div>
        <%= unImagedNote %>
<%    if (allSpecimensCriteria != null || editSpeciesListUrl != null) { %>
      <div id="list-links" class="list-links">
<%      if (allSpecimensCriteria != null) { %><a rel='nofollow' href='<%= AntwebProps.getDomainApp() %>/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=formicidae&<%= Html.escAttr(allSpecimensCriteria) %>&resultRank=specimen' title='Every specimen record collected here, under any name'>List all specimens collected in <%= Html.esc(overview.getName()) %></a><% } %>
<%      if (allSpecimensCriteria != null && editSpeciesListUrl != null) { %> &nbsp;|&nbsp; <% } %>
<%      if (editSpeciesListUrl != null) { %><a href='<%= Html.escAttr(editSpeciesListUrl) %>'>Edit Species List</a><% } %>
      </div>
<%    } %>
    </div>
    <div class="clear"></div>
</div>

<%@include file="/common/taxonSetHeaderNotes.jsp" %>
<%
  
  boolean isWithSpecimen = taxaPage.isWithSpecimen();
  // 2026-10-28: the place's specimens identified to genus only (genus rows, the line under the species list)
  org.calacademy.antweb.home.GenusOnly genusOnly = taxaPage.getGenusOnly();
  // 2026-10-30: genus lists, the specimens collected here not on the species list, by genus (the genus rows)
  PlaceTaxonNotes.OtherNames otherNames = taxaPage.getOtherNames();
%>

    <%@ include file="/taxonReportBody.jsp" %>

