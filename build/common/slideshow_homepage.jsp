<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.util.*" %>

<%
    ArrayList<Taxon> subfamilies = TaxonMgr.getSubfamilies();
    if (subfamilies == null) return;
%>

<a class="prev browse left">&#171;</a>
<div class="scrollable" id="autoscroll">
<div class="items">
<div class="things">

<%    
    //A.log("slideshow_homepage.jsp subfamilies:" + subfamilies);
    ArrayList<String> added = new ArrayList<String>();
    ArrayList<String> notAdded = new ArrayList<String>();

    int i = 0;
    for (Taxon subfamily : subfamilies) {
      String subfamilyName = subfamily.getName();
      boolean include = false;
      String imgFile = AntwebProps.getDomainApp() + "/image/" + subfamilyName + ".jpg";
      //A.log("slideshow_homepage.jsp subfamily:" + subfamily + " file:" + imgFile);

      include = subfamily.isValid();

      if (subfamilyName.contains("incertae_sedis")) include = true;
         
      if (include) {
        added.add(subfamilyName);
        ++i;
        
          %>
  <div class="slide small" style="background-image: url(<%= imgFile %>);" id="<%= i %>" onclick="window.location.href='<%= AntwebProps.getDomainApp() %>/images.do?subfamily=<%= subfamilyName %>&rank=subfamily&project=allantwebants';">
      <div class="hover small"></div>
      <div class="top_gradient small"></div>
      <div class="name small"><a class="" href="<%= AntwebProps.getDomainApp() %>/images.do?subfamily=<%= subfamilyName %>&rank=subfamily&project=allantwebants"><%= Formatter.initCap(subfamilyName) %></a></div>
      <div class="clear"></div>
  </div>
          <%
        
        if (i % 7 == 0) { %>
          </div>
          <div class="things">
          <%
        }
      } else {
        notAdded.add(subfamilyName);
        //A.log("slideshow_homepage.jsp not included subfamily:" + subfamilyName);
      }
    }

    //A.log("added:" + added);
    //A.log("notAdded:" + notAdded);
%>

</div>
<div class="things">

</div>
</div>
</div>
<a class="next browse right">&#187;</a>
<div class="clear"></div>
