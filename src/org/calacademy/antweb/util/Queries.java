package org.calacademy.antweb.util;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Date;

import javax.servlet.http.*;
import javax.servlet.*;

import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


public abstract class Queries {

    private static final Log s_log = LogFactory.getLog(Queries.class);
        
    public static String[] getIntegrityNames() {
        String[] list = {
            "geolocaleListMorphotaxa"
          , "noSpeciesForSpecies"
          , "noGenusForGenus"
          , "noSubfamilyForSubfamily"
          , "noFamilyForFamily"
          , "emptyGenus"
          , "emptySubfamily"
          , "generaInMultipleSubfamilies"
          //, "generaInMultipleSubfamilies2"
          , "taxonNameWithDoubleQuote"
          , "taxonNameWithSingleQuote"
          , "adminLoginIdIsZero"
          , "sourceWithBadParent"
          , "validsWithCurrentValidName"
          , "missingCurrentValidName"
          , "checkSpecimenStatus"
          , "spaceInGenusName"
          , "invalidBioregion"
          , "quadrinomialsInSpeciesLists"
          , "oldBadBioregionsInSpecimen"
          , "nonAntsInSpeciesListsProject"
          , "nonAntsInSpeciesListsGeolocale"
          , "badSingleQuotes"
          , "geolocaleNameWithQuestionMarks"
          , "geolocaleTaxaWithoutTaxon"
          , "geolocaleTaxaMorphosWithNoSpecimen"
          , "homonymWithoutTaxon"
          , "projTaxaWithoutTaxonWithoutHomonym"
          , "projTaxaWithoutTaxonWithHomonym"
        };
        return list;      
    }
    public static ArrayList<String> getIntegrityNamesArray() {
      return new ArrayList<String>(Arrays.asList(getIntegrityNames()));
    }        
    
    public static ArrayList<NamedQuery> getIntegrityQueries() {
        String[] list = getIntegrityNames();
        
        return Queries.getNamedQueryList(list);
    }


    public static String[] getCurateAntcatNames() {
        String[] list = {
          "unrecognizedTaxaWithoutSpecimen"
        , "unrecognizedWithSynonymParent"
        , "geolocaleTaxaNotUsingCurrentValidName"   
        , "badParentTaxonName"      
        , "worldantsUploads"   
        };
        return list;
    }
    public static ArrayList<String> getCurateAntcatNamesArray() {
      return new ArrayList<String>(Arrays.asList(getCurateAntcatNames()));
    }        
    
    public static String[] getDevIntegrityNames() {
        String[] list = {
            "emptySourceTaxonSets"
          , "brokenTaxaHierarchy"
        };
        return list;
    }
    public static ArrayList<String> getDevIntegrityNamesArray() {
      return new ArrayList<String>(Arrays.asList(getDevIntegrityNames()));
    }        
    
    // Highest priority. More so that Integrity queries.
    public static ArrayList<NamedQuery> getAdminCheckQueries() {
        String[] list = {
            "brokenTaxaHierarchy"
          //, "missingDefaultSpecimen"
        };
        
        return Queries.getNamedQueryList(list);
    }
    
    public static String[] getCuriousNames() {
        String[] list = {
            "speciesEqualsSubspecies"
          , "specimenCountByTaxa"
          , "taxonCountBySubfamily"
          , "taxonCountByGenus"
          , "taxonCountBySpecies"
          , "ancillaryFiles"
          , "speciesListHomonyms"
          , "introducedSpecimen"
          , "morphoProjTaxa"
          , "duplicatedGeolocaleNames"
          , "nonAnts"
          , "nonIntroducedAntsInMultipleBioregions"
          , "distinctMales"
          , "distinctWorkers"
          , "distinctQueens"
          , "distinctOthers"
          , "geolocaleTaxonDisputes"
          , "projectTaxonDisputes"
        };    
        return list;    
    }        
    public static ArrayList<String> getCuriousNamesArray() {
      return new ArrayList<String>(Arrays.asList(getCuriousNames()));
    }  
    
    public static ArrayList<NamedQuery> getCuriousQueries() {
        String[] list = getCuriousNames();
        
        return getNamedQueryList(list);
    }    

    // Used in the queries.jsp to list the various Queries.
    public static ArrayList<String> getNames() {
    
      String[] list = {
          "geolocaleListMorphotaxa"      
        , "nonAsciiTaxonName"
        , "invalidCurrentValid"
        , "longCurrentValidNames"
        , "notValidTaxaFromSpeciesList"
        , "notValidTaxaFromSpecimenList"
        , "imageCountByOwner"
        , "specimenCountByOwner"
        , "descriptionEditCountByOwner"
        , "importedCountryData"
        , "badParentTaxonName"
        , "specimenGroups"
        , "multiBioregionSpecimenTaxa"
        , "notValidBioregionTaxonNames"
        , "notValidMuseumTaxonNames"
        , "notValidGeolocaleTaxonNames"
        , "notValidProjectTaxonNames"
        , "worldantsUploads"
      };
      ArrayList<String> names = new ArrayList<String>(Arrays.asList(list));
      return names;
    }

    // For Geolocale Manager.
    public static ArrayList<String> getGeolocaleQueries() {
      String[] list = {
          "invalidValidNamelessCountries"
        , "usedAndUnusedCoords"
        , "lowGeolocaleTaxonCount"
        , "unfinishedBioregions"
        , "nonvalidValidNames"
        , "nonLiveValidCountries"
        , "geolocaleTaxaFromInvalidTaxonName"
        , "geolocaleTaxaInMultipleBioregions"        
        , "geolocaleTaxaWithoutTaxon"
      };
      ArrayList<String> names = new ArrayList<String>(Arrays.asList(list));
      return names;
    }
    
    // For Login Manager.
    public static ArrayList<String> getLoginQueries() {
      String[] list = {
          "uploadingCurators"
        , "curatorEmails"
      };
      ArrayList<String> names = new ArrayList<String>(Arrays.asList(list));
      return names;
    }

    // For Group Manager.
    public static ArrayList<String> getGroupQueries() {
      String[] list = {
          "groups"
        , "specimenGroups"
      };
      ArrayList<String> names = new ArrayList<String>(Arrays.asList(list));
      return names;
    }            

// ----------------

