<%-- "About this list", one closed line above the list of taxonomicPage.do and browse.do in a place (included by
     web/taxonomicPage-body.jsp and web/showBrowse-body.jsp; reads overview, pageRank and pageType): a plain details
     element that opens on click (Brian Fisher, 2026-10-30). Since 2026-11-02 it explains, in plain words, the four
     views (ListView.viewDescriptions), the columns (Species and Other specimens of genus rows) and the three source
     icons (ListView.sourceIconHtml), then the sources of a georegion, bioregion, museum or project list, the range-data
     report links for curators and administrators, and the administrators' note on georegion species lists. Docs:
     docs/flows/places.md, docs/numbers.md "Views of lists". --%>
<%
StringBuilder aboutList = new StringBuilder();
// The views, columns and source icons (2026-11-02), on every list with the four views (not the AntCat list and Fossil Ants).
if (org.calacademy.antweb.ListView.hasViews(overview)
    && (Rank.SUBFAMILY.equals(pageRank) || Rank.GENUS.equals(pageRank) || Rank.SPECIES.equals(pageRank))) {
    String aboutRowRank = pageRank;   // the rank of the rows (showBrowse-body.jsp: the child rank)
    aboutList.append("<p><b>Views.</b> The buttons above the list choose which names it shows; the number in the header always counts the names of the chosen view.</p><ul class=\"about-views\">");
    for (String[] view : org.calacademy.antweb.ListView.viewDescriptions()) {
        aboutList.append("<li><b>").append(view[0]).append("</b>: ").append(org.calacademy.antweb.util.Html.esc(view[1])).append("</li>");
    }
    aboutList.append("</ul><p>Red-flagged specimens never count, in any view.</p>");
    if (Rank.GENUS.equals(aboutRowRank)) {
        aboutList.append("<p><b>Columns.</b> <b>Species</b>: the species and subspecies of the genus in the chosen view (a dash when none)."
            + " <b>Other specimens</b>: the specimens of the genus collected here under names that are not in the chosen view,"
            + " such as morphospecies that no curator has vetted and specimens identified only to genus; click the number to"
            + " see them. A genus known here only from specimens identified to genus is listed only in All specimen names.</p>");
    }
    aboutList.append("<p><b>Source.</b> ")
        .append(org.calacademy.antweb.ListView.sourceIconHtml(org.calacademy.antweb.ListView.SOURCE_SPECIMEN)).append(" Specimen: collected here (a name with a specimen collected here shows this even when it is also in the literature); ")
        .append(org.calacademy.antweb.ListView.sourceIconHtml(org.calacademy.antweb.ListView.SOURCE_CURATED)).append(" Curated: added by a curator; ")
        .append(org.calacademy.antweb.ListView.sourceIconHtml(org.calacademy.antweb.ListView.SOURCE_LITERATURE)).append(" Literature: published records, including AntCat type localities.</p>");
}
if (true) {
    String note = "";

    if (overview instanceof Geolocale ) {
      Geolocale geolocale = (Geolocale) overview;
      boolean isUseChildren = geolocale.getIsUseChildren();

      note = Formatter.initCap(Rank.getPluralRank(pageRank)) + " in the Georegion list are based on the following sources:"
          + " a) specimen records of valid species from the region (a genus also from specimens identified only to genus, 2026-10-29),"
          + " b) AntCat.org type locality information, and"
          + " c) Curator added records. Any curator may add or remove valid species or morphospecies (Edit Species List);"
          + " a morphospecies must have at least one specimen record in AntWeb, though not necessarily from this place.";

      //A.log("geolocaleTaxaHeaderNotes.jsp geolocale:" + geolocale + " num:" + Georank.getGeorankLevel(pageRank));
      if (Georank.getGeorankLevel(geolocale) >=3 && isUseChildren) {
        note += "This list is based on it's children " + Georank.getChildPluralRank(geolocale).toLowerCase() + ".";
      }

    if ("species".equals(pageRank)) {
          String speciesListWithRangeDataNote = "<br><br><b>Report: </b><a href='" + AntwebProps.getDomainApp() + "/query.do?name=geolocaleSpeciesListWithRangeData&param=" + overview + "'>Species List with Range Data</a>";
          if (LoginMgr.isAdmin(request)) {
              // Link below replaced with dash because of queryFile servlet suspicion. Admin only, but does not use connection pool. Connection pool fixed, but link does not work
              //speciesListWithRangeDataNote += "&nbsp;<a href='" + AntwebProps.getDomainApp() + "/queryFile?name=geolocaleSpeciesListWithRangeData&param=" + overview + "'><img src='" + AntwebProps.getDomainApp() + "/image/fileDownload.png' width=15></a>";
              speciesListWithRangeDataNote += "&nbsp;-";

              speciesListWithRangeDataNote += "&nbsp;<a href='" + AntwebProps.getDomainApp() + "/query.do?name=geolocaleSpeciesListRangeSummary&param=" + overview + "'>Summary</a>";
          }
          if ((overview instanceof Country || overview instanceof Adm1) && LoginMgr.isCurator(request)) {
              note += speciesListWithRangeDataNote;
              note += "<br>";
          }
          if ((overview instanceof Subregion || overview instanceof Region) && LoginMgr.isAdmin(request)) {
              speciesListWithRangeDataNote += "<br><b>&nbsp;&nbsp;&nbsp;(Admin only)</b> Use sparingly. Approximately 1 - 2 minutea for every 1K species - species count:" + overview.getSpeciesCount();
              note += speciesListWithRangeDataNote;
              note += "<br>";
          }
      }
      aboutList.append("<p>").append(note).append("</p>");
    }

    if (overview instanceof Bioregion) {
      note = Formatter.initCap(Rank.getPluralRank(pageRank)) + " in the Bioregion list are based on the following sources:"
          + " a) specimen records of any species from the bioregion,"
          + " b) AntCat.org type locality information. Bioregion lists are not managed by curators.";

      if ("species".equals(pageRank)) {

          String speciesListWithRangeDataNote = "<br><br><b>Report: </b><a href='" + AntwebProps.getDomainApp() + "/query.do?name=bioregionSpeciesListWithRangeData&param=" + overview + "'>Species List with Range Data</a>";
          if (LoginMgr.isAdmin(request)) {
              // Link below replaced with dash because of queryFile servlet suspicion. Admin only, but does not use connection pool. Connection pool fixed, but link does not work
              //speciesListWithRangeDataNote += "&nbsp;<a href='" + AntwebProps.getDomainApp() + "/queryFile?name=bioregionSpeciesListWithRangeData&param=" + overview + "'><img src='" + AntwebProps.getDomainApp() + "/image/fileDownload.png' width=15></a>";
              speciesListWithRangeDataNote += "&nbsp;-";

              speciesListWithRangeDataNote += "&nbsp;<a href='" + AntwebProps.getDomainApp() + "/query.do?name=bioregionSpeciesListRangeSummary&param=" + overview + "'>Summary</a>";
          }
          note += speciesListWithRangeDataNote;
          note += "<br>";
      }
      aboutList.append("<p>").append(note).append("</p>");
    }

    if (overview instanceof Museum) {
      note = Formatter.initCap(Rank.getPluralRank(pageRank)) + " in the Museum list are based on"
          + " specimen records of any species from the museum as indicated by the 'Owned by' field. Museum lists are not managed by curators."
      ;
      aboutList.append("<p>").append(note).append("</p>");
    }

    if (LoginMgr.isAdmin(request) && overview instanceof Project) {
      Project headerProject = (Project) overview;

      note = Formatter.initCap(Rank.getPluralRank(pageRank)) + " in the Project: " + org.calacademy.antweb.util.Html.esc(headerProject.getTitle()) + " are based on ";
      if ("worldants".equals(headerProject.getName())) {
          note += "data from Antcat.org.";
      } else if ("allantwebants".equals(headerProject.getName())) {
          note += " a) specimen records of any species,"
          + " b) AntCat.org type locality information. All Antweb list is not managed by curators.";
      } else if ("fossilants".equals(headerProject.getName())) {
          note += " Antcat fossil records and curator added records.";
      } else {
          note += "curator added records.";
      }
      aboutList.append("<p>").append(note).append("</p>");
    }
}

if (
    LoginMgr.isAdmin(request)
    && overview instanceof Geolocale
    && Rank.SPECIES.equals(pageRank)
    ) {
      boolean isUseChildren = ((Geolocale) overview).getIsUseChildren();
      aboutList.append("<p>(Administrators) Species in the \"Georegion\" lists are based on specimen records of valid species from the region, on literature, and on curation."
          + " Any curator may add or remove valid species from the list and also morphospecies"
          + " (which must have at least one record in Antweb though not necessary from the specific georegion).");
      if (isUseChildren) {
          aboutList.append(" This list is based on it's children ").append(Georank.getChildPluralRank((Geolocale) overview).toLowerCase()).append(".");
      }
      aboutList.append("</p>");
}

if (aboutList.length() > 0) { %>
<details id="about-list" class="about-list">
<summary>About this list</summary>
<%= aboutList %>
</details>
<div class="page_divider taxonomic"></div>
<% } %>
