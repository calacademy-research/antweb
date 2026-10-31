package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;

/**
 * Comparator of Taxon.sortTaxa for orderBy=ie. Each name gets the key "ei" (endemic and introduced), "e", "i" or "";
 * the keys are compared in descending order, so the rows come as: introduced only, endemic and introduced, endemic,
 * then the others (introduced only above endemic: probably not what was meant, unchanged here). Endemic is the row's flag; introduced means the name's introduced map does not count the place's
 * bioregion as native (TaxonPropMgr). The place's bioregion comes from the bioregion or geolocale given to the
 * constructor. Taxon.sortTaxa calls it when a list is sorted by a column heading: the list rows
 * (taxonReportBody.jsp: taxonomicPage.do, browse.do), the images view (taxonChildImages.jsp: images.do),
 * comparisons and field guides.
 */
public class SortTaxaByIE implements Comparator<Taxon> 
{ 
    String bioregion;

    public SortTaxaByIE(Overview overview) {	
        if (overview instanceof Bioregion) {
		  bioregion = overview.getName();
		}  
		if (overview instanceof Geolocale) {
		  bioregion = ((Geolocale) overview).getBioregion(); 
		  //altBioregion = ((Geolocale) overview).getAltBioregion();
		}		
	}
    public int compare(Taxon a, Taxon b) {
      try {

		boolean aIsIntroduced = false;
		boolean bIsIntroduced = false;
		String aIntroducedMap = a.getIntroducedMap();
		String bIntroducedMap = b.getIntroducedMap();
		if (aIntroducedMap != null) {
		  aIsIntroduced = !TaxonPropMgr.isBioregionNative(bioregion, aIntroducedMap);  
		}
		if (bIntroducedMap != null) {
		  bIsIntroduced = !TaxonPropMgr.isBioregionNative(bioregion, bIntroducedMap);  
		}

        boolean aIsEndemic = a.getTaxonSet().getIsEndemic();
        boolean bIsEndemic = b.getTaxonSet().getIsEndemic();

        String aStr = "";
        String bStr = "";
        if (aIsEndemic) aStr += "e";
        if (bIsEndemic) bStr += "e";
        if (aIsIntroduced) aStr += "i";
        if (bIsIntroduced) bStr += "i";
        int compareInt = bStr.compareTo(aStr);

        return compareInt;
      } catch (NullPointerException e) {
        A.log("compare() NPE a:" + a + " b:" + b);
      }
      return 0;
    } 
} 
