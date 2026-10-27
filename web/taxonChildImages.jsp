<!-- taxonChildImages.jsp -->

<%
	ArrayList<Taxon> childrenList = taxon.getChildren();

    //AntwebUtil.log(childrenList.toString());
    int totalImaged = taxon.getUniqueChildImagesCount("p", "h", "d");
    String optionalCaste = "";
    String tCaste = Caste.getDisplayCaste(request);
    if (!Caste.DEFAULT.equals(tCaste)) optionalCaste = "<font color=red>" + Formatter.initCap(tCaste) + "</font> ";
%>

<div id="totals_and_tools_container">
    <div id="totals_and_tools">
        <h2 class="display_count"><%=totalImaged%> <%= optionalCaste%><%= Rank.getRankPl(taxon.getNextRank(), totalImaged)%> Imaged <logic:present name="taxon" property="childrenCount">(<bean:write name="taxon" property="childrenCount" /> total)</logic:present></h2> 

        <span id="sub_taxon">
            <ul>
            <% TaxonSet taxonSet = taxon.getTaxonSet(); %>
                <li><%= taxonSet.getNextSubtaxon(2) %></li>            
                <li><%= taxonSet.getNextSubtaxon(3) %></li>
                <li><%= taxonSet.getNextSubtaxon(4) %></li>
                <%
                  // 2026-10-27 (Brian Fisher): a species' images come from anywhere in AntWeb; the place's specimens
                  // first. The old links to the global set (global=true) and back are replaced by
                  // placeOnly=true (BrowseAction, ImagesAnywhere).
                  Boolean imagesPlaceOnlyAttr = (Boolean) request.getAttribute("imagesPlaceOnly");
                  String imagesFrom = (String) request.getAttribute("imagesFromPhrase");
                  if (imagesPlaceOnlyAttr != null && imagesFrom != null) {
                     if (imagesPlaceOnlyAttr) { %>
                       <li><a class="images_anywhere" href="<%= org.calacademy.antweb.util.Html.escAttr(HttpUtil.getTargetMinusParam(request, ImagesAnywhere.PLACE_ONLY)) %>">All images of this <%= Html.esc(taxon.getRank()) %> in AntWeb</a></li>
                  <% } else { %>
                       <li><a class="images_place_only" href="<%= org.calacademy.antweb.util.Html.escAttr(HttpUtil.getTargetMinusParam(request, ImagesAnywhere.PLACE_ONLY) + "&" + ImagesAnywhere.PLACE_ONLY + "=true") %>">Only specimens <%= Html.esc(imagesFrom) %></a></li>
                <%   }
                  } %>

            </ul>
        </span>        
        
<%
		  if (true || !(taxon instanceof Subspecies) && !(taxon instanceof Species)) { 
		    // Mod Jan 17 2019. Don't know why we were omitting for species. See email thread: images not showing up on species page
              //A.log("taxonChildImages taxon:" + taxon.getClass()); 
              String statusSet = taxon.getStatusSetStr();
              String statusSetSize = taxon.getStatusSetSize();
              //A.log("taxonChildImages.jsp statusSetStr:" + statusSet + " statusSetSize:" + statusSetSize);            
              %>           

	          <%@ include file="/common/subgeneraDisplay.jsp" %>

	          <%@ include file="/common/statusesDisplay.jsp" %>
 	   <% }

          String browserParams = taxon.getBrowserParams();
          String imagesTrueStr = "";  %>
        <%@ include file="/common/imageViewsDisplay.jsp" %>

  	    <%@ include file="/common/casteViewsDisplay.jsp" %>
        
        <%@include file="/common/pageToolLinks.jsp" %>
        <%@ include file="/common/data_download_overlay.jsp" %>
        
        <!--/div the thumb_togle -->
        <div class="clear"></div>

<%

    //A.log("taxonChildImages");
	if (childrenList != null) { 
	
        String orderBy = request.getParameter("orderBy");
        Taxon.sortTaxa(orderBy, childrenList, overview);
	
	%>	
	<%@ include file="/unImagedTaxa.jsp" %>       
 <% } %>

    </div>
</div> <!-- totals_and_tools_container -->

<%-- The comparison form is closed at once; its fields point to it with form="getComparison".
     It must not wrap the images: the Pick and Unpick buttons are small POST forms of their own,
     and a form inside a form is dropped by the browser, so the first button submitted this form. --%>
<form id="getComparison" action="<%= AntwebProps.getDomainApp() %>/getComparison.do"></form>

<div id="page_data">
    <div id="domain" style="display:none;"><%= AntwebProps.getDomainApp() %></div>
