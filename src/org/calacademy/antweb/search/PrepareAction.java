package org.calacademy.antweb.search;

import org.apache.struts.action.Action;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public class PrepareAction extends Action {

    private static final Log s_log = LogFactory.getLog(PrepareAction.class);

/*
The PrepareAction classes (for CompareResults, MapResults and FieldGuideResults) creates
a set of results that can then be further filtered with user input, via checkboxes.

Each PrepareAction class behaves differently.
  PrepareCompareResults for specimens is intuitive, but for species level, we take the
    list of taxon names for the qualifying specimens and go and to the database to get
    the list of species that has images
    * Does not yet support genus level search
    
  MapCompareResults for specimens is intuitive, but for species level, and genus level,
    probably more work, after testing.  hasGeoRef() seems to take the first record
    (a specimen) for testing.  Perhaps this test isn't necessary at all, as good odds
    are that all species have a geo-refed specimen.  Perhaps loop through results for one?
    
  FieldGuideResults
    Results are not winowed by georeferencing or isImaged, so a little simpler.      



Outstanding questions:
  How important is genus and subfamily query functionality?
    The field guide sample in particular was genus level.
    
    

*/

 }
