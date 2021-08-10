package org.calacademy.antweb.upload;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

import javax.servlet.http.*;

import org.apache.struts.action.*;
import org.apache.regexp.*;

import org.apache.struts.upload.FormFile;

import java.sql.*;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.curate.speciesList.*;

import org.apache.avalon.framework.logger.ConsoleLogger;
import org.apache.avalon.framework.logger.Logger;

/**
 * SpeciesListUpload is invoked by curate.do (UploadAction) to handle "project files".  For a given project -
 * generally a bioregion, but also Bolton (fossil:extinct.xls, worldants:extant.xls), and Encyclopedia of Life) - there
 * is a text file (or it could have an xls extension).  This will be uploaded through the 
 * UploadAction interface.  It will be uploaded from the client machine to the server.
 * If for instance Arizona is selected as the project and ariazonaants.txt as the file then... 
 *
 * First the file is copied to the server's working dir:
 * {$workingdir}/[project].txt
 *   where $workingdir is on production:/antweb/workingdir and on my dev box:/Users/mark/dev/calAcademy/workingdir
 * 
 * And then it is copied to the project's data directory... {$antweb.home}/[project]/[project]_project.txt
 *   where $antweb.home is on production:/data/antweb and on my dev box: /usr/local/tomcat/webapps/antweb
 *   Either way, pointed to by /usr/local/antweb
 *
 * So we might end up with these files, for instance:
 *   /home/antweb/workingdir/arizonaants.txt
 *   /data/antweb/arizona/arizonaants_project.txt
 *
 * or on my dev box:
 *   /Users/mark/dev/calAcademy/workingdir/arizonaants.txt
 *   /usr/local/tomcat/webapps/antweb/arizona/arizonaants_project.txt
 *
 */

/*
Proper results uploading arizona: (in dev Jan 13, 2011.  Mark.

Here are the latest and greatest statistics: 230686 specimens
14079 extant valid taxa
11483 specimens imaged
5025 species imaged 

After Worldants upload:
Here are the latest and greatest statistics: 230686 specimens
14070 extant valid taxa
11483 specimens imaged
5025 species imaged 
*/
 
 /*
    SpeciesListUpload gets called from UploadAction in the case of species list files and Bolton (which
is a sort of species list (with a description field which goes into description_edit).  
ImportSpeciesList() is called in either case (with an isWorldAnts boolean property) and 
generateBiogeographicRegions() in the case of Bolton.

      uploadSpeciesList()
        importSpeciesList()
          importProjectByValidity()
            saveProjectTaxon()
              saveTaxon()
            saveDescription()
         generateBiogeographicRegions()  - and then for all project
           createBioGeoProjectFile
           importProject()
           generateAndSaveHomePage()
*/

public class SpeciesListUpload extends AntwebUpload {
    private static Log s_log = LogFactory.getLog(SpeciesListUpload.class);

    ArrayList dateHeaders = new ArrayList(Arrays.asList(dateHeaderString));

    SpeciesListUploadDb m_speciesListUploadDb = null;
    private int totalTaxonCountryPrimaryKeyViolations = 0;
    private int totalFossils = 0; 
    private int totalNotFossils = 0;   
    
    public SpeciesListUpload(Connection connection) {
      super(connection, "worldants");

      m_speciesListUploadDb = new SpeciesListUploadDb(getConnection());      
    }
    
// WOULD BE GOOD TO REMOVE PROJECT AS IT IS ALWAYS WORLDANTS
// RENAME UPLOADSPECIESLIST TO uploadWorldants
    public UploadDetails uploadSpeciesList(String project, FormFile theFile, UploadFile uploadFile, int accessGroupId) {
    /* Called by UplaodAction in the case of an project file

       UploadFile in this case is the file being created on the server.  So if a bolton010112.txt file is uploaded
         then the uploadFile is pointing to  fileName:/data/antweb/worldants.txt encoding:UTF-8
     */
        UploadDetails uploadDetails = new UploadDetails("uploadWorldants");
        String message = null;
        
        //s_log.warn("uploadSpeciesList() projectFile:" + project + " theFile:" + theFile + " root:" + uploadFile.getRoot() + " uploadFile:" + uploadFile);
        Utility util = new Utility();
        
        //String outputFileDir = util.getInputFileHome();
        //String outputFileName = outputFileDir + project + ".txt";
        //String encoding = UploadFile.getEncoding(outputFileName, userAgent);
        A.log("uploadSpeciesList(4) project:" + project + " formFile:" + theFile + " to uploadFileLoc:" + uploadFile.getFileLoc());                        
        
        util.copyFile(theFile, uploadFile.getFileLoc()); // Copy to the working directory

        String backupDirFile = uploadFile.backup(); // We keep a timestamped copy even if it is bad...
        A.log("uploadSpeciesList:" + backupDirFile);

        if (!(theFile.toString().indexOf(".txt") > 0)) {
          message = "Species List must be a .txt file.";
        }
        if (!util.isTabDelimited(uploadFile.getFileLoc())) {
          message =  "Species List must be a tab-delimited file."; 
        }
        if (!isCurrentProjectFileFormat(uploadFile.getFileLoc())) {
       // we should really be reading in theFile before copying, so that if it is wrong format we don't overwrite the good one on the server.
          message =  "Species List must be in the most current format.";
        }
        if (message != null) {
          uploadDetails.setMessage(message);
          return uploadDetails;
        }

        s_log.warn("uploadSpeciesList() uploadFile.fileName:" + uploadFile.getFileLoc());

        uploadDetails = importSpeciesList(project, uploadFile, accessGroupId, true); 
        uploadDetails.setOperation("uploadWorldants");
        if (!uploadDetails.getMessage().equals("success")) {
          uploadDetails.setMessage(message);
          return uploadDetails;
        }
        
        copySpeciesListFile(project, uploadFile);

        boolean skip = false;
        if (AntwebProps.isDevMode()) skip = true;
        if (!skip) {
            try {
                (new ProjTaxonDb(getConnection())).regenerateAllAntweb();
            } catch (SQLException e) {
                s_log.error("uploadSpeciesList() unable to regenerateAllAntwebProject due to e:" + e); // XXX
            }
        } else {
            A.log("Warning ProjTaxonDb.regenerateAllAntweb() is skipped in Dev. See SpeciesListUpload.java:169");
        }

        s_log.warn("uploadSpeciesList() fossils:" + totalFossils);
        s_log.warn("uploadSpeciesList() NOT fossils:" + totalNotFossils);

        message = "success";
        
        uploadDetails.setMessage(message);
        return uploadDetails;
    }                  
    
