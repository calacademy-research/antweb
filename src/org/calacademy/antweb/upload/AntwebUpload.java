package org.calacademy.antweb.upload;

import java.util.Date;
import java.util.*;
import java.sql.*;
import java.text.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;

import org.apache.regexp.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.Formatter;

public class AntwebUpload {
    /**
     * Extended by SpecimenUpload, and SpeciesListUpload
     */

    private Connection m_connection = null;
    private static Log s_log = LogFactory.getLog(AntwebUpload.class);
    static String currentDateFunction = "now()";  // for mysql
    String[] dateHeaderString = {"spcmrecorddate", "spcmrecchangeddate",
            "transecttype", "locrecorddate", "locrecchangeddate"
    };
    ArrayList dateHeaders = new ArrayList(Arrays.asList(dateHeaderString));
    String[] taxonHeaders = {"kingdom_name", "phylum_name", "class_name", "order_name", "family",
            "subfamily", "tribe", "genus", "subgenus", "speciesgroup", "species", "subspecies"
            // worldants
            , "country", "valid", "fossil"
            , "type"
            , "antcat_id"
            , "author_date", "author_date_html", "authors", "year", "status", "available"
            , "original_combination", "was_original_combination", "current_valid_name"
            , "hol_id"
            // new
            // , "taxonomic_history_html  // taxonomichistory is handled in a custom way...
            , "reference_id", "bioregion", "country", "current_valid_rank", "current_valid_parent"
    };

    // Set to null for no action.
    //private static String s_testTaxonName = null; //"myrmicinaenesomyrmex hirtellus";
    private static String s_testTaxonName = "myrmicinaestrumigenys dicomas";

    private TaxonQueryHashMap taxonQueryHashMap = new TaxonQueryHashMap();
    private String lastTaxonName = null;

    private int countUploaded = 0;
    private int uploadSkipped = 0;

    ArrayList goodTaxonHeaders = new ArrayList(Arrays.asList(taxonHeaders));

    UploadDb uploadDb = null;

    private DescCounter m_descCounter = new DescCounter();

    private UploadDetails uploadDetails = null;

    public static int saveSpecimenCount = 0;

/*
	at org.calacademy.antweb.upload.AntwebUpload.<init>(AntwebUpload.java:70)
	at org.calacademy.antweb.upload.SpecimenUploadSupport.<init>(SpecimenUploadSupport.java:25)
	at org.calacademy.antweb.upload.SpecimenUploadProcess.<init>(SpecimenUploadProcess.java:56)
	at org.calacademy.antweb.upload.SpecimenUploadParse.<init>(SpecimenUploadParse.java:66)
	at org.calacademy.antweb.upload.SpecimenUpload.<init>(SpecimenUpload.java:45)
	at org.calacademy.antweb.upload.Uploader.uploadSpecimenFile(Uploader.java:103)
	at org.calacademy.antweb.upload.UploadAction.execute(UploadAction.java:237)
*/

    AntwebUpload(Connection connection, String operation) {
        saveSpecimenCount = 0;

        setUploadDetails(new UploadDetails(operation));
        setConnection(connection);
        uploadDb = new UploadDb(getConnection());
    }

    private UploadDb getUploadDb() {
        return uploadDb;
    }

    protected DescCounter getDescCounter() {
        return m_descCounter;
    }

    protected void saveHomonym(Hashtable item)  //, String source
            throws SQLException {
        //A.log("SaveHomonym item:" + item);
        saveTaxon(item, "homonym");  //source, 
    }

    protected int saveTaxon(Hashtable item) throws SQLException {
        return saveTaxon(item, "taxon");  //source, 
    }

    private int saveTaxon(Hashtable item, String table) throws SQLException {
        return saveTaxon(item, table, false);
    }

