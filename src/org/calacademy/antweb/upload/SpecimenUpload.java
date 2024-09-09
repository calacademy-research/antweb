package org.calacademy.antweb.upload;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.Date;
import java.sql.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.regexp.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/*
Inheritance hierarchy:

Antwebupload
  SpecimenUploadSupport
    SpecimenUploadProcess
      SpecimenUploadParse
        SpecimenUpload
*/

public class SpecimenUpload extends SpecimenUploadParse {
/* 
    Biota Data is uploaded through the curator interface.  It loads data from the "Biota Database"
    (a Windows client database that Michele Esposito uses).  The data is exported from Biota,
    and the data file uploaded.  Biota application is no longer under active development.
    
*/
    private static final Log s_log = LogFactory.getLog(SpecimenUpload.class);

    static int MAXLENGTH = 80;

    String currentDateFunction = "now()";  // for mysql 
    
    private final TreeSet illegitimateCountries = new TreeSet();
    private final TreeSet illegitimateBioregions = new TreeSet();

    SpecimenUpload(Connection connection) {
      super(connection);      
    }

    private String m_mode = "";
    
    private String getMode() {
      return m_mode;
    }
    private void setMode(String mode) {
      m_mode = mode;
    }

    SpecimenUpload(Connection connection, String mode) {
      super(connection);
      // Mode can be "full" or "incremental"
      setMode(mode);
      //setSpecimenUploadDb(new SpecimenUploadDb(getConnection()));       
    }
    
    void doPreliminaries(Group group) throws SQLException {
        // overridden by SpecimenUploadAugment
        new SpecimenUploadDb(getConnection()).dropSpecimens(group);
    }    
    
