package org.calacademy.antweb.upload;

import java.io.*;
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
    private static Log s_log = LogFactory.getLog(SpecimenUpload.class);

    static int MAXLENGTH = 80;

    String currentDateFunction = "now()";  // for mysql 
    
    private TreeSet illegitimateCountries = new TreeSet();
    private TreeSet illegitimateBioregions = new TreeSet();

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
        (new SpecimenUploadDb(getConnection())).dropSpecimens(group);    
    }    
    
    public UploadDetails importSpecimens(UploadFile uploadFile, Login accessLogin) 
      throws SQLException, TestException, AntwebException
    {      
        //UploadDetails uploadDetails = null;
        
        Group accessGroup = accessLogin.getGroup();
     
	    Date startTime = new Date();

        ArrayList<String> headerArrayList = getHeaderArrayList();       
        String[] headerArray = headerArrayList.toArray(new String[headerArrayList.size()]);
        ArrayList goodSpecimenHeaders = new ArrayList(Arrays.asList(headerArray));  // Could we just use the headerArrayList here?
        Hashtable columnTranslation = getColumnTranslations();
    
        try {

            LineNumMgr.init(uploadFile, getMessageMgr(), getConnection());
        
            BufferedReader in = new BufferedReader(
                new InputStreamReader(new FileInputStream(uploadFile.getFileLoc()), uploadFile.getEncoding()));

            // parse the header 
            String theLine = in.readLine();         
            theLine = theLine.toLowerCase();

            doPreliminaries(accessGroup);
                                
            // Get the header values
            ArrayList colList = getSpecimenColumns(theLine);
            ArrayList otherColumns = getOtherColumns(theLine);

            //s_log.info("importSpecimens() colList:" + colList.toString());
            theLine = in.readLine();

            int lineNum = 1; //was 2;
            int batchCount = 0;
                
            String oldLine = null;
            Hashtable specimenItem = null;
            Hashtable taxonItem = null;
            
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
                    oldLine = new String(theLine);                    
                    
                    //Throwing a TestException will end execution upon discovery of a test condition.
                    //if (AntwebProps.isDevMode() && getSpecimenUploadDb().hasTaxon("dorylinaeaenictogiton")) {
                    if (AntwebProps.isDevMode() && false && lineNum > 3000) {
                      s_log.warn("importSpecimens() lineNum:" + lineNum);
                      throw new TestException(true);  // commit?
                    }
                                        
                    if ( (true) && ((lineNum % 10000 ) == 0)) {  // was: AntwebProps.isDevOrStageMode()
                      if ("incremental".equals(getMode())) {
                         // getConnection().commit();  This doesn't help at all
                      }
                      //s_log.warn("importSpecimens() lineNum:" + lineNum
                      //  + " brt:" + getBadRankTaxonList() + " dbes:" + getUploadDetails().getDbErrorSet());
                        //" gsh:" + getUploadDetails().getPassGenusSubfamilyHash() + 
                        // " bsc:" + getUploadDetails().getPassWorldantsSpeciesCheckSet().size()
                    }

                    specimenItem = new Hashtable();
                    taxonItem = new Hashtable();

                    String shortFileName = uploadFile.getShortFileName();

					if (LineNumMgr.isGoodCarriageReturnLine(lineNum)) {        
						// Do the work to populate the specimenItem, taxonItem
						String errorMessage = parseLine(theLine, lineNum, specimenItem, taxonItem, otherColumns, colList, shortFileName, accessLogin);
						if (errorMessage == null) {
						  ++buildLineTotal;
						  //if (!ok) A.log("importSpecimens() lineNum:" + lineNum + " theLine:" + theLine);

						  // This slows down the upload 20 -> 40 min. Could do on command only?
						  //A.log("importSpeciens() backupFile:" + uploadFile.getBackupFileName());
						  // Really? Not changing anything and wondering... what?

						  specimenItem.put("backupFileName", uploadFile.getBackupFileName());

						  boolean retVal = processLine(specimenItem, taxonItem, lineNum, shortFileName, accessGroup);
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
					if ((lineNum % 5000) == 0) s_log.debug("importSpecimens() lineNum:" + lineNum + " buildLineTotal:" + buildLineTotal + " processLineTotal:" + processLineTotal);
				
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
                A.log("deleteOrphanedSpeciesFromSource() returns:" + message);
                if (message != null && !AntwebProps.isDevMode()) {
                  getUploadDetails().setMessage(message);
                }                
            } catch (AntwebException e) {
              s_log.warn("e:" + e);
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
                s_log.error("execute() rollback e:" + e2);
              }
            }

            s_log.info("importSpecimens() final lineNum:" + lineNum);
                
            in.close();
            // finally, you have to get rid of stuff in the Taxon table which is marked
            // as valid, but is not in any authority file, nor is in specimen.  This happens if
            // something is in specimen, and then removed

            s_log.info("importSpecimens() regenerateAllAntwebProject");
            // Date startTimex = new Date();
            (new ProjTaxonDb(getConnection())).regenerateAllAntweb();
            //Profiler.profile("regenerateAllAntweb", startTimex);
                
            // s_log.info("importSpecimens() removing orphans");                
            //getSpecimenUploadDb().deleteTaxonOrphans();
            //(new OrphansDb(getConnection())).deleteOrphanViloma();

            // s_log.info("importSpecimens() done removing orphans");
            (new SpecimenUploadDb(getConnection())).updateSpecimenUploadDate(accessGroup);
                
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
        int j = 0;
        String head = "";
        while (header.indexOf("\t", i) > 0) {
          j = header.indexOf("\t", i);
          head = header.substring(i, j);
          //A.log("getArrayListEmptiesToo() head:" + head + " i:" + i + " j:" + j);
          i = j+1;
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

    protected ArrayList<String> getSpecimenColumns(String header) {
        
        // Somewhat goofy way to match biota field names to  
        // the database schema.  There's a better way to do this, but I don't have time now!
        Hashtable columnTranslations = getColumnTranslations();    
        
        ArrayList<String> headers = getArrayListEmptiesToo(header);
        //String[] stringArray = header.split("\t");
        //ArrayList<String> headers = new ArrayList<String>(Arrays.asList(stringArray));
        //A.log("getSpecimenColumns() header:" + header + " headers:" + headers);
        
        ArrayList<String> colList = new ArrayList<>();
                
        for (String theHead : headers) {
            theHead = theHead.trim();

            if (getHeaderArrayList().contains(theHead)) {    
                if (columnTranslations.containsKey(theHead)) {
                    theHead = (String) columnTranslations.get(theHead);
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
    
    protected ArrayList<String> getOtherColumns(String header) {

        // ArrayList<String> headers = new ArrayList<String>(Arrays.asList(header.split("\t")));
        ArrayList<String> headers = getArrayListEmptiesToo(header);
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

    protected void compileMessages(Group group) {

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
        ArrayList<ArrayList<String>> multiBioregionList = (new SpecimenDb(getConnection())).getMultiBioregionTaxaList(group.getId());
        if (multiBioregionList.size() > 1) { // The first record would be the header.
          // Create the link to the multiBioregionTaxa
            String message = "<a href='" + AntwebProps.getDomainApp() + "/list.do?action=multiBioregionTaxaList&groupId=" + group.getId() + "'>list</a>";
            getMessageMgr().addToMessages(MessageMgr.multipleBioregionsForNonIntroducedTaxa, message);         
        } 
        
        groupMorphoGenera(group);        
    }

    private void groupMorphoGenera(Group group) {

        String query = null;
        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {

            query = "select distinct subfamily, genus from taxon where (taxarank = 'species' or taxarank = 'subspecies') "
              + " and (subfamily, genus) in ( select subfamily, genus from taxon where taxarank = 'genus' and status = 'morphotaxon') "
              + " and genus not like '(%' and status = 'morphotaxon' and access_group = ?";

            stmt = DBUtil.getPreparedStatement(getConnection(), "groupMorphoGenera()", query);

             A.log("groupMorphoGenera() query:" + query);

             stmt.setInt(1, group.getId());

            rset = stmt.executeQuery();
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
        } finally {
            DBUtil.close(stmt, "groupMorphoGenera()");
        }
    } 
        
    
}