    // Method is recursively inserting parent records.
    private int saveTaxon(Hashtable item, String table, boolean isParent) throws SQLException {
        //Called from both SpeciesListUpload and SpecimenUpload.
        int c = 0;

        boolean debugItem = true;

        String taxonName = (String) item.get("taxon_name");

        if (Formatter.hasSpecialCharacter(taxonName)) {
            s_log.warn("saveTaxon() special character found in:" + taxonName);
            getMessageMgr().addToMessages(MessageMgr.specialCharacterFound, taxonName);
            return 0;
        }

        if ((taxonName == null) || (taxonName.length() == 0)) return 0;
        if (lastTaxonName != null && lastTaxonName.equals(taxonName)) return 0;
        lastTaxonName = taxonName;

        String source = (String) item.get("source");
        String rank = (String) item.get("rank");
        String lineNum = "" + item.get("line_num");

        taxonQueryHashMap.setSource(source);

        // if record already exists (in taxon table) then update, if valid.
        if ("taxon".equals(table)) {

            // When called from line 103. CAS: 8
            // When called from line 99. CAS: 86606
            // DummyTaxon dummyTaxon = new TaxonDb(getConnection()).getDummyTaxon(taxonName, "taxon");
            // Always look to the database. This determines if we insert or update.

            Taxon dummyTaxon = new TaxonDb(getConnection()).getTaxon(taxonName);
            //A.log("saveTaxon() dummyTaxon:" + dummyTaxon + " source:" + source + " taxonName:" + taxonName);

            if (dummyTaxon != null) {
                if (isParent) {
                    return 0; // Already exists. Great.
                } else {
                    //A.iLog(3, "saveTaxon() update taxonName:" + taxonName + " lineNum:" + lineNum, 1000);

                    // Unresolved junior homonyms from worldants are getting inserted (technically updated).
                    if ("worldants".equals(source)) {
                        String status = (String) item.get("status");

                        if (!Status.VALID.equals(status) && !Status.UNRECOGNIZED.equals(dummyTaxon.getStatus())) {
                            // log. Nope. Won't update a non-valid taxon from worldants.
                            String display = taxonName + " " + status;
                            getMessageMgr().addToMessages(MessageMgr.nonValidWorldantsDup, display);

                            if (Status.HOMONYM.equals(status) || Status.SYNONYM.equals(status)) {
                                saveTaxon(item, "homonym", false);
                            } else {
                                //A.log("saveTaxon() do nothing with duplicate original combination:" + display);
                            }

                            return 0;
                        }
                    }
                    //A.log("saveTaxon() dummyTaxon:" + dummyTaxon + " item:" + item + " table:" + table);

                    updateTaxon(item, table);
                    return 0;
                }
            } else {
                if (isParent) {
                    s_log.debug("saveTaxon() 1 parent doesn't exist rank:" + rank + " taxonName:" + taxonName + " so creating it.");
                }
            }
        }

        // This would cause an NPE for a specimen upload file
        //String lineNum = ((Integer) item.get("line_num")).toString();

        String status = (String) item.get("status");
        String authorDate = (String) item.get("author_date");
        authorDate = UploadUtil.cleanHtml(authorDate);
        if (authorDate != null && authorDate.contains("Csősz")) {
            //A.log("saveTaxon() does not contains authorDate:" + authorDate);
            authorDate = AntFormatter.replace(authorDate, "Csősz", "Csosz");
            //A.log("saveTaxon() new authorDate:" + authorDate);
            item.put("author_date", authorDate);
        }

        String parentTaxonName = null;
        String currentValidName = (String) item.get("current_valid_name");
        if (taxonName.contains("dolichoderinaecolobopsis macrocephala"))
            s_log.debug("saveTaxon() 2 CURRENT VALID NAME:" + currentValidName + " taxonName:" + taxonName);
        if (currentValidName != null && !taxonName.equals(currentValidName)) {
            parentTaxonName = Taxon.getParentTaxonNameFromName(currentValidName);
        } else {
            parentTaxonName = Taxon.getParentTaxonNameFromName(taxonName);
        }
        if (parentTaxonName != null) {
            //A.log("saveTaxon() taxonName:" + taxonName + " parentTaxonName:" + parentTaxonName);
            if (("species".equals(rank) || "subspecies".equals(rank)) && TaxonMgr.getGenus(parentTaxonName) == null) {
                //A.log("saveTaxon() 2 parent does not exist:" + parentTaxonName + " rank:" + rank + " genus:" + TaxonMgr.getGenus(parentTaxonName));
            } else {
                item.put("parent_taxon_name", parentTaxonName);
            }
        }

        if (table.equals("taxon") && taxonQueryHashMap.containsKey(taxonName)) {
            ++uploadSkipped;
        } else {

            // if we have a new taxon and we have not already inserted it, then insert it.
            if (true) {
                String message = "AntwebUpload.saveTaxon() 3 taxonName:" + taxonName + " rank:" + rank + " isParent:" + isParent + " lineNum:" + lineNum + " hash.size:" + taxonQueryHashMap.size() + " descCounter.size:" + getDescCounter().size();
                A.iLog(1, message, 5000);
                //AntwebUtil.logShortStackTrace();
                //return 0;
            }

            String query = getInsertionQuery(item, table);

            Statement stmt = null;
            try {
                stmt = DBUtil.getStatement(getConnection(), "AntwebUpload.saveTaxon()");

                ++countUploaded;
                if (table.equals("taxon")) taxonQueryHashMap.put(taxonName, query);

                int rowCount = stmt.executeUpdate(query);

                c += rowCount;
                if (Rank.SPECIES.equals(rank) || Rank.SUBSPECIES.equals(rank)) getUploadDetails().countUpdatedSpecies();

                // See TaxonMgr.refreshTaxon() for documentation.
                //if (TaxonMgr.isUseRefreshing() && c > 0) {
                //    TaxonMgr.refreshTaxon(getConnection(), "save", table, taxonName, item);
                //}

            } catch (java.sql.SQLIntegrityConstraintViolationException e) {
                String message = "e:" + e + " query:" + query;
                s_log.warn("saveTaxon() 4 " + message);
                MessageMgr.addToErrors(message);
            } catch (SQLException e) {
                s_log.error("saveTaxon() 5 e:" + e + " query:" + query);
                throw e;
            } finally {
                DBUtil.close(stmt, "AntwebUpload.saveTaxon()");
            }
        }

        // New functionality. Parent taxons are handled.
        if (!isParent && parentTaxonName != null && !"worldants".equals(source)) {
            
            if (taxonName.contains("formicinaemyrma iperstriata"))
                s_log.debug("saveTaxon() 5 BAD currentValidName:" + currentValidName + " taxonName:" + taxonName);
            // if we save a taxon, we make sure it's parent exists, or we create it.
            item.put("taxon_name", parentTaxonName);
            String grandParentTaxonName = Taxon.getParentTaxonNameFromName(parentTaxonName);
            if (grandParentTaxonName != null) {
                item.put("parent_taxon_name", grandParentTaxonName);
                String parentRank = Taxon.getRankFromName(parentTaxonName);
                item.put("rank", parentRank);
                if ("family".equals(parentRank)) {
                    item.remove("subfamily");
                }
                if ("subfamily".equals(parentRank)) {
                    item.remove("genus");
                }
                if ("genus".equals(parentRank)) {
                    item.remove("species");
                    item.remove("subspecies");
                }

                //A.iLog(2, "callParent", 10);

                saveTaxon(item, table, true);
            } else {
                s_log.debug("saveTaxon() 6 grandParentTaxonName null for parentTaxonName:" + parentTaxonName);
            }
        }

        return c;
    }

    private String getInsertionQuery(Hashtable item, String table) {
        String query = "insert into " + table;

        StringBuffer fields = new StringBuffer();
        StringBuffer values = new StringBuffer();
        fields.append("(");
        values.append("(");
        Float floatValue = null;
        Object value = null;
        Set<String> keys = item.keySet();

        boolean logThis = false;
        for (String key : keys) {
            value = item.get(key);

            //A.log("getInsertionQuery() key:" + key + " value:" + value);
            if (enactExceptions(key, value)) continue;

            fields.append(translateKeyToColumn(key) + ",");

            if (value instanceof String) {
                if (((String) value).contains("\"")) {
                    value = AntFormatter.escapeQuotes((String) value);
                    //s_log.warn("getInsertionQuery() using mysqlEscapeQuotes().  value:" + value);
                }

                // *** Added Apr 4 2020
                if (((String) value).contains("'")) {
                    logThis = true;
                    //AntwebUtil.logShortStackTrace(10);

                    value = AntFormatter.escapeQuotes((String) value);
                    //s_log.warn("getInsertionQuery() using mysqlEscapeQuotes().  value:" + value);
                }

                if (((String) value).equals("true")) value = "1";
                if (((String) value).equals("false")) value = "0";

                values.append("'" + value + "',");
            } else {
                values.append(value + ",");
            }
        }

        fields.setCharAt(fields.length() - 1, ')'); // here we remove final commas
        values.setCharAt(values.length() - 1, ')');
        query += " " + fields.toString() + " values ";

        String theValues = values.toString();
        query += theValues;

        if (logThis) A.logi("Ivory Coast", "Should swap Coate'ivory for proper name? query:" + query);

        //if (query.contains("country")) A.log("getInsertionQuery() query:" + query);        

        return query;
    }

    // The key is almost always the columns. But...
    private String translateKeyToColumn(String key) {
      if ("rank".equals(key)) return "taxarank";
      return key;
    }