    public UploadDetails importSpecimens(UploadFile uploadFile, Login accessLogin, String action)
      throws SQLException, TestException, AntwebException
    {

        if (TaxonMgr.getSubfamilies().size() < 10) {
            throw new AntwebException("Taxon Mgr size discrepancy.");
        }

        getUploadDetails().setOperation(action);
        
        Group accessGroup = accessLogin.getGroup();
     
	    Date startTime = new Date();

        // generate list of important columns in specimen table
        getHeaderArrayList();

        // generate map of translations from file headers to database columns for specimen table
        getColumnTranslations();

        try {

            LineNumMgr.init(uploadFile, getMessageMgr(), getConnection());

            BufferedReader in = Files.newBufferedReader(Paths.get(uploadFile.getFileLoc()), uploadFile.getCharset());

            // parse the header 
            String theLine = in.readLine();         
            theLine = theLine.toLowerCase();

            doPreliminaries(accessGroup);
                                
            // Get the header values
            ArrayList<String> colList = getSpecimenColumns(theLine);
            ArrayList<String> otherColumns = getOtherColumns(theLine);

            //s_log.info("importSpecimens() colList:" + colList.toString());
            theLine = in.readLine();

            int lineNum = 1; //was 2;

            Hashtable<String, Object> specimenItem = new Hashtable<>();
            Hashtable<String, Object> taxonItem = new Hashtable<>();
            
            int buildLineTotal = 0;
            int processLineTotal = 0;
            
            try {    
				boolean processedSome = false;
				
                while (theLine != null) {     

                    LineNumMgr.setLineNum(lineNum);

                    Date startTimeLoop = new Date();    
                
                    if (theLine.trim().equals("")) {
                      //s_log.warn("importSpecimens() trim = emptyString");
                      theLine = in.readLine();
                      ++lineNum;
                      continue;
                    }                  
                  
                    if (theLine.contains("CASENT10156657")
                      || theLine.contains("JTL187227")) {
                      s_log.info("importSpecimens() lineNum:" + lineNum + " is " + theLine);
                    }

                    //Throwing a TestException will end execution upon discovery of a test condition.
                    //if (AntwebProps.isDevMode() && getSpecimenUploadDb().hasTaxon("dorylinaeaenictogiton")) {
                    if (AntwebProps.isDevMode() && false && lineNum > 3000) {
                      s_log.warn("importSpecimens() lineNum:" + lineNum);
                      throw new TestException(true);  // commit?
                    }
                                        
                    if ( true && lineNum % 10000 == 0) {  // was: AntwebProps.isDevOrStageMode()
                      if ("incremental".equals(getMode())) {
                         // getConnection().commit();  This doesn't help at all
                      }
                      //s_log.warn("importSpecimens() lineNum:" + lineNum
                      //  + " brt:" + getBadRankTaxonList() + " dbes:" + getUploadDetails().getDbErrorSet());
                        //" gsh:" + getUploadDetails().getPassGenusSubfamilyHash() + 
                        // " bsc:" + getUploadDetails().getPassWorldantsSpeciesCheckSet().size()
                    }

                    specimenItem.clear();
                    taxonItem.clear();

                    // Used for the taxon's source. Something like specimen1.txt
                    String uploadFileName = uploadFile.getFileNameBase(); //uploadFile.getBackupFileName();
                    //A.log("importSpecimens could use backupFileName:" + uploadFile.getFileNameBase() + " instead of shortFileName:" + shortFileName);

					if (LineNumMgr.isGoodCarriageReturnLine(lineNum)) {        
						// Do the work to populate the specimenItem, taxonItem
						String errorMessage = parseLine(theLine, lineNum, specimenItem, taxonItem, otherColumns, colList, uploadFileName, accessLogin);
						if (errorMessage == null) {
						  ++buildLineTotal;
						  //if (!ok) A.log("importSpecimens() lineNum:" + lineNum + " theLine:" + theLine);

						  // This slows down the upload 20 -> 40 min. Could do on command only?
						  //A.log("importSpeciens() backupFile:" + uploadFile.getBackupFileName());
						  // Really? Not changing anything and wondering... what?

						  specimenItem.put("backupFileName", uploadFile.getBackupFileName());

						  boolean retVal = processLine(specimenItem, taxonItem, lineNum, uploadFileName, accessGroup);
						  if (retVal) {
							++processLineTotal;
							processedSome = true;
						  } else { 
							//A.log("processLine retVal:" + retVal + " lineNum:" + lineNum);
						  }
						} else {
						  //A.log("buildLine errorMessage:" + errorMessage + " lineNum:" + lineNum + " theLine:" + theLine);
						}
					} else {		
					  //A.log("CARRIAGERETURN lineNum:" + lineNum + " displayLineNum:" + LineNumMgr.getDisplayLineNum(lineNum) + " line:" + theLine);
					}			  

					theLine = in.readLine();
					++lineNum;   
					if (lineNum % 5000 == 0) s_log.debug("importSpecimens() lineNum:" + lineNum + " buildLineTotal:" + buildLineTotal + " processLineTotal:" + processLineTotal);
				
					//Profiler.profile("importSpecimenLoop", startTimeLoop);                     
		
  	  	        } // end while.

				//A.log("importSpecimens() final lineNum:" + lineNum + " okTotal:" + okTotal); 
 
				// How to ascertain which museum to update?
				//(new MuseumDb.updateMuseum(museum, getConnection());
				getUploadDetails().setBuildLineTotal(buildLineTotal);
				getUploadDetails().setProcessLineTotal(processLineTotal);

                getUploadDetails().setRecordCount(lineNum);

                if (!processedSome) throw new AntwebException("No records processed");

                compileMessages(accessGroup);
                // getPassWorldantsSpeciesCheck added above
                // dbErrorSet added in AntwebUpload.saveSpecimen()

                String source = "specimen" + accessGroup.getId();
                boolean governed = true;
                if (AntwebProps.isDevMode()) governed = false;
                String message = new OrphansDb(getConnection()).deleteOrphanedSpeciesFromSource(source, governed); // Only if less than allowable size: governed.
                s_log.debug("deleteOrphanedSpeciesFromSource() returns:" + message);

                // This was below within the isDevMode test.
                if (message != null) {
                    getUploadDetails().setMessage(message);
                }
/*
                if (!AntwebProps.isDevMode()) {

                } else {
                    s_log.warn("importSpecimens() DEV MODE SKIPPING");
                }
*/
                // For debugging purposes. See log file.
                ProfileCounter.report();
                ProfileCounter.reset();

            } catch (AntwebException e) {
              s_log.warn("importSpecimens() e:" + e);
              getUploadDetails().getMessageMgr().addToMessages(MessageMgr.noRecordsProcessed, e.getMessage());
              //throw e;
            } catch (TestException e) {
              s_log.warn("TestException thrown");
              try {
                if (e.isCommit()) {
                  getConnection().commit();
                } else {
                  getConnection().rollback();
                }
              } catch (SQLException e2) {
                s_log.error("importSpecimens() rollback e:" + e2);
              }
            }

            s_log.info("importSpecimens() final lineNum:" + lineNum);
                
            in.close();
            // finally, you have to get rid of stuff in the Taxon table which is marked
            // as valid, but is not in any authority file, nor is in specimen.  This happens if
            // something is in specimen, and then removed

            s_log.info("importSpecimens() regenerateAllAntwebProject");
            // Date startTimex = new Date();

            if (AntwebProps.isDevMode()) { s_log.debug("DEV SKIPPING regenerateAllAntweb"); } else
            new ProjTaxonDb(getConnection()).regenerateAllAntweb();
            //Profiler.profile("regenerateAllAntweb", startTimex);

            TaxonMgr.populate(getConnection(), true, false);

            //getSpecimenUploadDb().deleteTaxonOrphans();
            //(new OrphansDb(getConnection())).deleteOrphanViloma();

            // s_log.info("importSpecimens() done removing orphans");
            new SpecimenUploadDb(getConnection()).updateSpecimenUploadDate(accessGroup);
                
        } catch (IOException e) {
            s_log.error("importSpecimens()  (File input error?)  e:" + e);
            AntwebUtil.errorStackTrace(e);
        }

        Profiler.profile("importSpecimens", startTime);

        return getUploadDetails();
    }

