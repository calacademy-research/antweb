package org.calacademy.antweb.upload;

import java.util.*;
import java.util.Date;

import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public class SpecimenUploadProcess extends AntwebUpload {   //extends SpecimenUploadSupport {
/* 
    Biota Data is uploaded through the curator interface.  It loads data from the "Biota Database"
    (a Windows client database that Michele Esposito uses).  The data is exported from Biota,
    and the data file uploaded.  Biota application is no longer under active development.
    
*/
    private static final Log s_log = LogFactory.getLog(SpecimenUploadProcess.class);

    static int MAXLENGTH = 80;

    String currentDateFunction = "now()";  // for mysql 

	SpecimenUploadProcess(Connection connection) {
      super(connection);

      //setSpecimenUploadDb(new SpecimenUploadDb(getConnection()));       
    }

    private String m_mode = "";
    
    private String getMode() {
      return m_mode;
    }
    private void setMode(String mode) {
      m_mode = mode;
    }

    // This is useful when various values depend on others. They are all loaded now.
    boolean processLine(Hashtable<String, Object> specimenItem, Hashtable<String, Object> taxonItem, int lineNum, String shortFileName, Group group)
      throws SQLException {

        //UploadHelper.setLineNum(lineNum);

		String skipRecord = null;

		String taxonName = (String) taxonItem.get("taxon_name");
		String code = (String) specimenItem.get("code");
		String rank = (String) taxonItem.get("rank");		
		String subspecies = (String) taxonItem.get(Rank.SUBSPECIES);
		String species = (String) taxonItem.get(Rank.SPECIES);
		String genus = (String) taxonItem.get(Rank.GENUS);
		String subfamily = (String) taxonItem.get(Rank.SUBFAMILY);
		String family = (String) taxonItem.get(Rank.FAMILY);
		String subfamilyGenus = subfamily + genus;

        if (LineNumMgr.getDisplayLineNum(lineNum) == 130)
          s_log.debug("processLine() lineNum:" + LineNumMgr.getDisplayLineNum(lineNum) + " code:" + code + " taxonName:" + taxonName + " genus:" + genus + " rank:" + rank);


		//A.log("processLine() taxon_name:" + taxonName + " species:" + species + " rank:" + rank);

		/*
		  If family = "formicidae"
			Is subfamily in worldants, or in {Incertae_sedis (Formicidae)}
		  If genus in worldants, then the subfamily must be in the worldants subfamily/genus combo.
		  Is ant a morphospecies?
			else if not in worldants, add to list

		(1) No new subfamilies accepted for family Formicidae, only subfamilies in Bolton World Cat. 
		exceptions:   Incertae_sedis,  (Formicidae)  ACTION: do not upload
		[note if not in family Formicidae, any subfamily can be uploaded)  * new feature - this would have prevented "Aldabra" as a new subfamily
		*/                    

		if (skipRecord == null) {
		  if (code == null || code.length() == 0) {
			s_log.warn("processLine() code is null or empty string line:" + lineNum + " taxon:" + taxonName);
			getMessageMgr().addToMessages(MessageMgr.codeNotFound, "line:" + LineNumMgr.getDisplayLineNum(lineNum) + " code:" + Html.esc(code));
			skipRecord = "codeIsNull";
		  }
		}

		if (skipRecord == null) {
		  if (Utility.isBlank(subfamily)) {
			//s_log.warn("processLine() subfamily is empty string line:" + lineNum + " taxon:" + taxonName);
			getMessageMgr().addToMessages(MessageMgr.emptyStringSubfamily, "line:" + LineNumMgr.getDisplayLineNum(lineNum) + " code:" + Html.esc(code) + " taxon:" + Html.esc(taxonName));
			skipRecord = "subfamilyEmptyString";
		  }
		}

		if (skipRecord == null) {
		  if (Utility.isBlank(genus)) {
			  s_log.debug("processLine() genus is empty string line:" + lineNum + " displayLineNum:" + LineNumMgr.getDisplayLineNum(lineNum) + " genus:" + genus + " taxon:" + taxonName);
			  getMessageMgr().addToMessages(MessageMgr.emptyStringGenus, "line:" + LineNumMgr.getDisplayLineNum(lineNum) + " code:" + Html.esc(code) + " taxon:" + Html.esc(taxonName));
			  skipRecord = "genusEmptyString";
		  }
		}

        //A.log("processLine() species:" + species);
		if (skipRecord == null) {
		  if (Utility.isBlank(species) && !Utility.isBlank(genus)) {
		    if (genus.contains("(")) {
		      species = genus;
		    } else {
		      species = "(" + genus + ")";
            }
			//A.log("processLine() species is empty string line:" + lineNum + " taxon:" + taxonName);
			getMessageMgr().addToMessages(MessageMgr.emptyStringSpecies, "line:" + LineNumMgr.getDisplayLineNum(lineNum) + " code:" + Html.esc(code) + " taxon:" + Html.esc(taxonName));
			skipRecord = "speciesEmptyString";
		  }
		}
   
		if ("casent0003145".equals(code)) {     
		  s_log.debug("processLine() code:" + code + " species:" + species + " subspecies:" + subspecies);
		}

		if (skipRecord == null) {                   
		  if (Rank.SUBSPECIES.equals(rank)) { 
		   if (Utility.blank(subfamily)) skipRecord = "blankSubfamilyNameForSubspecies";
		   if (Utility.blank(genus)) skipRecord = "blankGenusNameForSubspecies";
		   if (Utility.blank(species)) skipRecord = "blankSpeciesNameForSubspecies"; 
		   if (Utility.blank(subspecies)) skipRecord = "badRank"; 
		  }
		  if (Rank.SPECIES.equals(rank)) { 
		   if (Utility.blank(subfamily)) skipRecord = "blankSubfamilyNameForSpecies";
		   if (Utility.blank(genus)) skipRecord = "blankGenusNameForSpecies";
		   if (Utility.blank(species)) skipRecord = "blankSpeciesNameForSpecies"; 
		  }
		  if (Rank.GENUS.equals(rank)) { 
		   if (Utility.blank(subfamily)) skipRecord = "blankSubfamilyNameForGenus";
		   if (Utility.blank(genus)) skipRecord = "blankGenusNameForGenus";
		  }
		  if (Rank.SUBFAMILY.equals(rank)) { 
		   if (Utility.blank(subfamily)) skipRecord = "blankSubfamilyNameForSubfamily";
		  }
		  if (skipRecord != null) getBadRankTaxonList().add("line:" + LineNumMgr.getDisplayLineNum(lineNum) + " code:" + code + " rank:" + rank);
		}
						 
		if (skipRecord == null) {

		  //Date startTime1 = new Date();

		  if (!isValidSubfamily(family, subfamily)) {
			if (isExceptionalSubfamilySoCreate(family, subfamily, shortFileName)) { //uploadFile.getFileName())) {
			  // done
			} else {
			  // add to the nonLegit subfamily list
			  //A.log("processLine() invalid subfamily !isValidSubfamily(" + family + ", " + subfamily + ")");
			  String offender = "taxonName:" + taxonName + " lineNum:" + LineNumMgr.getDisplayLineNum(lineNum);   // the line number in the file, as in the other sections
			  getMessageMgr().addToMapMessages(MessageMgr.invalidSubfamilyMap, Html.esc(Formatter.initCap(subfamily)), Html.esc(offender));
			  //A.log("processLine() isValidSubfamily failure.  " + offender);
			  skipRecord = "invalidSubfamily";
			}
		  }

		  // Profiler.profile("invalidSubfamily", startTime1);                     
		}

		/*
		(3) Genus names that are not morphogenera that are  not found in Bolton World Catalog
		Action: upload but give report [why?  to catch misspellings and to check for out of date classifications)  
		* currently genus names not in Bolton are not uploaded.  Fixed Sep 3, 2013
		*/

		if (skipRecord == null) {

			Date startTimeGenusSet = new Date();
            SpecimenUploadDb specimenUploadDb = new SpecimenUploadDb(getConnection());
			boolean passGenusSubfamilyCheck = specimenUploadDb.passGenusSubfamilyCheck(subfamilyGenus, genus, subfamily, family, shortFileName, group.getId());
			//A.log("processLine() passGenusSubfamilyCheck:" + passGenusSubfamilyCheck + " taxonName:" + taxonName);
			if (!passGenusSubfamilyCheck) {
			  // if we don't pass, it means the genus/subfamily combo does not exist.  No problem, create it and log it..
			  //   Unless!  if the genus does exist with the wrong subfamily, block entry.
			 /*                    
			 (2) genera that already exist in Bolton Cat must match genus/subfamily classification in Bolton World Cat. Action:  Do not upload
			   [why?  We don't want to create genera in multiple subfamilies]
			 */
			  //boolean isExistingSubfamilyForAGenus = getSpecimenUploadDb().isExistingSubfamilyForAGenus(family, subfamily, genus);
			  //A.log("processLine() Not existing subfamily:" + subfamily + " for genus:" + genus);
				ProfileCounter.add("SpecimenUploadProcessIsExistingSubfamilyForGenus");
			  boolean isExistingTaxonSubfamilyForAGenus = new TaxonDb(getConnection()).isExistingSubfamilyForAGenus(family, subfamily, genus);
			  boolean isExistingHomonymSubfamilyForAGenus = false;
			  if (!isExistingTaxonSubfamilyForAGenus) {
				// Date startTime1 = new Date();
				// Check HomonymDb.
				isExistingHomonymSubfamilyForAGenus = new HomonymDb(getConnection()).isExistingSubfamilyForAGenus(family, subfamily, genus);
				if (isExistingHomonymSubfamilyForAGenus) {
				  //A.log("processLine() Homonym found in antweb.  Subfamily:" + subfamily + " genus:" + genus);
				  String displayTaxonName = Taxon.makeDescriptionLink(subfamily+genus, Taxon.displaySubfamilyGenus(subfamily, genus));                          
				  getMessageMgr().addToMessages(MessageMgr.subfamilyForGenusFoundInHomonym, displayTaxonName);
				} else {
				  //A.log("processLine() not existing homonym subfamily for a genus.  family:" + family + " subfamily:" + subfamily + " genus:" + genus);				
				  //skipRecord = "antwebSubfamilyNotFound";                        
				}
				//Profiler.profile("genus1", startTime1);                   
			  }	
			  if (!(isExistingTaxonSubfamilyForAGenus || isExistingHomonymSubfamilyForAGenus)) {
				//Date startTime1 = new Date();
				String uploadedSubfamilyGenus = Taxon.displaySubfamilyGenus(subfamilyGenus);
				int uploadedSubfamilyLength = subfamily.length();
				String antwebSubfamily = specimenUploadDb.getAntwebSubfamily(genus);
				String antwebTaxonName = antwebSubfamily + taxonName.substring(uploadedSubfamilyLength);

				// Not happy with this logic.  Creating trouble.  Doubted.
				boolean subfamilyNotFoundInAntweb = "".equals(antwebSubfamily) || subfamily == null || antwebTaxonName == null;
				if (true && subfamilyNotFoundInAntweb) {
				  //A.log("processLine() 2 subfamily not found in antweb.  antwebTaxonName:" + antwebTaxonName + " subfamily:" + subfamily + " genus:" + genus + " antwebSubfamily:" + antwebSubfamily);
				  getMessageMgr().addToMessages(MessageMgr.invalidSubfamilyForGenus, Html.esc(Taxon.displaySubfamilyGenus(subfamily, genus)));
				  skipRecord = "antwebSubfamilyNotFound";
				} else { 
				  subfamily = antwebSubfamily;
				  taxonItem.put("subfamily", antwebSubfamily);
				  String oldTaxonName = taxonName;
				  taxonName = antwebTaxonName;
		
				  OrphansDb orphansDb = new OrphansDb(getConnection());
				  orphansDb.moveTaxonSupportingDataToAlternate(taxonName, oldTaxonName);
		
				  taxonItem.put("taxon_name", antwebTaxonName); 
				  specimenItem.put("taxon_name", antwebTaxonName); 
				  specimenItem.put("subfamily", subfamily);
				  subfamilyGenus = antwebSubfamily + genus;	
		
		// ??? *** odd: "-> " found at beginning of following string
				  String displayTaxonName = Taxon.makeDescriptionLink(subfamilyGenus, Taxon.displaySubfamilyGenus(antwebSubfamily, genus));
				  getMessageMgr().addToMapMessages(MessageMgr.invalidSubfamilyForGenusReplaced, Html.esc(uploadedSubfamilyGenus), displayTaxonName); 
				}
				//Profiler.profile("genus2", startTime1);                     

			  } else {  // existingSubfamilyForGenus 
				/* For testing:
				delete from taxon where insert_method = "addMissingGenus" and access_group = 21;                     
				*/               
				// Date startTime1 = new Date();
						  
				  // Add to the Subfamily/Genus combo not found and DO upload
				  String displaySubfamilyGenus = Taxon.makeDescriptionLink(subfamilyGenus, Taxon.displaySubfamilyGenus(subfamilyGenus));
				  String displayTaxonName = Taxon.makeDescriptionLink(taxonName);

				  //A.log("processLine() subfamily/Genus combo not found taxonName:" + taxonName + " displayTaxonName:" + displayTaxonName);
				  addMissingGenus(subfamily, genus, Project.ALLANTWEBANTS, shortFileName, lineNum, group.getId());
			
				  getMessageMgr().addToMapMessages(MessageMgr.subfamilyGenusComboNotInBolton, displaySubfamilyGenus, displayTaxonName);
				  //getUploadDetails().addToPassGenusSubfamilyHash(subfamilyGenus, taxonName);
				  //Profiler.profile("addGenus", startTime1);                     

			  }   // existingSubfamilyForGenus 
			}  // end passGenusSubfamilyCheck
			//Profiler.profile("passGenusSet", startTimeGenusSet, genus);                     
	  
		} 
		
		String useTaxonName = null;
		String bioregion = null;
		boolean isIntroduced = false;		
		if (skipRecord == null) {
			/*
			  (4) Species names that are not morphospecies and not found in Bolton World Cat.
			  Action: upload but give report [why?  to catch mispellings and to check for out of date classifications)  
			  We put these here, was just before data operations, because we need to know the taxonname we will be using for this specimen.
			  See upload report.
			  3: Recognized invalid species. Submission replaced with current valid name from AntCat.org (uploaded) (total:1): 
				Cardiocondyla wroughtoni -> Cardiocondyla wroughtonii
			*/
			useTaxonName = setStatusAndCurrentValidName(taxonName, taxonItem, specimenItem, shortFileName);                        
			//if (!makeTaxonName.equals(taxonName)) A.log("buildLineItems() makeTaxonName:" + makeTaxonName + " taxonName:" + taxonName);

			bioregion = (String) specimenItem.get("bioregion");

			if (TaxonPropMgr.isIntroduced(useTaxonName, bioregion)) {
				//A.log("processLine() useTaxonName:" + useTaxonName + " bioregion:" + bioregion);
			  isIntroduced = true;
			  specimenItem.put("is_introduced", 1);
			  getMessageMgr().flag("is_introduced");
			}
		}

		//A.log("processLine() isIntroduced:" + isIntroduced + " bioregion:" + bioregion + " skipRecord:" + skipRecord + " useTaxonName:" + useTaxonName);

		if (skipRecord == null) {
		  if (!isIntroduced) {
			//if (!Taxon.isMorpho(useTaxonName)) {

			  // Verify the Bioregion. Must have a legal bioregion for the given genus.
			  boolean isInvalidBioregion = invalidGenusBioregion(useTaxonName, bioregion);

			  if (AntwebProps.isDevMode() && code.contains("casent0170541")) s_log.debug("processLine() bioregion:" + bioregion + " useTaxonName:" + useTaxonName + " code:" + code + " isInvalidBioregion:" + isInvalidBioregion);

			  if (isInvalidBioregion) {
			    
			    boolean isFossil = false;
			    Taxon t = TaxonMgr.getTaxon(useTaxonName);
			    if (t != null && t.getIsFossil()) isFossil = true;

			    if (!isFossil) {
   	  	  	      //A.log("processLine() isInvalidBioregion:" + isInvalidBioregion + " bioregion:" + bioregion + " useTaxonName:" + useTaxonName + " isFossil:" + isFossil);

				  // the genus that was checked (the current valid name's), not the uploaded one
				  String message = Formatter.initCap(Taxon.getGenusFromName(useTaxonName)) + " not native to " + bioregion + " Region";
				  String displayCode = Specimen.makeReportLink(code);
				  getMessageMgr().addToMapMessages(MessageMgr.genusOutsideNativeBioregion, Html.esc(message), displayCode);   
				  specimenItem.put("flag", "red");
				  specimenItem.put("issue", MessageMgr.genusOutsideNativeBioregion);  
				  //skipRecord = "invalidBioregionForGenus";
				}
			  }
			//}
		  }
		}  

		if (false && (AntwebProps.isDevMode() && skipRecord != null)) {
		  s_log.warn("processLine() skipRecord:" + skipRecord 
			+ " line:" + lineNum 
			+ " code:" + code
			+ " taxon:" + taxonName
			+ " subfamily:" + subfamily
			);
		}

		if (skipRecord == null) {
			// Here we actually add the line item to the Antweb database                  

			Date startTime1 = new Date();
			saveSpecimen(specimenItem);
			Profiler.profile("saveSpecimen", startTime1, code); 
			Date startTime2 = new Date();

			taxonItem.put("source", shortFileName);
			taxonItem.put("insert_method", "specimenUpload");

			saveTaxon(taxonItem);  // THE ACTION See AntwebUpload.saveTaxon();

			Profiler.profile("saveTaxon", startTime2, code);
			Profiler.profile("saveSpecimenAndTaxon", startTime1, code); 
			return true;
		} else {
		    s_log.debug("processLine() false skipRecord:" + skipRecord);
		    return false;
		}
    }

    private static final int figuredElevation = 0;
    private static final int greaterThanElevation = 0;
    private static final int rangeElevation = 0;
    private static final int decimalElevation = 0;
    private static final int unfathomableElevation = 0;

    private void elevationReport() {
      s_log.info("FiguredElevations:" + figuredElevation
              + " greaterThanElevation:" + greaterThanElevation
              + " decimalElevation:" + decimalElevation
              + " rangeElevation:" + rangeElevation
              + " unfathomableElevation:" + unfathomableElevation);
    }


    public boolean invalidGenusBioregion(String taxonName, String bioregion) {

      if (taxonName == null || bioregion == null) return false;

	  boolean debug = false; //AntwebProps.isDevMode() && "formicinaebrachymyrmex afr001".equals(taxonName);

      //A.log("invalidGenusBioreegion() taxonName:" + taxonName + " bioregion:" + bioregion + " debug:" + debug);
      /*
	  if (TaxonPropMgr.isNativeNowhere(taxonName)) {
		  if (debug) A.log("invalidGenusBioregion() isNativeNowhere taxonName:" + taxonName);
		  return false;
	  }
      */

	  if (TaxonPropMgr.isIntroducedSomewhere(taxonName)) {
		  if (debug) s_log.debug("invalidGenusBioregion() isIntroduceSomewhere() taxonName:" + taxonName);
		  return false;
	  }

	  String genusTaxonName = Taxon.getGenusTaxonNameFromName(taxonName);
	  if (genusTaxonName == null) {
		  if (debug) s_log.debug("invalidGenusBioregion() genusTaxonNameFromName is null for taxonName:" + taxonName);
          return false;          
      }
      String firstChar = genusTaxonName.substring(0,1);
	  if ("(".equals(firstChar)) {
		  if (debug) s_log.debug("invalidGenusBioregion() ( is first char of taxonName:" + taxonName + " genusTaxonName:" + genusTaxonName);
		  return false;
	  }

	  Date startTimeBio = new Date();
	  //if (genusName != null) A.log("invalidGenusBioregione() genusName[0]:" + genusName.substring(0,1));
	  Genus genus = TaxonMgr.getGenus(genusTaxonName); // Really should use taxonName. Not distinct in a couple cases. See Integrity Queries (generaInMultipleSubfamilies & generaInMultipleSubfamilies2).
	  if (genus == null) {
		if (debug) s_log.debug("invalidGenusBioregion() genus not fouund for genusTaxonName:" + genusTaxonName + " taxon:" + taxonName);
        return false;
	  }

      if (!genus.isValid()) {
        if (debug) s_log.debug("invalidGenusBioregion() genus not valid:" + genus);
        return false;
      }

	  boolean legitBioregion = bioregion != null && !"null".equals(bioregion);
      if (debug) s_log.debug("invalidGenusBioregion() legitBioregion:" + legitBioregion);

	  if (legitBioregion) {
	    if (genus.getBioregionMap() == null) {
			if (debug) s_log.debug("invalidGenusBioregion() bioregionMap is null");
	    	return false; // For instance, fossil genera are not even listed.
		}
		boolean bioregionMapped = TaxonPropMgr.isMapped(genus.getBioregionMap(), bioregion);
	    if (debug) s_log.debug("invalidGenusBioregion() bioregionMapped:" + bioregionMapped);
		if (!bioregionMapped) {
          //A.iLog(7, "SpecimenUpload.invalidGenusBioregion() INVALID taxonName:" + taxonName + " bioregion:" + bioregion + " genusTaxonName:" + genusTaxonName + " genus:" + genus + " bioregionMap:" + genus.getBioregionMap(), 100);
		  return true;
		} else if (bioregion != null) {
		  // Remove it! 
		}
	  }
  
	  Profiler.profile("genusBioregion", startTimeBio, genusTaxonName);

      if (debug) s_log.debug("invalidGenusBioregion() default is false");
      return false;
    }

	private static int s_statusAndCurrentValidNameCount = 0;    

    // Similar method in AntwebUpload.
    public String setStatusAndCurrentValidName(String taxonName, Hashtable<String, Object> taxonItem, Hashtable<String, Object> specimenItem, String source)
      throws SQLException
    {  

      ++s_statusAndCurrentValidNameCount;

      Date startTime = new Date();
    
  	  if (false && "gryllinaegryllus (indet)".equals(taxonName)) {
  	    //A.log("SpecimenUpload.setStatusAndCurrentValidName() taxonName:" + taxonName + " count:" + s_statusAndCurrentValidNameCount + " morpho:" + Taxon.isMorpho(taxonName) +  " indet:" + Taxon.isIndet(taxonName));
      }
      
      // Here we choose the best taxa for uploaded specimen.  
      boolean skipTaxonEntry = false;
      String status = null;    
      String originalTaxonName = null;
      
      // The decision is CurrentValidNameRule.decide() (shared with the nightly name update,
      // SpecimenNameUpdate); the report lines, the homonym placeholder taxon and the name columns stay here.
      CurrentValidNameRule.Result rule = CurrentValidNameRule.decide(taxonName, CurrentValidNameRule.uploadLookup(getConnection()));
      status = rule.status;

      // A name of two or more AntCat taxa (Pachycondyla sauteri: now Cryptopone sauteri or Ectomomyrmex
      // sauteri) is not moved to the one the taxon table happens to hold: kept under the uploaded name and
      // listed for the curator, as the nightly name update does (SharedNameReport; Brian Fisher, 2026-10-20).
      String keptLine = rule.replacesName()
          ? SharedNameReport.uploadLine(taxonName, rule, isInHomonymTable(taxonName), placeholderTest(), getSharedNames()) : null;
      if (keptLine != null) {
        status = rule.uploadedStatus;
        getMessageMgr().addToMessages(MessageMgr.sharedNameKept, keptLine);
      } else switch (rule.kind) {
        case INDET:
        case MORPHO:
          break;

        case NOT_IN_ANTCAT: {
            /*
            If it is unrecognized, it could have a parent specified which is a synonym.  In this case
            we would want to insert the taxon with a new parent, and with modified taxonomic lineage.
            use makeSpecimenUseTaxon() as illustrated below.
            */
            // In AntCat, but not as a name AntWeb files specimens under (a junior homonym): its AntCat taxa.
            String homonymOnly = SharedNameReport.homonymOnlyLine(taxonName, getSharedNames());
            if (homonymOnly != null) {
              getMessageMgr().addToMessages(MessageMgr.taxonNamesAreHomonyms, homonymOnly);
            } else {
              // if quadrinomial put in a separate list.  Do upload, otherwise don't.
              if (Taxon.isQuadrinomial(taxonName)) {
                String displayName = Taxon.makeDescriptionLink(taxonName);
                getMessageMgr().addToMessages(MessageMgr.unavailableQuadrinomial, displayName);
              } else {
                if (Taxon.isAnt(taxonName)) {
                    // Currently we allow these to be uploaded.  In the future we won't.
                    String displayName = Taxon.makeDescriptionLink(taxonName);
                    getMessageMgr().addToMessages(MessageMgr.unrecognizedInvalidSpecies, displayName);
                }
              }
            }
            break;
        }

        case NO_CURRENT_VALID_NAME:
            s_log.debug("setStatusAndCurrentValidName() shouldn't status:" + status + " have a current valid name?");
            break;

        case KEEP:
            if (!(Status.VALID.equals(status) || Status.UNAVAILABLE.equals(status) || Status.UNIDENTIFIABLE.equals(status))) {
              s_log.debug("setStatusAndCurrentValidName() for taxonName:" + taxonName + " status not found:" + status);
            }
            break;

        default: {
            // REPLACE, TARGET_IS_HOMONYM, TARGET_MISSING, TARGET_SAME: filed under the current valid name.
            String currentValidName = rule.taxonName;
            originalTaxonName = rule.originalTaxonName;
            taxonName = currentValidName;
            skipTaxonEntry = true;

            ProfileCounter.add("SpecimenUploadProcess.SetStatusAndCurrentValidName()B");
            TaxonDb taxonDb = new TaxonDb(getConnection());
            String newTaxonStr = null;

            if (rule.kind == CurrentValidNameRule.Kind.TARGET_IS_HOMONYM) {
                Taxon dummyTaxon = new HomonymDb(getConnection()).getHomonym(currentValidName);  // Unsafe usage! Homonym primary key includes author_date.
                if (dummyTaxon != null) {
                    // Create a matching taxon to be parent of the specimen. We do not want children of homonyms.
                    // currentValidName is a misnomer at this point.  As a homonym, new taxon created that is "Unrecoginzed" as a place holder for the homonym.
                    s_log.warn("setStatusAndCurrentValidName() taxon does not exist for homonym with specimen.  Create.  new taxon:" + currentValidName + " code:" + specimenItem.get("code") + " taxonItem:" + taxonItem + " specimenItem:" + specimenItem);

                    taxonItem.put("family", dummyTaxon.getFamily());
                    taxonItem.put("subfamily", dummyTaxon.getSubfamily());
                    if (dummyTaxon.getTribe() != null) taxonItem.put("tribe", dummyTaxon.getTribe());
                    taxonItem.put("genus", dummyTaxon.getGenus());
                    if (dummyTaxon.getSubgenus() != null) taxonItem.put("subgenus", dummyTaxon.getSubgenus());
                    taxonItem.put("species", dummyTaxon.getSpecies());
                    if (dummyTaxon.getSubspecies() != null) taxonItem.put("subspecies", dummyTaxon.getSubspecies());

                    taxonItem.put("insert_method", "homononymMirroringTaxon");
                    taxonItem.put("source", source);

                    saveTaxonAndProjTaxon(taxonItem, Project.ALLANTWEBANTS);

                    newTaxonStr = makeSpecimenUseTaxon(taxonDb, currentValidName, originalTaxonName, specimenItem, taxonItem);
                }
            } else if (rule.kind == CurrentValidNameRule.Kind.TARGET_SAME) {
                s_log.warn("setStatusAndCurrentValidTaxonName() currentValidName should be distinct from taxonName:" + originalTaxonName);
            } else if (rule.kind == CurrentValidNameRule.Kind.REPLACE) {
                newTaxonStr = makeSpecimenUseTaxon(taxonDb, rule.validTaxonName, originalTaxonName, specimenItem, taxonItem);
                // "old -> new: why", worded as on the Name updates page (2026-10-20); once per pair.
                if (newTaxonStr != null) newTaxonStr = getNameReasons().line(newTaxonStr, originalTaxonName, rule.validTaxonName);
            }
            if (newTaxonStr != null)
              getUploadDetails().getMessageMgr().addToMessages(MessageMgr.recognizedInvalidSpecies, newTaxonStr);
        }
      }

	  if (!Taxon.isAnt(taxonName)) {
		String displayName = Taxon.makeDescriptionLink(taxonName);
        if (taxonName.contains("odontomachus cephalotes")) {
		  s_log.debug("setStatusAndCurrentValidName() NOT ANT taxonName:" + taxonName + " displayName:" + displayName);
          //AntwebUtil.logStackTrace();
        }
        // displayName += " (line:" + (String) specimenItem.get("lineNum") + ")";
		getMessageMgr().addToMessages(MessageMgr.nameNotInFamilyFormicidae, displayName);
  	  }
      
      if ("formicinaeforelophilus philippinensis_cf".equals(taxonName)) {
        s_log.debug("setStatusAndCurrentValidName() taxonName:" + taxonName + " status:" + status + " skip:" + skipTaxonEntry);
        //AntwebUtil.logShortStackTrace();
      }
      
      if (!skipTaxonEntry) {
        taxonItem.put("status", status);
        taxonItem.put("taxon_name", taxonName);
      }

      specimenItem.put("taxon_name", taxonName);
      specimenItem.put("status", status);
      if (originalTaxonName != null) {
        specimenItem.put("original_taxon_name", originalTaxonName);
      }
      
	  Profiler.profile("setStatusAndCurrentValidName", startTime, taxonName);
	  
	  return taxonName;
    }
  
    /** The reasons of the "Recognized invalid species" lines of this upload's report (UploadNameReasons). */
    private UploadNameReasons nameReasons;

    private UploadNameReasons getNameReasons() {
        if (nameReasons == null) nameReasons = UploadNameReasons.of(getConnection());
        return nameReasons;
    }

    private static String makeSpecimenUseTaxon(TaxonDb taxonDb, String currentTaxonName, String originalTaxonName, Hashtable specimenItem, Hashtable taxonItem)
      throws SQLException {
        
        Date startTime = new Date();

        ProfileCounter.add("SpecimenUploadProcess.makeSpecimenUseTaxon()");
        Taxon currentTaxon = taxonDb.getTaxon(currentTaxonName);
        if (currentTaxon == null) {
          s_log.warn("makeSpecimenUseTaxon() taxon not found:" + currentTaxonName);
          return null;
        }

        specimenItem.put("family", currentTaxon.getFamily());
        specimenItem.put("subfamily", currentTaxon.getSubfamily());
        if (currentTaxon.getTribe() != null) specimenItem.put("tribe", currentTaxon.getTribe());
        specimenItem.put("genus", currentTaxon.getGenus());
        if (currentTaxon.getSubgenus() != null) specimenItem.put("subgenus", currentTaxon.getSubgenus());
        specimenItem.put("species", currentTaxon.getSpecies());
        if (currentTaxon.getSubspecies() != null) specimenItem.put("subspecies", currentTaxon.getSubspecies());

        //specimenItem.put("status", currentTaxon.getStatus());
        //A.log("makeSpecimenUseTaxon() taxonItem.put:" + currentTaxon.getStatus());
        //A.log("makeSpecimenUseTaxon() taxonItem.put:" + currentTaxon.getSpecies());

        //String heading = "<b>Recognized invalid species.  Submission replaced with current valid name from AntCat.org <font color=green>(uploaded)</font></b>";
        String displayName = Taxon.makeDescriptionLink(originalTaxonName);
        if ("".equals(originalTaxonName)) {
           displayName = "[empty string]";
             // was: getUploadDetails().getPassWorldantsSpeciesCheckSet().add(taxonName);
        }
        String toName = Taxon.makeDescriptionLink(currentTaxonName);

        //uploadDetails.getMessageMgr().addToMessages(MessageMgr.recognizedInvalidSpecies, displayName + " -> " + toName);

        String returnVal = displayName + " -> " + toName; 

 	    Profiler.profile("makeSpecimenUseTaxon", startTime);
 	    
 	    return returnVal;
    }
        
    
}
