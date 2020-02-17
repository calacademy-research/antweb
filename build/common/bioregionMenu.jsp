
<div id="bioregions" class="subnav">
  <ul>
<%
    ArrayList<Bioregion> bioregions = BioregionMgr.getBioregions();

    if (bioregions != null) {
        //AntwebUtil.log("bioregionMenu.jsp regions:" + bioregions);
        for (Bioregion bioregion : bioregions) {        
            //if (AntwebProps.isDevMode()) AntwebUtil.log("georegionMenu.jsp region:" + region.fullReport());
        
            String titleHTML = "";
            titleHTML = "<a href='" + AntwebProps.getDomainApp() + "/bioregion.do?name=" + bioregion.getName() + "'>" + bioregion.getName() + "</a>";  // no href  
%>
    <li><%= titleHTML %></li>
<%
        }
    } else {
      //A.log("bioregionMenu.jsp bioregions is null");
    }
%>

  </ul>
</div>
