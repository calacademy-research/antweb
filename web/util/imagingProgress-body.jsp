<%-- Body of the public Imaging progress page (imagingProgress.do; ImagingProgressAction, Brian Fisher 2026-11-08): the
     headline (valid living ant species and subspecies imaged, with a bar), the species-only number of the home page, the
     fossils apart, the names that can be imaged now (unimaged, with specimens in AntWeb; CASC first), the trend from the
     daily statistics rows since 2026-10-28, and sortable tables by museum, bioregion, country and genus, each row linked
     to its list of unimaged names. With genus=<key>: the unimaged names of that genus. Every number is explained on the
     page. Reads request attributes only (table imaging_progress). Docs: docs/numbers.md rows 124 to 128. Tests:
     tests/e2e/test_imaging_progress.py. --%>
<%@ page import="java.util.*" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="org.calacademy.antweb.home.ImagingProgressDb" %>
<%@ page import="org.calacademy.antweb.util.AntwebProps" %>
<%@ page import="org.calacademy.antweb.util.Html" %>
<%@ page import="org.calacademy.antweb.util.A" %>
<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%!
  static String n(long v) { return A.commaFormat(v); }
  static String enc(String s) { return URLEncoder.encode(s == null ? "" : s, java.nio.charset.StandardCharsets.UTF_8); }
  /** A bar of the imaged share (decorative; the numbers are written next to it). */
  static String bar(long part, long whole) {
    double p = whole <= 0 ? 0 : Math.min(100.0, 100.0 * part / whole);
    return "<span class=\"ip-bar\" aria-hidden=\"true\"><span style=\"width:" + String.format(java.util.Locale.ROOT, "%.1f", p) + "%\"></span></span>";
  }
  static ImagingProgressDb.Row find(List<ImagingProgressDb.Row> rows, String key) {
    if (rows != null) for (ImagingProgressDb.Row r : rows) if (key.equals(r.key)) return r;
    return null;
  }
%>
<%
  String domainApp = AntwebProps.getDomainApp();
  String self = domainApp + "/imagingProgress.do";
  ImagingProgressDb.Row genusRow = (ImagingProgressDb.Row) request.getAttribute("genusRow");
  @SuppressWarnings("unchecked") List<ImagingProgressDb.Unimaged> genusList = (List<ImagingProgressDb.Unimaged>) request.getAttribute("genusList");
  @SuppressWarnings("unchecked") Map<String, List<ImagingProgressDb.Row>> progress = (Map<String, List<ImagingProgressDb.Row>>) request.getAttribute("progress");
  @SuppressWarnings("unchecked") List<ImagingProgressDb.Day> trend = (List<ImagingProgressDb.Day>) request.getAttribute("trend");
  java.sql.Timestamp computed = (java.sql.Timestamp) request.getAttribute("computed");
  boolean notCounted = Boolean.TRUE.equals(request.getAttribute("notCounted"));
%>
<style>
#imaging-progress { max-width: 1100px; line-height: 1.4; }
#imaging-progress h2 { margin-top: 28px; }
#imaging-progress .ip-headline { font-size: 18px; margin: 12px 0 4px; }
#imaging-progress .ip-headline .numbers { font-size: 30px; font-weight: bold; }
#imaging-progress .ip-bar { display: inline-block; width: 320px; max-width: 100%; height: 14px; background: #e6e6e6; border-radius: 7px; overflow: hidden; vertical-align: middle; }
#imaging-progress .ip-bar span { display: block; height: 100%; background: #3a7d44; }
#imaging-progress table.ip-table .ip-bar { width: 90px; height: 9px; }
#imaging-progress .ip-note { color: #555; }
#imaging-progress .ip-explain { margin: 4px 0 4px 18px; color: #333; }
#imaging-progress table.ip-table { border-collapse: collapse; margin: 8px 0 4px; font-size: 13px; }
#imaging-progress table.ip-table th, #imaging-progress table.ip-table td { border-bottom: 1px solid #ddd; padding: 3px 8px; text-align: left; vertical-align: top; }
#imaging-progress table.ip-table td.num, #imaging-progress table.ip-table th.num { text-align: right; }
#imaging-progress table.ip-table th { background: #f2f2f2; cursor: pointer; white-space: nowrap; }
#imaging-progress table.ip-table th[aria-sort="ascending"]::after { content: " \25B2"; }
#imaging-progress table.ip-table th[aria-sort="descending"]::after { content: " \25BC"; }
#imaging-progress .ip-scroll { overflow-x: auto; max-width: 100%; }
#imaging-progress dl.ip-columns dt { font-weight: bold; margin-top: 6px; }
#imaging-progress dl.ip-columns dd { margin-left: 18px; }
#imaging-progress svg.ip-trend { max-width: 100%; height: auto; }
</style>