    private UploadDetails importSpeciesList(String project, UploadFile uploadFile, int accessGroupId) {
        UploadDetails uploadDetails = null;
        String fileLoc = uploadFile.getFileLoc();
        String shortFileName = uploadFile.getShortFileName();
        String encoding = uploadFile.getEncoding();
        //boolean isBioRegion = uploadFile.getIsBioRegion();
        //s_log.warn("importSpeciesList() project:" + project + " shortFileName:" + shortFileName + " fileLoc:" + fileLoc);        
        try {
            uploadDetails = importSpeciesList(project, fileLoc, shortFileName, encoding, accessGroupId);
        } catch (Exception e) {
			DBUtil.rollback(getConnection());
            AntwebUtil.logStackTrace(e);
            s_log.error("importSpeciesList() 3 project:" + project + " e" + e);
        }
        return uploadDetails;
    }
        
    private UploadDetails importSpeciesList(String project, UploadFile uploadFile, int accessGroupId, boolean singleUpload) {
        // isBioGeoRegion = isWorldAnts on first call

        /* 
         * Anything that's in the headers list goes into the taxon table.  
         * Anything that's not, goes into the description table
         * except for country, which gets put into the taxon_country table
         * this is only really used for the world ants
         */
        UploadDetails uploadDetails = new UploadDetails("importWorldants");
        String returnStr = "failure";
        
        String fileName = uploadFile.getFileName();
        String fileLoc = uploadFile.getFileLoc();
        
        if (!uploadFile.exists()) {
          if ( (!fileName.contains("projectants")) && (!fileName.contains("globalants"))) {
            returnStr = "importSpeciesList(" + singleUpload + ") uploadFileLoc:" + fileLoc + " does not exist.";
            s_log.error(returnStr);
          }
          return new UploadDetails("importWorldants", returnStr);
        }
        
        s_log.warn("importSpeciesList(" + singleUpload + ") - importing " + project + " fileName:" + fileName + " fileLoc: " + fileLoc + " isBioGeoRegion:" + uploadFile.getIsBioRegion());   

  //LogMgr.logAntQuery(getConnection(), "projectTaxaCountByProjectRank", "Before worldants upload Proj_taxon worldants counts");
  //LogMgr.logAntBattery(getConnection(), "projectTaxonCounts", "before worldants upload Proj_taxon worldants counts");

        if (Project.WORLDANTS.equals(project)) { // always does
          new BioregionTaxonDb(getConnection()).deleteSource("antcat");
        }

        ArrayList preStats = null;
        try {
          if (singleUpload)
            preStats = ProjTaxonDb.getProjectStatistics(project, getConnection());

            A.log("importSpeciesList() project:" + project + " singleUpload:" + singleUpload + " preStats:" + preStats);

        } catch (SQLException e) {
            s_log.error("importSpeciesList(" + singleUpload + ") unable to setPreUploadStatistics e:" + e); // XXX was regen
        } 

		// Worldants is a special case.  Download the file and load it.
		if ("worldants".equals(project)) {
		    int origWorldantsCount = (new TaxonDb(getConnection())).getWorldantsCount();
		    String message = (new SpeciesListUploader(getConnection())).validateWorldantsFile(fileLoc, origWorldantsCount);
            if (!"success".equals(message)) return new UploadDetails(message); 
		}

        // first flag the records that will be updated.  We did delete.  Now we update the 
        // pending flag, and the delete the ones that remain pending after import, so as to 
        // not lose the valid or antcat properties from the bolton upload.
        getSpeciesListUploadDb().modifyProjectData(project); 

        uploadDetails = importSpeciesList(project, uploadFile, accessGroupId);

        uploadDetails.setPreUploadStatistics(preStats);

  // LogMgr.logAntQuery(getConnection(), "projectTaxaCountByProjectRank", "After worldants upload Proj_taxon worldants counts");
  //LogMgr.logAntBattery(getConnection(), "projectTaxonCounts", "after worldants upload Proj_taxon worldants counts");

        // getSpeciesListUploadDb().deletePendingData(); 

        TaxonSetDb.updateTaxonSetTaxonNames(getConnection());

        try {
          if (singleUpload)
            uploadDetails.setPostUploadStatistics(ProjTaxonDb.getProjectStatistics(project, getConnection()));
        } catch (SQLException e) {
            s_log.error("importSpeciesList() singleUpload:" + singleUpload + "  unable to setPostUploadStatistics" + e); // XXX was regen
        }

        if (uploadDetails.getMessage().equals("success")) {        
          getSpeciesListUploadDb().updateProjectUploadDate(project);
          s_log.info("importSpeciesList(" + singleUpload + ") - done generating home page");
        } else {
          s_log.error("importSpeciesList(" + singleUpload + ") No success at importing project:" + project + " fileLoc:" + fileLoc);
        }
        return uploadDetails;
    }

