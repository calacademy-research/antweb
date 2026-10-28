<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>

<%@ page import="java.util.*" %>


<div class=left>
<%
    //String domainApp = AntwebProps.getDomainApp();
    ArrayList<Overview> overviews = (ArrayList<Overview>) request.getAttribute("overviews"); 
    
    String message = null; // (String) request.getAttribute("message"); 

    if (overviews == null) message = "Overviews is null";
    
    if (message != null) out.println("<h2>" + message + "</h2>");

    if (overviews.get(0) == null) AntwebUtil.log("overviews-body.jsp get(0) is null.");

%>
<br>
<% 
String heading = overviews.get(0).getHeading();
if ("Country".equals(heading)) heading = "Countries";
else heading = heading + "s";
%>
<h1><%= heading %>:</h1>
<br>

<% if (overviews.get(0) instanceof Museum) { %>
<br><b>Museum</b> refers to any institution or organization that owns and manages scientific collections. This includes <b>Institutional Collections, Personal Collections, Project Collections</b>.  Specimens associated with a museum are grouped by the information in the "owned by" field in specimen records.  
<br><br><br>
<h3><u>Kinds of Collections Antweb</u></h3>
<ul>
<li><b>&nbsp;&nbsp;&nbsp;Institutional Collections</b> are those that have been formally accessioned into an institution and are curated and cared for by the institution.  Example: BMNH
</li>
<br>
<li><b>&nbsp;&nbsp;&nbsp;Personal Collections</b> are under the control of an individual researcher and are not formally associated with or accessioned into an institution. They may be the personal property of a private collector.  We hope all private collectors have a plan to eventually donate their collection to an institution which will provide long-term care for the specimens. Example: PSWC, Ward
</li>
<br>
<li><b>&nbsp;&nbsp;&nbsp;Research Collections</b> are those collected by a researcher or research team that may not be formally accessioned into its collections. Project Collections may eventually become part of the Institutional Collections or remain under the management and control of the individual researchers. Example: EcoFoG, Kourou
</li>
</ul>
<br><br><br>
<% } %>

<%
    boolean titleIsName = true;
    for (Overview overview : overviews) {
        if (!overview.getName().equals(overview.getTitle())) {
          titleIsName = false;
          A.log("Setting title isName:" + titleIsName + " name:" + overview.getName() + " title:" + overview.getTitle());
          continue;
        }
    }
    out.println("<table><tr><th>Name</th>");
    if (!titleIsName) out.println("<th>Title</th>");
    // The column is the Valid species number of the place pages (2026-10-21: it was labelled "Species").
    // "Image files" (2026-10-27, Brian Fisher: "Images" means images of the ant): the place's image_count, every
    // image file of its specimens, labels included; the place page shows images of the ant and label images apart.
    out.println("<th>Valid species</th><th>Specimens</th><th title='Every image file of the specimens, label images included'>Image files</th></tr>");
    for (Overview overview : overviews) {
      if ("Antarctica_region".equals(overview.getName())) continue;
      out.println(
          "<tr>"
          + "<td><a href=\"" + overview.getThisPageTarget() + "\">" + overview.getKeyStr() + "</a></td>");
       if (!titleIsName) out.println("<td>" + org.calacademy.antweb.util.Html.esc(overview.getTitle()) + "</td>");
       out.println("<td>" + overview.getValidSpeciesCount() + "</td>");
       // Port of Entry (2026-10-22): a species list only, no specimen or image numbers (Project.isSpeciesListOnly).
       boolean listOnly = overview instanceof Project && ((Project) overview).isSpeciesListOnly();
       out.println("<td>" + (listOnly ? "<span title='a list of species intercepted at ports of entry; no specimen numbers'>-</span>" : "" + overview.getSpecimenCount()) + "</td>"
        + "<td>" + (listOnly ? "-" : "" + overview.getImageCount()) + "</td>"
        + "</tr>");
    }
    out.println("</table>");
    request.setAttribute("imagesDefinitionFiles", Boolean.TRUE);
%>
<%@ include file="/common/imagesDefinition.jsp" %>
<%

    if (LoginMgr.isAdmin(request)) { %>
      <br><br><b>Admin Note:</b>Data comes from the specimen_count, image_count fields of the appropriate overview table.

<%  } 

    String[] colors = HttpUtil.getColors();
%>

<br><br><br>

<script src="//cdnjs.cloudflare.com/ajax/libs/d3/3.4.4/d3.min.js"></script>
<script src="<%= AntwebProps.getDomainApp() %><%= org.calacademy.antweb.util.Asset.url(application, "/chart/d3pie.min.js") %>"></script>

<%
  if (true) {
    String title = "Image Submissions 'Pie Chart'";
    String subtitle = "Distribution of image files by " + overviews.get(0).getHeading();
    String footer = "Source: Data submitted to Antweb.org";
    String jsonData = "";
    int i = 0;
    for (Overview overview : overviews) {
      if (i > 0) jsonData += ",";
      ++i;
      String color = overview.getChartColor();
      if (color == null) {
        //AntwebUtil.log("overview-body.jsp color is null");
        try {
          color = colors[i];
        } catch (java.lang.ArrayIndexOutOfBoundsException e) {
          AntwebUtil.log("overviews-body.jsp colors[" + i + "] out of bounds");
          color = "#0c6197";
        }
      }

      jsonData += "{"
        + "\"label\": \"" + overview.getDisplayName() + "\","
        + "\"value\": " + overview.getImageCount() + ","
        + "\"color\": \"" + color + "\""
        + "}"
      ;
    } 	
%> 
    <%@include file="chart/pieChart.jsp" %>
<% 
  } 
%>


<%
  if (true) {
    String title = "Specimen Submissions 'Pie Chart'";
    String subtitle = "Distribution of Specimen by " + overviews.get(0).getHeading();
    String footer = "Source: Data submitted to Antweb.org";
    String jsonData = "";
    int i = 0;
    for (Overview overview : overviews) {
      if (i > 0) jsonData += ",";
      ++i;
      String color = overview.getChartColor();
      if (color == null) {
        //if (AntwebProps.isDevMode()) AntwebUtil.log("overviews-body.jsp color is null i:" + i);
        color = colors[i];
      }

      jsonData += "{"
        + "\"label\": \"" + overview.getDisplayName() + "\","
        + "\"value\": " + overview.getSpecimenCount() + ","
        + "\"color\": \"" + color + "\""
        + "}"
      ;
    } 	
%> 
    <%@include file="chart/pieChart.jsp" %>
<% 
  } 
%>




</div > 


