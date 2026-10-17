<div id="regions" class="subnav">
  <ul id="accordion">
<%
if (true) { // to create nesting of variables...

    ArrayList<Bioregion> bioregions = BioregionMgr.getBioregions();
    String titleHTML = "";
    if (bioregions != null) {
      for (Bioregion bioregion : bioregions) {
        titleHTML = "<a>" + org.calacademy.antweb.util.Html.esc(bioregion.getTitle()) + "</a>";
                
        ArrayList<Project> projects = bioregion.getProjects();
                
        if (!projects.isEmpty()) {
%>
    <li class="show_items"><%=titleHTML %>
      <ul class="region_items">
<%      } else {   %>
        <li class="show_items"><%=titleHTML %></li>
<%      }
        int i = 1;
        for (Project subProject : projects) { %>      
        <li><a href="<%=domainApp%>/taxonomicPage.do?rank=genus&project=<%= org.calacademy.antweb.util.Html.esc(org.calacademy.antweb.util.Html.url(subProject.getUseName())) %>&images=true"><%= org.calacademy.antweb.util.Html.esc(subProject.getTitle()) %></a></li>  
<%      }
        if (!projects.isEmpty()) { %>
      </ul> <!-- close region_items -->
    </li>
<%      }
      }
    } else {
      //if (AntwebProps.isDevMode()) AntwebUtil.log("regionMenu.jsp region is null");
    }
}
%>
  </ul>  <!-- close region_list -->
</div>