    private void updateTaxon(Hashtable item, String table)
      throws SQLException
    {

        String taxonName = (String) item.get("taxon_name");
        String source = (String) item.get("source");
        String rank = (String) item.get("rank");

        Taxon referenceTaxon = TaxonMgr.getTaxon(taxonName);

        if (referenceTaxon == null) s_log.debug("updateTaxon() no referenceTaxon found:" + taxonName);

        /*
            Taxon referenceTaxon = null; //(new TaxonDb(getConnection())).getDummyTaxon(taxonName);
            // CAS:86,598 x
            if (TaxonMgr.isUseRefreshing()) {
                dummyTaxon = TaxonMgr.getTaxon(taxonName);
            } else {
                dummyTaxon = (new TaxonDb(getConnection())).getTaxon(taxonName);
            }
            */

        boolean skip = referenceTaxon != null && referenceTaxon.isWorldAnts() && !"worldants".equals(source);  // A taxon from a specimen record source.
        if (!skip) {

            String query = "update " + table + " set ";
            StringBuffer sets = new StringBuffer();
            String key = null;
            Object value = null;
            try {
                // prepare the fields and values
                Enumeration keys = item.keys();
                StringBuffer fields = new StringBuffer();
                StringBuffer values = new StringBuffer();
                Float floatValue = null;

                while (keys.hasMoreElements()) {
                    key = (String) keys.nextElement();
                    value = item.get(key);

                    //if (!"worldants".equals(source)) {  // A taxon from a specimen record source.
                    // species list or specimen upload name will not overwrite worldants, but insert_method, line_num, etc... will
                    //boolean enactExceptions = enactExceptions(key, value, referenceTaxon);
                    //A.log("updateTaxon() key:" + key + " value:" + value + " source:" + source + " enactException:" + enactExceptions + " taxonName:" + taxonName);
                    //if (enactExceptions) continue;
                    //}

                    if (key.equals("decimal_latitude") || key.equals("decimal_longitude")) {
                        floatValue = (Float) item.get(key);
                        String setStr = key + "=" + floatValue.floatValue() + ",";
                        sets.append(setStr);
                        //A.log("updateTaxon():" + setStr);
                    } else {
                        if (value instanceof String) {
                            String valueStr = (String) value;

					/* // Nothing seems to test for: C�te d'Ivoire                    
					  String original = (String) value; //new String("A" + "\u00ea" + "\u00f1" + "\u00fc" + "C");
					  try {
						  byte[] utf8Bytes = original.getBytes("UTF8");
						  byte[] defaultBytes = original.getBytes();
						  if (!java.util.Arrays.equals(utf8Bytes, defaultBytes)) A.log("!utf8Bytes:" + utf8Bytes);
						  String roundTrip = new String(utf8Bytes, "UTF8");
						  if (!roundTrip.equals(original)) A.log("!roundTrip:" + roundTrip);    
					  } catch (UnsupportedEncodingException e) {
						  e.printStackTrace();
					  }                        

					  if (Utility.isASCII(valueStr)) A.log("updateTaxon() hasDiacritics:" + valueStr);

                      // This works... AsciiUtils.test();

                      if (valueStr.contains("Ivoire")) if (AsciiUtils.isNonAscii(valueStr)) A.log("updateTaxon() 2hasDiacritics:" + valueStr);
					*/


                            if (valueStr.contains("\"") || valueStr.contains("\'")) {
                                //A.log("updateTaxon() key:" + key + " value:" + value);
                                value = AntFormatter.escapeQuotes(valueStr);
                            }

                            if (((String) value).equals("true")) value = "1";
                            if (((String) value).equals("false")) value = "0";

                            sets.append(translateKeyToColumn(key) + "='" + value + "',");
                        } else {
                            sets.append(translateKeyToColumn(key) + "=" + value + ",");
                        }
                    }
                }
                if (sets.length() > 0) {
                    sets.setLength(sets.length() - 1);
                }

                query += " " + sets.toString();
                query += ", pending=0";
                if (!query.contains("parent_taxon_name"))
                    query += ", parent_taxon_name='" + Taxon.getParentTaxonNameFromName(taxonName) + "'";
                query += " where taxon_name = '" + taxonName + "'";

                if (!query.contains("insert_method")) {
                    s_log.error("updateTaxon() Somewhere in the following stacktrace should have been put an insert_method into item."); // + " query:" + query);
                    AntwebUtil.logShortStackTrace(8);
                }

                if (AntwebProps.isDevMode() && taxonName.equals(s_testTaxonName))
                    s_log.warn("updateTaxon() taxonName:" + taxonName + " query:" + query);

                Statement stmt = DBUtil.getStatement(getConnection(), "AntwebUpload.updateTaxon()");

                int c = stmt.executeUpdate(query);
                A.iLog("updateTaxon() c:" + c + " taxonName:" + taxonName); //query:" + query);

                DBUtil.close(stmt, "AntwebUpload.updateTaxon()");

                if (Rank.SPECIES.equals(rank) || Rank.SUBSPECIES.equals(rank)) getUploadDetails().countUpdatedSpecies();

                //if (TaxonMgr.isUseRefreshing() && c > 0) {
                //    TaxonMgr.refreshTaxon(getConnection(), "update", table, taxonName, item);
            } catch (com.mysql.cj.jdbc.exceptions.MysqlDataTruncation e) {
                String message = "e:" + e + " query:" + query;
                s_log.error("updateTaxon() 1" + message);
                MessageMgr.addToErrors(message);
            } catch (SQLException e) {
                s_log.error("updateTaxon() 2 e:" + e + " query:" + query);
                throw e;
            }
        }
    }  
    
	public static boolean hasDiacritics(String s) {
		// Decompose any á into a and combining-'.
	//    String s2 = Normalizer.normalize(s, Normalizer.Form.NFD);
	//    return s2.matches("(?s).*\\p{InCombiningDiacriticalMarks}.*");
		//return !s2.equals(s);
	  return Normalizer.isNormalized(s, Normalizer.Form.NFD);
	}      

