<!-- taxonChildImages.jsp -->

<%
	ArrayList<Taxon> childrenList = taxon.getChildren();

    // AntwebUtil.log("warn", " :" + taxon.getChildImagesCount());
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
                  //String global = request.getParameter("global");
                  //boolean isGlobal = "true".equals(global);

                  if (displayGlobal) {
                     if (isGlobal) { %>
                       <li><a href="<%= HttpUtil.getTargetReplaceParam(request, "global=true", "") %>">See <%= overview %> only</a></li>
                     <% } else if (!isGlobal) { %>
                       <li><a href="<%= HttpUtil.getTarget(request) + "&global=true" %>">See global set</a></li>                
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

<form id="getComparison" action="<%= AntwebProps.getDomainApp() %>/getComparison.do">

<div id="page_data">
    <div id="domain" style="display:none;"><%= AntwebProps.getDomainApp() %></div>
<%
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
      A.log("taxonChildImages.jsp useShot:" + useShot + " use_thumb:" + use_thumb + " choice_is:" + choice_is + " reallyCookie:" + cookieStr + " totalImaged:" + totalImaged);
    }

%>
<input type="hidden" name="name" value="<bean:write name="showTaxon" property="name"/>">
<input type="hidden" name="rank" value="<bean:write name="showTaxon" property="rank"/>">
<logic:equal value="species" name="showTaxon" property="rank">
<input type="hidden" name="genus" value="<bean:write name="showTaxon" property="genus" />">
</logic:equal>
<input type="hidden" name="overview" value="<%= overview.getName() %>">

 <%
    if (useShot) { %>
 <input id="thumb_choice" type="hidden" name="shot" value="<%= choice_is %>" checked>
 <% }
    if (totalImaged > 0) {
        ArrayList<Taxon> theChildren = taxon.getChildren();
        if (theChildren != null) {
			// if children are specimen, re-order by caste.
			if (taxon.isSpeciesOrSubspecies()) {
              theChildren = Caste.sortSpecimenByCasteSubcaste(theChildren);
			}
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
                //A.log("taxonChildImages.jsp size:" + theChildren.size());
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
</div>

<!-- /html:form -->
</form>
