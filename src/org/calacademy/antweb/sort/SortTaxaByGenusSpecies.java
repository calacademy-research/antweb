package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

/**
 * Comparator of Taxon.sortTaxa for orderBy=taxonName: genus, then species of the row's name. Taxon.sortTaxa calls
 * it when a list is sorted by a column heading: the list rows (taxonReportBody.jsp: taxonomicPage.do, browse.do),
 * the images view (taxonChildImages.jsp: images.do), comparisons and field guides.
 */
public class SortTaxaByGenusSpecies implements Comparator<Taxon> 
{
    public int compare(Taxon a, Taxon b) 
    { 
      try {
        String aTaxonName = a.getTaxonSet().getTaxonName();
        String aGenusSpecies = Taxon.getGenusFromName(aTaxonName) + Taxon.getSpeciesFromName(aTaxonName);
        String bTaxonName = b.getTaxonSet().getTaxonName();
        String bGenusSpecies = Taxon.getGenusFromName(bTaxonName) + Taxon.getSpeciesFromName(bTaxonName);
        int compareInt = aGenusSpecies.compareTo(bGenusSpecies);

        //A.log("compare() a.GenusSpecies:" + aGenusSpecies + " bGenusSpecies:" + bGenusSpecies + " compareInt:" + compareInt);
        return compareInt;
      } catch (NullPointerException e) {
        A.log("compare() NPE a:" + a + " b:" + b);
      }
      return 0;
    } 
} 
