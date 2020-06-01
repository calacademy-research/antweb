<%@ page import="org.calacademy.antweb.util.*" %>

<% 
    String subgeneraTarget = HttpUtil.getTargetMinusParam(request, "subgenus"); 
    subgeneraTarget = HttpUtil.getTargetMinusParam(subgeneraTarget, "orderBy");    
 
    String displaySubgenera = request.getParameter("subgenus");
    if (displaySubgenera != null) {
      if ("none".equals(displaySubgenera)) {
        displaySubgenera = "Unnassigned";
      } else if (!"all".equals(displaySubgenera)) {
        displaySubgenera = "(" + displaySubgenera + ")";
      }
    } else {
      String sDOrderBy = request.getParameter("orderBy");
      if (sDOrderBy != null) displaySubgenera = "by " + sDOrderBy;
      if (displaySubgenera == null) displaySubgenera = "All";
    }
    //displaySubgenera = "all";
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

      %>

        <div id="subgenera_toggle">
            <div class="left">Subgenera:</div>

            <div id="change_subgenera" class="has_options">
                <span id="which_subgenera"><span style="text-transform:capitalize;"><%= displaySubgenera %></span></span>
                <div id="subgenera_choices" class="options">
                    <ul>              
                        <li><span style="text-transform:capitalize;">Sort</span></li>
                        <li><a href="<%= subgeneraTarget + "&orderBy=subgenera" %>"><span style="text-transform:capitalize;">&nbsp;&nbsp;By Subgenera</span></a></li>
                        <li><a href="<%= subgeneraTarget + "&orderBy=species" %>"><span style="text-transform:capitalize;">&nbsp;&nbsp;By Species</span></a></li>
                        <li><span style="text-transform:capitalize;">Select</span></li>
                        <li><a href="<%= subgeneraTarget + "&subgenus=all" %>"><span style="text-transform:capitalize;">&nbsp;&nbsp;All</span></a></li>
<%
      if (subgenera != null && (subgenera.size() > 0 || "none".equals(displaySubgenera))) {
          A.log("subgeneraDisplay.jsp subgenera:" + subgenera + " genus:" + taxon.getGenus());
%>
                        <li><a href="<%= subgeneraTarget + "&subgenus=none" %>"><span style="text-transform:capitalize;">&nbsp;&nbsp;Unassigned</span></a></li>
                     <% for (String subgenus : subgenera) { %>
                            <li><a href="<%= subgeneraTarget + "&subgenus=" + subgenus %>"><span style="text-transform:capitalize;">&nbsp;&nbsp;(<%= subgenus %>)</span></a></li>
                     <% } %>
 <% } %>

                    </ul>
                </div>
                <div class="clear"></div>
            </div>
        </div>


 <% } %>