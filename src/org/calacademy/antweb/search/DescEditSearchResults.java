package org.calacademy.antweb.search;

import java.io.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.ArrayList;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class DescEditSearchResults extends GenericSearchResults
    implements Serializable {

    private static Log s_log = LogFactory.getLog(DescEditSearchResults.class);

    /*
    public void setResultsWithFilters(ArrayList filters) throws Exception {
        
        removeNullCodes();

        Iterator iter = filters.iterator();
        ArrayList tempSet = rset;
        while (iter.hasNext()) {
            tempSet = filter(tempSet, (String) iter.next());
        }
        rset = tempSet;
        removeNullCodes();
        setResults();
    }
    */
    
    public void setResults() { // throws Exception 

      if (rset == null) {
        return;
      }
      if (project == null) {
        project = "";
      }

      ArrayList myResults = new ArrayList();

     // try {
        
        String code = null;
        String taxonName = null;
        String shotType = null;
        String shotNumber = null;
        String uploadDate = null;
        String artist = null;
        String group = null;
        String genus = null;
        String species = null;
        SearchItem thisItem = null;
        int imageId = -1;

        for (Object o : rset) {
            //System.out.println("adding to result item " + thisItem.getShotType());
            thisItem = (SearchItem) o;
            myResults.add(makeNewItem(thisItem.getCode(), thisItem.getName(), thisItem.getShotType(), thisItem.getShotNumber(), thisItem.getArtist(),
                    thisItem.getGroup(), thisItem.getGenus(), thisItem.getSpecies(), thisItem.getImageId(), thisItem.getUploadDate()));
        }
      //} catch (Exception e) {
      //  s_log.error("setResults() e:" + e);
      //  throw new Exception("Formating problem");
      //}
    
      this.results = myResults;
    }

  protected ResultItem makeNewItem(
    String code,
    String name,
    String shotType,
    String shotNumber,
    String artist,
    String group,
    String genus,
    String species,
    int imageId,
    String uploadDate) {
      ResultItem item = new ResultItem();
      item.setName(name);
      item.setCode(code);
      item.setShotType(shotType);
      item.setShotNumber(shotNumber);
      item.setArtist(artist);
      item.setGroup(group);
      item.setGenus(genus);
      item.setSpecies(species);
      item.setImageId(imageId);
      item.setUploadDate(uploadDate);
      return item;
  }

  // code shot number name author institution upload date
  public void sortBy(String fieldName) {
    
    s_log.info("sorting descriptionEdits with field " + fieldName);
      switch (fieldName) {
          case "code":
              Collections.sort(results, new Comparator() {

                  public int compare(Object o1, Object o2) {
                      ResultItem p1 = (ResultItem) o1;
                      ResultItem p2 = (ResultItem) o2;
                      return p1.getCode().compareToIgnoreCase(p2.getCode());
                  }

              });

              break;
          case "shotType":
              Collections.sort(results, new Comparator() {

                  public int compare(Object o1, Object o2) {
                      ResultItem p1 = (ResultItem) o1;
                      ResultItem p2 = (ResultItem) o2;
                      return p1.getShotType().compareToIgnoreCase(p2.getShotType());
                  }

              });
              break;
          case "shotNumber":
              Collections.sort(results, new Comparator() {

                  public int compare(Object o1, Object o2) {
                      ResultItem p1 = (ResultItem) o1;
                      ResultItem p2 = (ResultItem) o2;
                      return p1.getShotNumber().compareToIgnoreCase(p2.getShotNumber());
                  }

              });
              break;
          case "name":
              Collections.sort(results, new Comparator() {

                  public int compare(Object o1, Object o2) {
                      ResultItem p1 = (ResultItem) o1;
                      ResultItem p2 = (ResultItem) o2;
                      String p1Name = p1.getGenus() + " " + p1.getSpecies();
                      String p2Name = p2.getGenus() + " " + p2.getSpecies();
                      return p1Name.compareToIgnoreCase(p2Name);
                  }

              });
              break;
          case "artist":
              Collections.sort(results, new Comparator() {

                  public int compare(Object o1, Object o2) {
                      ResultItem p1 = (ResultItem) o1;
                      ResultItem p2 = (ResultItem) o2;
                      return p1.getArtist().compareToIgnoreCase(p2.getArtist());
                  }

              });
              break;
          case "group":
              Collections.sort(results, new Comparator() {

                  public int compare(Object o1, Object o2) {
                      ResultItem p1 = (ResultItem) o1;
                      ResultItem p2 = (ResultItem) o2;
                      return p1.getGroup().compareToIgnoreCase(p2.getGroup());
                  }

              });
              break;
          case "uploadDate":
              Collections.sort(results, new Comparator() {

                  public int compare(Object o1, Object o2) {
                      ResultItem p1 = (ResultItem) o1;
                      ResultItem p2 = (ResultItem) o2;

                      return p2.getUploadDate().compareToIgnoreCase(p1.getUploadDate());
                  }

              });
              break;
      }
  }
}

