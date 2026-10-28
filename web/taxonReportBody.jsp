<!-- taxonReportBody.jsp -->
<%
    // Included by showBrowse-body.jsp and 

    // Swap out. Replace the following with taxonReportLines.jsp

    String orderBy = request.getParameter("orderBy");
    Taxon.sortTaxa(orderBy, children, overview);

    // Must have defined isSpecimen, pageRank and displayGlobal.
    // Included by taxonomicPage-body.jsp and taxonReport.jsp (showBrowse-body.jsp)

    String nextPluralRank = Rank.getNextPluralRank(pageRank).toLowerCase();
    String locale = overview.toString();
    if (isMuseum) locale = "the " + ((Museum) overview).getCode() + " museum";
    if (isGeolocale) locale = overview.toString();
    if (isBioregion) locale = "the " + overview.toString() + " bioregion";
    if (isProject) locale = "the " + org.calacademy.antweb.util.Html.esc(overview.getTitle()) + " project";
    String asterixTitle = "Counts include " + nextPluralRank + " of all statuses contained within " + locale + "."; 
    // Port of Entry (Brian Fisher, 2026-10-22): a species list only; no specimen, child or image counts per row.
    boolean listOnly = isProject && overview instanceof Project && ((Project) overview).isSpeciesListOnly();
    // Images column (Brian Fisher, 2026-10-28): one number, the images of the ant of the name in AntWeb, from any
    // specimen; the name's images page says which of them come from the place (from 10-27 to 10-28 the cell also
    // showed the place's number, "(0 from Comoros)").
    String imagesFromPhrase = org.calacademy.antweb.ImagesAnywhere.fromPhrase(overview);
    String imagesColumnTitle = "Images of the ant of the name in AntWeb, from any specimen (labels are not counted)."
        + (imagesFromPhrase == null ? "" : " Click the number to see them; the specimens " + imagesFromPhrase + " come first.");
    // The definition under the list (Brian Fisher, 2026-10-27: every Images column counts images of the ant only).
    // The definition is ImageCounts.DEFINITION (also web/common/imagesDefinition.jsp), with what the number counts.
    String imagesDefinition = org.calacademy.antweb.ImageCounts.DEFINITION
        + " The number is the images of the ant of the name in AntWeb, from any specimen."
        + (imagesFromPhrase == null ? "" : " Click it to see them; the specimens " + imagesFromPhrase + " come first.");
    // Brian Fisher, 2026-10-27: plain words for the * note (was "Counts include specimens of all statuses").
    String asterixUnit = "specimens".equals(nextPluralRank) ? "specimen filed under the name"
        : "genera".equals(nextPluralRank) ? "genus under the row"
        : "subfamilies".equals(nextPluralRank) ? "subfamily under the row"
        : nextPluralRank + " name under the row";   // species: "species name"
    String asterixNote = "Numbers marked * count every " + asterixUnit
        + ", whatever the name's status (valid, morphospecies, indetermined and others)"
        + (displayGlobal ? "." : ", in " + locale + ".");
    // Brian Fisher, 2026-10-28: a species is a valid name or a morphospecies. The Species of a genus row count those only.
    if (Rank.GENUS.equals(pageRank)) {
      asterixNote = "Numbers marked * count the species and subspecies of the genus in " + locale
          + " that are valid names or morphospecies; names identified to genus only (indetermined, unidentifiable)"
          + " and names not in AntCat (unrecognized) are not species.";
    }
    // The specimens identified to genus only, linked to the place's specimen search (as "List all specimens collected in").
    String genusOnlySearch = null;
    if (overview instanceof org.calacademy.antweb.geolocale.LocalityOverview && !HttpUtil.isBot(request)) {
      String genusOnlyCriteria = ((org.calacademy.antweb.geolocale.LocalityOverview) overview).getSearchCriteria();
      if (genusOnlyCriteria != null && !"".equals(genusOnlyCriteria)) {
        genusOnlySearch = AntwebProps.getDomainApp() + "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=formicidae&"
            + genusOnlyCriteria + "&resultRank=specimen";
      }
    }
