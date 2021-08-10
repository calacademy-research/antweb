package org.calacademy.antweb.search;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import java.io.*;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.ArrayList;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class RecentImageSearchResults extends GenericSearchResults
    implements Serializable {

    private static Log s_log = LogFactory.getLog(RecentImageSearchResults.class);

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
      ArrayList<ResultItem> myResults = new ArrayList();
      ResultItem resultItem = null;

        //for (SearchItem searchItem : rset) {
        for (Object o : rset) {
            //System.out.println("adding to result item " + thisItem.getShotType());
            resultItem = (ResultItem) o;
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
    if (fieldName.equals("code")) {
        Collections.sort(results, new Comparator(){
             
            public int compare(Object o1, Object o2) {
                ResultItem p1 = (ResultItem) o1;
                ResultItem p2 = (ResultItem) o2;
               return p1.getCode().compareToIgnoreCase(p2.getCode());
            }
 
        });

    } else if (fieldName.equals("shotType")) {
        Collections.sort(results, new Comparator(){
             
            public int compare(Object o1, Object o2) {
                ResultItem p1 = (ResultItem) o1;
                ResultItem p2 = (ResultItem) o2;
               return p1.getShotType().compareToIgnoreCase(p2.getShotType());
            }
 
        });
    } else if (fieldName.equals("shotNumber")) {
        Collections.sort(results, new Comparator(){
             
            public int compare(Object o1, Object o2) {
                ResultItem p1 = (ResultItem) o1;
                ResultItem p2 = (ResultItem) o2;
               return p1.getShotNumber().compareToIgnoreCase(p2.getShotNumber());
            }
 
        });
    } else if (fieldName.equals("name")) {
        Collections.sort(results, new Comparator(){
             
            public int compare(Object o1, Object o2) {
                ResultItem p1 = (ResultItem) o1;
                ResultItem p2 = (ResultItem) o2;
                String p1Name = p1.getGenus() + " " + p1.getSpecies();
                String p2Name = p2.getGenus() + " " + p2.getSpecies();
               return p1Name.compareToIgnoreCase(p2Name);
            }
 
        });
    } else if (fieldName.equals("artist")) {
        Collections.sort(results, new Comparator(){
             
            public int compare(Object o1, Object o2) {
                ResultItem p1 = (ResultItem) o1;
                ResultItem p2 = (ResultItem) o2;
               return p1.getArtist().compareToIgnoreCase(p2.getArtist());
            }
 
        });
    } else if (fieldName.equals("group")) {
        Collections.sort(results, new Comparator(){
             
            public int compare(Object o1, Object o2) {
                ResultItem p1 = (ResultItem) o1;
                ResultItem p2 = (ResultItem) o2;
               return p1.getGroup().compareToIgnoreCase(p2.getGroup());
            }
 
        });
    } else if (fieldName.equals("uploadDate")) {
        Collections.sort(results, new Comparator(){
             
            public int compare(Object o1, Object o2) {
                ResultItem p1 = (ResultItem) o1;
                ResultItem p2 = (ResultItem) o2;
                
               return p2.getUploadDate().compareToIgnoreCase(p1.getUploadDate());
            }
 
        });
    }
  }
}

