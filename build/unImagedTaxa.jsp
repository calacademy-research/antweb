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
		unImagedList += "<br><b>Non-imaged " + pluralUnImagedRank + " (" + unImagedTaxa.size() + "):</b> ";
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
