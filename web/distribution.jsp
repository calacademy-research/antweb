
<%      
  // Distribution
    ArrayList<Country> countries = taxon.getCountries();

    //A.log("taxonPage-body.jsp geolocales:" + geolocales);
    
    String distributionHtml = "<h3>Distribution:</h3>";
    if (countries.isEmpty()) { 
      distributionHtml += "&nbsp;&nbsp;<b>Geographic regions:</b> Not found on any curated Geolocale/Taxon lists.";
    } else {
      distributionHtml += "&nbsp;&nbsp;<b>Geographic regions</b> (According to curated Geolocale/Taxon lists)<b>:</b>";
	  
      int i = 0;
      String lastRegion = null;
      String regionHtml = null;
      //A.log("BioregionsStr:" + taxon.getBioregionsStr() );     

      for (Geolocale aGeolocale : countries) {
        String region = aGeolocale.getRegion();
        //A.log("taxonPage-body.jsp region:" + region + " geolocale:" + aGeolocale.getName() + " overview:" + overview);
        if (region == null) {
          //A.log("taxonPage-body.jsp bioregion:" + bioregion + "-");
          continue;
        }
        if (region.equals(lastRegion)) {
          ++i;
          if (i > 0) regionHtml += ", ";
        } else {
          //A.log("taxonPage-body.jsp bioregion:" + bioregion + " lastRegion:" + lastRegion);
          //A.log("taxonPage-body.jsp regionHtml:" + regionHtml);
          if (lastRegion != null && regionHtml != null) distributionHtml += regionHtml;
          i = 0; 
          regionHtml = "<br>&nbsp;&nbsp;&nbsp;&nbsp;<font color=DarkBlue>" + region + ":&nbsp;</font>";
          lastRegion = region;
        }
        //A.log("taxonPage-body.jsp aGeolocale:" + aGeolocale + " distributionHtml:" + distributionHtml);        
        if (overview.equals(aGeolocale.getName())) { 
          regionHtml += "<font color=green>" + aGeolocale.getName() + "</font>";
        } else {
          regionHtml += "<a href='" + AntwebProps.getDomainApp() + "/taxonomicPage.do?rank=species&" + aGeolocale.getParams() + "'>" + aGeolocale.getName() + "</a>";
        }
      }
      if (lastRegion != null && regionHtml != null) distributionHtml += regionHtml;
      //A.log("taxonPage-body.jsp end distributionHtml:" + distributionHtml);
    }
    
    ArrayList<Bioregion> bioregions = taxon.getBioregions();
    if (bioregions != null && !bioregions.isEmpty()) {
      distributionHtml += "<br>&nbsp;&nbsp;<b>Biogeographic regions</b> (According to curated Bioregion/Taxon lists)<b>:</b>";    
      String bioregionHtml = "";
      int i = 0;
      for (Bioregion aBioregion : bioregions) {
        String bioregion = aBioregion.getTitle();
        ++i;
        if (i <= 1) {
          bioregionHtml = "<br>&nbsp;&nbsp;&nbsp;&nbsp;";
        } else { 
          bioregionHtml += ", ";
        }
        String bioregionRank = taxon.getRank();
        if ("subspecies".equals(bioregionRank)) bioregionRank = "species";
        bioregionHtml += "<a href='" + AntwebProps.getDomainApp() + "/taxonomicPage.do?rank=" + bioregionRank + "&bioregionName=" + aBioregion.getName() + "'>" + aBioregion + "</a>";
      }
      distributionHtml += bioregionHtml;
    }    

    if (Rank.SPECIES.equals(taxon.getRank())) {
      String nativeStr = taxon.getNativeStr();
      if (nativeStr != null && !"".equals(nativeStr)) {
        distributionHtml += "<br>&nbsp;&nbsp;<b>Native to</b> (according to species list records)<b>:</b><br>";
        distributionHtml += "&nbsp;&nbsp;&nbsp;&nbsp;" + nativeStr;
      }
    }
    if (Rank.GENUS.equals(taxon.getRank())) {
      ArrayList<Bioregion> nativeBioregions = taxon.getNativeBioregions();
      if (!nativeBioregions.isEmpty()) {
        distributionHtml += "<br>&nbsp;&nbsp;<b>Native biogeographic regions</b> (according to species list records)<b>:</b><br>&nbsp;&nbsp;&nbsp;&nbsp;";
        int i = 0;
        String bioregionsHtml = "";
        for (Bioregion bioregion : nativeBioregions) { 
          ++i;
          if (i > 1)  bioregionsHtml += ", ";
          bioregionsHtml += "<a href='" + AntwebProps.getDomainApp() + "/taxonomicPage.do?rank=" + taxon.getRank() + "&bioregionName=" + bioregion.getName() + "'>" + bioregion + "</a>";
        }
        distributionHtml += bioregionsHtml;
      }
    }
    //A.log("taxonPage-body.jsp B distributionHtml:" + distributionHtml);
              
  
    if (!"<h3>Distribution:</h3>".equals(distributionHtml)) {
      //A.log("taxonPage-body.jsp distributionHtml:" + distributionHtml + "-");
      out.println(distributionHtml);
    } else {
      A.log("taxonPage-body.jsp no distribution");
    }    

 %>