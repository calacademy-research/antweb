package org.calacademy.antweb; 

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;

/** Class Species keeps track of the information about a specific taxon */
public class Species extends Genus implements Serializable {

    private static Log s_log = LogFactory.getLog(Species.class);

    public String getNextRank() {
        return "Specimens";
    }
    
    public String getName() { 
        return getSpecies(); 
    }

/*
   Stenamma punctatoventre_cf1
   Stenamma punctatoventre_cf2
   Stenamma punctatoventre_cf3

so maybe the see also should find: any taxa with the same species name 
(that will get all supspecies and quadrinomials) and  those that include 
the species name + and "_"  (that will get all the _cf, _nr, and any of 
these other _cf1 etc.
*/

    public String getSeeAlsoCfAndNr(Connection connection) throws SQLException {
      Statement stmt = null;
      ResultSet rset = null;
      try {
        String cfAndNrTaxaLinks = "";
        String notCfOrNrTaxonName = getTaxonName();
        if (getTaxonName().indexOf("_") > 0) notCfOrNrTaxonName = getTaxonName().substring(0, getTaxonName().indexOf("_"));
        String cfTaxonName = notCfOrNrTaxonName + "_cf%";
        String nrTaxonName = notCfOrNrTaxonName + "_nr%";
        String notCfOrNrTaxonNameCondition = "";
        if (getTaxonName().contains("_cf") || getTaxonName().contains("_nr")) {
            notCfOrNrTaxonNameCondition = " or taxon.taxon_name = '" + notCfOrNrTaxonName + "'";
        }                
        String query = "select taxon.taxon_name " 
            + " from taxon " 
            + " where taxon.taxon_name like '" + cfTaxonName + "'"
            + "    or taxon.taxon_name like '" + nrTaxonName + "'"
            + notCfOrNrTaxonNameCondition
          ;
        stmt = DBUtil.getStatement(connection, "getSeeAlsoCfAndNr()");
        rset = stmt.executeQuery(query);
        //A.log("getSeeAlsoCfAndNr() query:" + query);
        while (rset.next()) {
            String taxonName = rset.getString("taxon.taxon_name");
            if (taxonName.equals(getTaxonName())) continue;
            if (!"".equals(cfAndNrTaxaLinks)) {
              cfAndNrTaxaLinks += ", ";
            }
            cfAndNrTaxaLinks += "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.displayTaxonName(taxonName) + "</a>";
        }
        //A.log("getSeeAlsoCfAndNr() cfAndNrTaxaLinks:" + cfAndNrTaxaLinks);        
        return cfAndNrTaxaLinks;
      } finally {
        DBUtil.close(stmt, rset, this, "getSeeAlsoCfAndNr()");
      }
    }
    
    public String getSeeAlsoSiblingSubspecies(Connection connection) throws SQLException {
      // Used by SetSeeAlso()
      String siblingSubspecies = "";
      Statement stmt = null;
      ResultSet rset = null;
      try {
        String query = "select taxon.taxon_name " 
            + " from taxon " 
            + " where taxon.subfamily = '" + getSubfamily() + "'"
            + "   and taxon.genus = '" + getGenus() + "'"
            + "   and taxon.species = '" + getSpecies() + "'"
            + "   and taxon.taxarank = 'subspecies'"
            + "   and taxon.status = 'valid'"
          ;
        stmt = DBUtil.getStatement(connection, "getSeeAlsoSiblingSubspecies()");
        rset = stmt.executeQuery(query);
        while (rset.next()) {
            String taxonName = rset.getString("taxon.taxon_name");
            if (!"".equals(siblingSubspecies)) {
              siblingSubspecies += ", ";
            }
            siblingSubspecies += "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.displayTaxonName(taxonName) + "</a>";
        }
      } finally {
          DBUtil.close(stmt, rset, this, "getSeeAlsoSiblingSubspecies()");
      }
      //A.log("getSiblingSubspecies() siblingSubspecies:" + siblingSubspecies);
      return siblingSubspecies;
    }

