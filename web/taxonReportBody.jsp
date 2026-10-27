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
    // Images column (2026-10-27): images of the name in AntWeb, and in brackets those of this place when fewer.
    String imagesFromPhrase = org.calacademy.antweb.ImagesAnywhere.fromPhrase(overview);
    String imagesColumnTitle = "Images of the ant of the name in AntWeb, from any specimen (labels are not counted)"
        + (imagesFromPhrase == null ? "." : "; in brackets, those of specimens " + imagesFromPhrase + " when fewer.");
    // The definition under the list (Brian Fisher, 2026-10-27: every Images column counts images of the ant only).
    String imagesDefinition = "Images: images of the ant (head, profile, dorsal and other views); label images are not counted."
        + (imagesFromPhrase == null ? "" : " The first number is the images of the name in AntWeb, from any specimen; in brackets, those of specimens " + imagesFromPhrase + ", when fewer.");
    String asterixNote = "Counts include " + nextPluralRank + " of all statuses";
    if (displayGlobal){
      asterixNote += ".";
    } else {
      asterixNote += " contained within " + locale + "."; 
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
		String sortLink = HttpUtil.removeParam(thisTarget, "orderBy");
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
         // the images of the name in AntWeb and, when the place has fewer, the place's own: "4 Images (0 from Comoros)".
         String imagesCell = org.calacademy.antweb.ImagesAnywhere.imagesColumn(childTaxonSet.getGlobalImageCount(), childTaxonSet.getImageCount(), imagesFromPhrase);
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
<% if (LoginMgr.isAdmin(request)) { %>
     <br>Subtaxon total: <%= subtaxonTotal %>
<% } %>
<% if (listOnly) { %>
<BR><span id="port-of-entry-list-note">These are ant species intercepted at ports of entry, not established introductions. They are not counted in any place total or in the introduced species list, and this list shows no specimen or image numbers.</span>
<% } else { %>
<BR>* <%= asterixNote %>
<BR><span id="images-definition"><%= org.calacademy.antweb.util.Html.esc(imagesDefinition) %></span>
<% } %>
