
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
<br><br><br>

<h2>Current Statistics:</h2>                    
<br><b><%= A.commaFormat(TaxonMgr.getValidTaxonCount()) %> valid species + ssp.</b>
<br>&nbsp;&nbsp;Valid species and subspecies.
<% if (LoginMgr.isDeveloper(request)) { %> <br>&nbsp;&nbsp;<b>Dev:</b> select count(taxon_name) from taxon where status = 'valid' and rank in ('species', 'subspecies')" <% } %>
 
<br><br><b><%= A.commaFormat(AntwebMgr.getSpecimensCount()) %> specimen records</b>
<br>&nbsp;&nbsp;Specimen records from Antweb's 21 collaborating institutions.

<br><br><b><%= A.commaFormat(AntwebMgr.getImagedSpeciesCount()) %>  species + ssp. imaged </b>
<br>&nbsp;&nbsp;Valid, morpho or indetermined species and subspecies imaged.
<% if (LoginMgr.isDeveloper(request)) { %> <br>&nbsp;&nbsp;<b>Dev:</b> select distinct genus, species, subspecies from specimen, image where specimen.code = image.image_of_id <% } %>

<br><br><b><%= A.commaFormat(AntwebMgr.getImagedSpecimensCount()) %> specimens imaged</b>
<% if (LoginMgr.isDeveloper(request)) { %> <br>&nbsp;&nbsp;<b>Dev:</b> select count(distinct specimen.code) from specimen,image where specimen.code = image.image_of_id <% } %>

<br><br><b><%= A.commaFormat(AntwebMgr.getTotalImagesCount()) %> total specimen images</b>

<% if (LoginMgr.isDeveloper(request)) { %> <br><b>Dev:</b> &nbsp;&nbsp;select count(*) from image <br>&nbsp;&nbsp;This is all images. Should claim to be specimen images?  Or just all images.<% } %>


<br><br><br>
<h2>Global Data:</h2>
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
<tr><th>Specimen Status</th><th>Total</th><th>Worker</th><th>Male</th><th>Queen</th><th>Other</th></tr>

<%
    int grandTotal = 0, workers = 0, males = 0, queens = 0, others = 0;
    HashMap<String, int[]> imageStats = (HashMap<String, int[]>) request.getAttribute("imageStats");
    
    String[] keys = {"valid", "morphotaxon", "indetermined", "unrecognized", "unavailable", "unidentifiable"};
    if (imageStats != null) {
        for (String status : keys) {
          int[] stats = imageStats.get(status);
          if (stats != null) {
              grandTotal += stats[0]; workers +=  stats[1]; males += stats[2]; queens += stats[3]; others += stats[4];
              %>
              <tr align=right><td><%= Formatter.initCap(status) %></td><td><%= A.commaFormat(stats[0]) %></td><td><%= A.commaFormat(stats[1]) %></td><td><%= A.commaFormat(stats[2]) %></td><td><%= A.commaFormat(stats[3]) %></td><td><%= A.commaFormat(stats[4]) %></td></tr> 
<%
          }
        }
    } %>
    <tr><td></td><td>---------------</td><td>---------------</td><td>---------------</td><td>---------------</td><td>---------------</td></tr>
    <tr align=right><td>Sum</td><td><%= A.commaFormat(grandTotal) %></td><td><%= A.commaFormat(workers) %></td><td><%= A.commaFormat(males) %></td><td><%= A.commaFormat(queens) %></td><td><%= A.commaFormat(others) %></td></tr> 
</table>

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
<b>Unimaged Valid Ants:</b> <a href='<%= AntwebProps.getDomainApp() %>/taxonomicPage.do?rank=species&project=worldants&isImaged=false'>here</a>

</div>