    private ArrayList<String> getArrayListEmptiesToo(String header) {
        /*
         * Replacement for:  // ArrayList<String> headers = new ArrayList<String>(Arrays.asList(header.split("\t")));
         *
         * The above would return one to few items which would monkeywrench the works.
         */
        ArrayList<String> headers = new ArrayList<>();
        int i = 0;
        int j;
        String head;
        while (header.indexOf("\t", i) > 0) {
            j = header.indexOf("\t", i);
            head = header.substring(i, j);
            //A.log("getArrayListEmptiesToo() head:" + head + " i:" + i + " j:" + j);
            i = j + 1;
            if ("".equals(head)) {
                head = " ";
                //s_log.warn("getArrayListEmptiesToo() head!:" + head);
            }
            headers.add(head);
        }

        head = header.substring(i);
        if ("".equals(head)) {
            head = " ";
        }
        //s_log.warn("getArrayListEmptiesToo() head:" + head + " i:" + i);
        headers.add(head);

        //s_log.warn("getArrayListEmptiesToo() size:" + headers.size() + " header:" + headers);
        return headers;
    }

    private ArrayList<String> getSpecimenColumns(String header) {
        
        // Somewhat goofy way to match biota field names to  
        // the database schema.  There's a better way to do this, but I don't have time now!
        Hashtable<String, String> columnTranslations = getColumnTranslations();
        
        String[] headers = header.split("\t", -1);  // -1 doesn't remove empty elements
        //A.log("getSpecimenColumns() header:" + header + " headers:" + headers);
        
        ArrayList<String> colList = new ArrayList<>();
                
        for (String theHead : headers) {
            theHead = theHead.trim();

            if (getHeaderArrayList().contains(theHead)) {
                if (columnTranslations.containsKey(theHead)) {
                    theHead = columnTranslations.get(theHead);
                }
                colList.add(theHead);
            } else {
                //s_log.warn("getSpecimenColumns() !!!!!! theHead not found:" + theHead);
                colList.add(null);
            }
        }

        //A.log("getSpecimenColumns() size:" + colList.size() + " colList:" + colList);
        //A.log("getSpecimenColumns(" + header + ") done");
        return colList;
    }
    
