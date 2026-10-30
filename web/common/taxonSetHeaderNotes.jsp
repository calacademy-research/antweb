<%-- The notes on where a list's names come from, above the list of taxonomicPage.do and browse.do in a place (included
     by web/taxonomicPage-body.jsp and web/showBrowse-body.jsp; reads overview, pageRank and pageType). Since 2026-10-30
     (Brian Fisher, less crowded header) they sit in one closed line, "About this list", a plain details element that
     opens on click: the sources of a georegion, bioregion, museum or project list, the range-data report links for
     curators and administrators, and the administrators' note on georegion species lists. Nothing when no note
     applies. The "Show Specimen Taxa" sentences are gone: the "Names from" selector of the list header does that. --%>
<%
StringBuilder aboutList = new StringBuilder();
if (true) {
    String note = "";

    if (overview instanceof Geolocale ) {
      Geolocale geolocale = (Geolocale) overview;
      boolean isUseChildren = geolocale.getIsUseChildren();

      note = Formatter.initCap(Rank.getPluralRank(pageRank)) + " in the Georegion list are based on the following sources:"
          + " a) specimen records of valid species from the region (a genus also from specimens identified only to genus, 2026-10-29),"
          + " b) AntCat.org type locality information, and"
          + " c) Curator added records.  Curators may add or remove valid species or morphospecies from the list."
          + " Morphospecies, however,  must have at least one specimen record in Antweb though not necessary from the specific georegion).";

      //A.log("geolocaleTaxaHeaderNotes.jsp geolocale:" + geolocale + " num:" + Georank.getGeorankLevel(pageRank));
      if (Georank.getGeorankLevel(geolocale) >=3 && isUseChildren) {
        note += "This list is based on it's children " + Georank.getChildPluralRank(geolocale).toLowerCase() + ".";
      }

      if ("taxaPage".equals(pageType) && geolocale.isCanShowSpecimenTaxa()) {
        // 2026-10-30: the selector replaced the "Show Specimen Taxa" link and the sentence that pointed to it
        note += "<br><br>Names from: <b>Specimens collected here</b> adds the names of every specimen collected in this "
          + geolocale.getGeorank() + " that is not on the list; <b>Place list</b> shows the list alone.";
      }
      if ("genus".equals(pageRank)) {
        note += "<br><br>A genus can be on the list from specimens collected here under names that are not species on the list"
          + " (morphospecies, names not in AntCat, specimens identified only to genus). Its row then says \"No species on the"
          + " list\" and how many specimens were collected here; a genus with species on the list says \"+ N specimens of"
          + " other names\" when there are such specimens. Click the number to see the specimens.";
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
          + " Curators may add or remove valid species from the list and also morphospecies"
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
