
<div id="georegions" class="subnav">
  <ul id="accordion">
<!-- begin listing available projects -->

<%  ArrayList<Region> regions = GeolocaleMgr.getDeepRegions();

    if (regions != null) {
        //AntwebUtil.log("georegionMenu.jsp regions:" + regions);
        for (Region region : regions) {        
            if (!region.getIsLive()) continue;
            
            //if (AntwebProps.isDevMode()) AntwebUtil.log("georegionMenu.jsp region:" + region.fullReport());        
            String titleHTML = "";

			String regionHref = " href='" + AntwebProps.getDomainApp() + "/region.do?name=" + region.getName() + "'";

            if (region.getName().equals("projectsants") || region.getName().equals("globalants")) {
              titleHTML = "<a href=\"#\" onClick=\"return false;\">" + region.getName() + "</a>";  
            } else { // if (region.getName().equals("Africa")) {
           	  //  titleHTML = "<a class='has_items' href='" + AntwebProps.getDomainApp() + "/region.do?name=Africa' >Africa</a>";
              // } else {
              titleHTML = "<a class='has_items'>" + region.getName() + "</a>";  // no href
            }
            ArrayList<Subregion> subregions = region.getSubregions();
            if (subregions != null && !subregions.isEmpty()) {
            
            //titleHTML += "<a" + regionHref + ">.</a>";  // This will add a hyperlink but will break the accordion
%>
    <li class="show_items"><%= titleHTML %>
      <ul class="subregion_list">
<%              for (Subregion subregion : subregions) {

                    if (!subregion.getIsLive()) continue;
            
                    //A.log("georegionMenu.jsp region:" + region + " subregion:" + subregion + " empty:" + subregion.getLiveCountries().isEmpty());                    

					String subregionHref = " href='" + AntwebProps.getDomainApp() + "/subregion.do?name=" + subregion.getName() + "'";

                    if(subregion.getLiveCountries().isEmpty()) {
                        titleHTML = "<a>" + subregion.getName() + "</a>";  // no href
                    } else {
                        titleHTML = "<a class='has_items'>" + subregion.getName() + "</a>";  // no href
                    }

                    //titleHTML += "<a" + subregionHref + ">.</a>";  // This will add a hyperlink but will break the accordion
%>
        <li><%= titleHTML %>
          <ul class="country_list">
<%                  ArrayList<org.calacademy.antweb.geolocale.Country> countries = subregion.getLiveCountries();

                    for (org.calacademy.antweb.geolocale.Country country : countries) {

//A.log("georegionMenu.jsp region:" + region + " subregion:" + subregion + " country:" + country + " live:" + country.getIsLive());                    

                        if (!country.getIsLive()) {
                          //if (AntwebProps.isDevMode()) AntwebUtil.log("georegionMenu.jsp isLive");
                          continue;	
                        }

  					    String countryHref = " href='" + AntwebProps.getDomainApp() + "/country.do?name=" + country.getName() + "'";

                        ArrayList<Adm1> adm1s = country.getLiveAdm1s();                        
                        if (!adm1s.isEmpty()) {

                            titleHTML = "<a class='has_items'>" + country.getName() + "</a>";  // no href
                            //AntwebUtil.log("georegionMenu.jsp title:" + titleHTML + " name:" + country.getName());

                        //titleHTML += "<a" + countryHref + ">.</a>";  //This will add a hyperlink but will break the accordion
                            
%>
            <li><%= titleHTML %>
              <ul class="adm1_list region_items">
<%

                            int adm1i = 0;
                            for (Adm1 adm1 : adm1s) { 
                              //A.log("countryName:" + country + " adm1:" + adm1);
                              if (adm1i == 0) {
                                 ++adm1i;
                                 String countryName = country.getName();
                                 //A.log("countryName:" + countryName + " adm1:" + adm1);
								 if ((AntwebProps.isDevMode() || LoginMgr.isMichele(request)) && "United States".equals(countryName)) {
								   countryName = "Untethered Sociopaths<br>of Alienation"; // This is a joke for Michele.
                                 }
                                  %>
                        <li><a href="<%= country.getThisPageTarget() %>"><img src="<%= AntwebProps.getDomainApp() %>/image/flag/16/<%= country.getFlagIcon() %>">&nbsp;<%= countryName %></a></li>
                           <% }
                           %>
                                <!-- taxonomicPage.do?rank=genus&project=< %= adm1.getUseName() % -->
                                <li><a href="<%= adm1.getThisPageTarget() %>"><%= adm1.getName() %></a></li>
         <% if ("Georgia".equals(adm1.getName())) {
                        Country hawaii = GeolocaleMgr.getIsland("Hawaii");
         %>
                                <li><a href="<%= hawaii.getThisPageTarget() %>">Hawaii</a></li>
        <% } %>
<%
                            }
%>
              </ul> <!-- closing adm1_list -->
            </li>
<%                      } else { 
							 String countryName = country.getName();
							 String flagIcon = "";
							 if (false && AntwebProps.isDevOrStageMode()) flagIcon = "<img src=" + AntwebProps.getDomainApp() + "/image/flag/16/" + country.getFlagIcon() + ">&nbsp";
							  %>
            <li><a href="<%= country.getThisPageTarget() %>"><%= flagIcon %><%= countryName %></a></li>
	<%
                        }
                    } // end country loop
%>
          </ul> <!-- closing country_list -->
        </li>
<%              } // end subregion loop   %>
      </ul> <!-- closing subregions_list -->
    </li>
<%          } else { 
                AntwebUtil.log("georegionMenu.jsp subregions is null:" + subregions + " for region:" + region);
            %>
  <li class="show_items"><%= titleHTML %></li>
<%          } // for subregions
        } // for regions
    } // regions != null
%> 
  </ul> <!-- closing region_list 	-->
</div>