    protected int saveTaxonAndProjTaxon(Hashtable item, String project) {
        int c = 0; // The number of saved Taxa (saveTaxon() is recursive).
        
        //A.log("saveTaxonAndProjTaxon() project:" + project + " item:" + item);    
    
       // Called from SpeciesListUpload()
        String query = "";
        Status status = null;
        try {
            String taxonName = UploadUtil.makeName(item);

            if (taxonName.length() > 0) {

                String family = (String) item.get("family");
                String subfamily = (String) item.get("subfamily");
                String genus = (String) item.get("genus");
                String species = (String) item.get("species");
                String rank = (String) item.get("rank");
                String source = (String) item.get("source");

				// XXX without genus null check this seems to fail on worldants without a rollback.
				if (genus != null && (taxonName.equals(s_testTaxonName))) {
				  s_log.debug("saveTaxonAndProjTaxon() taxonName:" + taxonName + " family:" + family + " project:" + project + " genus:" + genus + " authorDate:" + item.get("author_date"));
				  //AntwebUtil.logStackTrace();
				}

                if (taxonName.equals("formicidae"))
                    s_log.debug("saveTaxonAndProjTaxon() taxonName:formicidae project:" + project + " family:" + family); // item:" + item + "
                            
                if (!isValidSubfamily(family, subfamily)) {
                
                  /* Do we want the checks here? */
                  if (isExceptionalSubfamilySoCreate(family, subfamily, source)) {
                    //A.log("saveTaxonAndProjTaxon() isValidSubfamily!.  taxonName:" + taxonName);
                    // done
                  } else {              
                    // add to the nonLegit subfamily list
                    getMessageMgr().addToMessages(MessageMgr.invalidSubfamily, subfamily);
                    s_log.debug("saveTaxonAndProjTaxon() isValidSubfamily failure.  taxonName:" + taxonName);
                    return 0;
                  }  
                }
                     
                if (Project.WORLDANTS.equals(project)) {          

                  // If it is Worldants, it's status will come from the file and being in the item status field.
                } else {      
                  // For other projects, we will determine here.
                  
                  // A.log("WE DON'T USE THIS CODE?"); Yes we do! Specimen uploads.
                  
                    //Taxon dummyTaxon = (new TaxonDb(getConnection())).getDummyTaxon(taxonName);
                    Taxon dummyTaxon = null;
                    if (TaxonMgr.isUseRefreshing()) {
                        dummyTaxon = TaxonMgr.getTaxon(taxonName); // This is thought to be faster and w/ integrity now that taxa are refreshed. Not a big performance concern as only happens 245 for a CAS specimen upload.
                    } else {
                        dummyTaxon = (new TaxonDb(getConnection())).getTaxon(taxonName);
                    }


                  if (dummyTaxon != null) {
                    status = new Status(dummyTaxon.getStatus(), dummyTaxon.getCurrentValidName());
                  } else {
                    if (Taxon.isMorpho(taxonName)) {
                      status = new Status(Status.MORPHOTAXON);
                    } else {
                      status = new Status(Status.UNRECOGNIZED);
                    }
                  }

				  if (taxonName.equals(s_testTaxonName)) {
				    s_log.debug("saveTaxonAndProjTaxon() taxonName:" + taxonName + " dummyTaxon:" + dummyTaxon + " status:" + status.getValue());
				    //AntwebUtil.logStackTrace();
				  } 

                  boolean isValid = status.getValue().equals(Status.VALID);
                  boolean isValidSubfamilyForGenus = (new TaxonDb(getConnection())).isValidSubfamilyForGenus(family, subfamily, genus);

                  if ((!isValid) && !isValidSubfamilyForGenus) {
                    s_log.debug("saveTaxonAndProjTaxon() isValidSubfamilyForGenus failure.  Add to list.  taxonName:" + taxonName);
                    // add to the Invalid Subfamily for Genus list. - to avoid duplicates

                    isValidSubfamilyForGenus = (new HomonymDb(getConnection())).isValidSubfamilyForGenus(family, subfamily, genus);
                    if (isValidSubfamilyForGenus) {
                      String message = Taxon.displaySubfamilyGenus(subfamily, genus);
                      getMessageMgr().addToMessages(MessageMgr.generaAreHomonyms, message);
                      return 0;
                    } else {
                      String message = Taxon.displaySubfamilyGenusLinkToGenus(subfamily, genus);
                      getMessageMgr().addToMessages(MessageMgr.invalidSubfamilyForGenus, message);
                      return 0;
                    }
                  }

                  if (status.isPassWorldAntsSpeciesCheck()) {             
                    // Things changed here in version 5.2.3
                  } else {
                    // Will be uploaded, perhaps with a suggestion of proper taxon name (for now).
                    //A.log("saveProjectTaxon() status.value:" + status.getValue() 
                       // + " status.currentValidName: " + status.getCurrentValidName() 
                       // + " taxonName:" + taxonName + " subfamily:" + subfamily + " genus:" + genus);
                    handleWorldAntsSpeciesCheck(taxonName, status, family, subfamily, genus);
                  }
                  
				  if (taxonName.equals(s_testTaxonName)) {
				    s_log.debug("saveTaxonAndProjTaxon() taxonName:" + taxonName + " dummyTaxon:" + dummyTaxon + " status:" + status);
				    //AntwebUtil.logStackTrace();
 				  }

                  item.put("status", status.getValue());
                }
                //A.log("saveProjectTaxon() passBoltonSpeciesCheck:" + passBoltonSpeciesCheck + " taxonName:" + taxonName);

                // first save this thing to the proj_taxon table                
                ProjTaxonDb projTaxonDb = new ProjTaxonDb(getConnection());
                projTaxonDb.insert(project, taxonName, source);
                if (!Project.ALLANTWEBANTS.equals(project)) {
                  //A.log("saveTaxonAndProjTaxon() insert allantwebant taxon:" + taxonName + " status:" + item.get("status"));

                  String s = (String) item.get("status");
                  if (StatusSet.isAllAntwebAnts(s))
                    projTaxonDb.insert(Project.ALLANTWEBANTS, taxonName, source);
                }  
                //s_log.warn("saveProjectTaxon() taxonName:" + taxonName + " project:" + project);
                setHigherTaxonomicHierarchy(item);
                // next, save this thing to the taxon table

                c = saveTaxon(item);
                if (taxonName.contains("formicinaemyrma iperstriata"))
				  if (c <= 0) s_log.debug("saveTaxonAndProjTaxon() c:" + c + " taxonName:" + taxonName);

            } else {
              s_log.debug("saveTaxonAndProjTaxon() taxonName:" + taxonName);
            }
        } catch (SQLException e) {
            if (e instanceof java.sql.DataTruncation) {
                AntwebUtil.logStackTrace(e);
            }
            s_log.error("saveTaxonAndProjTaxon() project:" + project + " e:" + e);
            String message = "project:" + project + " e:" + e;
	  	    getMessageMgr().addToMessages(MessageMgr.databaseErrors, message);
        }
        return c;
    }

    public String isValidSubfamilyForGenus(String family, String subfamily, String genus)
      throws SQLException {
      
      if (new TaxonDb(getConnection()).isValidSubfamilyForGenus(family, subfamily, genus)) return "true";
      
      if (new HomonymDb(getConnection()).isValidSubfamilyForGenus(family, subfamily, genus)) return "true";
 
      return "false";    
    }

    private boolean enactExceptions(String key, Object value) {
        // We don't update these. These are never included for specimen taxa.
      if ("author_date_html".equals(key)) return true;
      if ("taxonomichistory".equals(key)) return true;

      //if ("country".equals(key)) return true;
      //if ("bioregion".equals(key)) return true;

      return ("reference_id".equals(key)) && ("".equals((String) value));   // The ints are sometimes nil "".
    }

    // If an exception, value will not be updated. Only runs on specimen record taxa.
    // The referenceTaxon comes from the TaxonMgr. If it is worldants, we will make exceptions.
    private boolean enactExceptions(String key, Object value, Taxon referenceTaxon) {
        if (enactExceptions(key, value)) return true;
        // Should this be here? Probably. Worldants deletes before insertion, so updates don't happen.

        if (referenceTaxon == null) {
            //s_log.warn("enactExceptioon() Why no referenceTaxon? It is an update. Should exist in taxonMgr.");
            return false; // If not worldants, then OK. But is that concerning?
        } else if (!referenceTaxon.isWorldAnts()) {
            return false;
        }

        // There is a referenceTaxon and it is worldants
        Set<String> values = Set.of("insert_method", "line_num");  // Allow these
        if (values.contains(key)) return false;

        return true;
    }