    // Can be called directly from UploadAction for speciesTest
    public UploadDetails importSpeciesList(String project, String fileName, String shortFileName
        , String encoding, int accessGroupId) throws IOException, SQLException {
          
        UploadDetails uploadDetails = new UploadDetails("failure");
        String returnStr = null;   
        //A.log("importSpeciesList() project:" + project + " fileName:" + fileName + " shortFileName:" + shortFileName + " encoding:" + encoding);          
        LogMgr.appendLog("speciesListLog.txt", DateUtil.getFormatDateTimeStr(new java.util.Date()) + " import:" + fileName);
               
        boolean isWorldants = Project.WORLDANTS.equals(project);

        ArrayList colList = new ArrayList();
        ArrayList descriptionList = new ArrayList();
        
        int i = 0;
        try {
            RE tab = new RE("\t");

            String[] components;

            BufferedReader in = new BufferedReader(
                new InputStreamReader(new FileInputStream(fileName), encoding));
                    
            if (in == null) {
                returnStr = "importSpeciesList() BufferedReader is null for file:" + fileName;
                s_log.error(returnStr);
                return new UploadDetails("importWorldants", returnStr);	
            }
            
            // parse the header 
            String theLine = in.readLine();
            if (theLine == null) {
                returnStr = "importSpeciesList() null line.  Perhaps empty file:" + fileName + "?";
                s_log.error(returnStr);
                return new UploadDetails("importWorldants", returnStr);	
            }
            theLine = theLine.toLowerCase();

            /* To handle an added column mimic what has been done with authorDate.  The header is "author date",
               the column is "author_date".  Search on author.  Poorly designed, but all mods are in order to
               upload, are in this method.
             */

            components = tab.split(theLine);
            //String theHead;
            ArrayList<String> headers = new ArrayList<>(Arrays.asList(components));
            int headCount = 0;
            int homonymCount = 0;
            
            int countryHeader = -1;

            int referenceIdHeader = -1;
            int bioregionHeader = -1;

            int subfamilyHeader = -1;
            int genusHeader = -1;
            int speciesHeader = -1;
            int subspeciesHeader = -1;
            int authorDateHeader = -1;
            int validHeader = -1;
            int statusHeader = -1;
            int currentValidRankHeader = -1;
            int antcatIdHeader = -1;
            int fossilHeader = -1;

            for (String theHead : headers) { 
                theHead = getGoodHeader(theHead);

                if ((!"subfamily".equals(theHead) && theHead.contains("subfamily"))) {
                  // We should not have a header that contains subfamily but is not subfamily.
                  s_log.warn("importSpeciesList() theHead like subfamily:" + theHead);
                }

                boolean isGoodTaxonHeader = goodTaxonHeaders.contains(theHead);
                
                //A.log("importSpeciesList() header:" + theHead + " isGoodTaxonHeader:" + isGoodTaxonHeader);

                if (isGoodTaxonHeader) {
                    colList.add(theHead);
                    descriptionList.add(null);
                    switch (theHead) {
                        case "subfamily":
                            subfamilyHeader = headCount;
                            break;
                        case "genus":
                            genusHeader = headCount;
                            break;
                        case "species":
                            speciesHeader = headCount;
                            break;
                        case "subspecies":
                            subspeciesHeader = headCount;
                            break;
                        case "author_date":
                            // A.log("importSpeciesList authorDate header found");
                            authorDateHeader = headCount;

                            break;
                        case "country":
                            countryHeader = headCount;
                            break;
                        case "bioregion":
                            bioregionHeader = headCount;
                            break;
                        case "reference_id":
                            referenceIdHeader = headCount;

                            break;
                        case "status":
                            statusHeader = headCount;
                            break;
                        case "current_valid_rank":
                            currentValidRankHeader = headCount;
                            break;
                        case "antcat_id":
                            antcatIdHeader = headCount;
                            break;
                        case "fossil":
                            fossilHeader = headCount;
                            break;
                    }
                } else {
                    descriptionList.add(theHead);
                    colList.add(null);
                    A.iLog(2, "importSpeciesList(5) descriptionList.size:" + descriptionList.size() + " colList:" + colList.size(), 300);
                }
                headCount++;
            }

            if (! (
                   (colList.contains("subfamily"))
                && (colList.contains("genus"))
                && (colList.contains("species"))
              )) {
                returnStr = "Species file required header not included for " + fileName;
                s_log.error(returnStr  + " colList:" + colList); 
                return new UploadDetails("importWorldants", returnStr);
            }

            String element;

            TaxonHash taxonHash = new TaxonHash();
            Hashtable description = new Hashtable();

            //s_log.warn("importSpeciesListByValidity() project:" + project);

            int lineNum = 1;
            while (theLine != null) {            
                theLine = in.readLine();
                ++lineNum;

                //if (AntwebProps.isDevMode() && lineNum < 16000) continue;

                if (theLine == null) continue;
                
                taxonHash.clear();      
                description.clear();

                String theLineBefore = theLine;
                theLine = AntFormatter.escapeQuotes(theLine);
                if (false
                    && (AntwebProps.isDevMode())
                    && !theLine.equals(theLineBefore)
                    && (lineNum > 28 && lineNum < 31)
                   ) {
                     A.log("importSpeciesList() lineNum:" + lineNum + " theLine:" + theLine); // + " theLineBefore:" + theLineBefore);
                }
                
                components = tab.split(theLine);
                ArrayList elements = new ArrayList(Arrays.asList(components));
                Iterator iter = elements.iterator();

				String taxonName = null;
				String status = null;
				String currentValidRank = null;
				int antcatId = 0;
			
                String thisSubfamily = "";
                String thisGenus = "";
                String thisSpecies = "";
                String thisSubspecies = "";
                String thisAuthorDate = null;
                
                String thisCountry = null;
                String thisBioregion = null;
                int referenceId = 0; 
                boolean isFossil = false;
                
                Hashtable temptaxonHash;
                int index = 0;

                String goodLineStatus = "true";

                RE multipleSpaces = new RE(" +");
                while ((iter.hasNext()) && (index < headers.size())) {
                    // This loop will populate the hashtable of elements (fields).
                    element = (String) iter.next();
                    element = multipleSpaces.subst(element.trim(), " ");                    
                    if (element == null)  s_log.error("importSpeciesList() 2 element is null");
                    //A.log("importSpeciesList() innerloop element:" + element);

                    String theElementBefore = element;
                    try {
                        element = Utility.customTrim(element, "\'");
                        element = Utility.customTrim(element, "\"");
                    } catch (AntwebException e) {
                        s_log.warn("importSpeciesList() e:" + e.getMessage());
                    }
                    if (!element.equals(theElementBefore)) {
                      //getMessageMgr().addToMapMessages(MessageMgr.badQuotations, getLineIdStr(lineNum, antcatId), theElementBefore);
                      //goodLineStatus = "Bad quotation marks";
                    }
                    
                    if (colList.get(index) != null) {

                        //A.log("importSpeciesList() ");
                        String header = headers.get(index);

                        if (!(
                            header.contains("author")
                          || header.contains("country")
                          || header.contains("bioregion")
                          ) ) {
                          element = element.toLowerCase();
                        } // else A.log("Not lowercasing " + headers.get(index) + " value:" + element);
                        
                        // Here we populate vars from data for use below...
                        if (index == statusHeader) status = element;
                        if (index == currentValidRankHeader) { 
                          currentValidRank = element;                    
                          //A.log("currentValidRank:" + currentValidRank);      
                        }
                        if (index == speciesHeader) {
                          thisSpecies = element.toLowerCase();
                          if ((thisSpecies != null) && !"".equals(thisSpecies)) {

                            // Subspecies can either be in their own column, or concatenated with the species (backwards compatible)
                            if (thisSpecies.contains(" ")) {
                                int spaceIndex = thisSpecies.indexOf(" ");
                                String speciesStr = thisSpecies.substring(0, spaceIndex);
                                thisSubspecies = thisSpecies.substring(spaceIndex + 1);
                                element = speciesStr;
                                thisSpecies = speciesStr;
                                // This may be overridden by a subspecies column
                                //A.log("buildLineItems() col:" + Rank.SUBSPECIES + " element:" + thisSubspecies);
                                taxonHash.put(Rank.SUBSPECIES, thisSubspecies);
                                
                                //A.log("importSpeciesList() species with space - spaceIndex:" + spaceIndex + " element:" + element + " species:" + thisSpecies + " subspecies:" + thisSubspecies);
                            }
                          }
                        }
                        if (index == subspeciesHeader) {
                          thisSubspecies = element.toLowerCase();
                        }
                        if (index == authorDateHeader) {
                          // element = UploadUtil.cleanHtml(element); // This line would take the <i>...</i> out.
                          //if (element != null && element.contains("<")) A.log("importSpeciesList HTML!!! authorDate:" + thisAuthorDate + " element:" + element);
                        }


                        if (index == countryHeader) {
                          thisCountry = element;
                          //A.log("importSpeciesList() index:" + index + " thisCountry:" + thisCountry);
                        }
                        if (index == bioregionHeader) {
                          thisBioregion = element;
                        }
                        if (index == fossilHeader) {
                          isFossil = "true".equals(element);
                        }
                        //if (index == fossilHeader) A.log("importSpeciesList isFossil:" + isFossil + " element:" + element + " fossilHeader:" + fossilHeader);
/**/
                        
                        try {
                          if (index == antcatIdHeader) antcatId = (Integer.valueOf(element)).intValue();
                        } catch (NumberFormatException e) {
                          s_log.warn("importSpeciesList() skipping line:" + lineNum + " column:" + index + " element:" + element + " e:" + e); 
                          //String heading = "Trouble parsing lines:";
                          String message = getLineIdStr(lineNum, antcatId); //"lineNum:" + lineNum;
                          //getMessageMgr().addToMessageStrings(heading, message);                           
                          getMessageMgr().addToMessages(MessageMgr.troubleParsingLines, message); 
                          goodLineStatus = "Number Format";

                          // readLine() above does not seem to handle blank lines.  They get skipped, count not incremented.                          
                        }

						String column = (String) colList.get(index);                        
						String elementSubfamily = Taxon.getSubfamilyFromName(element);
						try {                        
							if ("current_valid_name".equals(column) && elementSubfamily != null) {
  
							  int subfamilyLength = elementSubfamily.length();
							  //A.log("importSpeciesList() elementSubfamily:" + elementSubfamily + " element:" + element + " l:" + subfamilyLength);
							  if (elementSubfamily != null) {
								if (" ".equals(element.substring(subfamilyLength, subfamilyLength + 1))) {
								  String newElement = elementSubfamily + element.substring(subfamilyLength + 1);
								  //A.log("importSpeciesList() elementSubfamily:" + elementSubfamily + " newElement:" + newElement);
								  element = newElement;
								}
							  }
							  //A.log("importSpeciesList() column:" + column + " element:" + element);
							}
						} catch (StringIndexOutOfBoundsException e) {
						  A.log("importSpeciesList() column:" + column + " element:" + element + " elementSubfamily:" + elementSubfamily + " e:" + e);
						}

                        if (element != null && !"".equals(element)) {
                            String col = (String) colList.get(index);
                            
                            //if (col.contains("country")) A.log("importSpeciesList () col:" + col + " element:" + element);
                            
                            taxonHash.put(col, element);
                        }
                    } else if (descriptionList.get(index) != null) {
                        if (!(element.equals(""))) {
                            description.put(descriptionList.get(index), element);
                            //A.log("importSpeciesList() key:" + descriptionList.get(index) + " element:" + element);
                        }
                    }
                    index++;
                } // end while header columns
                
                if (!"true".equals(goodLineStatus)) {
                  s_log.warn("Not inserting line:" + lineNum + " " + goodLineStatus);
                  continue;    
                }
                    
                taxonHash.put("insert_method", "speciesListUpload");                                     
                taxonHash.put("source", project);
                taxonHash.put("access_group", accessGroupId);

                //A.log("importSpeciesList() worldants:" + isWorldants + " country:" + thisCountry + " bioregion:" + thisBioregion);
                // set the rank
                if (isWorldants) {
                    taxonHash.put("antcat", "1");                    
                    if (Rank.FAMILY.equals(currentValidRank)) {
                        taxonHash.put("taxon_name", "formicidae");
                        taxonHash.put("rank", Rank.FAMILY);                     
                        taxonHash.put("subfamily", "");   
                        (new ProjTaxonDb(getConnection())).addProjectFamily(project);              
                        //getSpeciesListUploadDb().specialFormicidaeHandling(project);             
                        //s_log.warn("importSpeciesListByValidity in formicidae +++++ taxonHash:" + taxonHash);                
                    } else if (Rank.SUBSPECIES.equals(currentValidRank)) {
                        // Antweb does not currently handle rank of subspecies.  So we munge it.
                        // In the future we are likely to want to not concatenate the two into one, but to store each.
                      
                        // This logic will go away.  World ants has no species for some subspecies 
                        if (thisSpecies == null || "".equals(thisSpecies)) {
                          thisSpecies = "(" + taxonHash.get("genus") + ")";
                          // A.log("insertSpeciesList() species empty for antcatId:" + antcatId + "  Using:" + thisSpecies); 
                        }                      
                        taxonHash.put("species", thisSpecies);
                        taxonHash.put("subspecies", thisSubspecies);                      

                        taxonHash.put("rank", Rank.SUBSPECIES);
                    } else {
                        if (currentValidRank != null) {
                          taxonHash.put("rank", currentValidRank);
                        } else {
                          A.log("Was NVE. null currentValidRank.");
                        } 
                    }                    
                } else {  // other species lists
                    if ((taxonHash.get(Rank.GENUS) == null) || (taxonHash.get(Rank.GENUS).equals(""))) {
                        taxonHash.put("rank", Rank.SUBFAMILY);                      
                    } else if ((taxonHash.get(Rank.SPECIES) == null) || (taxonHash.get(Rank.SPECIES).equals(""))) {
                        taxonHash.put("rank", Rank.GENUS);                      
                    } else if ((taxonHash.get(Rank.SUBSPECIES) == null) || (taxonHash.get(Rank.SUBSPECIES).equals(""))) {
                        taxonHash.put("rank", Rank.SPECIES);  
                    } else {
                        taxonHash.put("rank", Rank.SUBSPECIES);
                    }
                }

                // We wait until here to define it because the salient info could change above.                  
                taxonName = UploadUtil.makeName(taxonHash);   

                //A.log("importSpeciesList() taxonName:" + taxonName);
                if (taxonName == null) {
                    s_log.warn("importSpeciesList() taxonName:null lineNum:" + getLineIdStr(lineNum, antcatId));
                    getMessageMgr().addToMessages(MessageMgr.taxonNameNotFoundInAntcatUpload, getLineIdStr(lineNum, antcatId));
                } else {  
                    // For Worldants, only valid records of selected ranks will be inserted

                    taxonHash.put("taxon_name", taxonName);
                    taxonHash.put("line_num", lineNum);
                    // String debugTaxonName = "dorylinaecerapachys mayri brachynodus"; // "dolichoderinaecolobopsis macrocephala";
                    String debugTaxonName = "formicinaephasmomyrmex";
                    //String debugTaxonName = taxonName; // Debug all lines...
                    // A.log("importSpeciesList() lineNum:" + lineNum + " taxonName:" + taxonName);

                    String currentValidName = taxonName;
                    
                    boolean ignoreTaxon = false;
                    if (isWorldants) {
                        if (! (
                            (Rank.FAMILY.equals(currentValidRank))
                         || (Rank.SUBFAMILY.equals(currentValidRank))
                         // || (Rank.TRIBE.equals(currentValidRank))                        
                         || (Rank.GENUS.equals(currentValidRank))
                         // || (Rank.SUBGENUS.equals(currentValidRank))                        
                         || (Rank.SPECIES.equals(currentValidRank))
                         || (Rank.SUBSPECIES.equals(currentValidRank))                        
                          ) ) {
                            if (taxonName.contains(debugTaxonName)) A.log("importSpeciesList() 1 ignoreTaxon:true because of taxarank:" + currentValidRank);
                            ignoreTaxon = true;
                        }

                        if (Status.HOMONYM.equals(status)) {
                            ++homonymCount;
                            //A.log("importSpeciesList() inserting homonym:" + taxonName + " homonymCount:" + homonymCount);
                            
                            setHigherTaxonomicHierarchy(taxonHash);

                            saveHomonym(taxonHash);

                            description.put("taxon_name", taxonName);
                            if (thisAuthorDate != null) description.put("author_date", thisAuthorDate);
                            saveDescriptionHomonym(description);

                            ignoreTaxon = true;
                        }

                        // Completely exclude from import.
                        if (Status.excludeFromImport(status)) {
                            ignoreTaxon = true;
                        }

                        if (taxonName.contains(debugTaxonName)) A.log("importSpeciesList() 2 ignoreTaxon:" + ignoreTaxon + " status:" + status + " taxonName:" + taxonName + " currentValidName:" + taxonHash.get("current_valid_name") + " exclude:" + Status.excludeFromImport(status));

                        // if current valid name is the same as the taxon_name, ignore.
                        if (Status.usesCurrentValidName(status) && taxonName.equals(taxonHash.get("current_valid_name"))) {
                            if (taxonName.contains(debugTaxonName)) A.log("importSpeciesList() 3 skip because taxonName:" + taxonName + " = " + taxonHash.get("current_valid_name") );
                            ignoreTaxon = true;
                        }

                          String t = (String) taxonHash.get("current_valid_name");
                          if (t != null && !t.equals("") && !t.equals(taxonName)) {
                          
                            DummyTaxon currentValidTaxon = (new TaxonDb(getConnection())).getDummyTaxon(t);
                            if (currentValidTaxon != null) {
								currentValidName = t;                            
                            } else {
	                            if (taxonName.contains(debugTaxonName)) A.log("importSpeciesList() 4 CurrentValidName:" + t + " not found for taxonName:" + taxonName + " of status:" + status);
                                ignoreTaxon = true;
							}
                          }
                        
						/*
						// Are these automatically added in the post process when we generate bioregion data from geolocale data?
						if (thisBioregion != null) {
						  Bioregion bioregion = BioregionMgr.getBioregion(thisBioregion);                    
						  //if (bioregion != null) A.log("importSpeciesList()  SET SOURCE! taxonName:" + taxonName + " bioregion:" + bioregion);
			 			}
                        */
                    } else {
                        currentValidName = setStatusAndCurrentValidName(taxonName, taxonHash);
					    if (taxonName.contains(debugTaxonName)) A.log("importSpeciesList() 5 SET taxonName:" + taxonName + " currentValidName:" + currentValidName);
                    }

					if (taxonName.contains(debugTaxonName)) A.log("importSpeciesList() 6 SET ignoreTaxon:" + ignoreTaxon + " taxonName:" + taxonName + " country:" + thisCountry + " status:" + status + " currentValidName:" + currentValidName);

                    if (Rank.FAMILY.equals(currentValidRank)) {
                        taxonHash.log("importSpeciesList() FAMILY" + " project:" + project);
                    }

                    if (taxonName.contains(debugTaxonName)) A.log("importSpeciesList() 7 taxonName:" + taxonName + " currentValidRank:" + currentValidRank + " status:" + status + " thisCountry:" + thisCountry + " ignore:" + ignoreTaxon + " currentValidName:" + currentValidName);

                    if (!ignoreTaxon) {
                        //A.log("importSpeciesList() currentValidRank:" + currentValidRank + " status:" + status);

                        int c = saveTaxonAndProjTaxon(taxonHash, project);

                        if (taxonName.contains(debugTaxonName)) A.log("importSpeciesList() 8 c:" + c + " taxonName:" + taxonName + " currentValidRank:" + currentValidRank + " status:" + status + " thisCountry:" + thisCountry + " ignore:" + ignoreTaxon + " currentValidName:" + currentValidName);

                        if (c > 0) {
                          description.put("taxon_name", taxonName); // We do NOT use currentValidName here, so as not to overwrite.
                          description.put("proj_name", project);  // This is not used.  ?
                          saveDescriptionEdit(description);

						  // Use the worldants country to populate geolocale_taxon... 
						  if (thisCountry != null && !"".equals(thisCountry) && !isFossil) {						
							String tempCountry = thisCountry;
							// We could try to get the adm1 from the parenthesis..
							if (tempCountry.contains("(")) tempCountry = tempCountry.substring(0, tempCountry.indexOf("("));
							tempCountry = tempCountry.trim();
							Country country = GeolocaleMgr.getValidCountry(tempCountry);
                            if (taxonName.contains(debugTaxonName)) A.log("importSpeciesList() 9 taxonName:" + taxonName + " status:" + status + " country:" + country);
							if (country != null) {
							  //if (taxonName.contains(debugTaxonName))
							  if (taxonName.contains(debugTaxonName)) A.log("importSpeciesList() 10 taxonName:" + taxonName + " thisCountry:" + thisCountry + " country:" + country + " currentValidName:" + currentValidName);
							  //if (taxonName.contains("achycondyla") && country.getName() != null) && country.getName().contains("adagascar")) A.log("importSpeciesList() madagascar taxonName:" + taxonName + "   pachycondyla currentValidName:" + currentValidName);

                              //if (taxonName.contains("dorylinaecerapachys") || currentValidName.contains("dorylinaecerapachys")) A.log("importSpeciesList() taxonName:" + taxonName + " currentValidName:" + currentValidName + " status:" + status + " ignoreTaxon:" + ignoreTaxon);

                              Taxon current = TaxonMgr.getTaxon(currentValidName);
                              if (current == null) {
                                  current = (new TaxonDb(getConnection())).getTaxon(currentValidName);

                                  if (taxonName.contains(debugTaxonName)) A.log("importSpeciesList() 11 currentValidName:" + currentValidName + " not found in TaxonMgr. Found in DB:" + current);
                                  // If we load a small worldants and then try to load a big one, taxa will not be in the taxonMgr.
                                  // If this happens a lot, good to know. Hopefully fast enough. Remove the log message.
                              }
                              if (current != null) {
                                  if (current.isValid()) {
                                      (new GeolocaleTaxonDb(getConnection())).setTaxonSet(country, currentValidName, Source.ANTCAT);
                                  }
                              }
							} else {
							  getMessageMgr().addToMessages(MessageMgr.countryNotFound, tempCountry); 
							  //A.log("country not found:" + thisCountry + " tempCountry:" + tempCountry);
							}
						  }

                          if (taxonName.contains(debugTaxonName)) A.log("importSpeciesList() 12 taxonName:" + taxonName + " bioregion:" + thisBioregion + " fossil:" + isFossil);

						  // Use the worldants bioregion to populate bioregion_taxon... 						
						  if (thisBioregion != null && !"".equals(thisBioregion) && !isFossil) {
                            Bioregion bioregion = BioregionMgr.getBioregion(thisBioregion);           
						    if (bioregion != null) {
						      boolean inserted = (new BioregionTaxonDb(getConnection())).insertTaxon(bioregion.getName(), currentValidName, "importSpeciesList", Source.ANTCAT);
                              if (inserted) {
                                i = i + 1;
                                //if ("dorylinaedorylus erraticus".equals(currentValidName)) A.log("importSpeciesList() inserted:" + inserted + " i:" + i + " bioregion:" + bioregion + " currentValidName:" + currentValidName);
						      }
						    }
						  }

						  // IF FOSSIL, ADD TO THE FOSSIL LIST.
						  if (isFossil) {
							  ++fossilCount;
							  //A.log("importSpeciesList() fossilCount:" + fossilCount + " Add to fossil list taxonName:" + taxonName);
							  Project fossilants = ProjectMgr.getProject("fossilants"); 
							  (new ProjTaxonDb(getConnection())).insert(fossilants, currentValidName, Source.ANTCAT); 
						  }                   
                        } else {
							if (taxonName.contains(debugTaxonName)) A.log("importSpeciesList() 13 Not inserted taxonName:" + taxonName + " c:" + c);
                        }
                        
                        if (taxonName.equals("formicidae") && (!"worldants".equals(project))) {
                          s_log.warn("importSpeciesList() non-worldants insertion of formicidae.  lineNum:" + lineNum + " source:" + shortFileName);
                        }  

                    } else {
                        //A.log("importSpeciesList() Not uploading " + taxonName + " status:" + status + " currentValidRank:" + currentValidRank);
                    }

                }                              
            } // end while loop through lines
            in.close();

            returnStr = "success";
        } catch (java.io.FileNotFoundException e) {
            returnStr = "importSpeciesList() c1 project:" + project + " e" + e;
            s_log.error(returnStr);             
        } catch (RESyntaxException e) {
            returnStr = "importSpeciesList() c2 project:" + project + " fileName:" + fileName + " e" + e;
            s_log.error(returnStr);
        } catch (java.util.MissingResourceException e) {
            returnStr = "importSpeciesList() c3 project:" + project + " fileName:" + fileName + " e" + e;
            s_log.error(returnStr);
        } catch (IndexOutOfBoundsException e) {
            returnStr = "importSpeciesList() project:" + project + " e" + e;
            AntwebUtil.logStackTrace(e);
            s_log.error(returnStr);
        }

        if (AntwebProps.isDevMode()) {
            // debugging here:
            int reportTaxonCountryPrimaryKeyViolations = SpeciesListUploadDb.reportTaxonCountryPrimaryKeyViolations();
            totalTaxonCountryPrimaryKeyViolations += reportTaxonCountryPrimaryKeyViolations;
            if (reportTaxonCountryPrimaryKeyViolations > 0) {
              s_log.warn("importSpeciesList() project:" + project 
                + " taxonCountryPrimaryKeyViolations:" + reportTaxonCountryPrimaryKeyViolations        
                + " totalTaxonCountryPrimaryKeyViolations:" + totalTaxonCountryPrimaryKeyViolations);
            }
        }      
        return new UploadDetails("importWorldants", returnStr);
    }
    
