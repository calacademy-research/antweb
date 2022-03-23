package org.calacademy.antweb.search;

import org.calacademy.antweb.*;

import java.io.*;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public class AdvancedSearchResults extends GenericSearchResults
	implements Serializable {

    private static Log s_log = LogFactory.getLog(AdvancedSearchResults.class);

	public void setResultsWithFilters(ArrayList<String> filters) {

		removeNullCodes();
		ArrayList tempSet = rset;
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
			ResultItem thisItem = (ResultItem) subfamilyHash.get(thisKey);
			subfamilyList.add(thisItem);
		}
		Collections.sort(subfamilyList, new Comparator(){			 
            public int compare(Object o1, Object o2) {
              ResultItem p1 = (ResultItem) o1;
              ResultItem p2 = (ResultItem) o2;
              return p1.getFullName().compareToIgnoreCase(p2.getFullName());
            }
 
        });
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
			thisKey = (String) keyIter.next();
			thisItem = (ResultItem) genusHash.get(thisKey);
			genusList.add(thisItem);
		}
		Collections.sort(genusList, new Comparator(){			 
            public int compare(Object o1, Object o2) {
              ResultItem p1 = (ResultItem) o1;
              ResultItem p2 = (ResultItem) o2;
              return p1.getFullName().compareToIgnoreCase(p2.getFullName());
            }
 
        });
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
            String fullName = thisItem.getFullName();	
            // if (AntwebProps.isDevMode()) if (fullName.contains("rotundata")) s_log.warn("getSpeciesList() fullName:" + fullName);			
			specHash.put(fullName,thisItem);
		}

        // Add one resultItem per species fullName to the specList.
		Set<String> keys = specHash.keySet();
		Iterator<String> keyIter = keys.iterator();
		String thisKey;
		while (keyIter.hasNext()) {
			thisKey = (String) keyIter.next();
			thisItem = (ResultItem) specHash.get(thisKey);
			specList.add(thisItem);
		}
        // Sort it
		Collections.sort(specList, new Comparator() {			 
            public int compare(Object o1, Object o2) {
            	ResultItem p1 = (ResultItem) o1;
            	ResultItem p2 = (ResultItem) o2;
               return p1.getFullName().compareToIgnoreCase(p2.getFullName());
            }
        });

        s_log.debug("getSpeciesList() specList.size():" + specList.size() + " from " + results.size());
		return specList;
	}


	public static int s_count = 0;
	public static String s_fieldName = null;
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
				  Collections.sort(results, new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  return CompareUtil.compareString(((ResultItem) o1).getCode(), ((ResultItem) o2).getCode());
					  }
				  });
				  break;
			  case "country":
				  Collections.sort(results, new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  return CompareUtil.compareString(((ResultItem) o1).getCountry(), ((ResultItem) o2).getCountry());
					  }
				  });
				  break;
			  case "images":
				  Collections.sort(results, new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  // 2nd one first because reverse order
						  return CompareUtil.compareIntNoZero(((ResultItem) o2).getImageCount(), ((ResultItem) o1).getImageCount());
					  }
				  });
				  break;
			  case "location":
				  Collections.sort(results, new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  return compareVals(((ResultItem) o1).getLocalityString(), ((ResultItem) o2).getLocalityString());
					  }
				  });
				  break;
			  case "type":
				  Collections.sort(results, new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  a1 = ((ResultItem) o1).getType();
						  a2 = ((ResultItem) o2).getType();
						  // 2nd one first because reverse order
						  Integer returnVal = compareVals(a2, a1);
						  return returnVal;
					  }
				  });

				  // caste atually sorts by caste+subcaste
				  break;
			  case "caste":
				  Collections.sort(results, new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  ResultItem resultItemO1 = (ResultItem) o1;
						  ResultItem resultItemO2 = (ResultItem) o2;
						  String casteSubcasteO1 = resultItemO1.getCaste() + resultItemO1.getSubcaste();
						  String casteSubcasteO2 = resultItemO2.getCaste() + resultItemO1.getSubcaste();
						  // 2nd one first because reverse order
						  return CompareUtil.compareString(casteSubcasteO2, casteSubcasteO1);
					  }
				  });
				  break;
			  case "lifestage":
				  s_fieldName = "lifeStage";
				  Collections.sort(results, new Comparator() {
					  public int compare(Object o1, Object o2) {
						  //A.log("compare(lifestage) o1:" + o1 + " o2:" + o2);
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  // 2nd one first because reverse order
						  return CompareUtil.compareString(((ResultItem) o2).getLifeStage(), ((ResultItem) o1).getLifeStage());
					  }
				  });
				  break;
			  case "medium":
				  Collections.sort(results, new Comparator() {
					  public int compare(Object o1, Object o2) {
						  if ("down".equals(sortOrder)) {
							  Object t = o1;
							  o1 = o2;
							  o2 = t;
						  }
						  a1 = ((ResultItem) o1).getMedium();
						  a2 = ((ResultItem) o2).getMedium();

						  Integer returnVal = compareVals(a1, a2);
						  return returnVal;
					  }
				  });
				  break;
			  case "specimennotes":
				  Collections.sort(results, new Comparator() {
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
				  Collections.sort(results, new Comparator() {
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
				  Collections.sort(results, new Comparator() {
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
				  Collections.sort(results, new Comparator() {
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
				  Collections.sort(results, new Comparator() {
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
				  Collections.sort(results, new Comparator() {
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
				  Collections.sort(results, new Comparator() {
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
				  Collections.sort(results, new Comparator() {
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
				  Collections.sort(results, new Comparator() {
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
				  Collections.sort(results, new Comparator() {
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
				  Collections.sort(results, new Comparator() {
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
				  Collections.sort(results, new Comparator() {
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
				  Collections.sort(results, new Comparator() {
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
				  Collections.sort(results, new Comparator() {
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
				  Collections.sort(results, new Comparator() {
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
				  Collections.sort(results, new Comparator() {
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
				  Collections.sort(results, new Comparator() {
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
				  Collections.sort(results, new Comparator() {
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

