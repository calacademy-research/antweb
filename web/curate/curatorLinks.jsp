	<div class="clear"></div>
	
    <h3 align=left>Curator Links</h2>
    <ul align=left>
	  <li><a href="<%= domainApp %>/description.do?taxonName=">Access</a> direct by taxon_name
	  <li><a href="<%= domainApp %>/description.do?antcatId=">Access</a> direct by Antcat ID
	  <li><a href="<%= domainApp %>/specimen.do?code=">Access</a> direct by specimen code	  
	  <li><a href="<%= domainApp %>/geolocale.do?id=">Access</a> direct by Geolocale Id
	  <li><a href="<%= domainApp %>/query.do?action=curiousQuery&name=">Access</a> Curious Query	  
    </ul>

<% if (accessLogin.isAdmin()) { %>  
    <h3 align=left>Manager Tools</h2>
    <ul align=left>
      <li>Display <a href="<%= domainApp %>/geolocaleMgr.do?name=displayGeoregions">Georegions</a> or
         <a href="<%= domainApp %>/geolocaleMgr.do?name=displayBioregions">Bioregions</a>.     
      <li><a href="<%= domainApp %>/viewLogins.do">Login Manager</a>
      <li><a href="<%= domainApp %>/viewGroups.do">Group Manager</a>
      <li><a href="<%= domainApp %>/manageMuseum.do">Museum Manager</a>
      <li><a href="<%= domainApp %>/geolocaleMgr.do">Geolocale Manager</a>    
      <li><a href="<%= domainApp %>/projectMgr.do">Project Manager</a>
      <li><a href="<%= domainApp %>/artists.do">Artist Manager</a>
      <li><a href="<%= domainApp %>/adminAlert.do">Admin Alert Manager</a>
      <li><a href="<%= domainApp %>/ancPageMgr.do">Ancillary Page Manager</a> 
      <li><a href="<%= domainApp %>/orphanMgr.do">Orphan Manager</a>
      <li><a href="<%= domainApp %>/bioregionMapMgr.do">Bioregion Map Manager</a>      
      <li><a href="<%= domainApp %>/introducedMapMgr.do">Introduced Map Manager</a>
      <li><a href="<%= domainApp %>/typeStatus.do">Type Status Manager</a>
      <li><a href="<%= domainApp %>/query.do">Query Manager</a>
      <li><a href="<%= domainApp %>/logMgr.do">Log Manager</a>
    </ul>

    <h3 align=left>Administrator Functions</h2>
    <ul align=left>	
      <li><a href="<%= domainApp %>/statistics.do">Antweb Statistics</a>
      <li><a href="<%= domainApp %>/serverStatus.do">Server Status</a>
      <li><a href="<%= domainApp %>/userAgents.do">User Agents</a>
      <li><a href="<%= domainApp %>/dbStatus.do">Database Status</a>
      <!-- li><a href="< %= domainApp % >/util.do?action=unlockImageUpload">Unlock</a> Image Upload -->
      <li><a href="<%= AntwebProps.getSecureDomainApp() %>/list.do?action=usrAdm">User Login List</a>
      <li><a href="<%= AntwebProps.getSecureDomainApp() %>/query.do?action=curiousQuery&name=lastSpecimenUpload">Last Upload List</a>
      <li><a href="<%= domainApp %>/list.do?action=usrAdmLastLogin">User Last Login List</a>
<%
      String content = AntwebUtil.readFile("/data/antweb/web/siteWarning.jsp");
      if (content != null) A.log("Current: <verbatim>" + HttpUtil.verbatimify(content) + "</verbatim>");
%>      

      <li><a href="<%= domainApp %>/utilData.do?action=siteWarning&text=<%= content %>">Site Warning</a> 
         <br><verbatim>&lt;br&gt;&lt;font color=lightgreen&gt;&lt;/font&gt;</verbatim>
      <li><a href="<%= domainApp %>/utilData.do?action=deleteTaxonProp&taxonName=&prop=">Delete Taxon Property</a> 
    </ul>

    <h3 align=left>Reports</H3>
    <ul align=left>
      <li><a href="<%= domainApp %>/query.do?action=curateAntcat">Curate AntCat Queries</a>
      <li><a href="<%= domainApp %>/web/log/admin.log">Admin Log</a>      
      <li><a href="<%= domainApp %>/museum.do">Museum Overview</a>    
      <li><a href="<%= domainApp %>/util.do?action=homonyms">Homonym Report</a>
      <li><a href="<%= domainApp %>/adminUpdates.do">Admin Updates</a>
      <li><a href="<%= domainApp %>/util.do?action=events">Events</a>
      <li><a href="<%= domainApp %>/util.do?action=adminAlerts">Admin Alerts</a>      
      <li><a href="<%= domainApp %>/common/statusDisplayPage.jsp">Taxa Status Documentation</a>
      <li><a href="<%= domainApp %>/taxonomicPage.do?rank=species&project=worldants&isImaged=false">Unimaged Bolton Ants</a>    
      <li><a href="<%= domainApp %>/web/upload/">Upload Files</a>      
      <li><a href="<%= domainApp %>/list.do?action=countries">Countries List</a>
      <li><a href="<%= domainApp %>/list.do?action=bioregions">Bioregions List</a>
      <li><a href="<%= domainApp %>/list.do?action=pictureLikes">Image Likes</a>
      <li><a href="<%= domainApp %>/list.do?action=likes">Image Likes Display</a>
      <li><a href="<%= domainApp %>/recentDescEdits.do?action=recentDescEdits">Recent Description Edits</a>
      <li><a href="<%= domainApp %>/list.do?action=casentDAnamalies">Casent -Dxx Anomalies</a>
      <li><a href="<%= domainApp %>/utilData.do?action=taxaOutsideOfNativeBioregion">Taxa Outside of Native Bioregion</a>
    </ul>

    <h3 align=left>Technical Functions</h2>
     <ul align=left>
      <li><a href="<%= domainApp %>/util.do?action=reloadAntwebMgr">Reload</a> All Managers  
        <br>&nbsp&nbsp;Manager:<a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=group">g</a> 
        <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=allAntweb">a</a> 
        <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=login">l</a> 
        <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=project">p</a> 
        <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=bioregion">b</a> 
        <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=museum">m</a> 
        <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=geolocale">g</a> 
        <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=taxon">t</a> 
        <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=taxonProp">tp</a> 
        <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=upload">u</a> 
        <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=artist">a</a> 
        <a href="<%= domainApp %>/util.do?action=reloadAntwebMgr&name=adminAlert">aa</a> 
     
	  <li><a href="<%= domainApp %>/pageTracker.do">Page Tracker</a>
	  <li><a href="<%= domainApp %>/cache.do?action=display">Cache Manager</a>
	  <li>Process (automated)</li>
      <ul align=left>
  	    <li><a href="<%= domainApp %>/utilData.do?action=genRecentContent">Generate</a> Recent Content
    	    <!-- li><a href="< %= domainApp % >/upload.do?action=genRecentDescEdits">Generate</a> Recent Desc Edits -->
    	    <!-- li><a href="< %= domainApp % >/imageUploader.do?action=regenerate">Generate</a> Recent Images -->
	    <li><a href="<%= domainApp %>/upload.do?action=runStatistics">Generate</a> Statistics
	    <li><a href="<%= domainApp %>/upload.do?action=materializeImages">Run</a> Materialize Images
	    <li><a href="<%= domainApp %>/upload.do?action=updateAdvancedSearch">Update</a> Advanced Search
	    <li><a href="<%= domainApp %>/upload.do?action=updateSpecimenStatus">Update</a> Specimen Status
      </ul>

	  <li>Crawls (not automated)</li>
      <ul align=left>
  	    <!-- li><a href="< %= domainApp % >/upload.do?action=reloadSpeciesLists">Reload</a> All Species Lists -->
		<li><a href="<%= domainApp %>/schedule.do?action=populateAll"> Populate </a> All (1.5hrs) 
		<li>Crawl <a href="<%= domainApp %>/web/log/compute.log"> Log </a> File

<% if (LoginMgr.isDeveloper(request)) { %>
		<li><a href="<%= domainApp %>/utilData.do?action=testProjTaxon">testProjTaxon</a>
		<li><a href="<%= domainApp %>/web/log/profiler.jsp">Profiler log</a>
<% } %>
      </ul>
	  <li><a href="<%= domainApp %>/util.do?action=testMessage"> Test </a>Message
	  <li><a href="<%= domainApp %>/util.do?action=unboldMessage"> Test </a>Unbold Message
	  <li><a href="<%= domainApp %>/util.do?action=bareMessage"> Bare </a>Message
	  <li><a href="<%= domainApp %>/adm1.do?%20(Terr.%20Amazonas)"> Session Request Error </a>Message
	  <li><a href="<%= domainApp %>/uptime.do"> Uptime </a>Message
	  <li><a href="<%= domainApp %>/util.do?action=descEdit&field=notes">Notes</a>
	  <li><a href="<%= domainApp %>/util.do?action=logQueryStats">Log</a> Query Stats
	  <!-- li><a href="< %= domainApp % >/util.do?action=moveSpeciesListPages">Move</a> Species List Pages	  -->
      <li><a href="<%= domainApp %>/documentation/links.jsp">Links</a>      
      <li><a href="<%= domainApp %>/utilData.do?action=updateAdm1FromParentData">Update</a> Adm1 from parent data
    </ul>

<%   if (LoginMgr.isDeveloper(accessLogin)) { %>
      <h3 align=left>Data Mgmt</h3>
      <ul align=left>
        <!-- li><a href="< %= domainApp % >/utilData.do?action=plaziData">Get Plazi Data</a -->
        <!-- li><a href="< %= domainApp % >/getPlazi.do?test">Plazi Test</a -->
        <!-- li><a href="< %= domainApp % >/utilData.do?action=populateObjectEdit">Populate</a> Object Edit -->
        <li><a href="<%= domainApp %>/utilData.do?action=countryData">Fetch</a> Country Data <!-- Fetch data from antwiki.org -->
        <li><a href="<%= domainApp %>/utilData.do?action=finishCountryUpload">Finish</a> Country Upload
        <li><a href="<%= domainApp %>/utilData.do?action=calcEndemic">Calc</a> Endemism
        <li><a href="<%= domainApp %>/utilData.do?action=calcIntroduced">Calc</a> Introduced

  	    <li><a href="<%= domainApp %>/utilData.do?action=regenerateAllAntweb"> Regenerate AllAntwebAnts </a>
	    <li><a href="<%= domainApp %>/utilData.do?action=crawlForType">Crawl </a> For Type
  	    <li><a href="<%= domainApp %>/utilData.do?action=allCountCrawls"> Crawl</a> for Children and Image Counts</a>
		<li><a href="<%= domainApp %>/utilData.do?action=fetchGoogleApisData">Fetch</a> Google Apis Data for Adm1  	 

        <li>Populate Overviews
          <ul>
            <li><a href="<%= domainApp %>/utilData.do?action=populateMuseum&code="> Populate </a> One Museum
	        <li><a href="<%= domainApp %>/utilData.do?action=museumTaxonCountCrawl&code=AFRC">Crawl</a> Museum Counts

            <li><a href="<%= domainApp %>/utilData.do?action=populateBioregion&name=Indomalaya"> Populate </a> Indomalaya Bioregion

            <li><a href="<%= domainApp %>/utilData.do?action=populateOneGeolocaleTaxon&num=392"> Populate </a> One Geolocale (CA)
            <li><a href="<%= domainApp %>/utilData.do?action=geolocaleTaxonCountCrawl&num=392"> Crawl</a> One Geolocale (CA)
            <li><a href="<%= domainApp %>/utilData.do?action=populateGeolocaleCountryTaxon"> Populate </a> Countries


            <li><a href="<%= domainApp %>/utilData.do?action=populateOneProjTaxon"> Populate </a> One Project

          </ul>

          <!-- li><a href="< %= domainApp % >/utilData.do?action=fetchGeonamesData">Fetch</a> Geonames Data -->
          <!-- li>&nbsp;&nbsp;<a href="< %= domainApp % >/web/log/geonames.log">GeonamesAdm1</a> log file -->

          <li><a href="<%= domainApp %>/utilData.do?action=fetchGeonamesCountryData">Fetch</a> Get Geonames Country Data
          <li><a href="<%= domainApp %>/utilData.do?action=fetchGeonamesAdm1Data">Fetch</a> Get Geonames Adm1 Data
          <li>&nbsp;&nbsp;<a href="<%= domainApp %>/web/log/geonames.log">Geonames</a> log file
          <li>&nbsp;&nbsp;<a href="<%= domainApp %>/web/log/DataPlaceCase.txt">Data Place</a> Case file          
          <li><a href="<%= domainApp %>/utilData.do?action=fetchFlickrAdm1Data">Fetch</a> Flickr Adm1 Data
          <li>&nbsp;&nbsp;<a href="<%= domainApp %>/web/log/flickrAdm1.html">FlickrAdm1</a> log file
          <li><a href="<%= domainApp %>/utilData.do?action=fullGeodataFetch">Full</a> Geodata Fetch
          <li><a href="<%= domainApp %>/utilData.do?action=geodataTestFetch">Test</a> Geodata Fetch
          <li><a href="<%= domainApp %>/utilData.do?action=fetchGeonetData">Geonet</a> Fetch
          <!-- li><a href="< %= domainApp % >/utilData.do?action=moveDataToTaxonProps">Move</a> Favorites to Taxon Prop -->
          <!-- li><a href="< %= domainApp % >/utilData.do?action=populateIntroduced">Populate</a> Introduced -->
	      <li><a href="<%= domainApp %>/utilData.do?action=updateTaxonSetTaxonNames"> Update</a> Taxon Set TaxonNames 
	      <li><a href="<%= domainApp %>/utilData.do?action=genObjectMaps"> Generate</a> Geolocale Maps
	      <li><a href="<%= domainApp %>/utilData.do?action=updateGroupCounts"> Update</a> Group Counts
	      <li><a href="<%= domainApp %>/utilData.do?action=genGroupObjectMaps"> Generate</a> Group maps.
	       
      </ul>
<%   } %> 

<%   if (AntwebProps.isDevOrStageMode()) { %>
      <h3 align=left>Dev or Stage</h3>
      <ul align=left>
	  <li><a href="<%= domainApp %>/upload.do?action=specimenTest">Run</a> Specimen Test
	  <li><a href="<%= domainApp %>/upload.do?action=allSpecimenFiles">Load</a> All Specimen Files
	  <li><a href="<%= domainApp %>/upload.do?action=speciesTest">Run</a> Species Test
      <li>Taxon <a href="<%= domainApp %>/utilData.do?action=taxonFinish"> finish </a>
      <li><a href="<%= domainApp %>/util.do?action=archiveLogs">Archive</a> logs
      </ul>
<%   } %> 
<%   if (AntwebProps.isDevOrStageMode()) { %>
      <h3 align=left>Dev</h3>
      <ul align=left>
	    <li><a href="<%= domainApp %>/utilData.do?action=reCrawl"> ReCrawl (custom)</a>
	    <li><a href="<%= domainApp %>/utilData.do?action=countCrawls"> Count </a> Crawls
	    <li><a href="<%= domainApp %>/utilData.do?action=parentCrawl"> Crawl </a> for Parents
	    <li><a href="<%= domainApp %>/utilData.do?action=imageCountCrawl"> Crawl </a> Image Counts
	    <li><a href="<%= domainApp %>/utilData.do?action=projTaxonChildCountCrawl"> Crawl </a> Project Taxon Child Counts
	    <li><a href="<%= domainApp %>/utilData.do?action=projTaxonImageCountCrawl"> Crawl </a> Project Taxon Image Counts
	    <li><a href="<%= domainApp %>/utilData.do?action=worldantsFetchAndReload"> Worldants Fetch and Reload</a>
      </ul>
<%   } %> 

<%   if (LoginMgr.isMark(request)) { %>
      <h3 align=left>Scheduler</h3>
      <ul align=left>
	    <li><a href="<%= domainApp %>/schedule.do?action=run"> Full Scheduler</a>
	    <li><a href="<%= domainApp %>/schedule.do?action=run&num=1"> Schedule 1 </a>
	    <li><a href="<%= domainApp %>/schedule.do?action=run&num=2"> Schedule 2 </a>
	    <li><a href="<%= domainApp %>/schedule.do?action=run&num=3"> Schedule 3 </a>
	    <li><a href="<%= domainApp %>/schedule.do?action=run&num=4"> Schedule 4 </a>
	    <li><a href="<%= domainApp %>/schedule.do?action=run&num=5"> Schedule 5 </a>
      </ul>
<%   } %> 

<%
   if (false) {
     if (AntwebProps.isDevOrStageMode()) { %>
      <h3 align=left>Retired</h3>
      <ul align=left>
	  <li><a href="<%= domainApp %>/utilData.do?action=updateFormicidaeProjects"> Update </a>Formicidae Project
      <li><a href="<%= domainApp %>/moveToValid.do">Move To Valid</a>      
	  <li><a href="<%= domainApp %>/utilData.do?action=calculateTaxonIsValidNames"> Calculate </a>Taxon is Valid Names
	  <!-- li><a href="< %= domainApp % >/upload.do?action=genAll">Generate All</a> Species Lists -->
      </ul>
<%   } %> 

<%   if (LoginMgr.isDeveloper(accessLogin)) { %>
      <h3 align=left>Release 7.1</h3>
      <ul align=left>
	  <li><a href="<%= domainApp %>/utilData.do?action=generateGeolocaleTaxaFromSpecimens"> Generate </a>Geolocale Taxa
	  <!-- 
	    Finished Generate Geolocale Taxa From Specimens retVal:deleteTotal:115645 insertTotal:624532066 in 1.87 mins  
	  -->
      <li><a href="<%= domainApp %>/utilData.do?action=worldantsReload">Worldants</a> Reload</li>
      <li>Verify Literature upload.</li>
      <!-- 
        execute() Completion of the Upload Process.  Group:CAS action:upload:AntWiki_Regional_Taxon_List 2017-07-16_v2.txt in 0.78 mins
      -->
      <li><a href="<%= domainApp %>/utilData.do?action=fixGeolocaleTaxonParentage">fixGeolocaleTaxonParentage</a>
      <li><a href="<%= domainApp %>/utilData.do?action=testGeolocaleTaxonParentage">testGeolocaleTaxonParentage</a>
      <!-- 
        execute() 6003 geolocale_taxon Taxon parentage records missing, 9661 inserted. 770 geolocale_taxon Taxon parentage records, 425 inserted.  2.22 mins
      -->
      <li>Check specimen upload: <a href='https://www.antweb.org/web/log/upload/20170731-14:44:39-12SpecimenList.html'>report</a></li>
      <li><a href="<%= domainApp %>/query.do?action=curiousQuery&name=badGeolocaleTaxonParentTaxonName"> Bad GeolocaleTaxon parents</a></li>
      <li><a href="<%= domainApp %>/query.do?action=curiousQuery&name=badParentTaxonName"> Bad Parent TaxonName</a></li>
      <li><a href="<%= domainApp %>/schedule.do?action=run"> New Scheduler functionalty</a></li>
      <li><a href="<%= domainApp %>/utilData.do?action=regenerateAllAntweb"> Regenerate All Antweb</a></li>
      </ul>
<%   }
   }
%>
    </ul>

<% } %>    