    private static int fossilCount = 0;

    public UploadDetails reloadSpeciesList(String project, int accessGroupId) {
      // Called only from UploadAction. This is half of the fetch and reload process.
      // This is only ever done for Worldants anymore.

		UploadDetails uploadDetails = null;

        if (!Project.WORLDANTS.equals(project)) s_log.error("reloadSpeciesList() Investigate... not only done for worldants? project:" + project);

        String root = null;
        String query = "";
		Statement stmt = null;
		ResultSet rset = null;
        try {
            query = "select root from project where project_name = \'" + project + "\'";
            stmt = DBUtil.getStatement(getConnection(), "SpeciesListUpload.reloadSpeciesList()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
              root = rset.getString("root");
            }

			uploadDetails = reloadSpeciesList(root, project, accessGroupId, true);
        } catch (SQLException e) {
          s_log.error("reloadSpeciesList(" + project + ") e:" + e);
        } finally {
          DBUtil.close(stmt, rset, "SpeciesListUpload.reloadSpeciesList()");
        }

        return uploadDetails;
    }
        
    public UploadDetails reloadSpeciesList(String root, String project, int accessGroupId, boolean singleUpload) {
      /* Generally the file is fetched from the project directory.  In dev, they can be
         fetched from the working dir (to enable rapid update with server.
         On dev machine: cd workingdir/
         then: scp mjohnson@antweb.org:/home/mjohnson/links/workingdir/*ants.txt .
         then: run the "Reload all Project Files" option
      */
        String baseDir = AntwebProps.getDocRoot() + Project.getSpeciesListDir(); // + root + "/";
        //A.log("reloadSpeciesList(4) baseDir:" + baseDir + " root:" + root + " project:" + project);
        return reloadSpeciesList(baseDir, root, project, UploadFile.getSpeciesListTail(), accessGroupId, singleUpload);
    }