%>

<!--  Column Headings -->
    <div class="clear"></div>
<br>
<%-- aw-list-scroll / aw-list: on narrow screens the list scrolls sideways inside this box
     (header and rows together) instead of the whole page; see responsive.css. Plain divs at desktop width. --%>
<div class="aw-list-scroll"><div class="aw-list">
    <div class="sd_data">

	  <%
		// Escaped: the link is the request's own address, written into single-quoted attributes below.
		String sortLink = org.calacademy.antweb.util.Html.escAttr(HttpUtil.removeParam(thisTarget, "orderBy"));
		String underline = "style='border-bottom:1px solid black;text-decoration:none;color:#000001;'";
		String u = "";

        //A.log("taxonReportBody.jsp pageRank:" + pageRank);
	  %>
        <div class="sd_name pad">

        <% if ("status".equals(orderBy)) u = underline; else u = ""; %>
           <a <%= u %> title="Status" href='<%= sortLink + "&orderBy=status" %>'><img src="<%= AntwebProps.getDomainApp() %>/image/valid_nameBW.png"></a>


          <% if (Rank.getRankLevel(pageRank) <= Rank.getRankLevel("species")) {

               if ("type".equals(orderBy)) u = underline; else u = ""; %>
               <a <%= u %> title="Type" href='<%= sortLink + "&orderBy=type" %>'><img src="<%= AntwebProps.getDomainApp() %>/image/has_type_status_iconBW.png" width="11"></a>
          <%
           
               if ("ie".equals(orderBy)) u = underline; else u = "";  
               String ieTitle = "Native (default)/Introduced/Endemic";
               if (overview instanceof Bioregion) { 
                        ieTitle = "Native (default)/Introduced";
               } %>
               <a <%= u %> title="<%= ieTitle %>" href='<%= sortLink + "&orderBy=ie" %>'><img src="<%= AntwebProps.getDomainApp() %>/image/greenELowBW.png" width="11" height="12" border="0"></a>
          <% } %>

<%               
		   if (isWithSpecimen) {
			 if ("fromSpecimen".equals(orderBy)) u = underline; else u = "";  %>
			 <a <%= u %> title="From Specimen Data" href='<%= sortLink + "&orderBy=fromSpecimen" %>'><img src="<%= AntwebProps.getDomainApp() %>/image/yellow_antBW.png" width="11" height="12" border="0"></a>
		<% } else { %>      
			 &nbsp;&nbsp;&nbsp;&nbsp;    
		<% }   
           if ("taxonName".equals(orderBy)) u = underline; else u = "";  %>
           <font size=2><a <%= u %> href='<%= sortLink + "&orderBy=taxonName" %>'>Taxon Name</a></b></font>
        </div>        
     <% if ("authorDate".equals(orderBy)) u = underline; else u = "";  %>        
        <div class="list_extras author_date"><font size=2><b><a <%= u %> href='<%= sortLink + "&orderBy=authorDate" %>'>Author Date</a></b></font></div>


     <% if (listOnly) { %>
     <% } else if (displayGlobal) { %>
         <% if ("specimensGlobal".equals(orderBy)) u = underline; else u = "";  %>
            <div class="list_extras specimens"><font size=2><b><a <%= u %> href='<%= sortLink + "&orderBy=specimensGlobal" %>'>Global</a><a title='Counts include specimens of all statuses'>*</a></b></font></div>

         <% if ("specimens".equals(orderBy)) u = underline; else u = "";  
              String heading = overview.toString();
              if (isMuseum) heading = ((Museum)overview).getCode();
         %>
            <div class="list_extras specimens"><font size=2><b><a <%= u %> href='<%= sortLink + "&orderBy=specimens" %>'><%= heading %><a title='<%= asterixTitle %>'>*</a></a></b></font></div>
     <% } else { %>
         <% 
            if (Rank.SUBFAMILY.equals(pageRank)) { %>
             <% if ("genera".equals(orderBy)) u = underline; else u = "";  %>
                <div class="list_extras specimens"><font size=2><b><a <%= u %> href='<%= sortLink + "&orderBy=genera" %>'>Genera</a><a title='<%= asterixTitle %>'>*</a></a></b></font></div>
         <% } else if (Rank.GENUS.equals(pageRank)) { %>
             <% if ("species".equals(orderBy)) u = underline; else u = "";  %>
                <div class="list_extras specimens"><font size=2><b><a <%= u %> href='<%= sortLink + "&orderBy=species" %>'>Species</a><a title='<%= asterixTitle %>'>*</a></b></font></div>        
         <% } else { %>

             <% if ("specimens".equals(orderBy)) u = underline; else u = "";  %>
                <div class="list_extras specimens"><font size=2><b><a <%= u %> href='<%= sortLink + "&orderBy=specimens" %>'>Specimens</a></b></font></div>
         <% } %>


     <% } %>
     <% if ("images".equals(orderBy)) u = underline; else u = "";  %>        
     <% if (!listOnly) { %>
        <div class="list_extras images"><font size=2><b><a <%= u %> title='<%= org.calacademy.antweb.util.Html.escAttr(imagesColumnTitle) %>' href='<%= sortLink + "&orderBy=images" %>'>Images</a></b></font></div>
     <% } %>

     <% if ("map".equals(orderBy)) u = underline; else u = ""; %>
     <div class="list_extras map"><font size=2><b><a <%= u %> href='<%= sortLink + "&orderBy=map" %>'>Map</a></u></b></font></div>
	 <% if (!isMuseum) { %>        
		  <% if ("source".equals(orderBy)) u = underline; else u = "";  %>
		  <div class="list_extras source"><font size=2><b><a <%= u %> href='<%= sortLink + "&orderBy=source" %>'>Source</a></b></font></div>
	 <% } %>

    </div>
    <div class="clear"></div>

    <div class="page_divider taxonomic"></div>

