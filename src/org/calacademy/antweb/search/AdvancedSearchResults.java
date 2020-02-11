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

import org.apache.batik.dom.util.HashTable;

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
		
		ArrayList<ResultItem> subfamilyList = new ArrayList<ResultItem>();
		HashMap<String, ResultItem> subfamilyHash = new HashMap<String, ResultItem>();
		
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
		
		ArrayList<ResultItem> genusList = new ArrayList<ResultItem>();
		HashMap<String, ResultItem> genusHash = new HashMap<String, ResultItem>();
		
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
		
		ArrayList<ResultItem> specList = new ArrayList<ResultItem>();
		HashMap<String, ResultItem> specHash = new HashMap<String, ResultItem>();
		
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

        A.log("getSpeciesList() specList.size():" + specList.size() + " from " + results.size());		
		return specList;
	}
	
	
	public void sortBy(String fieldName) {
	     
		if (fieldName.equals("code")) {
			Collections.sort(results, new Comparator(){
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getCode(), ((ResultItem) o2).getCode());	
	            }
	        });
		} else if (fieldName.equals("country")) {
			Collections.sort(results, new Comparator(){			 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getCountry(), ((ResultItem) o2).getCountry());
	            }
	        });
		} else if (fieldName.equals("images")) {
			Collections.sort(results, new Comparator(){			 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareIntNoZero(((ResultItem) o1).getImageCount(), ((ResultItem) o2).getImageCount());
	            }
	        });
		} else if (fieldName.equals("locality")) {
			Collections.sort(results, new Comparator(){				 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getLocalityString(), ((ResultItem) o2).getLocalityString());	
	            }
	        });
		} else if (fieldName.equals("type")) {
			Collections.sort(results, new Comparator(){
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getType(), ((ResultItem) o2).getType());	
	            }
	        });
		} else if (fieldName.equals("lifeStage")) {
			Collections.sort(results, new Comparator(){				 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getLifeStage(), ((ResultItem) o2).getLifeStage());	
	            }	 
	        });
		} else if (fieldName.equals("caste")) {
			Collections.sort(results, new Comparator(){				 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getCaste(), ((ResultItem) o2).getCaste());	
	            }	 
	        });
		} else if (fieldName.equals("subcaste")) {
			Collections.sort(results, new Comparator(){				 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getSubcaste(), ((ResultItem) o2).getSubcaste());	
	            }	 
	        });
		} else if (fieldName.equals("medium")) {
			Collections.sort(results, new Comparator(){				 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getMedium(), ((ResultItem) o2).getMedium());	
	            }	 
	        });
		} else if (fieldName.equals("specimennotes")) {
			Collections.sort(results, new Comparator(){				 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getSpecimenNotes(), ((ResultItem) o2).getSpecimenNotes());	
	            }	 
	        });
		} else if (fieldName.equals("name")) {
			Collections.sort(results, new Comparator(){				 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getFullName(), ((ResultItem) o2).getFullName());	
	            }
	        });
		} else if (fieldName.equals("ownedby")) {
			Collections.sort(results, new Comparator(){				 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getOwnedBy(), ((ResultItem) o2).getOwnedBy());	            
	            }
	        });
		} else if (fieldName.equals("habitat")) {
			Collections.sort(results, new Comparator(){
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getHabitat(), ((ResultItem) o2).getHabitat());	            
	            }
	        });
		} else if (fieldName.equals("microhabitat")) {
			Collections.sort(results, new Comparator(){				 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getMicrohabitat(), ((ResultItem) o2).getMicrohabitat());	            
	            }
	        });
		} else if (fieldName.equals("collectedby")) {
			Collections.sort(results, new Comparator(){
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getCollectedBy(), ((ResultItem) o2).getCollectedBy());   
	            }
	        });
		} else if (fieldName.equals("museumCode") || fieldName.equals("museum")) {
			Collections.sort(results, new Comparator(){
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getMuseumCode(), ((ResultItem) o2).getMuseumCode());   
	            }
	        });		} else if (fieldName.equals("method")) {
			Collections.sort(results, new Comparator(){				 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getMethod(), ((ResultItem) o2).getMethod());	            
	            }
	        });
		} else if (fieldName.equals("dna")) {
			Collections.sort(results, new Comparator(){				 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getDnaExtractionNotes(), ((ResultItem) o2).getDnaExtractionNotes());	            
	            }
	        });
		} else if (fieldName.equals("determinedby")) {
			Collections.sort(results, new Comparator(){				 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getDeterminedBy(), ((ResultItem) o2).getDeterminedBy());	            
	            }
	        });
		} else if (fieldName.equals("databy")) {
			Collections.sort(results, new Comparator(){			 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getGroupName(), ((ResultItem) o2).getGroupName());
	            }
	        });
		} else if (fieldName.equals("datecollected")) {   // These could be backwards to sort backwards
			Collections.sort(results, new Comparator(){				 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareStringDesc(((ResultItem) o1).getDateCollectedStart(), ((ResultItem) o2).getDateCollectedStart());
	            }
	        });
		} else if (fieldName.equals("collection")) {
			Collections.sort(results, new Comparator(){				 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareString(((ResultItem) o1).getCollectionCode(), ((ResultItem) o2).getCollectionCode());
	            }
	        });       
		} else if (fieldName.equals("elevation")) {
			Collections.sort(results, new Comparator(){		 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareIntString(((ResultItem) o1).getElevation(), ((ResultItem) o2).getElevation());
	            }
	        });
		} else if (fieldName.equals("latitude")) {
			Collections.sort(results, new Comparator() {				 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareFloat(((ResultItem) o1).getDecimalLatitude(), ((ResultItem) o2).getDecimalLatitude());
	            }
	        });
		} else if (fieldName.equals("longitude")) {
			Collections.sort(results, new Comparator(){		 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareFloat(((ResultItem) o1).getDecimalLongitude(), ((ResultItem) o2).getDecimalLongitude());
	            }
	        });	      

		} else if (fieldName.equals("created")) {   // These could be backwards to sort backwards
			Collections.sort(results, new Comparator(){				 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareStringDesc(((ResultItem) o1).getCreated(), ((ResultItem) o2).getCreated());
	            }
	        });
		} else if (fieldName.equals("uploadid")) {
			Collections.sort(results, new Comparator(){		 
	            public int compare(Object o1, Object o2) {
	                return CompareUtil.compareInt(((ResultItem) o1).getUploadId(), ((ResultItem) o2).getUploadId());
	            }
	        });
	        	        
		}
	}  
}

