<%@ page import = "org.calacademy.antweb.Login" %>
<%@ page import = "java.util.ArrayList" %>

<!-- specimenReport.jsp -->

<%
/*
Legacy Merge Sort: true

The sort will fail with: java.lang.IllegalArgumentException: Comparison method violates its general contract!
if the server is not configured to use Legacy Merge Sort.
To fix this proper would involve rewriting Species.sort().
*/

//AntwebUtil.log("specimenReport.jsp: MODE IS:" + mode);
    
int index = -1;
boolean isBrowsePage = false;
String target = HttpUtil.getTarget(request); 
String requestUri = HttpUtil.getRequestURI(request);
if ( (requestUri != null) && requestUri.contains("browse.do") ) isBrowsePage = true;
boolean isAdvancedSearchPage = false;
if (pageContainer.equals("advancedSearchResults")) isAdvancedSearchPage = true;
String for_chosen = "";

String sortedBy = (String) request.getParameter("sortBy");
if (sortedBy == null) {
  if (isAdvancedSearchPage) {
    sortedBy = "name";
  } else {
    sortedBy = "code";
  }
}
//A.log("specimenReport.jsp sortedBy:" + sortedBy); // + " name:" + name + " code:" + code);
%>

</style>
<![endif]-->
<logic:notPresent parameter="mode">
<div id="specimen_list_header_container">
<div id="specimen_list_header">

    <div id="thumb_toggle">
     <% if (isBrowsePage) { 
         // See basicLayout.jsp conditions for inclusion of necessary javascript code       
         %>
           <form id="sortby">
            <input type="hidden" id="sortby_action" name="sortby_action" value="<%= AntwebProps.getDomainApp() %>/browse.do" />
            <input type="hidden" id="ns_sortby_extras" name="b" value="<%= HttpUtil.getQueryStringNoQuestionMark(request) %>" />
            <input type="hidden" id="show_tool" name="t" value="" />
            <span class="label">Sort by:</span>
            <select name="sortBy" id="sortBy_select">
     <% } else { %>
          <form id="sortby">
            <input type="hidden" id="sortby_action" name="sortby_action" value="<%= AntwebProps.getDomainApp() %>/advancedSearchResults.do" />
            <input type="hidden" id="sortby_extras" name="b" value="" />
            <input type="hidden" id="show_tool" name="t" value="" />
            <span class="label">Sort by</span>
            <select name="sortBy" id="sortBy_select">
     <% } %>

          <option value="bioregion" <%= ("bioregion".equals(sortedBy) ? "selected" : "") %>>Bioregion</option>
          <option value="caste" <%= ("caste".equals(sortedBy) ? "selected" : "") %>>Caste</option>
          <option value="collectedby" <%= ("collectedby".equals(sortedBy) ? "selected" : "") %>>Collected By</option>
        <% if (!pageContainer.equals("collection")) { %>
          <option value="collection" <%= ("collection".equals(sortedBy) ? "selected" : "") %>>Collection Code</option>
        <% } %>
          <option value="datecollected" <%= ("datecollected".equals(sortedBy) ? "selected" : "") %>>Date Collected</option>
          <option value="country" <%= ("country".equals(sortedBy) ? "selected" : "") %>>Country</option>
          <option value="databy" <%= ("databy".equals(sortedBy) ? "selected" : "") %>>Data Provided By</option>
          <option value="determinedby" <%= ("determinedby".equals(sortedBy) ? "selected" : "") %>>Determined By</option>
          <option value="dna" <%= ("dna".equals(sortedBy) ? "selected" : "") %>>DNA Notes</option>
          <option value="elevation" <%= ("elevation".equals(sortedBy) ? "selected" : "") %>>Elevation</option>
          <option value="habitat" <%= ("habitat".equals(sortedBy) ? "selected" : "") %>>Habitat</option>
          <option value="latitude" <%= ("latitude".equals(sortedBy) ? "selected" : "") %>>Latitude</option>
          <option value="lifestage" <%= ("lifestage".equals(sortedBy) ? "selected" : "") %>>Life Stage</option>
          <option value="locality" <%= ("location".equals(sortedBy) ? "selected" : "") %>>Location</option>
          <option value="locatedat" <%= ("locatedat".equals(sortedBy) ? "selected" : "") %>>Located At</option>
          <option value="longitude" <%= ("longitude".equals(sortedBy) ? "selected" : "") %>>Longitude</option>
          <option value="medium" <%= ("medium".equals(sortedBy) ? "selected" : "") %>>Medium</option>
          <option value="method" <%= ("method".equals(sortedBy) ? "selected" : "") %>>Method</option>
          <option value="microhabitat" <%= ("microhabitat".equals(sortedBy) ? "selected" : "") %>>Microhabitat</option>
          <option value="museum" <%= ("museum".equals(sortedBy) ? "selected" : "") %>>Museum</option>
          <option value="images" <%= ("images".equals(sortedBy) ? "selected" : "") %>>Number of Images</option>
          <option value="ownedby" <%= ("ownedby".equals(sortedBy) ? "selected" : "") %>>Owned By</option>
          <option value="code" <%= ("code".equals(sortedBy) ? "selected" : "") %>>Specimen Code</option>
        <% if (!pageContainer.equals("showBrowse")) { %>
            <option value="name" <%= ("name".equals(sortedBy) ? "selected" : "") %>>Specimen Name</option>
        <% } %>
          <option value="specimennotes" <%= ("specimennotes".equals(sortedBy) ? "selected" : "") %>>Specimen Notes</option>
          <option value="type" <%= ("type".equals(sortedBy) ? "selected" : "") %>>Type Status</option>
          <option value="created" <%= ("databy".equals(sortedBy) ? "selected" : "") %>>Uploaded</option>
          <option value="uploadid" <%= ("uploadId".equals(sortedBy) ? "selected" : "") %>>Upload ID</option>
        </select>

        </form>
    </div>
    <div class="clear"></div> 
