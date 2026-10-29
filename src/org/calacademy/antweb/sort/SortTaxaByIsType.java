package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public class SortTaxaByIsType implements Comparator<Taxon> 
{ 
    public int compare(Taxon a, Taxon b) 
    { 
      boolean aT = a != null && a.getIsType();
      boolean bT = b != null && b.getIsType();
      return Boolean.compare(bT, aT);   // types first
    } 
} 
