package org.calacademy.antweb;

import java.util.*;

public class TaxonComparator implements Comparator {
 
  public static final Comparator INSTANCE = new TaxonComparator();

  public static Comparator getInstance() {
    return INSTANCE;
  }

  public int compare(Object taxon1, Object taxon2) {
    String s1 = taxon1.toString();
    String s2 = taxon2.toString();
    return s1.compareTo(s2);
  }
}
