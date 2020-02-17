<div id="global" class="subnav">
  <ul> <!-- open global -->
<%
    ArrayList<Project> globalProjects = ProjectMgr.getGlobalProjects();

    if (globalProjects != null) {
        //AntwebUtil.log("bioregionMenu.jsp regions:" + bioregions);
        for (Project globalProject : globalProjects) {        
            //if (AntwebProps.isDevMode()) AntwebUtil.log("georegionMenu.jsp region:" + region.fullReport());
        
            String titleHTML = "";
            
            // This one would perform much faster and be better against bots.
            titleHTML = "<a href='" + AntwebProps.getDomainApp() + "/project.do?name=" + globalProject.getName() + "'>" + globalProject.getDisplayName() + "</a>"; 
            //titleHTML = "<a href='" + AntwebProps.getDomainApp() + "/taxonomicPage.do?rank=genus&project=" + globalProject.getName() + "&images=true'>" + globalProject.getTitle() + "</a>"; 
%>
    <li><%= titleHTML %></li>
<%
        }
    }
%>
  </ul> <!-- close global -->
</div>