    void saveSpecimen(Hashtable item)
      throws SQLException {
      //throws com.mysql.jdbc.exceptions.jdbc4.MySQLNonTransientConnectionException {
       /** 
        *  Called from SpecimenUpload.
        */
        String taxonName = (String) item.get("taxon_name");
        //A.log("saveSpecimen() taxonName:" + taxonName);

        boolean debugItem = false;
        String code = (String) item.get("code");
        if (
          code.equals("casent0427783") || code.equals("casent0122898")
  /*
                  (code.equals("casent0160810"))
               || (code.equals("casent0625035"))
               || (code.equals("casent10156657"))
               || (code.equals("casent0010127"))
               || (code.contains("0242004-D01"))  
               || (code.equals("casent0339695"))    
               // || (code.contains("0000105982"))  
               || (code.equals("fmnhins0000106291"))
               || (code.equals("casent0813723"))
*/
             ) {
            s_log.debug("saveSpecimen() SPECIFIC CODE:" + code + " item:" + item);
            debugItem = true;
        }
        
        String dml = "";
        Statement stmt = null;
        try {
            // prepare the fields and values
            Enumeration keys = item.keys();
            StringBuffer fields = new StringBuffer();
            StringBuffer values = new StringBuffer();
            fields.append("(");
            values.append("(");
            String key = null;
            String value = null;
            Float floatValue = null;
            
            while (keys.hasMoreElements()) {
                key = (String) keys.nextElement();
                switch (key) {
                    case "decimal_latitude":
                    case "decimal_longitude":
                        floatValue = (Float) item.get(key);
                        fields.append(key + ",");

                        String appendValue = "";
                        if (floatValue.compareTo((float) -999.9) == 0) {
                            appendValue = "null,";
                        } else {
                            appendValue = floatValue.floatValue() + ",";
                        }
                        values.append(appendValue);
                        //A.log("saveSpecimen() appendValue:" + appendValue);

                        break;
                    case "elevation":
                        //s_log.warn("saveSpecimen elevation query:" + query);
                        fields.append("elevation,");
                        values.append(((Integer) item.get("elevation")).toString() + ",");

                        break;
                    case "datecollectedstart": {
                        String dateStr = (String) item.get("datecollectedstart");
                        String formatDate = DateUtil.getConstructDateStr(dateStr);
                        fields.append("datecollectedstartstr,");
                        values.append("'" + dateStr + "',");
                        if (validDate("datecollectedstart", formatDate, code)) {
                            fields.append("datecollectedstart,");
                            values.append("'" + formatDate + "',");
                        } else {
                            getMessageMgr().addToMessages(MessageMgr.invalidDateCollectedStart, dateStr);
                            //LogMgr.appendLog("dateCollected.log", "code:" + code + " dateStr:" + dateStr + " formatDate:" + formatDate);
                        }
                        break;
                    }
                    case "datecollectedend": {
                        String dateStr = (String) item.get("datecollectedend");
                        String formatDate = DateUtil.getConstructDateStr(dateStr);
                        fields.append("datecollectedendstr,");
                        values.append("'" + dateStr + "',");
                        if (validDate("datecollectedend", formatDate, code)) {
                            fields.append("datecollectedend,");
                            values.append("'" + formatDate + "',");
                        } else {
                            getMessageMgr().addToMessages(MessageMgr.invalidDateCollectedEnd, dateStr);
                            //LogMgr.appendLog("dateCollected.log", "code:" + code + " dateStr:" + dateStr + " formatDate:" + formatDate);
                        }
                        break;
                    }
                    case "datedetermined": {
                        String dateStr = (String) item.get("datedetermined");
                        String formatDate = DateUtil.getConstructDateStr(dateStr);
                        fields.append("datedeterminedstr,");
                        values.append("'" + dateStr + "',");
                        if (validDate("datedetermined", formatDate, code)) {
                            fields.append("datedetermined,");
                            values.append("'" + formatDate + "',");
                        } else {
                            getMessageMgr().addToMessages(MessageMgr.invalidDateDetermined, dateStr);
                            LogMgr.appendLog("dateDetermined.log", "code:" + code + " dateStr:" + dateStr + " formatDate:" + formatDate);
                        }
                        break;
                    }
                    case "access_group":
                        fields.append("access_group,");
                        values.append(((Integer) item.get("access_group")).intValue() + ",");
                        break;
                    case "access_login":
                        fields.append("access_login,");
                        values.append(((Integer) item.get("access_login")).intValue() + ",");
                        break;
                    case "is_introduced":
                        fields.append("is_introduced,");
                        values.append(((Integer) item.get("is_introduced")).intValue() + ",");
                        break;
                    case "backupFileName":
                        fields.append("backup_file_name,");
                        values.append("'" + item.get("backupFileName") + "',");
                        break;
                    default:
                        try {
                            value = (String) item.get(key);
                            fields.append(key + ",");

                            // Perhaps a more stringent check here is appropriate.  For now, if the
                            // last letter of the string is a \ then it fouls up the SQL quoting.
                            // This was happening for some of Jack's collectionnotes.
                            String lastChar = null;
                            try {
                                lastChar = value.substring(value.length() - 1, value.length());
                                if (lastChar.equals("\\")) value += " ";
                            } catch (Exception e) {
                                // no action taken
                            }
                            value = AntFormatter.escapeSingleQuotes(value);

                            //A.log("saveSpecimen() lastCHar:" + lastChar + " value:" + value); // AntFormatter.escapeQuotes(value));
                            values.append("'" + value + "',");
                        } catch (ClassCastException e) {
                            s_log.debug("AntwebUpload.saveSpecimen() key:" + key + " e:" + e);
                        }
                        break;
                }
            } // end while loop
            if (!item.containsKey("last_modified")) {
                fields.append("last_modified" + ",");
                values.append(currentDateFunction + ",");
            }
            
            if (!item.containsKey("kingdom_name")) {
                fields.append("kingdom_name" + ",");
                values.append("'animalia'" + ",");
            }
            if (!item.containsKey("phylum_name")) {
                fields.append("phylum_name" + ",");
                values.append("'arthropoda'" + ",");
            }
            if (!item.containsKey("class_name")) {
                fields.append("class_name" + ",");
                values.append("'insecta'" + ",");
            }
            if (!item.containsKey("order_name")) {
                fields.append("order_name" + ",");
                values.append("'hymenoptera'" + ",");
            }


			fields.append("upload_id" + ")");
			values.append(AntwebMgr.getNextSpecimenUploadId() + ")");
			
//            fields.setCharAt(fields.length() - 1, ')'); // here we remove final commas
//            values.setCharAt(values.length() - 1, ')');

            stmt = DBUtil.getStatement(getConnection(), "AntwebUpload.saveSpecimen()");
            dml = "insert into specimen " + fields.toString()
                    + " values " + values.toString();

            //A.iLog("saveSpecimen() dml:" + dml);        
            
            int retVal = stmt.executeUpdate(dml);

            if (retVal != 0) {
              getUploadDetails().countInsertedSpecimen();
            } else {
              s_log.debug("saveSpecimen() failed dml:" + dml);
            }

            ++saveSpecimenCount;
            if ((saveSpecimenCount % 25000 == 0)) s_log.info("saveSpecimen() count" + saveSpecimenCount + " code:" + code);
                                    
            // Only if successful insert, count and record the museum
            if (item.containsKey("ownedby")) {
              String ownedBy = (String) item.get("ownedby");
              getUploadDetails().addToMuseumMap(ownedBy);
              //A.log("saveSpecimen() ownedby value:" + ownedBy);
            } 
            
        } catch (ClassCastException e) {
           AntwebUtil.logStackTrace(e);
        } catch (java.sql.SQLSyntaxErrorException e) {
            s_log.error("saveSpecimen() dml:" + dml + " e:" + e);
            String message = "Specimen jdbc exception.  code:" + code + " line:" + LineNumMgr.getLineNum() + " e:" + e; // + " query:" + query;
            s_log.debug("saveSpecimen() " + message);
            getMessageMgr().addToMessages(MessageMgr.databaseErrors, message);
        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            String message = "Specimen code:" + code;
            getMessageMgr().addToMessages(MessageMgr.duplicateEntries, "", message);       
        } catch (SQLException e) {
            // See Note below of fix implemented here...
            // this is a lame hack - if this is the Taxon table, go ahead and update it (Thau).

            // if it is the specimen table, we just quit?  Mark, Jan 27, 2011.
            // Should not happen.  We delete all specimens of a given access_group during to upload.
            String message = "Specimen sql exception.  code:" + code + " line:" + LineNumMgr.getLineNum() + " e:" + e; // + " query:" + query;
            getMessageMgr().addToMessages(MessageMgr.databaseErrors, message);

            // if a different access_group attempts to load a code of another group's specimen...

            if (e instanceof java.sql.DataTruncation) return;
            
            // or could we always add to the DBErrorSet and return?
            if (!(e instanceof java.sql.SQLIntegrityConstraintViolationException)) {
                throw e;
            }
        } finally {
            DBUtil.close(stmt, "AntwebUpload.saveSpecimen()");
        }        
    }
    