    private static ArrayList<NamedQuery> getTaxaNamedQueries() {  
        ArrayList<NamedQuery> queries = new ArrayList<NamedQuery>();

        queries.add(new NamedQuery(
            "brokenTaxaHierarchy"
          , "Broken Taxa Hierarchy:"
          , ""      
          , "select taxon_name from taxon where taxarank = 'species' and (species is null or species = '') and taxon_name not like '%(%'"
        ));
 
        queries.add(new NamedQuery(
            "nonAsciiTaxonName"
          , "These taxa contain non ascii taxon names:"
          , "<th>Taxon Name</th><th>Source</th><th>Created</th>"      
          , "SELECT taxon_name, concat('&nbsp;&nbsp;&nbsp;', source), created " 
            + " FROM taxon WHERE NOT HEX(taxon_name) REGEXP '^([0-7][0-9A-F])*$' order by source, taxon_name"
        ));
        
        queries.add(new NamedQuery(
              "selfRefSynonyms"
            , "These are the Antcat synonyms which have current valid names that point to themselves."
            , "<th>Taxon Name</th><th>Subfamily</th><th>Current Valid Name</th>"      
            , "select taxon_name, subfamily, current_Valid_name from taxon where source = 'worldants.txt' " 
            + " and status = 'synonym' and instr(taxon_name, current_Valid_name) + length(current_valid_name) = length(taxon_name) + 1 " 
            + " and substring(taxon_name, 1, instr(taxon_name, current_Valid_name)-1) = subfamily"
        ));
        
        queries.add(new NamedQuery(
            "homonyms"
          , "This is the complete list of homonyms."
          , "<th>Taxon Name</th><th>Author Date</th>"      
          , "select taxon_name, author_date from homonym order by taxon_name"
        ));

        queries.add(new NamedQuery(
            "invalidCurrentValid"
          , "These are the Antcat original combination taxa with current valid names not found in Antweb."
          , "<th>Taxon Name</th><th>Current Valid Name</th>"      
          , "select taxon_name, current_valid_name from taxon where status = 'original combination' and current_valid_name is not null and taxon_name != concat(subfamily, current_valid_name) and concat(subfamily, current_valid_name) not in (select taxon_name from taxon) and concat(subfamily, current_valid_name) not in (select taxon_name from homonym)"
        ));

       queries.add(new NamedQuery(
            "longCurrentValidNames"
          , "These current valid names contain three spaces."
          , "<th>Taxon Name</th><th>Current Valid Name</th>"
          , "select taxon_name, current_valid_name  from taxon where status = 'original combination' and current_valid_name is not null and taxon_name != concat(subfamily, current_valid_name) and concat(subfamily, current_valid_name) not in (select taxon_name from taxon) and concat(subfamily, current_valid_name) not in (select taxon_name from homonym) and ROUND ( ( LENGTH( current_valid_name ) - LENGTH( REPLACE ( current_valid_name , ' ', '') ) ) / LENGTH(' ') ) > 2"
        ));
        
        queries.add(new NamedQuery(
            "noSpeciesForSpecies"
          , "There is no Species value for a species."
          , "<th>Taxon Name</th>"      
          , "select taxon_name from taxon where taxarank = 'species' and (species is null or species = '')"
          ));

        queries.add(new NamedQuery(
            "noGenusForGenus"
          , "Null or empty genus name for a genus."
          , "<th>Taxon Name</th>"      
          , "select taxon_name from taxon where taxarank = 'genus' and (genus is null or genus = '')"
          ));

        queries.add(new NamedQuery(
            "noSubfamilyForSubfamily"
          , "Null or empty subfamily from a subfamily"
          , "<th>Taxon Name</th><th>source</th><th>created</th>"      
          , "select taxon_name, source, created from taxon where taxarank = 'subfamily' and (subfamily is null or subfamily = '')"
          ));

        /*
        queries.add(new NamedQuery(
            "noFamilyForFamily"
          , "Null or empty family for a family"
          , "<th>Taxon Name</th>"      
          , "select taxon_name from taxon where taxarank = 'family' and (family is null or family = '')"
          ));
        */
        
        queries.add(new NamedQuery(
            "emptyGenus"
          , "Genus field is empty for a genus or species"
          , "<th>Taxon Name</th><th>Subfamily</th><th>Genus</th><th>Rank</th>"      
          , "select taxon_name, subfamily, genus, taxarank from taxon where genus = '' and taxarank != 'subfamily' and taxon_name != 'formicidae'"
          ));
          
        // contain detail query  
        queries.add(new NamedQuery(
            "generaInMultipleSubfamilies"
          , "Show the genera that are in more than one subfamily"
          , "<th>Genus</th><th>Subfamily Count</th><th>Source</th><th>Statuses</th>"      
          , "select genus, count(distinct subfamily), source, group_concat(distinct status) from taxon where taxarank != 'family' and taxarank != 'subfamily' and status not in ('synonym', 'original combination') group by genus, source having count(distinct subfamily) > 1"
          , "generalInMultipleSubfamiliesDetail"
          ));
            
        // This ran for long time and created trouble for site.    
        queries.add(new NamedQuery(
            "generaInMultipleSubfamilies2"
          , "Show the genera that are in more than one subfamily, 2nd method."
          , "<th>Taxon Name</th><th>Subfamily</th><th>Genus</th><th>Source</th>"      
          , "select taxon_name, subfamily, genus, source from taxon where genus in (select genus from taxon where taxarank = 'genus' group by genus having count(*) > 1)"
          ));
            
        queries.add(new NamedQuery(
          "generalInMultipleSubfamiliesDetail"
          , "Show the genera that are in more than one subfamily"
          , "<th>Taxon Name</th><th>Subfamily</th><th>Genus</th><th>Source</th><th>Created</th><th>Insert Method</th>"      
          , "select taxon_name, subfamily, genus, source, created, insert_method from taxon where genus in (select genus from taxon where taxarank != 'family' and taxarank != 'subfamily' and status != 'synonym' group by genus having count(distinct subfamily) > 1)"
        ));            
            
        queries.add(new NamedQuery(
            "emptySubfamily"
          , "Subfamily is empty though genus is not."
          , "<th>Taxon Name</th><th>Rank</th><th>Subfamily</th><th>Genus</th><th>Source</th><th>Created</th>"
          , "select taxon_name, taxarank, subfamily, genus, source, created from taxon where (subfamily is null or subfamily = '') and (genus is not null and genus != '') order by source, taxon_name"
          ));
          
        queries.add(new NamedQuery(
            "taxonNameWithDoubleQuote"
          , "There is a double quotation within the taxon name"
          , "<th>Taxon Name</th><th>Rank</th><th>Status</th><th>Type</th><th>Source</th><th>Insert Method</th><th>Created</th>"      
          , "select taxon_name, taxarank, status, type, source, insert_method, created from taxon where taxon_name like '%\"%'"
          ));

        queries.add(new NamedQuery(
            "taxonNameWithSingleQuote"
          , "There is a single quotation within the taxon name"
          , "<th>Taxon Name</th><th>Rank</th><th>Status</th><th>Type</th><th>Source</th><th>Insert Method</th><th>Created</th>"      
          , "select taxon_name, taxarank, status, type, source, insert_method, created from taxon where taxon_name like '%\''%'"
          ));
        
        queries.add(new NamedQuery(
            "validsWithCurrentValidName"
          , "Valids with current valid names"
          , "<th>Taxon Name</th><th>Status</th><th>Rank</th><th>Source</th><th>Current Valid Name</th>"      
          , "select taxon_name, status, taxarank, source, current_valid_name from taxon where status = 'valid' and current_valid_name is not null"
          ));

        queries.add(new NamedQuery(
            "missingCurrentValidName"
          , "Status which uses Current Valid Name is missing it."
          , "<th>Taxon Name</th><th>Current Valid Name</th><th>Status</th><th>Created</th><th>Source</th><th>Line Num</th><th>Rank</th>"   
          , "select taxon_name, current_valid_name, status, created, source, line_num, taxarank from taxon where current_valid_name is null and status in ('unavailable uncategorized', 'original combination', 'unavailable misspelling', 'obsolete combination', 'synonym')"
          ));
        
        queries.add(new NamedQuery(
            "spaceInGenusName"
          , "There is a space in the genus name"
          , "<th>Taxon Name</th><th>Genus</th>"
          , "select taxon_name, genus from taxon where genus like '% %'"
          ));          
          
        queries.add(new NamedQuery( 
            "unrecognizedWithSynonymParent"
          , "These unrecognized taxa have parent_taxa that are synonyms"
          , "<th>Taxon Name</th><th>Source</th>"
          , "select taxon_name, source from taxon where status = 'unrecognized' and parent_taxon_name in (select taxon_name from taxon where status = 'synonym')"
          ));
          
        queries.add(new NamedQuery(
            "unrecognizedTaxaWithoutSpecimen"
          , "These taxa remain without correlated specimen.  To be deleted?"
          , "<th>Source</th><th>Created</th><th>Taxon Name</th>"
          , "select source, created, taxon_name from taxon where source like 'specimen%' and status = 'unrecognized' and taxon_name not in (select distinct taxon_name from specimen) order by source, created"
          ));
        
        queries.add(new NamedQuery(
            "speciesEqualsSubspecies"
          , "Species equals subspecies and species of subspecies has parenthesis."
          , "<th>Taxon 1 Name</th><th>Subfamily</th><th>Genus</th><th>Species</th>Subspecies<th></th><th>Taxon 2 Name</th><th>Subfamily</th><th>Genus</th><th>Species</th><th>Subspecies</th>"      
          , "select t1.taxon_name, t1.subfamily, t1.genus, t1.species, t1.subspecies, t2.taxon_name, t2.subfamily, t2.genus, t2.species, t2.subspecies from taxon t1, taxon t2 where t1.species = t2.subspecies and t2.species like '(%' and t1.subfamily = t2.subfamily and t1.genus = t2.genus"
          ));

        queries.add(new NamedQuery(
            "taxonCountBySubfamily"
          , "Taxon count by subfamily"
          , "<th>Subfamily</th><th>Count</th>"      
          , "select subfamily, count(*) from taxon group by subfamily order by count(*) desc limit 8"
          ));

        queries.add(new NamedQuery(
            "taxonCountByGenus"
          , "Taxon count by genus"
          , "<th>Genus</th><th>Count</th>"      
          , "select genus, count(*) from taxon group by genus order by count(*) desc limit 8"
          ));

        queries.add(new NamedQuery(
            "taxonCountBySpecies"
          , "Taxon count by species"
          , "<th>Species</th><th>Count</th>"      
          , "select species, count(*) from taxon group by species order by count(*) desc limit 8"
          ));
        
        queries.add(new NamedQuery(
            "badSpecies"
           , "Species must have not-null species name."         
           , "<th>Taxon Name</th><th>source</th><th>Created</th>"
           , "select taxon_name, source, created from taxon where taxarank = 'species' and species is null"
           ));

        queries.add(new NamedQuery(
            "antwebUniqueValidTaxa"
          , "The list of valid species and subspecies exist in Antweb but are not contained in the submitted valid taxon set."
          , "<th>Taxon Name</th>"
          , "select "
          // Probably should use domainApp
            + " concat(concat(concat(concat(\"<a href=\'" + AntwebProps.getDomainApp() + "/description.do?taxonName=\", taxon_name), \"\'>\"), taxon_name), \"</a>\") "            
            + " from taxon where taxarank in ('species', 'subspecies') and status = 'valid' and fossil = 0 and taxon_name not in (select taxon_name from antwiki_valid_taxa)"
          ));
                  
        queries.add(new NamedQuery(
           "indeterminedAndQuestionableMorphoTaxa"
          , "Display indetermined and questionable morphotaxa."
          , "<th>Taxon Name</th><td>Rank</td><th>Status</th><th>Source</th><th>Created</th>"
          , "select taxon_name, taxarank, status, source, created from taxon where status = 'indetermined' or (status = 'morphotaxon' and taxon_name like '%(%') order by status desc, source, taxon_name"
        ));
                
        queries.add(new NamedQuery(
            "badParentTaxonName"
          , "These taxa have a parent_taxon_name that does not exist in the taxon table. "
          , "<th>TaxonName</th><th>Parent Taxon Name</th><th>Source</th><th>Status</th><th>current_valid_name</th><th>Created</th>"
          , "select t.taxon_name, t.parent_taxon_name, t.source, t.status, t.current_valid_name, t.created from taxon t where family = 'formicidae' and taxarank != 'family' and status not in ('unavailable misspelling', 'unavailable uncategorized', 'original combination') and parent_taxon_name not in (select taxon_name from taxon) order by created"
        ));
                
        // This query has a detail query
        queries.add(new NamedQuery(
            "sourceWithBadParent"
          , "Source with taxa with parent taxa not found"
          , "<th>Source</th>"      
          , "select distinct source from taxon where parent_taxon_name not in (select taxon_name from taxon) and family = 'formicidae' and taxon_name not in ('formicidae', '(formicidae)')"
          , "sourceWithBadParentDetail1"
          ));

        queries.add(new NamedQuery(
            "sourceWithBadParentDetail1"
          , "Source with taxa with parent taxa not found (Details)"
          , "<th>Taxon Name</th><th>Parent Taxon Name</th><th>Source</th><th>Status</th><th>Created</th>"      
          , "select taxon_name, parent_taxon_name, source, status, created from taxon where parent_taxon_name not in (select taxon_name from taxon) and family = 'formicidae' and taxon_name != 'formicidae' order by created"
          , "sourceWithBadParentDetail2"
          ));

        queries.add(new NamedQuery(
            "sourceWithBadParentDetail2"
          , "Source with taxa with parent taxa not found (Details)"
          , "<th>Source</th><th>Taxon Name</th><th>Groups</th>"      
          , "select source, taxon_name, (select GROUP_CONCAT(distinct access_group) from specimen where specimen.taxon_name = taxon.taxon_name) as access_groups, (select count(*) from specimen where specimen.taxon_name = taxon.taxon_name) specimen_count, (select count(distinct access_group) from specimen where specimen.taxon_name = taxon.taxon_name) specimen_owner_count, parent_taxon_name, taxarank, created, insert_method from taxon where parent_taxon_name not in (select taxon_name from taxon) and source like 'specimen%' and family = 'formicidae' order by source"
          ));
        // Drill into the results with a query like:  select code, taxon_name, access_group, created, subfamily, genus from specimen where taxon_name = "cerapachyinaecerapachys centurio_cf";
          
        queries.add(new NamedQuery(
           "taxaFromOldSpecimenLists"
          , "Show the taxa created from species lists that no longer exist, and their specimen counts."
          , "<th>Taxon Name</th><td>Rank</td><th>Status</th><th>Source</th><th>Created</th><th>Count</th>"
          , "select t.taxon_name, taxarank, status, source, created, (select count(*) from specimen s where s.taxon_name = t.taxon_name) count from taxon t where (source like '%ants' or source like '%speciesList.txt') and source not in (select project_name from project) order by count desc"
        ));
                        
        return queries; // end getTaxonNamedQueries()
    }
    
