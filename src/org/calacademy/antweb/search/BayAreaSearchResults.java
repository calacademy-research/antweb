package org.calacademy.antweb.search;

import java.io.*;
import java.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class BayAreaSearchResults
    extends GenericSearchResults
	//extends AdvancedSearchResults
	implements Serializable {

    private static Log s_log = LogFactory.getLog(BayAreaSearchResults.class);

	public void setResults() { // throws Exception

		if (rset == null) {
			return;
		}

		ArrayList myResults = new ArrayList();
		HashMap theNames = new HashMap();

		String subfamily = null;
		String genus = null;
		String species = null;
		String adm2 = null;
		String fullName = null;
		String thisRank = null;
		String name = null;
		String pageParams = null;
		boolean images = false;
		ResultItem item = null;
		ResultItem thisItem = null;
		String thisAdm2 = null;
		SearchItem thisSearchItem = null;

		for (Object o : rset) {
			thisSearchItem = (SearchItem) o;
			subfamily = thisSearchItem.getSubfamily();
			genus = thisSearchItem.getGenus();
			species = thisSearchItem.getSpecies();
			adm2 = thisSearchItem.getAdm2();
			//s_log.info("setResults() adm2:" + adm2);  //  why?  Standard.
			fullName = thisSearchItem.getName();
			images = thisSearchItem.isHasImages();
			thisRank = "species";

			name = genus + " " + species;
			pageParams = "rank=" + thisRank + "&genus=" + genus + "&name=" + species;

			if (theNames.containsKey(fullName)) {
				thisItem = (ResultItem) theNames.get(fullName);
				thisAdm2 = thisItem.getAdm2();   // was county
				if (thisAdm2 != null && !thisAdm2.contains(adm2)) {
					thisItem.setAdm2(thisAdm2 + ", " + adm2);
				} else {
					s_log.info("thisAdm2:" + thisAdm2);
				}
			} else {
				item = makeNewItem(fullName, name, thisRank, subfamily, genus,
						species, adm2, pageParams, images);
				myResults.add(item);
				theNames.put(fullName, item);
			}
		}
		Collections.sort(myResults, new ResultItemComparator());
		this.results = myResults;
	}

	protected ResultItem makeNewItem (String fullName, String name, String thisRank, String subfamily
	  , String genus, String species, String adm2, String pageParams, boolean images) {
		
		ResultItem item = new ResultItem();
		item.setName(name);
		item.setFullName(fullName);
		item.setRank(thisRank);
		item.setAdm2(adm2);
		item.setSubfamily(subfamily);
		item.setGenus(genus);
		item.setSpecies(species);
		item.setPageParams(pageParams);
		item.setHasImages(images);

		return item;
	}
}