<div id="page_data">
<% 
	int k = 0;
    // A.log("taxonReportBody.jsp " + childTaxonSet.getNextSubtaxon());
	// A.log("taxonReportBody.jsp size:" + children.size());

    int subtaxonTotal = 0;

	for (Taxon child : children) { 
	  Taxon thisChild = child;
	  //AntwebUtil.iLog("thisChild");
	  String browserParams = child.getBrowserParams();
      //A.log("BROWSER PARAMS:" + browserParams);
	  ++k;
  
	 // isImaged=false: only names with no photo of the ant anywhere in AntWeb (Q12, TaxaPage.findUnimaged)
	 boolean showChild = !isOnlyShowUnImaged || unimagedNames == null || unimagedNames.contains(child.getTaxonName());
	 if (showChild) {

	   TaxonSet childTaxonSet = child.getTaxonSet();

	   String childDagger = "";
	   if (child.getIsFossil()) childDagger = "&dagger;";
	   //if (AntwebProps.isDevMode()) childTaxonSet.setIsEndemic(true);

  	    //if (childTaxonSet instanceof BioregionTaxon) A.log("taxonReportBody.jsp childTaxonSet:" + child + " pageRank:" + Rank.getRankLevel(pageRank) + " endemic:" + ((BioregionTaxon) childTaxonSet).getIsEndemic());

  	    //A.log("taxonReportBody.jsp k:" + k + " hasCount:" + ((Taxon) child).getHasImagesCount());
		//	   String projectStr = "";
		//	   if (projectName != null) projectStr = "&project=" + projectName;
  	   String mapLink = "bigMap.do?taxonName=" + child.getTaxonName(); // + projectStr;

	   if (true) {   // the unimaged filter is showChild above (was: no image of this place, labels included)
%>
<div class="specimen_layout">

    <div class="sd_data">
        <div class="sd_name pad">
        <%@include file="/common/statusDisplayChild.jsp" %>
<% if (Rank.getRankLevel(pageRank) <= Rank.getRankLevel("species")) { %>
        <%@include file="/common/isType.jsp" %>
        <%@include file="/common/distStatus.jsp" %>
<% } %>
        <%@include file="/common/isFromSpecimen.jsp" %> 

          <a href="<%= AntwebProps.getDomainApp() %>/description.do?<%= browserParams %>"><%= childDagger %><%= child.getPrettyName() %></a>
        </div> 
        <div class="list_extras author_date"><%= (new Formatter()).clearNull(child.getAuthorDate()) %></div>
    <% if (childTaxonSet.exists()) {         

          String childCountStr = null;

          int nextSubtaxonCount = childTaxonSet.getSubtaxonCount(1);
          subtaxonTotal += nextSubtaxonCount;

          String nextSubtaxon = childTaxonSet.getNextSubtaxon(1);
          //A.log("taxonReportBody.jsp nextSubtaxon:" + nextSubtaxon + " Um, nextTaxon should be returning 'No [rank]' instead of :" + childCountStr);
          if (nextSubtaxon != null && nextSubtaxon.contains("No ")) {
            childCountStr = nextSubtaxon; //"No Specimens";
            // 2026-10-28: a genus known here only from specimens identified to genus: "No Species" and how many.
            int genusOnlyCount = (Rank.GENUS.equals(pageRank) && genusOnly != null) ? genusOnly.specimens(child.getTaxonName()) : 0;
            if (genusOnlyCount > 0) {
              String genusOnlyText = org.calacademy.antweb.home.GenusOnly.genusRowText(genusOnlyCount);
              if (genusOnlySearch != null) genusOnlyText = "<a rel='nofollow' href='" + org.calacademy.antweb.util.Html.escAttr(genusOnlySearch + "&"
                  + org.calacademy.antweb.home.GenusOnly.searchParams(genusOnly.genus(child.getTaxonName()))) + "'>" + genusOnlyText + "</a>";
              childCountStr += "<br><span class='genus-only' data-genus-only='" + genusOnlyCount + "'>" + genusOnlyText + "</span>";
            }
          } else {
            //if (child.getTaxonName().contains("graulomyrmex")) A.log("init() taxonName:" + taxonName + " childCount:" + projTaxon.getGlobalChildCount());
            //if (child.getTaxonName().contains("graulomyrmex")) A.log("taxonReport.jsp ! nextSubtaxon:" + nextSubtaxon + " globalChildCount:" + childTaxonSet.getGlobalChildCount());
            childCountStr = "<a href='" + AntwebProps.getDomainApp() + "/browse.do?" + child.getBrowserParams() + "'>" + nextSubtaxon + "</a>";
          } 

          if (listOnly) {
            // no counts (Port of Entry)
          } else if (displayGlobal) {
            String globalChildCountStr = null;
            globalChildCountStr = "<a href='" + AntwebProps.getDomainApp() + "/browse.do?" + child.getBrowserParams() + "&global=true'>" + childTaxonSet.getNextGlobalSubtaxon(pageRank) + "</a>"; 

%>
            <div class="list_extras specimens"> <%= globalChildCountStr %></div>     
            <div class="list_extras specimens"> <%= childCountStr %></div>
<%

          } else {
            %>                
            <div class="list_extras specimens"> <%= childCountStr %></div>     
            <%
          }


         // 2026-10-27 (Brian Fisher): images help identification wherever the specimen came from. The column shows
         // the images of the ant of the name in AntWeb, one number ("153 Images", 2026-10-28); the images page it
         // links to says how many imaged specimens are from the place and shows them first.
         String imagesCell = org.calacademy.antweb.ImagesAnywhere.imagesColumn(childTaxonSet, imagesFromPhrase);
         String imageCountStr = null;
         if (imagesCell.startsWith("No ")) {
           imageCountStr = imagesCell;
         } else {
           imageCountStr = "<a href='" + AntwebProps.getDomainApp() + "/images.do?" + child.getBrowserParams() + "'>" + imagesCell + "</a>";
         }
     %>
        <% if (!listOnly) { %><div class="list_extras images"><%= imageCountStr %></div><% } %>

        <div class="list_extras map">
          <% if (childTaxonSet.getSubtaxonCount(1) > 0) { %>
            <a href="<%= mapLink %>">Map</a>
          <% } %>
        </div>
        <div class="list_extras source">
        <%
          String sourceDisplay = childTaxonSet.getSourceDisplay();
          //A.log("taxonReportBody.jsp sourceDisplay:" + sourceDisplay);
          if ("Antcat".equals(sourceDisplay)) {
            sourceDisplay = "<a target='new' href='http://www.antcat.org/catalog/" + child.getAntcatId() + "'>Antcat</a>";
          }
          if ("Curator".equals(sourceDisplay)) {
            if (overview instanceof Geolocale) {
              sourceDisplay = "<a title='" + childTaxonSet.getSource() + "' href='" + AntwebProps.getDomainApp() + "/curation.do?taxonName=" + childTaxonSet.getTaxonName() + "&geolocaleId=" + ((Geolocale) overview).getId() + "'>" + sourceDisplay + "</a>";
            }
          }
        %>
            <%= sourceDisplay %>         
        </div>
    <% } else { %>
        <div class="list_extras specimens"><a title="From specimen data">n/a</a></div>
        <div class="list_extras localSpecimens"><a title="From specimen data">n/a</a></div>
        <div class="list_extras images"><a title="From specimen data">n/a</a></div>
        <div class="list_extras map"><a title="From specimen data">n/a</a></div>
        <div class="list_extras source">AntWeb&nbsp;specimen</div>
    <% } %>
    </div>
    <div class="clear"></div>
</div>
<div class="clear"></div>
<% 
   }   
 }
} // end for loop
%>