    public void setSeeAlso(Connection connection)  throws SQLException {
        //super.setSeeAlso();
 
        // Don't do this now.  We have a separate section for current valid names       
        //setAlsoDatabased();
        boolean debug = false;
        
        String cfAndNr = getSeeAlsoCfAndNr(connection);
        if (cfAndNr != null) seeAlso = cfAndNr;        

        if (debug) s_log.warn("setSeeAlso() cfAndNr:" + cfAndNr);

        String siblingSubspecies = getSeeAlsoSiblingSubspecies(connection);

        if (debug) s_log.warn("setSeeAlso() siblingSubspecies:" + siblingSubspecies);

        if (siblingSubspecies != null  && !"".equals(siblingSubspecies)) {
          if (seeAlso != null && !"".equals(seeAlso)) seeAlso += ", ";
          if (seeAlso == null) seeAlso = "";
          seeAlso += siblingSubspecies;
        }

        if (debug) s_log.warn("setSeeAlso() seeAlso:" + seeAlso);

        if ("".equals(seeAlso)) seeAlso = null;
        //A.log("seeAlso() synonyms:" + synonyms + " cfAndNR:" + cfAndNr + " siblingSubspecies:" + siblingSubspecies + " seeAlso:" + seeAlso); 
    }


    public String getFullName() {
        StringBuffer fullName = new StringBuffer();
        fullName.append(genus + " ");
        if (subgenus != null
            && !"".equals(subgenus)
            && !"null".equals(subgenus)) {
            fullName.append("(" + subgenus + ") ");
        }
/*        
        if ((speciesGroup != null)
            && (!("".equals(speciesGroup)))
            && (!("null".equals(speciesGroup)))) {
            fullName.append("(" + speciesGroup + ") ");
        }
*/
        fullName.append(species);
        if (subspecies != null
            && !"".equals(subspecies)
            && !"null".equals(subspecies)) {
            fullName.append(" " + subspecies);
        }

        //A.log("getFullName() fullName:" + fullName + " genus:" + genus + " subgenus:" + subgenus);
        return fullName.toString();
    }


    // TODO after removing EOL, is this called anywhere?
    public void setAllImages(Connection connection) {
       // called by EOL to get all images for a given genus
        Hashtable myImages = new Hashtable();
        String theQuery = null;

        Statement stmt = null;
        ResultSet rset = null;
        try {
            
            theQuery = "select image_of_id, shot_type, has_tiff " 
                + " from specimen, image, proj_taxon where "
                + " specimen.genus = '" + AntFormatter.escapeQuotes(genus) + "'" 
                + " and specimen.species = '" + AntFormatter.escapeQuotes(species) + "'" 
                //+ " and specimen.subspecies is null"
                + " and specimen.code = image.image_of_id"
                + " and proj_taxon.taxon_name = specimen.taxon_name"
                + " and proj_taxon.project_name = 'worldants'"
                + " and source_table = 'specimen' and shot_number = 1"; 
     
            s_log.debug("setAllImages() genus image query: " + theQuery);
            
            stmt = DBUtil.getStatement(connection, "setAllImages()");
            rset = stmt.executeQuery(theQuery);

                String shot = null;
                int hasTiff = 0;
                SpecimenImage specImage = null;
                int i = 0;
                while (rset.next()) {
                    ++i;
                    String thisCode = rset.getString(1);
                    shot = rset.getString(2);
                    hasTiff = rset.getInt(3);
                    specImage = new SpecimenImage();
                    specImage.setShot(shot);
                    specImage.setNumber(1);
                    specImage.setCode(thisCode);
                    specImage.setHasTiff(hasTiff == 1);
                    //specImage.setPaths();
                    myImages.put(shot + i, specImage);
                }
          //if (genus.equals("anoplolepis") && species.equals("gracilipes")) {
          if (AntwebProps.isDevMode() && getTaxonName().contains("insularis")) {
            s_log.warn("setAllImages query:" + theQuery + " myImages.size:" + myImages.size());
          }
            //if (AntwebProps.isDevMode()) s_log.error("setAllImages() theQuery:" + theQuery);                
        } catch (SQLException e) {
            s_log.error("setAllImages() e:" + e + " theQuery:"+ theQuery);
        } finally {
            DBUtil.close(stmt, rset, this, "setAllImages()");
        }
        this.images = myImages;   // setting Taxon.java protected Hashtable images;
    }