    public UploadDetails reloadSpeciesList(String baseDir, String root, String project, String tail, int accessGroupId, boolean singleUpload) {
        // Can be called directly below in dev mode, for rapid reload of all projects from workingdir
        String fileName = project + tail;

        fileName = root + "/" + fileName;  // For reloads, the root, is the projectName

        String fileLoc = baseDir + fileName;
        UploadFile uploadFile = null;
        try { 
          uploadFile = new UploadFile(baseDir, fileName, "", null);
        } catch (Exception e) {
          s_log.error("reloadSpeciesList(6) Upload File not created for baseDir:" + baseDir + " fileName:" + fileName);
          s_log.warn("reloadSpeciesList(6) e:" + e);
        }        
        
        uploadFile.setRoot(AntwebProps.getDocRoot()); 
        uploadFile.setIsReload(true);   

        //A.log("SpeciesListUpload.reloadSpeciesList(6) fileLoc:" + fileLoc);

		if ("worldants".equals(project)) {
		    int origWorldantsCount = (new TaxonDb(getConnection())).getWorldantsCount();
		    String message = (new SpeciesListUploader(getConnection())).validateWorldantsFile(fileLoc, origWorldantsCount);
		    //A.log("SpeciesListUpload.reloadSpeciesList(6) message:" + message);
            if (!"success".equals(message)) {
              return (new UploadDetails(message));
            }
		}        

        if (!uploadFile.exists()) {
          s_log.warn("reloadSpeciesList(6) does NOT exist uploadFile:" + uploadFile.getFileLoc());
          return new UploadDetails("File Not Found:" + uploadFile.getFileLoc(), "worldantsReload"); 
        } else {
          // The web/workingdir always has one copy of the latest... in theory.
          String webWorkingDir = uploadFile.getRoot() + "web/workingdir/";
          String webWorkingDirCopy = webWorkingDir + project + UploadFile.getSpeciesListTail();

          //A.log("reloadSpeciesList() DOES exist uploadFile:" + uploadFile.getFileLoc() + " webWorkidngDirCopy:" + webWorkingDirCopy);
          try {
            Utility.makeDirTree(webWorkingDir);          
            //A.log("reloadSpeciesList(6) fileLoc:" + uploadFile.getFileLoc() + " workingDir:"+ webWorkingDirCopy);
            (new Utility()).copyFile(uploadFile.getFileLoc(), webWorkingDirCopy);          
          } catch (IOException e) {
            s_log.error("reloadSpeciesListFile(6) copyFile e:" + e);
          }    
        }
        
        UploadDetails uploadDetails = importSpeciesList(project, uploadFile, accessGroupId, singleUpload);
        uploadDetails.setOperation("worldantsReload");

        uploadDetails.setMessage("success");
        
        return uploadDetails;
    }