</div>
</div></div><%-- /aw-list, /aw-list-scroll --%>
<% if (LoginMgr.isAdmin(request)) {
     // Brian Fisher, 2026-10-27: say what the check number is. It adds up each row's own number for this place (the
     // column marked *); the tests read data-subtaxon-total. docs/numbers.md row 45.
     String totalColumn, totalUnit, totalCheck;
     if (Rank.SUBFAMILY.equals(pageRank)) {
       totalColumn = "Genera"; totalUnit = "genera";
       totalCheck = "should equal Genera on " + locale + "'s page";
     } else if (Rank.GENUS.equals(pageRank)) {
       totalColumn = "Species"; totalUnit = "species and subspecies";
       totalCheck = "should equal Species / subspecies on " + locale + "'s page";
     } else {
       totalColumn = displayGlobal ? (isMuseum ? ((Museum) overview).getCode() : overview.toString()) : "Specimens";
       totalUnit = "specimens";
       totalCheck = "Specimens on " + locale + "'s page can be higher: it also counts specimens of names not on this list,"
           + " such as those not identified to genus";
     }
%>
     <br><span id="subtaxon-total" data-subtaxon-total="<%= subtaxonTotal %>">Total of the <%= org.calacademy.antweb.util.Html.esc(totalColumn) %> column: <%= Formatter.commaFormat(subtaxonTotal) %> <%= totalUnit %> (administrators only; <%= totalCheck %>).</span>
<% } %>
<% String genusOnlyLine = (Rank.SPECIES.equals(pageRank) && genusOnly != null) ? genusOnly.speciesListText() : null;
   if (genusOnlyLine != null) { %>
<BR><span id="genus-only-note" data-genus-only="<%= genusOnly.totalSpecimens() %>"><%= org.calacademy.antweb.util.Html.esc(genusOnlyLine) %><% if (genusOnlySearch != null) { %> <a rel='nofollow' href='<%= org.calacademy.antweb.util.Html.escAttr(genusOnlySearch) %>'>Show all specimens.</a><% } %></span>
<% } %>
<% if (listOnly) { %>
<BR><span id="port-of-entry-list-note">These are ant species intercepted at ports of entry, not established introductions. They are not counted in any place total or in the introduced species list, and this list shows no specimen or image numbers.</span>
<% } else { %>
<BR>* <%= asterixNote %>
<BR><span id="images-definition" class="images-definition"><%= org.calacademy.antweb.util.Html.esc(imagesDefinition) %></span>
<% } %>
