package org.calacademy.antweb.search;

import java.util.*;
import org.calacademy.antweb.*;

public class GenusSpeciesItemComparator extends ResultItemComparator {
 
  public static final Comparator INSTANCE = new GenusSpeciesItemComparator();

  public static Comparator getInstance() {
    return INSTANCE;
  }

  public int compare(Object item1, Object item2) {
    Taxon t1 = (Taxon) item1;
    Taxon t2 = (Taxon) item2;
    //if (AntwebProps.isDevMode()) AntwebUtil.logStackTrace();
    String s1 = t1.getGenus() + t1.getSpecies() + t1.getSubspecies();
    String s2 = t2.getGenus() + t2.getSpecies() + t2.getSubspecies();
    return s1.compareTo(s2);
  }
}
