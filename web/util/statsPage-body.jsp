<%-- Body of the public Statistics page (statsPage.do): the live numbers (valid species, specimens, imaged
     species and specimens, images and label images, collaborating institutions) from TaxonMgr, AntwebMgr and
     MuseumMgr ("not yet available" in the minutes after a start), then the Global, bioregion and museum tables
     (StatSet request attributes from StatsPageAction, table statistics). Read-only. Docs: docs/numbers.md,
     docs/flows/home-and-statistics.md. Tests: tests/e2e/test_numbers_pages.py, test_count_check.py. --%>

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
   // The three image numbers come from the same start-up load (AntwebMgr.populateStats): 0 until then too.
   int liveImagedSpecies = AntwebMgr.getImagedSpeciesCount();
   int liveImagedSpecimens = AntwebMgr.getImagedSpecimensCount();
   int liveTotalImages = AntwebMgr.getTotalImagesCount();
   String starting = "<span data-stat=\"starting\">not yet available: AntWeb is starting (a few minutes after a restart); reload in a few minutes</span>"; %>
<% // The home page numbers of 2026-11-06 (Brian Fisher, release 9.3.0; StatisticsDb.HOME_COLUMNS), with the subspecies,
   // morphospecies and imaged fossils the home page leaves out. From the latest nightly statistics (-1 until loaded).
   long hLiving = AntwebMgr.getHomeCount("living_species");
   long hSpecimens = AntwebMgr.getHomeCount("living_species_specimens");
   long hImaged = AntwebMgr.getHomeCount("living_species_imaged");
   String notYet = "<span data-stat=\"pending\">not yet counted (the next nightly run counts it)</span>";
   java.util.function.LongFunction<String> fmt = v -> v >= 0 ? A.commaFormat(v) : notYet; %>
<br><b data-stat="living-species"><%= fmt.apply(hLiving) %> valid living ant species</b> (+ <span data-stat="fossil-species"><%= fmt.apply(AntwebMgr.getHomeCount("fossil_species")) %></span> fossil species)
<br>&nbsp;&nbsp;Valid ant species in AntCat, identified to genus; subspecies are counted below. As the default species lists, living species apart from fossils.
<br><br><b data-stat="species-with-specimens"><%= fmt.apply(hSpecimens) %> species with specimens in AntWeb</b><% if (hLiving > 0 && hSpecimens >= 0) { %> (<%= org.calacademy.antweb.home.StatisticsDb.percent(hSpecimens, hLiving) %> of valid living species)<% } %>
<br>&nbsp;&nbsp;Valid living ant species with at least one specimen record in AntWeb.
<br><br><b data-stat="species-imaged"><%= fmt.apply(hImaged) %> species imaged</b><% if (hLiving > 0 && hImaged >= 0) { %> (<%= org.calacademy.antweb.home.StatisticsDb.percent(hImaged, hLiving) %> of valid living species<% if (hSpecimens > 0) { %>; <%= org.calacademy.antweb.home.StatisticsDb.percent(hImaged, hSpecimens) %> of those with specimens<% } %>)<% } %>
<br>&nbsp;&nbsp;Valid living ant species with at least one photo of the ant (label photos and photos of red-flagged specimens do not count).
<br>&nbsp;&nbsp;<a href="<%= AntwebProps.getDomainApp() %>/imagingProgress.do" id="imaging-progress-link">Imaging progress</a>: species and subspecies imaged by bioregion, country and genus, and the ones still to image.
<br><br><b data-stat="fossil-species-imaged"><%= fmt.apply(AntwebMgr.getHomeCount("fossil_species_imaged")) %> fossil species imaged</b>
<br><br><b data-stat="living-subspecies"><%= fmt.apply(AntwebMgr.getHomeCount("living_subspecies")) %> valid living subspecies</b>
<br><br><b data-stat="morphospecies"><%= fmt.apply(AntwebMgr.getHomeCount("morphospecies")) %> morphospecies</b>, <span data-stat="morphospecies-imaged"><%= fmt.apply(AntwebMgr.getHomeCount("morphospecies_imaged")) %></span> imaged
<br>&nbsp;&nbsp;Ant morphospecies (codes such as "amd01" for species not yet named), identified to genus.
<br><br>
<br><b data-stat="valid-species"><%= liveValidSpecies > 0 ? A.commaFormat(liveValidSpecies) : starting %> valid species + ssp.</b>
<br>&nbsp;&nbsp;Valid ant (Formicidae) species and subspecies, fossils included. Names of other families and names not identified to genus are not counted.
 
