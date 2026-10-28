<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.util.*" %>

<%
    // The home page's subfamily slideshow. The slides are built first and written in one piece: an error while
    // building them (a subfamily without a name, the subfamily list replaced by an upload's TaxonMgr reload) leaves
    // the slideshow empty and is logged, instead of cutting the home page off mid-response (PR #291, staging runs 1
    // and 2: "/" ended with a ChunkedEncodingError while it was read with 79 other pages).
    ArrayList<Taxon> subfamilies = TaxonMgr.getSubfamilies();
    StringBuilder slides = new StringBuilder();
    try {
      if (subfamilies != null) {
        int i = 0;
        for (Taxon subfamily : new ArrayList<Taxon>(subfamilies)) {
          String subfamilyName = subfamily == null ? null : subfamily.getName();
          if (subfamilyName == null) continue;
          boolean include = subfamily.isValid() || subfamilyName.contains("incertae_sedis");
          String imgFile = AntwebProps.getDomainApp() + "/image/" + subfamilyName + ".jpg";
          String imgLoc = AntwebProps.getDocRoot() + "image/" + subfamilyName + ".jpg";
          if (!include || !FileUtil.fileExists(imgLoc)) continue;
          ++i;
          String target = AntwebProps.getDomainApp() + "/images.do?subfamily=" + subfamilyName + "&rank=subfamily&project=allantwebants";
          slides.append("  <div class=\"slide small\" style=\"background-image: url(").append(imgFile).append(");\" id=\"").append(i)
            .append("\" onclick=\"window.location.href='").append(target).append("';\">\n")
            .append("      <div class=\"hover small\"></div>\n")
            .append("      <div class=\"top_gradient small\"></div>\n")
            .append("      <div class=\"name small\"><a class=\"\" href=\"").append(target).append("\">").append(Formatter.initCap(subfamilyName)).append("</a></div>\n")
            .append("      <div class=\"clear\"></div>\n")
            .append("  </div>\n");
          if (i % 7 == 0) slides.append("          </div>\n          <div class=\"things\">\n");
        }
      }
    } catch (RuntimeException e) {
      AntwebUtil.log("slideshow_homepage.jsp e:" + e);
      slides.setLength(0);
    }
%>

<a class="prev browse left">&#171;</a>
<div class="scrollable" id="autoscroll">
<div class="items">
<div class="things">
<%= slides %>
</div>
<div class="things">

</div>
</div>
</div>
<a class="next browse right">&#187;</a>
<div class="clear"></div>
