
<div id="museums" class="subnav">
  <ul> <!-- open museums -->

<%
    ArrayList<Museum> museums = MuseumMgr.getMuseums();
    if (museums != null) {
      for (Museum museum : museums) {
        if (museum.getIsActive()) {
          String a = "";
          a = "<a href='" + AntwebProps.getDomainApp() + "/museum.do?" + museum.getParams() + "'>" + museum.getDisplayName() + "</a>";

          out.println("<li>"+ a + "</li>");
        }
      }
    }
%>
  </ul> <!-- close museums -->
</div>
