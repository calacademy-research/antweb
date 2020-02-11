package org.calacademy.antweb.search;

import java.util.*;
import org.calacademy.antweb.util.*;

public class ResultItemComparator implements Comparator {
 
  public static final Comparator INSTANCE = new ResultItemComparator();

  public static Comparator getInstance() {
    return INSTANCE;
  }

  public int compare(Object item1, Object item2) {
    //if (AntwebProps.isDevMode()) AntwebUtil.logStackTrace();
    String s1 = item1.toString().toLowerCase();
    String s2 = item2.toString().toLowerCase();
    return s1.compareTo(s2);
  }
}
