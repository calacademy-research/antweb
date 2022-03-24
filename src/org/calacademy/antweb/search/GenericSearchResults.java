package org.calacademy.antweb.search;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import java.io.*;
import java.sql.ResultSet;
import java.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/** The class holds the elements common to all beans that handle the results information
 * @author thau
 * @version 0.1
*/

/* there are a number of things wrong here 
 * first rather than store the search results in an array list, there 
 * probably should be an object which stores them
 * that object would then be able to return filtered versions of itself for 
 * processing.  Second, right now filtering is destructive - it destroys
 * the original dataset in this object.  So, there's no clever remembering
 * the object and reprocessing it.  Oh well.... maybe when there's more time...
 */
 
public class GenericSearchResults implements Serializable {

    private static Log s_log = LogFactory.getLog(GenericSearchResults.class);

    protected static int maxResultsToShow = 20;
    protected ArrayList<ResultItem> rset;
    protected ArrayList filters;
    protected String project;
    protected ArrayList<ResultItem> results;
    protected int startingPoint = 2;
    protected String name;
    protected String taxonName;
    
    public ArrayList<ResultItem> filter(ArrayList<ResultItem> dataset, String property) {
        ArrayList<ResultItem> newList = new ArrayList<>();
        ResultItem thisItem;
        if (dataset != null) {
            for (ResultItem resultItem : dataset) {
                thisItem = resultItem;
                if ("images".equals(property)) {
                    if (thisItem.isHasImages() || thisItem.getSynonym() != null && thisItem.getSynonym().isHasImages()) {
                        newList.add(thisItem);
                    }
                } else if ("types".equals(property) && thisItem.getType() != null && !thisItem.getType().equals("")) {
                    newList.add(thisItem);
                } else {
                    //System.out.println(thisItem.getName() + " filtered out with type: " + thisItem.getType());
                }
            }
        }
        return newList;
    }
    
    
    public Taxon getResultsAsTaxon() {
      Taxon dummyTaxon = new Taxon();      
      dummyTaxon.setRank("species");
      
      ArrayList<Taxon> taxonList = new ArrayList<>();
 
      //AntwebUtil.logFirstStackTrace();

      ArrayList<ResultItem> results = getResults();
      for (ResultItem thisItem : results) {
          Specimen dummySpecimen = new Specimen();
          dummySpecimen.setRank("specimen");
          
          dummySpecimen.setFamily(thisItem.getFamily());
          dummySpecimen.setSubfamily(thisItem.getSubfamily());
          dummySpecimen.setGenus(thisItem.getGenus());
          dummySpecimen.setSpecies(thisItem.getSpecies());
      
          String subspecies = thisItem.getSubspecies();
          if (subspecies != null && !"".equals(subspecies)) {
            dummySpecimen.setSubspecies(subspecies);
            dummyTaxon.setRank("subspecies");
          }
          dummySpecimen.setCode(thisItem.getCode());

          dummySpecimen.setStatus(thisItem.getStatus());
          //if (thisItem.getValid() == 1) dummySpecimen.setStatus("valid");
           
          //dummySpecimen.setPrettyName(thisItem.getFullName());
          dummySpecimen.setCountry(thisItem.getCountry());

          //dummySpecimen.setChildrenCount(1);
          dummySpecimen.setHasImages(thisItem.getHasImages());
          dummySpecimen.setImageCount(thisItem.getImageCount());
          // dummySpecimen.setHasImagesCount(thisItem.getImageCount());
          //A.log("getResultsAsTaxon() imageCount:" + dummySpecimen.getImageCount() + " hasImages:" + dummySpecimen.getHasImages() + " thisItem:" + thisItem.getImageCount());   
          
          dummySpecimen.setTypeStatus(thisItem.getType());
          dummySpecimen.setBrowserParams(thisItem.getPageParams());
          //dummySpecimen.setHasImagesCount(1);
          //dummySpecimen.setChildrenCount(1);
          //dummySpecimen.setPrettyName("");

          dummySpecimen.setIsFossil(thisItem.getIsFossil());
          dummySpecimen.setCollectionCode(thisItem.getCollectionCode());
          dummySpecimen.setLocalityCode(thisItem.getLocalityCode());
          dummySpecimen.setUploadDate(thisItem.getUploadDate());

          //AntwebUtil.log("getResultsAsTaxa CCode:" + thisItem.getCollectionCode() + " LCode:" + thisItem.getLocalityCode());    

          dummySpecimen.setAdm1(thisItem.getAdm1());
          dummySpecimen.setAdm2(thisItem.getAdm2());

          dummySpecimen.setLocalityName(thisItem.getLocalityName());
          
          // Ultimately, Specimen and the JSP, every but the database should use caste, subcaste and lifeStage.
          // To be done... ultimately we could change the column names too...
          dummySpecimen.setLifeStage(thisItem.getLifeStage());
          //A.log("GenericSearchResults dummy:" + dummySpecimen.getLifeStage() + " lifeStage:" + thisItem.getLifeStage());
          dummySpecimen.setCaste(thisItem.getCaste());
          dummySpecimen.setSubcaste(thisItem.getSubcaste());
          
          dummySpecimen.setMedium(thisItem.getMedium());
          dummySpecimen.setSpecimenNotes(thisItem.getSpecimenNotes());
          dummySpecimen.setHabitat(thisItem.getHabitat());
          dummySpecimen.setMicrohabitat(thisItem.getMicrohabitat());
          dummySpecimen.setMethod(thisItem.getMethod());
          dummySpecimen.setDnaExtractionNotes(thisItem.getDnaExtractionNotes());
          dummySpecimen.setDeterminedBy(thisItem.getDeterminedBy());
          dummySpecimen.setCollectedBy(thisItem.getCollectedBy());
          dummySpecimen.setMuseumCode(thisItem.getMuseumCode());
          dummySpecimen.setDecimalLatitude(thisItem.getDecimalLatitude());
          dummySpecimen.setDecimalLongitude(thisItem.getDecimalLongitude());
          dummySpecimen.setElevation(thisItem.getElevation());
          dummySpecimen.setDateCollectedStart(thisItem.getDateCollectedStart());

          //A.log("GenericSearchResults.getResultsAsTaxon() date:" + thisItem.getDateCollectedStart() + " dummy:" + dummySpecimen.getDateCollectedStart());        
  
  
          dummySpecimen.setGroupId(GroupMgr.getGroup(thisItem.getGroupName()).getId());

          //A.log("getResultsAsTaxon() child code:" + dummySpecimen.getCode() + " resultItem:" + thisItem.toDebugString());          
          
          dummySpecimen.setOwnedBy(thisItem.getOwnedBy());
          dummySpecimen.setLocatedAt(thisItem.getLocatedAt());
          dummySpecimen.setCreated(thisItem.getCreated());
          dummySpecimen.setBioregion(thisItem.getBioregion());
          dummySpecimen.setUploadId(thisItem.getUploadId());
          taxonList.add(dummySpecimen);  

          AntwebUtil.logFirst("GenericSearchResults.getResultsAsTaxon() code:" + dummySpecimen.getCode() + " resultItem:" + dummySpecimen.toString() + " adm1:" + dummySpecimen.getAdm1() + " adm2:" + dummySpecimen.getAdm2());                
      }
      dummyTaxon.setChildren(taxonList);
      
      // A.log("getResultsAsTaxon() code:" + dummyTaxon.getCode() + " resultItem:" + dummyTaxon.toString());          
      return dummyTaxon;
    }    
    
