package org.calacademy.antweb.upload;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

import javax.servlet.http.*;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.avalon.framework.logger.ConsoleLogger;
import org.apache.avalon.framework.logger.Logger;

import org.apache.struts.action.*;

import java.sql.*;
 
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public class SpecimenUploadProcess extends SpecimenUploadSupport {
/* 
    Biota Data is uploaded through the curator interface.  It loads data from the "Biota Database"
    (a Windows client database that Michele Esposito uses).  The data is exported from Biota,
    and the data file uploaded.  Biota application is no longer under active development.
    
*/
    private static Log s_log = LogFactory.getLog(SpecimenUploadProcess.class);

    static int MAXLENGTH = 80;

    String currentDateFunction = "now()";  // for mysql 
    
    private TreeSet illegitimateCountries = new TreeSet();
    private TreeSet illegitimateBioregions = new TreeSet();

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
    boolean processLine(Hashtable specimenItem, Hashtable taxonItem, int lineNum, String shortFileName, Group group) 
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
		  if ((code == null) || (code.length() == 0) ) {
			s_log.warn("processLine() code is null or empty string line:" + lineNum + " taxon:" + taxonName);
			getMessageMgr().addToMessages(MessageMgr.codeNotFound, "line:" + LineNumMgr.getDisplayLineNum(lineNum) + " code:" + code);
			skipRecord = "codeIsNull";
		  }
		}

		if (skipRecord == null) {
		  if (Utility.isBlank(subfamily)) {
			//s_log.warn("processLine() subfamily is empty string line:" + lineNum + " taxon:" + taxonName);
			getMessageMgr().addToMessages(MessageMgr.emptyStringSubfamily, "line:" + LineNumMgr.getDisplayLineNum(lineNum) + " code:" + code + " taxon:" + taxonName);
			skipRecord = "subfamilyEmptyString";
		  }
		}

		if (skipRecord == null) {
		  if (Utility.isBlank(genus)) {
			  A.log("processLine() genus is empty string line:" + lineNum + " displayLineNum:" + LineNumMgr.getDisplayLineNum(lineNum) + " genus:" + genus + " taxon:" + taxonName);
			  getMessageMgr().addToMessages(MessageMgr.emptyStringGenus, "line:" + LineNumMgr.getDisplayLineNum(lineNum) + " code:" + code + " taxon:" + taxonName);
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
			getMessageMgr().addToMessages(MessageMgr.emptyStringSpecies, "line:" + LineNumMgr.getDisplayLineNum(lineNum) + " code:" + code + " taxon:" + taxonName);
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
			  String offender = "taxonName:" + taxonName + " lineNum:" + lineNum;
			  getMessageMgr().addToMapMessages(MessageMgr.invalidSubfamilyMap, Formatter.initCap(subfamily), offender);
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
			  boolean isExistingTaxonSubfamilyForAGenus = (new TaxonDb(getConnection())).isExistingSubfamilyForAGenus(family, subfamily, genus);
			  boolean isExistingHomonymSubfamilyForAGenus = false;
			  if (!isExistingTaxonSubfamilyForAGenus) {
				// Date startTime1 = new Date();
				// Check HomonymDb.
				isExistingHomonymSubfamilyForAGenus = (new HomonymDb(getConnection())).isExistingSubfamilyForAGenus(family, subfamily, genus);
				if (isExistingHomonymSubfamilyForAGenus) {
				  //A.log("processLine() Homonym found in antweb.  Subfamily:" + subfamily + " genus:" + genus);
				  String displayTaxonName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + subfamily+genus + "'>" + Taxon.displaySubfamilyGenus(subfamily, genus) + "</a>";                          
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
				boolean subfamilyNotFoundInAntweb = "".equals(antwebSubfamily) || (subfamily == null) || (antwebTaxonName == null);
				if (true && subfamilyNotFoundInAntweb) {
				  //A.log("processLine() 2 subfamily not found in antweb.  antwebTaxonName:" + antwebTaxonName + " subfamily:" + subfamily + " genus:" + genus + " antwebSubfamily:" + antwebSubfamily);
				  getMessageMgr().addToMessages(MessageMgr.invalidSubfamilyForGenus, Taxon.displaySubfamilyGenus(subfamily, genus));
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
				  String displayTaxonName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + subfamilyGenus + "'>" + Taxon.displaySubfamilyGenus(antwebSubfamily, genus) + "</a>";
				  getMessageMgr().addToMapMessages(MessageMgr.invalidSubfamilyForGenusReplaced, uploadedSubfamilyGenus, displayTaxonName); 
				}
				//Profiler.profile("genus2", startTime1);                     

			  } else {  // existingSubfamilyForGenus 
				/* For testing:
				delete from taxon where insert_method = "addMissingGenus" and access_group = 21;                     
				*/               
				// Date startTime1 = new Date();
						  
				  // Add to the Subfamily/Genus combo not found and DO upload
				  String displaySubfamilyGenus = "<a href=" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + subfamilyGenus + ">" + Taxon.displaySubfamilyGenus(subfamilyGenus) + "</a>";
				  String displayTaxonName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.displayTaxonName(taxonName) + "</a>";

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
			  specimenItem.put("is_introduced", Integer.valueOf(1));
			  getMessageMgr().flag("is_introduced");
			}
		}

		if (skipRecord == null) {
		  if (!isIntroduced) {
			//if (!Taxon.isMorpho(useTaxonName)) {

			  // Verify the Bioregion. Must have a legal bioregion for the given genus.
			  boolean isInvalidBioregion = invalidGenusBioregion(useTaxonName, bioregion);

			  //if (code.contains("casent0103752") || code.contains("csironc0202")) A.log("processLine() bioregion:" + bioregion + " taxonName:" + useTaxonName + " code:" + code + " isInvalidBioregion:" + isInvalidBioregion);
			  if (isInvalidBioregion) {
			    
			    boolean isFossil = false;
			    Taxon t = TaxonMgr.getTaxon(useTaxonName);
			    if (t != null && t.getIsFossil()) isFossil = true;
 	 	 	    //A.log("processLine() isInvalidBioregion:" + isInvalidBioregion + " bioregion:" + bioregion + " useTaxonName:" + useTaxonName + " isFossil:" + isFossil);

			    if (!isFossil) {
				  String message = Formatter.initCap(Taxon.getGenusFromName(taxonName)) + " not native to " + bioregion + " Region";
				  String displayCode = "<a href='" + AntwebProps.getDomainApp() + "/specimen.do?code=" + code + "'>" + code + "</a>";
				  getMessageMgr().addToMapMessages(MessageMgr.genusOutsideNativeBioregion, message, displayCode);   
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
			saveTaxon(taxonItem);
			Profiler.profile("saveTaxon", startTime2, code); 
			Profiler.profile("saveSpecimenAndTaxon", startTime1, code); 
			return true;
		} else {
		    A.log("processLine() false skipRecord:" + skipRecord);
		    return false;
		}
    }

    private static int figuredElevation = 0;
    private static int greaterThanElevation = 0;
    private static int rangeElevation = 0;
    private static int decimalElevation = 0;
    private static int unfathomableElevation = 0;

    private void elevationReport() {
      s_log.info("FiguredElevations:" + figuredElevation
              + " greaterThanElevation:" + greaterThanElevation
              + " decimalElevation:" + decimalElevation
              + " rangeElevation:" + rangeElevation
              + " unfathomableElevation:" + unfathomableElevation);
    }


    public boolean invalidGenusBioregion(String taxonName, String bioregion) {
    
      if (taxonName == null || bioregion == null) return false;

/*
//if (taxonName.contains("wroughtoni") && bioregion.contains("australasia")) {
  A.log("invalidGenusBioregion() introduced:" + TaxonPropMgr.isIntroduced(taxonName, bioregion) 
    + " introducedSomewhere:" + TaxonPropMgr.isIntroducedSomewhere(taxonName)
    + " genusTaxonName:" + Taxon.getGenusTaxonNameFromName(taxonName)
    + " genus:" + TaxonMgr.getGenus(Taxon.getGenusTaxonNameFromName(taxonName))
    );
//} 
	  if (TaxonPropMgr.isNativeNowhere(taxonName)) {
		  //s_log.warn("invalidGenusBioregion() INTRODUCED taxonName:" + taxonName);
		  return false;
	  }
*/
	  if (TaxonPropMgr.isIntroducedSomewhere(taxonName)) {
		  //s_log.warn("invalidGenusBioregion() INTRODUCED taxonName:" + taxonName);
		  return false;
	  }

	  if (TaxonPropMgr.isIntroducedSomewhere(taxonName)) {
		  //s_log.warn("invalidGenusBioregion() INTRODUCED taxonName:" + taxonName);
		  return false;
	  }
	  String genusTaxonName = Taxon.getGenusTaxonNameFromName(taxonName);
	  if (genusTaxonName == null) {
		  //A.log("invalidGenusBioregion() genusName is null for taxonName:" + taxonName);
          return false;          
      }
      String firstChar = genusTaxonName.substring(0,1);
	  if ("(".equals(firstChar)) {
		  //A.log("invalidGenusBioregion() taxonName:" + taxonName + " genusTaxonName:" + genusTaxonName);
		  return false;
	  }
	  Date startTimeBio = new Date();
	  //if (genusName != null) A.log("invalidGenusBioregione() genusName[0]:" + genusName.substring(0,1));
	  Genus genus = TaxonMgr.getGenus(genusTaxonName); // Really should use taxonName. Not distinct in a couple cases. See Integrity Queries (generaInMultipleSubfamilies & generaInMultipleSubfamilies2).

	  if (genus == null) {
		//A.log("invalidGenusBioregion() genus:" + genusTaxonName + " not found for taxon:" + taxonName);  
        return false;
	  }

      if (!genus.isValid()) {
        return false;
      }

	  boolean legitBioregion = bioregion != null && !"null".equals(bioregion);
	  if (legitBioregion) {
	    if (genus.getBioregionMap() == null) return false; // For instance, fossil genera are not even listed.
		boolean bioregionMapped = TaxonPropMgr.isMapped(genus.getBioregionMap(), bioregion);
		if (!bioregionMapped) {
          //A.iLog(7, "SpecimenUpload.invalidGenusBioregion() INVALID taxonName:" + taxonName + " bioregion:" + bioregion + " genusTaxonName:" + genusTaxonName + " genus:" + genus + " bioregionMap:" + genus.getBioregionMap(), 100);
		  return true;
		} else if (bioregion != null) {
		  // Remove it! 
		}
	  }
  
	  Profiler.profile("genusBioregion", startTimeBio, genusTaxonName);      

      return false;
    }

	private static int s_statusAndCurrentValidNameCount = 0;    
    
    // Similar method in AntwebUpload.
    public String setStatusAndCurrentValidName(String taxonName, Hashtable taxonItem, Hashtable specimenItem, String source)
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
      
      //The case of morpho and indet are easy because we can determine by the taxon name.
      if (Taxon.isIndet(taxonName)) {
        status = Status.INDETERMINED;  
      } else if (Taxon.isMorpho(taxonName)) {
        status = Status.MORPHOTAXON;
      } else {
          TaxonDb taxonDb = new TaxonDb(getConnection());
          DummyTaxon taxon = taxonDb.getDummyTaxon(taxonName);
          if (taxon != null) status = taxon.getStatus();

          if ((taxon == null) || (status == null) || (Status.UNRECOGNIZED.equals(status))) {
          
            if (false && taxonName.contains("gryllinaegryllus")) A.log("setStatusAndCurrentValidName() taxon:" + taxon 
              + " status:" + status + " statusAndCurrentValidNameCount:" + s_statusAndCurrentValidNameCount
              + " quad:" + Taxon.isQuadrinomial(taxonName)
              + " ant:" + Taxon.isAnt(taxonName)
              );

			/*
			If it is unrecognized, it could have a parent specified which is a synonym.  In this case
			we would want to insert the taxon with a new parent, and with modified taxonomic lineage.
			use makeSpecimenUseTaxon() as illustrated below.
			*/

            if (status == null) status = Status.UNRECOGNIZED;

            if ((new HomonymDb(getConnection())).isHomonym(taxonName)) {
              //String heading = "<b>Taxon names recognized as Homonyms.  Names are not verified against AntCat.org  Submission <font color=green>(uploaded)</font></b>";
              String displayName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.displayTaxonName(taxonName) + "</a>";
              getMessageMgr().addToMessages(MessageMgr.taxonNamesAreHomonyms, displayName);            
            } else {
              // if quadrinomial put in a separate list.  Do upload, otherwise don't.
              if (Taxon.isQuadrinomial(taxonName)) {
                //String heading = "<b>Unavailable quadrinomial. Submission <font color=green>(uploaded)</font></b>";
                String displayName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.displayTaxonName(taxonName) + "</a>";
                getMessageMgr().addToMessages(MessageMgr.unavailableQuadrinomial, displayName);            
              } else {
                if (Taxon.isAnt(taxonName)) {
                	  // Currently we allow these to be uploaded.  In the future we won't.
					//String heading = "<b>Unrecognized Invalid species.  Names not found in AntCat.org. Submission <font color=green>(uploaded)</font></b>";
					String displayName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.displayTaxonName(taxonName) + "</a>";
					getMessageMgr().addToMessages(MessageMgr.unrecognizedInvalidSpecies, displayName);            
    		    }
              }
            }
          } else { 
            String currentValidName = taxon.getCurrentValidName();
            String subfamily = taxon.getSubfamily();
            status = taxon.getStatus();
 
            if (Status.usesCurrentValidName(status)) {
              if (currentValidName == null) {
                A.log("setStatusAndCurrentValidName() shouldn't status:" + status + " have a current valid name?");
                // Shouldn't a status that uses a current valid name have a current valid name?
              } else {

                originalTaxonName = taxonName;
                taxonName = currentValidName;
                skipTaxonEntry = true;
                                
                String currentValidTaxonName = TaxonDb.getCurrentValidTaxonName(getConnection(), currentValidName);  // Does not look in Homonym table.

                String newTaxonStr = null;
                
                if (currentValidTaxonName == null) {
                  DummyTaxon dummyTaxon = (new HomonymDb(getConnection())).getDummyTaxon(currentValidName);
                  boolean isHomonym = false;
                  if (dummyTaxon != null) isHomonym = true;
                  if (isHomonym) {
                    // Seems to be Homonym.  Ideally in this case, we would create a taxon for the insertion.  As is, pointing at not current valid name.
                    // XXX.  FM.  See http://localhost/antweb/specimen.do?name=fmnhins0000109175 
                    // Create a matching taxon to be parent of the specimen.
                    // We will do this even AFTER we shut off specimen upload taxa creation.  We do not want children of homonyms.

                    // currentValidName is a misnomer at this point.  As a homonym, new taxon created that is "Unrecoginzed" as a place holder for the homonym.
                    s_log.warn("setStatusAndCurrentValidName() taxon does not exist for homonym with specimen.  Create.  new taxon:" + currentValidName + " code:" + specimenItem.get("code") + " taxonItem:" + taxonItem + " specimenItem:" + specimenItem);

//                    DummyTaxon currentTaxon = (new HomonymDb(getConnection())).getDummyTaxon(currentValidName);

	                taxonItem.put("family", dummyTaxon.getFamily());
                    taxonItem.put("subfamily", dummyTaxon.getSubfamily());
                    if (dummyTaxon.getTribe() != null) taxonItem.put("tribe", dummyTaxon.getTribe());
                    taxonItem.put("genus", dummyTaxon.getGenus());
                    if (dummyTaxon.getSubgenus() != null) taxonItem.put("subgenus", dummyTaxon.getSubgenus());
                    taxonItem.put("species", dummyTaxon.getSpecies());
                    if (dummyTaxon.getSubspecies() != null) taxonItem.put("subspecies", dummyTaxon.getSubspecies());

                    taxonItem.put("insert_method", "homononymMirroringTaxon");
                    taxonItem.put("source", source);

                    // (new AntwebUpload(getConnection())). // *** changed Nov 11, 2019
                    saveTaxonAndProjTaxon(taxonItem, Project.ALLANTWEBANTS);
                    
                    status = Status.UNRECOGNIZED;

                    newTaxonStr = makeSpecimenUseTaxon(taxonDb, currentValidName, originalTaxonName, specimenItem, taxonItem);

                  } else {
                    //A.log("setStatusAndCurrentValidName() No currentValidTaxonName for" + " currentValidName:" + currentValidName + " taxonName:" + taxonName + " code:" + specimenItem.get("code"));
                  }
                } else {
                  if (currentValidTaxonName.equals(originalTaxonName)) {
                    s_log.warn("setStatusAndCurrentValidTaxonName() currentValidName should be distinct from taxonName:" + taxonName);
                  } else {
                    // We found it.  Use it.

                    status = Status.VALID;

                    newTaxonStr = makeSpecimenUseTaxon(taxonDb, currentValidTaxonName, originalTaxonName, specimenItem, taxonItem);

                    //A.iLog(6, "setStatusAndCurrentValidName() Using currentValidTaxonName:" + currentValidTaxonName + " for originalTaxonName:" + originalTaxonName, 100);
                  }
                } 
                if (newTaxonStr != null)
                  getUploadDetails().getMessageMgr().addToMessages(MessageMgr.recognizedInvalidSpecies, newTaxonStr);

              } // Check for null currentValidName
            } else if (Status.VALID.equals(status)) {
              // do nothing
            } else {
              A.log("setStatusAndCurrentValidName() for taxonName:" + taxonName + " status not found:" + status);
            }            
          }
      } // end if/else

	  if (!Taxon.isAnt(taxonName)) {
		String displayName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.displayTaxonName(taxonName) + "</a>";
        if (taxonName.contains("odontomachus cephalotes")) {
		  A.log("setStatusAndCurrentValidName() NOT ANT taxonName:" + taxonName + " displayName:" + displayName);
          //AntwebUtil.logStackTrace();
        }
        // displayName += " (line:" + (String) specimenItem.get("lineNum") + ")";
		getMessageMgr().addToMessages(MessageMgr.nameNotInFamilyFormicidae, displayName);
  	  }
      
      if ("formicinaeforelophilus philippinensis_cf".equals(taxonName)) {
        A.log("setStatusAndCurrentValidName() taxonName:" + taxonName + " status:" + status + " skip:" + skipTaxonEntry);      
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
  
    private static String makeSpecimenUseTaxon(TaxonDb taxonDb, String currentTaxonName, String originalTaxonName, Hashtable specimenItem, Hashtable taxonItem)
      throws SQLException {
        
        Date startTime = new Date();

        DummyTaxon currentTaxon = taxonDb.getDummyTaxon(currentTaxonName);
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
        
/*
        taxonItem.put("family", currentTaxon.getFamily());
        taxonItem.put("subfamily", currentTaxon.getSubfamily());
        if (currentTaxon.getTribe() != null) taxonItem.put("tribe", currentTaxon.getTribe());
        taxonItem.put("genus", currentTaxon.getGenus());
        if (currentTaxon.getSubgenus() != null) taxonItem.put("subgenus", currentTaxon.getSubgenus());
        taxonItem.put("species", currentTaxon.getSpecies());
        if (currentTaxon.getSubspecies() != null) taxonItem.put("subspecies", currentTaxon.getSubspecies());
*/
        //A.log("makeSpecimenUseTaxon() taxonItem.put:" + currentTaxon.getSpecies());

        //String heading = "<b>Recognized invalid species.  Submission replaced with current valid name from AntCat.org <font color=green>(uploaded)</font></b>";
        String displayName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + originalTaxonName + "'>" + Taxon.displayTaxonName(originalTaxonName) + "</a>";
        if ("".equals(originalTaxonName)) {
           displayName = "[empty string]";
             // was: getUploadDetails().getPassWorldantsSpeciesCheckSet().add(taxonName);
        }
        String toName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + currentTaxonName + "'>" + Taxon.displayTaxonName(currentTaxonName) + "</a>";

        //uploadDetails.getMessageMgr().addToMessages(MessageMgr.recognizedInvalidSpecies, displayName + " -> " + toName);

        String returnVal = displayName + " -> " + toName; 

 	    Profiler.profile("makeSpecimenUseTaxon", startTime);
 	    
 	    return returnVal;
    }
        
    
}
