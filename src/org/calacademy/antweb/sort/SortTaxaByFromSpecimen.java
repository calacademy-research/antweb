package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

/**
 * Comparator of Taxon.sortTaxa for orderBy=fromSpecimen: the names not on the place's list (known only from
 * specimens, the yellow ant mark) first, then the names on the list, each by genus and species. Taxon.sortTaxa
 * calls it when a list is sorted by a column heading: the list rows (taxonReportBody.jsp: taxonomicPage.do,
 * browse.do), the images view (taxonChildImages.jsp: images.do), comparisons and field guides.
 */
public class SortTaxaByFromSpecimen implements Comparator<Taxon> 
{
    public int compare(Taxon a, Taxon b) 
    { 
      try {
        boolean aExists = a.getTaxonSet().exists();
        String aTaxonName = a.getTaxonSet().getTaxonName();
        String aGenusSpecies = "" + aExists + Taxon.getGenusFromName(aTaxonName) + Taxon.getSpeciesFromName(aTaxonName);
        boolean bExists = b.getTaxonSet().exists();
        String bTaxonName = b.getTaxonSet().getTaxonName();
        String bGenusSpecies = "" + bExists + Taxon.getGenusFromName(bTaxonName) + Taxon.getSpeciesFromName(bTaxonName);
        int compareInt = aGenusSpecies.compareTo(bGenusSpecies);

        //A.log("compare() a.GenusSpecies:" + aGenusSpecies + " bGenusSpecies:" + bGenusSpecies + " compareInt:" + compareInt);
        return compareInt;
      } catch (NullPointerException e) {
        A.log("compare() NPE a:" + a + " b:" + b);
      }
      return 0;
    } 
} 
