<%
    if (overview == null) {
      AntwebUtil.log("Overview not found in overviewHeading.jsp");
      return;
    }
    String overviewUrl = overview.getThisPageTarget();

    int maxSpeciesCount = 700;

//    if (LoginMgr.isCurator(request)) maxSpeciesCount = 2000;    
    if (LoginMgr.isAdmin(request)) maxSpeciesCount = 2000;    
    
    String icon = "";
    if (overview instanceof org.calacademy.antweb.geolocale.Country) {
      String iconFileName = "/image/flag/16/" + ((org.calacademy.antweb.geolocale.Country) overview).getFlagIcon();
      if (AntwebUtil.webFileFound(iconFileName)) {
        icon = "<img src=" + AntwebProps.getDomainApp() + iconFileName + ">&nbsp;";
      }
    }
%>
<br>
<h1><%= "<a href='" + AntwebProps.getDomainApp() + "/" + overview.getPluralTargetDo() + "'>" + overview.getHeading() + ":</a> " + icon + overview.getDisplayName() %> </h1>

	<div class="links">
		<ul>
			<li><a href="<%= overviewUrl %>">Overview</a></li>                
			<li><span class="proj_options" id="project_list">Taxa <img src="<%= AntwebProps.getDomainApp() %>/image/options.png">
				<div class="proj_taxon" id="proj_list">
					<ul>
						<li><a href="<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=subfamily&<%= overview.getParams() %>"><span class="numbers"></span> Subfamilies</a></li>
						<li><a href="<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=genus&<%= overview.getParams() %>"><span class="numbers"></span> Genera</a></li> 
						<li><a href="<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=species&<%= overview.getParams() %>"><span class="numbers"></span> Species</a></li> 
					</ul>
				</div>
			</span></li>
			<li><span class="proj_options" id="project_images">Images <img src="<%= AntwebProps.getDomainApp() %>/image/options.png">
				<div class="proj_taxon" id="proj_images">
					<ul>
						<li><a href="<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=subfamily&<%= overview.getParams() %>&images=true">Subfamilies</a></li>  
						<li><a href="<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=genus&<%= overview.getParams() %>&images=true">Genera</a></li> 

					  <% if (overview.getSpeciesCount() < maxSpeciesCount) { %>                            
						<li><a href="<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=species&<%= overview.getParams() %>&images=true">Species</a></li> 
					  <% } else {  %>
						<li><%= Formatter.commaFormat(overview.getSpeciesCount()) %> Species</li>   <!--span class="numbers"></span -->
					  <% } %>                          
					</ul>
				</div>
			</span></li>
		</ul>
	</div>
	<div id="antcat_view">
	</div>
	<div class="clear"></div>

<%
    //AntwebUtil.log("overviewHeading.jsp speciesImaged:" + overview.getNumSpeciesImaged() );
    //A.log("overviewHeading.jsp overview:" + overview);

    if (overview instanceof Museum || overview instanceof org.calacademy.antweb.geolocale.Country || overview instanceof Project
      && !( Project.ALLANTWEBANTS.equals(overview.getName()) || Project.WORLDANTS.equals(overview.getName()) )
       ) {

%>

    <div id="totals_and_tools_container">
        <div id="totals_and_tools">
            <div class="clear"></div>
            <div id="tools">
                <div class="tool_label" id="project_fg">
                    Field Guides
                    <div id="pfg_choices">
                        <ul>
                        <% 
                           String specifics = "";
                           if (overview.getShortDisplayName() != null && !"".equals(overview.getShortDisplayName())) specifics = " of " + overview.getShortDisplayName();
                        %>
                            <li><a href="<%= AntwebProps.getDomainApp() %>/fieldGuide.do?<%= overview.getParams() %>&rank=subfamily">Subfamilies<%= specifics %></a></li>
                            <li><a href="<%= AntwebProps.getDomainApp() %>/fieldGuide.do?<%= overview.getParams() %>&rank=genus">Genera<%= specifics %></a></li>
                         <% if (overview.getSpeciesCount() < maxSpeciesCount) { %> 
                            <li><a href="<%= AntwebProps.getDomainApp() %>/fieldGuide.do?<%= overview.getParams() %>&rank=species">Species<%= specifics %></a></li>
                         <% } %>
                        </ul>
                    </div>
                </div>
                <div class="tool_label"><span id="download_data">Download Data</span></div>
                <div class="clear"></div>
            </div>
            <div class="clear"></div>

            <div id="download_data_overlay" style="display:none;">
                <div id="download_overlay">
                    <div class="left"><h3>Download Species</h3></div><div class="right" id="close_download_data">X</div>
                    <div class="clear"></div>
                    <p>To download the species list, Right-click and save to your desktop.</p>
                    <ul>
                        <li>&middot; <a href="<%= AntwebProps.getDomainApp() %>/speciesListDownload.do?<%= overview.getParams() %>" target="new">Tab-delimited</a></li>
                    </ul>
                </div>
            </div>
        </div>
    </div>
 <% } %>