    public ArrayList<ResultItem> getSearchResults() {
      return getResults();
    }    
    
    public ArrayList<ResultItem> getResults() {
        if (results == null) results = new ArrayList<>();
        return results;
    }

    public void setResults(ArrayList<ResultItem> rset, String name, String project)
        throws Exception {
            
        this.rset = rset;
        this.name = name;
        this.project = project;
        setResults();
    }
    
    public void setResults(ArrayList<ResultItem> results) {
        this.results = results;
    }
    
    public void setResultsWithFilters(ArrayList<String> filters) {  // throws Exception
        Iterator<String> iter = filters.iterator();
        ArrayList<ResultItem> tempSet = rset;
        while (iter.hasNext()) {
            tempSet = filter(tempSet, (String) iter.next());
        }
        rset = tempSet;
        if (filters.contains("types")) {
            setResults();
        } else {        
            setResultsCollapse();
        }
    }

    public void setResults() {
    
        if (rset == null) {
            return;
        }
        if (project == null) {
            project = "";
        }

        ArrayList<ResultItem> myResults = new ArrayList<>();
        
            // figure out which column is which
            //ArrayList columns = getColumns(rset);
            ArrayList tracker = new ArrayList();
            Hashtable imageCheck = new Hashtable();
            Hashtable typeCheck = new Hashtable();

            ResultItem item;
            ArrayList rank = null;
            ResultItem synonym;


            Iterator<ResultItem> resIter = rset.iterator();
            ResultItem thisItem;
            while (resIter.hasNext()) {
                thisItem = (ResultItem) resIter.next();
                 
                String genus = thisItem.getGenus();
                String species = thisItem.getSpecies();
                String subspecies = thisItem.getSubspecies();
                
                String pageParams = "";
                
                item = new ResultItem();
                
                item.setCode(thisItem.getCode());
                item.setFamily(thisItem.getFamily());
                item.setSubfamily(thisItem.getSubfamily());
                item.setGenus(genus);
                item.setSpecies(species);
                                
                if (subspecies != null && !"".equals(subspecies)) {
                  item.setSubspecies(subspecies);
                  pageParams = "rank=subspecies&genus=" + genus + "&species=" + species + "&subspecies=" + subspecies;
                  item.setRank(Rank.SUBSPECIES);
                  item.setFullName(genus + " " + species + " " + subspecies);
                  item.setName(species + " " + subspecies);
                } else {
                  pageParams = "rank=species&genus=" + genus + "&species=" + species;
                  item.setRank(Rank.SPECIES);
                  item.setFullName(genus + " " + species);
                  item.setName(species);
                }
                if (project != null) {
                    pageParams += "&project=" + project;
                }
                item.setPageParams(pageParams);
 
                if (thisItem.getHasImages()) item.setHasImages(true);                
                item.setImageCount(thisItem.getImageCount());                
                item.setStatus(thisItem.getStatus());
                item.setType(thisItem.getType());
                
                item.setCountry(thisItem.getCountry());
                item.setAdm1(thisItem.getAdm1());
                item.setAdm2(thisItem.getAdm2());
                //A.log("setResults() adm1:" + item.getAdm1() + " adm2:" + item.getAdm2());
                item.setLocalityName(thisItem.getLocalityName());
                item.setLocalityCode(thisItem.getLocalityCode());
                item.setCollectionCode(thisItem.getCollectionCode());  

                item.setLifeStage(thisItem.getLifeStage());
                item.setCaste(thisItem.getCaste());
                item.setSubcaste(thisItem.getSubcaste());

                item.setMedium(thisItem.getMedium());
                item.setSpecimenNotes(thisItem.getSpecimenNotes());
                item.setArtist(thisItem.getArtist());
                item.setGroup(thisItem.getGroup());
                item.setShotType(thisItem.getShotType());
                item.setShotNumber(thisItem.getShotNumber());
                item.setUploadDate(thisItem.getUploadDate());
                item.setImageId(thisItem.getImageId());
                     
                //A.log("setResults() imageCount:" + thisItem.getImageCount() + " groupName:" + thisItem.getGroupName());   

                item.setHabitat(thisItem.getHabitat());
                item.setMicrohabitat(thisItem.getMicrohabitat());
                item.setMethod(thisItem.getMethod());
                item.setDnaExtractionNotes(thisItem.getDnaExtractionNotes());
                item.setDeterminedBy(thisItem.getDeterminedBy());
                item.setCollectedBy(thisItem.getCollectedBy());
                item.setMuseumCode(thisItem.getMuseumCode());
                item.setDateCollectedStart(thisItem.getDateCollectedStart());
                //item.setAccessGroup(thisItem.getAccessGroup());
                item.setGroupName(thisItem.getGroupName());
                item.setOwnedBy(thisItem.getOwnedBy());
                
                item.setLocatedAt(thisItem.getLocatedAt());

                item.setDecimalLatitude(thisItem.getDecimalLatitude());
                item.setDecimalLongitude(thisItem.getDecimalLongitude());
                item.setElevation(thisItem.getElevation());
                
                item.setMuseum(thisItem.getMuseum());
                item.setCreated(thisItem.getCreated());
                item.setBioregion(thisItem.getBioregion());
                
                item.setUploadId(thisItem.getUploadId());
                
                // Mark.  Jul 5th.  This was printing MANY lines in the logs with shotType null.
                //s_log.warn("shot type is: " + shotType);
                if (thisItem.getSynonym() != null) {
                    synonym = new ResultItem();
                    synonym.setFullName(thisItem.getSynonym().getName());
                    synonym.setName(thisItem.getSynonym().getGenus() +" " + thisItem.getSynonym().getSpecies());
                    if (thisItem.getSynonym().getCode() != null) {
                        synonym.setPageParams("name=" + thisItem.getSynonym().getCode());
                    } else {
                         synonym.setPageParams("");
                    }
                    item.setSynonym(synonym);
                }
                
                //A.log("setResults() resultItem:" + item.toDebugString());
                                
                myResults.add(item);
            }

        Collections.sort(myResults, new ResultItemComparator());
        this.results = myResults;
    }