	private boolean validDate(String dateType, String formatDate, String code) {
	  if (formatDate == null) return false;

	  Date date = DateUtil.getDate(formatDate);
	  if (date != null) {
		if (date.after(new Date())) {
			String message = "code:" + code + " dateDetermined:" + formatDate;
			getMessageMgr().addToMessages(MessageMgr.futureDateDetermined, message);
			return false;
		}
	  } else {
		return false;
	  }
	  return true;
	}
	

    // Similar method implemented in SpecimenUpload. This one called from SpeciesListUpload.
    public String setStatusAndCurrentValidName(String taxonName, Hashtable taxonItem)
      throws SQLException
    {  
      String currentValudName = taxonName;
      // Here we choose the best taxa for uploaded specimen.  
      boolean skipTaxonEntry = false;
      String status = null;    
      String originalTaxonName = null;
      
      //The case of morpho and indet are easy because we can determine by the taxon name.
      if (Taxon.isMorpho(taxonName)) {
        status = Status.MORPHOTAXON;
      } else if (Taxon.isIndet(taxonName)) {
        status = Status.INDETERMINED;  
      } else {
          TaxonDb taxonDb = new TaxonDb(getConnection());

          ProfileCounter.add("AntwebUpload.setStatusAndCurrentValidName()A");
          Taxon taxon = null;
          if (TaxonMgr.isUseRefreshing()) {
              taxon = TaxonMgr.getTaxon(taxonName); // This is thought to be faster and w/ integrity now that taxa are refreshed. Not a big performance concern as only happens 245 for a CAS specimen upload.
          } else {
              taxon = taxonDb.getTaxon(taxonName);
          }



		  //if (taxonName.contains("formicinaemyrma iperstriata")) A.log("setStatusAndCurrentValidName() taxonName:" + taxonName + " taxon:" + taxon);
		  //A.log("AntwebUpload.setStatusAndCurrentValidName() taxonName:" + taxonName + " taxon:" + taxon);

          if (taxon != null) status = taxon.getStatus();
          if ((taxon == null) || (status == null) || (Status.UNRECOGNIZED.equals(status))) {
            if (status == null) status = Status.UNRECOGNIZED;

            if ((new HomonymDb(getConnection())).isHomonym(taxonName)) {
//            if (Status.HOMONYM.equals(status)) {
              String displayName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.displayTaxonName(taxonName) + "</a>";
              getMessageMgr().addToMessages(MessageMgr.taxonNamesAreHomonyms, displayName);            
            } else {
            
              // if quadrinomial put in a separate list.  Do upload, otherwise don't.
              if (Taxon.isQuadrinomial(taxonName)) {
                String displayName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.displayTaxonName(taxonName) + "</a>";
                getMessageMgr().addToMessages(MessageMgr.unavailableQuadrinomial, displayName);
              } else {

                if (!Taxon.isAnt(taxonName)) {
					String displayName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.displayTaxonName(taxonName) + "</a>";
					//displayName += " (line:" + (String) specimenItem.get("lineNum") + ")";
					s_log.debug("AntwebUpload.setStatusAndCurrentValidName() displayName:" + displayName);
					getMessageMgr().addToMessages(MessageMgr.nameNotInFamilyFormicidae, displayName);
                } else {              
					// Currently we allow these to be uploaded.  In the future we won't.
					String displayName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.displayTaxonName(taxonName) + "</a>";
					getMessageMgr().addToMessages(MessageMgr.unrecognizedInvalidSpecies, displayName);            
                }
              }
            }
          } else { 
            String currentValidName = taxon.getCurrentValidName();
            String subfamily = taxon.getSubfamily();
            status = taxon.getStatus();
 
            //A.log("AntwebUpload.setStatusAndCurrentValidName() 1 currentValidName:" + currentValidName);

            if (Status.usesCurrentValidName(status)) {
              if (currentValidName == null) {
                s_log.debug("AntwebUpload.setStatusAndCurrentValidName() shouldn't status:" + status + " have a current valid name?");
                // Shouldn't a status that uses a current valid name have a current valid name?
              } else {
                skipTaxonEntry = true;

                ProfileCounter.add("AntwebUpload.setStatusAndCurrentValidName()B");
                String currentValidTaxonName = null;
                if (TaxonMgr.isUseRefreshing()) {
                    currentValidTaxonName = TaxonMgr.getTaxon(currentValidName).getTaxonName(); // This is thought to be faster and w/ integrity now that taxa are refreshed. Not a big performance concern as only happens 245 for a CAS specimen upload.
                } else {
                    currentValidTaxonName = TaxonDb.getCurrentValidTaxonName(getConnection(), currentValidName);
                }

                if (currentValidTaxonName == null) {
                  s_log.debug("AntwebUpload.setStatusAndCurrentValidName() No currentValidTaxonName for"
                    + " currentValidName:" + currentValidName
                    + " taxonName:" + taxonName);
                } else {
                  if (currentValidTaxonName.equals(taxonName)) {
                    s_log.warn("AntwebUpload.setStatusAndCurrentValidTaxonName() currentValidName should be distinct from taxonName:" + taxonName);
                  } else {
                    // We found it.  Use it.

                      ProfileCounter.add("AntwebUpload.setStatusAndCurrentValidName()C");
                      Taxon currentValidTaxon = null;
                      if (TaxonMgr.isUseRefreshing()) {
                          currentValidTaxon = TaxonMgr.getTaxon(currentValidTaxonName); // This is thought to be faster and w/ integrity now that taxa are refreshed. Not a big performance concern as only happens 245 for a CAS specimen upload.
                      } else {
                          currentValidTaxon = taxonDb.getTaxon(currentValidTaxonName);
                      }

                    status = Status.VALID;
                    originalTaxonName = taxonName;
                    taxonName = currentValidTaxonName;
                    taxonItem.put("taxon_name", currentValidTaxon.getTaxonName());

                    taxonItem.put("family", currentValidTaxon.getFamily());
                    taxonItem.put("subfamily", currentValidTaxon.getSubfamily());
                    if (currentValidTaxon.getTribe() != null) taxonItem.put("tribe", currentValidTaxon.getTribe());
                    taxonItem.put("genus", currentValidTaxon.getGenus());
                    if (currentValidTaxon.getSubgenus() != null) taxonItem.put("subgenus", currentValidTaxon.getSubgenus());
                    taxonItem.put("species", currentValidTaxon.getSpecies());
                    if (currentValidTaxon.getSubspecies() != null) taxonItem.put("subspecies", currentValidTaxon.getSubspecies());

                    //String heading = "<b>Recognized invalid species.  Submission replaced with current valid name from AntCat.org <font color=green>(uploaded)</font>:</b>";
                    String displayName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + originalTaxonName + "'>" + Taxon.displayTaxonName(originalTaxonName) + "</a>";
                    if ("".equals(taxonName)) {
                       displayName = "[empty string]";
                      // was: getUploadDetails().getPassWorldantsSpeciesCheckSet().add(taxonName);
                    }
                    String toName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + currentValidTaxonName + "'>" + Taxon.displayTaxonName(currentValidTaxonName) + "</a>";
                    String message = displayName + " -> " + toName;
                    getMessageMgr().addToMessages(MessageMgr.recognizedInvalidSpecies, message);
                  }
                } 
              } // Check for null currentValidName
            } else if (Status.VALID.equals(status)) {
              // do nothing
            } else {
              s_log.debug("AntwebUpload.setStatusAndCurrentValidName() for taxonName:" + taxonName + " status not found:" + status);
            }            
          }
      }
      taxonItem.put("status", status);
      taxonItem.put("taxon_name", taxonName);

      return taxonName; // May have been set to be currentValidName.
    }
    
