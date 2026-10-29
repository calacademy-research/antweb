package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public class SortTaxaByImages implements Comparator<Taxon> 
{
    public int compare(Taxon a, Taxon b) 
    { 
      // the number the Images cell shows (Brian Fisher 2026-10-28: the images of the name in AntWeb)
      TaxonSet aS = SortKeys.set(a), bS = SortKeys.set(b);
      return SortKeys.countDesc(aS == null ? null : ImagesAnywhere.shownImages(aS), bS == null ? null : ImagesAnywhere.shownImages(bS));
    } 
} 