    private String getGoodHeader(String head) {
      String goodHead = null;
      try {
        RE multipleSpaces = new RE(" +");
        if (goodTaxonHeaders.contains(head)) goodHead = head;
        String spaceLessHead = multipleSpaces.subst(head, "");
        if (goodTaxonHeaders.contains(spaceLessHead)) goodHead = spaceLessHead;
        String underScoreHead = multipleSpaces.subst(head, "_");
        if (goodTaxonHeaders.contains(underScoreHead)) goodHead = underScoreHead;

        //A.log("getGoodHeader() head:" + head + " goodHead:" + goodHead);

        if ("taxonomic history html".equals(head) || "taxonomic history".equals(head)) goodHead = "taxonomichistory";

        if (goodHead == null) goodHead = head;
        goodHead = goodHead.trim();
      } catch (RESyntaxException e) {
        s_log.error("getGoodHeader() e:" + e);
        return head;
      }
      return goodHead;
    }

    private String getLineIdStr(int lineNum, int antcatId) {
      if (antcatId == 0) {
        return "lineNum:" + lineNum;
      } else {
        return "antcatId:" + antcatId;
      }
    } 
    
    private void copySpeciesListFile(String project, UploadFile uploadFile) {    
        Utility util = new Utility();
        try {
            // copy project file to home directory

            String projectRoot = ProjectMgr.getProject(project).getRoot();

            String speciesListFile = uploadFile.getRoot() + Project.getSpeciesListDir() + projectRoot +  "/" + project + UploadFile.getSpeciesListTail();
            String message = "   copy:" + uploadFile.getFileLoc() + " to:" + speciesListFile;
            s_log.warn("copySpeciesListFile()" + message);
            util.copyFile(uploadFile.getFileLoc(), speciesListFile);

            LogMgr.appendLog("speciesListLog.txt", DateUtil.getFormatDateTimeStr(new java.util.Date()) + message);

            // The web/workingdir always has one copy of the latest... in theory.  Similar logic in reloadSpeciesList().
            String webWorkingDir = uploadFile.getRoot() + "web/workingdir/";
            util.makeDirTree(webWorkingDir); // Didn't work on dev
            String webWorkingDirCopy = webWorkingDir + project + UploadFile.getSpeciesListTail();
            s_log.warn("copySpeciesListFile() webWorkingDirCopy:" + webWorkingDirCopy);        
            util.copyFile(uploadFile.getFileLoc(), webWorkingDirCopy);

          } catch (IOException e) {
            s_log.error("copySpeciesListFile() e:" + e);
        }
    }