    public String getTaxonomicBrowserParams() {
        String theParams = "genus=" + this.getGenus() + "&species=" + this.getSpecies() + "&rank=species";
        return theParams;
    }

    protected String getThisWhereClause() {
      return getThisWhereClause("");
    }
    protected String getThisWhereClause(String table) {    
        if (!"".equals(table)) table = table + ".";
        String clause = " and " + table 
            + "genus = '" + AntFormatter.escapeQuotes(genus) + "'" 
            + " and " + table + "species = '" + AntFormatter.escapeQuotes(species) + "'"
            + " and " + table + "subspecies is null";
        return clause;    
    }
    
    public void setChildren(Connection connection, Overview overview, StatusSet statusSet, boolean getChildImages, boolean getChildMaps, String caste, boolean global, String subgenus) throws SQLException {
      // This method does not seem to use project in it's criteria?!  SetChildrenLocalized below does...
  
        ArrayList theseChildren = new ArrayList();
        String overviewCriteria = "";
        //A.log("setChildren() global:" + global);
        if (!global) overviewCriteria = overview.getOverviewCriteria();
        
        String query =
            "select distinct specimen.code " 
          + " from taxon, specimen" 
          + " where taxon.taxon_name = specimen.taxon_name"
          + overviewCriteria
          + getThisWhereClause("taxon")
          + statusSet.getAndCriteria()
          + " and " + Caste.getSpecimenClause(caste)
          ;
            
        s_log.debug("setChildren() query:" + query);

        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = DBUtil.getStatement(connection, "setChildren()");
          rset = stmt.executeQuery(query);
          Specimen child = null;

          //A.log("Species.setChildren(5) overview:" + overview + " getChildImages:" + getChildImages + " query:" + query);

          int i = 0;
          while (rset.next()) {
            ++i;
            child = new Specimen();
            child.setRank(Rank.SPECIMEN);
            child.setSubfamily(subfamily);
            child.setGenus(genus);
            child.setSpecies(species);
            child.setCode(rset.getString("code"));
            child.setStatus(getStatus());
            if (getChildImages) {
                child.setImages(connection, overview, caste);
            } else {
                child.setHasImages(connection, overview);
            }
            if (AntwebDebug.isDebugTaxon(getTaxonName())) s_log.debug("setChildren(7) setHasImages code:" + child.getCode() + " hasImages:" + child.getHasImages());

            if (getChildMaps && i < Taxon.getMaxSafeChildrenCount() && overview instanceof LocalityOverview) {
                child.setMap(new Map(child, (LocalityOverview) overview, connection));
            }
            child.setTaxonomicInfo(connection);   // is this needed?  Yes, for now.
            child.generateBrowserParams(); 
            // child.init(); // added Oct 3, 2012 Mark.  No, can't.  Specimen has all in setTaxonomicInfo(), bad.            
            child.initTaxonSet(connection, overview);

            //A.log("setChildren() overview:" + overview + " child:" + child.getTaxonName() + " code:" + child.getCode() + " bioregion:" + child.getBioregion());
            theseChildren.add(child);
          }
        } catch (SQLException e) {
            s_log.warn("setChildren(" + overview + ", 3) e:" + e + " query:" + query);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "setChildren()");
        }        

        this.children = overview.sort(theseChildren);
        setChildrenCount(theseChildren.size());
        //A.log("setChildren() size:" + getChildrenCount() + " query:" + query);
    }
    
    public void setChildrenLocalized(Connection connection, Overview overview) throws SQLException {
    /* Called by FieldGuideAction.execute() in the case of species.
       ??? Used to be, this method uses project to figure the locality criteria does not use project in it's criteria.
     */      
        ArrayList theseChildren = new ArrayList();
        String query =
                "select distinct specimen.code from taxon, specimen" 
                    + " where taxon.taxon_name = specimen.taxon_name" 
                    + " and taxon.genus = '" + AntFormatter.escapeQuotes(genus) + "'" 
                    + " and taxon.species = '" + AntFormatter.escapeQuotes(species) + "'" 
                  //  + ' and status = "valid"'
                  ;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            // This one is differerent from setChildren in that it will pull back subspecies as well.  Correct?
            

            if (overview instanceof LocalityOverview) {
              String locality = ((LocalityOverview) overview).getLocality();                    
			  s_log.debug("setChildrenLocalized() locality:" + locality);
              if (locality != null && locality.length() > 0 && !locality.equals("null"))  {
                if ("country".equals(locality.substring(0, 7))) {
                    locality = "specimen." + locality;
                }
                query += " and " + locality;
              }
            }
            //A.log("setChildrenLocalized() query:" + query);
            
            stmt = DBUtil.getStatement(connection, "setChildrenLocalized()");
            rset = stmt.executeQuery(query);
            Specimen child = null;
            while (rset.next()) {
                child = new Specimen();
                child.setRank(Rank.SPECIMEN);
                child.setSubfamily(subfamily);
                child.setGenus(genus);
                child.setSpecies(species);
                child.setCode(rset.getString("code"));
                child.setImages(connection, overview);
                child.setTaxonomicInfo(connection);
                child.generateBrowserParams(overview);
                //child.setMap(new Map(child, overview, connection));
                theseChildren.add(child);
            }
        } catch (SQLIntegrityConstraintViolationException e) {
           s_log.warn("setChildrenLocalized(" + overview + ") e:" + e + " query:" + query); 
        } finally {
            DBUtil.close(stmt, rset, this, "setChildrenLocalized()");
        }        
        
        this.children = overview.sort(theseChildren);

        setChildrenCount(theseChildren.size());
    }

    public static int count = 0;
    public static String a1 = "";
    public static String a2 = "";

    public void sortBy(String fieldName) {
        sortBy(fieldName, "up");
    }
	public void sortBy(String fieldName, String sortOrder) {

        //AntwebUtil.logStackTrace();
/*
Required: Legacy Merge Sort: true

The sort will fail with: java.lang.IllegalArgumentException: Comparison method violates its general contract!
if the server is not configured to use Legacy Merge Sort.
To fix this proper would involve rewriting Species.sort()
*/
	
	    //s_log.warn("sortBy() field		:" + fieldName + " children:" + children);

        s_log.debug("sortBy() fieldName:" + fieldName);

      try {

	    if (fieldName.equals("bioregion")) {
			children.sort(new Comparator() {
                public int compare(Object o1, Object o2) {
                    if ("down".equals(sortOrder)) {
                        Object t = o1;
                        o1 = o2;
                        o2 = t;
                    }
                    return CompareUtil.compareString(((Specimen) o1).getBioregion(), ((Specimen) o2).getBioregion());
                }
            });
		}
          switch (fieldName) {
              case "code":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          return CompareUtil.compareString(((Specimen) o1).getCode(), ((Specimen) o2).getCode());
                      }
                  });
                  break;
              case "collectedby":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          return CompareUtil.compareString(((Specimen) o1).getCollectedBy(), ((Specimen) o2).getCollectedBy());
                      }
                  });
                  // Caste actually sorts by caste+subcaste
                  break;
              case "caste":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          Specimen specimenO1 = (Specimen) o1;
                          Specimen specimenO2 = (Specimen) o2;
                          String casteSubcasteO1 = specimenO1.getCaste() + specimenO1.getSubcaste();
                          String casteSubcasteO2 = specimenO2.getCaste() + specimenO1.getSubcaste();
                          // 2nd one first because reverse order
                          return CompareUtil.compareString(casteSubcasteO2, casteSubcasteO1);
                      }
                  });
                  break;
              case "collection":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          return CompareUtil.compareString(((Specimen) o1).getCollectionCode(), ((Specimen) o2).getCollectionCode());
                      }
                  });
                  break;
              case "country":

                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          ++count;
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          a1 = ((Specimen) o1).getCountry();
                          a2 = ((Specimen) o2).getCountry();

                          int c = CompareUtil.compareString(a1, a2);
                          //A.log("sort() count:" + count + " o1:" + ((Specimen) o1).getCountry() + " o2:" +  ((Specimen) o2).getCountry());
                          return c;
                      }
                  });
                  break;
              case "databy":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          return CompareUtil.compareString(((Specimen) o1).getGroup().getName(), ((Specimen) o2).getGroup().getName());
                      }
                  });
                  break;
              case "datecollected":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          return CompareUtil.compareString(((Specimen) o1).getDateCollectedStart(), ((Specimen) o2).getDateCollectedStart());
                      }
                  });
                  break;
              case "determinedby":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          return CompareUtil.compareString(((Specimen) o1).getDeterminedBy(), ((Specimen) o2).getDeterminedBy());
                      }
                  });
                  break;
              case "dna":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          // 2nd one first because reverse order.
                          return CompareUtil.compareString(((Specimen) o2).getDnaExtractionNotes(), ((Specimen) o2).getDnaExtractionNotes());
                      }
                  });
                  break;
              case "elevation":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          // 2nd one first because reverse order.
                          return CompareUtil.compareIntString(((Specimen) o2).getElevation(), ((Specimen) o1).getElevation());
                      }
                  });
                  break;
              case "habitat":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          // 2nd one first because reverse order.
                          return CompareUtil.compareString(((Specimen) o2).getHabitat(), ((Specimen) o1).getHabitat());
                      }
                  });
                  break;
              case "images":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          //A.log("Species.sortBy() o1:" + o1 + " c1:" + ((Specimen) o1).getImageCount() + " o2:" + o2 + " c2:" + ((Specimen) o2).getImageCount());
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          // 2nd one first because reverse order.
                          return CompareUtil.compareInt(((Specimen) o2).getImageCount(), ((Specimen) o1).getImageCount());
                      }
                  });
                  break;
              case "latitude":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          // 2nd one first because reverse order.
                          return CompareUtil.compareFloat(((Specimen) o2).getDecimalLatitude(), ((Specimen) o1).getDecimalLatitude());
                      }
                  });
                  break;
              case "lifestage":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          String ls1 = ((Specimen) o1).getLifeStage();
                          String ls2 = ((Specimen) o2).getLifeStage();
                          // 2nd one first because reverse order
                          int retVal = CompareUtil.compareString(ls2, ls1);
                          //A.log("Species.sortBy() retVal:" + retVal + " o1:" + o1 + " ls1:" + ((Specimen) o1).getLifeStage() + " o2:" + o2 + " ls2:" + ((Specimen) o2).getLifeStage());
                          return retVal;
                      }
                  });
                  break;
              case "location":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          return CompareUtil.compareString(((Specimen) o1).getLocalityString(), ((Specimen) o2).getLocalityString());
                      }
                  });
                  break;
              case "locatedat":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          return CompareUtil.compareString(((Specimen) o1).getLocatedAt(), ((Specimen) o2).getLocatedAt());
                      }
                  });
                  break;
              case "longitude":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          // 2nd one first because reverse order.
                          return CompareUtil.compareFloat(((Specimen) o2).getDecimalLongitude(), ((Specimen) o1).getDecimalLongitude());
                      }
                  });
                  break;
              case "medium":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          return CompareUtil.compareString(((Specimen) o1).getMedium(), ((Specimen) o2).getMedium());
                      }
                  });
                  break;
              case "method":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          return CompareUtil.compareString(((Specimen) o1).getMethod(), ((Specimen) o2).getMethod());
                      }
                  });
                  break;
              case "microchabitat":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          // 2nd one first because reverse order.
                          return CompareUtil.compareString(((Specimen) o2).getMicrohabitat(), ((Specimen) o1).getMicrohabitat());
                      }
                  });
                  break;
              case "museum":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          return CompareUtil.compareString(((Specimen) o1).getMuseumCode(), ((Specimen) o2).getMuseumCode());
                      }
                  });
                  break;
              case "taxonname":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          return CompareUtil.compareString(((Specimen) o1).getFullName(), ((Specimen) o2).getFullName());
                      }
                  });
                  break;
              case "ownedby":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          return CompareUtil.compareString(((Specimen) o1).getOwnedBy(), ((Specimen) o2).getOwnedBy());
                      }
                  });
                  break;
              case "specimennotes":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          // 2nd one first because reverse order.
                          return CompareUtil.compareString(((Specimen) o2).getSpecimenNotes(), ((Specimen) o1).getSpecimenNotes());
                      }
                  });
                  break;
              case "type":
                  children.sort(new Comparator() {
                      public int compare(Object o1, Object o2) {
                          if ("down".equals(sortOrder)) {
                              Object t = o1;
                              o1 = o2;
                              o2 = t;
                          }
                          // 2nd one first because reverse order.
                          return CompareUtil.compareString(((Specimen) o2).getTypeStatus(), ((Specimen) o1).getTypeStatus());
                      }
                  });
                  break;
          }
      } catch (IllegalArgumentException e) {
        s_log.warn("sortBy() a1:" + a1 + " a2:" + a2 + " e:" + e);
      }
	}  
	
     /* These two methods are for the automated generation of authority files. */
    public static String getDataHeader() {
      String header = 
        "Subfamily" + "\t" +
        "Tribe" + "\t" +
        "Genus" + "\t" +
        "Subgenus" + "\t" +
        "SpeciesGroup" + "\t" +
        "Species" + "\t" +
        "Subspecies" + "\t";
      return header;
    }    


    public String getData() throws SQLException {
      String data = "";
      String delimiter = "\t";   // ", ";
      
      data += Utility.notBlankValue(getSubfamily()) + delimiter;
      data += delimiter;  // data += Utility.notBlankValue(getTribe()) + delimiter;
      data += Utility.notBlankValue(getGenus()) + delimiter;
      data += Utility.notBlankValue(getSubgenus()) + delimiter;
      data += Utility.notBlankValue(getSpeciesGroup()) + delimiter;
      data += Utility.notBlankValue(getSpecies()) + delimiter;
      data += Utility.notBlankValue(getSubspecies()) + delimiter;
      return data;
    }  


    public boolean hasSpecimenDataSummary() {
        boolean hasSpecimenData = false;
        if (
             habitats != null && habitats.size() > 0
          || methods != null && methods.size() > 0
          || microhabitats != null && microhabitats.size() > 0
          || !"".equals(elevations)
          || !"".equals(collectDateRange)
          || !"".equals(types)
        ) hasSpecimenData = true;

        //A.log("hasSpecimenDataSummary() " + hasSpecimenData);

        return hasSpecimenData;
    }
    
    public void setHabitats(Connection connection) {
        //Formatter formatter = new Formatter();
        Vector<String> habitats = new Vector<>();
        String taxonName = null;
        String theQuery = null; 
        Statement stmt = null;
        ResultSet rset = null;
        try {
            //taxonName = getTaxonName();
            taxonName = AntFormatter.escapeQuotes(getTaxonName());
            
            theQuery =
//                " select count(habitat), habitat " 
              " select count(distinct collectioncode), habitat "
              + " from specimen " 
              + " where taxon_name='" + taxonName + "'"
              + " and habitat is not null and habitat != ''"
              + " group by habitat " 
              + " order by count(habitat) desc"
              + " limit 25";

            stmt = DBUtil.getStatement(connection, "setHabitats()");
            rset = stmt.executeQuery(theQuery);

            String count = null;
            String habitat = null;
            int recordCount = 0;
            while (rset.next()) {
                recordCount++;
                count = rset.getString(1);
                habitat = rset.getString(2);
                habitat = Formatter.dequote(habitat);
                habitats.add(habitat + ":" + count);
            }
            //if (AntwebProps.isDevMode()) s_log.info("setHabitats() recordCount:" + recordCount + " q:" + theQuery);
        } catch (Exception e) {
            s_log.error("setHabitats() for taxonName:" + taxonName + " exception:" + e);
            s_log.info("setHabitats() badQuery? - " + theQuery);
            // project:" + project + " 
        } finally {
            DBUtil.close(stmt, rset, this, "setHabitats()");
        }        
        this.habitats = habitats;
    }
    
    public void setMicrohabitats(Connection connection) {
        //Formatter formatter = new Formatter();
        Vector<String> microhabitats = new Vector<>();
        String taxonName = null;
        String theQuery = null; 
        Statement stmt = null;
        ResultSet rset = null;
        try {
            //taxonName = getTaxonName();
            taxonName = AntFormatter.escapeQuotes(getTaxonName());
            
            theQuery =
//                " select count(habitat), habitat " 
              " select count(distinct collectioncode), microhabitat "
              + " from specimen " 
              + " where taxon_name='" + taxonName + "'"
              + " and microhabitat is not null and microhabitat != ''"
              + " group by microhabitat " 
              + " order by count(microhabitat) desc"
              + " limit 25";

//A.log("setMicrohabitats() query:" + theQuery);

            stmt = DBUtil.getStatement(connection, "setMicrohabitats()");
            rset = stmt.executeQuery(theQuery);

            String count = null;
            String microhabitat = null;
            int recordCount = 0;
            while (rset.next()) {
                recordCount++;
                count = rset.getString(1);
                microhabitat = rset.getString(2);
                microhabitat = Formatter.dequote(microhabitat);
                microhabitats.add(microhabitat + ":" + count);
            }
            //if (AntwebProps.isDevMode()) s_log.info("setHabitats() recordCount:" + recordCount + " q:" + theQuery);
        } catch (Exception e) {
            s_log.error("setMicrohabitats() for taxonName:" + taxonName + " exception:" + e + " query:" + theQuery);
            //s_log.info("setMicrohabitats() badQuery? - " + theQuery);
            // project:" + project + " 
        } finally {
            DBUtil.close(stmt, rset, this, "setMicrohabitats()");
        }        
        this.microhabitats = microhabitats;
    }    
    
    public void setMethods(Connection connection) {
        Vector<String> methods = new Vector<>();
        String taxonName = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            //taxonName = getTaxonName();
            taxonName = AntFormatter.escapeQuotes(getTaxonName());
            
            String theQuery =
                " select count(distinct collectioncode), method " 
              + " from specimen " 
              + " where taxon_name='" + taxonName + "'"
              + " and method is not null and method != ''"
              + " group by method " 
              + " order by count(method) desc"
              + " limit 25";

            stmt = DBUtil.getStatement(connection, "setMethods()");
            rset = stmt.executeQuery(theQuery);

            String count = null;
            String method = null;
            int recordCount = 0;
            while (rset.next()) {
                recordCount++;
                count = rset.getString(1);
                method = rset.getString(2);
                method = Formatter.dequote(method);
                methods.add(method + ":" + count);
            }

            //s_log.info("setMethods() recordCount:" + recordCount + " q:" + theQuery);
            
        } catch (Exception e) {
            s_log.error("setMethods() for taxonName:" + taxonName + " exception:" + e);
            // project:" + project + " 
        } finally {
            DBUtil.close(stmt, rset, this, "setMethods()");
        }        
        this.methods = methods;
    }    
        
        
    public void setTypes(Connection connection) {
        //Formatter formatter = new Formatter();
        String typeString = "";
        String taxonName = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            //taxonName = getTaxonName();
            taxonName = AntFormatter.escapeQuotes(getTaxonName());
            
            String theQuery =
                " select code, type_status " 
              + " from specimen " 
              + " where taxon_name='" + taxonName + "'"
              + " and type_status is not null and type_status != ''"
              + " order by type_status";

            stmt = DBUtil.getStatement(connection, "setTypes()");
            rset = stmt.executeQuery(theQuery);

            String count = null;
            String typeStatus = "";
            String lastTypeStatus = "";
            int recordCount = 0;
            while (rset.next()) {
                ++recordCount;
                code = rset.getString(1);
                typeStatus = rset.getString(2);
                if (!typeStatus.equals(lastTypeStatus)) {
                    if (recordCount > 1) typeString += ";  ";
                    typeString += typeStatus + ":";
                } else {
                    if (recordCount > 1) typeString += ", ";                 
                }
                lastTypeStatus = typeStatus;
                typeString += " <a href=" + AntwebProps.getDomainApp() + "/specimen.do?name=" + code + ">" + code + "</a>";
            }

            //A.log("setTypes() recordCount:" + recordCount + " query:" + theQuery);
            
        } catch (Exception e) {
            s_log.error("setTypes() for taxonName:" + taxonName + " exception:" + e);
            // project:" + project + " 
        } finally {
            DBUtil.close(stmt, rset, this, "setTypes()");
        }
        this.types = typeString;
    }        
}


