<!-- unImagedTaxa.jsp -->

<%
    ArrayList<Taxon> unImagedTaxa = new ArrayList<Taxon>();

    for (Taxon thisChild : childrenList) {
      if (!thisChild.getHasImages()) {
        unImagedTaxa.add(thisChild);
      }
    }

	int unImagedCount = 0;
    String unImagedList = "";
    String etcList = "";
    if (unImagedTaxa.size() > 0) {
      String unImagedRank = unImagedTaxa.get(0).getRank();
      if (!Rank.SPECIMEN.equals(unImagedRank)) {
		String pluralUnImagedRank = Rank.getPluralRank(unImagedRank).toLowerCase();
		//AntwebUtil.log("taxonomicPageImages-body.jsp unImagedTaxa.size:" + unImagedTaxa.size() + " unImagedTaxa:" + unImagedTaxa);
		// The pictures of a name come from its best specimen anywhere in AntWeb (Taxon.setImages), so a name listed here
		// has no picture in AntWeb at all (Brian Fisher, 2026-10-27, finding B45, option A: "with no image from this
		// place" said otherwise; a local picture for a list is chosen with the image picker).
		unImagedList += "<br><b>" + org.calacademy.antweb.Formatter.initCap(pluralUnImagedRank) + " with no picture in AntWeb (" + unImagedTaxa.size() + "):</b> ";
		for (Taxon unImagedTaxon : unImagedTaxa) {      
		  ++unImagedCount;
		  String prettyTaxonName = Taxon.getPrettyTaxonName(unImagedTaxon.getTaxonName());
          boolean isTaxonomicPage = HttpUtil.getTarget(request).contains("taxonomicPage");
		  if (unImagedTaxon.isSpeciesOrSubspecies() && !isTaxonomicPage) {
		    prettyTaxonName = unImagedTaxon.getSpeciesSubspecies();
		  }
          if (unImagedCount > 50) {
            break;
          } else {
  	 	    if (unImagedCount > 1) unImagedList += ", ";
            String item = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + unImagedTaxon.getTaxonName() + "'>" + prettyTaxonName + "</a>";
            unImagedList += item;
          }
		}
      }
    }
    out.print(unImagedList);
    if (unImagedCount > 50) out.println(", ...");

%>
