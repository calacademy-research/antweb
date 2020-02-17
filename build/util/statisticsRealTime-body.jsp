<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>

<%
    Boolean isLink = (Boolean) session.getAttribute("isLink");

    String target = HttpUtil.getTarget(request);
    //A.log("statisticsRealTime-body.jsp target:" + target);
    String firstHeading = "Project Name";
    String title = "Project Statistics";
    String param = "project=";
    if (target.contains("byBioregion")) {
      param = "bioregionName=";
      firstHeading = "Bioregion Name";
      title = "Bioregion Statistics";
    }
    if (target.contains("byMuseum")) {
      param = "museumCode=";
      firstHeading = "Museum Code";
      title = "Museum Statistics";
    }
    if (target.contains("byGeolocale")) {
      param = "geolocaleName=";
      firstHeading = "Geolocale";
      title = "Geolocale Statistics";
    }
%>

<H2><%= title %></H2>
<table border=1><tr>
<th> <%= firstHeading %> </th>
<th>Extinct all subf.</th>  <!-- 2 -->
<th>Extant all subf.</th>
<th>V. subf.</th>
<th>All subf.</th>
<th>Extinct all gen.</th>
<th>Extant all gen.</th>
<th>V. gen.</th> <!-- 8 -->
<th>All gen.</th>
<th>Extinct all sp.</th>
<th>Extant all sp.</th>
<th>V. sp.</th>
<th>Imaged v. sp.</th>
<th>All sp.</th> <!-- 12 -->
<th>Total Taxa</th></tr>            

<%
    ArrayList<ArrayList<String>> stats = (ArrayList) session.getAttribute("statistics"); 
    int lineCount = 0;
    for (ArrayList<String> statsLine : stats) {
      ++lineCount;
      %> <tr> <%
      int statCount = 0;
      String paramValue = "";
      for (String stat : statsLine) {
        ++statCount;    

        if (isLink) {
          if (statCount == 1) { 
            paramValue = stat; %> 
            <td><%= stat %></td> 
       <% } 
          if (statCount == 2) { %> 
            <td><a href="<%= AntwebProps.getDomainApp()%>/taxaList.do?extant=0&<%= param %><%= paramValue %>&rank=subfamily"><%= stat %></a></td>
       <% } 
          if (statCount == 3) { %> 
            <td><a href="<%= AntwebProps.getDomainApp()%>/taxaList.do?extant=1&<%= param %><%= paramValue %>&rank=subfamily"><%= stat %></a></td>
       <% }
          if (statCount == 4) { %> 
            <td><a href="<%= AntwebProps.getDomainApp()%>/taxaList.do?status=valid&<%= param %><%= paramValue %>&rank=subfamily"><%= stat %></a></td> 
       <% }
          if (statCount == 5) { %> 
            <td><a href="<%= AntwebProps.getDomainApp()%>/taxaList.do?<%= param %><%= paramValue %>&rank=subfamily"><%= stat %></a></td>
       <% }
          if (statCount == 6) { %> 
            <td><a href="<%= AntwebProps.getDomainApp()%>/taxaList.do?extant=0&<%= param %><%= paramValue %>&rank=genus"><%= stat %></a></td> 
       <% }
          if (statCount == 7) { %> 
            <td><a href="<%= AntwebProps.getDomainApp()%>/taxaList.do?extant=1&<%= param %><%= paramValue %>&rank=genus"><%= stat %></a></td> 
       <% }
          if (statCount == 8) { %> 
            <td><a href="<%= AntwebProps.getDomainApp()%>/taxaList.do?status=valid&<%= param %><%= paramValue %>&rank=genus"><%= stat %></a></td> 
       <% }
          if (statCount == 9) { %> 
            <td><a href="<%= AntwebProps.getDomainApp()%>/taxaList.do?<%= param %><%= paramValue %>&rank=genus"><%= stat %></a></td>  
       <% }
          if (statCount == 10) { %> 
            <td><a href="<%= AntwebProps.getDomainApp()%>/taxaList.do?extant=0&<%= param %><%= paramValue %>&rank=species"><%= stat %></a></td> 
       <% }
          if (statCount == 11) { %> 
            <td><a href="<%= AntwebProps.getDomainApp()%>/taxaList.do?extant=1&<%= param %><%= paramValue %>&rank=species"><%= stat %></a></td> 
       <% }
          if (statCount == 12) { %> 
            <td><a href="<%= AntwebProps.getDomainApp()%>/taxaList.do?status=valid&<%= param %><%= paramValue %>&rank=species"><%= stat %></a></td> 
       <% }
          if (statCount == 13) { %> 
            <td><a href="<%= AntwebProps.getDomainApp()%>/taxaList.do?imaged=1&status=valid&<%= param %><%= paramValue %>&rank=species"><%= stat %></a></td>        
       <% }
          if (statCount == 14) { %> 
            <td><a href="<%= AntwebProps.getDomainApp()%>/taxaList.do?<%= param %><%= paramValue %>&rank=species"><%= stat %></a></td>  
       <% }
          if (statCount == 15) { %> 
            <td><a href="<%= AntwebProps.getDomainApp()%>/taxaList.do?<%= param %><%= paramValue %>"><%= stat %></a></td>        
       <% }
        } else {
          if (statCount == 1) { 
            paramValue = stat; %> 
            <td><%= stat %></td> 
       <% } 
          if ((statCount >1) && (statCount <= 14)) { %> 
            <td><%= stat %></td>
       <% }
        }

      }
      %> </tr> <%
    }
%>

</table>