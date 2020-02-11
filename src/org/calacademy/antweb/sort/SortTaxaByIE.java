package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;

public class SortTaxaByIE implements Comparator<Taxon> 
{ 
    String bioregion = null;

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
