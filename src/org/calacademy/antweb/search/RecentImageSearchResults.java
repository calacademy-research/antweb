package org.calacademy.antweb.search;

import java.io.*;
import java.util.Comparator;
import java.util.ArrayList;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class RecentImageSearchResults extends GenericSearchResults
    implements Serializable {

    private static final Log s_log = LogFactory.getLog(RecentImageSearchResults.class);

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

    public void setResults() {
      if (rset == null) {
        return;
      }
      if (project == null) {
        project = "";
      }
      ArrayList<ResultItem> myResults = new ArrayList<>();
      ResultItem resultItem;

        //for (SearchItem searchItem : rset) {
        for (ResultItem o : rset) {
            //System.out.println("adding to result item " + thisItem.getShotType());
            resultItem = o;
        /*
        ResultItem resultItem = makeNewItem(
            resultItem.getCode(), resultItem.getName(), resultItem.getShotType()
          , resultItem.getShotNumber(), resultItem.getArtist()
          //, resultItem.getGroup()
          , resultItem.getGroupName()
          , resultItem.getGenus()
          , resultItem.getSpecies(), resultItem.getSubspecies()
          , resultItem.getImageId(), resultItem.getUploadDate()
        );
        */
            //A.log("setResults() resultItem:" + resultItem.toDebugString() + " resultItem:" + resultItem.toDebugString());
            myResults.add(resultItem);
        }
      this.results = myResults;
    }

/*
  protected ResultItem makeNewItem(
      String code, String name, String shotType, String shotNumber, String artist
    , String group, String genus, String species, String subspecies, int imageId
    , String uploadDate) {
      ResultItem item = new ResultItem();
      item.setName(name);
      item.setCode(code);
      item.setShotType(shotType);
      item.setShotNumber(shotNumber);
      item.setArtist(artist);
      item.setGroup(group);
      item.setGenus(genus);
      item.setSpecies(species);
      item.setSubspecies(subspecies);
      item.setImageId(imageId);
      item.setUploadDate(uploadDate);
      return item;
  }
*/

  // code shot number name author institution upload date
  public void sortBy(String fieldName) {
    
    s_log.info("sortBy() fieldName:" + fieldName);
      switch (fieldName) {
          case "code":
              results.sort(new Comparator() {

                  public int compare(Object o1, Object o2) {
                      ResultItem p1 = (ResultItem) o1;
                      ResultItem p2 = (ResultItem) o2;
                      return p1.getCode().compareToIgnoreCase(p2.getCode());
                  }

              });

              break;
          case "shotType":
              results.sort(new Comparator() {

                  public int compare(Object o1, Object o2) {
                      ResultItem p1 = (ResultItem) o1;
                      ResultItem p2 = (ResultItem) o2;
                      return p1.getShotType().compareToIgnoreCase(p2.getShotType());
                  }

              });
              break;
          case "shotNumber":
              results.sort(new Comparator() {

                  public int compare(Object o1, Object o2) {
                      ResultItem p1 = (ResultItem) o1;
                      ResultItem p2 = (ResultItem) o2;
                      return p1.getShotNumber().compareToIgnoreCase(p2.getShotNumber());
                  }

              });
              break;
          case "name":
              results.sort(new Comparator() {

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
              results.sort(new Comparator() {

                  public int compare(Object o1, Object o2) {
                      ResultItem p1 = (ResultItem) o1;
                      ResultItem p2 = (ResultItem) o2;
                      return p1.getArtist().compareToIgnoreCase(p2.getArtist());
                  }

              });
              break;
          case "group":
              results.sort(new Comparator() {

                  public int compare(Object o1, Object o2) {
                      ResultItem p1 = (ResultItem) o1;
                      ResultItem p2 = (ResultItem) o2;
                      return p1.getGroup().compareToIgnoreCase(p2.getGroup());
                  }

              });
              break;
          case "uploadDate":
              results.sort(new Comparator() {

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

