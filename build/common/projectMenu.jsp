
<div id="projects" class="subnav">
  <ul> <!-- open subglobalProjects ("projectants") -->
<%
    ArrayList<Project> subglobalProjects = ProjectMgr.getSubglobalProjects();

    //if (AntwebProps.isDevMode()) AntwebUtil.log("projectMenu.jsp subglobalProjects:" + subglobalProjects);

    if (subglobalProjects != null) {
        for (Project subglobalProject : subglobalProjects) {                
            String titleHTML = "";

            // This one would perform much faster and be better against bots.
            //titleHTML = "<a href='" + AntwebProps.getDomainApp() + "/project.do?name=" + subglobalProject.getProjectName() + "'>" + globalProject.getName() + "</a>"; 
            titleHTML = "<a href='" + subglobalProject.getThisPageTarget() + "'>" + subglobalProject.getTitle() + "</a>"; 
%>
    <li><%= titleHTML %></li>
<%
        }
    }
%>
  </ul> <!-- close subglobalProjects -->
</div>