    private static ArrayList<NamedQuery> getHomonymNamedQueries() {  
        ArrayList<NamedQuery> queries = new ArrayList<NamedQuery>();

        queries.add(new NamedQuery(
           "homonymWithoutTaxon"
          , "Homonyms without matching records in the taxon table."
          , "<th>Taxon Name</th>"
          ,"select concat(concat(concat(concat(\"<a href=\'" + AntwebProps.getDomainApp() + "/description.do?taxonName=\", taxon_name), \"\'>\"), taxon_name), \"</a>\") from homonym where taxon_name not in (select taxon_name from taxon)"
        ));    
        return queries; // end getHomonymNamedQueries()
    }
        
    private static ArrayList<NamedQuery> getSpecimenNamedQueries() {  
        ArrayList<NamedQuery> queries = new ArrayList<NamedQuery>();

        queries.add(new NamedQuery(
              "notValidTaxaFromSpecimenList"
            , "Taxon name not valid from specimen list."
            , "<th>Group</th><th>Taxon Name</th><th>Status</th>"
            , " select distinct g.abbrev, " 
            + " concat(concat(concat(concat(\"<a href=\'http://antweb.org/description.do?taxonName=\", t.taxon_name), \"\'>\"), t.taxon_name), \"</a>\") "            
            + ", t.status from specimen sp, taxon t, ant_group g"
            + " where sp.taxon_name = t.taxon_name " 
            + " and sp.access_group = g.id"
            + " and t.family = 'formicidae'"
            + " and t.status != 'valid'"
            + " and t.status not in ('morphotaxon')"
            + " order by g.name, sp.taxon_name"
        ));

        queries.add(new NamedQuery(
          "specimenCountByOwner"
        , "Count of Specimen by Owner"
        , "<th>Specimen Count</th><th>Owned By</th>"
        , "select count(s.code) theCount, s.ownedby from specimen s group by s.ownedBy order by theCount desc"
        ));

        queries.add(new NamedQuery(
            "checkSpecimenStatus"
          , "These specimen have a status of null"
          , "<th>Code</th><th>Taxon Name</th><th>Group</th><th>Created</th>"
          , "select code, taxon_name, access_group, created from specimen where status is null"
          ));

        queries.add(new NamedQuery(
            "invalidBioregion"
          , "Specimen have invalid bioregions"
          , "<th>Bioregion</th>, <th>Count</th>, <th>Group</th>"
          , "select bioregion, count(*), group_concat(distinct access_group) from specimen where bioregion not in (select name from bioregion) group by bioregion"
          ));
          
        queries.add(new NamedQuery(
            "oldBadBioregionsInSpecimen"
           , "This Bioregions are non standard and still exist in specimen data"         
           , "<th>Bioregion</th><th>Group</th><th>Count</th>"
           , "select bioregion, access_group, count(*) from specimen where bioregion not in ('Antarctica', 'Afrotropical', 'Neotropical', 'Malagasy', 'Australasia', 'Oceania', 'Indomalaya', 'Palearctic', 'Nearctic')  group by bioregion, access_group"
           ));
           
        queries.add(new NamedQuery(
            "specimenCountByTaxa"
          , "Specimen count by taxa"
          , "<th>Taxon Name</th><th>Count</th>"      
          , "select taxon_name, count(*) from specimen group by taxon_name order by count(*) desc limit 8"
          ));
           
        queries.add(new NamedQuery(
            "specimenGroups"
          , "Specimen submissions by group. "
          , "<th>Group ID</th><th>Group Name</th><th>Count</th>"
          , "select access_group, (select name from ant_group where id = access_group) name, count(*) from specimen group by access_group order by count(*) desc"
        ));
          
        queries.add(new NamedQuery(
            "introducedSpecimen"
          , "These are the specimen records flagged as introduced."
          , "<th>Group</th><th>Bioregion</th><th>code</th><th>taxon_name</th><th>Country</th>"
          , "select ant_group.name, bioregion, code, taxon_name, country from specimen, ant_group where specimen.access_group = ant_group.id and is_introduced = 1 order by access_group, bioregion, country"
          ));          
          
        queries.add(new NamedQuery(
            "nonAnts"
          , "Specimen in Antweb that are not ants."
          , "<th>Link</th><th>Specimen Code</th><th>Family</th><th>Subfamily</th><th>Genus</th>"
          , "select concat(concat('<a href=" + AntwebProps.getDomainApp() + "/specimenImages.do?name=', image_of_id),'>link</a>')" 
            + ", image_of_id, family, subfamily, genus, species from image, specimen where image_of_id = code and family != 'formicidae' and shot_type = 'l' order by family"        
        ));      
        
        queries.add(new NamedQuery(
            "castes"
          , "Distinct set of castes along with counts."
          , "<th>Count</th><th>Life Stage/Sex</th><th>Caste</th><th>Subcaste</th><th>Access Groups</th>"
          , "select count(*), CONCAT(LEFT(life_stage, 25), IF(LENGTH(life_stage)>25, '...', '')), caste, subcaste, group_concat(distinct access_group) from specimen group by life_stage, caste, subcaste order by count(*) desc"        
        ));      

        queries.add(new NamedQuery(
            "nullCastes"
          , "Distinct set of castes along with counts."
          , "<th>Count</th><th>Life Stage/Sex</th><th>Access Groups</th>"
          , "select count(*), life_stage, group_concat(distinct access_group) from specimen where caste is null group by life_stage, caste, subcaste order by count(*) desc"        
        ));

        queries.add(new NamedQuery(
            "nullLifeStage"
          , "List of specimen without a lifestage field."
          , "<th>Code</th><th>Access Group</a>"
          , "select code, access_group from specimen where life_stage is null and image_count > 0 order by access_group, code"        
        ));

        queries.add(new NamedQuery(
            "multiBioregionSpecimenTaxa"
          , "These non-introduced taxa are found in multiple bioregions."
          , "<th>Taxon Name</th><th>Bioregions</th><th>Bioregion Count</th>"
          , "select concat(concat(concat(concat(\"<a href=\'" + AntwebProps.getDomainApp() + "/bigMap.do?taxonName=\", taxon_name), \"\'>\"), taxon_name), \"</a>\"), group_concat(distinct bioregion), count(distinct bioregion) from specimen where bioregion is not null and taxon_name not like '%indet%' and country != 'Port of Entry' and taxon_name not in (select distinct taxon_name from proj_taxon where project_name = 'introducedants') group by taxon_name having count(distinct bioregion) > 1 order by count(distinct bioregion) desc, taxon_name"
        ));

        queries.add(new NamedQuery(
           "nonIntroducedAntsInMultipleBioregions"
          , "Specimen that are not on the introduced list found in multiple bioregions."
          , "<th>Taxon Name</th><th>Bioregions</th><th>Countries...</th><th>Access Groups</th><th>Count</th>"
          , "select taxon_name, group_concat(distinct bioregion), substring(group_concat(distinct country), 1, 140), group_concat(distinct access_group), count(distinct bioregion) count from specimen where bioregion is not null and taxon_name not in (select distinct taxon_name from proj_taxon where project_name = 'introducedants') and taxon_name not like '%(indet)%' and country != 'Port of Entry' group by taxon_name having count(distinct bioregion) > 1;"
        ));
        
        return queries; // end getSpecimenNamedQueries()
    }
        