    public void setStartingPoint(int startingPoint) {
        this.startingPoint = startingPoint;
    }

    public int getStartingPoint() {
        return startingPoint;
    }

    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
    
    public void setTaxonName(String name) {
        this.taxonName = name;
    }
    public String getTaxonName() {
        return taxonName;
    }

    protected void updateHash(Hashtable hash, String key, Boolean value) {
        if (!hash.containsKey(key)) {
            hash.put(key, value);
        } else if ((Boolean) hash.get(key) == false) {
            hash.put(key, value);
        }
    }

/*    
    protected void finalize() throws Throwable {
        super.finalize();
        ResultItem theItem = null;
        if (results != null) {
            Iterator iter = results.iterator();
            while (iter.hasNext()) {
                theItem = (ResultItem) iter.next();
                theItem.clear();
            }
            results.clear();
        }
    }
*/
    public ArrayList<ResultItem> getRset() {
        return rset;
    }
    public void setRset(ArrayList<ResultItem> list) {
        rset = list;
    }

    public String getProject() {
        return project;
    }
    public void setProject(String string) {
        project = string;
    }

    public ArrayList getFilters() {
        return filters;
    }
    public void setFilters(ArrayList list) {
        filters = list;
    }

    public void setResultsCollapse() { //throws Exception 
        s_log.warn("setResultsCollapse() This code is SHakY!");
        if (rset == null) {
            return;
        }
        if (project == null) {
            project = "";
        }

        ArrayList<ResultItem> myResults = new ArrayList<>();

		ArrayList<String> tracker = new ArrayList<>();
		Hashtable<String, Boolean> imageCheck = new Hashtable<>();
		Hashtable<String, Boolean> typeCheck = new Hashtable<>();

		ArrayList rank;
		String thisRank;
		String pageParams;
		boolean hasImages = false;
		boolean hasTypes = false;
		String family;
		String subfamily;
		String genus;
		String species;
		String subspecies = null;
		String code;
		String type;
		Iterator rankIterator;
		ResultItem item;
		String typeOriginalCombination = null;
		String fullName;
				
		String tempCombo;
		String combo;
		Iterator resIter = rset.iterator();
		SearchItem thisItem;
		ResultItem synonym;
		String country;
		String adm1;
		String localityName;
		String itemName;
		String lifeStage;
		String caste;
		String subcaste;
		String medium;
		String specimenNotes;
		String artist;
		String group;
		String shotType = null;
		String shotNumber = null;
		String uploadDate = null;
		int imageId = -1;
				
		while (resIter.hasNext()) {
			thisItem = (SearchItem) resIter.next();
			rank = new ArrayList();
			thisRank = null;
			pageParams = null;
			hasImages = false;
			hasTypes = false;

			family = thisItem.getFamily();
			subfamily = thisItem.getSubfamily();
			genus = thisItem.getGenus();
			species = thisItem.getSpecies();
			species = thisItem.getSubspecies();
			code = thisItem.getCode();
			country=thisItem.getCountry();
			adm1=thisItem.getAdm1();
			//A.log("setResultsCollapse() adm1:" + adm1);
			localityName=thisItem.getLocalityName();
			typeOriginalCombination = thisItem.getTypeOriginalCombination();
			lifeStage = thisItem.getLifeStage();
			caste = thisItem.getCaste();
			subcaste = thisItem.getSubcaste();
			medium = thisItem.getMedium();
			specimenNotes = thisItem.getSpecimenNotes();
			artist = thisItem.getArtist();
			group = thisItem.getGroup();
			hasImages = thisItem.isHasImages();
			type = thisItem.getType();

					
			rank = Rank.getRankList(name, subfamily, genus, species, subspecies);
			rankIterator = rank.iterator();
			while (rankIterator.hasNext()) {
					
					item = new ResultItem();

					thisRank = (String) rankIterator.next();
					if (type != null && !"".equals(type)) {
						hasTypes = true;
					}

					// the craziness of the below is made more
					// crazy because Brian wants the species to appear when a search
					// matches on genus
					//
					fullName = null;
					tempCombo = null;
					
					if (thisRank.equals("species")) {
						fullName = genus + " " + species;
						itemName = species;
						pageParams =
							"rank=" + thisRank
								+ "&genus=" + genus
								+ "&species=" + species
								+ "&project=" + project;

					}  // ACCIDENTAL? Should be else? Who wrote this? Not me. - Mark
					if (thisRank.equals("subspecies")) {
						fullName = genus + " " + species + " " + subspecies;
						itemName = subspecies;
						pageParams =
							"rank=" + thisRank
						  + "&genus=" + genus
						  + "&species=" + species
						  + "&subspecies=" + subspecies
						  + "&project=" + project;
					} else if (thisRank.equals("family")) {
						fullName = family;
						itemName = family;
						pageParams = "rank=family&family=" + family + "&project=" + project;
					} else if (thisRank.equals("subfamily")) {
						fullName = subfamily;
						itemName = subfamily;
						pageParams = "rank=subfamily&subfamily=" + subfamily + "&project=" + project;

					// if this is a genus and not a binomial pair
					} else if (thisRank.equals("genus") && (species == null || species.length() == 0)) {
						fullName = genus;
						itemName = genus;
						pageParams =
							"rank=genus&genus=" + genus 
							+ "&project=" + project;
					
// WhAt? What is a binomial pair anyway?
						
					// if this is a genus and it's part of a binomial pair
					} else if (thisRank.equals("genus")) {
						
						thisRank = "species";
						fullName = genus + " " + species;
						itemName = species;
						pageParams =
							"rank=" + thisRank
						  + "&genus=" + genus
						  + "&species=" + species
						  + "&project=" + project;
						tempCombo = "genus:" + genus;
						// this here deals with the special genus case
						
						updateHash(imageCheck, tempCombo, hasImages);
						updateHash(typeCheck, tempCombo, hasTypes);
						
						if (!tracker.contains(tempCombo)
							&& fullName.length() > 2) {
							item = makeNewItem(
								genus, genus, family, subfamily, genus,
								null,  // this was species, but I think it should be null 
								null,  // subspecies
								"genus", code, type,
								"rank=genus&genus=" + genus + "&project=" + project,
								country, adm1, localityName, lifeStage, caste, subcaste, 
								medium, specimenNotes, artist, group,
								shotType, shotNumber, uploadDate, imageId);
							myResults.add(item);
							tracker.add(tempCombo);
						}
					// I suppose this should be for a specimen
					} else {
						fullName = "species";
						itemName = "species";
						// not sure about the above lines - it's really weird, I wonder if 
						// it's ever called - I hope not!
						pageParams =
							"rank="
								+ thisRank
								+ "&name="
								+ fullName
								+ "&project="
								+ project;
					}

					combo = thisRank + ":" + fullName;

					updateHash(imageCheck, combo, hasImages);
					updateHash(typeCheck, combo, hasTypes);

					if (!tracker.contains(combo)
						&& fullName.length() > 2) {
						item =
							makeNewItem(
								itemName, fullName, family, subfamily, genus, species, subspecies,
								thisRank, code, type, pageParams, country, adm1, localityName,
								lifeStage, caste, subcaste, 
								medium, specimenNotes, artist, group, 
								shotType, shotNumber, uploadDate, imageId);
						if (thisItem.getSynonym() != null) {
							synonym = new ResultItem();
							synonym.setFullName(
								thisItem.getSynonym().getName());
							synonym.setName(thisItem.getSynonym().getGenus() +" " + thisItem.getSynonym().getSpecies());
							synonym.setPageParams(
								"name=" + thisItem.getSynonym().getCode());
							item.setSynonym(synonym);
						}
						
						myResults.add(item);
						tracker.add(combo);
					}
				}
			}  // end while

		// set the types and images correctly
		// imageCheck has the items which have images
		// typeCheck has the items which have types
		// now go through the items and set the images and types appropriately

		Iterator iterator = myResults.iterator();
		ResultItem resItem;
		String thisCombo;
		while (iterator.hasNext()) {
			resItem = (ResultItem) iterator.next();
		   thisCombo = resItem.getRank() + ":" + resItem.getFullName();

            resItem.setHasImages((Boolean) imageCheck.get(thisCombo)
                    == true);

            resItem.setTypes((Boolean) typeCheck.get(thisCombo)
                    == true);
		}

        Collections.sort(myResults, new ResultItemComparator());
        this.results = myResults;
    }
            
