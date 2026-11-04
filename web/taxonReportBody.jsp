<%-- The rows of a place's list of taxa, shared by taxonomicPage.do (taxonomicPage-body.jsp) and browse.do
     (taxonReport.jsp): sorts the children by orderBy (Taxon.sortTaxa), writes the sortable headings, one line per
     row, and the notes under the list (the asterisk counts, Images, specimens identified only to genus). Columns
     (Brian Fisher, 2026-11-02): Taxon name | Author date (narrow) | Species of a genus row in the view (number, or
     "&ndash;") or the row's counts | Not curated (genus rows, views Valid and Curated, countries, adm1s, museums,
     bioregions: the specimens of the genus collected here whose name is not curated, counted nightly, linked to the
     genus page in the view Not curated; empty when 0; ListView.GenusRows, PlaceTaxonNotes.notCuratedCellHtml) | Images
     (genus rows in a place: "N species imaged", the species of the view with an image of the ant) | Map |
     Source (an icon, ListView.sourceIconHtml: specimen, curated, literature; a name with a specimen collected here
     shows specimen even with a literature entry, ListView.sourceKind). Uses the including page's children, overview,
     pageRank, displayGlobal, place flags, genusRows, subfamilyRows and placeSpecimens. Subfamily rows in a place with the
     views (2026-11-03, B77): Genera = the genus rows of the subfamily's browse page in the same view, linked to it
     (ListView.SubfamilyRows). Docs: docs/flows/places.md,
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
    // Genus rows in a place (Brian Fisher, 2026-11-02, 18:40): "N species imaged", of the species of the genus in this
    // view, those with at least one image of the ant anywhere in AntWeb (labels never count; ListView.GenusRows.imaged);
    // it replaces "N Images" (every image of the genus in AntWeb, e.g. Camponotus 20,663). The link opens the genus's
    // images page in the same view (no place-first order there: that is for species pages).
    boolean genusImagedCells = Rank.GENUS.equals(pageRank) && genusRows != null;
    if (genusImagedCells) {
      imagesColumnTitle = "Species of the genus in this view with at least one image of the ant, from any specimen in AntWeb (labels are not counted).";
      imagesDefinition = "Sp. imaged: of the species and subspecies of the genus in this view, how many have at least one image of"
          + " the ant (head, profile, dorsal or another view) from a specimen collected anywhere in AntWeb. Specimen label"
          + " images are not counted. Click the number to see the genus's images.";
      // sorting by Images follows the number shown (most species imaged first; Taxon.sortTaxa sorted by image counts)
      if ("images".equals(orderBy)) {
        final org.calacademy.antweb.ListView.GenusRows sortRows = genusRows;
        children.sort((a, b) -> Integer.compare(sortRows.imaged(b.getSubfamily(), b.getGenus()), sortRows.imaged(a.getSubfamily(), a.getGenus())));
      }
      // sorting by Not curated (2026-11-02, 21:21: the heading is a sort link like the others): most first
      if ("notCurated".equals(orderBy)) {
        final org.calacademy.antweb.ListView.GenusRows sortRows = genusRows;
        java.util.function.ToIntFunction<Taxon> nc = t -> {
          org.calacademy.antweb.home.PlaceGenusCurationDb.Row row = sortRows.notCurated(t.getSubfamily(), t.getGenus());
          return row == null ? 0 : row.notCurated;
        };
        children.sort((a, b) -> Integer.compare(nc.applyAsInt(b), nc.applyAsInt(a)));
      }
    }
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
          + StatusSet.choiceLabel(genusRows.choice, "species") + "); a dash when none."
          + (genusRows.showsNotCurated() ? " Not curated: the specimens of the genus collected here (not red-flagged) whose"
              + " name is not curated: morphospecies no curator has put on a species list, specimens identified only to genus"
              + " and names not in AntCat. Counted once a day (after an upload it catches up the next morning); click the"
              + " number to see those names and their specimens." : "");
      asterixTitle = "The species and subspecies of the genus in this view";
    } else if (Rank.SUBFAMILY.equals(pageRank) && subfamilyRows != null) {
      // 2026-11-03 (B77): a subfamily row's Genera are the genus rows of its browse page in this view
      asterixNote = "Genera: the genera of the subfamily in " + locale + " in this view ("
          + StatusSet.choiceLabel(subfamilyRows.choice, "genera") + "), the genus rows its page lists in the same view.";
      asterixTitle = "The genera of the subfamily in this view";
    } else if (Rank.GENUS.equals(pageRank)) {
      asterixNote = "Numbers marked * count the species and subspecies of the genus in " + locale
          + " that are valid names or morphospecies; names identified to genus only (indetermined, unidentifiable)"
          + " and names not in AntCat (unrecognized) are not species.";
      asterixTitle = asterixNote;   // the * tooltip says what the note under the list says (it said "all statuses")
    }
    // The specimens identified to genus only (species lists), linked to the place's specimen search (as "List all
    // specimens collected in").
    String genusOnlySearch = null;
    if (overview instanceof org.calacademy.antweb.geolocale.LocalityOverview && !HttpUtil.isBot(request)) {
      String genusOnlyCriteria = ((org.calacademy.antweb.geolocale.LocalityOverview) overview).getSearchCriteria();
      if (genusOnlyCriteria != null && !"".equals(genusOnlyCriteria)) {
        genusOnlySearch = AntwebProps.getDomainApp() + "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=formicidae&"
            + genusOnlyCriteria + "&resultRank=specimen";
      }
    }
    // 2026-11-02: the Not curated column of genus rows (ListView.GenusRows.showsNotCurated): Valid and Curated, on
    // countries, adm1s, museums and bioregions (stored nightly; replaces Other specimens).
    boolean showNotCuratedColumn = Rank.GENUS.equals(pageRank) && genusRows != null && genusRows.showsNotCurated();
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
                <div class="list_extras specimens<%= genusImagedCells ? " genus-col" : "" %>"><font size=2><b><a <%= u %> href='<%= sortLink + "&orderBy=species" %>'>Species</a><a title='<%= Html.escAttr(asterixTitle) %>'>*</a></b></font></div>
             <% if (genusImagedCells && !listOnly) { u = "images".equals(orderBy) ? underline : ""; %>
                <div class="list_extras images genus-col"><font size=2><b><a <%= u %> title='<%= org.calacademy.antweb.util.Html.escAttr(imagesColumnTitle) %>' href='<%= sortLink + "&orderBy=images" %>'>Sp. imaged</a></b></font></div>
             <% } %>
             <% if (showNotCuratedColumn) { u = "notCurated".equals(orderBy) ? underline : ""; %>
                <div class="list_extras other genus-col"><font size=2><b><a <%= u %> title="Specimens of the genus collected here whose name is not curated: uncurated morphospecies and specimens identified only to genus (counted once a day)" href='<%= sortLink + "&orderBy=notCurated" %>'>Not curated</a></b></font></div>
             <% } %>
         <% } else { %>

             <% if ("specimens".equals(orderBy)) u = underline; else u = "";  %>
                <div class="list_extras specimens"><font size=2><b><a <%= u %> href='<%= sortLink + "&orderBy=specimens" %>'>Specimens</a></b></font></div>
         <% } %>


     <% } %>
     <% if ("images".equals(orderBy)) u = underline; else u = "";  %>        
     <% if (!listOnly && !genusImagedCells) { %>
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
	   // A row not on the list (added from specimens, or removed and shown in All specimen names): its name and
	   // specimen links open the name in the place in the view All specimen names, which shows its specimens. The
	   // default view leaves out indetermined and other non-curated names: Temnothorax (indet) in Mozambique opened
	   // with "0 Specimens" (CASENT0849587; Brian Fisher, 2026-11-04).
	   if (childTaxonSet == null || !childTaxonSet.exists()) {
	     browserParams += "&statusSet=" + org.calacademy.antweb.StatusSet.LIST_ALL;
	   }

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

          <a href="<%= AntwebProps.getDomainApp() %>/description.do?<%= browserParams %>"><%= childDagger %><%= child.getPrettyName() %></a><%= org.calacademy.antweb.ListView.removedMarkHtml(listDisputes, child.getTaxonName()) %>
        </div> 
        <div class="list_extras author_date"><%= (new Formatter()).clearNull(child.getAuthorDate()) %></div>
    <% if (childTaxonSet.exists()) {         

          String childCountStr = null;

          int nextSubtaxonCount = childTaxonSet.getSubtaxonCount(1);
          subtaxonTotal += nextSubtaxonCount;

          String nextSubtaxon = childTaxonSet.getNextSubtaxon(1);
          //A.log("taxonReportBody.jsp nextSubtaxon:" + nextSubtaxon + " Um, nextTaxon should be returning 'No [rank]' instead of :" + childCountStr);
          // 2026-11-02 (Brian Fisher, views): a genus row's Species are its species and subspecies in the view (a number
          // linked to the genus page in the same view, or a dash), and Not curated the stored number of its specimens
          // collected here whose name is not curated, linked to the genus page in the view Not curated (ListView.GenusRows;
          // replaces Other specimens).
          String notCuratedCell = null;
          if (Rank.GENUS.equals(pageRank) && genusRows != null) {
            int viewSpecies = genusRows.species(child.getSubfamily(), child.getGenus());
            subtaxonTotal += viewSpecies - nextSubtaxonCount;
            childCountStr = viewSpecies <= 0 ? "<span class='no-species' title='No species of this genus in this view'>&ndash;</span>"
                : "<a href='" + AntwebProps.getDomainApp() + "/browse.do?" + child.getBrowserParams() + "&statusSet=" + Html.url(genusRows.choice) + "'><span class='numbers'>" + viewSpecies + "</span></a>";
            if (showNotCuratedColumn) {
              org.calacademy.antweb.home.PlaceGenusCurationDb.Row ncRow = genusRows.notCurated(child.getSubfamily(), child.getGenus());
              notCuratedCell = ncRow == null ? "" : org.calacademy.antweb.PlaceTaxonNotes.notCuratedCellHtml(ncRow.notCurated,
                  org.calacademy.antweb.PlaceTaxonNotes.notCuratedUrl(child.getSubfamily(), child.getGenus(), overview));
            }
          } else if (Rank.SUBFAMILY.equals(pageRank) && subfamilyRows != null) {
            // 2026-11-03 (B77): the genus rows of the subfamily's browse page in this view (ListView.SubfamilyRows), linked
            // to that page in the same view; the stored genus_count (every counted genus row of the nightly count) is not shown
            int viewGenera = subfamilyRows.genera(child.getSubfamily());
            subtaxonTotal += viewGenera - nextSubtaxonCount;
            childCountStr = viewGenera <= 0 ? "No " + Rank.getNextPluralRank(pageRank, 1)
                : "<a href='" + AntwebProps.getDomainApp() + "/browse.do?" + child.getBrowserParams() + "&statusSet=" + Html.url(subfamilyRows.choice) + "'><span class='numbers'>" + viewGenera + "</span> " + Rank.getNextPluralRank(pageRank, 1) + "</a>";
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
            <div class="list_extras specimens<%= genusImagedCells ? " genus-col" : "" %>"> <%= childCountStr %></div>     
            <% if (showNotCuratedColumn && !genusImagedCells) { %><div class="list_extras other"><%= notCuratedCell == null ? "" : notCuratedCell %></div><% } %>
            <%
          }


         // 2026-10-27 (Brian Fisher): images help identification wherever the specimen came from. The column shows
         // the images of the ant of the name in AntWeb, one number ("153 Images", 2026-10-28); the images page it
         // links to says how many imaged specimens are from the place and shows them first.
         String imagesCell = org.calacademy.antweb.ImagesAnywhere.imagesColumn(childTaxonSet, imagesFromPhrase);
         String imageCountStr = null;
         // genus rows in a place (2026-11-02): "N species imaged", linked to the genus's images page in this view
         int genusImaged = genusImagedCells ? genusRows.imaged(child.getSubfamily(), child.getGenus()) : -1;
         if (genusImaged >= 0) {
           String gi = org.calacademy.antweb.ImagesAnywhere.speciesImagedCell(genusImaged);
           imageCountStr = genusImaged == 0 ? gi : "<a title='" + genusImaged + (genusImaged == 1 ? " species" : " species") + " of this genus in this view with an image; click to see the images' href='" + AntwebProps.getDomainApp() + "/images.do?" + child.getBrowserParams()
               + "&statusSet=" + Html.url(genusRows.choice) + "'>" + gi + "</a>";
         } else if (imagesCell.startsWith("No ")) {
           imageCountStr = imagesCell;
         } else {
           imageCountStr = "<a href='" + AntwebProps.getDomainApp() + "/images.do?" + child.getBrowserParams() + "'>" + imagesCell + "</a>";
         }
     %>
        <% if (!listOnly) { %><div class="list_extras images<%= genusImagedCells ? " genus-col" : "" %>"><%= imageCountStr %></div><% } %>
        <% if (showNotCuratedColumn && genusImagedCells) { %><div class="list_extras other genus-col"><%= notCuratedCell == null ? "" : notCuratedCell %></div><% } %>

        <div class="list_extras map">
          <% if (childTaxonSet.getSubtaxonCount(1) > 0) { %>
            <a href="<%= mapLink %>">Map</a>
          <% } %>
        </div>
        <% if (!isMuseum) { %>
        <div class="list_extras source">
        <%
          // 2026-11-02: an icon (specimen, curated, literature; AntCat as a word) with the word as title; specimens trump literature
          // (a name with a specimen collected here, not red-flagged, shows specimen). AntCat entries (type localities)
          // have their own AntCat label and link to AntCat; curated entries of a geolocale link to their curation history.
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
         // A row added from specimens collected here (views Curated, All specimen names and Not curated, 2026-11-02): not
         // on the list, so no list numbers; a species row says how many specimens were collected here (linked to them), a
         // genus row its Species, Not curated and species imaged in the view.
         String addedCount = "&ndash;";
         String addedOther = "";
         String addedImages = "&ndash;";
         if (placeSpecimens != null && placeSpecimens.has(child.getTaxonName())) {
           // "N specimens", as on the list's own rows (Brian Fisher, 2026-11-03: was "N here")
           int addedN = placeSpecimens.count(child.getTaxonName());
           addedCount = Formatter.commaFormat(addedN) + (addedN == 1 ? " Specimen" : " Specimens");
           // the admin check number adds these up in the view Not curated (every row is added there; its Note 1 read 0,
           // 11-03); in the other views it stays the specimens of the list's rows, as the place's Specimens number
           if (!Rank.GENUS.equals(pageRank) && org.calacademy.antweb.StatusSet.LIST_NOT_CURATED.equals(request.getParameter("statusSet")))
             subtaxonTotal += placeSpecimens.count(child.getTaxonName());
           // on a genus page (rows of species), linked to the name's specimens in the place (its page in the place)
           if (!Rank.GENUS.equals(pageRank)) {
             addedCount = "<a href='" + AntwebProps.getDomainApp() + "/browse.do?" + browserParams + "'>" + addedCount + "</a>";
           }
         }
         if (Rank.GENUS.equals(pageRank) && genusRows != null) {
           int addedSpecies = genusRows.species(child.getSubfamily(), child.getGenus());
           // linked to the genus page in the same view, as on the list's own rows (it was a bare number, 11-03: Phyracaces
           // in Madagascar)
           if (addedSpecies > 0) addedCount = "<a href='" + AntwebProps.getDomainApp() + "/browse.do?" + child.getBrowserParams()
               + "&statusSet=" + Html.url(genusRows.choice) + "'><span class='numbers'>" + addedSpecies + "</span></a>";
           org.calacademy.antweb.home.PlaceGenusCurationDb.Row addedNc = genusRows.notCurated(child.getSubfamily(), child.getGenus());
           if (addedNc != null) addedOther = org.calacademy.antweb.PlaceTaxonNotes.notCuratedCellHtml(addedNc.notCurated,
               org.calacademy.antweb.PlaceTaxonNotes.notCuratedUrl(child.getSubfamily(), child.getGenus(), overview));
           int addedImaged = genusRows.imaged(child.getSubfamily(), child.getGenus());
           if (addedImaged > 0) addedImages = "<a href='" + AntwebProps.getDomainApp() + "/images.do?" + child.getBrowserParams()
               + "&statusSet=" + Html.url(genusRows.choice) + "'>" + org.calacademy.antweb.ImagesAnywhere.speciesImagedCell(addedImaged) + "</a>";
         }
    %>
        <% if (displayGlobal) { %><div class="list_extras specimens">&ndash;</div><% } %>
        <div class="list_extras specimens<%= genusImagedCells ? " genus-col" : "" %>" title="Not on the list: from specimens collected here"><%= addedCount %></div>
        <% if (showNotCuratedColumn && !genusImagedCells) { %><div class="list_extras other"><%= addedOther %></div><% } %>
        <% if (!listOnly) { %><div class="list_extras images<%= genusImagedCells ? " genus-col" : "" %>"><%= addedImages %></div><% } %>
        <% if (showNotCuratedColumn && genusImagedCells) { %><div class="list_extras other genus-col"><%= addedOther %></div><% } %>
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
     if (Rank.SUBFAMILY.equals(pageRank) && subfamilyRows != null) {
       totalNote = "Genera total (administrators only): the Genera column adds up to " + Formatter.commaFormat(subtaxonTotal)
           + " genera of this view; it should equal the header of the genus list in the same view.";
     } else if (Rank.SUBFAMILY.equals(pageRank)) {
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
<%-- Folded like "About this list" at the top (Brian Fisher, 2026-11-04): click the arrow to read the notes. --%>
<details id="list-notes" class="about-list list-notes"><summary>Notes on specimen and image counts</summary>
<% if (totalNote != null) { %>
<br><%= ++noteNum %>. <span id="subtaxon-total" data-subtaxon-total="<%= subtaxonTotal %>"><%= totalNote %></span>
<% } %>
<% String genusOnlyLine = (Rank.SPECIES.equals(pageRank) && genusOnly != null) ? genusOnly.speciesListText() : null;
   if (genusOnlyLine != null) { %>
<br><%= ++noteNum %>. <span id="genus-only-note" data-genus-only="<%= genusOnly.totalSpecimens() %>"><%= org.calacademy.antweb.util.Html.esc(genusOnlyLine) %><% if (genusOnlySearch != null) { %> <a rel='nofollow' href='<%= org.calacademy.antweb.util.Html.escAttr(genusOnlySearch) %>'>Show all specimens.</a><% } %></span>
<% } %>
<br><%= ++noteNum %>. <span id="asterisk-note"><%= asterixNote %></span>
<br><%= ++noteNum %>. <span id="images-definition" class="images-definition"><%= org.calacademy.antweb.util.Html.esc(imagesDefinition) %></span>
</details>
<% } %>