    private static ArrayList<NamedQuery> getFossilNamedQueries() {  
        ArrayList<NamedQuery> queries = new ArrayList<NamedQuery>();

        queries.add(new NamedQuery(
           "validNonfossilSpeciesWithBioregions"
          , "Valid, non-fossil species and their bioregions. Records can be correlated with query: validNonfossilSpeciesWithMuseums"
          , "<th>Taxon Name</th><th>subfamily</th><th>genus</th><th>species</th><th>bioregions</th>"
          , "select t.taxon_name, subfamily, genus, species, group_concat(distinct bioregion_name) from taxon t left join bioregion_taxon bt on t.taxon_name = bt.taxon_name where status = 'valid' and fossil = 0 and taxarank in ('species') group by t.taxon_name, subfamily, genus, species"
        ));

        queries.add(new NamedQuery(
           "validNonfossilSpeciesWithMuseumsOwnedByLocatedAt"
          , "Valid, non-fossil species and their museums, owned by, and located at."
          , "<th>Taxon Name</th><th>Museums</th><th>Owned By</th><th>Located At</th>"
          , "select t.taxon_name, group_concat(distinct s.museum) museum, group_concat(distinct s.ownedby) ownedBy, group_concat(distinct locatedat) locatedAt from taxon t left join specimen s on t.taxon_name = s.taxon_name where t.status = 'valid' and t.fossil = 0 and t.taxarank in ('species') group by t.taxon_name order by taxon_name"
        ));
        
        queries.add(new NamedQuery(
           "validNonfossilSpeciesWithMuseums"
          , "Valid, non-fossil species and their museums."
          , "<th>Taxon Name</th><th>Museums</th>"
          , "select t.taxon_name, group_concat(distinct s.museum) from taxon t left join specimen s on t.taxon_name = s.taxon_name where t.status = 'valid' and t.fossil = 0 and t.taxarank in ('species') group by t.taxon_name;"
        ));

        queries.add(new NamedQuery(
           "validNonfossilTaxaWithBioregions"
          , "Valid, non-fossil taxa and their bioregions."
          , "<th>Taxon Name</th><th>subfamily</th><th>genus</th><th>species</th><th>subspecies</th><th>bioregions</th>"
          , "select taxon_name, subfamily, genus, species, subspecies, group_concat(distinct bioregion_name) from taxon t left join bioregion_taxon bt on t.taxon_name = bt.taxon_name where status = 'valid' and fossil = 0 and taxarank in ('species', 'subspecies') group by subfamily, genus, species, subspecies"
        ));        
        
        queries.add(new NamedQuery(
            "antwebUniqueFossilTaxa"
          , "The list of fossil species and subspecies exist in Antweb but are not contained in the submitted fossil taxon set."
          , "<th>Taxon Name</th>"
          , "select "
            + " concat(concat(concat(concat(\"<a href=\'" + AntwebProps.getDomainApp() + "/description.do?taxonName=\", taxon_name), \"\'>\"), taxon_name), \"</a>\") "            
            + " from taxon where taxarank in ('species', 'subspecies') and status= 'valid' and fossil = 1 and taxon_name not in (select taxon_name from antwiki_fossil_taxa)"
          ));
          
        return queries; // end getFossilNamedQueries()
    }        
        

    private static ArrayList<NamedQuery> getLoginNamedQueries() {  
        ArrayList<NamedQuery> queries = new ArrayList<NamedQuery>();
                        
        queries.add(new NamedQuery(
            "loginProjectMapping"
          , "These are the logins mapped to projects."
          , "<th>Group Name</th><th>Login Name</th><th>Login Email</th><th>Project Title</th><th>Project Name</th>"
          , "select g.name, l.name, l.email, p.project_title, p.project_name from ant_group g, login l, login_project lp, project p where g.id = l.group_id and l.id = lp.login_id and lp.project_name = p.project_name order by g.name, l.name, p.project_title"
        ));

        queries.add(new NamedQuery(
            "curatorInfoAndProjects"
          , "These are the emails of curators that manage species lists."
          , "<th>Name</th><th>Email</th><th>Projects</th>"
          , "select l.name, l.email, group_concat(lp.project_name) from login l, login_project lp where l.id = lp.login_id group by l.name, l.email order by l.name;"
        ));

        queries.add(new NamedQuery(
            "curatorEmails"
          , "These are a comma separated list of the emails of curators that manage species lists."
          , "<th>Email</th>"
          , "select group_concat(distinct concat(l.email, ' ')) from login l, login_project lp where l.id = lp.login_id"
        ));
        
        queries.add(new NamedQuery(
            "uploadingCurators"
          , "Curators who can upload."
          , "<th>Login ID</th><th>First Name</th><th>Last Name</th><th>Email</th><th>Name</th><th>Is Admin?</th><th>Can Upload Specimen</th><th>Can Upload Images</th>"
          , "select id, first_name, last_name, email, name, group_id, is_admin, is_upload_specimens, is_upload_images from login where is_upload_specimens = true or is_upload_images = true"
        ));
                
        return queries; // end getLoginNamedQueries()
    }        
   
    private static ArrayList<NamedQuery> getGroupsNamedQueries() {  
        ArrayList<NamedQuery> queries = new ArrayList<NamedQuery>();
         
        queries.add(new NamedQuery(
            "adminLoginIdIsZero"
          , "Admin Login ID is zero"
          , "<th>Group Name</th>"      
          , "select name from ant_group where admin_login_id = 0 and id > 0"
          ));
             
        queries.add(new NamedQuery(
            "groups"
          , "Group data."
          , "<th>Group ID</th><th>Group Name</th><th>Last Upload</th><th>Admin Login ID</th><th>Can Upload Specimen?</th><th>Can Upload Images</th><th>Abbreviation</th>"
          , "select id, name, last_specimen_upload, admin_login_id, is_upload_specimens, is_upload_images, abbrev from ant_group"
        ));
             
                                  
        return queries; // end getGroupsNamedQueries()
    }                 
        
