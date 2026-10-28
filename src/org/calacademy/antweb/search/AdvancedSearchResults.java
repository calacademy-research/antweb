package org.calacademy.antweb.search;

import org.calacademy.antweb.*;

import java.io.*;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Set;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.PlaceTotals;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public class AdvancedSearchResults extends GenericSearchResults
	implements Serializable {

    private static final Log s_log = LogFactory.getLog(AdvancedSearchResults.class);

	public void setResultsWithFilters(ArrayList<String> filters) {

		removeNullCodes();
		ArrayList<ResultItem> tempSet = rset;
        for (String filter : filters) {
			tempSet = filter(tempSet, filter);
        }
		rset = tempSet;
		removeNullCodes();
		setResults();
	}

	public ArrayList<ResultItem> getSubfamilyList() {
		
		ArrayList<ResultItem> subfamilyList = new ArrayList<>();
		HashMap<String, ResultItem> subfamilyHash = new HashMap<>();
		
        for (ResultItem thisItem : getResults()) {
            String subfamily = thisItem.getSubfamily();
            if (Subfamily.isValidAntSubfamily(subfamily)) {
    			subfamilyHash.put(subfamily, thisItem);
	        }
		}
		Set<String> keys = subfamilyHash.keySet();
        for (String thisKey : keys) {
			ResultItem thisItem = subfamilyHash.get(thisKey);
			subfamilyList.add(thisItem);
		}
		subfamilyList.sort((o1, o2) -> o1.getFullName().compareToIgnoreCase(o2.getFullName()));
		return subfamilyList;
	}
	
	public ArrayList<ResultItem> getGenusList() {
		
		ArrayList<ResultItem> genusList = new ArrayList<>();
		HashMap<String, ResultItem> genusHash = new HashMap<>();
		
		Iterator<ResultItem> resIter = results.iterator();
		
		ResultItem thisItem;
		while (resIter.hasNext()) {
			thisItem = resIter.next();
			genusHash.put(thisItem.getGenus(), thisItem);
		}
		Set<String> keys = genusHash.keySet();
		Iterator<String> keyIter = keys.iterator();
		String thisKey;
		while (keyIter.hasNext()) {
			thisKey = keyIter.next();
			thisItem = genusHash.get(thisKey);
			genusList.add(thisItem);
		}
		genusList.sort((o1, o2) -> o1.getFullName().compareToIgnoreCase(o2.getFullName()));
		return genusList;
	}
	 
	public ArrayList<ResultItem> getSpeciesList() {
		
		ArrayList<ResultItem> specList = new ArrayList<>();
		HashMap<String, ResultItem> specHash = new HashMap<>();
		
		Iterator<ResultItem> resIter = results.iterator();
		
		// Go through all of the results (specimens) and put the species fullName/resultItem in specHash
		ResultItem thisItem;
		while (resIter.hasNext()) {
			thisItem = resIter.next();
            // B54 (Brian Fisher, 2026-10-22): a name not identified to genus is not a species; its specimens stay in
            // the search (and in its specimen total), they are counted by getSpecimensNotIdentifiedToGenus().
            if (!countsAsSpecies(thisItem)) continue;
            String fullName = thisItem.getFullName();	
			specHash.put(fullName,thisItem);
		}

        // Add one resultItem per species fullName to the specList.
		Set<String> keys = specHash.keySet();
		Iterator<String> keyIter = keys.iterator();
		String thisKey;
		while (keyIter.hasNext()) {
			thisKey = keyIter.next();
			thisItem = specHash.get(thisKey);
			specList.add(thisItem);
		}
        // Sort it
		specList.sort((o1, o2) -> o1.getFullName().compareToIgnoreCase(o2.getFullName()));

        s_log.debug("getSpeciesList() specList.size():" + specList.size() + " from " + results.size());
		return specList;
	}


	/**
	 * B54, 2026-10-27: the species list of a search by species (and of its Compare, Map and field guide, which keep
	 * this list in the session as taxonList and choose from it by index) leaves out names not identified to genus,
	 * the rule of the place pages (PlaceTotals.isIdentifiedToGenus).
	 */
	public static boolean countsAsSpecies(ResultItem item) {
		return item != null && PlaceTotals.isIdentifiedToGenus(item.getGenus());
	}

	/** The specimens of the search whose name is not identified to genus (not counted as species, B54). */
	public int getSpecimensNotIdentifiedToGenus() {
		int count = 0;
		if (results == null) return 0;
		for (ResultItem item : results) {
			if (item != null && !countsAsSpecies(item)) ++count;
		}
		return count;
	}

	/**
	 * The query string of the same search listed by specimen (B54: the link of "N specimens not identified to genus").
	 * params: the request parameters of the search; the form tokens, resultRank, output and page are dropped.
	 */
	public static String specimenViewQuery(java.util.Map<String, String[]> params) {
		StringBuilder q = new StringBuilder();
		if (params != null) {
			java.util.TreeMap<String, String[]> sorted = new java.util.TreeMap<>(params);
			for (java.util.Map.Entry<String, String[]> e : sorted.entrySet()) {
				String key = e.getKey();
				if (key == null || key.isEmpty()) continue;
				if ("resultRank".equals(key) || "output".equals(key) || "page".equals(key)
					|| Csrf.PARAM.equals(key) || key.startsWith("org.apache.struts.")) continue;
				if (e.getValue() == null) continue;
				for (String v : e.getValue()) {
					if (q.length() > 0) q.append('&');
					q.append(java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8)).append('=')
					 .append(java.net.URLEncoder.encode(v == null ? "" : v, java.nio.charset.StandardCharsets.UTF_8));
				}
			}
		}
		if (q.length() > 0) q.append('&');
		q.append("resultRank=specimen");
		return q.toString();
	}

	public static int s_count = 0;
	public static String s_fieldName;
	public static String a1 = "";
	public static String a2 = "";

	// This is for debugging. Might as well just call CompareUtil.compareString();
	static Integer compareVals(String a1, String a2) {
		++s_count;

		int returnVal = CompareUtil.compareString(a1, a2);

		s_log.debug("compareVals() fieldname:" + s_fieldName + " returnVal:" + returnVal + " a1:" + a1 + " a2:" + a2 + " count:" + s_count);

		return returnVal;
	}

	public void sortBy(String fieldName) {
		sortBy(fieldName, "up");
	}

	public void sortBy(String fieldName, String sortOrder) {  // sortOrder is up or down.

      //if (AntwebProps.isDevMode()) AntwebUtil.logStackTrace();

      s_count = 0;
      s_fieldName = fieldName;
      s_log.debug("sortBy() fieldName:" + fieldName + " sortOrder:" + sortOrder);

      try {
		  switch (fieldName) {
			  case "code":
				  results.sort((o1, o2) -> {
					  if ("down".equals(sortOrder)) {
						  ResultItem t = o1;
						  o1 = o2;
						  o2 = t;
					  }
					  return CompareUtil.compareString(o1.getCode(), o2.getCode());
				  });
				  break;
			  case "country":
				  results.sort((o1, o2) -> {
					  if ("down".equals(sortOrder)) {
						  ResultItem t = o1;
						  o1 = o2;
						  o2 = t;
					  }
					  return CompareUtil.compareString(o1.getCountry(), o2.getCountry());
				  });
				  break;
			  case "images":
				  results.sort((o1, o2) -> {
					  if ("down".equals(sortOrder)) {
						  ResultItem t = o1;
						  o1 = o2;
						  o2 = t;
					  }
					  // 2nd one first because reverse order
					  return CompareUtil.compareIntNoZero(o2.getImageCount(), o1.getImageCount());
				  });
				  break;
			  case "location":
				  results.sort((o1, o2) -> {
					  if ("down".equals(sortOrder)) {
						  ResultItem t = o1;
						  o1 = o2;
						  o2 = t;
					  }
					  return compareVals(o1.getLocalityString(), o2.getLocalityString());
				  });
				  break;
			  case "type":
				  results.sort((o1, o2) -> {
					  if ("down".equals(sortOrder)) {
						  ResultItem t = o1;
						  o1 = o2;
						  o2 = t;
					  }
					  // 2nd one first because reverse order (local values: the static a1, a2 were shared by every request)
					  return compareVals(o2.getType(), o1.getType());
				  });

				  // caste atually sorts by caste+subcaste
				  break;
			  case "caste":
				  results.sort((o1, o2) -> {
					  if ("down".equals(sortOrder)) {
						  ResultItem t = o1;
						  o1 = o2;
						  o2 = t;
					  }
					  ResultItem resultItemO1 = o1;
					  ResultItem resultItemO2 = o2;
					  String casteSubcasteO1 = resultItemO1.getCaste() + resultItemO1.getSubcaste();
					  String casteSubcasteO2 = resultItemO2.getCaste() + resultItemO2.getSubcaste();  // was O1's subcaste
					  // 2nd one first because reverse order
					  return CompareUtil.compareString(casteSubcasteO2, casteSubcasteO1);
				  });
				  break;
			  case "lifestage":
				  s_fieldName = "lifeStage";
				  results.sort((o1, o2) -> {
					  //A.log("compare(lifestage) o1:" + o1 + " o2:" + o2);
					  if ("down".equals(sortOrder)) {
						  ResultItem t = o1;
						  o1 = o2;
						  o2 = t;
					  }
					  // 2nd one first because reverse order
					  return CompareUtil.compareString(o2.getLifeStage(), o1.getLifeStage());
				  });
				  break;
			  case "medium":
				  results.sort((o1, o2) -> {
					  if ("down".equals(sortOrder)) {
						  ResultItem t = o1;
						  o1 = o2;
						  o2 = t;
					  }
					  a1 = o1.getMedium();
					  a2 = o2.getMedium();

					  return compareVals(a1, a2);
				  });
				  break;
			  case "specimennotes":
				  results.sort(new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  // 2nd one first because reverse order.
						  return CompareUtil.compareString(((ResultItem) o2).getSpecimenNotes(), ((ResultItem) o1).getSpecimenNotes());
					  }
				  });
				  break;
			  case "taxonname":
				  results.sort(new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  return CompareUtil.compareString(((ResultItem) o1).getFullName(), ((ResultItem) o2).getFullName());
					  }
				  });
				  break;
			  case "ownedby":
				  results.sort(new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  return CompareUtil.compareString(((ResultItem) o1).getOwnedBy(), ((ResultItem) o2).getOwnedBy());
					  }
				  });
				  break;
			  case "habitat":
				  results.sort(new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  // 2nd one first because reverse order.
						  return CompareUtil.compareString(((ResultItem) o2).getHabitat(), ((ResultItem) o1).getHabitat());
					  }
				  });
				  break;
			  case "microhabitat":
				  results.sort(new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  // 2nd one first because reverse order.
						  return CompareUtil.compareString(((ResultItem) o2).getMicrohabitat(), ((ResultItem) o1).getMicrohabitat());
					  }
				  });
				  break;
			  case "collectedby":
				  results.sort(new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  return CompareUtil.compareString(((ResultItem) o1).getCollectedBy(), ((ResultItem) o2).getCollectedBy());
					  }
				  });
				  break;
			  case "museumCode":
			  case "museum":
				  results.sort(new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  return CompareUtil.compareString(((ResultItem) o1).getMuseumCode(), ((ResultItem) o2).getMuseumCode());
					  }
				  });
				  break;
			  case "method":
				  results.sort(new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  return CompareUtil.compareString(((ResultItem) o1).getMethod(), ((ResultItem) o2).getMethod());
					  }
				  });
				  break;
			  case "dna":
				  results.sort(new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  // 2nd one first because reverse order.
						  return CompareUtil.compareString(((ResultItem) o2).getDnaExtractionNotes(), ((ResultItem) o1).getDnaExtractionNotes());
					  }
				  });
				  break;
			  case "determinedby":
				  results.sort(new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  return CompareUtil.compareString(((ResultItem) o1).getDeterminedBy(), ((ResultItem) o2).getDeterminedBy());
					  }
				  });
				  break;
			  case "databy":
				  results.sort(new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  return CompareUtil.compareString(((ResultItem) o1).getGroupName(), ((ResultItem) o2).getGroupName());
					  }
				  });
				  break;
			  case "datecollected":    // These could be backwards to sort backwards
				  results.sort(new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  return CompareUtil.compareStringDesc(((ResultItem) o1).getDateCollectedStart(), ((ResultItem) o2).getDateCollectedStart());
					  }
				  });
				  break;
			  case "collection":
				  results.sort(new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  return CompareUtil.compareString(((ResultItem) o1).getCollectionCode(), ((ResultItem) o2).getCollectionCode());
					  }
				  });
				  break;
			  case "elevation":
				  results.sort(new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  // 2nd one first because reverse order.
						  return CompareUtil.compareIntString(((ResultItem) o2).getElevation(), ((ResultItem) o1).getElevation());
					  }
				  });
				  break;
			  case "latitude":
				  results.sort(new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  // 2nd one first because reverse order.
						  return CompareUtil.compareFloat(((ResultItem) o2).getDecimalLatitude(), ((ResultItem) o1).getDecimalLatitude());
					  }
				  });
				  break;
			  case "longitude":
				  results.sort(new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  // 2nd one first because reverse order.
						  return CompareUtil.compareFloat(((ResultItem) o2).getDecimalLongitude(), ((ResultItem) o1).getDecimalLongitude());
					  }
				  });
				  break;
			  case "created":    // These could be backwards to sort backwards
				  results.sort(new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  return CompareUtil.compareStringDesc(((ResultItem) o1).getCreated(), ((ResultItem) o2).getCreated());
					  }
				  });
				  break;
			  case "uploadid":
				  results.sort(new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  return CompareUtil.compareInt(((ResultItem) o1).getUploadId(), ((ResultItem) o2).getUploadId());
					  }
				  });
				  break;
		  }
	  } catch (IllegalArgumentException e) {
		  s_log.warn("sortBy() fieldName:" + s_fieldName + " count:" + s_count + " a1:" + a1 + " a2:" + a2 + " e:" + e);
		  s_count = 0;
	  }
	}
}

