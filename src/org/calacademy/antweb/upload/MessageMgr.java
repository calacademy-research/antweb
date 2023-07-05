package org.calacademy.antweb.upload;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.Group;
import org.calacademy.antweb.util.A;
import org.calacademy.antweb.util.AntwebProps;
import org.calacademy.antweb.util.HttpUtil;
import org.calacademy.antweb.util.LogMgr;

import java.util.*;
    
public class MessageMgr {

    public MessageMgr() {
      init();
    }

    private static final Log s_log = LogFactory.getLog(MessageMgr.class);

    private Vector<String> s_messages = new Vector<>();
    public Vector<String> getMessages() {
      return s_messages;
    }
    // called with an empty vector by UploadAction.
    public void setMessages(Vector<String> vector) {
      s_messages = vector;
    }

    // Used by Worldants
    private static Vector<String> s_errors = new Vector<>();
    public static Vector<String> getErrors() {
        return s_errors;
    }
    // called with an empty vector by UploadAction.
    //public void setErrors(Vector<String> vector) {
    //    s_errors = vector;
    //}
    public static void addToErrors(String error) {
        if (getErrorCount() <= maxI)
          getErrors().add(error);
    }
    public static int getErrorCount() {
        return getErrors().size();
    }
    public static boolean hasErrors() {
        return getErrors().size() > 0;
    }
    private static final int maxI = 20;
    public static String getErrorsReport() {
        String errorReport = "<br>&nbsp;&nbsp;&nbsp;<h3>Errors:</h3> ";
        int i = 0;
        for (String error : getErrors()) {
          i = i + 1;
          if (i <= maxI) errorReport += "<br><br>" + error;
          if (i == maxI) errorReport += "<br>...";
        }
        return errorReport;
    }


    public static String s_message;
    // This is for a show stopper.  Bad column for instance.
    public void addMessage(String message) {
      s_message = message;
    }
    
    private String messageStr;
    public String getMessageStr() {
      return messageStr;
    }

    // Flag system is different from messages. Will create a message but just include a count.
    // Customized code below. Anti-pattern.
    private HashMap<String, Integer> flags = new HashMap<>();
    public void flag(String key) {
      //A.log("MessageMgr.flag() key:" + key);
      if (flags.get(key) == null) {
        flags.put(key, 1);
      } else {
        Integer v = flags.get(key);
        flags.put(key, v + 1);
      }
    }

    private final int maxMessageCount = 200;

    private final String SET = "set";
    private final String STR = "str";
    private final String MAP = "map";
    private final String NUM = "num";

    final ArrayList<Test> testList = new ArrayList<>();
    private ArrayList<Test> getTests() {
      return testList;
    }

    // Tests
    public static final String invalidSubfamily = "invalidSubfamily";
    public static final String invalidSubfamilyMap = "invalidSubfamilyMap";    
    public static final String generaAreHomonyms = "generaAreHomonyms";
    public static final String invalidSubfamilyForGenus = "invalidSubfamilyForGenus";
    public static final String noValidSubfamilyForGenus = "noValidSubfamilyForGenus";
    public static final String preferredSubfamilyForGenusReplaced = "preferredSubfamilyForGenusReplaced";
    public static final String invalidSubfamilyForGenusReplaced = "invalidSubfamilyForGenusReplaced";
    public static final String databaseErrors = "databaseErrors";
    public static final String futureDateCollected = "futureDateCollected";
    public static final String futureDateDetermined = "futureDateDetermined";
    public static final String duplicateEntries = "duplicateEntries";
    public static final String taxonNamesAreHomonyms = "taxonNamesAreHomonyms";
    public static final String unavailableQuadrinomial = "unavailableQuadrinomial";
    public static final String unrecognizedInvalidSpecies = "unrecognizedInvalidSpecies";
    public static final String recognizedInvalidSpecies = "recognizedInvalidSpecies";
    public static final String taxonNameNotFoundInBolton = "taxonNameNotFoundInBolton";
    public static final String taxonNameNotFoundInAntcatUpload = "taxonNameNotFoundInAntcatUpload";
    public static final String taxonNamesUpdatedToBeCurrentValidName = "taxonNamesUpdatedToBeCurrentValidName";
    public static final String countryMissing = "countryMissing";
    public static final String adm1Missing = "adm1Missing";
    public static final String countryNotFound = "countryNotFound";
    public static final String notValidBioregion = "notValidBioregion";
    //public static final String adm1UpgradeToIsland = "adm1UpgradeToIsland";
    public static final String invalidLatLon = "invalidLatLon";
    public static final String notValidCountries = "notValidCountries";
    public static final String unrecognizedColumn = "unrecognizedColumn";
    public static final String invalidColumn = "invalidColumn";
    public static final String notValidAdm1 = "notValidAdm1";
    //public static final String noCasteNotes = "noCasteNotes";  
    public static final String unrecognizedCaste = "unrecognizedCaste";
    public static final String invalidDateCollectedStart = "invalidDateCollectedStart";
    public static final String invalidDateCollectedEnd = "invalidDateCollectedEnd";
    public static final String invalidDateDetermined = "invalidDateDetermined";