    private static ArrayList<NamedQuery> getGeolocaleNamedQueries() {  
        ArrayList<NamedQuery> queries = new ArrayList<NamedQuery>();

        queries.add(new NamedQuery(
            "invalidValidNamelessCountries"
          , "These countries are listed as not valid but do not have a valid_name"
          , "<th>Name</th>"
          , "select name from geolocale where georank = 'country' and is_valid = 0 and valid_name is null"
          ));

        queries.add(new NamedQuery(
            "usedAndUnusedCoords"
          , "These countries are valid but do not have coords, or are not valid and do have coords."
          , "<th>Name</th><th>Is Valid</th><th>Is UN</th><th>Coords</th>"
          , "select name, is_valid, is_un, coords from geolocale where (is_valid = 0 and coords is not null) or (is_valid = 1 and coords is null) and georank = 'country' order by is_valid, name"
          ));

        queries.add(new NamedQuery(
            "nonAntsInSpeciesListsGeolocale"
           , "Non-formicidae should not be added to geolocale species lists." 
           , "<th>Taxon Name</th><th>Geolocale</th>"
           , "select gt.taxon_name, g.name from geolocale_taxon gt, taxon t, geolocale g where gt.geolocale_id = g.id and gt.taxon_name = t.taxon_name and family != 'formicidae'"
           ));

        /*
          Technique for fixing these is a bit cumbersome. Execute the following, put the resulting dml in text wrangler and do global search and
          replace on the two kinds of single quotes, replacing them with the proper single quote duplicated.  ''
            select concat(concat(concat("update geolocale set name = '", name),"' where id = "), id) as dml from geolocale where name like '%‘%' or name like '%’%'
        */
        queries.add(new NamedQuery(
            "badSingleQuotes"
          , "Two kinds of single quotes that are no good."
          , "<th>ID</th><th>Name</th><th>Source</th><th>Created</th>"
          , "select id, name, source, created from geolocale where name like '%‘%' or name like '%’%'"
           ));
          
        queries.add(new NamedQuery(
            "geolocaleNameWithQuestionMarks"
          , "Question marks should not be in Geolocale names. Diacritic/UTF8 issue?"
          , "<th>ID</th><th>Name</th><th>Parent</th><th>Source</th><th>Created</th>"
		  , "select id, name, parent, source, created from geolocale where name like '%?%'"  
           ));

        queries.add(new NamedQuery(
            "duplicatedGeolocaleNames"
          , "Geolocale Name is not unique. Returned in order of Georank descending."
          , "<th>ID</th><th>Geolocale Name</th><th>Georank</th>"
          , "select id, name, georank from geolocale where name in (select name from geolocale group by name having count(*) > 1) order by georank desc, is_valid desc, name, valid_name"        
        ));

        queries.add(new NamedQuery(
            "lowGeolocaleTaxonCount"
          , "These countries have less than 5 proj_taxon records."
          , "<th>Project Name</th><th>Bioregion</th>"
          , "select id, name, bioregion from geolocale where georank = 'country' and id not in (select geolocale_id from geolocale_taxon group by geolocale_id having count(*) > 5) order by name"
          ));
          
        queries.add(new NamedQuery(
            "unfinishedBioregions"
          , "These are geolocales have not had their bioregions set."
          , "<th>Geolocale Name</th><th>Geolocale - Bioregion</th>"
          , "select g.name, g.georank, g.bioregion from geolocale g where g.georank = 'country' and g.bioregion is null"   
        ));
        
        queries.add(new NamedQuery(
            "nonvalidValidNames"
          , "These 'other' valid names are specified as valid_name, but they are not valid."
          , "<th>Geolocale ID</th><th>Name</th><th>Parent</th><th>Valid Name</th>"
          , "select id, name, parent, valid_name from geolocale where valid_name not in (select name from geolocale where is_valid = 1) and valid_name != 'none'"
        ));
        
        queries.add(new NamedQuery(
            "nonLiveValidCountries"
          , "These countries are flagged as valid but not live."
          , "<th>Geolocale ID</th><th>Name</th><th>is_valid</th><th>is_live</th>"
          , "select id, name, is_valid, is_live from geolocale where georank = 'country' and (is_live = false || is_live is null) and is_valid = 1 order by name"        
        ));
           
        queries.add(new NamedQuery(
            "flickrGeoData"
          , "Bounding box, longitude and latitude are coming from Flickr."
          , "<th>Valid</th><th>Name</th><th>Extent</th><th>Coords</th><th>Bounding Box</th><th>Latitude</th><th>Longitude</th>"
          , "select is_valid, name, extent, coords, bounding_box, latitude, longitude from geolocale where georank = 'country' and is_valid =1 order by name"
          ));
           
        queries.add(new NamedQuery(
            "geolocaleCountryTest"
          , "Show the specimen table countries not in the geolocale table."
          , "<th>Country</th><th>Codes</th>"
          , "select country, group_concat(code) from specimen where country not in (select name from geolocale where georank = 'country') group by country;"
          ));

        queries.add(new NamedQuery(
            "geolocaleAdm1Test"
          , "Show the specimen table adm1 not in the geolocale table."
          , "<th>Country</th><th>Adm1</th>"
          , "select country, adm1, group_concat(code) from specimen where (country, adm1) not in (select parent, name from geolocale where georank = 'adm1') group by country, adm1"
          ));


        queries.add(new NamedQuery(
            "extentAndCoords"
          , "To be verified that the extents and coords in the geolocale table are the ones we like."
          , "<th>Project Name</th><th>P Extent</th><th>P Coords</th><th>Geolocale Name</th><th>G Extent</th><th>G Coords</th>"
          , "select p.project_name, p.extent, p.coords, g.name, g.extent, g.coords from project p, geolocale g where p.geolocale_id = g.id and (p.extent != g.extent or p.coords != g.coords)"
          ));
           
        return queries; // end getGeolocaleNamedQueries()
    }        
            
