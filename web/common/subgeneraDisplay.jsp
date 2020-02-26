<%@ page import="org.calacademy.antweb.util.*" %>

<% 
    String subgeneraTarget = HttpUtil.getTargetMinusParam(request, "subgenus"); 
    subgeneraTarget = HttpUtil.getTargetMinusParam(subgeneraTarget, "orderBy");    
 
    String displaySubgenera = request.getParameter("subgenus");
    if (displaySubgenera == null) displaySubgenera = "all";
    A.log("displaySubgenera:" + displaySubgenera);

    List<String> fullSetSubgenera = TaxonMgr.getSubgenera(taxon.getGenus());

    if (fullSetSubgenera != null && fullSetSubgenera.size() > 0) {
    
      List<String> subgenera = new ArrayList<String>();
    
      if (!"none".equals(displaySubgenera)) {
        // Remove if not in the childrenList.
        for (String subgenus : fullSetSubgenera) {
          boolean existsOnPage = false;
          for (Taxon child : childrenList) {
            if (subgenus.equals(child.getSubgenus())) {
              subgenera.add(subgenus);
              break;
            }
          }
        }
      }
      //A.log("suggeneraDisplay.jsp displaySubgenera:" + displaySubgenera + " size:" + subgenera.size() + " full:" + fullSetSubgenera.size());

      if (subgenera != null && (subgenera.size() > 0 || "none".equals(displaySubgenera))) {
          A.log("subgeneraDisplay.jsp subgenera:" + subgenera + " genus:" + taxon.getGenus());
        %>

        <div id="subgenera_toggle">
            <div class="left">Subgenera:</div>

            <div id="change_subgenera" class="has_options">
                <span id="which_subgenera"><span style="text-transform:capitalize;"><%= displaySubgenera %></span></span>
                <div id="subgenera_choices" class="options">
                    <ul>              
                        <li><a href="<%= subgeneraTarget + "&orderBy=subgenera" %>"><span style="text-transform:capitalize;">Sort by</span></a></li> 
                        <li><a href="<%= subgeneraTarget + "&subgenus=all" %>"><span style="text-transform:capitalize;">(all)</span></a></li>                        
                        <li><a href="<%= subgeneraTarget + "&subgenus=none" %>"><span style="text-transform:capitalize;">(none)</span></a></li>                        
                     <% for (String subgenus : subgenera) { %>
                            <li><a href="<%= subgeneraTarget + "&subgenus=" + subgenus %>"><span style="text-transform:capitalize;">(<%= subgenus %>)</span></a></li>                        
                     <% } %>
                    </ul>
                </div>
                <div class="clear"></div>
            </div>
        </div>
   <% } %>      
 <% } %>