    package org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class TaxonMgr extends Manager {

    private static final Log s_log = LogFactory.getLog(TaxonMgr.class);

    private static ArrayList<Taxon> s_subfamilies = null;
    
    private static HashMap<String, Taxon> s_genera = null;
    
    private static HashMap<String, Taxon> s_species = null;

    // Shallow copies
    private static HashMap<String, Taxon> s_taxa = null;

    private static HashMap<String, ArrayList<String>> s_subgenusHashMap = new HashMap<>();

    //private static List<String> taxaNamesList = null;
    private static List<String> prettyTaxaNamesList = null;

    private static Date s_populateTime = null;

    public static void populate(Connection connection, boolean forceReload, boolean initialRun) throws SQLException {
        if (!forceReload && (s_subfamilies != null)) return;

        TaxonDb taxonDb = new TaxonDb(connection);
        s_subfamilies = taxonDb.getTaxa(Rank.SUBFAMILY);
        //A.log("populate() subfamilies:" + s_subfamilies);

        ArrayList<Taxon> genera = taxonDb.getTaxa(Rank.GENUS);
        s_genera = new HashMap<>();

        //A.log("populate() genera.size:" + genera.size());
        for (Taxon taxon : genera) {
            //if ("apterogyna".equals(taxon.getGenus())) A.log("populate() genus:" + taxon);
            s_genera.put(taxon.getTaxonName(), taxon);
        }

        // For Taxon Name Search Autocomplete
        prettyTaxaNamesList = new ArrayList<>();
        prettyTaxaNamesList.addAll(CommonNames.getNames());

        List<String> taxaNamesList = taxonDb.getTaxonNames();
        for (String taxonName : taxaNamesList) {
            prettyTaxaNamesList.add(Taxon.getPrettyTaxonName(taxonName));
        }

        s_taxa = new HashMap<>();
        ArrayList<Taxon> taxa = taxonDb.getTaxa();
        for (Taxon taxon : taxa) {
            s_taxa.put(taxon.getTaxonName(), taxon);
        }

        s_subgenusHashMap = taxonDb.getSubgenusHashMap();

        s_populateTime = new Date();

        s_log.warn("populate() " + report());
       // if (AntwebProps.isDevMode()) AntwebUtil.logShortStackTrace();
    }

    public static String report() {
        return "TaxonMgr last loaded:" + s_populateTime + " subfamily:" + getSubfamilies().size() + " genera:" + s_genera.size() + " species:" + getSpeciesCount() + " taxa:" + s_taxa.size();
    }

    public static int getSpeciesCount() {
        int c = 0;
        for (Taxon t : s_taxa.values()) {
            if ("species".equals(t.getRank())) c = c + 1;
        }
        return c;
    }

    //Called through UtilAction to, in a separate thread.
    public static void postInitialize(Connection connection) throws SQLException {
    }
    
    public static ArrayList<Taxon> getSubfamilies() {
      //A.log("getSubfamilies() TaxonMgr.subfamilies:" + s_subfamilies);
      return s_subfamilies;
    }
    
    public static Subfamily getSubfamily(String subfamilyName) {
      if (subfamilyName == null) return null;
      Subfamily subfamily = null;
      for (Taxon taxon : getSubfamilies()) {
        if (subfamilyName.equals(taxon.getSubfamily())) return (Subfamily) taxon;
      }
      return null;
    }

    public static ArrayList<Genus> getGenera() {
      ArrayList<Genus> genera = new ArrayList<>();
      for (Taxon genus : s_genera.values()) {
        genera.add((Genus)genus);
      }
      Collections.sort(genera);
      return genera;
    }

    private static boolean s_ambiguousGenusReported = false;
    // Used from Specimen upload and specimen-body.jsp
    // genusTaxonName is [subfamily][genus]
    public static Genus getGenus(String genusTaxonName) {
        //ProfileCounter.add("TaxonMgr.getGenus()" + AntwebUtil.getShortStackTrace());

      if (genusTaxonName == null) return null;
      if (s_genera == null) return null; // Could happen due to server initialization.
      return (Genus) s_genera.get(genusTaxonName);
    }

    public static Genus getGenusFromName(String genusName) {
      //ProfileCounter.add("TaxonMgr.getGenusFromName()" + AntwebUtil.getShortStackTrace());

      if (genusName == null) return null;

      Taxon genus = null;
      //A.log("getGenus() s_genera:" + s_genera.size());

      boolean genusFound = false;
      s_ambiguousGenusReported = false;
      for (Taxon g : s_genera.values()) {
        //A.log("getGenus() genusTaxonName:" + genusName + " genus:" + genus.getName());
        if (genusName.equals(g.getName())) {
            genus = g;
            if (genusFound == true && !s_ambiguousGenusReported) {
                s_ambiguousGenusReported = true;
                s_log.warn("getGenusFromName(). AMBIGUOUS! Found a second genus with genusName:" + genusName + ". generaSize:" + s_genera.size());
                AntwebUtil.logShortStackTrace();
            }
            genusFound = true;
            // Keep looping. Small performance cost but if we find another, ambiguity bad.
        }
      }
      if (genusFound) {
          return (Genus) genus;
      }
      return null;
    }

    public static int s_getTaxonCount = 0;
    public static Taxon getTaxon(String taxonName) {

        //ProfileCounter.add("TaxonMgr.getTaxon()" + AntwebUtil.getShortStackTrace());

        if (taxonName == null) return null;
        if (s_taxa == null) {
            A.log("getTaxon() returning null because s_taxa is null");
            // Could happen due to serverinitialization.
            return null;
        }

        Taxon taxon = s_taxa.get(taxonName);
        if (taxon == null) {
            //A.log("getTaxon() taxon not found:" + taxonName);
            return null;
        }
        //A.log("getTaxon() returning taxon:" + taxon);

        ++s_getTaxonCount;
        return taxon;
    }

    public static String getSubgenus(String taxonName) {
        Taxon taxon = getTaxon(taxonName);
        if (taxon == null) return null;
        return taxon.getSubgenus();
    }

    public static List<String> getSubgenera(String genusName) {
        if (s_subgenusHashMap == null) return null;
        return s_subgenusHashMap.get(genusName);
    }

    // Seems this has been changed. Only now used by TypeStatusMgr and TestAction.
    // !!! Only used by Specimen Upload. (First call will cause a 40 second delay). Nope, used to be slow. Now fast.
    public static Species getSpecies(Connection connection, String taxonName) {
        ProfileCounter.add("TaxonMgr.getSpecies()");

        if (taxonName == null) return null;

      if (s_species == null) {
        s_species = new HashMap<>();
        TaxonDb taxonDb = new TaxonDb(connection);      
        ArrayList<Taxon> species = taxonDb.getTaxa("taxarank in ('" + Rank.SPECIES + "', '" + Rank.SUBSPECIES + "')");
        A.log("getSpecies() speciesCount:" + species.size());
        for (Taxon taxon : species) {
          s_species.put(taxon.getTaxonName(), taxon);
        } 
      }
      return (Species) s_species.get(taxonName);
    }

    /*
    // Deprecated.
    public static Species getSpecies(String taxonName) {
        ProfileCounter.add("TaxonMgr.getSpecies()");

        if (taxonName == null) return null;

        if (s_species == null) {
          A.log("getSpecies() species not loaded. Improper use.");
          return null;
        }
        return (Species) s_species.get(taxonName);
    }
*/

    /*
     2021-11-09 21:50:42,494 WARN http-nio-8080-exec-11 org.calacademy.antweb.util.TaxonMgr - getGenusFromName(). AMBIGUOUS! Found a second genus with genusName:leptanilloides
     2021-11-09 21:50:42,495 WARN http-nio-8080-exec-11 org.calacademy.antweb.util.AntwebUtil - AntwebUtil.logShortStackTrace(6) - org.calacademy.antweb.util.StackTraceException
	at org.calacademy.antweb.util.AntwebUtil.logShortStackTrace(AntwebUtil.java:592)
	at org.calacademy.antweb.util.AntwebUtil.logShortStackTrace(AntwebUtil.java:589)
	at org.calacademy.antweb.util.TaxonMgr.getGenusFromName(TaxonMgr.java:144)
	at org.calacademy.antweb.upload.SpecimenUploadParse.parseLine(SpecimenUploadParse.java:295)
	at org.calacademy.antweb.upload.SpecimenUpload.importSpecimens(SpecimenUpload.java:159)
	at org.calacademy.antweb.upload.SpecimenUploader.uploadSpecimenFile(SpecimenUploader.java:98)

    mysql> select taxon_name, subfamily, genus from taxon where genus = "leptanilloides" and taxarank = "genus";
+-------------------------+-----------+----------------+
| taxon_name              | subfamily | genus          |
+-------------------------+-----------+----------------+
| dorylinaeleptanilloides | dorylinae | leptanilloides |
+-------------------------+-----------+----------------+
1 row in set (0.01 sec)

     */

    // -------------------------------------------------------------------------------------------------------------

/*
    The TaxonMgr has functionality (not ready for production use) designed to improve speed of the specimen file upload.

    In the midst of the upload, there are "getDummyTaxon()" queries that are necessary because we can't trust freshness
    of the taxa in the TaxonMgr object. This implementation (if s_useRefreshedTaxonMgr == true) will fetch the taxon
    from the taxonMgr.

    The performance gains (about 25%) are not as we much as we like because to rely on the TaxonMgr for DummyTaxa we
    need to update the taxonMgr when we save taxa. Should this solution be utilized, it should be verified that other
    methods don't modify taxa in the database that would also require refresh().

    If the taxon data wasn't held in a hashtable, we would simply insert it into the TaxonMgr. The proper solution would
    be to construct taxon objects during the specimen parsing process, instead of a hashtable.

    Could we reliably construct taxon objects from the hashtable to serve the purpose of dummyTaxons? This would require
    a lot less code modification.

    Would need source and taxonomic data at least. Gains are recorded below.

    This functionality might optimize methods AntwebUpload.setStatusAndCurrentValidName(),
    SpecimenUpload.setStatusAndCurrentValidName(), TaxonDb.getCurrentValidTaxonName(getConnection(), currentValidName);
    Results report at the end of SpecimenUpload.importSpecimens();
    Gains as getDummyTaxon() calls are replaced:
    Exec Time: 502 secs (8.366666666666667 min)  // Without s_useRefreshedTaxonMgr
    Exec Time: 459 secs (7.65 min)               // AntwebUpload.setStatusAndCurrentValidName()
    Exec Time: 407 secs (6.783333333333333 min)  // SpecimenUploadProcess.setStatusAndCurrentValidName()
    Exec Time: 377 secs (6.283333333333333 min)  // Added AntwebUpload.enactExceptions()
    Exec Time: 552 secs (9.2 min)   // After Dummy refactoring. Oops.
    Exec Time: 469 secs (7.816666666666666 min)
// after code rollback
    Exec Time: 327 secs (5.45 min)
    Exec Time: 220 secs (3.6666666666666665 min)
    Exec Time: 295 secs (4.916666666666667 min) Finished mass integration.
    Exec Time: 457 secs (7.616666666666666 min) // With TaxonMgr.isUseRefreshing() turned off.

    Recommenation:
      Option 1. This current "Refresh implementation" would need further testing to be sure that other methods don't
        update underlying data, and it must be tested with the Worldants upload". Currently seems to cut upload times by
        25%.
      Option 2. Perhaps the ideal solution is to rewrite the upload process to use Taxon objects instead of Hashtables.
        Then objects could be just copied into the TaxonMgr at save time. Fairly major refactoring - Maybe 2-5 days.
      Option 3. Generate taxon objects from hashtable for insertion into TaxonMgr. A bit faster (because it doesn't
        require database requests to update, and easier to implement, but would require care to ensure that no errors
        are introduced. Would need to assess what member data is used from the taxon objects fetched from the TaxonMgr.

    Note: It would also be good to refactor the two setStatusAndCurrentValidName() methods (one in AntwebUpload and one
      in SpecimenUpload).

    totalRefreshTaxon-update:86598 totalRefreshTaxon-save:8
    SpecimenUploadProcess.SetStatusAndCurrentValidName()A:158415
    SpecimenUploadProcess.SetStatusAndCurrentValidName()B:42
    SpecimenUploadProcess.makeSpecimenUseTaxon():42
        getFamily());
        getSubfamily());
        getTribe());
        getGenus());
        getSubgenus());
        getSpecies());
        getSubspecies());

    TaxonMgr.getGenus(TaxonMgr.java:97)
    at org.calacademy.antweb.home.UploadDb.passGenusSubfamilyCheck(UploadDb.java:544)
    at org.calacademy.antweb.upload.SpecimenUploadProcess.processLine(SpecimenUploadProcess.java:177)
    at org.calacademy.antweb.upload.SpecimenUpload.importSpecimens(SpecimenUpload.java:170):27278
// Existence

    TaxonMgr.getGenus(TaxonMgr.java:97)
    at org.calacademy.antweb.upload.SpecimenUploadProcess.invalidGenusBioregion(SpecimenUploadProcess.java:398)
    at org.calacademy.antweb.upload.SpecimenUploadProcess.processLine(SpecimenUploadProcess.java:291)
    at org.calacademy.antweb.upload.SpecimenUpload.importSpecimens(SpecimenUpload.java:170):209463
// getBioregionMap()

    TaxonMgr.getGenus(TaxonMgr.java:97)
    at org.calacademy.antweb.upload.AntwebUpload.saveTaxon(AntwebUpload.java:202)
    at org.calacademy.antweb.upload.AntwebUpload.saveTaxon(AntwebUpload.java:103)
    at org.calacademy.antweb.upload.AntwebUpload.saveTaxon(AntwebUpload.java:99):8
// Existence

    TaxonMgr.getGenusFromName(TaxonMgr.java:104)
    at org.calacademy.antweb.upload.SpecimenUploadParse.parseLine(SpecimenUploadParse.java:295)
    at org.calacademy.antweb.upload.SpecimenUpload.importSpecimens(SpecimenUpload.java:159)
    at org.calacademy.antweb.upload.SpecimenUploader.uploadSpecimenFile(SpecimenUploader.java:98):232017
// isIndet, subfamily, genus name

    TaxonMgr.getTaxon(TaxonMgr.java:135)
    at org.calacademy.antweb.upload.SpecimenUploadProcess.setStatusAndCurrentValidName(SpecimenUploadProcess.java:457)
    at org.calacademy.antweb.upload.SpecimenUploadProcess.processLine(SpecimenUploadProcess.java:273)
    at org.calacademy.antweb.upload.SpecimenUpload.importSpecimens(SpecimenUpload.java:170):158415
// Status, isQuadranomial, isAnt, currentValidName, etc...

    TaxonMgr.getTaxon(TaxonMgr.java:135)
    at org.calacademy.antweb.upload.AntwebUpload.enactExceptions(AntwebUpload.java:634)
    at org.calacademy.antweb.upload.AntwebUpload.updateTaxon(AntwebUpload.java:372)
    at org.calacademy.antweb.upload.AntwebUpload.saveTaxon(AntwebUpload.java:168):86598
// Source

    TaxonMgr.getTaxon(TaxonMgr.java:135)
    at org.calacademy.antweb.upload.AntwebUpload.saveTaxon(AntwebUpload.java:138)
    at org.calacademy.antweb.upload.AntwebUpload.saveTaxon(AntwebUpload.java:103)
    at org.calacademy.antweb.upload.AntwebUpload.saveTaxon(AntwebUpload.java:99):86606
// Existence. Status.

    TaxonMgr.getTaxon(TaxonMgr.java:135)
    at org.calacademy.antweb.upload.AntwebUpload.saveTaxon(AntwebUpload.java:138)
    at org.calacademy.antweb.upload.AntwebUpload.saveTaxon(AntwebUpload.java:276)
    at org.calacademy.antweb.upload.AntwebUpload.saveTaxon(AntwebUpload.java:103):8
// Status.

    TaxonMgr.getTaxon(TaxonMgr.java:135)
    at org.calacademy.antweb.upload.SpecimenUploadProcess.processLine(SpecimenUploadProcess.java:297)
    at org.calacademy.antweb.upload.SpecimenUpload.importSpecimens(SpecimenUpload.java:170)
    at org.calacademy.antweb.upload.SpecimenUploader.uploadSpecimenFile(SpecimenUploader.java:98):245
// Genus

// For specimenUpload, full set:
//   subfamily, genus, status, source, isQudranomial, isAnt, currentValidName, bioregionMap, isIndet,
    */


    // This is a rough but effective solution. Set true during worldants upload (in UploadAction) and false at the end.
    private static boolean s_isInWorldants = false;
    public static boolean isInWorldants() { return s_isInWorldants; }
    public static void setIsInWorldants(boolean isInWorldants) {
        s_isInWorldants = isInWorldants;
    }

    public static boolean isUseRefreshing() {
        return false;
    }

/*
    // Unless we know we need to refresh, refrain.
    public static boolean s_useRefreshingTaxonMgr = false; //AntwebProps.isDevMode();
    public static boolean isUseRefreshing() {
        boolean val = (s_useRefreshingTaxonMgr && !isInWorldants());
        //A.log("isUseRefreshing() s_use:" + s_useRefreshingTaxonMgr + " isIn:" + isInWorldants() + " val:" + val);
        return val;
    }

    public static int s_refreshTaxonCount = 0;
    public static void refreshTaxon(Connection connection, String operation, String table, String taxonName, Hashtable item) throws SQLException {
        Taxon taxon = null;

        //Really what we want is neither to refresh the taxon from the database OR update it from the hashtable values.
        //We want to update the member data that is needed during the upload process. We can refresh the whole TaxonMgr
        //at the end of the upload.

        if (!"update".equals(operation) && !("save".equals(operation))) {
          return;
        }

        ProfileCounter.add("totalRefreshTaxon-" + operation);

        // We either want to do the dbRefresh method, or construct a taxon for the TaxonMgr from the hashtable.
        // For production use have one or the other set to true. If both true, debug testing for equality.
        // Look at the code to be sure of implementation.
        boolean hashtableRefresh = false;
        boolean dbRefresh = false; //true;

        // updates can not use the hashtableRefresh method.
        if ("update".equals(operation)) {
          hashtableRefresh = false;
          dbRefresh = true;
        }

        Taxon hashTaxon = null;
        if (hashtableRefresh) {
            hashTaxon = Taxon.getTaxon(item);
            taxon = hashTaxon;
        }

        Taxon dbTaxon = null;
        if (dbRefresh) {
            if (taxonName == null) return;
            TaxonDb taxonDb = new TaxonDb(connection);
            dbTaxon = taxonDb.getTaxonForMgr(taxonName);
            if (dbTaxon == null) {
                s_log.error("refreshTaxon() from DB taxon:" + taxonName + " not found.");
                return;
            }
            taxon = dbTaxon;
        }

        // If we care to compare them for validity
        if (AntwebProps.isDevMode() && dbRefresh) { // && hashtableRefresh) {
            //String hashTaxonStr = hashTaxon.toFullString();
            //String dbTaxonStr = dbTaxon.toFullString();
            //boolean equal = hashTaxonStr.equals(dbTaxonStr);
            Taxon mgrTaxon = getTaxon(taxonName);
            if (mgrTaxon == null) {
                s_log.error("refreshTaxon() mgrTaxon not found:" + mgrTaxon);
            } else {
                String diff = mgrTaxon.diff(dbTaxon);
                if (diff != null) A.log("refreshTaxon() taxonName:" + taxonName + " diff(mgr/db):" + diff);
            }
        }

        // Created was null for hashtable.  CurrentValidName was null for hashtable but empty for db.

        //A.log("refreshTaxon() operation:" + operation);
        add(taxonName, taxon);
    }



    public static void add(String taxonName, Taxon taxon) {

        if (s_species != null) {
            if (taxon instanceof Species) s_species.replace(taxonName, taxon);  // Is species used?
        }

        String className = taxon.getClass().toString();
        if (!"class org.calacademy.antweb.Species".equals(className)
                && !"class org.calacademy.antweb.Subspecies".equals(className)) {
            A.log("refreshTaxon() taxon:" + taxon + " class:" + taxon.getClass());
        }
        // Is it ever a subclass? Is it actually ever a genus?

        if (taxon instanceof Genus) s_genera.replace(taxonName, taxon);
        s_taxa.replace(taxonName, taxon);

        s_refreshTaxonCount = s_refreshTaxonCount + 1;
    }
     */

    /*
TaxonMgr.refreshTaxon() equal:false
  FROM HASHTABLE: taxarank:species taxonName:myrmicinaestrumigenys dicomas kindgom:animalia phylum:arthropoda order:hymenoptera class:insecta family:formicidae subfamily:myrmicinae tribe:dacetini genus:strumigenys subgenus:null species:dicomas subspecies:null status:valid groupId:1 source:specimen1.txt lineNum:1 insertMethod:specimenUpload created:null                  fossil:false isType:false isAntCat:false antcatId:0 isPending:false authorDate:null authorDateHtml:null authors:null year:null isAvailable:false currentValidName:null currentValidRank:null currentValidParent:null isOriginalCombination:false wasOriginalCombination:null parentTaxonName:null imageCount:0 holidId:0 chartColor:null defaultMale:null defaultWorker:null defaultQueen:null bioregionMap:null introduceMap:null
  FROM DATABASE:  taxarank:species taxonName:myrmicinaestrumigenys dicomas kindgom:animalia phylum:arthropoda order:hymenoptera class:insecta family:formicidae subfamily:myrmicinae tribe:dacetini genus:strumigenys subgenus:null species:dicomas subspecies:null status:valid groupId:1 source:worldants     lineNum:1 insertMethod:specimenUpload created:2021-09-11 22:01:28.0 fossil:false isType:true  isAntCat:true antcatId:448489 isPending:false authorDate:Fisher, 2000 authorDateHtml:null authors:Fisher year:2000 isAvailable:true currentValidName: currentValidRank:species currentValidParent:strumigenys isOriginalCombination:true wasOriginalCombination:null parentTaxonName:myrmicinaestrumigenys imageCount:0 holidId:0 chartColor:null defaultMale:null defaultWorker:null defaultQueen:null bioregionMap:null introduceMap:null

Changed source, created, isType

TaxonMgr.refreshTaxon() operation:update taxon:dicomas class:class org.calacademy.antweb.Species
 */


    // For Taxon Name Search Autocomplete
    public static List<String> getPrettyTaxaNames(String text) {
        if (prettyTaxaNamesList == null) {
            //A.log("getPrettyTaxaNames(text) initializing...");
            return null;
        }
        if (text == null) {
            s_log.warn("TaxonMgr.getPrettyTaxaNames(text) text is null");
            return null;
        }
        text = text.toLowerCase();
        //A.log("getPrettyTaxaNames(text) text:" + text + " prettyTaxaListSize:" + prettyTaxaNamesList.size());
        String[] texts = text.split(" ");
        List<String> prettyTaxaNamesSubset = new ArrayList<>();
        int i = 0;

        for (String taxonName : prettyTaxaNamesList) {
            boolean containsAll = true;
            for (String s : texts) {
                //log("getPrettyTaxaNames() text:" + text + " j:" + texts[j] + " taxonName:" + taxonName);
                if (!taxonName.toLowerCase().contains(s)) containsAll = false;
                if (!containsAll) break;
            }
            if (containsAll) {
                prettyTaxaNamesSubset.add(taxonName);
                ++i;
                if (i > 5000) break; // Because there are 4600 species in genus Camponotus
            }
        }
        //A.log("getPrettyTaxaNames(q) returning size:" + prettyTaxaNamesSubset.size());
        return prettyTaxaNamesSubset;
    }
}