    private static ArrayList<NamedQuery> getProjectTaxaNamedQueries() {  
        ArrayList<NamedQuery> queries = new ArrayList<NamedQuery>();
            
        queries.add(new NamedQuery(
           "projTaxaWithoutTaxonWithoutHomonym"
          , "Project_taxon records without matching records in the taxon table (without homonym) ."
          , "<th>Project Name</th><th>Taxon Name</th><th>Source</th>"
          ,"select project_name, concat(concat(concat(concat(\"<a href=\'" + AntwebProps.getDomainApp() + "/description.do?taxonName=\", taxon_name), \"\'>\"), taxon_name), \"</a>\"), source from proj_taxon where taxon_name not in (select taxon_name from taxon) and source not in ('worldants', 'fossilants') and taxon_name not in (select taxon_name from homonym) order by project_name, source"
        ));

        queries.add(new NamedQuery(
           "projTaxaWithoutTaxonWithHomonym"
          , "Project_taxon records without matching records in the taxon table (with homonym)."
          , "<th>Project Name</th><th>Taxon Name</th><th>Source</th>"
          ,"select project_name, concat(concat(concat(concat(\"<a href=\'" + AntwebProps.getDomainApp() + "/description.do?taxonName=\", taxon_name), \"\'>\"), taxon_name), \"</a>\"), source from proj_taxon where taxon_name not in (select taxon_name from taxon) and source not in ('worldants', 'fossilants') and taxon_name in (select taxon_name from homonym)order by project_name, source"
        ));

        queries.add(new NamedQuery(
            "notValidTaxaFromSpeciesList"
          , "Taxon name not valid from species list."
          , "<th>Project Name</th><th>Taxon Name</th><th>Status</th>"
          , "select pt.project_name, pt.taxon_name, t.status from proj_taxon pt, taxon t "
            + " where pt.taxon_name = t.taxon_name and t.status != 'valid'" 
            + " and pt.project_name not in ('allantwebants', 'worldants')" 
            + " and t.family = 'formicidae'"
            + " and t.status not in ('morphotaxon')" 
            + " order by pt.project_name"
        ));

        // Species lists using incorrect taxon names
        queries.add(new NamedQuery(
            "incorrectNameInSpeciesList"
          , "Not Valid or Morpho taxa in Species list"
          , "<th>Project Name</th><th>Taxon Name</th><th>Source</th><th>Rank</th><th>Status</th><th>Current Valid Name</th>"      
          , "select pt.project_name, pt.taxon_name, pt.source, t.taxarank, t.status, t.current_valid_name from taxon t, proj_taxon pt where pt.taxon_name = t.taxon_name and t.status != 'valid' and t.status != 'morphotaxon' and pt.project_name != 'worldants' and pt.project_name != 'allantwebants' order by pt.project_name"
          ));
          
        queries.add(new NamedQuery(
            "quadrinomialsInSpeciesLists"
          , "Quadrinomials are allowed in Allantweb and Worldants, but not in species lists (unless through species list upload?)"
          , "<th>Taxon Name</th><th>Project Name</th><th>Source</th><th>Created</th>"
          , "select taxon_name, project_name, source, created from proj_taxon where project_name not in ('worldants', 'allantwebants') and length(taxon_name) - length(replace(taxon_name, ' ', '')) > 2"
          ));
         
                                        
        queries.add(new NamedQuery(
            "nonAntsInSpeciesListsProject"
           , "Non-formicidae should not be added to project species lists (aside from allantwebants)." 
           , "<th>Taxon Name</th><th>Project Name</th>"
           , "select pt.taxon_name, pt.project_name from proj_taxon pt, taxon t where pt.taxon_name = t.taxon_name and family not like 'form%' and project_name != 'allantwebants'"
           ));

        queries.add(new NamedQuery(
            "speciesListHomonyms"
          , "These homonyms were included in the following projects:"
          , "<th>Homonym</th><th>Project</th>"
          , "select pt.taxon_name, pt.project_name from proj_taxon pt where project_name not in ('allantwebants', 'worldants') " 
            + " and taxon_name not in ('formicinaecamponotus')" 
            + " and taxon_name in (select taxon_name from homonym) order by project_name, taxon_name;"
          ));

        queries.add(new NamedQuery(
            "morphoProjTaxa"
          , "These are morphotaxa in proj_taxon."
          , "<th>Taxon Name</th><th>Project Name</th><th>Source</th><th>Created</th>"
          , "select taxon_name, project_name, source, created from proj_taxon where (taxon_name like '%?%' or taxon_name like '%1%' or taxon_name like '%2%' or taxon_name like '%3%' or taxon_name like '%4%' or taxon_name like '%5%' or taxon_name like '%6%' or taxon_name like '%7%' or taxon_name like '%8%' or taxon_name like '%9%' or taxon_name like '%-%' or taxon_name like '%\\_%' or taxon_name like '%(%' or taxon_name like '%)%' or taxon_name like '%.%') and source is null and project_name not in ('worldants', 'allantwebants')"        
        ));
        
        // These are moved to java code and automated.        
        // Maybe these are better. Needed for proj_taxon, yes? geolocaleTaxaMorphosWithNoSpecimen is like speciesListMorphotaxa. Need actions...
        queries.add(new NamedQuery(
           "projTaxaMorphosWithNoSpecimen"
          , "Proj_Taxon records that are morphos must have specimen data. Refer to Queries.java for corrective action."
          , "<th>Taxon Name</th><th>Project Name</th><th>Source</th>"
          , "select pt.taxon_name, pt.project_name, pt.source from proj_taxon pt where pt.taxon_name in (select taxon_name from taxon t where t.taxarank in ('species', 'subspecies') and t.status = 'morphotaxon') and pt.taxon_name not in (select taxon_name from specimen)"
        //  , "deleteProjTaxaMorphosWithNoSpecimen"
        ));
        /* Something like this...     
        namedQueries.add(new NamedQuery(
            "deleteProjTaxaMorphosWithNoSpecimen"
          , "Delete the proj_taxon records for morphotaxa that are in project lists that do not have correlated specimen data."
          , "<th>Taxon Name</th><th>Project Name</th>"
          , "delete from proj_taxon where project_name = 'XXX' and taxon_name in (select taxon_name from taxon t where t.taxarank in ('species', 'subspecies') and t.status = 'morphotaxon') and taxon_name not in (select taxon_name from specimen)"
        ));
        */

        queries.add(new NamedQuery(
            "notValidProjectTaxonNames"
          , "These Proj_taxon records have taxon names that are not currently valid."
          , "<th>Project Name</th><th>Taxon Name</th><th>Status</th><th>Current Valid Name</th>"
          , "select p.project_name, t.taxon_name, t.status, t.current_valid_name from taxon t, proj_taxon pt, project p where t.taxon_name = pt.taxon_name and pt.project_name = p.project_name and t.status != 'valid' and p.project_name != 'allantwebants' and p.project_name != 'worldants' and t.status != 'morphotaxon' order by project_name, taxon_name"
        ));


        // These queries are to support the bad projTaxon counts bug. Removable down the road...
        // For the Query Battery: projectTaxonCounts
        // /query.do?action=queryBattery&name=projectTaxonCounts
        queries.add(new NamedQuery(
            "projectTaxaCount"
          , "The count of proj_taxon records."
          , "<th>Count</th>"
          , "select count(*) from proj_taxon"
        ));
        queries.add(new NamedQuery(
            "projectTaxaCountByProject"
          , "The count of proj_taxon records by project."
          , "<th>Project Name</th><th>Count</th>"
          , "select project_name, count(taxon_name) from proj_taxon group by project_name"
        ));
        queries.add(new NamedQuery(
            "projectTaxaCountByProjectRank"
          , "The count of proj_taxon records."
          , "<th>Project Name</th><th>Rank</th><th>Source</th><th>Count</th>"
        //  , "select project_name, taxarank, count(pt.taxon_name) from proj_taxon pt, taxon t where pt.taxon_name = t.taxon_name group by project_name, taxarank"
        //  , "select project_name, taxarank, pt.source, count(pt.taxon_name) from proj_taxon pt, taxon t where pt.taxon_name = t.taxon_name and project_name = 'allantwebants' group by project_name, taxarank, pt.source"
          , "select taxarank, project_name, pt.source, count(pt.taxon_name) from proj_taxon pt, taxon t where pt.taxon_name = t.taxon_name and project_name in ('worldants', 'allantwebants') group by project_name, taxarank, pt.source order by taxarank, project_name, source"
        ));
        


        // A list of the proj_taxon taxa created from proxyworldants
        // select taxon_name, status from taxon where taxon_name in (select taxon_name from proj_taxon where source like "proxy%");        

        // This shows that after specimen upload, not all worldants records are existing in allantweb ants. Could affect counts.
        // select rank, project_name, pt.source, count(pt.taxon_name) from proj_taxon pt, taxon t where pt.taxon_name = t.taxon_name and project_name in ('worldants', 'allantwebants') group by project_name, taxarank, pt.source order by rank, project_name, source;

        
        /*
        queries.add(new NamedQuery(
            "statusSpecimenCount"
          , "The count of proj_taxon records."
          , "<th>Status</th><th>Count</th>"
          , "select status, count(distinct taxon_name) from specimen where access_group = 16 group by status;"
        ));
        */
        
        return queries; // end getProjectTaxonNamedQueries()
    }        