<br><br><b><%= liveSpecimens > 0 ? A.commaFormat(liveSpecimens) : starting %> specimen records</b>
<% // Counted from the Museums list (MuseumMgr, as museums.do), 2026-10-27: was the fixed text "21".
   int institutions = MuseumMgr.getMuseumCount(); %>
<br>&nbsp;&nbsp;Specimen records from <% if (institutions > 0) { %>AntWeb's <span data-stat="institutions"><%= A.commaFormat(institutions) %></span> collaborating institutions (the <a href="<%= AntwebProps.getDomainApp() %>/museums.do">Museums</a> list)<% } else { %>AntWeb's collaborating institutions (the <a href="<%= AntwebProps.getDomainApp() %>/museums.do">Museums</a> list; their number is <%= starting %>)<% } %>.

<br><br><b><%= liveImagedSpecies > 0 ? A.commaFormat(liveImagedSpecies) : starting %>  species + ssp. imaged </b>
<br>&nbsp;&nbsp;Ant species and subspecies that are valid names or morphospecies, with at least one photo of the ant (label photos and photos of red-flagged specimens do not count). Names identified to genus only (indetermined, unidentifiable) and names not in AntCat (unrecognized) are not counted as species (since 2026-10-28).

<br><br><b><%= liveImagedSpecimens > 0 ? A.commaFormat(liveImagedSpecimens) : starting %> specimens imaged</b>
<br>&nbsp;&nbsp;Specimens with at least one photo of the ant; red-flagged specimens are not counted (since 2026-11-09).

<br><br><b><%= liveTotalImages > 0 ? A.commaFormat(liveTotalImages) : starting %> total specimen images<%= AntwebMgr.getLabelImagesCount() > 0 ? " + " + A.commaFormat(AntwebMgr.getLabelImagesCount()) + " labels" : "" %></b>
<br>&nbsp;&nbsp;Images of the ant; label images are counted separately (since 2026-10-27; until then the total counted every image file, labels included). Images of red-flagged specimens are not counted (since 2026-11-09).



<br><br><br>
<h2>Global Data:</h2>
<p id="stats-global-note">Valid ant (Formicidae) taxa in AntWeb's taxon table, from AntCat: living (Extant) and fossil. Other families are not counted, and a species or subspecies counts only when it is identified to genus. In the Fossil row, subfamilies and genera are those with valid fossil species, extinct and living, as on the Fossil lists.</p>
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
<p>Images of the specimens of each status; images of red-flagged specimens are not counted (since 2026-11-09).</p>
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
              <tr align=right><td><%= Html.esc(Formatter.initCap(status)) %></td><td><%= A.commaFormat(stats[0]) %></td><td><%= A.commaFormat(stats[1]) %></td><td><%= A.commaFormat(stats[2]) %></td><td><%= A.commaFormat(stats[3]) %></td><td><%= A.commaFormat(stats[4]) %></td><td><%= A.commaFormat(stats[5]) %></td></tr> 
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
<p>Names with at least one specimen that has a photo of the ant, by status; photos of red-flagged specimens do not count.</p>
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
        // A status with no imaged name shows 0 (a missing one threw a NullPointerException and the page failed).
        out.println("<tr><td align=right>" + Html.esc(Formatter.initCap(status)) + "</td><td align=right>" + A.commaFormat(count == null ? 0 : count.intValue()) + "</td></tr>");
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
<p id="stats-museum-note">Valid ant (Formicidae) taxa on each museum's list, living (Valid Extant) and fossil (Valid Fossil). Other families are not counted, as on the museum pages. Valid Fossil subfamilies and genera are those with fossil species on the museum's list, extinct and living: the headers of its Fossil lists.</p>

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
<b>Unimaged Valid Ants:</b> <a href='<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=species&project=worldants&isImaged=false'>here</a> (valid species with no photo of the ant anywhere in AntWeb; a specimen with only label photos is not imaged, nor is a red-flagged specimen)

</div>
