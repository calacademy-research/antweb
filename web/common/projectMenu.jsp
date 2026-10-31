<%-- Static include of common/menuBar.jsp: the Projects menu of the header, the live projects of scope PROJECT other
     than allantwebants and worldants (ProjectMgr.getSubglobalProjects, by project name), each its title linked to
     project.do. Docs: docs/flows/places.md. --%>
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
            titleHTML = "<a href='" + org.calacademy.antweb.util.Html.esc(subglobalProject.getThisPageTarget()) + "'>" + org.calacademy.antweb.util.Html.esc(subglobalProject.getTitle()) + "</a>"; 
%>
    <li><%= titleHTML %></li>
<%
        }
    }
%>
  </ul> <!-- close subglobalProjects -->
</div>
