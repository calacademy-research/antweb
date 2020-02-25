<%@ page import="org.calacademy.antweb.util.*" %>

<% 
  String subgeneraTarget = HttpUtil.getTargetMinusParam(request, "subgenera"); 
 
  String displaySubgenera = request.getParameter("subgenera");
  A.log("displaySubgenera:" + displaySubgenera);
  
  Set<String> subgenera = new TreeSet<String>();
  for (Taxon t : childrenList) {
    if (t.getSubgenus() != null) subgenera.add(t.getSubgenus());
  }
  
%>
 
<div id="subgenera_toggle">
    <div class="left">Subgenera:</div>

    <div id="change_subgenera" class="has_options">
        <span id="which_subgenera"><span style="text-transform:capitalize;"><%= displaySubgenera %></span></span>
        <div id="subgenera_choices" class="options">
            <ul>
                <li><a href="<%= subgeneraTarget + "&subgenus=none" %>"><span style="text-transform:capitalize;">(none)</span></a></li>                        
             <% for (String subgenus : subgenera) { %>
                    <li><a href="<%= subgeneraTarget + "&subgenus=" + subgenus %>"><span style="text-transform:capitalize;">(<%= subgenus %>)</span></a></li>                        
             <% } %>
            </ul>
        </div>
        <div class="clear"></div>
    </div>
</div>