    protected ResultItem makeNewItem(
        String name, String fullName, String family, String subfamily, String genus, String species, String subspecies
        , String thisRank, String code, String type, String pageParams, String country
        , String adm1, String localityName, String lifeStage, String caste, String subcaste
        , String medium, String specimenNotes, String artist, String group
        , String shotType, String shotNumber, String uploadDate, int imageId) {
            
            
        ResultItem item = new ResultItem();
        item.setName(name);
        item.setFullName(fullName);
        item.setRank(thisRank);
        item.setCode(code);
        item.setType(type);
        item.setFullName(fullName);
        item.setPageParams(pageParams);
        item.setCountry(country);
        item.setAdm1(adm1);
        item.setLocalityName(localityName);
        item.setFamily(family);
        item.setSubfamily(subfamily);
        item.setGenus(genus);   
        item.setSpecies(species);
        item.setSpecies(subspecies);

        //A.log("makeNewItem() adm1:" + adm1);
        
        item.setLifeStage(lifeStage);
        item.setCaste(caste);
        item.setSubcaste(subcaste);
        item.setMedium(medium);
        item.setSpecimenNotes(specimenNotes);
        item.setArtist(artist);
        item.setGroup(group);
        item.setShotType(shotType);
        item.setShotNumber(shotNumber);
        item.setUploadDate(uploadDate);
        item.setImageId(imageId);
        return item;
    }

    // pulls the things which don't have codes out of the rset
    // this is for advanced search and searches with types only
    // this is only a problem when someone types in a synonym
    // and there is no type for that synonym in the specimen table
    protected void removeNullCodes() {
        if (rset == null) return;  // Mark Feb, 2013
        Iterator rsetIter = rset.iterator();
        ResultItem resItem;
        while (rsetIter.hasNext()) {
            resItem = (ResultItem) rsetIter.next();
            if (resItem.getCode() == null) {
                rsetIter.remove();
            }
        }
    }
        
    public ArrayList<String> getSpecimens() {            
        if (rset == null || rset.size() < 1) {
            return null;
        }
            
        ArrayList<String> specimens = new ArrayList();
        Iterator iter = rset.iterator();
        //Utility util = new Utility();
        SearchItem thisResult;
        while (iter.hasNext()) {
            thisResult = (SearchItem) iter.next();
            if (Utility.notBlank(thisResult.getCode())) {
               specimens.add(thisResult.getCode());
            }
        }
        return specimens;
    }
}
