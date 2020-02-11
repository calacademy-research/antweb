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
import org.apache.regexp.*;

import java.sql.*;
 
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public abstract class SpecimenUploadParse extends SpecimenUploadProcess {

/* 
    Biota Data is uploaded through the curator interface.  It loads data from the "Biota Database"
    (a Windows client database that Michele Esposito uses).  The data is exported from Biota,
    and the data file uploaded.  Biota application is no longer under active development.    
*/

    private static Log s_log = LogFactory.getLog(SpecimenUploadParse.class);

    static int MAXLENGTH = 80;

    String currentDateFunction = "now()";  // for mysql 

    SpecimenUploadDb m_specimenUploadDb = null;

    private ArrayList<String> m_headerArrayList = null;

	//  columns describing taxonomic rank.  This is used for lowercases, only.
	String[] taxonomy = {"kingdom_name", "phylum_name", "class_name", "order_name", "specimencode", "family", "subfamily", "tribe", "genus",
			"subgenus", "speciesgroup", "species", "subspecies" };
	ArrayList taxonomyHeaders = new ArrayList(Arrays.asList(taxonomy));

    SpecimenUploadParse(Connection connection) {
      
      super(connection);
    }

    //public abstract boolean importSpecimens(UploadFile uploadFile, Group group) throws SQLException, TestException, AntwebException;
        
    protected String parseLine(String theLine, int lineNum, Hashtable specimenItem, Hashtable taxonItem
      , ArrayList otherColumns, ArrayList colList, String shortFileName, Login accessLogin) 
      throws SQLException, AntwebException {

        Group accessGroup = accessLogin.getGroup();

        Date startTime = new Date();
        String initLine = theLine;                                  
        StringBuffer otherInfo = new StringBuffer();             
        String otherColumn = null;
        String errorMessage = null;            
		String code = null;
		
        try {      
           
            RE tab = new RE("\t");
            RE multipleSpaces = new RE(" +");
            RE multipleQuotes = new RE("\"{3}");
            RE badXML = new RE("['\"&/<>]");          
            Formatter formatter = new Formatter();
            ArrayList<String> elements = null;

            Float lat = new Float(0);
            Float lon = new Float(0);

            Iterator loopIter = null;
            if (otherInfo.length() > 0) {
                otherInfo.delete(0, otherInfo.length());
            }
            otherColumn = "";
            otherInfo.append("<features>");

            boolean debugLine = false;
            if (AntwebProps.isDevMode() && true && (
                      theLine.contains("32-004941")
                   // || theLine.contains("0000105982")
                 )) {
                debugLine = true;
            }

            theLine = multipleQuotes.subst(theLine, "");

            //s_log.warn("parseLine() theLine:" + theLine);

            String[] loopComponents = tab.split(theLine);
            elements = new ArrayList<String>(Arrays.asList(loopComponents));
            loopIter = elements.iterator();
            int colIndex = 0;
            

			String adm1ToIsland = null;
                        
            for (String next : elements) {
                String element = multipleSpaces.subst(next.trim(), " ");

                element = Utility.customTrim(element, "\'");
                element = Utility.customTrim(element, "\"");
                if (element == null) element = "";

                //A.log("next:" + next + " element:" + element);
                // if (colIndex == 0) code = element;
                // colIndex:27  colList:27  otherColumns:27
                if ((colIndex < colList.size()) && (colIndex < otherColumns.size())) {
                    if (colList.get(colIndex) != null) {
                        String col = (String) colList.get(colIndex);
                        col = col.toLowerCase();
 
                        /* This code block handles columns and headers as listed and defined in AntwebUpload */
                        if (taxonomyHeaders.contains(col)) {
                            element = element.toLowerCase();
                        }

                        if (col.equals("kingdom_name")) {
                            //s_log.warn("parseLine() col:" + col + " element:" + element);
                            if (element.equals("")) element = "animalia";
                        }
                        if (col.equals("phylum_name")) {
                            //s_log.warn("parseLine() col:" + col + " element:" + element);
                            if (element.equals("")) element = "arthropoda";
                        }
                        if (col.equals("class_name")) {
                            //s_log.warn("parseLine() col:" + col + " element:" + element);
                            if (element.equals("")) element = "insecta";
                        }                                
                        if (col.equals("order_name")) {
                            //s_log.warn("parseLine() col:" + col + " element:" + element);
                            if (element.equals("")) element = "hymenoptera";
                        }
                        if (col.equals("family")) {
                            //s_log.warn("parseLine() col:" + col + " element:" + element);
                            if (element.equals("")) element = "formicidae";
                        }

                        if (col.equals("species")) {
                          element = Utility.makeASCII(element);
                        }        

                        if (goodTaxonHeaders.contains(col)) {
                            // A.log("parseLine() col:" + col + " element:" + element);
                            if (!"".equals(element) && (element != null) && !"type".equals(col)) { // Specimen can have this but not taxon
                              taxonItem.put(col, element);
                            }
                        }
                                 
                        if (col.equals("code")) {
                           String lowerCode = element.toLowerCase();
                           String newCode = (new Formatter()).removeSpaces(lowerCode);
                           code = newCode;
                           //if (!lowerCode.equals(newCode)) ++spacesRemoved;
                           specimenItem.put(col, newCode);
                           //A.log("parseLine() code col:" + col + " element:" + element);
                        } else {
                           if (!"".equals(element) && (element != null)) {
                             specimenItem.put(col, element);
                             // A.log("parseLine() not code col:" + col + " element:" + element);
                           }
                        }                                    
                    } else {
                        /* Columns that require data type conversion are not listed above so that here
                           they cam be handled manually.  adding them to the hashtable.
                            See AntwebUpload.saveHash() for manual handling of elevation, date_collected
                              , access_group, access_login, decimal_longitude, decimal_latitude
                              , datedetermined
                         */
                        
                        boolean otherColumnFound = false;
                         
                        if (((String) otherColumns.get(colIndex)).equals("loclongitude")) {
                            otherColumnFound = true;
                            Float number = convertGeorefToDecimal(element.toLowerCase());
                            if (number >= -180 && number <= 180) {
								specimenItem.put("decimal_longitude", number);
								lon = new Float(number.floatValue() * 1000);
								if (lon.intValue() == 0) {
									element = "";
									specimenItem.put("decimal_longitude",  new Float(-999.9));
								}
                            } else {                              
						      //String heading = "<b>Invalid lat/lon <font color=red>(not uploaded):</font></b>";
							  //getMessageMgr().addToMessageStrings(heading, (String) specimenItem.get("code")); 
						      getMessageMgr().addToMessages(MessageMgr.invalidLatLon, (String) specimenItem.get("code")); 
						      errorMessage = "invalidLongitude for code:" + code;
						      break;
                            }
                        }

                        if (((String) otherColumns.get(colIndex)).equals("loclatitude")) {
                            otherColumnFound = true;
                            Float number = convertGeorefToDecimal(element.toLowerCase());
                            if (number >= -90 && number <= 90) {
								specimenItem.put("decimal_latitude", number);
								lat = new Float(number.floatValue() * 1000);
								//A.log("parseLine() lat:" + lat + " number:" + number);
								if (lat.intValue() == 0) {
									element = "";
									specimenItem.put("decimal_latitude", new Float(-999.9));
								}
                            } else {                              
							  //String heading = "<b>Invalid lat/lon <font color=red>(not uploaded):</font></b>";
							  //getMessageMgr().addToMessageStrings(heading, (String) specimenItem.get("code")); 
							  getMessageMgr().addToMessages(MessageMgr.invalidLatLon, (String) specimenItem.get("code")); 
							  errorMessage =  "invalidLatitude for code:" + code;
							  break;							  
                            }
                        }

                        // Elevation is inserted into the database as a simple number so that it is searchable.
                        //   and added to the hashtable in it's original form for display.
                        if (((String) otherColumns.get(colIndex)).equals("elevation")) {   
                            otherColumnFound = true;
                            Integer elevation = getElevationFromString(element, lineNum);
                            if (elevation != null) {
                                specimenItem.put("elevation", elevation);
                            }
                        } 
                        
                        if (!otherColumnFound) {
                            //String heading = "<b>Unrecognized Column <font color=red>(ignored):</font></b>";
                            //getMessageMgr().addToMessageStrings(heading, (String) otherColumns.get(colIndex));
                            getMessageMgr().addToMessages(MessageMgr.unrecognizedColumn, (String) otherColumns.get(colIndex));                            
                        }
                         
                        if (!element.equals("")) {
                            otherColumn = (String) otherColumns.get(colIndex);
                            otherColumn = badXML.subst(otherColumn, "");
                            //A.log("parseLine() otherColumn:" + otherColumn);
                            element = formatter.replaceBadXML(element);
                            otherInfo.append("<" + otherColumn + ">" + element + "</" + otherColumn + ">");
                        }
                    }
                    ++colIndex;
                
                } else {  //  if ((colIndex < colList.size()) && (colIndex < otherColumns.size())) {
                    String message = "parseLine() - Something wrong with specimen.  colIndex:" + colIndex + " colList.size:" + colList.size() + " otherColumns.size:" + otherColumns.size();
                    if (specimenItem.containsKey("code")) {
                        message += " code:" + (String) specimenItem.get("code");
                    } else {
                        message += " code:?";
                    }
                    
                    //String heading = "<b>Invalid Column Count <font color=red>(Not uploaded)</font></b>";
                    //getMessageMgr().addToMessageSets(heading, (String) specimenItem.get("code"));
                    getMessageMgr().addToMessages(MessageMgr.invalidColumn, (String) specimenItem.get("code"));
                    s_log.error(message);
                    errorMessage = "invalid column for code:" + code;
                    break;
                }
            } // end for looping through columns

            // ONCE WE HAVE THE COLUMNS PARSED, NOW WE CAN TOUCH DATA...
  
		/*
		Add two more tests. Both upload anyway, just warnings. 
		  If we use a different subfamily from the one enterered.
		  If the specimen is not an ant.
		*/

            String subfamilyName = (String) taxonItem.get("subfamily"); // The one the user entered. We prefer the one inferred from genus.
            String genusName = (String) taxonItem.get("genus");
            String speciesName = (String) taxonItem.get("species");
            String subspeciesName = (String) taxonItem.get("subspecies");
            
			/*  
			  (1). if species name blank = change to (indet)
			  (2). if genus name blank = change to (subfamily) name
			  (3). if subfamily blank, then change to (formicidae) [in this case the genus would also be (Formicidae)
			  For species, there are a couple of other changes that would be helpful.
			  if there is a ? at the end of  species name, have it removed
			  if there is only "sp." for species name,  change to (indet)
			  Also, the field subfamily is not available in all the SCAN data.
   	        */
   	        
   	        // Safe to assume? A non-ant could be created if it's subfamily was omitted as an ant...
            if (Utility.isBlank(subfamilyName)) {
              subfamilyName = "(formicidae)";
            }
            
            // If it is blank, make it a specified indet.
            if (Utility.isBlank(genusName) || "(indet)".equals(genusName)) {
              if ("(formicidae)".equals(subfamilyName)) {
                genusName = "(formicidae)";
              } else {
                genusName = "(" + subfamilyName + ")";
              }
            }         

    		// We ignore the subfamily column. Use genus to derive.
     	    if (!Genus.isIndet(genusName)) {
     	        // Perhaps replace subfamilyName with the parent of the genus...
			    Genus genus = TaxonMgr.getGenusFromName(genusName);
			    //A.log("not indet:" + genus);
				if (genus != null) {
                  // If we find it, use it.
				  String subfamily = genus.getSubfamily();
                  if (!"(Formicidae)".equals(subfamilyName) && !subfamily.equals(subfamilyName)) {                  
 					  String displayTaxonName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + subfamily + genusName + "'>" + Taxon.displaySubfamilyGenus(subfamily, genusName) + "</a>";
					  getMessageMgr().addToMapMessages(MessageMgr.preferredSubfamilyForGenusReplaced, subfamilyName, displayTaxonName);                  
                      //A.log("buildLines() preferredSubfamilyForGenusReplaced: displayTaxonName:" + displayTaxonName);
                  }
                  subfamilyName = subfamily;
				} else {
				  // If we don't find it, maybe it is not an ant. Then insert it, no error message.
				  if (!("(Formicidae)".equals(subfamilyName) || Subfamily.isValidAntSubfamily(subfamilyName))) {
					if (AntwebProps.isDevMode()) {
					  String displayCode = "<a href='" + AntwebProps.getDomainApp() + "/specimen.do?code=" + code + "'>" + code + "</a>";
					  getMessageMgr().addToMessages(MessageMgr.nonAntTaxa, displayCode); 
					  // No error code. Allowed.                 
					}
				  } else {
				    if (Taxon.isMorphoOrIndet(genusName)) {
				      //A.log("parseLine() isMorphoOrIndet:" + genusName);
				    } else {
  		 	 	 	  // No valid subfamily found for this genus.
					  getMessageMgr().addToMessages(MessageMgr.noValidSubfamilyForGenus, genusName);
					  //A.log("parseLine() no valid subfamily found for subfamily:" + subfamilyName + " genus:" + genusName + " lineNum:" + LineNumMgr.getDisplayLineNum(lineNum));
					  errorMessage = "no validSubfamilyForGenus:" + genusName;                  				  
                    }
				  }
				}
            }
        
            if (Utility.isBlank(speciesName) || "sp.".equals(speciesName)) {
              speciesName = "(indet)";
            }
            speciesName = Formatter.stripString(speciesName, "?");
			if (speciesName.contains(" ")) {
			  int spaceIndex = speciesName.indexOf(" ");
			  String tempSpeciesName = speciesName.substring(0, spaceIndex);
			  subspeciesName = speciesName.substring(spaceIndex + 1);
			  speciesName = tempSpeciesName;
             // A.log("parseLine() null for speciesName:" + speciesName + " spaceIndex:" + spaceIndex);
  	  	    }
            if (speciesName.contains("\"") 
              || (subspeciesName != null && subspeciesName.contains(" ")))
            {
                String tempName = speciesName;
                if (subspeciesName != null && subspeciesName.contains(" ")) tempName += " " + subspeciesName;
                getMessageMgr().addToMessages(MessageMgr.invalidSpeciesName, tempName);
                errorMessage = "invalid name for species:" + tempName;
                A.log("parseLine() errorMessage:" + errorMessage);
            }

			if (LineNumMgr.getDisplayLineNum(lineNum) == 0) {
			  s_log.warn("parseLine() line:" + lineNum + " displayLineNum:" + LineNumMgr.getDisplayLineNum(lineNum)
				+ " subfamily:" + subfamilyName + " genusName:" + genusName + " speciesName:" + speciesName + " errorMessage:" + errorMessage);
			}
            //if ("utic00201495".equals(code)) A.log("parseLine() lineNum:" + LineNumMgr.getDisplayLineNum(lineNum) + " speciesName:" + speciesName + " errorMessage:" + errorMessage);

            if (errorMessage == null) {
			  taxonItem.put("subfamily", subfamilyName);
			  specimenItem.put("subfamily", subfamilyName);
			  taxonItem.put("genus", genusName);
			  specimenItem.put("genus", genusName);
			  if (!Utility.isBlank(speciesName)) {
			  taxonItem.put("species", speciesName);
			  specimenItem.put("species", speciesName);
			  //A.log("parseLine() lineNum:" + lineNum + " genus:" + genusName + " speciesName:" + speciesName + " errorMessage:" + errorMessage);
			  }
			  if (!Utility.isBlank(subspeciesName)) {
				taxonItem.put("subspecies", subspeciesName);
				specimenItem.put("subspecies", subspeciesName);			
			  }
			}
			
            String country = (String) specimenItem.get("country");
            Country validCountry = null;

            if (country == null) {
                AntwebUtil.count("countryMissing");
                //A.log("parseLine() code:" + code + " countryMissingCount:" + AntwebUtil.getCount("countryMissing") + " lineNum:" + lineNum + " countryMissing:" + country);
				getMessageMgr().addToMessages(MessageMgr.countryMissing);
            String adm1 = (String) specimenItem.get("adm1");
				Geolocale inferCountry = GeolocaleMgr.inferCountry(adm1);
            A.log("parseLine() adm1:" + adm1 + " country:" + inferCountry);            
				if (inferCountry != null) country = inferCountry.getName();
            }
            if (country != null) {
				validCountry = GeolocaleMgr.getValidCountry(country);  // May change, for instance USA to United States.
				if (validCountry != null) {
				  country = validCountry.getName();  
				  String region = validCountry.getRegion();
				  String subregion = validCountry.getSubregion();
                  specimenItem.put("region", region);
                  specimenItem.put("subregion", subregion);			                        
				}
				boolean isValidCountry = GeolocaleMgr.isValidCountry(country);   // CountryDb.isValid(getConnection(), element);
				boolean notBlank = (new Utility()).notBlank(country);
				if (!isValidCountry && notBlank && !"Port of Entry".equals(country)) {
				  getMessageMgr().addToMessages(MessageMgr.notValidCountries, country);
				}
                specimenItem.put("country", country);
            }

/*
				// Island Country Part II of II.  If adm1ToIsland was set above as upgraded, then upgrade it and log it.
				if (adm1ToIsland != null && upgradeToIsland(adm1ToIsland)) {
				  //element = adm1ToIsland;
				  //adm1ToIsland = null;                              
				  Geolocale islandCountry = GeolocaleMgr.getCountry(adm1ToIsland);
				  String heading = "<b>Adm1 upgraded to Island Country</b> ";
				  String message = islandCountry.getBioregion() + " - " + islandCountry.getParent() + " - " + islandCountry.getName();
				  //getMessageMgr().addToMessageStrings(heading, message);   
				  getMessageMgr().addToMessages(MessageMgr.adm1UpgradeToIsland, message);   
				  s_log.warn("parseLine() adm1ToIsland" + adm1ToIsland + " upgraded to Island Country for code:" + code);
				}
*/

            String listedAdm1 = (String) specimenItem.get("adm1");
            String adm1 = listedAdm1;
            if (adm1 == null) {
              if (validCountry != null && validCountry.hasLiveValidAdm1()) {
                //A.log("parseLine() adm:" + adm1 + " validCountry:" + validCountry + " hasAdm1:" + validCountry.hasLiveValidAdm1());
                getMessageMgr().addToMessages(MessageMgr.adm1Missing, validCountry.getName(), (String) specimenItem.get("code"));              
              }
            } else {
				if (adm1 != null && adm1.contains("’")) {
				  adm1 = Formatter.replace(adm1, "‘", "'");
				  adm1 = Formatter.replace(adm1, "’", "'");
				  specimenItem.put("adm1", adm1);
				  //A.log("parseLine() swap:" + element + " for:" + listedAdm1);
				}				
			}
                                  
            if (adm1 != null && country != null) {
				Geolocale validAdm1 = GeolocaleMgr.getValidAdm1(adm1, country);  // May change, for instance MA to Massachussetts
				if (validAdm1 != null) {                  
				  adm1 = validAdm1.getName(); // This will be used for bioregion validation
				}

				boolean isValidAdm1 = GeolocaleMgr.isValid(adm1, country);
				boolean notBlank = (new Utility()).notBlank(adm1);
                boolean isIsland = GeolocaleMgr.isIsland(adm1);  // Hasn't been promoted yet...

                //A.log("buildLinesItems() thisAdm1:" + adm1 + " country:" + country + " validAdm1:" + validAdm1 + " isValidAdm1:" + isValidAdm1 + " notBlank:" + notBlank);

				if (!isValidAdm1 && notBlank && !isIsland) {
				  //String heading = "<b>Not valid Antweb Adm1</b> ";

				  // We will here lead to a tool that allows curators to select a mapping, if they don't want to change their data.
				  // It will show specimens that use this "bad" adm1 name.
				  // At least it will show what the valid adm1 names are for a given country.
				  String encodedAdm1 = java.net.URLEncoder.encode(adm1);
				  String encodedCountry = java.net.URLEncoder.encode(country);
				  String addAdm1Link = AntwebProps.getDomainApp() + "/adm1Mgr.do?adm1Name=" + encodedAdm1 + "&countryName=" + encodedCountry + "&groupId=" + accessGroup.getId();

                  //A.log("parseLine() addAdm1Link:" + addAdm1Link + " code:" + code);
				  String adm1LogMessage = "";
                  adm1LogMessage = "<a href='" + addAdm1Link + "'>" + adm1 + "</a>";
                  //A.log("adm1LogMessage:" + adm1LogMessage);

				  getMessageMgr().addToMessages(MessageMgr.notValidAdm1, adm1LogMessage);  

				  // A.log("parseLine() heading:" + heading + " listedAdm1:" + listedAdm1 + " country:" + country);

                  // Don't automatically add. Later we will delete the non-valid specimen adm1 that don't have valid_names.
                  // Then we can add individually when we find the valid_name to automate the mapping.
                  // For now, logging up the upload logs is enough.
                  //(new GeolocaleDb(getConnection())).addAdm1FromSpecimenData(listedAdm1, country, group.getId()); // These will be added as invalid. May then have a valid_name set by an admin.
				}
            }

			/*
			Derive bioregion from country if null during specimen upload
			  If country with only 1 bioregion, use it.
			  If an alternate, use the adm1 bioregion.
				If no adm1 present, or adm1 has no bioregion, don't specify.

            This code figures bioregions. Bit complicated.  A bioregion can be specified
            for a specimen, but it will be overridden if there is an adm1 set and it has
            a different bioregion. Otherwise the bioregion must be in either the country's
            bioregion or alt_bioregion.
            United States, China, India, Nepal, Bhutan and Indonesia have alternatives.
			*/

            String listedBioregion = (String) specimenItem.get("bioregion");
            if (listedBioregion == null) listedBioregion = (String) specimenItem.get("biogeographicregion");
			String useBioregion = GeolocaleMgr.getGeolocaleBioregion(country, adm1); // return country or adm1 as appropriate.
            if (useBioregion != null) {
              if (!useBioregion.equals(listedBioregion)) {
				  String bioregionStr = "";
				  if (listedBioregion != null && !useBioregion.equals(listedBioregion)) bioregionStr = " instead of " + listedBioregion;
				  String message = "Using " + useBioregion + bioregionStr + " for country:" + country;
				  if (adm1 != null && !"".equals(adm1)) message += " adm1:" + adm1;

				  String detail = "code:" + code + " country:" + country + " adm1:" + adm1 + " useBioregion:" + useBioregion + " listedBioregion:" + listedBioregion;
				  getMessageMgr().addToMessages(MessageMgr.correctedBioregion, message, detail);
	          }
            } else {
              if (BioregionMgr.isValid(listedBioregion)) {
                useBioregion = listedBioregion;
              } else {
                // listed bioregion is not valid. Report to the curator?
              }
            }
            if (useBioregion != null) {
				specimenItem.put("bioregion", useBioregion);
			}
			
            otherInfo.append("</features>");
            //A.log("parseLine() otherInfo:" + otherInfo);            
            String isInvalid = (new SpecimenXML()).isInvalid(otherInfo.toString());
            if (isInvalid != null) {
              //s_log.warn("importSpeciments() invalid is null.  otherInfo:" + otherInfo.toString());
              throw new AntwebException("<b>Invalid Column<font color=red>(Not uploaded)</font>:</b>" + "  " + isInvalid);
            } else {
              specimenItem.put("other", otherInfo.toString());
            } 
            

            // Before we are done with specimen...
            //   If an island, change country, adm1 and bioregion to reflect...
			if (listedAdm1 != null && GeolocaleMgr.isIsland(listedAdm1)) {                           
			  Geolocale islandCountry = GeolocaleMgr.getCountry(listedAdm1);
			  specimenItem.put("bioregion", islandCountry.getBioregion());
			  specimenItem.put("country", islandCountry.getName());
			  specimenItem.remove("adm1");
			  
			  //String heading = "<b>Adm1 upgraded to Island Country</b> ";
			  //String message = islandCountry.getName();
			  //getMessageMgr().addToMessages(MessageMgr.adm1UpgradeToIsland, message);   
			}

            //A.log("parseLine() 2 taxonItem:" + taxonItem);

            if (taxonItem.get("subspecies") != null) {
              taxonItem.put("rank", Rank.SUBSPECIES);
            } else {
              taxonItem.put("rank", Rank.SPECIES);
            }

            // Called casteNotes here because we derive the caste and subcaste from it.
            // In the upload file it is called caste or lifestagesex.
            String casteNotes = (String) specimenItem.get("caste"); 
            if (casteNotes != null) {
              specimenItem.put("life_stage", casteNotes);
            }
            String[] casteValues = Caste.getCasteValues(casteNotes); 
            String caste = casteValues[0];
            String subcaste = casteValues[1];
            if (casteNotes == null) {
              //getMessageMgr().addToMessages(MessageMgr.noCasteNotes); // lifeStage field
              getMessageMgr().flag("noCasteNotes"); 
            }
            if (casteNotes != null &&  caste == null) {
              getMessageMgr().addToMessages(MessageMgr.unrecognizedCaste, casteNotes); 
            }
            if (caste != null) specimenItem.put("caste", caste);
            if (subcaste != null) specimenItem.put("subcaste", subcaste);

            String taxonName = UploadUtil.makeName(specimenItem);
            if (taxonName == null) {
              if (code != null) {
                errorMessage = "makeTaxonNameError code:" + code;
                getMessageMgr().addToMessages(MessageMgr.makeTaxonNameError, code);

/*           // Would be good to load even though bad taxonomic info with a flag. But unintended consequences.
                if (AntwebProps.isDevMode()) {
                  specimenItem.put("flag", "red");
                  A.log("parseLine() flagged:" + code);
                } */

                  return errorMessage;

              } else {
                errorMessage = "code Not Found In Line line:" + lineNum + " displayLine:" + LineNumMgr.getDisplayLineNum(lineNum);
                getMessageMgr().addToMessages(MessageMgr.codeNotFoundInLine, "line:" + LineNumMgr.getDisplayLineNum(lineNum));
                return errorMessage;
              }
            } else {
              specimenItem.put("taxon_name", taxonName);
              taxonItem.put("taxon_name", taxonName);            
            }      
            
//if ("casent0187122".equals(code)) A.log("parseLine() 1");                  
            taxonItem.put("source", shortFileName);
            taxonItem.put("line_num", (new Integer(lineNum)).toString());
            taxonItem.put("access_group", new Integer(accessGroup.getId()));
            
            //if (!taxonItem.containsKey("fossil")) taxonItem.put("fossil", 0);

            specimenItem.put("line_num", (new Integer(lineNum)).toString());
            specimenItem.put("access_group", new Integer(accessGroup.getId()));
            specimenItem.put("access_login", new Integer(accessLogin.getId()));

            // put a subfamily in front of the TOC
            if ((taxonItem.containsKey("toc")) && (!"".equals((String) taxonItem.get("toc")))) {
                if ((taxonItem.containsKey("subfamily")) && (!"".equals((String) taxonItem.get("subfamily")))) {
                    taxonItem.put("toc", (String) taxonItem.get("subfamily") + (String) taxonItem.get("toc"));
                } else {
                    taxonItem.put("toc", null);
                }
            }
            
            if (AntwebProps.isDevMode()) {
              // A.log("parseLine() code:" + (String) specimenItem.get("code") + " withinCountry:" + isWithinCountryBounds(specimenItem) + " withingAdm1:" + isWithinAdm1Bounds(specimenItem));
            }
            
            String withinCountryBoundsMsg = isWithinCountryBounds(specimenItem);
			if (withinCountryBoundsMsg != null) {
				String displayCode = "<a href='" + AntwebProps.getDomainApp() + "/specimen.do?code=" + (String) specimenItem.get("code") + "'>" + (String) specimenItem.get("code") + "</a>";
				getMessageMgr().addToMessages(MessageMgr.latLonNotInCountryBounds, displayCode, withinCountryBoundsMsg);  	
                specimenItem.put("flag", "red");
			    specimenItem.put("issue", MessageMgr.latLonNotInCountryBounds);  							
			} else {
				//if (AntwebProps.isCAS(group) || AntwebProps.isDavis(group)) {
				  String withinAdm1BoundsMsg = isWithinAdm1Bounds(specimenItem);
				  if (withinAdm1BoundsMsg != null) {
					String displayCode = "<a href='" + AntwebProps.getDomainApp() + "/specimen.do?code=" + (String) specimenItem.get("code") + "'>" + (String) specimenItem.get("code") + "</a>";				
					getMessageMgr().addToMessages(MessageMgr.latLonNotInAdm1Bounds, displayCode, withinAdm1BoundsMsg);  				
                    specimenItem.put("flag", "red");
   			        specimenItem.put("issue", MessageMgr.latLonNotInAdm1Bounds);  							
				  }
				//}
            }
            
            setHigherTaxonomicHierarchy(specimenItem);
            setHigherTaxonomicHierarchy(taxonItem);
          
            if (accessLogin.isAdmin()) {
              // boolean isAllAscii = Utility.isAllASCII(speciesName);
              String isAscii = Utility.getASCII(speciesName);
              if ("false".equals(isAscii)) {
                // isAllAscii has been modified to return a corrected String if the space is a 160.    Use it now?

                // s_log.warn("parseLine() Non ASCII characters.  lineNum:" + lineNum + " for taxonName:" + taxonName + " species:" + speciesName + " isAllAscii:" + isAllAscii);
                //getUploadDetails().getNonAsciiTaxonNameSet().add(taxonName);
                //String heading = "<b>Non ASCII TaxonNames <font color=red>(not uploaded)</font></b>";
                //getMessageMgr().addToMessageSets(heading, taxonName);
                //getMessageMgr().addToMessages(MessageMgr.nonASCII, taxonName);
                //ok = false;
              }
            }

            //A.log("parseLine() logFileName:" + getUploadDetails().getLogFileName());

        } catch (RESyntaxException e) {
            s_log.error("parseLine() e:" + e);
            //AntwebUtil.errorStackTrace(e);
            errorMessage = "e:" + e;
        }

        Profiler.profile("parseLine", startTime);

        //if ("casent0187122".equals(code)) A.log("parseLine() code:" + code + " ok:" + ok);

        return errorMessage;  // end parseLine()
    }

	private static int s_countryOutOfBounds = 0;
	private static String s_withinBoundsCountry = null;
	private static int s_withinBoundsCountryCount = 0;
	private static int s_lastCountryCount = 0;

    private static HashMap<String, String> latLonHash = new HashMap<String, String>();

    private static Coordinate getCoordinate(Hashtable specimenItem) {
		Object latObj = specimenItem.get("decimal_latitude");
		Object lonObj = specimenItem.get("decimal_longitude");
		if (latObj == null || lonObj == null) {
          //s_log.warn("getCoordinate() no lat/lon for code:" + specimenItem.get("code"));	
          return null;	
		}
		if (
		     "-999.9".equals(latObj.toString()) || "-999.9".equals(lonObj.toString())
		  || "".equals(latObj.toString()) || "".equals(lonObj.toString())		
		   ) {
		   //A.log("getCoordinate() true. latObj or lonObj = -999.9 which means it wasn't entered at all.");
		   return null;
		}
        Float lat = (Float) latObj;
        Float lon = (Float) lonObj;
        Coordinate coord = new Coordinate(lon, lat);
        return coord;
    }


    private String isWithinCountryBounds(Hashtable specimenItem) {
        boolean isWithinBounds = true;
 
        Coordinate coord = getCoordinate(specimenItem);
        if (coord == null) return null;
        Float lat = coord.getLat();
        Float lon = coord.getLon();


		// If the localitycode is null but the decimal_longitude and decimal_latitude are not
		// then create a temporary localitycode. Store in a static hashtable so can match up.
		// This is useful for maps so they can link through to find the specimens.
		// Do similar for collectioncodes.
        String localityCode = (String) specimenItem.get("localitycode"); 
        //A.iLog("isWithinCountryBounds() localityCode:" + localityCode);
        if (localityCode == null) {  // Maybe we have one already by lat/lon.
          String latLon = "" + lat + "," + lon;
          localityCode = (String) latLonHash.get(latLon);
          if (localityCode == null) {

            localityCode = UploadUtil.cleanCode(localityCode);
            if (lat != 0 || lon != 0) latLonHash.put(latLon, localityCode);
          }
          //A.iLog("isWithinCountryBounds() code:" + specimenItem.get("code") + " localityCode:" + localityCode);
        }
        specimenItem.put("localitycode", localityCode);
        
        //if ("mem89751".equals(specimenItem.get("code"))) A.log("isWithinCountryBounds() lat:" + lat + " lon:" + lon + " localityCode:" + localityCode);
        
        String collectionCode = (String) specimenItem.get("collectioncode"); 
        collectionCode = UploadUtil.cleanCode(collectionCode);        
        specimenItem.put("collectioncode", collectionCode);

        

        String country = (String) specimenItem.get("country"); 
        //A.log("isWithinBounds() specimen country:" + country + " adm1:" + adm1 + " lat:" + lat + " lon:" + lon);

        if (country == null) return null;

        if (!country.equals(s_withinBoundsCountry)) {
          s_lastCountryCount = s_withinBoundsCountryCount;
          s_withinBoundsCountryCount = 1;
          s_withinBoundsCountry = country;
        } else {
          ++s_withinBoundsCountryCount;
        }
        //A.log("isWithinBounds() country:" + country + " s_country:" + s_withinBoundsCountry + " count:" + s_withinBoundsCountryCount);
        
        Geolocale geoCountry = GeolocaleMgr.getCountry(country);
        if (geoCountry != null) {
          // Test lat/lon

          isWithinBounds = geoCountry.isWithinBounds(lat, lon);
          //A.log("isWithinBounds:" + isWithinBounds);
                    
          // Currently Fiji breaks in Locality.isWithinBounds(). For now, return true;
          //if ("Fiji".equals(country) || "French Polynesia".equals(country)) return true;
          
          if (!isWithinBounds) {
            ++ s_adm1OutOfBounds;          
            //A.log("isWithinBounds() FALSE geoCountry:" + country + " coords:" + geoCountry.getCoords() + " boundingBox:" + geoCountry.getBoundingBox() + " lat:" + lat + " lon:" + lon);            
          }
        } {
          // geoCountry not found
        }

        if (!isWithinBounds) {
          //if (s_withinBoundsCountryCount == 1) A.log("isWithinBounds() code:" + specimenItem.get("code") + " country:" + country + " boundingBox:" + geoCountry.getBoundingBoxStr() + " lat:" + lat + " lon:" + lon + " countryOutOfBounds:" + s_countryOutOfBounds + " lastCountryCount:" + s_lastCountryCount);
          String logMsg = "code:" + specimenItem.get("code") + " country:" + country + " boundingBox:" + geoCountry.getBoundingBoxStr() + " lat:" + lat + " lon:" + lon;
          return logMsg;
  	  	  //LogMgr.appendLog("outOfBounds"  + group.getId() + ".txt", logMsg);
        }        
        
        return null; // Return a message in case of issue.
    }

	private static int s_adm1OutOfBounds = 0;
	private static String s_withinBoundsAdm1 = null;
	private static int s_withinBoundsAdm1Count = 0;
	private static int s_lastAdm1Count = 0;

    private String isWithinAdm1Bounds(Hashtable specimenItem) {
        boolean isWithinBounds = true;
  
        String adm1 = (String) specimenItem.get("adm1");    
        if (adm1 == null) return null;
        if ("Cauca".equals(adm1)) return null;
        if ("Galapagos".equals(adm1)) return null;
        
        String country = (String) specimenItem.get("country");    
        //A.log("isWithinBounds() code:" + specimenItem.get("code") + " country:" + country + " adm1:" + adm1 + " lat:" + lat + " lon:" + lon);
        
        Coordinate coord = getCoordinate(specimenItem);
        if (coord == null) return null;
        Float lat = coord.getLat();
        Float lon = coord.getLon();
        
      // Check the bounds
        if (!adm1.equals(s_withinBoundsAdm1)) {
          s_lastAdm1Count = s_withinBoundsAdm1Count;
          s_withinBoundsAdm1Count = 1;
          s_withinBoundsAdm1 = adm1;
        } else {
          ++s_withinBoundsAdm1Count;
        }
        //A.log("isWithinBounds() adm1:" + adm1 + " s_adm1:" + s_withinBoundsAdm1 + " count:" + s_withinBoundsAdm1Count);

        Geolocale geoAdm1 = GeolocaleMgr.getAdm1(adm1, country);
        if (geoAdm1 != null) {
          // Test lat/lon
          //if (!geoAdm1.getIsValid()) return true; // We only complain about valid adm1 out of bounds.

          isWithinBounds = geoAdm1.isWithinBounds(lat, lon);
          //A.log("isWithinBounds() geoAdm1:" + geoAdm1.getName() + " withinBounds:" + isWithinBounds);

          // Currently Fiji breaks in Locality.isWithinBounds(). For now, return true;
          //if ("Fiji".equals(country) || "French Polynesia".equals(country)) return true;

          if (!isWithinBounds) {
            ++ s_adm1OutOfBounds;
            //A.log("isWithinAdm1Bounds() FALSE geoAdm1:" + adm1 + " centroid:" + geoAdm1.getCentroid() + " boundingBox:" + geoAdm1.getBoundingBox() + " lat:" + lat + " lon:" + lon);            
          }
        } {
          // A.log("isWithinAdm1Bounds() adm1:" + adm1 + " not found.");
          // geoAdm1 not found
        }

        if (!isWithinBounds) {
          //if (s_withinBoundsAdm1Count == 1) A.log("isWithinBounds() code:" + specimenItem.get("code") + " adm1:" + adm1 + " boundingBox:" + geoAdm1.getBoundingBoxStr() + " lat:" + lat + " lon:" + lon + " adm1OutOfBounds:" + s_adm1OutOfBounds + " lastAdm1Count:" + s_lastAdm1Count);
          String logMsg = "code:" + specimenItem.get("code") + " adm1:" + adm1 + " boundingBox:" + geoAdm1.getBoundingBoxStr() + " lat:" + lat + " lon:" + lon;
  	  	  return logMsg;
  	  	  //LogMgr.appendLog("outOfBounds"  + group.getId() + ".txt", logMsg);
        }        
        
        return null;
    }
    
    protected ArrayList<String> getHeaderArrayList() {
    /* the important columns in specimen
       *Mark Note.  toc is not among the headers as far as I can tell.
       These are lowercased from the specimen headers.

       Header/db field anomalies.  (This list could be incomplete.)
         Biota:LocXYAccuracy          Spec:LatLonMaxError      DB:locxyaccuracy        UI:Lat. Lon. max error
         Biota:DateCollected(Start)   Spec:DateCollectedStart  DB:datecollectedstart   UI:Date collected
         Biota:DateCollected(End)     Spec:DateCollectedEnd    DB:datecollectedend     UI:Date collected end

       Specimen Header Array - This is the only place that should require addition for most cases 
         (not requiring data type transformation, and where the header name equals the database column 
         
          To be added: 
          Added: medium, locxyaccuracy, microhabitat, datedetermined, determinedby
            , "specimennotes", "dnaextractionnotes", "localitynotes", "elevationmaxerror" 
          Do not add (manually handled below): datescollected, loclatitude, loclongitude, elevation
          To be removed: 1st instance of duplicates.  toc.                
          , "elevation"  - elevation added by Mark on May 25, 2011         
       */

       if (m_headerArrayList != null) return m_headerArrayList;

       m_headerArrayList = new ArrayList();
       m_headerArrayList.add("specimencode");
       m_headerArrayList.add("subfamily");
       m_headerArrayList.add("tribe");
       m_headerArrayList.add("genus");
       m_headerArrayList.add("species");
       m_headerArrayList.add("[species]genus");
       m_headerArrayList.add("speciesgenus");
       m_headerArrayList.add("subgenus");
       m_headerArrayList.add("speciesgroup");
       m_headerArrayList.add("speciesname");
       m_headerArrayList.add("subspecies");
       m_headerArrayList.add("typestatus");
       m_headerArrayList.add("country");
       m_headerArrayList.add("biogeographic region");
       m_headerArrayList.add("biogeographicregion");
       m_headerArrayList.add("bioregion");
       m_headerArrayList.add("adm1");
       m_headerArrayList.add("adm2");
       m_headerArrayList.add("localityname");
       m_headerArrayList.add("localitycode");
       m_headerArrayList.add("collectioncode");
       m_headerArrayList.add("habitat");
       m_headerArrayList.add("method");
       m_headerArrayList.add("ownedby");
       m_headerArrayList.add("locatedat");
       m_headerArrayList.add("collectedby");
       m_headerArrayList.add("toc");
       m_headerArrayList.add("lifestagesex");
       m_headerArrayList.add("medium");
       //m_headerArrayList.add("collxyaccuracy");
       m_headerArrayList.add("locxyaccuracy");
       m_headerArrayList.add("latlonmaxerror");
       m_headerArrayList.add("microhabitat");
       m_headerArrayList.add("determinedby");
       m_headerArrayList.add("specimennotes");
       m_headerArrayList.add("dnaextractionnotes");
       m_headerArrayList.add("dnanotes");
       m_headerArrayList.add("localitynotes");
       m_headerArrayList.add("elevationmaxerror");
       m_headerArrayList.add("collectionnotes");
       m_headerArrayList.add("datecollectedstart");
       m_headerArrayList.add("datecollected(start)");
       m_headerArrayList.add("datecollectedend");
       m_headerArrayList.add("datecollected(end)");
       m_headerArrayList.add("family");
       m_headerArrayList.add("datedetermined");
       m_headerArrayList.add("kingdom");
       m_headerArrayList.add("phylum");
       m_headerArrayList.add("class");
       m_headerArrayList.add("order");           

   // if (!(group.getId() == 1))
       m_headerArrayList.add("collxyaccuracy");    
       
      return m_headerArrayList; 
    }

    protected Hashtable getColumnTranslations() {
        // *Thau Note:  Somewhat goofy way to match specimen field names to  
        // the database schema.  There's a better way to do this, but I don't have time now!
        // Mark Note.  If the column in the specimen file differs from the database field, map it.
        // Add it here AND above to the headerArrayList!

        Hashtable columnTranslations = new Hashtable();
        //     put ( specimen header, db column name )
        columnTranslations.put("speciesgenus", "genus");
        columnTranslations.put("specimencode", "code");
        columnTranslations.put("speciesname", "species");
        columnTranslations.put("[species]genus", "genus");
        columnTranslations.put("typestatus", "type_status");
        columnTranslations.put("biogeographicregion", "biogeographicregion");
        columnTranslations.put("lifestagesex", "caste");
        columnTranslations.put("datecollected(start)", "datecollectedstart");
        columnTranslations.put("datecollected(end)", "datecollectedend");
        //columnTranslations.put("datecollectedstart", "datecollectedstartstr");
        //columnTranslations.put("datecollectedend", "datecollectedendstr");        
        columnTranslations.put("locxyaccuracy", "latlonmaxerror");
        columnTranslations.put("kingdom", "kingdom_name");
        columnTranslations.put("phylum", "phylum_name");
        columnTranslations.put("class", "class_name");
        columnTranslations.put("order", "order_name");  
        columnTranslations.put("dnanotes", "dnaextractionnotes");   
     
        // Michelle (CAS) records some info in collxyaccuracy that is NOT simply used as a collectionnotes
        // field.  Jack (Utah) does.
        //if (!(group.getId() == 1))
        
        columnTranslations.put("collxyaccuracy", "collectionnotes");        
        
        return columnTranslations;
    }


    private String displayStringArray(String[] stringArray) {
      String string = "";
      for (int i = 0 ; i < stringArray.length ; ++i) {
        string += " i:" + i + " " + stringArray[i];
      }    
      return string;
    }
    

    private Date getParseDate(String thedate) {
      
      //A.log("getParseDate(" + thedate + ")");
      
      Date returnDate = null;

      thedate = thedate.trim();      
      if (thedate.equals("")) return null;

      // many possibilities here...      
      returnDate = getDate(thedate);
      if (returnDate != null) return returnDate;
 
      // Not a simply parsed date.  perhaps it is like: 8-11 Feb 2010   or like: 1 Feb - Mar 2010
      if (thedate.contains("-")) {
        String t = thedate.substring(thedate.indexOf("-") + 1);
        //s_log.info("getDateCollected() hypen removed from origDatesCollected:" + thedate + " making:" + t);
        returnDate = getDate(t);
        if (returnDate != null) return returnDate;      
        
        returnDate = getTruncatedDate(t);         
        if (returnDate != null) return returnDate;              
      }      

      // Maybe it is simply like: Mar 2011   or like: 2010
      returnDate = getTruncatedDate(thedate);         

      if (returnDate != null) return returnDate;            

      s_log.warn("getParseDate() Date not found for date:" + thedate);

      return null;
    }

    private Date getTruncatedDate(String truncDatesCollected) {
        // Perhaps it's like: may 2003
        Date returnDate = getDate("1 " + truncDatesCollected);
        if (returnDate != null) return returnDate;      

        // Perhaps it's like: 2003
        returnDate = getDate("1 Jan " + truncDatesCollected);
        if (returnDate != null) return returnDate;      

        return null;    
    }
    
    private Date getDate(String datesCollected) {
      try {
        Date d = new Date(datesCollected);
        if (AntwebProps.isDevMode()) s_log.info("getDateCollected() Found dateStr:" + datesCollected);
        return d;
      } catch (Exception e) {
        // These are expected to occur with our data.
        s_log.info("getDate() failed on datesCollected:" + datesCollected + " e:" + e);
      }
      return null;
    }

    private static int figuredElevation = 0;
    private static int greaterThanElevation = 0;
    private static int rangeElevation = 0;
    private static int decimalElevation = 0;
    private static int unfathomableElevation = 0;

    void elevationReport() {
      s_log.warn("FiguredElevations:" + figuredElevation
              + " greaterThanElevation:" + greaterThanElevation
              + " decimalElevation:" + decimalElevation
              + " rangeElevation:" + rangeElevation
              + " unfathomableElevation:" + unfathomableElevation);
    }
    
    private Integer getElevationFromString(String element, int lineNum) {
        if ((element == null) || (element.equals(""))) return null;
        Integer elevation = null;
        String elemStr = element.toLowerCase();
        try {
            //s_log.warn("getElevationFromString() 1 elevation:" + elemStr);
            int commaIndex = elemStr.indexOf(",");
            if (commaIndex > 0) {
                elemStr = elemStr.substring(0, commaIndex) + elemStr.substring(commaIndex + 1);
                //A.log("getElevationFromString() has , in elevation:" + element + " elemStr:" + elemStr + " commaIndex:" + commaIndex);            
            }            
            
            if (elemStr.indexOf("\"") == 0) {
                elemStr = elemStr.substring(1);
                //A.log("getElevationFromString()  1 has \" in elevation:" + element + " elemStr:" + elemStr);            
                if (elemStr.indexOf("\"") == elemStr.length() - 1) {
                    elemStr = elemStr.substring(0, elemStr.length() -1);
                    //A.log("getElevationFromString() 2 \" in elevation:" + element + " elemStr:" + elemStr);                            
                }
            }                        
            
            if (elemStr.indexOf("m") > 0) {
                elemStr = elemStr.substring(0, elemStr.indexOf("m"));

                if (figuredElevation < 100) {
                  //s_log.warn("getElevationFromString() has m elevation:" + element + " elemStr:" + elemStr);            
                }
            }
            if (elemStr.indexOf("ca ") >= 0) {
                elemStr = elemStr.substring(elemStr.indexOf("ca ") + 3);      
                //s_log.warn("getElevationFromString() has ca elevation:" + element + " elemStr:" + elemStr);
            }
            if (elemStr.indexOf("<") >= 0) {
                ++greaterThanElevation;
                elemStr = elemStr.substring(elemStr.indexOf("<") + 1);
                //s_log.warn("getElevationFromString() GreaterThan elevation:" + element + " elevation:" + elemStr);
            }
            if (elemStr.indexOf(">") >= 0) {
                ++greaterThanElevation;
                elemStr = elemStr.substring(elemStr.indexOf(">") + 1);
                //s_log.warn("getElevationFromString() GreaterThan elevation:" + element + " elevation:" + elemStr);
            }
            if (elemStr.indexOf("~") >= 0) {
                ++greaterThanElevation;
                elemStr = elemStr.substring(elemStr.indexOf("~") + 1);
                //s_log.warn("getElevationFromString() GreaterThan elevation:" + element + " elevation:" + elemStr);
            }        
            if (elemStr.indexOf("-") >= 1) {
                // We take the average of the two
                ++rangeElevation;
                String lowRange = elemStr.substring(0, elemStr.indexOf("-")).trim();
                elemStr = elemStr.substring(elemStr.indexOf("-") + 1).trim();
                double averageElev = ((new Integer(lowRange)).intValue() + (new Integer(elemStr)).intValue()) / 2;
                int averageElevInt = (new Double(averageElev)).intValue();
                elemStr = (new Integer(averageElevInt)).toString();
                //s_log.warn("getElevationFromString() RangeElevation elevation:" + element + " elevation:" + elemStr);
            }
            if (elemStr.indexOf("to") >= 1) {
                // We take the average of the two
                ++rangeElevation;
                String lowRange = elemStr.substring(0, elemStr.indexOf("to")).trim();
                elemStr = elemStr.substring(elemStr.indexOf("to") + 2).trim();
                double averageElev = ((new Integer(lowRange)).intValue() + (new Integer(elemStr)).intValue()) / 2;
                int averageElevInt = (new Double(averageElev)).intValue();
                elemStr = (new Integer(averageElevInt)).toString();
                //s_log.warn("getElevationFromString() RangeElevation elevation:" + element + " elevation:" + elemStr);
            }
            if (elemStr.indexOf("+") >= 0) {
                ++greaterThanElevation;
                elemStr = elemStr.substring(0, elemStr.indexOf("+"));
                //s_log.warn("getElevationFromString() GreaterThan elevation:" + element + " elevation:" + elemStr);
            }      
            if (elemStr.indexOf(".") > 0) {
                ++decimalElevation;
                elemStr = elemStr.substring(0, elemStr.indexOf("."));
                elemStr = (new java.math.BigDecimal(elemStr)).intValue() + "";
               //s_log.warn("getElevationFromString() decimal Elevation elevation:" + element + " elevation:" + elemStr);
            }        
            elemStr = elemStr.trim();

            elevation = (new Integer(elemStr));
            ++figuredElevation;
        } catch (Exception e) {
            ++unfathomableElevation;
            s_log.error("getElevationFromString() unfathomable elevation:" + element + " line:" + lineNum + " elevation:" + elemStr + " e:" + e);

            //String heading = "<b>Incorrect elevation format</b>";   
            //getMessageMgr().addToMessageStrings(heading, "Line:" + lineNum + " elevation:" + element);          
            getMessageMgr().addToMessages(MessageMgr.incorrectElevationFormat, "Line:" + lineNum + " elevation:" + element);          
        }
        return elevation;
    }
    

    public void handleWorldAntsSpeciesCheck(String taxonName, Status status, String family, String subfamily, String genus) {

      Date startTime = new Date();
          
       s_log.error("handleWorldAntsSpeciesCheck() Specimen Upload no longer uses handleWorlAntsSpeciesCheck(), except via addMissingGenus() or homononymMirroringTaxon.");
       //if (AntwebProps.isDevMode()) AntwebUtil.logStackTrace();
       //if (true) return;

       // This is overriding the same method in AntwebUpload.java
       if (Status.UNRECOGNIZED.equals(status.getValue())) {         
            // getUploadDetails().getPassWorldantsSpeciesCheckSet().add(taxonName);
         //String heading = "<b>Taxon names not found in Bolton World Catalog.  <font color=green>(uploaded)</font></b>";
         //if (AntwebProps.isDevMode()) heading += "SpecimenUpload.handleWorldantsSpeciesCheck()";
         String displayName = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + Taxon.displayTaxonName(taxonName) + "</a>";

         //getMessageMgr().addToMessageSets(heading, displayName);
         getMessageMgr().addToMessages(MessageMgr.taxonNameNotFoundInBolton, displayName);
         //return;  // We do want to upload these, and flag them.
       } else if (Status.usesCurrentValidName(status.getValue())) {
         s_log.error("handleWorldAntsSpeciesCheck() This shouldn't happen.  Didn't we swap out and go with the currentValidName?  taxonName:" + taxonName);
       } else {
         s_log.info("handleWorldAntsSpeciesCheck() status:" + status.getValue() + " not handled for taxon:" + taxonName);
       }
	  Profiler.profile("handleWorldAntsSpeciesCheck", startTime);       
    }
    
    
}