    public void handleWorldAntsSpeciesCheck(String taxonName, Status status, String family, String subfamily, String genus) {
       // This is overridden in for specimen in SpecimenUpload.java
       if (Status.UNRECOGNIZED.equals(status.getValue())) {         
         // getUploadDetails().getPassWorldantsSpeciesCheckSet().add(taxonName);
         String displayName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.displayTaxonName(taxonName) + "</a>";
         getMessageMgr().addToMessages(MessageMgr.taxonNameNotFoundInBolton, displayName);
         //return;  // We do want to upload these, and flag them.
       } else if (Status.SYNONYM.equals(status.getValue())) {
         // This condition could expand to be any status that has a current valid name.
         
         // We are not yet doing this with specimen uploads.  Should we?  No, prolly go to the new Species List Tool model instead.
         String displayName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.displayTaxonName(taxonName) + "</a>";
         if ("".equals(taxonName)) {
            displayName = "[empty string]";
            // was: getUploadDetails().getPassWorldantsSpeciesCheckSet().add(taxonName);
         }
         String toName = (new org.calacademy.antweb.Formatter()).capitalizeFirstLetter(status.getCurrentValidName());
         String message = displayName + " -> " + toName;
         getMessageMgr().addToMessages(MessageMgr.taxonNamesUpdatedToBeCurrentValidName, message); 
       } else {
         s_log.info("handleWorldAntsSpeciesCheck() status:" + status.getValue() + " not handled for taxon:" + taxonName);
       }
    }

    private boolean isExceptionalSubfamily(String family, String subfamily) throws SQLException {
      // Both "incertae_sedis" and "([subfamily])" are considered exceptional. 
    
      boolean isExceptional = false;
      if ("incertae_sedis".equals(subfamily)) isExceptional = true;
      if ( (subfamily != null) 
        && (!"".equals(subfamily)) 
        && (subfamily.substring(0,1).equals("("))
         ) {
        isExceptional = true;
      }
      return isExceptional;      
    }

    protected boolean isExceptionalSubfamilySoCreate(String family, String subfamily, String source) 
      throws SQLException {
        if (isExceptionalSubfamily(family, subfamily)) {
          // If it is not valid, but is morpho or indet, create it.
          // Create it if it does not exist...
          boolean indetSubfamilyExists = getUploadDb().getExtantIndetSubfamilies().contains(subfamily); 
          //A.log("isExceptionalSubfamily() family:" + family + " subfamily:" + subfamily 
          //  + " indetSubfamilyExists:" + indetSubfamilyExists + " isExceptional:" + isExceptional);      
          if (!indetSubfamilyExists) {
              addMissingSubfamily(subfamily, Project.ALLANTWEBANTS, "isExceptionalSubfamily");
              //getExtantIndetSubfamilies().add(subfamily);
          }    
          return true;
        } else {
          return false;
        }
    }

    private boolean isValidSubfamilyCheck(String subfamily) {
        return Subfamily.isValidAntSubfamily(subfamily);
    }
    
    public boolean isValidSubfamily(String family, String subfamily) throws SQLException {
      //A/.log("isValidSubfamily() family:" + family + " subfamily:" + subfamily);

      if (!"formicidae".equals(family)) return true;  // We do not question the validity of non-ants.
      if (Subfamily.isValidAntSubfamily(subfamily)) return true;
      return true;
    }    
    
    private void addMissingSubfamily(String subfamily, String source, String insertMethod) throws SQLException {
        //source was project
        String taxonName = subfamily;
        
        // add to Proj_taxon?
        s_log.debug("addMissingSubfamily() subfamily:" + subfamily + " source:" + source + " insertMethod:" + insertMethod);
        
        getUploadDb().insertSubfamily(taxonName, "formicidae", subfamily, source, insertMethod, Status.UNRECOGNIZED); // This is a PLACEHOLDER taxon.
    }

