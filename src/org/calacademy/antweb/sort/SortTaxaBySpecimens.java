package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public class SortTaxaBySpecimens implements Comparator<Taxon> 
{
    public int compare(Taxon a, Taxon b) 
    { 
      TaxonSet aS = SortKeys.set(a), bS = SortKeys.set(b);
      return SortKeys.countDesc(aS == null ? null : aS.getSpecimenCount(), bS == null ? null : bS.getSpecimenCount());
    } 
} 