    private String makeNameUpToSpecies(Hashtable taxonHash) {

        StringBuffer sb = new StringBuffer();

        if (validNameKey("subfamily", taxonHash)) {
            sb.append((String) taxonHash.get("subfamily"));
        }

        if (validNameKey("genus", taxonHash)) {
            sb.append((String) taxonHash.get("genus"));
        }

        //        if (validNameKey("subgenus", taxonHash)) {
        //            sb.append(" (" + (String) taxonHash.get("subgenus") + ")");
        //        } else if (validNameKey("speciesgroup", taxonHash)) {
        //            sb.append(" (" + (String) taxonHash.get("speciesgroup") + ")");
        //        }

        return sb.toString();
    }

    private boolean validNameKey(String key, Hashtable taxonHash) {
        boolean valid = false;
        if ((taxonHash.containsKey(key))
                && (!((String) taxonHash.get(key)).equals("null"))
                && (!((String) taxonHash.get(key)).equals(""))) {
            valid = true;
        }
        return valid;
    }    
    
    public SpeciesListUploadDb getSpeciesListUploadDb() {
      return m_speciesListUploadDb;
    }
    

    private boolean isCurrentProjectFileFormat(String fileName) {
        boolean isCurrentFormat = true;
        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            if (in == null) {
              s_log.error("isCurrentProjectFileFormat() BufferedReader is null for file:" + fileName);
              return false;
            }
            
            String theLine = in.readLine();
            if (theLine == null) {
              s_log.error("isCurrentProjectFileFormat() null line.  Perhaps empty file:" + fileName + "?");
              return false;
            }

            s_log.warn("isCurrentProjectFileFormat() testLine:" + theLine);

            if ((!theLine.contains("Subfamily")) && (!theLine.contains("subfamily"))) {
              s_log.error("isCurrentProjectFileFormat() " + fileName + " does not contain Subfamily");
              return false;
            }

        } catch (Exception e) {
            s_log.error("isCurrentProjectFileFormat() fileName:" + fileName + " e:" + e);
            return false;
        }
        return isCurrentFormat;
    }
    
}
