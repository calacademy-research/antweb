package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

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