</div>
</div>
<div class="clear"></div>
</logic:notPresent>
<div id="domain" style="display:none;"><%= AntwebProps.getDomainApp() %></div>
<%
    String use_thumb = null; // new String();
    String the_cookie = "thumbs";
    Cookie the_cookies [] = request.getCookies ();
    Cookie reallyCookie = null;
       if (the_cookies != null) {
           for (int i = 0; i < the_cookies.length; i++) {
           if (the_cookies [i].getName().equals (the_cookie)) {
               reallyCookie = the_cookies[i];
               break;
           }
       }
    }
    if (reallyCookie == null) {
       use_thumb = "h";   
    } else {
       use_thumb = reallyCookie.getValue();   
    }
    //A.log("specimenReport.jsp use_thumb:" + use_thumb + " reallyCookie:" + reallyCookie);
    String choice_is = use_thumb;
    String profile = "p";
    String dorsal = "d";    
    String ventral = "v";    
    String label = "l";
    //String head = "h";

    boolean useShot = true;
    // This same code is in searchResultsByTaxon-body.jsp.
    if (use_thumb.equals(profile)) {
        choice_is = "Profile";
    } else if (use_thumb.equals(dorsal)) {
        choice_is = "Dorsal";
    } else if (use_thumb.equals(ventral)) {
        choice_is = "Ventral";
    } else if (use_thumb.equals(label)) {
        choice_is = "Label";
    } else if (use_thumb.equals("a")) {
        useShot = false;
    } else {
        choice_is = "Head";
    }
     
    if (isAdvancedSearchPage) { %> 

    <form id="theform" name="taxaFromSearchForm" method="POST">
<%    
      Login srAccessLogin = LoginMgr.getAccessLogin(request);

      String username = null;
      if (srAccessLogin != null) {
        username = srAccessLogin.getName();
      } 
      if ((srAccessLogin == null) && ((username != "photo_review") || (srAccessLogin.isUploadImages() == false))) { 

        if (useShot) { %>
    <input id="thumb_choice" type="hidden" name="shot" value="<%= choice_is %>" checked>
     <% }
      }
    } else { %>
<form id="theform" name="browseForm" method="POST">
<%
	  // Probably caused by direct JSP access.
	  if (taxon.toString() == null) {
	    AntwebUtil.log("specimenReport() Why is taxon.toString() null for:" + taxon.getTaxonName() + "");
	    // Correlates with getFullName being blank, and showTaxon below undefined.
	    // Happens: http://localhost/antweb/showBrowse.jsp?genus=tapinoma&species=melanocephalum&rank=species&project=floridaants
	    return;
	  }
%>

<input type="hidden" name="name" value="<bean:write name="showTaxon" property="name"/>">
<input type="hidden" name="rank" value="<bean:write name="showTaxon" property="rank"/>">
<logic:equal value="species" name="showTaxon" property="rank">
<input type="hidden" name="genus" value="<bean:write name="showTaxon" property="genus" />">
</logic:equal>
<input type="hidden" id="the_project" name="project" value="">

<% 
      if (useShot) { %>
<input id="thumb_choice" type="hidden" name="shot" value="<%=choice_is%>" checked>
   <% } %>
<%  } // if advancedSearch or else

    if (taxon.getChildren() == null) {
      AntwebUtil.log("specimenReport.jsp taxon.getChildren() is null. No display of specimenReport for taxon:" + taxon);
      return;
    }

    // To avoid ConcurrentModificationException
    ArrayList<Taxon> specimenReportTaxa = taxon.getChildren();
    int specimenReportTaxaSize = specimenReportTaxa.size();
    Taxon[] specimenReportTaxaArray = new Taxon[specimenReportTaxaSize];
    specimenReportTaxa.toArray(specimenReportTaxaArray);
    //A.log("specimenReport.jsp specimenReportTaxaArray:" + specimenReportTaxaArray.length + " taxonChildren:" + taxon.getChildren().size());

 for (Taxon thisSpecimen : specimenReportTaxaArray) {
    Specimen thisChild = (Specimen) thisSpecimen;
    //AntwebUtil.log("specimenReport.jsp children code:" + ((Specimen) child).getCode() + " groupName:" + ((Specimen) child).getGroupName());

    ++index;
    //A.log("specimenReport.jsp index:" + index + " thisChild:" + thisChild);
    if (index <= limit) { // set in showBrowse-body.jsp from Taxon.MAX_DISPLAY or override.

      // Note the advancedSearchResults-body.jsp also has a hardcoded 2000
      //A.log("specimenReport.jsp index:" + index); }
    
      //Specimen thisChild = (Specimen) child;
      String specimenDagger = "";
      if (thisChild.getIsFossil()) specimenDagger = "&dagger;";
      
      String specimenIntroduced = "";    
      if (thisChild.getIsIntroduced()) specimenIntroduced = "<a href='" + AntwebProps.getDomainApp() + "/common/distStatusDisplayPage.jsp' target='new'><img src='" + AntwebProps.getDomainApp() + "/image/redI.png' width='11' height='12' border='0' title='Introduced'></a>";    

      if (!thisChild.getName().equals("\"\"")) { 
          String moreData = "";
          if ((thisChild.getTypeStatus() != null) && !"".equals(thisChild.getTypeStatus())) moreData += "type:" + thisChild.getTypeStatus() + ", ";
          if ((thisChild.getUploadDate() != null) && !"".equals(thisChild.getUploadDate())) moreData += "uploadDate:" + thisChild.getUploadDate();
          //AntwebUtil.log("More data:" + moreData);
          /* It seems that childrenCount is > -1 with no mode and = 0 with a mode */ 
          if (isBrowsePage) {
            for_chosen = thisChild.getName();
          }
          if (isAdvancedSearchPage) {
            for_chosen = new Integer(index).toString();
          }
	      //A.log("specimenReport.jsp thisChild.getImageCount:" + ((Taxon) thisChild).getImageCount());

          if (thisChild.getChildrenCount() < 0) {
%>
            <!-- logic:lessThan name="thisSpecimen" property="childrenCount" value="0" --> 
            <!-- specimenReport.jsp search and specimen list -->
            <div class="specimen_layout<% if (!thisChild.getHasImages()) { %> no_photos<% } %>">
                <div class="sd_checkbox">
                    <span class="sdcb<% if (!thisChild.getHasImages()) { %> np<% } %>"><input type="checkbox" class="thecb" name="chosen" value="<%= for_chosen %>"></span>
                </div>
                <div class="sd_data">
                    <div class="sd_specimen_code">
                        <%= specimenDagger %><%= specimenIntroduced %>
                        <a href="<%= AntwebProps.getDomainApp() %>/specimen.do?code=<%= thisChild.getName() %>"><span class="
                          <%= ("code".equals(sortedBy) ? "sorted_by" : "") %>"><%= thisChild.getPrettyName() %></span></a><br />
                            <span class="<%= ("images".equals(sortedBy) ? "sorted_by" : "") %>">

                   <% if (thisChild instanceof Specimen) {            
                        if (thisChild.getImageCount() == 0) { %>
                                 No Images
                     <% } else { %> 
                                 <span class='numbers'><%= thisChild.getImageCount() %></span> Images
                     <% } %>

                          <%  // Not sure if this functionality is ever reached. 
                      } else {
                        AntwebUtil.log("specimenReport functionality not deprecated!!!!!!");
                        if (((Taxon) thisChild).getTaxonSet().getImageCount() > 0) { %>
                          <a href="<%= AntwebProps.getDomainApp() %>/specimenImages.do?name=<%= thisChild %>">
                            <%= ((Taxon) thisChild).getTaxonSet().getImageCountStr() %>
                          </a>              
                     <% } else { %>
                            <%= ((Taxon) thisChild).getTaxonSet().getImageCountStr() %>              
                     <% }
                      } // is Specimen %>
                        </span>
                    </div>
                    <%
                      if (!pageContainer.equals("showBrowse")) {
                       %>
                      <div class="sd_specimen_name"><%@include file="/common/statusDisplayChild.jsp" %><a href="<%= AntwebProps.getDomainApp() %>/description.do?<%= thisChild.getBrowserParams() %>"><span class="<%= ("name".equals(sortedBy) ? "sorted_by" : "") %>"><%= new Formatter().capitalizeFirstLetter(thisChild.getSimpleName()) %></span></a></div>
                   <% } else { %>
                      <div class="sd_specimen_name">&nbsp;</div>
                   <% } %>

                   <% if (!pageContainer.equals("collection")) { %>
                      <div class="sd_collection_code">Collection: <a href="<%= AntwebProps.getDomainApp() %>/collection.do?name=<%= thisChild.getCollectionCode() %>"><span class="<%= ("collection".equals(sortedBy) ? "sorted_by" : "") %>"><%= thisChild.getCollectionCode() %></a></div>
                   <% } else { %>
                      <div class="sd_specimen_name">&nbsp;</div>
                   <% } %>

                    <div class="sd_location">
                        <span class="label">Location:</span>
            
                   <% 
                     String infoType = null;
                     if ("locality".equals(sortedBy)) infoType = "locality";
                     if ("country".equals(sortedBy)) infoType = "country";
%>
                     <span class="data"><%= ((Specimen) thisChild).getLocalityInfoString(infoType) %></span>

                   <% //AntwebUtil.log("specimenReport.jsp localityInfo:" + ((Specimen) thisChild).getLocalityInfoString(sortBy)); %>
                        <div class="clear"></div>
                    </div>
                    <div class="sd_latlong" title="Latitude/Longitude"><span class="<%= ("latitude".equals(sortedBy) ? "sorted_by" : "") %>"><%= Utility.notBlankValue(thisChild.getDecimalLatitude()) %></span>
                      &deg;,<span class="<%= ("longitude".equals(sortedBy) ? "sorted_by" : "") %>"><%= Utility.notBlankValue(thisChild.getDecimalLongitude()) %>&deg;</span></div>
                    <div class="sd_elevation">Elevation: <span class="<%= ("elevation".equals(sortedBy) ? "sorted_by" : "") %>"><%= Utility.notBlankValue(thisChild.getElevation()) %>m</span></div>
                    <div class="clear"></div>
                </div>
                <div class="clear"></div>
            <% 
                      //A.log("specimenReport.jsp rank:" + thisChild.getRank());
                      // http://localhost/antweb/advancedSearch.do?searchMethod=advancedSearch&advanced=true&isIgnoreInsufficientCriteria=false&sortBy=name&collGroupOpen=none&specGroupOpen=none&geoGroupOpen=none&typeGroupOpen=none&typeGroupOpen=none&searchType=contains&name=Camponotus+afrc-za61&familySearchType=equals&family=Formicidae&subfamilySearchType=equals&subfamily=&genusSearchType=equals&genus=&speciesSearchType=contains&species=&subspeciesSearchType=contains&subspecies=&bioregion=&country=&adm1=&adm2SearchType=contains&adm2=&localityNameSearchType=contains&localityName=&localityCodeSearchType=contains&localityCode=&habitatSearchType=contains&habitat=&elevationSearchType=greaterThanOrEqual&elevation=&methodSearchType=contains&method=&microhabitatSearchType=equals&microhabitat=&collectedBySearchType=equals&collectedBy=&collectionCodeSearchType=contains&collectionCode=&dateCollectedSearchType=greaterThanOrEqual&dateCollected=&specimenCodeSearchType=contains&specimenCode=&locatedAtSearchType=contains&locatedAt=&lifeStageSearchType=contains&lifeStage=&casteSearchType=contains&caste=&mediumSearchType=contains&medium=&specimenNotesSearchType=contains&specimenNotes=&dnaExtractionNotesSearchType=contains&dnaExtractionNotes=&ownedBySearchType=contains&ownedBy=&createdSearchType=equals&
                      if ("specimen".equals(thisChild.getRank())) {  
                        int maxStrLength = 24;
                        //A.iLog("specimenReport.jsp results:" + ((Specimen)child).getDateCollectedStart());

            %>
                <div class="sd_subdata">
                    <div class="sd_subdata_items">
                     <% String habitatStr = ((Specimen) thisChild).getHabitat();
                        if (habitatStr != null && habitatStr.length() > maxStrLength) habitatStr = habitatStr.substring(0, maxStrLength) + "...";
                         %>
                        Habitat: <span class="<%= ("habitat".equals(sortedBy) ? "sorted_by" : "") %>"><%= Utility.notBlankValue(habitatStr) %></span><br />
                        Microhabitat: <span class="<%= ("microhabitat".equals(sortedBy) ? "sorted_by" : "") %>"><%= Utility.notBlankValue(thisChild.getMicrohabitat()) %></span><br />
                     <% String specNotes = ((Specimen) thisChild).getSpecimenNotes();
                        if (specNotes != null && specNotes.length() > maxStrLength) specNotes = specNotes.substring(0, maxStrLength) + "...";
                        %>
                        Notes:<span class="<%= ("specimennotes".equals(sortedBy) ? "sorted_by" : "") %>"><%= Utility.notBlankValue(specNotes) %></span><br />
                        Medium:<span class="<%= ("medium".equals(sortedBy) ? "sorted_by" : "") %>"><%= Utility.notBlankValue(thisChild.getMedium()) %></span><br />
                    </div>
                    <div class="sd_subdata_items">      
                        Collected by: <span class="<%= ("collectedby".equals(sortedBy) ? "sorted_by" : "") %>"><%= Utility.notBlankValue(thisChild.getCollectedBy()) %></span><br />
                        Date Collected: <span class="<%= ("datecollected".equals(sortedBy) ? "sorted_by" : "") %>"><%= Utility.notBlankValue(thisChild.getDateCollectedStart()) %></span><br />
                        Uploaded: <span class="<%= ("created".equals(sortedBy) ? "sorted_by" : "") %>"><%= Utility.notBlankValue(thisChild.getCreated()) %></span><br />
                        <% String groupLink = "";
                           groupLink = thisChild.getGroup().getLink();
                           //groupLink = thisChild.getGroupName();
                        %>
                        Data provided by: <span class="<%= ("databy".equals(sortedBy) ? "sorted_by" : "") %>"><%= Utility.notBlankValue(groupLink) %></span>
                    </div>
                     <% //A.log("specimenReport.jsqp code:" + ((Specimen)thisChild).getCode() + " created:" + ((Specimen)thisChild).getCreated()); %>
                    <div class="sd_subdata_items">
                        Owned by: <span class="<%= ("ownedby".equals(sortedBy) ? "sorted_by" : "") %>"><%= thisChild.getOwnedByLink() %></span><br />
                        Located At: <span class="<%= ("locatedat".equals(sortedBy) ? "sorted_by" : "") %>"><%= thisChild.getLocatedAtLink() %></span><br />
                        Determined by: <span class="<%= ("determinedby".equals(sortedBy) ? "sorted_by" : "") %>"><%= Utility.notBlankValue(thisChild.getDeterminedBy()) %></span><br />
                    </div>
                    <div class="sd_subdata_items">
                        Method: <span class="<%= ("method".equals(sortedBy) ? "sorted_by" : "") %>"><%= Utility.notBlankValue(thisChild.getMethod()) %></span><br />
                        DNA Notes: <span class="<%= ("dna".equals(sortedBy) ? "sorted_by" : "") %>"><%= Utility.notBlankValue(thisChild.getDnaExtractionNotes()) %></span><br />
                        Bioregion: <span class="<%= ("bioregion".equals(sortedBy) ? "sorted_by" : "") %>"><% if (!Utility.isBlank(((Specimen) thisChild).getBioregion())) { %><%= ((Specimen) thisChild).getBioregion() %><% } %></span><br />
                    </div>
                    <div class="sd_subdata_items">
                        Type Status: <span class="<%= ("type".equals(sortedBy) ? "sorted_by" : "") %>"><% if (thisChild.getTypeStatus() != null && !"".equals(thisChild.getTypeStatus())) { %><img style="top:1px;position:relative;" src="<%= AntwebProps.getDomainApp() %>/image/has_type_status_icon.png"><% } %> <%= Utility.notBlankValue(thisChild.getTypeStatus()) %></span><br />
                        Life Stage: <span class="<%= ("lifestage".equals(sortedBy) ? "sorted_by" : "") %>"><%= Utility.notBlankValue(new Formatter().capitalizeFirstLetter((String) thisChild.getLifeStage())) %></span><br />
                        Caste: <span class="<%= ("caste".equals(sortedBy) ? "sorted_by" : "") %>"><%= Utility.notBlankValue(new Formatter().capitalizeFirstLetter((String) thisChild.getCaste())) %></span><br />
                        Subcaste: <%= Utility.notBlankValue(new Formatter().capitalizeFirstLetter((String) thisChild.getSubcaste())) %><br />
                    </div>
                </div>
                   <% } // "specimen".equals(thisChild.getRank() %>

            <div class="clear"></div>
            </div>
<%
          } %>
<!-- /logic:lessThan -->

       <% if (thisChild.getChildrenCount() > -1) { %>
            <!-- and now for the browse -->
            <%@include file="/common/statusDisplayChild.jsp" %>
         <% if ("specimen".equals(thisChild.getRank())) { %>            
                <%= specimenDagger %>&nbsp<%= specimenIntroduced %>&nbsp;
                <div class=browse_col_one><a href="specimen.do?name=<%= thisChild.getName() %>">
         <% } else { 
                if (thisChild.getHasImages()) {
         %>
                    <div class=browse_span_col><a href="description.do?<%= thisChild.getBrowserParams() %>">
                    <img src="<%= AntwebProps.getDomainApp() %>/image/has_photo.gif" border="0" title="<%= thisChild.getHasImagesCount() %> Images"></a>
             <% } else { %>
                    <div class=browse_span_col><img src="<%= AntwebProps.getDomainApp() %>/image/no_photo.gif" border="0">
             <% } 
                if (thisChild.getChildrenCount() > 0) { %>
                    <a href="<%= AntwebProps.getDomainApp() %>/description.do?<%= thisChild.getBrowserParams() %>">
             <% } %>
         <% } %>
            <%= thisChild.getPrettyName() %>

            <!-- Close the anchor tag -->
         <% if ("species".equals(thisChild.getRank()) || "specimen".equals(thisChild.getRank())) { %>
                </a>
         <% } %>
         <% if (!"species".equals(thisChild.getRank()) && !"specimen".equals(thisChild.getRank()) && thisChild.getChildrenCount() > 0) { %>
                </a>
         <% } %>
         <% if (!"specimen".equals(thisChild.getRank())) { %>
                &nbsp;&nbsp;&nbsp;<a href="<%= AntwebProps.getDomainApp() %>/browse.do?<%= thisChild.getBrowserParams() %>"><img src="<%= AntwebProps.getDomainApp() %>/image/browse_icon.png" border=0 alt="Browse"/></a></div>
         <% } %>
        <div id=col_line><hr></hr></div>
       <% } // childrenCount > -1 %>
  <%  } else { // Name is not empty quotes
        if (AntwebProps.isDevMode()) AntwebUtil.log("name:" + thisChild.getName());
      } 
    }  // Index < taxon.MAX_DISPLAY. Only display N records.

} // For loop
%>

</form>