    private static ArrayList<NamedQuery> getGeolocaleTaxaNamedQueries() {  
        ArrayList<NamedQuery> queries = new ArrayList<NamedQuery>();
 
        queries.add(new NamedQuery(
            "badGeolocaleTaxonParentTaxonName"
          , "These taxa have a parent_taxon_name that does not exist in the geolocale_taxon table. "
          , "<th>TaxonName</th><th>Geolocale ID</th><th>Geolocale Name</th><th>Source</th><th>Parent Taxon Name</th><th>Created</th>"
          , "select gt.taxon_name, gt.geolocale_id, (select name from geolocale where id = gt.geolocale_id), gt.source, t.parent_taxon_name, gt.created "
          + " from geolocale_taxon gt, taxon t   where gt.taxon_name = t.taxon_name "
          + " and (t.parent_taxon_name, gt.geolocale_id) not in (select taxon_name, geolocale_id from geolocale_taxon) " 
          + " and t.parent_taxon_name != 'hymenoptera'"
          + " order by gt.source, gt.taxon_name"
        ));
        /*
        select gt.taxon_name, gt.geolocale_id, (select distinct name from geolocale where id = gt.geolocale_id), gt.source, (select distinct id from geolocale where name = g.parent) as parentId 
        from geolocale_taxon gt, geolocale g where gt.geolocale_id = g.id 
        and (gt.taxon_name, parentId) not in (select taxon_name, geolocale_id from geolocale_taxon) 
        order by gt.source, gt.taxon_name;
        */
        
        queries.add(new NamedQuery(
           "geolocaleTaxaMorphosWithNoSpecimen"
          , "Geolocale_Taxon records that are morphos must have specimen data. Refer to Queries.java for corrective action."
          , "<th>Taxon Name</th><th>Geolocale Name</th><th>Source</th>"
          , "select t.taxon_name, g.name, gt.source from geolocale_taxon gt, taxon t, geolocale g where g.id = gt.geolocale_id and gt.taxon_name = t.taxon_name and t.taxarank in ('species', 'subspecies') and t.status = 'morphotaxon' and t.taxon_name not in (select taxon_name from specimen)"
        ));


        queries.add(new NamedQuery(
            "geolocaleListMorphotaxa"
          , "Show the morphotaxa that are in geolocales that do not have specimen."
          , "<th>Taxon Name</th><th>Geolocale Name</th><th>Geolocale Id</th><th>Created</th><th>Source</th>"
          , "select "
          + " concat(concat(concat(concat(\"<a href=\'" + AntwebProps.getDomainApp() + "/browse.do?taxonName=\", taxon_name), \"\'>\"), taxon_name), \"</a>\") "                      
          + ", name, geolocale_id, geolocale_taxon.created, geolocale_taxon.source " 
          + " from geolocale_taxon, geolocale where geolocale_taxon.geolocale_id = geolocale.id and taxon_name in (select taxon_name from taxon where status = 'morphotaxon')  and taxon_name not in (select distinct taxon_name from specimen) order by georank, name"
          , "deleteGeolocaleListMorphotaxa"
          ));

        queries.add(new NamedQuery(
            "deleteGeolocaleListMorphotaxa"
          , "Delete the morphotaxa that are in geolocales that do not have specimen."
          , "<th>Taxon Name</th><th>Geolocale Name</th><th>Geolocale Id</th>"
          , "delete from geolocale_taxon where taxon_name in (select taxon_name from taxon where status = 'morphotaxon')  and taxon_name not in (select distinct taxon_name from specimen)"
          ));

        queries.add(new NamedQuery(
           "geolocaleTaxaFromInvalidTaxonName"
          , "Show the geolocale taxon records that have geolocale records that are not valid or morphos.."
          , "<th>Taxon Name</th><th>Status</th><th>Current Valid Name</th><th>Source</th><th>Parent</th><th>Name</th><th>Geolocale ID</th>"
          , "select gt.taxon_name, t.status, t.current_valid_name, gt.source, g.parent, g.name, geolocale_id from geolocale_taxon gt, geolocale g, taxon t where gt.geolocale_id = g.id and gt.taxon_name = t.taxon_name and t.taxon_name not in (select taxon_name from taxon where status in ('valid', 'morphotaxon')) order by taxon_name, parent, name"
        ));
 
        queries.add(new NamedQuery(
           "geolocaleTaxaNotUsingCurrentValidName"
          , "These geolocale could be updated to use the taxon's current valid name."
          , "<th>Geolocale ID</th><th>Taxon Name</th><th>Status</th><th>Current Valid Name</th>"
          , "select geolocale_id, gt.taxon_name, t.status, t.current_valid_name from geolocale_taxon gt, taxon t where gt.taxon_name = t.taxon_name and t.taxon_name not in (select taxon_name from taxon where status in ('valid', 'morphotaxon')) and t.current_valid_name is not null and t.taxon_name != t.current_valid_name"
        ));

        queries.add(new NamedQuery(
           "geolocaleTaxaWithoutTaxon"
          , "These distinct taxon_names of geolocale_taxon records do not have corresponding taxon records."
          , "<th>Taxon Name</th><th>Max Created</th><th>Source</th>"
          , "select taxon_name, max(created), source from geolocale_taxon where taxon_name not in (select taxon_name from taxon) group by taxon_name, source order by source, max(created) desc, taxon_name"
          , "geolocaleTaxaWithoutTaxonDetail"
        ));
  
        queries.add(new NamedQuery(
           "geolocaleTaxaWithoutTaxonDetail"
          , "These geolocale_taxon records do not have corresponding taxon records."
          , "<th>Source</th><th>Taxon Name</th><th>Geolocale Name</th><th>Created</th><th>Insert Method</th>"
          , "select geolocale_taxon.source, taxon_name, name, geolocale_taxon.created, insert_method from geolocale_taxon, geolocale where geolocale_taxon.geolocale_id = geolocale.id and taxon_name not in (select taxon_name from taxon) order by source, taxon_name, geolocale.georank desc"
          , "deleteGeolocaleTaxaWithoutTaxon"
        ));

        queries.add(new NamedQuery(
            "deleteGeolocaleTaxaWithoutTaxon"
          , "Delete the geolocale_taxon records that do not have corresponding taxon records."
          , "<th>Taxon Name</th><th>Geolocale Name</th><th>Geolocale Id</th>"
          , " delete from geolocale_taxon where taxon_name not in (select taxon_name from taxon)"
              + " and source in ('curator', 'speciesList', 'speciesListTool')"
          ));
    
        queries.add(new NamedQuery(
           "geolocaleTaxaInMultipleBioregions"
          , "Non-introduced geolocale taxa found in multiple bioregions."
          , "<th>Taxon Name</th><th>Bioregion Count</th><th>Bioregions</th>"
          , "select "
            + " concat(concat(concat(concat(\"<a href=\'" + AntwebProps.getDomainApp() + "/description.do?taxonName=\", t.taxon_name), \"\'>\"), t.taxon_name), \"</a>\") "                      
            + ", count(distinct g.bioregion), group_concat(distinct concat(' ', g.bioregion)) from geolocale_taxon gt, geolocale g, taxon t where g.id = gt.geolocale_id and gt.taxon_name = t.taxon_name " 
            + " and t.taxon_name not in (select taxon_name from proj_taxon where project_name = 'introducedants')"
            + " and t.taxarank in ('species', 'subspecies') group by gt.taxon_name having count(distinct g.bioregion) > 2 order by count(distinct g.bioregion) desc"
        ));
                
        queries.add(new NamedQuery(
            "notValidGeolocaleTaxonNames"
          , "These Geolocale_taxon records have taxon names that are not currently valid."
          , "<th>Geolocale Name</th><th>Taxon Name</th><th>Status</th><th>Current Valid Name</th>"
          , "select g.name, t.taxon_name, t.status, t.current_valid_name from taxon t, geolocale_taxon gt, geolocale g where t.taxon_name = gt.taxon_name and gt.geolocale_id = g.id and t.status != 'valid' and status != 'morphotaxon' order by name, taxon_name"
        ));
    
        return queries; // end getGeolocaleTaxaNamedQueries()
    }                 

    private static ArrayList<NamedQuery> getBioregionTaxaNamedQueries() {  
        ArrayList<NamedQuery> queries = new ArrayList<NamedQuery>();
 
         queries.add(new NamedQuery(
            "notValidBioregionTaxonNames"
          , "These Bioregion_taxon records have taxon names that are not currently valid."
          , "<th>Bioregion Name</th><th>Taxon Name</th><th>Status</th><th>Current Valid Name</th>"
          , "select b.name, t.taxon_name, t.status, t.current_valid_name from taxon t, bioregion_taxon bt, bioregion b where t.taxon_name = bt.taxon_name and bt.bioregion_name = b.name and t.status != 'valid' and status != 'morphotaxon' order by name, taxon_name"
        ));

        return queries; // end getBioregionTaxaNamedQueries()
    }     

    private static ArrayList<NamedQuery> getMuseumTaxaNamedQueries() {  
        ArrayList<NamedQuery> queries = new ArrayList<NamedQuery>();
    
        queries.add(new NamedQuery(
            "notValidMuseumTaxonNames"
          , "These Museum_taxon records have taxon names that are not currently valid."
          , "<th>Museum Name</th><th>Taxon Name</th><th>Status</th><th>Current Valid Name</th>"
          , "select m.name, t.taxon_name, t.status, t.current_valid_name from taxon t, museum_taxon mt, museum m where t.taxon_name = mt.taxon_name and mt.code = m.code and t.status != 'valid' and status != 'morphotaxon' order by name, taxon_name"
        ));    
        return queries; // end getMuseumTaxaNamedQueries()
    }         

    private static ArrayList<NamedQuery> getImageNamedQueries() {  
        ArrayList<NamedQuery> queries = new ArrayList<NamedQuery>();
 
        queries.add(new NamedQuery(
          "imageCountByOwner"
        , "Count of Images by Owner"
        , "<th>Image Count</th><th>Owned By</th>"
        , "select count(i.id) theCount, s.ownedby from image i, specimen s where i.image_of_id = s.code group by s.ownedBy order by theCount desc"
        ));

        queries.add(new NamedQuery(
           "imageData"
          , "All Image Data"
          , "<th>Specimen</th><th>Shot</th><th>Number</th><th>Upload Date</th><th>Photographer</th><th>Uploaded By</th><th></th>"
          , "select specimen.code, image.shot_type, image.shot_number, image.upload_date, artist.name, ant_group.name from  ant_group, artist, group_image, image left join specimen on  specimen.code = image.image_of_id  where group_image.image_id = image.id  and ant_group.id=group_image.group_id  and image.upload_date is not null   and artist.id = image.artist    order by image.upload_date desc, specimen.code, image.shot_type, image.shot_number"
        ));

        return queries; // end getImageNamedQueries()
    }         