    private ArrayList<String> getOtherColumns(String header) {

        String[] headers = header.split("\t", -1);  // -1 doesn't remove empty list elements
        ArrayList<String> otherColumns = new ArrayList<>();
        
        //s_log.info("getOtherColumns(" + header + ")");
        
        RE multipleSpaces;
        RE badXML;
        RE tab;
        RE multipleQuotes;
        
        try {
            tab = new RE("\t");
            multipleSpaces = new RE(" +");
            multipleQuotes = new RE("\"{3}");
            badXML = new RE("['\"&/<>]");
            
            for (String theHead : headers) {
                theHead = theHead.trim();

                if (getHeaderArrayList().contains(theHead)) {
                    otherColumns.add(null);
                } else {
                    theHead = multipleSpaces.subst(theHead, "");
                    theHead = badXML.subst(theHead, "");

                    // s_log.warn("getOtherColumns() !!!!!! theHead not found:" + theHead);

                    otherColumns.add(theHead);
                }
            }    
        } catch (RESyntaxException e) {
            s_log.error("getOtherColumns(" + header + ") e:" + e);
        }
        //A.log("getOtherColumns() colList:" + otherColumns);
        return otherColumns;
    }

    private void compileMessages(Group group) throws SQLException {

        if (getBadRankTaxonList().size() > 0) {
            String badRankErrors = "";
            for (String badRank : getBadRankTaxonList()) {
              getMessageMgr().addToMessages(MessageMgr.badRankList, badRank);              
                //badRankErrors += "<br>&nbsp;&nbsp;&nbsp;" + error;
            }
            s_log.warn("compileMessages() UNREPORTED ? badRankTaxonList:" + badRankErrors);
            //String message = " <b>Parsing Errors (bad rank - <font color=red>skipped</font>)</b>" + badRankErrors;
            //getMessageMgr().getMessages().add(message);
            
        } else {
          //A.log("compileMessages() badRankTaxonList Check passed");
        }

        elevationReport();

        // Warn if Multiple Bioregions for non-introduced taxa.                                                     
        // New Query to be added to specimen upload report. Get the total and if > 0 display link to proper access_group.
        ArrayList<ArrayList<String>> multiBioregionList = new SpecimenDb(getConnection()).getMultiBioregionTaxaList(group.getId());
        if (multiBioregionList.size() > 1) { // The first record would be the header.
          // Create the link to the multiBioregionTaxa
            String message = "<a href='" + AntwebProps.getDomainApp() + "/list.do?action=multiBioregionTaxaList&groupId=" + group.getId() + "'>list</a>";
            getMessageMgr().addToMessages(MessageMgr.multipleBioregionsForNonIntroducedTaxa, message);         
        } 
        
        groupMorphoGenera(group);        
    }

    private void groupMorphoGenera(Group group) throws SQLException {

        String query = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "groupMorphoGenera()");

            query = "select distinct subfamily, genus from taxon where (taxarank = 'species' or taxarank = 'subspecies') "
              + " and (subfamily, genus) in ( select subfamily, genus from taxon where taxarank = 'genus' and status = 'morphotaxon') "
              + " and genus not like '(%' and status = 'morphotaxon' and access_group = " + group.getId();

             s_log.debug("groupMorphoGenera() query:" + query);

            rset = stmt.executeQuery(query);
            while (rset.next()) {
                //String code = (String) rset.getObject("code");
                String subfamily = (String) rset.getObject("subfamily");
                String genus = (String) rset.getObject("genus");

                String prettyName = Formatter.initCap(subfamily) + " " + Formatter.initCap(genus);
                String taxonName = subfamily + genus;
                String genusLink = "<a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + prettyName + "</a>";        
                //String specimenLink = "<a href='" + AntwebProps.getDomainApp() + "/specimen.do?code=" + code + "'>" + code + "</a>";        

                getMessageMgr().addToMessages(MessageMgr.groupMorphoGenera, genusLink); //, specimenLink);  
            }            
        } catch (SQLException e) {
            s_log.error("groupMorphoGenera() e:" + e + " query:" + query);
            throw e;
        } finally {
            DBUtil.close(stmt, "groupMorphoGenera()");
        }
    } 
        
    
}
