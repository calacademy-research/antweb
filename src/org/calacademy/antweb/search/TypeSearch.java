package org.calacademy.antweb.search;

import org.calacademy.antweb.util.*;

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/** Class TypeSearch does the searching of the specimen data */
public class TypeSearch implements Serializable {

    private static Log s_log = LogFactory.getLog(TypeSearch.class);

    private String name;
    private String searchType;
    private String images;
    private String types;
    private String project;
    private Connection connection;
    private ResultSet results;

   public ResultSet getResults() {

     if (results == null) {
       setResults();
     }
     return results;
   }

   public void setResults()  {

     if ("on".equals(images)) {
       setWithImagesOnlyResults();
     } else {
     
        String theQuery;
        String genus = null;
        String species = null;
        if (name.contains(" ")) {
          StringTokenizer toke = new StringTokenizer(name," ");
          genus = toke.nextToken();
          species = toke.nextToken();
        }

      /*  if ((project == null) || (project.equals("worldants"))) { */
      
      if (project == null) {
         theQuery =
           "select taxon.subfamily, taxon.genus, taxon.species, sp.type_status, sp.code, " +
             " count(image.id) as imagecount, sp.country, sp.adm1, sp.localityname " + 
             " from taxon, specimen as sp left outer join image on " +
             " sp.code = image.image_of_id where (taxon.status = 'valid' and taxon.taxon_name = sp.taxon_name and ";

         if (genus != null && species !=null) {
            theQuery += getSearchString("taxon.genus","equals",genus) + " and " +
                        getSearchString("taxon.species","equals",species) + ") ";
          } else {
            theQuery +=
               getSearchString("taxon.subfamily", searchType, name) + " or " +
               getSearchString("taxon.genus", searchType, name) + " or " +
               getSearchString("taxon.species", searchType, name) + ") ";
          }

          theQuery +="  and sp.type_status !=''";

          theQuery += " group by taxon.subfamily, taxon.genus, taxon.species, sp.type_status, sp.code, sp.country, sp.adm1, sp.localityname order by taxon.genus, taxon.species";

         } else {
            theQuery =
             "select taxon.subfamily, taxon.genus, taxon.species, sp.type_status, sp.code, " +
               " count(image.id) as imagecount, sp.country, sp.adm1, sp.localityname " + 
               " from proj_taxon, taxon left outer join specimen as sp on  " +
               "((taxon.taxon_name = sp.taxon_name) or ((taxon.subfamily=sp.subfamily) and (taxon.genus=sp.genus)))" +
               " left outer join image on sp.code = image.image_of_id where  " +
               " proj_taxon.project_name = '" + project + "' and " +
               " proj_taxon.taxon_name = taxon.taxon_name and taxon.status = 'valid' and (";

          if (genus != null && species !=null) {
            theQuery += getSearchString("taxon.genus","equals",genus) + " and " +
                        getSearchString("taxon.species","equals",species) + ") ";
          } else {
            theQuery +=
               getSearchString("taxon.subfamily", searchType, name) + " or " +
               getSearchString("taxon.genus", searchType, name) + " or " +
               getSearchString("taxon.species", searchType, name) + ") ";
          }
 
          theQuery +=" and sp.type_status !=''";
          theQuery += " group by taxon.subfamily, taxon.genus, taxon.species, sp.type_status, sp.code, sp.country, sp.adm1, sp.localityname order by taxon.genus, taxon.species";
        }

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);
                   
            s_log.info("setResults() thequery:" + theQuery);
            this.results = rset;

        } catch (Exception e) {
          s_log.error("setResults() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, this, "setResults()");
        }
         // this.results = rset; changed 4/14/03
      }
    }

   public void setWithImagesOnlyResults()  {

        String theQuery;
        String genus = null;
        String species = null;
        if (name.contains(" ")) {
          StringTokenizer toke = new StringTokenizer(name," ");
          genus = toke.nextToken();
          species = toke.nextToken();
        }


      /*  if ((project == null) || (project.equals("worldants"))) {  */
      if (project == null)  {
 
         theQuery =
           "select taxon.subfamily, taxon.genus, taxon.species, sp.type_status, sp.code, " +
             " count(image.id) as imagecount, sp.country, sp.adm1, sp.localityname " + 
             " from taxon, specimen as sp, image " +
             " where sp.code = image.image_of_id and (taxon.taxon_name = sp.taxon_name and " +
                 " taxon.status = 'valid' and " ;

          if (genus != null && species !=null) {
            theQuery += getSearchString("taxon.genus","equals",genus) + " and " +
                        getSearchString("taxon.species","equals",species) + ") ";
          } else {
            theQuery +=
               getSearchString("taxon.subfamily", searchType, name) + " or " +
               getSearchString("taxon.genus", searchType, name) + " or " +
               getSearchString("taxon.species", searchType, name) + ") ";
          }
 
          theQuery += " and sp.type_status !='' ";
          theQuery += " group by  taxon.subfamily, taxon.genus, taxon.species,sp.type_status, sp.code, sp.country, sp.adm1, sp.localityName order by taxon.genus, taxon.species";

         } else {
 
          theQuery =
           "select taxon.subfamily, taxon.genus, taxon.species, sp.type_status, sp.code, " +
             " count(image.id) as imagecount, sp.country, sp.adm1, sp.localityname " + 
             " from proj_taxon, taxon, specimen as sp, image " +
             " where sp.code = image.image_of_id and " +
             " ((taxon.taxon_name = sp.taxon_name) or (taxon.subfamily=sp.subfamily and taxon.genus = sp.genus)) and " +
             " proj_taxon.project_name = '" + project + "' and taxon.status = 'valid' and " +
             " proj_taxon.taxon_name = sp.taxon_name and (";

          if (genus != null && species !=null) {
            theQuery += getSearchString("taxon.genus","equals",genus) + " and " +
                        getSearchString("taxon.species","equals",species) + ") ";
          } else {
            theQuery +=
               getSearchString("taxon.subfamily", searchType, name) + " or " +
               getSearchString("taxon.genus", searchType, name) + " or " +
               getSearchString("taxon.species", searchType, name) + ") ";
          }
 
          theQuery += " and sp.type_status !=''";

          theQuery += " group by taxon.subfamily, taxon.genus, taxon.species, sp.type_status, sp.code, sp.country, sp.adm1, sp.localityname order by taxon.genus, taxon.species";

          }

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);
        
            this.results = rset;
        } catch (Exception e) {
          s_log.error("setWithImagesOnlyResults e:" + e);
        } finally {
            DBUtil.close(stmt, rset, this, "setWithImagesOnlyResults()");
        }
        //this.results = rset;  changed 04/14/03
    }


    public String getSearchType() {
        return this.searchType;
    }

    public void setSearchType(String searchType) {
        this.searchType = searchType;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProject() {
        return this.project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getTypes() {
        return this.types;
    }

    public void setTypes(String types) {
        this.types = types;
    }

    public String getImages() {
        return this.images;
    }


    public void setImages(String images) {
        this.images = images;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

   private String getSearchString(String property, String searchType, String value) {

    StringBuffer sb = new StringBuffer();
    String operator;
    String leftPercent;
    String rightPercent;


    //if (value.equals("")) {
    //  return "";
    //}

       switch (searchType) {
           case "equals":
               operator = "=";
               leftPercent = "";
               rightPercent = "";
               break;
           case "contains":
               operator = "like";
               leftPercent = "%";
               rightPercent = "%";
               break;
           case "begins":
               operator = "like";
               leftPercent = "";
               rightPercent = "%";
               break;
           case "ends":
               operator = "like";
               leftPercent = "%";
               rightPercent = "";
               break;
           default:
               return null;
       }

    sb.append(property);
    sb.append(" ");
    sb.append(operator);
    sb.append(" \'");
    sb.append(leftPercent);
    sb.append(value);
    sb.append(rightPercent);
    sb.append("\'");

    return sb.toString();
  }


}
