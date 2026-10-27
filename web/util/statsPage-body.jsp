
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>

<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<!-- statsPage-body.jsp -->

<div id="page_contents">
<br>
<h1> Antweb Statistics</h1>
<% java.util.Date statsCountedAt = (java.util.Date) request.getAttribute("statsCountedAt"); %>
<% if (statsCountedAt != null) { %><p id="stats-counted-at">The tables below were counted at <%= new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm z").format(statsCountedAt) %>. They are counted once a day, at the end of the nightly update.</p><% } %>
<br><br><br>

<h2>Current Statistics:</h2>                    
<% // The two live numbers come from the caches AntWeb loads in the minutes after a start; until then they are 0.
   // Say so instead of showing 0 (2026-10-26: a test visit right after a restart saw "0 valid species").
   int liveValidSpecies = TaxonMgr.getValidSpeciesCount();
   int liveSpecimens = AntwebMgr.getSpecimensCount();
   String starting = "<span data-stat=\"starting\">not yet available: AntWeb is starting (a few minutes after a restart); reload in a few minutes</span>"; %>
<br><b data-stat="valid-species"><%= liveValidSpecies > 0 ? A.commaFormat(liveValidSpecies) : starting %> valid species + ssp.</b>
<br>&nbsp;&nbsp;Valid ant (Formicidae) species and subspecies, fossils included. Names of other families and names not identified to genus are not counted.
 
<br><br><b><%= liveSpecimens > 0 ? A.commaFormat(liveSpecimens) : starting %> specimen records</b>
<br>&nbsp;&nbsp;Specimen records from Antweb's 21 collaborating institutions.

<br><br><b><%= A.commaFormat(AntwebMgr.getImagedSpeciesCount()) %>  species + ssp. imaged </b>
<br>&nbsp;&nbsp;Valid, morpho or indetermined ant species and subspecies with at least one photo of the ant (label photos do not count). Names not identified to genus are not counted as species.

<br><br><b><%= A.commaFormat(AntwebMgr.getImagedSpecimensCount()) %> specimens imaged</b>

<br><br><b><%= A.commaFormat(AntwebMgr.getTotalImagesCount()) %> total specimen images<%= AntwebMgr.getLabelImagesCount() > 0 ? " + " + A.commaFormat(AntwebMgr.getLabelImagesCount()) + " labels" : "" %></b>
<br>&nbsp;&nbsp;Images of the ant; label images are counted separately (since 2026-10-27; until then the total counted every image file, labels included).



<br><br><br>
<h2>Global Data:</h2>
<p id="stats-global-note">Valid ant (Formicidae) taxa in AntWeb's taxon table, from AntCat: living (Extant) and fossil. Other families are not counted, and a species or subspecies counts only when it is identified to genus.</p>
<%
//String statsPageData = (String) request.getAttribute("statsPageData");
StatSet extantData = (StatSet) request.getAttribute("extantData");
StatSet fossilData = (StatSet) request.getAttribute("fossilData");
ArrayList<StatSet> bioregionData = (ArrayList<StatSet>) request.getAttribute("bioregionData");
ArrayList<StatSet> extantMuseumData = (ArrayList<StatSet>) request.getAttribute("extantMuseumData");
ArrayList<StatSet> fossilMuseumData = (ArrayList<StatSet>) request.getAttribute("fossilMuseumData");

     out.println(extantData.getHeader());
     out.println(extantData.toString());
     out.println(fossilData.toString());
     out.println(extantData.getFooter());

%>
<br><br><br>
<h2>Images:</h2>
<table border=1>
<tr><th>Specimen Status</th><th>Total</th><th>Worker</th><th>Male</th><th>Queen</th><th>Other</th><th>Label images</th></tr>

<%
    int grandTotal = 0, workers = 0, males = 0, queens = 0, others = 0, labelImages = 0;
    HashMap<String, int[]> imageStats = (HashMap<String, int[]>) request.getAttribute("imageStats");
    
    // Every status in the table (2026-10-21: six fixed statuses only, so the Sum row left the others out).
    ArrayList<String> keys = new ArrayList<String>(java.util.Arrays.asList("valid", "morphotaxon", "indetermined", "unrecognized", "unavailable", "unidentifiable"));
    if (imageStats != null) {
        java.util.TreeSet<String> more = new java.util.TreeSet<String>();
        for (String k : imageStats.keySet()) if (k != null && !keys.contains(k)) more.add(k);
        keys.addAll(more);
        for (String status : keys) {
          int[] stats = imageStats.get(status);
          if (stats != null) {
              grandTotal += stats[0]; workers +=  stats[1]; males += stats[2]; queens += stats[3]; others += stats[4]; labelImages += stats[5];
              %>
              <tr align=right><td><%= Formatter.initCap(status) %></td><td><%= A.commaFormat(stats[0]) %></td><td><%= A.commaFormat(stats[1]) %></td><td><%= A.commaFormat(stats[2]) %></td><td><%= A.commaFormat(stats[3]) %></td><td><%= A.commaFormat(stats[4]) %></td><td><%= A.commaFormat(stats[5]) %></td></tr> 
<%
          }
        }
    } %>
    <tr><td></td><td>---------------</td><td>---------------</td><td>---------------</td><td>---------------</td><td>---------------</td><td>---------------</td></tr>
    <tr align=right><td>Sum</td><td><%= A.commaFormat(grandTotal) %></td><td><%= A.commaFormat(workers) %></td><td><%= A.commaFormat(males) %></td><td><%= A.commaFormat(queens) %></td><td><%= A.commaFormat(others) %></td><td><%= A.commaFormat(labelImages) %></td></tr> 
</table>
<%@ include file="/common/imagesDefinition.jsp" %>

<%
/*
+----------------+----------+--------+-------+-------+-------+
| status         |    total | worker | male  | queen | other |
+----------------+----------+--------+-------+-------+-------+
| valid          |   187252 | 153469 | 12869 | 19056 |    97 |
| morphotaxon    |    29372 |  22516 |  3164 |  2926 |    24 |
| indetermined   |     4001 |   2607 |   940 |   411 |     0 |
| unrecognized   |      412 |    252 |    66 |    64 |     0 |
| unavailable    |      141 |    135 |     6 |     0 |     0 |
| unidentifiable |       29 |     29 |     0 |     0 |     0 |
+----------------+----------+--------+-------+-------+-------+
6 rows in set (0.93 sec)
*/
%>


<br><br><br>
<h2>Imaged Species:</h2>
<table border=1>
<tr><th>Species Status</th><th>Species</th></tr>
<%
    String[] keys2 = {"valid", "morphotaxon", "indetermined", "unrecognized", "unavailable", "unidentifiable", "obsolete combination"};
    HashMap<String, Integer> imageTaxonStats = (HashMap<String, Integer>) request.getAttribute("imageTaxonStats");
    if (imageTaxonStats != null) {
      //Set statuses = imageTaxonStats.keySet();
      //for (Object status : statuses) {
      for (String status : keys2) {
        Integer count = imageTaxonStats.get(status);
        out.println("<tr><td align=right>" + Formatter.initCap(status) + "</td><td align=right>" + A.commaFormat(count) + "</td></tr>");
      }
    }
%>
</table>


<br><br>
<h2>Bioregion Data:</h2>
<p id="stats-bioregion-note">Valid, living ant (Formicidae) taxa on each bioregion's list. Other families are not counted, as on the bioregion pages.</p>
<% int i = 0;
   String footer = "";
   for (StatSet statSet : bioregionData) {
     ++i;
     if (i == 1) out.println(statSet.getHeader());
     out.println(statSet.toString());
     footer = statSet.getFooter();
   }
   out.println(footer);
%>

<br><br>
<h2>Museum Data:</h2>
<p id="stats-museum-note">Valid ant (Formicidae) taxa on each museum's list, living (Valid Extant) and fossil (Valid Fossil). Other families are not counted, as on the museum pages.</p>

<br>
<h3>Valid Extant:</h3>
<% i = 0;
   footer = "";
   for (StatSet statSet : extantMuseumData) {
     ++i;
     if (i == 1) out.println(statSet.getHeader());
     out.println(statSet.toString());
     footer = statSet.getFooter();
   }
   out.println(footer);
%>

<br>
<h3>Valid Fossil:</h3>
<% i = 0;
   footer = "";
   for (StatSet statSet : fossilMuseumData) {
     ++i;
     if (i == 1) out.println(statSet.getHeader());
     out.println(statSet.toString());
     footer = statSet.getFooter();
   }
   out.println(footer);
%>



<br><br>
<b>Unimaged Valid Ants:</b> <a href='<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=species&project=worldants&isImaged=false'>here</a> (valid species with no photo of the ant anywhere in AntWeb; a specimen with only label photos is not imaged)

</div>
