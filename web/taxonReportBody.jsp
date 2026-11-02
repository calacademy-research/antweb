<%-- The rows of a place's list of taxa, shared by taxonomicPage.do (taxonomicPage-body.jsp) and browse.do
     (taxonReport.jsp): sorts the children by orderBy (Taxon.sortTaxa), writes the sortable headings, one line per
     row, and the notes under the list (the asterisk counts, Images, specimens identified only to genus). Columns
     (Brian Fisher, 2026-11-02): Taxon name | Author date (narrow) | Species of a genus row in the view (number, or
     "&ndash;") or the row's counts | Other specimens (genus rows: specimens collected here under names not in the
     view, linked to them; empty when 0; ListView.GenusRows, PlaceTaxonNotes.otherSpecimensCellHtml) | Images | Map |
     Source (an icon, ListView.sourceIconHtml: specimen, curated, literature; a name with a specimen collected here
     shows specimen even with a literature entry, ListView.sourceKind). Uses the including page's children, overview,
     pageRank, displayGlobal, place flags, genusRows and placeSpecimens. Docs: docs/flows/places.md,
     docs/flows/taxon-pages.md, docs/numbers.md rows 38 to 44. Tests: tests/e2e/test_list_sources.py,
     test_numbers_lists.py, test_numbers_live.py, test_status_choice.py. --%><!-- taxonReportBody.jsp -->
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
    // The Images note under the list (Brian Fisher, 2026-10-28, list notes): what the one number counts; label
    // images apart (ImageCounts.DEFINITION); where the place's own images are (ImagesAnywhere.fromPhrase).
    String imagesDefinition = "Images: the images of the ant (head, profile, dorsal and other views) of that name in all of"
        + " AntWeb, from specimens collected anywhere. Specimen label images are not counted."
        + (imagesFromPhrase == null ? "" : " Click the number to see the images; those " + imagesFromPhrase + " come first.");
    // The * note (Brian Fisher, 2026-10-28, list notes). The column marked * is Specimens on a species list, Species on
    // a genus list and Genera on a subfamily list.
    String asterixCounts = "specimens".equals(nextPluralRank) ? "Specimen"
        : "genera".equals(nextPluralRank) ? "Genus"
        : "subfamilies".equals(nextPluralRank) ? "Subfamily"
        : "Species";
    String asterixUnit = "specimens".equals(nextPluralRank) ? "specimen filed under that name"
        : "genera".equals(nextPluralRank) ? "genus under the row"
        : "subfamilies".equals(nextPluralRank) ? "subfamily under the row"
        : nextPluralRank + " name under the row";   // species: "species name"
    String asterixNote = asterixCounts + " counts marked with an asterisk (*) include every " + asterixUnit
        + ("specimens".equals(nextPluralRank)
            ? ", whatever its taxonomic status (valid species, morphospecies, indetermined, unidentifiable or unrecognized names)"
            : ", whatever the name's status")
        + (displayGlobal ? "." : ", in " + locale + ".");
    // Brian Fisher, 2026-10-28: a species is a valid name or a morphospecies. The Species of a genus row count those only.
    // 2026-11-02: in a place with the views, the Species of a genus row are the species and subspecies of the view.
    if (Rank.GENUS.equals(pageRank) && genusRows != null) {
      asterixNote = "Species: the species and subspecies of the genus in " + locale + " in this view ("
          + StatusSet.choiceLabel(genusRows.choice, "species") + "); a dash when none. Other specimens: the specimens of the"
          + " genus collected here under names that are not in this view; click the number to see them.";
      asterixTitle = "The species and subspecies of the genus in this view";
    } else if (Rank.GENUS.equals(pageRank)) {
      asterixNote = "Numbers marked * count the species and subspecies of the genus in " + locale
          + " that are valid names or morphospecies; names identified to genus only (indetermined, unidentifiable)"
          + " and names not in AntCat (unrecognized) are not species.";
      asterixTitle = asterixNote;   // the * tooltip says what the note under the list says (it said "all statuses")
    }
    // The specimens identified to genus only (species lists) and the genus rows' Other specimens (genus lists), linked
    // to the place's specimen search (as "List all specimens collected in").
    String genusOnlySearch = null;
    if (overview instanceof org.calacademy.antweb.geolocale.LocalityOverview && !HttpUtil.isBot(request)) {
      String genusOnlyCriteria = ((org.calacademy.antweb.geolocale.LocalityOverview) overview).getSearchCriteria();
      if (genusOnlyCriteria != null && !"".equals(genusOnlyCriteria)) {
        genusOnlySearch = AntwebProps.getDomainApp() + "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=formicidae&"
            + genusOnlyCriteria + "&resultRank=specimen";
      }
    }
    // 2026-11-02: the Other specimens column of genus rows (ListView.GenusRows): Valid, Curated, All specimen names.
    boolean showOtherColumn = Rank.GENUS.equals(pageRank) && genusRows != null && genusRows.showsOtherSpecimens() && genusRows.otherNames != null;
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
        <div class="list_extras author_date"><font size=2><b><a <%= u %> href='<%= sortLink + "&orderBy=authorDate" %>'>Author date</a></b></font></div>


     <% if (listOnly) { %>
     <% } else if (displayGlobal) { %>
         <% if ("specimensGlobal".equals(orderBy)) u = underline; else u = "";  %>
            <div class="list_extras specimens"><font size=2><b><a <%= u %> href='<%= sortLink + "&orderBy=specimensGlobal" %>'>Global</a><a title='Counts include specimens of all statuses'>*</a></b></font></div>

         <% if ("specimens".equals(orderBy)) u = underline; else u = "";  
              String heading = overview.toString();
              if (isMuseum) heading = ((Museum)overview).getCode();
         %>
            <div class="list_extras specimens"><font size=2><b><a <%= u %> href='<%= sortLink + "&orderBy=specimens" %>'><%= heading %><a title='<%= Html.escAttr(asterixTitle) %>'>*</a></a></b></font></div>
     <% } else { %>
         <% 
            if (Rank.SUBFAMILY.equals(pageRank)) { %>
             <% if ("genera".equals(orderBy)) u = underline; else u = "";  %>
                <div class="list_extras specimens"><font size=2><b><a <%= u %> href='<%= sortLink + "&orderBy=genera" %>'>Genera</a><a title='<%= Html.escAttr(asterixTitle) %>'>*</a></a></b></font></div>
         <% } else if (Rank.GENUS.equals(pageRank)) { %>
             <% if ("species".equals(orderBy)) u = underline; else u = "";  %>
                <div class="list_extras specimens"><font size=2><b><a <%= u %> href='<%= sortLink + "&orderBy=species" %>'>Species</a><a title='<%= Html.escAttr(asterixTitle) %>'>*</a></b></font></div>
             <% if (showOtherColumn) { %>
                <div class="list_extras other"><font size=2><b><span title="Specimens collected here under names that are not in this view">Other specimens</span></b></font></div>
             <% } %>
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
		  <div class="list_extras source"><font size=2><b><a <%= u %> title="Specimen, curated or literature; see About this list" href='<%= sortLink + "&orderBy=source" %>'>Source</a></b></font></div>
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
          // 2026-11-02 (Brian Fisher, views): a genus row's Species are its species and subspecies in the view (a number
          // linked to the genus page in the same view, or a dash), and Other specimens the specimens collected here under
          // names not in the view (ListView.GenusRows; replaces "No species on the list &middot; N specimens collected here"
          // and "+ N specimens of other names" of 2026-10-30).
          String otherCell = null;
          if (Rank.GENUS.equals(pageRank) && genusRows != null) {
            int viewSpecies = genusRows.species(child.getSubfamily(), child.getGenus());
            subtaxonTotal += viewSpecies - nextSubtaxonCount;
            childCountStr = viewSpecies <= 0 ? "<span class='no-species' title='No species of this genus in this view'>&ndash;</span>"
                : "<a href='" + AntwebProps.getDomainApp() + "/browse.do?" + child.getBrowserParams() + "&statusSet=" + Html.url(genusRows.choice) + "'>" + viewSpecies + "</a>";
            org.calacademy.antweb.PlaceTaxonNotes.OtherNames.Genus rowOther = genusRows.other(child.getSubfamily(), child.getGenus());
            if (showOtherColumn) {
              otherCell = rowOther == null ? "" : org.calacademy.antweb.PlaceTaxonNotes.otherSpecimensCellHtml(rowOther.specimens, genusOnlySearch == null ? null
                  : genusOnlySearch + "&" + org.calacademy.antweb.PlaceTaxonNotes.searchParams(rowOther, viewSpecies <= 0));
            }
          } else if (nextSubtaxon != null && nextSubtaxon.contains("No ")) {
            childCountStr = nextSubtaxon; //"No Specimens";
          } else {
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
            <% if (showOtherColumn) { %><div class="list_extras other"><%= otherCell == null ? "" : otherCell %></div><% } %>
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
        <% if (!isMuseum) { %>
        <div class="list_extras source">
        <%
          // 2026-11-02: an icon (specimen, curated, literature) with the word as title; specimens trump literature
          // (a name with a specimen collected here, not red-flagged, shows specimen). AntCat entries (type localities)
          // are literature and link to AntCat; curated entries of a geolocale link to their curation history.
          boolean rowHasSpecimen = (placeSpecimens != null && placeSpecimens.has(child.getTaxonName()))
              || (Rank.GENUS.equals(pageRank) && genusRows != null && genusRows.hasSpecimens(child.getSubfamily(), child.getGenus()));
          String sourceKind = org.calacademy.antweb.ListView.sourceKind(childTaxonSet.getSource(), rowHasSpecimen);
          String sourceDisplay = org.calacademy.antweb.ListView.sourceIconHtml(sourceKind);
          String canonicalSource = Source.getSourceCanonical(childTaxonSet.getSource());
          if (!rowHasSpecimen && Source.ANTCAT.equals(canonicalSource) && child.getAntcatId() > 0) {
            sourceDisplay = "<a target='new' href='https://www.antcat.org/catalog/" + child.getAntcatId() + "'>" + sourceDisplay + "</a>";
          }
          if (!rowHasSpecimen && Source.CURATOR.equals(canonicalSource) && overview instanceof Geolocale) {
            sourceDisplay = "<a href='" + AntwebProps.getDomainApp() + "/curation.do?taxonName=" + Html.escAttr(Html.url(childTaxonSet.getTaxonName())) + "&amp;geolocaleId=" + ((Geolocale) overview).getId() + "'>" + sourceDisplay + "</a>";
          }
        %>
            <%= sourceDisplay %>
        </div>
        <% } %>
    <% } else {
         // A row added from specimens collected here (views Curated and All specimen names, 2026-11-02): not on the list,
         // so no list numbers; a species row says how many specimens were collected here, a genus row its Species and
         // Other specimens in the view.
         String addedCount = "&ndash;";
         String addedOther = "";
         if (placeSpecimens != null && placeSpecimens.has(child.getTaxonName())) {
           addedCount = Formatter.commaFormat(placeSpecimens.count(child.getTaxonName())) + " here";
         }
         if (Rank.GENUS.equals(pageRank) && genusRows != null) {
           int addedSpecies = genusRows.species(child.getSubfamily(), child.getGenus());
           if (addedSpecies > 0) addedCount = "" + addedSpecies;
           org.calacademy.antweb.PlaceTaxonNotes.OtherNames.Genus addedRowOther = genusRows.other(child.getSubfamily(), child.getGenus());
           if (addedRowOther != null) addedOther = org.calacademy.antweb.PlaceTaxonNotes.otherSpecimensCellHtml(addedRowOther.specimens, genusOnlySearch == null ? null
               : genusOnlySearch + "&" + org.calacademy.antweb.PlaceTaxonNotes.searchParams(addedRowOther, addedSpecies <= 0));
         }
    %>
        <% if (displayGlobal) { %><div class="list_extras specimens">&ndash;</div><% } %>
        <div class="list_extras specimens" title="Not on the list: from specimens collected here"><%= addedCount %></div>
        <% if (showOtherColumn) { %><div class="list_extras other"><%= addedOther %></div><% } %>
        <% if (!listOnly) { %><div class="list_extras images">&ndash;</div><% } %>
        <div class="list_extras map"></div>
        <% if (!isMuseum) { %><div class="list_extras source"><%= org.calacademy.antweb.ListView.sourceIconHtml(org.calacademy.antweb.ListView.SOURCE_SPECIMEN) %></div><% } %>
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
<% // Notes under the list (Brian Fisher, 2026-10-28): one block, the admin check number, the * note, the Images note.
   String totalNote = null;
   if (LoginMgr.isAdmin(request)) {
     // The check number adds up each row's own number for this place (the column marked *); the tests read
     // data-subtaxon-total. docs/numbers.md row 45.
     String totalColumn = displayGlobal ? (isMuseum ? ((Museum) overview).getCode() : overview.toString()) : "Specimens";
     if (Rank.SUBFAMILY.equals(pageRank)) {
       totalNote = "Genera total (administrators only): the Genera column adds up to " + Formatter.commaFormat(subtaxonTotal)
           + " genera; it should equal Genera on the " + locale + " overview page.";
     } else if (Rank.GENUS.equals(pageRank) && genusRows != null) {
       totalNote = "Species total (administrators only): the Species column adds up to " + Formatter.commaFormat(subtaxonTotal)
           + " species and subspecies of this view; it should equal the header of the species list in the same view.";
     } else if (Rank.GENUS.equals(pageRank)) {
       totalNote = "Species total (administrators only): the Species column adds up to " + Formatter.commaFormat(subtaxonTotal)
           + " species and subspecies; it should equal Species / subspecies / morphospecies on the " + locale + " overview page.";
     } else {
       totalNote = "Specimen total (administrators only): the " + org.calacademy.antweb.util.Html.esc(totalColumn)
           + " column adds up to " + Formatter.commaFormat(subtaxonTotal) + " specimens. This can be lower than Specimens on the "
           + locale + " overview page, which also counts specimens not on this list, such as those identified only to"
           + " subfamily or family.";
     }
   }
   int noteNum = 0;
%>
<%-- The genus page's line in a place (browse.do; request attribute set by BrowseAction, none on taxonomicPage.do):
     above the notes (Brian Fisher, 2026-11-02; it was under them, from showBrowse-body.jsp). --%>
<%@include file="/common/genusIndetLine.jsp" %>
<% if (listOnly) { %>
<% if (totalNote != null) { %>
<br><span id="subtaxon-total" data-subtaxon-total="<%= subtaxonTotal %>"><%= totalNote %></span>
<% } %>
<BR><span id="port-of-entry-list-note">These are ant species intercepted at ports of entry, not established introductions. They are not counted in any place total or in the introduced species list, and this list shows no specimen or image numbers.</span>
<% } else { %>
<br><div id="list-notes"><b>Notes on specimen and image counts</b>
<% if (totalNote != null) { %>
<br><%= ++noteNum %>. <span id="subtaxon-total" data-subtaxon-total="<%= subtaxonTotal %>"><%= totalNote %></span>
<% } %>
<% String genusOnlyLine = (Rank.SPECIES.equals(pageRank) && genusOnly != null) ? genusOnly.speciesListText() : null;
   if (genusOnlyLine != null) { %>
<br><%= ++noteNum %>. <span id="genus-only-note" data-genus-only="<%= genusOnly.totalSpecimens() %>"><%= org.calacademy.antweb.util.Html.esc(genusOnlyLine) %><% if (genusOnlySearch != null) { %> <a rel='nofollow' href='<%= org.calacademy.antweb.util.Html.escAttr(genusOnlySearch) %>'>Show all specimens.</a><% } %></span>
<% } %>
<br><%= ++noteNum %>. <span id="asterisk-note"><%= asterixNote %></span>
<br><%= ++noteNum %>. <span id="images-definition" class="images-definition"><%= org.calacademy.antweb.util.Html.esc(imagesDefinition) %></span>
</div>
<% } %>
