	<div class="clear"></div>
	
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
      <li><a href="<%= domainApp %>/bioregionMapMgr.do">Bioregion Map Manager</a>      
      <li><a href="<%= domainApp %>/introducedMapMgr.do">Introduced Map Manager</a>
      <li><a href="<%= domainApp %>/query.do">Query Manager</a>
      <li><a href="<%= domainApp %>/logMgr.do?action=list">Log Manager</a>
    </ul>

    <h3 align=left>Administrator Functions</h2>
    <ul align=left>	
      <li><a href="<%= domainApp %>/statistics.do">Antweb Statistics</a>
      <li><a href="<%= domainApp %>/serverStatus.do">Server Status</a>
      <li><a href="<%= domainApp %>/sessionProps.do">Session Properties</a>
      <li><a href="<%= domainApp %>/dbStatus.do">Database Status</a>
      <li><a href="<%= AntwebProps.getSecureDomainApp() %>/list.do?action=usrAdm">User Login List</a>
      <li><a href="<%= AntwebProps.getSecureDomainApp() %>/query.do?action=curiousQuery&name=lastSpecimenUpload">Last Upload List</a>
      <li><a href="<%= domainApp %>/list.do?action=usrAdmLastLogin">User Last Login List</a>
<%
      String content = AntwebUtil.readFile("/data/antweb/web/siteWarning.txt");
      if (content == null || "null".equals(content)) content = "";
%>
      <li><a href="<%= domainApp %>/utilData.do?action=siteWarning&text=<%= org.calacademy.antweb.util.Html.escAttr(org.calacademy.antweb.util.Html.url(content)) %>">Site Warning</a>
         <br><verbatim>&lt;br&gt;&lt;font color=lightgreen&gt;&lt;/font&gt;</verbatim>
      <li><a href="<%= domainApp %>/util.do?action=isRestart">Restart Status</a>
      <% if (AntwebUtil.minsSince(SessionRequestFilter.getInitTime()) >= 10) { %>
      <br>&nbsp; &nbsp;<a href="<%= domainApp %>/utilData.do?action=setIsRestart&param=true">Restart server now</a> (<5 min)

     <% } %>

    </ul>

    <h3 align=left>Reports</H3>
    <ul align=left>
      <li><a href="<%= domainApp %>/query.do?action=curateAntcat">Curate AntCat Queries</a>
      <li><a href="<%= domainApp %>/logMgr.do?action=get&fileName=admin&ext=log">Admin Log</a>     
      <li><a href="<%= domainApp %>/query.do?action=homonyms">Homonym Report</a>
      <!-- li><a href="< %= domainApp % >/adminUpdates.do">Admin Updates</a -->
      <li><a href="<%= domainApp %>/query.do?action=events">Events</a>
      <li><a href="<%= domainApp %>/query.do?action=curiousQuery&name=adminAlerts">Admin Alerts</a>
      <li><a href="<%= domainApp %>/common/statusDisplayPage.jsp">Taxa Status Documentation</a>
      <li><a href="<%= domainApp %>/taxonomicPage.do?rank=species&project=worldants&isImaged=false">Unimaged Bolton Ants</a>    
      <li><a href="<%= domainApp %>/list.do?action=pictureLikes">Image Likes</a>
      <li><a href="<%= domainApp %>/recentDescEdits.do?action=recentDescEdits">Recent Description Edits</a>
      <li><a href="<%= domainApp %>/list.do?action=casentDAnomalies">Casent -Dxx Anomalies</a>
      <li><a href="<%= domainApp %>/utilData.do?action=taxaOutsideOfNativeBioregion">Taxa Outside of Native Bioregion</a>
    </ul>

    <h3 align=left>Technical Functions</h2>
     <ul align=left>
	  <li><a href="<%= domainApp %>/serverDebug.do">Server Debug</a>
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
	  <li>Process (automated)</li>
      <ul align=left>
  	    <li><a href="<%= domainApp %>/utilData.do?action=genRecentContent">Generate</a> Recent Content
    	    <!-- li><a href="< %= domainApp % >/upload.do?action=genRecentDescEdits">Generate</a> Recent Desc Edits -->
    	    <!-- li><a href="< %= domainApp % >/imageUploader.do?action=regenerate">Generate</a> Recent Images -->
	    <li><a href="<%= domainApp %>/upload.do?action=runStatistics">Generate</a> Statistics
      </ul>

	  <li>Crawl <a href="<%= domainApp %>/logMgr.do?action=get&fileName=compute&ext=log"> Log </a> File
	  <li><a href="<%= domainApp %>/logMgr.do?action=get&fileName=profiler&ext=jsp">Profiler log</a>
	  <li><a href="<%= domainApp %>/util.do?action=logQueryStats">Log</a> Query Stats
    </ul>

      <h3 align=left>Data Mgmt</h3>
      <ul align=left>
  	    <li><a href="<%= domainApp %>/utilData.do?action=regenerateAllAntweb"> Regenerate AllAntwebAnts </a>
      </ul>

<%   if (AntwebProps.isDevOrStageMode()) { %>
      <h3 align=left>Dev</h3>
      <ul align=left>
	    <li><a href="<%= domainApp %>/utilData.do?action=worldantsFetchAndReload"> Worldants Fetch and Reload</a>
      </ul>
<%   } %>

      <h3 align=left>Scheduler</h3>
      <ul align=left>
	    <li><a href="<%= domainApp %>/schedule.do?action=run"> Full Scheduler</a>
	    <li><a href="<%= domainApp %>/schedule.do?action=run&num=1"> Schedule 1 </a>
	    <li><a href="<%= domainApp %>/schedule.do?action=run&num=2"> Schedule 2 </a>
	    <li><a href="<%= domainApp %>/schedule.do?action=run&num=3"> Schedule 3 </a>
	    <li><a href="<%= domainApp %>/schedule.do?action=run&num=4"> Schedule 4 </a>
	    <li><a href="<%= domainApp %>/schedule.do?action=run&num=5"> Schedule 5 </a>
      </ul>
    </ul>

<% } %>    