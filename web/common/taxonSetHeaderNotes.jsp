<% 
if (true) {
    String note = "";
      
    if (overview instanceof Geolocale ) {
      Geolocale geolocale = (Geolocale) overview;
      boolean isUseChildren = geolocale.getIsUseChildren();       

      note = Formatter.initCap(Rank.getPluralRank(pageRank)) + " in the Georegion list are based on the following sources:" 
          + " a) specimen records of valid species from the region," 
          + " b) AntCat.org type locality information, and" 
          + " c) Curator added records.  Curators may add or remove valid species or morphospecies from the list." 
          + " Morphospecies, however,  must have at least one specimen record in Antweb though not necessary from the specific georegion)."; 

      //A.log("geolocaleTaxaHeaderNotes.jsp geolocale:" + geolocale + " num:" + Georank.getGeorankLevel(pageRank));
      if (Georank.getGeorankLevel(geolocale) >=3 && isUseChildren) {
        note += "This list is based on it's children " + Georank.getChildPluralRank(geolocale).toLowerCase() + ".";
      } 
     
      if ("taxaPage".equals(pageType) && !isUseChildren) {
        note += "<br><br>To see all " + Rank.getPluralRank(pageRank).toLowerCase() + " from specimen records in this " 
          + geolocale.getGeorank() + ", click on <b>Show Specimen Taxa</b> at the top of the list."
          ;
      } %>

    <%
      if ((overview instanceof Country || overview instanceof Adm1) && LoginMgr.isCurator(request)) {
          note += "<br><br><b>Report: </b><a href='" + AntwebProps.getDomainApp() + "/query.do?name=speciesListWithRangeData&param=" + overview + "'>Species List with Range Data</a><br>";
      }
      if ((overview instanceof Subregion || overview instanceof Region) && LoginMgr.isAdmin(request)) {
          note += "<br><br><b>Report: </b><a href='" + AntwebProps.getDomainApp() + "/query.do?name=speciesListWithRangeData&param=" + overview + "'>Species List with Range Data</a>";
          note += "<br><b>&nbsp;&nbsp;&nbsp;(Admin only)</b> Use sparingly. Approximately 1 - 2 minutea for every 1K species - species count:" + overview.getSpeciesCount();
          note += "<br>";
      }
    %>

      <%= note %>
      <div class="page_divider taxonomic"></div>
<%  }

    if (overview instanceof Bioregion) {
      Bioregion headerBioregion = (Bioregion) overview;    

      note = Formatter.initCap(Rank.getPluralRank(pageRank)) + " in the Bioregion list are based on the following sources:" 
          + " a) specimen records of any species from the bioregion,"
          + " b) AntCat.org type locality information. Bioregion lists are not managed by curators."; 
%>
      <%= note %>
      <div class="page_divider taxonomic"></div>
<%  }
 
    if (overview instanceof Museum) {
      Museum headerMuseum = (Museum) overview;    

      note = Formatter.initCap(Rank.getPluralRank(pageRank)) + " in the Museum list are based on" 
          + " specimen records of any species from the museum as indicated by the 'Owned by' field. Museum lists are not managed by curators."
      ;
%>
      <%= note %>
      <div class="page_divider taxonomic"></div>
<%  }
    
    if (LoginMgr.isAdmin(request) && overview instanceof Project) {
      Project headerProject = (Project) overview;

      note = Formatter.initCap(Rank.getPluralRank(pageRank)) + " in the Project: " + headerProject.getTitle() + " are based on ";
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
%>
      <%= note %>
      <div class="page_divider taxonomic"></div>
<%  } 
}   
 %>