<%
    // 2026-10-27 (images from anywhere): say where the images come from.
    java.util.Set<String> imagesPlaceCodes = (java.util.Set<String>) request.getAttribute("imagesPlaceCodes");
    Boolean imagesPlaceOnlyNote = (Boolean) request.getAttribute("imagesPlaceOnly");
    String imagesFromNote = (String) request.getAttribute("imagesFromPhrase");
    if (imagesPlaceOnlyNote != null && imagesFromNote != null) {
      if (imagesPlaceOnlyNote) { %>
    <p class="images_place_note" data-place-only="true">Only specimens <%= Html.esc(imagesFromNote) %> are shown.</p>
   <% } else if (imagesPlaceCodes != null) {
        int placeImaged = 0;
        if (childrenList != null) {
          for (Taxon c : childrenList) {
            if (c instanceof Specimen && ImagesAnywhere.isFromPlace(((Specimen) c).getCode(), imagesPlaceCodes)
                && c.getImages() != null && c.getImages().size() > 0) ++placeImaged;
          }
        }
        if (placeImaged == 0) { %>
    <p class="images_place_note" data-place-imaged="0"><b>No specimens <%= Html.esc(imagesFromNote) %> are imaged yet.</b> The images below are of specimens collected elsewhere, to help identification.</p>
     <% } else { %>
    <p class="images_place_note" data-place-imaged="<%= placeImaged %>">Images of this <%= Html.esc(taxon.getRank()) %> from anywhere in AntWeb, to help identification. The <%= placeImaged %> imaged specimen<%= placeImaged == 1 ? "" : "s" %> <%= Html.esc(imagesFromNote) %> come first, marked "<%= Html.esc(imagesFromNote) %>".</p>
     <% }
      }
    }


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
	String profile = "p";
	String dorsal = "d";
	String label = "l";
	String ventral = "v";

	boolean useShot = true;
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

    if (AntwebDebug.isDebugTaxon(taxon.getTaxonName())) {
      // This reallyCookie business is strange.
      useShot = true;
      use_thumb = "d";
      choice_is="Dorsal";
      String cookieStr = "";
      if (reallyCookie != null) cookieStr = reallyCookie.getValue();
      //A.log("taxonChildImages.jsp useShot:" + useShot + " use_thumb:" + use_thumb + " choice_is:" + choice_is + " reallyCookie:" + cookieStr + " totalImaged:" + totalImaged);
    }

%>
<input form="getComparison" type="hidden" name="name" value="<bean:write name="showTaxon" property="name"/>">
<input form="getComparison" type="hidden" name="rank" value="<bean:write name="showTaxon" property="rank"/>">
<% if (Rank.SPECIES.equals(taxon.getRank())) { %>
<input form="getComparison" type="hidden" name="genus" value="<%= org.calacademy.antweb.util.Html.escAttr(taxon.getGenus()) %>">
<% } %>
<input form="getComparison" type="hidden" name="overview" value="<%= org.calacademy.antweb.util.Html.escAttr(overview.getName()) %>">

 <%
    if (useShot) { %>
 <input form="getComparison" id="thumb_choice" type="hidden" name="shot" value="<%= choice_is %>" checked>
 <% }
    if (totalImaged > 0) {
        ArrayList<Taxon> theChildren = taxon.getChildren();

        //AntwebUtil.log("taxonChildImages.jsp total:" + totalImaged + " list:" + childrenList.toString());

        if (theChildren != null) {
			// if children are specimen, re-order by caste.

          //A.log("taxonChildImages.jsp size:" + theChildren.size());

			if (taxon.isSpeciesOrSubspecies()) {
              theChildren = Caste.sortSpecimenByCasteSubcaste(theChildren);
              if (imagesPlaceCodes != null) {
                // The place's specimens first, then the representatives (picked defaults, the specimen shown for
                // the name), then the others; each group by caste.
                java.util.List<String> representatives = new java.util.ArrayList<>();
                for (String rc : new String[] {Caste.WORKER, Caste.QUEEN, Caste.MALE}) {
                  if (taxon.getDefaultSpecimen(rc) != null) representatives.add(taxon.getDefaultSpecimen(rc));
                }
                if (taxon.getImages() != null && !taxon.getImages().isEmpty()) {
                  String shownCode = ((SpecimenImage) taxon.getImages().elements().nextElement()).getCode();
                  if (shownCode != null) representatives.add(shownCode);
                }
                theChildren = ImagesAnywhere.placeFirstSpecimens(theChildren, imagesPlaceCodes, representatives);
              }
			}

          //A.log("taxonChildImages.jsp size:" + theChildren.size());

			int childCount = 0;
			int rows = theChildren.size() / 4 + 1;
			int index = 0;
			int position = 0;
			int imgCount = 0;
			int first = 1;
			int fourth = 4;
			int loop = 0;
			int total = totalImaged;
			while (loop < rows) {
				int count = 1;
				int innerLoop = 0;
				boolean hasNext = childCount < theChildren.size();

   	  	  	    //AntwebUtil.log("taxonChildImages loop:" + loop + " rows:" + rows + " innerLoop:" + innerLoop);

				while ((innerLoop < 4) && (hasNext)) {
				   if (hasNext) {
					 try {
					   Taxon thisChild = theChildren.get(childCount);
					   ++childCount;
					   hasNext = childCount < theChildren.size();

					   String maleSpecimen = taxon.getDefaultSpecimen(Caste.MALE);                    
					   String workerSpecimen = taxon.getDefaultSpecimen(Caste.WORKER);                    
					   String queenSpecimen = taxon.getDefaultSpecimen(Caste.QUEEN);     

					   //A.log("taxonChildImages m:" + maleSpecimen + " w:" + workerSpecimen + " q:" + queenSpecimen);
%>
						<%@include file="/taxonChildImageSet.jsp" %>
<%
					  } catch (IndexOutOfBoundsException e) {
						AntwebUtil.log("taxonChildImages.jsp Investigate.  trapped:" + e 
						  + " taxon:" + taxon.getTaxonName() 
						  + " browserParams:" + taxon.getBrowserParams());
					  }
				  } // end if iterator has next  
				  innerLoop++; 
				} // end while innerLoop 
				loop++; 
			} //  end outer while loop < rows 
		}
    } // totalImaged > 0   
%>
<div class="clear"></div>
<%@ include file="/common/imagesDefinition.jsp" %>
</div>
