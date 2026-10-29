package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public class SortTaxaBySource implements Comparator<Taxon> 
{ 
    public int compare(Taxon a, Taxon b) 
    { 
      TaxonSet aS = SortKeys.set(a), bS = SortKeys.set(b);
      return SortKeys.textAsc(aS == null ? null : Source.getSourceDisplay(aS.getSource()), bS == null ? null : Source.getSourceDisplay(bS.getSource()));
    } 
} 
