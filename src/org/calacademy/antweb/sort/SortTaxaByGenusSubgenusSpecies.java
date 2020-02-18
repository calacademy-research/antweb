package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public class SortTaxaByGenusSubgenusSpecies implements Comparator<Taxon>
{
    public int compare(Taxon a, Taxon b) 
    { 
      try {
        String aTaxonName = a.getTaxonSet().getTaxonName();
        String aSubgenus = TaxonMgr.getSubgenus(aTaxonName);
        if (aSubgenus == null) aSubgenus = " ";
        String aGenusSubgenusSpecies = Taxon.getGenusFromName(aTaxonName) + aSubgenus + Taxon.getSpeciesFromName(aTaxonName);
        String bTaxonName = b.getTaxonSet().getTaxonName();
        String bSubgenus = TaxonMgr.getSubgenus(bTaxonName);
        if (bSubgenus == null) bSubgenus = " ";
        String bGenusSubgenusSpecies = Taxon.getGenusFromName(bTaxonName) + bSubgenus + Taxon.getSpeciesFromName(bTaxonName);
        int compareInt = aGenusSubgenusSpecies.compareTo(bGenusSubgenusSpecies);

        //A.log("compare() a.GenusSubgenusSpecies:" + aGenusSubgenusSpecies + " bGenusSubgenusSpecies:" + bGenusSubgenusSpecies + " compareInt:" + compareInt);
        return compareInt;
      } catch (NullPointerException e) {
        A.log("compare() NPE a:" + a + " b:" + b);
      }
      return 0;
    } 
} 