    protected int addMissingGenera(Hashtable genera, String project, String source, int lineNum, int accessGroup) {
        int c = 0;

        for (String genus : (Set<String>) genera.keySet()) {
            String subfamily = (String) genera.get(genus);
            if (isValidSubfamilyCheck(subfamily)) {
				c += addMissingGenus(subfamily, genus, project, source, "addMissingGenera", lineNum, accessGroup);
            } else {
              s_log.error("addMissingeGenera() not valid subfamily check.  project:" + project + " subfamily:" + subfamily);
            }
        }
        return c;
    }
    protected int addMissingGenus(String subfamily, String genus, String project, String source, int lineNum, int accessGroup) {
        return addMissingGenus(subfamily, genus, project, source, "addMissingGenus", lineNum, accessGroup);
    }
    protected int addMissingGenus(String subfamily, String genus, String project, String source, String insertMethod, int lineNum, int accessGroup) {
        int c = 0;
        
        if (AntwebProps.isDevOrStageMode() && "formicinae".equals(subfamily) && "acantholepis".equals(genus) ) {
            s_log.warn("addMissingGenus() subfamily:" + subfamily + " genus:" + genus + " project:" + project + " source:" + source + " insertMethod:" + insertMethod);
            //AntwebUtil.logShortStackTrace(8);
        }
 
        DBUtil.open("addMissingGenus()");
 
        String taxonName;
        Hashtable item;

        item = new Hashtable();
        item.put("genus", genus);

        item.put("subfamily", subfamily);
            
        item.put("species", "");
        taxonName = UploadUtil.makeName(item);
        item.put("taxon_name", taxonName);
        item.put("rank", "genus");
        item.put("source", source);
        item.put("insert_method", insertMethod);
        item.put("status", Status.UNRECOGNIZED);  // This is a PLACEHOLDER taxon.
        item.put("line_num", lineNum);
        item.put("access_group", accessGroup);
            
        if (Project.WORLDANTS.equals(project)) {
            //s_log.warn("addMissingGenera() taxonName:" + taxonName);
             item.put("antcat", "1");
        }
        c = saveTaxonAndProjTaxon(item, project);
            
        DBUtil.close("addMissingGenus()");
        
        return c;
    }

    protected void saveDescriptionEdit(Hashtable description) {
      saveDescription(description, "description_edit");
    }
    protected void saveDescriptionHomonym(Hashtable description) {
      saveDescription(description, "description_homonym");
    }
    private void saveDescription(Hashtable description, String table) {
      /*
        We used to save all of the description records in the description table.
        Now we are allowing them to be created via the user interface, aside from
        taxonomichistory.
       */

        for (Enumeration keys = description.keys(); keys.hasMoreElements();) {
          String taxonName = (String) description.get("taxon_name");
          String title = (String) keys.nextElement();            
          String content = (String) description.get(title);
          String authorDate = (String) description.get("author_date");        
          authorDate = UploadUtil.cleanHtml(authorDate);
          getDescCounter().register(title);

          // This logs each description record and it's content size.
          //s_log.info("saveDescription() taxonName:" + taxonName + " title:" + title + " content:" + content.length() + ")");
          if (title.equals("taxonomichistory")) {
          
            content = AntFormatter.replace(content, "Csősz", "Csosz");

            getUploadDb().insertDescription(table, taxonName, authorDate, title, content);  // was getSpeciesListUploadDb()
          }
        }
    }
    // End Move from SpeciesListUpload ?

    public void setConnection(Connection connection) {
      m_connection = connection;
    }
        
    public Connection getConnection() {
      return m_connection;
    }

    public void setUploadDetails(UploadDetails details) {
      uploadDetails = details;
    }
    public UploadDetails getUploadDetails() {
      return uploadDetails;
    }

    public MessageMgr getMessageMgr() {
      return uploadDetails.getMessageMgr();
    }
    
    protected void setHigherTaxonomicHierarchy(Hashtable item) {
        // set the hierarchy.  This is true for all projects.  Specimens data may differ.
        
        String family = (String) item.get("family");
        if ( ("formicidae".equals(family)) || (family == null)) {
            item.put("kingdom_name", "animalia");
            item.put("phylum_name", "arthropoda");
            item.put("class_name", "insecta");
            item.put("order_name", "hymenoptera");
            item.put("family", "formicidae");
        }
    }

    String addToBigLine(Hashtable item, String table) {

        String query = "";

        // prepare the fields and values
        Enumeration keys = item.keys();
        StringBuffer fields = new StringBuffer();
        StringBuffer values = new StringBuffer();
        fields.append("(");
        values.append("(");

        String key = null;
        String value = null;
        while (keys.hasMoreElements()) {
            key = (String) keys.nextElement();
            value = (String) item.get(key);
            fields.append(key + ",");
            values.append("'" + value + "',");
        }
        fields.setCharAt(fields.length() - 1, ')');
        values.setCharAt(values.length() - 1, ')');

        return "insert into " + table + " " + fields.toString() + " values "
                + values.toString() + ";";
    }


    protected Float convertGeorefToDecimal(String latlon) {
        float decimal = 0;
        Float result = (float) 0.0;
        try {
            result = Float.valueOf(latlon);
            //s_log.info("convertGeorefToDecimal() result: " + result); 
        } catch (NumberFormatException nfe) {
            try {
                RE oldGeo = new RE("^[^0-9]*([0-9]+)[^0-9]*([0-9]+)[^0-9]*([0-9]+)[^0-9]*(\\w)$");

                if (oldGeo.match(latlon)) {
                    float degrees = Float.parseFloat(oldGeo.getParen(1));
                    float minutes = Float.parseFloat(oldGeo.getParen(2));
                    float seconds = Float.parseFloat(oldGeo.getParen(3));
                    String direction = oldGeo.getParen(4);

                    decimal = degrees + (minutes / 60) + (seconds / 3600);
                    if ((direction.equals("s")) || (direction.equals("w"))) {
                        decimal = 0 - decimal;
                    }
                    result = decimal;
                }
            } catch (RESyntaxException e) {
                s_log.error("convertGeorefToDecimal() e:" + e);
            }
        }
        return result;
    }     
}

class UnicodeFormatter  {

   static public String byteToHex(byte b) {
      // Returns hex String representation of byte b
      char hexDigit[] = {
         '0', '1', '2', '3', '4', '5', '6', '7',
         '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
      };
      char[] array = { hexDigit[(b >> 4) & 0x0f], hexDigit[b & 0x0f] };
      return new String(array);
   }

   static public String charToHex(char c) {
      // Returns hex String representation of char c
      byte hi = (byte) (c >>> 8);
      byte lo = (byte) (c & 0xff);
      return byteToHex(hi) + byteToHex(lo);
   }

}