    public static final String makeTaxonNameError = "makeTaxonNameError";
    public static final String correctedBioregion = "correctedBioregion";
    public static final String latLonNotInCountryBounds = "latLonNotInCountryBounds";
    public static final String latLonNotInAdm1Bounds = "latLonNotInAdm1Bounds";
    //public static final String nonASCII = "nonASCII";
    public static final String incorrectElevationFormat = "incorrectElevationFormat";
    public static final String codeNotFound = "codeNotFound";
    public static final String emptyStringSubfamily = "emptyStringSubfamily";
    public static final String emptyStringGenus = "emptyStringGenus";
    public static final String emptyStringSpecies = "emptyStringSpecies";
    public static final String subfamilyForGenusFoundInHomonym = "subfamilyForGenusFoundInHomonym";
    public static final String subfamilyGenusComboNotInBolton = "subfamilyGenusComboNotInBolton";        
    public static final String genusOutsideNativeBioregion = "genusOutsideNativeBioregion";    
    public static final String badQuotations = "badQuotations";
    public static final String troubleParsingLines = "troubleParsingLines";
    public static final String duplicatedRecord = "duplicatedRecord";    
    public static final String multipleBioregionsForNonIntroducedTaxa = "multipleBioregionsForNonIntroducedTaxa";
     public static final String parsingErrors = "parsingErrors";
    public static final String extraCarriageReturn = "extraCarriageReturn";
    public static final String groupMorphoGenera = "groupMorphoGenera";
// These should be at the end of the report
    public static final String nameNotInFamilyFormicidae = "nameNotInFamilyFormicidae";
    public static final String nonValidWorldantsDup = "nonValidWorldantsDup";
    public static final String badRankList = "badRankList";    
    public static final String noRecordsProcessed = "noRecordsProcessed";
    public static final String codeNotFoundInLine = "codeNotFoundInLine";
    public static final String nonAntTaxa = "nonAntTaxa";
    public static final String invalidSpeciesName = "invalidSpeciesName";
    public static final String specialCharacterFound = "specialCharacterFound";
    // public static final String  = "";
    
     
    // All of the tests below should have constants above, and vice versa.
    public void init() {
        s_message = null;
        s_messages = new Vector<>();
        s_errors = new Vector<>();
        flags = new HashMap<>();

      testList.add(new Test(noRecordsProcessed, STR, "<b>Rollback occurred. <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(duplicateEntries, SET, "<b>Duplicate Entries <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(invalidSubfamily, SET, "<b>Invalid Subfamily <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(invalidSubfamilyMap, MAP, "<b>Invalid Subfamily <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(generaAreHomonyms, SET, "<b>The Following Genera are Homonyms <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(invalidSubfamilyForGenus, SET, "<b>Invalid Subfamily For Genus <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(noValidSubfamilyForGenus, SET, "<b>No valid Subfamily For Genus <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(preferredSubfamilyForGenusReplaced, SET, "<b>Preferred Subfamily For Genus <font color=green>(uploaded)</font></b>"));
      testList.add(new Test(invalidSubfamilyForGenusReplaced, MAP, "<b>Invalid Subfamily for Genus.  Submitted subfamily replaced with valid subfamily <font color=green>(uploaded)</font></b>"));
      testList.add(new Test(databaseErrors, SET, "<b>Database Errors <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(futureDateCollected, SET, "<b>Date Collected Start is in the future <font color=green>(uploaded)</font></b>"));
      testList.add(new Test(futureDateDetermined, SET, "<b>Date Determined is in the future <font color=green>(uploaded)</font></b>"));
      testList.add(new Test(taxonNamesAreHomonyms, SET, "<b>Taxon names recognized as Homonyms.  Names are not verified against AntCat.org  Submission <font color=green>(uploaded)</font></b>"));      
      testList.add(new Test(unavailableQuadrinomial, SET, "<b>Unavailable quadrinomial. Submission <font color=green>(uploaded)</font></b>"));
      testList.add(new Test(unrecognizedInvalidSpecies, SET, "<b>Unrecognized Invalid species.  Names not found in AntCat.org. Submission <font color=green>(uploaded)</font></b>"));
      testList.add(new Test(recognizedInvalidSpecies, SET, "<b>Recognized invalid species.  Submission replaced with current valid name from AntCat.org <font color=green>(uploaded)</font></b>"));
      testList.add(new Test(taxonNameNotFoundInBolton, SET, "<b>Taxon names not found in Bolton World Catalog <font color=green>(uploaded)</font></b>"));
      testList.add(new Test(taxonNameNotFoundInAntcatUpload, SET, "<b>Taxon names not found in Antcat upload <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(taxonNamesUpdatedToBeCurrentValidName, SET, "<b>Taxon names that need to be updated to current valid name<font color=green>(uploaded)</font></b>"));
      testList.add(new Test(countryMissing, NUM, "<b>Country is missing <font color=green>(uploaded)</font></b>"));
      testList.add(new Test(adm1Missing, SET, "<b>Adm1 is missing, but expected for these countries <font color=green>(uploaded)</font></b>"));
      testList.add(new Test(countryNotFound, SET, "<b>Country not found</b>"));
      testList.add(new Test(notValidBioregion, STR, "<b>Not valid Antweb Biogeographic Regions</b> "));
      //testList.add(new Test(adm1UpgradeToIsland, STR, "<b>Adm1 upgraded to Island Country</b>"));
      testList.add(new Test(notValidCountries, STR, "<b>Not Antweb valid countries</b>"));
      testList.add(new Test(invalidLatLon, STR, "<b>Invalid lat/lon <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(unrecognizedColumn, STR, "<b>Unrecognized Column <font color=red>(ignored)</font></b>"));
      testList.add(new Test(invalidColumn, SET, "<b>Invalid Column Count <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(notValidAdm1, STR, "<b>Not <a href='http://geonames.nga.mil/namesgaz/'>valid</a> Antweb adm1</b>"));
      //testList.add(new Test(noCasteNotes, STR, "<b>No life stage/sex data. Undiscernable <a href='" + AntwebProps.getDomainApp() + "/casteDisplayPage.do'>caste</a></b>"));
      testList.add(new Test(unrecognizedCaste, STR, "<b>Unrecognized Caste</b>")); // Not a recognized <a href='" + AntwebProps.getDomainApp() + "/casteList.do'>caste</a>
      testList.add(new Test(invalidDateCollectedStart, STR, "<b>Invalid Date Collected Start</b>"));
      testList.add(new Test(invalidDateCollectedEnd, STR, "<b>Invalid Date Collected End</b>"));
      testList.add(new Test(invalidDateDetermined , STR, "<b>Invalid Date Determined</b>"));
      testList.add(new Test(makeTaxonNameError, STR, "<b>Error constructing taxon name <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(latLonNotInCountryBounds, STR, "<b>Lat/Long not within country's bounds</b>", "red"));
      testList.add(new Test(latLonNotInAdm1Bounds, STR, "<b>Lat/Long not within adm1's bounds</b>", "red"));
      //testList.add(new Test(nonASCII, SET, "<b>Non ASCII TaxonNames <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(incorrectElevationFormat, STR, "<b>Incorrect elevation format</b>"));
      testList.add(new Test(codeNotFound, "set", "<b>Code not found <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(emptyStringSubfamily, "set", "<b>Empty String Subfamily <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(emptyStringGenus, "set", "<b>Empty String Genus <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(emptyStringSpecies, "set", "<b>Empty String Species <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(subfamilyForGenusFoundInHomonym, "set", "<b>Subfamily For Genus Found in Homonym <font color=green>(uploaded)</font></b>"));
      testList.add(new Test(subfamilyGenusComboNotInBolton, MAP, "<b>Taxon Subfamily / Genus combinations not found in Bolton World Catalog <font color=green>(uploaded)</font></b>"));
      testList.add(new Test(genusOutsideNativeBioregion, MAP, "<b>Genus found outside of known native biogeographic region</b>", "red"));
      testList.add(new Test(badQuotations, MAP, "<b>Data field should not be in quotations <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(troubleParsingLines, STR, "<b>Trouble parsing lines</b>"));
      testList.add(new Test(duplicatedRecord, MAP, "<b>Duplicated record</b>"));
      testList.add(new Test(multipleBioregionsForNonIntroducedTaxa, STR, "<b>Multiple bioregions for non-introduced taxa</b>"));
      testList.add(new Test(parsingErrors, STR, "<b>Parsing Errors (bad rank?) - <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(extraCarriageReturn, SET, "<b>Extra Carriage Return? (inconsistent column count) in specimen records <font color=red>(not uploaded)</font></b>"));     
      testList.add(new Test(correctedBioregion, SET, "<b>Corrected <a href='" + AntwebProps.getDomainApp() + "/bioregionCountryList.do'>Biogeographic Regions</a></b>"));
      testList.add(new Test(groupMorphoGenera, SET, "<b>Morpho Genera (containing non-alphabetic characters) <font color=green>(uploaded)</font></b>"));
// These should be at the end of the report
      testList.add(new Test(nameNotInFamilyFormicidae, SET, "<b>Names not in Family Formicidae <font color=green>(uploaded)</font></b>"));
      testList.add(new Test(nonValidWorldantsDup, SET, "<b>Worldants record is not valid and is duplicated <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(badRankList, SET, "<b>Parsing Errors (bad rank) <font color=red>(not uploaded)</font></b>"));
      testList.add(new Test(codeNotFoundInLine, SET, "<b>Code not found <font color=red>(fix these)</font></b>"));     
      testList.add(new Test(nonAntTaxa, SET, "<b>Non-ant taxa <font color=green>(uploaded)</font></b>"));     
      testList.add(new Test(invalidSpeciesName, SET, "<b>Invalid species name <font color=red>(not uploaded)</font></b>"));

      testList.add(new Test(specialCharacterFound, SET, "<b>Special Character Found <font color=red>(not uploaded)</font></b>"));
    }      
    
    public static String getMessageDisplay(String key) {
      Test test = new MessageMgr().getTest(key);
      if (test != null) return test.getHeading();
      return key;
    }
    
    private Test getTest(String key) {
      for (Test test : testList) {
        if (test.getKey().equals(key)) 
          return test;
      }
      s_log.warn("getTest() Not supposed to happen. It did. Key:" + key);
      return null; // Never happen.
    }

    public void addToMessages(String key) { 
      Test test = getTest(key);
      test.increment();
    }
    
    public void addToMessages(String key, String value) { 
        addToMessages(key, value, null);
    }

    public void addToMessages(String key, String value, String detail) { 
        Test test = getTest(key);
        if (value == null) value = "null";
        test.add(value);
        test.addDetail(detail);
        test.increment();
    }
    
    public void addToMapMessages(String key, String key2, String value) { 
        Test test = getTest(key);
        if (value == null) value = "null";
        test.add(key2, value);
        test.increment();
    }
   
    public String getMessagesReport() {        
      String logString = "";
      if (messageStr != null) {
        logString += messageStr;
      }

      if (hasErrors()) {
          logString += getErrorsReport();
      }

      Vector<String> messages = getMessages();
      boolean hasMessages = !messages.isEmpty();

      logString += "<h3>Errors:</h3>"; //<pre>
      int i = 0;
      for (String message : messages) {
  		  ++i;
		  //A.log("getMessagesReport() i:" + i + " message:" + message);
		  if (i > 1) logString += "<br><br>";
		  logString += "\r\r<b>" + i + "</b>:" + message;
      }

	  logString += "<h3>Passed Tests:</h3>";
	  i = 0;
	  for (Test test : getTests()) {
		  if (!test.isPassed()) continue;
		  ++i;
		  if (i > 1) logString += "<br>";
		  logString += "\r\r&nbsp;<b> " + i + "</b>:" + test.getHeading();
	  }          

	  A.log("getMessagesReport() logString:" + logString);
      return logString;
    }

    public void compileMessages(Group group) {
      // Called from SpecimenUpload, where this code used to live.
      
        if (s_message != null) {
          getMessages().add(s_message);
        }

        int i = 0;
        for (Test test : getTests()) {
          ++i;
          test.setGroup(group);
          String message = test.toString();
          //A.log("MessageMgr.compileMessages() i:" + i + " count:" + test.getCount() + " key:" + test.getKey() + " type:" +  test.getType() + " test:" + test + " message:" + message);
          if (message != null) {
            //if (SET.equals(test.getType())) {
              // then add the total. Can't do it later for this type.
              //message += "total:" + test.getCount() + ")";
            //}
            if ("red".equals(test.getFlag())) {
              message = " <a title='Item uploaded but until the issue is resolved the item will be innaccessible through most Antweb services.'><font color=red>(Red Flag)</font></a>" + message;
              addToRedFlagCount(test.getCount());
            }
            getMessages().add(message);
          }
        }  
                
        Integer introducedCount = flags.get("is_introduced");
        if (introducedCount != null && introducedCount > 0) {
          String curiousQuery = AntwebProps.getDomainApp() + "/list.do?action=introducedSpecimen&groupId=" + group.getId(); 
          String listLink = "<a href='" + curiousQuery + "'>list</a>";
          String message = " <b>Some specimens were flagged as introduced (total:" + introducedCount + "):</b> " + listLink;
          getMessages().add(message);
        }

        Integer noCasteNotesCount = flags.get("noCasteNotes");
        if (noCasteNotesCount != null && noCasteNotesCount > 0) {
          //String curiousQuery = AntwebProps.getDomainApp() + "/list.do?action=introducedSpecimen&groupId=" + group.getId(); 
          //String listLink = "<a href='" + curiousQuery + "'>list</a>";
          String message = " <b><a href='" + AntwebProps.getDomainApp() + "/casteDisplayPage.do'>Caste</a> is indiscernable (No life stage/sex data) (total:" + noCasteNotesCount + ").</b>"; // + listLink;  
          getMessages().add(message);
        }          
    }
    
    int redFlagCount = 0;
    public void addToRedFlagCount(int c) {
      redFlagCount = redFlagCount + c;
    }
    public int getRedFlagCount() {
      return redFlagCount;
    }
    
    class Test {
      boolean isPassed = true;
      int addCount = 0;
      Group group;
      final String key;
      final String type;
      final String heading;
      String flag;

      String getFlag() { return flag; }
      int count = 0;

      final HashMap<String, TreeSet<String>> messageStringHash = new HashMap<>();
      final HashMap<String, HashSet<String>> messageSetsHash = new HashMap<>();
      final HashMap<String, HashMap<String, HashSet<String>>> messageMapsHash = new HashMap<>();

      final Vector<String> details = new Vector<>();

      Test(String key, String type, String heading, String flag) {
        this(key, type, heading);
        this.flag = flag;
      }
      
      Test(String key, String type, String heading) {
        // type should be in {"String", "Set", "Hash")
        this.key = key;
        this.type = type;
        this.heading = heading;
      }
      
      boolean isPassed() {
        return isPassed;
      }
      
      void setGroup(Group group) {
        this.group = group;
      }
      Group getGroup() {
        return this.group;
      }
            
      String getKey() {
        return key;
      }
      String getHeading() {
        return heading;
      }
      String getType() {
        return type;
      }      
      void increment() {
        ++count;
      }
      int getCount() {
        return count;
      }
      
      /* 
      Details are written to a log file which is linked to from the Upload Report.
      These are allowed to get long without mussing up the report.      
      */
      void addDetail(String detail) {
        if (detail != null) details.add(detail);
      }            

      void writeDetails() {
        if (details.size() > 0) {
            String fileName = "detail/" + getKey() + getGroup().getId() + ".jsp";
            LogMgr.emptyLog(fileName);
            for (String detail : details) {
              LogMgr.appendLog(fileName, detail);
            }        
        }
      }
      String getDetailLink() {
        if (getGroup() == null) return "";
        if (details.size() > 0) {
  	  	  return " (<a href='" + AntwebProps.getDomainApp() + "/web/log/detail/" + getKey() + getGroup().getId() + ".jsp'>details</a>)";
        }
        return "";
      }

      // STR or SET
      void add(String value) {
      
        if (value == null) {
          s_log.debug("MessageMgr.set() ignoring null");
        }
            
        isPassed = false;

        
        // A.log("MessageMgr.Test.add() key:" + key + " value:" + value);
        if (STR.equals(type)) {
		  if (messageStringHash.size() == maxMessageCount) s_log.warn("add() exceeeds maxMessageCount:");
		  if (messageStringHash.size() > maxMessageCount) return;
	
		  //A.log("addToMessageStrings() key:" + key + " value:" + value);
		  if (!messageStringHash.containsKey(key)) {
    	 	++addCount;
			TreeSet<String> valueSet = new TreeSet<>();
			valueSet.add(value);
			messageStringHash.put(key, valueSet);
		  } else {
			TreeSet<String> valueSet = messageStringHash.get(key);
		    if (!valueSet.contains(value)) ++addCount;
			valueSet.add(value);
		  }
        } else if (SET.equals(type)) {
		  if (messageSetsHash.size() == maxMessageCount) s_log.warn("add() exceeeds maxMessageCount:");
		  if (messageSetsHash.size() > maxMessageCount) return;

		  if (!messageSetsHash.containsKey(key)) {
      	    ++addCount;
			HashSet<String> valueSet = new HashSet<>();
			valueSet.add(value);
			messageSetsHash.put(key, valueSet);
		  } else {
			HashSet<String> valueSet = messageSetsHash.get(key);
            if (!valueSet.contains(value)) ++addCount;
			valueSet.add(value);
		  }        
        }      
      }
      
      // MAP
      void add(String key2, String value) {
            
		 // A.log("MessageMgr.add() key:" + key + " key2:" + key2);
	
		  if (messageMapsHash.size() == maxMessageCount) s_log.warn("addToMessageMaps() exceeeds maxMessageCount:");
		  if (messageMapsHash.size() > maxMessageCount) return;
	
		  if (!messageMapsHash.containsKey(key)) {
            ++addCount;
			HashMap<String, HashSet<String>> valueMap = new HashMap<>();
			HashSet<String> valueSet = new HashSet<>();
			valueSet.add(value);
			valueMap.put(key2, valueSet);
			messageMapsHash.put(key, valueMap);
		  } else {
			HashMap<String, HashSet<String>> valueMap = messageMapsHash.get(key);
			if (!valueMap.containsKey(key2)) {
              ++addCount;
			  HashSet<String> valueSet = new HashSet<>();
			  valueSet.add(value);
			  valueMap.put(key2, valueSet);  
			} else {        
			  HashSet<String> valueSet = valueMap.get(key2);
			  if (valueSet == null) {
				s_log.error("addToMessageMaps() key:" + key + " key2:" + key2 + " value:" + value);
			  } else { 
				valueSet.add(value);
			  }
			}
		  }      
      }
      
      public String toString() {
        writeDetails();
      
        String returnStr = null;

        // NUM
        if (count > 0) {
          //A.log("toString() heading:" + heading);
          returnStr = "";
          String groupName = group.getName();
          if (groupName != null) groupName = HttpUtil.encode(groupName);
          if ("countryMissing".equals(getKey())) {
            //A.log("MessageMgr.Test.toString() group:" + groupName);
            String countLink = "<a href='" + AntwebProps.getDomainApp() + "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&country=null&groupName=" + groupName + "'>" + getCount() + "</a>";
            returnStr += "<b>" + getHeading() + ":" + countLink + "</b>";          
          } else {
            returnStr += "<b>" + getHeading() + ":" + getCount() + "</b>";          
          }
        }

        // STR
        if (messageStringHash.size() > 0) {
            returnStr = "";    
            Set<String> keySet = messageStringHash.keySet();
            ArrayList<String> list = new ArrayList<>(keySet);
            Collections.sort(list);
            for (String key : list) {
                TreeSet<String> values = messageStringHash.get(key);
                String messages = "&nbsp;";
                String listSize = "";
                if (addCount > 1) {
                  // Would be nice if this was accurate. 1: Duplicate Entries (not uploaded) (details)
                  // the value is always 1. Could we count correctly.
                  listSize = " (total:" + addCount + ")";
                }
                String detailLink = getDetailLink();
                
                //String flagLink = "";
                //if (...
                
                messages += "<b>" + getHeading() + detailLink + listSize + ": </b>";  // was with "["
				String delimiter = ", ";
                if ("unrecognizedCaste".equals(key)) delimiter = "; ";
                int i = 0;
                for (String value : values) {
                    ++i;
                    if (i > 1) messages += delimiter;
                    messages += value;                      
                }                    
                if (key.contains("lines")) messages += "<br>* line numbers are approximate";

				returnStr += messages;    
            } 
        }
        
        // SET
        if (messageSetsHash.size() > 0) {
            returnStr = "";    
            Set<String> keySet = messageSetsHash.keySet();
            ArrayList<String> list = new ArrayList<>(keySet);
            //A.log("MessagMgr.toString() SET list:" + list + " size:" + list.size());            
            Collections.sort(list);
            for (String key : list) {
                //This is hard to sort. Text string, with line numbers. 22 will come before 3.
                HashSet<String> values = messageSetsHash.get(key);
                ArrayList<String> list2 = new ArrayList<>(values);
                //A.log("MessagMgr.toString() SET key:" + key + " list2:" + list2 + " size:" + list2.size());                 

                Collections.sort(list2);

                //A.log("MessagMgr.toString() 2 b list2:" + list2);                 

                String detailLink = getDetailLink();

                String listSize = " (total:" + getCount() + ")";
                
                String messages = "&nbsp;<b>" + getHeading() + detailLink + listSize + ": </b>";

                boolean printAsList = !"adm1Missing".equals(getKey());
                int i = 0;
                for (String value : list2) {
                  if (printAsList) {
                    messages += "<br>&nbsp;&nbsp;" + value;          
                  } else {
                    if (i > 0) {
                      messages += ", &nbsp;" + value;          
                    } else {
                      messages += value;                              
                    }
                    ++i;
                  }
                }
				returnStr += messages;    
            } 
    }
        
        // MAP
        if (messageMapsHash.size() > 0) {
            returnStr = "";    
            Set<String> keySet = messageMapsHash.keySet();
            ArrayList<String> list = new ArrayList<>(keySet);
            Collections.sort(list);
            for (String key1 : list) {
                HashMap<String, HashSet<String>> valueMap = messageMapsHash.get(key1);
                ArrayList<String> list2 = new ArrayList<>(valueMap.keySet());
                Collections.sort(list2);

                String detailLink = getDetailLink();
                
                String listSize = " (total:" + addCount + ")";

                String messages = "&nbsp;<b>" + getHeading() + detailLink + listSize + ": </b>";
                for (String key2 : list2) {            
                    messages += "<br>&nbsp;&nbsp;<b>" + key2 + "</b>: ";
                    HashSet<String> values = valueMap.get(key2);
    				int i = 0;
                    for (String value : values) {
                       ++i;
                       if (i > 1) messages += ", ";
                       messages += value; 
                    }                    
                }
                //A.log("compileMessages() 3 key1:" + key1);
				returnStr += messages;    
            }    
        }           
        return returnStr;
      }
      
    }
    
}