<div id="page_contents">
<div id="imaging-progress">
<br>
<% if (genusRow != null) { %>
<h1 id="ip-genus-title">Unimaged species of <i><%= Html.esc(genusRow.label()) %></i></h1>
<p><a href="<%= Html.escAttr(self) %>">Imaging progress</a> &gt; <%= Html.esc(genusRow.label()) %><% if (genusRow.parent != null) { %> (<%= Html.esc(genusRow.parent) %>)<% } %></p>
<p class="ip-headline"><span class="numbers" data-stat="genus-imaged"><%= n(genusRow.imaged) %></span> of <span data-stat="genus-total"><%= n(genusRow.total) %></span>
  valid living species and subspecies of this genus are imaged (<%= genusRow.percent() %>). <%= bar(genusRow.imaged, genusRow.total) %></p>
<p class="ip-note">Imaged: at least one specimen filed under the name, anywhere in AntWeb, has a photo of the ant. Label photos never count.</p>
<p>The <span data-stat="genus-unimaged"><%= n(genusList == null ? 0 : genusList.size()) %></span> names below are not imaged yet.
  <span data-stat="genus-with-specimens"><%= n(genusRow.unimagedWithSpecimens) %></span> of them have specimens in AntWeb and can be imaged now
  (<%= n(genusRow.unimagedCasc) %> with a specimen at CASC). The ones with CASC specimens come first, then the ones with the most specimens.
  <a href="<%= Html.escAttr(self + "?genus=" + enc(genusRow.key) + "&format=csv") %>" id="ip-genus-csv">Download this list (CSV)</a></p>
<div class="ip-scroll">
<table class="ip-table" id="ip-genus-list">
<thead><tr><th>Name</th><th>Rank</th><th class="num">Specimens in AntWeb</th><th class="num">Specimens at CASC</th><th>Museums with specimens</th></tr></thead>
<tbody>
<% if (genusList != null) for (ImagingProgressDb.Unimaged u : genusList) { %>
<tr><td><a href="<%= Html.escAttr(domainApp + "/description.do?taxonName=" + enc(u.taxonName)) %>"><i><%= Html.esc(u.displayName()) %></i></a></td>
<td><%= Html.esc(u.rank) %></td><td class="num" data-v="<%= u.specimens %>"><%= n(u.specimens) %></td><td class="num" data-v="<%= u.cascSpecimens %>"><%= n(u.cascSpecimens) %></td>
<td><%= Html.esc(u.museums) %></td></tr>
<% } %>
</tbody>
</table>
</div>
<% } else { %>
<h1>Imaging progress</h1>
<p>How many of the known living ant species and subspecies have a photo of the ant on AntWeb, where they come from, and which ones
  can be imaged now because AntWeb already has specimens of them.</p>
<p class="ip-note" id="ip-label-note">Label photos never count: a name whose specimens only have photos of their labels is not imaged.</p>
<% if (notCounted || progress == null) { %>
<p id="ip-not-counted">The imaging progress has not been counted yet. The daily update counts it every morning; please come back tomorrow.</p>
<% } else {
     List<ImagingProgressDb.Row> all = progress.get(ImagingProgressDb.ALL);
     ImagingProgressDb.Row living = find(all, ImagingProgressDb.LIVING);
     ImagingProgressDb.Row species = find(all, ImagingProgressDb.LIVING_SPECIES);
     ImagingProgressDb.Row fossil = find(all, ImagingProgressDb.FOSSIL);
     if (computed != null) { %>
<p id="ip-counted-at">Counted on <%= new java.text.SimpleDateFormat("yyyy-MM-dd 'at' HH:mm").format(computed) %> (Pacific time) by the daily update, once a day.</p>
<%   } %>

<% if (living != null) { %>
<p class="ip-headline" id="ip-headline"><span class="numbers" data-stat="imaged"><%= n(living.imaged) %></span> of
  <span class="numbers" data-stat="total"><%= n(living.total) %></span> valid living ant species and subspecies are imaged
  (<span data-stat="percent"><%= living.percent() %></span>).</p>
<p><%= bar(living.imaged, living.total) %></p>
<p class="ip-explain">Valid living species and subspecies: the names AntCat accepts as valid, not fossils, identified to genus.
  Imaged: at least one specimen filed under the name, anywhere in AntWeb, has a photo of the ant.
  Still to image: <span data-stat="unimaged"><%= n(living.unimaged()) %></span>.</p>
<% } %>
<% if (species != null) { %>
<p id="ip-species-line">Species only (without subspecies): <b data-stat="species-imaged"><%= n(species.imaged) %></b> of <b data-stat="living-species"><%= n(species.total) %></b>
  valid living species are imaged (<%= species.percent() %>). This is the "species imaged" number of the home page and the
  <a href="<%= Html.escAttr(domainApp + "/statsPage.do") %>">statistics page</a>.</p>
<% } %>
<p id="ip-fossil-line">Fossil species and subspecies, not in the numbers above:
  <b data-stat="fossil-imaged"><%= n(fossil == null ? 0 : fossil.imaged) %></b> of <b data-stat="fossil-total"><%= n(fossil == null ? 0 : fossil.total) %></b> imaged.</p>

<% if (living != null) { %>
<h2>Can be imaged now</h2>
<p><b data-stat="unimaged-with-specimens"><%= n(living.unimagedWithSpecimens) %></b> of the <%= n(living.unimaged()) %> unimaged valid living species and
  subspecies have at least one specimen record in AntWeb, so they can be imaged without new fieldwork;
  <b data-stat="unimaged-casc"><%= n(living.unimagedCasc) %></b> of them have a specimen at CASC (California Academy of Sciences).
  The others are known to AntWeb only from AntCat, with no specimen record yet.</p>
<p><a href="<%= Html.escAttr(self + "?format=csv") %>" id="ip-csv">Download the list of all unimaged valid living species and subspecies (CSV)</a>:
  one line per name with its number of specimens in AntWeb, its specimens at CASC and the museums that have its specimens; names
  with CASC specimens first, then the ones with the most specimens.</p>
<% } %>

<h2>Trend</h2>
<% if (trend == null || trend.isEmpty()) { %>
<p>No daily numbers yet.</p>
<% } else {
     int w = 640, h = 180, pad = 40;
     long min = Long.MAX_VALUE, max = 0;
     for (ImagingProgressDb.Day d : trend) { min = Math.min(min, d.validImaged); max = Math.max(max, d.validImaged); }
     if (max == min) { max = max + 1; min = Math.max(0, min - 1); }
     StringBuilder pts = new StringBuilder();
     for (int i = 0; i < trend.size(); i++) {
       double x = pad + (trend.size() == 1 ? (w - 2 * pad) / 2.0 : (w - 2.0 * pad) * i / (trend.size() - 1));
       double y = h - pad / 2.0 - (h - 1.5 * pad) * (trend.get(i).validImaged - min) / (double) (max - min);
       pts.append(String.format(java.util.Locale.ROOT, "%.1f,%.1f ", x, y));
     }
     ImagingProgressDb.Day first = trend.get(0), last = trend.get(trend.size() - 1); %>
<p>Valid species and subspecies imaged each day, from <%= Html.esc(first.date) %> (<%= n(first.validImaged) %>) to <%= Html.esc(last.date) %>
  (<%= n(last.validImaged) %>, <%= ImagingProgressDb.Row.percent(last.validImaged, last.validTotal) %> of <%= n(last.validTotal) %>).</p>
<svg class="ip-trend" id="ip-trend" width="<%= w %>" height="<%= h %>" viewBox="0 0 <%= w %> <%= h %>" role="img"
  aria-label="Valid species and subspecies imaged per day since <%= ImagingProgressDb.RULE_SINCE %>">
  <line x1="<%= pad %>" y1="<%= h - pad / 2 %>" x2="<%= w - pad %>" y2="<%= h - pad / 2 %>" stroke="#999"/>
  <text x="<%= pad %>" y="<%= h - 4 %>" font-size="11"><%= Html.esc(first.date) %></text>
  <text x="<%= w - pad %>" y="<%= h - 4 %>" font-size="11" text-anchor="end"><%= Html.esc(last.date) %></text>
  <text x="4" y="<%= pad / 2 + 10 %>" font-size="11"><%= n(max) %></text>
  <text x="4" y="<%= h - pad / 2 %>" font-size="11"><%= n(min) %></text>
  <polyline fill="none" stroke="#3a7d44" stroke-width="2" points="<%= pts.toString().trim() %>"/>
</svg>
<p class="ip-note" id="ip-trend-note">The trend starts on <%= ImagingProgressDb.RULE_SINCE %>, when the rules of these numbers last changed;
  the days before were counted another way and are left out.
  It shows the number the daily statistics keep: valid species and subspecies imaged, fossils included, so it is a little larger
  than the headline. From 2026-11-06 the table below also has the species-only number of the home page.</p>
<details><summary>The numbers of each day</summary>
<table class="ip-table" id="ip-trend-table">
<thead><tr><th>Day</th><th class="num">Valid species and subspecies imaged (fossils included)</th><th class="num">Of</th><th class="num">%</th><th class="num">Living species imaged (home page)</th></tr></thead>
<tbody>
<% for (ImagingProgressDb.Day d : trend) { %>
<tr><td><%= Html.esc(d.date) %></td><td class="num"><%= n(d.validImaged) %></td><td class="num"><%= n(d.validTotal) %></td>
<td class="num"><%= ImagingProgressDb.Row.percent(d.validImaged, d.validTotal) %></td><td class="num"><%= d.livingSpeciesImaged >= 0 ? n(d.livingSpeciesImaged) : "-" %></td></tr>
<% } %>
</tbody>
</table>
</details>
<% } %>

<h2>What the columns mean</h2>
<dl class="ip-columns">
<dt>Valid living species and subspecies</dt><dd>Bioregions and countries: the valid living species and subspecies on the place's list
  (its Valid view). Genera: those of the genus. Museums: those with at least one specimen at the museum.</dd>
<dt>Imaged</dt><dd>Of those, the names with a photo of the ant anywhere in AntWeb (any specimen, from any place). Label photos never count.</dd>
<dt>Imaged from here</dt><dd>Of those, the names with a photo of the ant of a specimen collected in this place (museums: a specimen of this museum).
  A name can be imaged but not imaged from here, when its only photos are of specimens from elsewhere.</dd>
<dt>Unimaged</dt><dd>Valid living species and subspecies minus imaged. The number links to the list of these names.</dd>
<dt>Unimaged but with specimens in AntWeb</dt><dd>Of the unimaged names, those with at least one specimen record in AntWeb, in any museum:
  they can be imaged now. "At CASC": of those, the names with a specimen at CASC.</dd>
</dl>
<p class="ip-note">Click a column heading to sort the table.</p>

<%   String[][] sections = {
       {ImagingProgressDb.MUSEUM, "By museum", "Museum", "Where the specimens of the unimaged names are, CASC first."},
       {ImagingProgressDb.BIOREGION, "By bioregion", "Bioregion", ""},
       {ImagingProgressDb.COUNTRY, "By country", "Country", ""},
       {ImagingProgressDb.GENUS, "By genus", "Genus", ""}};
     for (String[] sec : sections) {
       List<ImagingProgressDb.Row> rows = progress.get(sec[0]);
       boolean hasHere = !ImagingProgressDb.GENUS.equals(sec[0]); %>
<h2 id="ip-h-<%= sec[0] %>"><%= sec[1] %></h2>
<% if (!sec[3].isEmpty()) { %><p><%= sec[3] %></p><% } %>
<% if (rows == null || rows.isEmpty()) { %>
<p>None counted.</p>
<% } else { %>
<div class="ip-scroll">
<table class="ip-table ip-sortable" id="ip-table-<%= sec[0] %>" data-kind="<%= sec[0] %>">
<thead><tr><th data-type="text"><%= sec[2] %></th><% if (ImagingProgressDb.COUNTRY.equals(sec[0])) { %><th data-type="text">Bioregion</th><% } %><% if (ImagingProgressDb.GENUS.equals(sec[0])) { %><th data-type="text">Subfamily</th><% } %>
<th class="num" data-type="num">Valid living species and subspecies</th><th class="num" data-type="num">Imaged</th><th class="num" data-type="num">%</th><th data-type="num">Share imaged</th>
<% if (hasHere) { %><th class="num" data-type="num"><%= ImagingProgressDb.MUSEUM.equals(sec[0]) ? "Imaged from this museum" : "Imaged from here" %></th><% } %>
<th class="num" data-type="num">Unimaged</th><th class="num" data-type="num">Unimaged but with specimens in AntWeb</th><th class="num" data-type="num">At CASC</th></tr></thead>
<tbody>
<%   for (ImagingProgressDb.Row r : rows) {
       String link = null;
       String placeLink = null;
       if (ImagingProgressDb.BIOREGION.equals(r.type)) {
         link = domainApp + "/taxonomicPage.do?rank=species&bioregionName=" + enc(r.key) + "&statusSet=valid&isImaged=false";
         placeLink = domainApp + "/bioregion.do?name=" + enc(r.key);
       } else if (ImagingProgressDb.COUNTRY.equals(r.type)) {
         link = domainApp + "/taxonomicPage.do?rank=species&countryName=" + enc(r.name) + "&statusSet=valid&isImaged=false";
         placeLink = domainApp + "/country.do?name=" + enc(r.name);
       } else if (ImagingProgressDb.GENUS.equals(r.type)) {
         link = self + "?genus=" + enc(r.key);
         placeLink = link;
       } else if (ImagingProgressDb.MUSEUM.equals(r.type)) {
         placeLink = domainApp + "/museum.do?museumCode=" + enc(r.key);
       }
       double pct = r.total <= 0 ? 0 : 100.0 * r.imaged / r.total; %>
<tr data-key="<%= Html.escAttr(r.key) %>"><td><a href="<%= Html.escAttr(placeLink) %>"><%= ImagingProgressDb.GENUS.equals(r.type) ? "<i>" + Html.esc(r.label()) + "</i>" : Html.esc(r.label()) %></a></td>
<% if (ImagingProgressDb.COUNTRY.equals(r.type) || ImagingProgressDb.GENUS.equals(r.type)) { %><td><%= Html.esc(r.parent == null ? "" : r.parent) %></td><% } %>
<td class="num" data-v="<%= r.total %>"><%= n(r.total) %></td><td class="num" data-v="<%= r.imaged %>"><%= n(r.imaged) %></td>
<td class="num" data-v="<%= String.format(java.util.Locale.ROOT, "%.3f", pct) %>"><%= r.percent() %></td><td data-v="<%= String.format(java.util.Locale.ROOT, "%.3f", pct) %>"><%= bar(r.imaged, r.total) %></td>
<% if (hasHere) { %><td class="num" data-v="<%= r.imagedHere %>"><%= r.imagedHere < 0 ? "-" : n(r.imagedHere) %></td><% } %>
<td class="num" data-v="<%= r.unimaged() %>"><%= link == null || r.unimaged() == 0 ? n(r.unimaged()) : "<a class=\"ip-unimaged\" href=\"" + Html.escAttr(link) + "\">" + n(r.unimaged()) + "</a>" %></td>
<td class="num" data-v="<%= r.unimagedWithSpecimens %>"><%= n(r.unimagedWithSpecimens) %></td><td class="num" data-v="<%= r.unimagedCasc %>"><%= n(r.unimagedCasc) %></td></tr>
<%   } %>
</tbody>
</table>
</div>
<% } %>
<%   } %>
<% } %>
<% } %>
</div>
</div>
<script>
// Click a column heading to sort its table (numbers by their data-v value; text by the cell's text). Without script the
// tables keep the order the page gave them (most unimaged names first; museums: CASC first).
(function () {
  var tables = document.querySelectorAll("#imaging-progress table.ip-sortable");
  Array.prototype.forEach.call(tables, function (table) {
    var heads = table.querySelectorAll("thead th");
    Array.prototype.forEach.call(heads, function (th, col) {
      th.setAttribute("tabindex", "0");
      var sort = function () {
        var asc = th.getAttribute("aria-sort") !== "ascending";
        Array.prototype.forEach.call(heads, function (h) { h.removeAttribute("aria-sort"); });
        th.setAttribute("aria-sort", asc ? "ascending" : "descending");
        var body = table.tBodies[0];
        var rows = Array.prototype.slice.call(body.rows);
        var num = th.getAttribute("data-type") === "num";
        rows.sort(function (a, b) {
          var x = a.cells[col], y = b.cells[col];
          var r = num ? parseFloat(x.getAttribute("data-v")) - parseFloat(y.getAttribute("data-v"))
                      : x.textContent.trim().localeCompare(y.textContent.trim());
          return asc ? r : -r;
        });
        rows.forEach(function (r) { body.appendChild(r); });
      };
      th.addEventListener("click", sort);
      th.addEventListener("keydown", function (e) { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); sort(); } });
    });
  });
})();
</script>
