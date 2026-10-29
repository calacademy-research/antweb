package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public class SortTaxaByStatus implements Comparator<Taxon> 
{ 
    public int compare(Taxon a, Taxon b) 
    { 
      return SortKeys.textDesc(a == null ? null : a.getStatus(), b == null ? null : b.getStatus());   // valid first, as before
    } 
} 
