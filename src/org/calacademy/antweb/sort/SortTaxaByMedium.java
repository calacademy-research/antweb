package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public class SortTaxaByMedium implements Comparator<Taxon> 
{ 
    public int compare(Taxon a, Taxon b) 
    { 
      Specimen a1 = SortKeys.specimen(a), b1 = SortKeys.specimen(b);
      return SortKeys.textAsc(a1 == null ? null : a1.getMedium(), b1 == null ? null : b1.getMedium());
    } 
} 
