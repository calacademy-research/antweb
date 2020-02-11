package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

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