    private static ArrayList<NamedQuery> getAssortedNamedQueries() {

        ArrayList<NamedQuery> queries = new ArrayList<NamedQuery>();

        queries.add(new NamedQuery(
                "descriptionEditCountByOwner"
                , "Count of Description Edits by Owner"
                , "<th>Description Edit Count</th><th>Owned By</th>"
                , "select count(distinct de.taxon_name, de.title) theCount, g.name from description_edit de, ant_group g where de.access_group = g.id group by de.access_group order by theCount desc"
        ));

        queries.add(new NamedQuery(
                "importedCountryData"
                , "Imported Country Data"
                , "<th>Country</th><th>UN Region</th><th>UN Subregion</th><th>Bioregion</th>"
                , "select name, un_region, un_subregion, bioregion from un_country"
        ));

        queries.add(new NamedQuery(
                "worldantsUploads"
                , "Worldants uploads since May of 2019."
                , "<th>Created</th><th>Operation</th><th>Message</th><th>File Size</th><th>Worldants Count</th><th>Exec Time</th><th>Log File</th>"
                , "SELECT created, operation, validate_message, file_size, orig_worldants_count, exec_time "
                + ", concat(concat(\"<a href=\'" + AntwebProps.getDomainApp() + "/web/log/worldants/\",log_file_name), \"'>Log file</a>\") "
                + " FROM worldants_upload order by created desc"
        ));

        queries.add(new NamedQuery(
                "ancillaryFiles"
                , "Ancillary file list"
                , "<th>ID</th><th>Title</th><th>FileName</th><th>Directory</th><th>Last Changed</th><th>Project Name</th>"
                , "select id, title, fileName, directory, last_changed, project_name, access_login from ancillary"
        ));

        queries.add(new NamedQuery(
                "adminAlerts"
                , "These are all of the Admin Alerts over time."
                , "<th>ID</th><th>Alert</th><th>Acknowledged</th><th>Created</th>"
                , "select id, alert, acknowledged, created from admin_alerts order by acknowledged, created desc, id"
        ));


        queries.add(new NamedQuery(
                "lastSpecimenUpload"
                , "Specimen uploads ordered by date."
                , "<th>Group Name</th><th>Count</th><th>Last Upload</th><th>Reload</th>"
                , "select g.name, count(distinct code), max(s.created)"
                + ", concat(concat(concat(concat(\"&nbsp;&nbsp;&nbsp;<a href=\'" + AntwebProps.getDomainApp() + "/upload.do?action=reloadSpecimenList&groupId=\", g.id), \"\'>\"), 'run'), \"</a>\") "
                + " from specimen s, ant_group g where s.access_group = g.id group by access_group order by max(s.created) desc"
        ));
        /* namedQueries.add(new NamedQuery(
              "lastSpecimenUpload"
             , "Specimen uploads ordered by date."
             , "<th>Group Name</th><th>Count</th><th>Emails</th><th>Last Upload</th><th>Reload</th>"
             , "select g.name, count(distinct code), substring(group_concat(distinct email), 1, 60), max(s.created)" 
             + ", concat(concat(concat(concat(\"&nbsp;&nbsp;&nbsp;<a href=\'" + AntwebProps.getDomainApp() + "/upload.do?action=reloadSpecimenList&groupId=\", g.id), \"\'>\"), 'run'), \"</a>\") " 
             + " from specimen s, ant_group g, login l where s.access_group = g.id and g.id = l.group_id group by access_group order by max(s.created) desc"
           ));
        */

        queries.add(new NamedQuery(
                "emptySourceTaxonSets"
                , "Taxon sets with empty source values."
                , "<th>Key</th><th>Taxon Name</th><th>Source</th><th>Created</th>"
                , "select project_name, taxon_name, source, created from proj_taxon where source = '' and taxon_name not in ('incertae_sedis', '(formicidae)')"
                + " union select geolocale_id, taxon_name, source, created from geolocale_taxon where source = '' and taxon_name not in ('incertae_sedis', '(formicidae)')"
                + " union select bioregion_name, taxon_name, source, created from bioregion_taxon where source = '' and taxon_name not in ('incertae_sedis', '(formicidae)')"
        ));

        queries.add(new NamedQuery(
                "geolocaleTaxonDisputes"
                , "Taxa that are thought to not exist in a given geolocale."
                , "<th>Geolocale</th><th>Geolocale ID</th><th>Taxon Name</th><th>Source</th><th>Curator</th><th>Created</th>"
                , "select g.name, g.id, gtd.taxon_name, gtd.source, gtd.curator_id, gtd.created from geolocale_taxon_dispute gtd, geolocale g where gtd.geolocale_id = g.id order by created desc"
        ));
        queries.add(new NamedQuery(
                "projectTaxonDisputes"
                , "Taxa that are thought to not exist in a given project."
                , "<th>Project</th><th>Taxon Name</th><th>Source</th><th>Curator</th><th>Created</th>"
                , "select p.project_name, ptd.taxon_name, ptd.source, ptd.curator_id, ptd.created from proj_taxon_dispute ptd, project p where ptd.project_name = p.project_name order by created desc"
        ));

        // These are now automated to dissappear.
        queries.add(new NamedQuery(
                "conflictedDefaultImages"
                , "Taxa that have default images set from specimen of different taxa."
                , "<th>taxon_name</th><th>prop</th><th>value</th><th>created</th><th>login_id</th><th>Specimen Taxon Name</th>"
                , "select tp.taxon_name, tp.prop, tp.value, tp.created, tp.login_id, specimen.taxon_name from taxon_prop tp, specimen where tp.value = code and tp.prop like '%Specimen' and tp.taxon_name != specimen.taxon_name"
        ));

        return queries; // end getAssortedNamedQueries()
    }

    // Invoke like: /util.do?action=curiousQuery&name=nonAsciiTaxonName
    public static ArrayList<NamedQuery> getNamedQueries() {        
        
        ArrayList<NamedQuery> namedQueries = new ArrayList<NamedQuery>();

        namedQueries.addAll(getTaxaNamedQueries());
        namedQueries.addAll(getHomonymNamedQueries());
        namedQueries.addAll(getSpecimenNamedQueries());
        namedQueries.addAll(getFossilNamedQueries());
        namedQueries.addAll(getLoginNamedQueries());
        namedQueries.addAll(getGroupsNamedQueries());
        namedQueries.addAll(getGeolocaleNamedQueries());
        namedQueries.addAll(getProjectTaxaNamedQueries());
        namedQueries.addAll(getGeolocaleTaxaNamedQueries());
        namedQueries.addAll(getBioregionTaxaNamedQueries());
        namedQueries.addAll(getMuseumTaxaNamedQueries());
        namedQueries.addAll(getImageNamedQueries());
        namedQueries.addAll(getAssortedNamedQueries());
        return namedQueries;
    }

    // Invoke like: /util.do?action=curiousQuery&name=nonAsciiTaxonName
    public static String getQueryManagerPage() {        
        String page = "<h2>&nbsp;Query Manager <img width='23' src='" + AntwebProps.getDomainApp() + "/image/new1.png'></h2>";
        page += "&nbsp;&nbsp;&nbsp;&nbsp;Old Query Manager is: <a href='" + AntwebProps.getDomainApp() + "/queries.do'>here</a><br>";
        page += getQueryManagerBlock("Taxon Queries", getTaxaNamedQueries());
        page += getQueryManagerBlock("Homonym Queries", getHomonymNamedQueries());
        page += getQueryManagerBlock("Specimen Queries", getSpecimenNamedQueries());
        page += getQueryManagerBlock("Fossil Queries", getFossilNamedQueries());
        page += getQueryManagerBlock("Login Queries", getLoginNamedQueries());
        page += getQueryManagerBlock("Group Queries", getGroupsNamedQueries());
        page += getQueryManagerBlock("Geolocale Queries", getGeolocaleNamedQueries());
        page += getQueryManagerBlock("Project Taxon Queries", getProjectTaxaNamedQueries());
        page += getQueryManagerBlock("Geolocale Taxon Queries", getGeolocaleTaxaNamedQueries());
        page += getQueryManagerBlock("Bioregion Taxon Queries", getBioregionTaxaNamedQueries());
        page += getQueryManagerBlock("Museum Taxon Queries", getMuseumTaxaNamedQueries());
        page += getQueryManagerBlock("Image Queries", getImageNamedQueries());
        page += getQueryManagerBlock("Assorted Queries", getAssortedNamedQueries());

        return page;
    }
    public static String getQueryManagerBlock(String title, ArrayList<NamedQuery> queries) {        
        String block = "";
        block += "<br><br><h3>&nbsp;<u>" + title + "</u></h3>";
        for (NamedQuery namedQuery : queries) {
          block += "&nbsp;&nbsp;&nbsp;&nbsp;<a href='" + AntwebProps.getDomainApp() + "/query.do?name=" + namedQuery.getName() + "'>" 
            + namedQuery.getName() + "</a> - " + namedQuery.getDesc() + "<br>" ;
        }         
        return block;
    }
    
 //   http://localhost/antweb/query.do?name=noSubfamilyForSubfamily

    public static String getQueryList(String listName) {
      String html = "";
      ArrayList<String> queries = null;
      if ("Geolocale".equals(listName)) queries = Queries.getGeolocaleQueries();    
      if ("Login".equals(listName)) queries = Queries.getLoginQueries();    
      if ("Groups".equals(listName)) queries = Queries.getGroupQueries();    
      int i = 0;
      if (queries == null) {
        s_log.warn("QueryList:" + listName + " not found.");
        return null;
      }
      for (String query : queries) {
        if (i == 0) html += "<ul align=left>";
        ++i;
        html += "<li><a href='" + AntwebProps.getDomainApp() + "/util.do?action=curiousQuery&name=" + query +"'>" + query + "</a>";
        if (i == queries.size()) html += "</ul>";
      }
      return html;
    }    
    
    public static NamedQuery getNamedQuery(String name) {
      ArrayList<NamedQuery> namedQueries = getNamedQueries();
      for (NamedQuery namedQuery : namedQueries) {
        if (name.equals(namedQuery.getName())) return namedQuery;
      }
      return null;     
    }    
    
    public static ArrayList<NamedQuery> getNamedQueryList(String[] list) {
        ArrayList<NamedQuery> namedQueries = new ArrayList<NamedQuery>();

        ArrayList<String> names = new ArrayList<String>(Arrays.asList(list));
        
        for (String name : names) {
          for (NamedQuery namedQuery : getNamedQueries()) {
            if (name.equals(namedQuery.getName())) {
              namedQueries.add(namedQuery);          
              break;
            }
          }
        }
        return namedQueries;    
    }   
}

