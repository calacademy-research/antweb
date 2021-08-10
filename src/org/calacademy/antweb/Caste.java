package org.calacademy.antweb;

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import javax.servlet.http.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
        
import org.calacademy.antweb.util.*;

public final class Caste {

    private static Log s_log = LogFactory.getLog(Caste.class);

    public static String DEFAULT = "default";
    public static String MALE = "male";
    public static String WORKER = "worker";
    public static String QUEEN = "queen";
    public static String OTHER = "other";

    public static boolean debug = AntwebProps.isDevMode();

    public Caste() {
    }

    public static String getCaste(HttpServletRequest request) {
        String casteStr = (String) request.getParameter("caste");
        return Caste.getCaste(casteStr, request);
    }
    public static String getCaste(String requestCaste, HttpServletRequest request) {
        String casteStr = requestCaste;

		HttpSession session = request.getSession();

		if (casteStr == null) {
		  casteStr = (String) session.getAttribute("caste");
		}
		if (casteStr == null) casteStr = DEFAULT;
        //A.log("getCaste() set caste:" + casteStr);
        session.setAttribute("caste", casteStr);

        return casteStr;    
    }

    public static String getDisplayCaste(HttpServletRequest request) {
        String caste = getCaste(request);
        return getDisplayCaste(caste);
    }
    public static String getShortDisplayCaste(HttpServletRequest request) {
        String caste = getCaste(request);
        return getShortDisplayCaste(caste);
    }
    
    public static String getShortDisplayCaste(String casteStr) {
        if ("alateDealateQueen".equals(casteStr))
            return "Alate/Dealate";
        if ("brachypterous".equals(casteStr))
            return "Brachypterous";
        if ("majorSoldier".equals(casteStr))
            return "Major/Soldier";
        return Caste.getDisplayCaste(casteStr);    
    }
    
    public static String getDisplayCaste(String casteStr) {
        //A.log("getDisplayCaste() casteStr:" + casteStr);
        switch(casteStr) {

          case "majorSoldier":
            return "Major/Soldier Worker";
          case "normal":
            return "Normal Worker";

          case "ergatoidQueen":
            return "Ergatoid Queen";
          case "alateDealateQueen":
            return "Alate/Dealate Queen";
          case "brachypterous":
            return "Brachypterous Queen";
            
          case "ergatoidMale":
            return "Ergatoid Male";
          case "alateMale":
            return "Alate Male";

          case "intercaste":
            return "Intercaste";
          case "gynandromorph":
            return "Gynandromorph";
          case "larvaPupa":
            return "Larva/Pupa";   
        }
        return casteStr;    
    }
        
    public static ArrayList<Taxon> sortSpecimenByCasteSubcaste(ArrayList<Taxon> theChildren) {  
	  ArrayList<Taxon> majorSoldiers = new ArrayList<>();
	  ArrayList<Taxon> normals = new ArrayList<>();
  
	  ArrayList<Taxon> ergatoidQueens = new ArrayList<>();
	  ArrayList<Taxon> alateDealateQueens = new ArrayList<>();
	  ArrayList<Taxon> brachypterousQueens = new ArrayList<>();

	  ArrayList<Taxon> ergatoidMales = new ArrayList<>();
	  ArrayList<Taxon> alateMales = new ArrayList<>();
	  
	  //ArrayList<Taxon> other = new ArrayList<Taxon>();
	  ArrayList<Taxon> intercastes = new ArrayList<>();
	  ArrayList<Taxon> gynandromorphs = new ArrayList<>();
	  ArrayList<Taxon> larvaPupas = new ArrayList<>();

	  ArrayList<Taxon> undefined = new ArrayList<>();

	  for (Taxon taxon : theChildren) {  
		//if (taxon instanceof Genus) AntwebUtil.log("sortSpecimenByCasteSubcaste error XXX taxon:" + taxon.getTaxonName());
		Specimen specimen = (Specimen) taxon;
		String caste = specimen.getCaste();
        String subcaste = specimen.getSubcaste();

        //A.log("sortSpecimenByCasteSubcaste() caste:" + caste + " subcaste:" + subcaste);

        if (subcaste != null) {
          if (subcaste.equals("major/soldier")) majorSoldiers.add(specimen);          
          if (subcaste.equals("normal")) normals.add(specimen);          

          if (subcaste.equals("ergatoid")) ergatoidQueens.add(specimen);          
          if (subcaste.equals("alate/dealate")) alateDealateQueens.add(specimen);          
          if (subcaste.equals("brachypterous")) brachypterousQueens.add(specimen);          

          if ("male".equals(caste) && subcaste.equals("ergatoid")) ergatoidMales.add(specimen);          
          if ("male".equals(caste) && subcaste.equals("alate")) alateMales.add(specimen);          

          if (subcaste.equals("intercaste")) intercastes.add(specimen);          
          if (subcaste.equals("gynandromorph")) gynandromorphs.add(specimen);          
          if (subcaste.equals("larva/pupa")) larvaPupas.add(specimen);          
        } else {
          undefined.add(specimen);        
        }
	  }

	  theChildren = new ArrayList<>();

	  theChildren.addAll(normals);
	  theChildren.addAll(majorSoldiers);	  

	  theChildren.addAll(alateDealateQueens);
	  theChildren.addAll(ergatoidQueens);	  
	  theChildren.addAll(brachypterousQueens);	  
	  
	  theChildren.addAll(alateMales);
	  theChildren.addAll(ergatoidMales);	  

	  theChildren.addAll(intercastes); 
	  theChildren.addAll(gynandromorphs);
	  theChildren.addAll(larvaPupas);	  

      theChildren.addAll(undefined);

	  return theChildren;
    }
            
    public static String getPropsClause(String caste) {
      if (Caste.DEFAULT.equals(caste)) caste = Caste.WORKER;
      return " prop = '" + Caste.getProp(caste) + "'";
    }
    
    // The comparison string should be constants defined in this class used technically by 
    // Antweb to uniquely refer to a subcaste. The caste and subcaste are as utilized in
    // the database. In order for UI display, we use...
    public static String getSpecimenClause(String caste) {
      if (Caste.MALE.equals(caste) || Caste.WORKER.equals(caste) || Caste.QUEEN.equals(caste) || Caste.OTHER.equals(caste)) 
        return " caste = '" + caste + "'";

      if ("majorSoldier".equals(caste)) return " caste = 'worker' and subcaste = 'major/soldier'";  
      if ("normal".equals(caste)) return " caste = 'worker' and subcaste = 'normal'";  

      if ("ergatoidQueen".equals(caste)) return " caste = 'queen' and subcaste = 'ergatoid'";  
      if ("alateDealateQueen".equals(caste)) return " caste = 'queen' and subcaste = 'alate/dealate'";  
      if ("brachypterous".equals(caste)) return " caste = 'queen' and subcaste = 'brachypterous'";  

      if ("ergatoidMale".equals(caste)) return " caste = 'male' and subcaste = 'ergatoid'";  
      if ("alateMale".equals(caste)) return " caste = 'male' and subcaste = 'alate'";  

      if ("intercaste".equals(caste)) return " caste = 'other' and subcaste = 'intercaste'";  
      if ("gynandromorph".equals(caste)) return " caste = 'other' and subcaste = 'gynandromorph'";  
      if ("larvaPupa".equals(caste)) return " caste = 'other' and subcaste = 'larva/pupa'";  

      return " 1=1 ";
    }
    public static String getProp(String caste) {
      if (Caste.WORKER.equals(caste)) return "workerSpecimen";
      if (Caste.QUEEN.equals(caste)) return "queenSpecimen";      
      if (Caste.MALE.equals(caste)) return "maleSpecimen";
      return null;
    }     
    public static String getPicImg(String caste) {
      if (Caste.WORKER.equals(caste)) return "picW.png";
      if (Caste.QUEEN.equals(caste)) return "picQ.png";
      if (Caste.MALE.equals(caste)) return "picM.png";
      return null;
    }

    private static boolean endsWith(String casteNote, String str) {
      int casteNoteLength = casteNote.length() - 1;
      if (casteNoteLength <= 0) return false;
      if (str.equals(casteNote.substring(casteNoteLength))) {
		  //A.log(""endsWith() true caste:" + caste + " str:" + str);
          return true;
      }
      return false;
    }
        
    private static String getCasteNote(String casteNotes) { // casteNotes is the Life Stage field.
      // return the string up until "and" or a comma. Could be many ants in the notes.
      if (casteNotes == null) return null;
      int delimiterPos = casteNotes.indexOf(",");
      int andPos = casteNotes.indexOf(" and ");
      if (andPos > 0 && andPos < delimiterPos) delimiterPos = andPos;
      int slashPos = casteNotes.indexOf("/");
      if (slashPos > 0 && slashPos < delimiterPos) delimiterPos = slashPos;
      if (delimiterPos <= 0) return casteNotes; 
      return casteNotes.substring(0, delimiterPos);
    }           

    public static boolean isWorker(String casteNote) {
      if ( casteNote.contains("worker")
        || endsWith(casteNote, "w")
        || casteNote.contains("w ")
        || casteNote.contains("soldier")
        || "w".equals(casteNote)
        || casteNote.contains("major")
        || casteNote.contains("minor")
         ) return true;
      for (int i=0 ; i < 10 ; ++i) {
        if (casteNote.contains(i + "w")) return true;
        if (casteNote.contains(i + "s")) return true;
      }
      return false;
    }
    public static String getWorkerSubcaste(String casteNote) {
      if ( casteNote.contains("major")
        //|| casteNote.contains("s")
         || casteNote.contains("soldier")
         ) return "major/soldier";
      for (int i=0 ; i < 10 ; ++i) {
        if (casteNote.contains(i + "s")) return "major/soldier";
      }
      if (casteNote.contains("minor")
        || casteNote.contains("normal")
         ) return "normal";
      return "normal";
    }
    public static boolean isQueen(String casteNote) {
      if ( casteNote.contains("q")
        || casteNote.contains("late")
        || casteNote.contains("gyne")
         ) return true;
      for (int i=0 ; i < 5 ; ++i) {
        if (casteNote.contains("brachypterous")) return true;  
        if (casteNote.contains("dichthadiiform")) return true;  
      }
      return false;
    }
    public static String getQueenSubcaste(String casteNote) {
      if (casteNote.contains("ergatoid")
        || casteNote.contains("wingless")        
        || casteNote.contains("apterous")
        || casteNote.contains("dichthadiiform")                
         ) return "ergatoid";
      if (casteNote.contains("winged")      
        || casteNote.contains("microgyne")
        || casteNote.contains("dealate") 
        || casteNote.contains("dq")         
        || casteNote.contains("alate") 
        || casteNote.contains("aq") 
         ) return "alate/dealate";
      if (casteNote.contains("brachypterous")
        || casteNote.contains("short-winged")         
        || casteNote.contains("non-flying") 
         ) return "brachypterous";
      return "alate/dealate";
    }        
    private static boolean isMale(String casteNote) {
      if ((casteNote.contains("male") && !casteNote.contains("female"))
        || endsWith(casteNote, "m")
        || casteNote.contains("m ")
        || "m".equals(casteNote)
         ) return true;
      for (int i=0 ; i < 10 ; ++i) {
        if (casteNote.contains(i + "m")) return true;
      }
      return false;
    }
    public static String getMaleSubcaste(String casteNote) {
      if (casteNote.contains("ergatoid")
        || casteNote.contains("apterous")
         ) return "ergatoid";
      if (casteNote.contains("winged")
         ) return "alate";
      return "alate";
    }    
    private static boolean isOther(String casteNote) {
      if ( casteNote.contains("intercaste")
        || casteNote.contains("gynandromorph")
        || casteNote.contains("larva")
        || casteNote.contains("pupa")
        || casteNote.contains("brood")
         ) return true;
      return false;
    }
    public static String getOtherSubcaste(String casteNote) {
      if (casteNote.contains("intercaste")
         ) return "intercaste";
      if (casteNote.contains("gynandromorph")
         ) return "gynandromorph";
      if (casteNote.contains("larva")
        || casteNote.contains("pupa")
        || casteNote.contains("brood")
         ) return "larva/pupa";
      return null;
    }    

    public static String[] getCasteValues(String casteNotes) {
      String[] casteValues = new String[2];
      String casteNote = getCasteNote(casteNotes);
      if (casteNote == null) {
        casteValues[0] = null;
        casteValues[1] = null;
        return casteValues;
      }
      casteNote = casteNote.toLowerCase();
      if (isWorker(casteNote)) {
        casteValues[0] = "worker";
        casteValues[1] = getWorkerSubcaste(casteNote);
      } else 
      if (isQueen(casteNote)) {
        casteValues[0] = "queen";
        casteValues[1] = getQueenSubcaste(casteNote);
      } else
      if (isMale(casteNote)) {
        casteValues[0] = "male";
        casteValues[1] = getMaleSubcaste(casteNote);
      } else 
      if (isOther(casteNote)) {
        casteValues[0] = "other";      
        casteValues[1] = getOtherSubcaste(casteNote);
      }
      //if (casteNotes.contains(",")) A.log("getCasteValues() casteNotes:" + casteNotes + " casteNote:" + casteNote);
      //String test = "1sq, 1s, 1w";
      
      debug(casteNotes, casteValues, "2 dealate queens, 12 workers");
	  debug(casteNotes, casteValues, "microgyne and worker");
	  //debug(casteNotes, casteValues, "male and worker");
	  debug(casteNotes, casteValues, "queen pupa");
	  debug(casteNotes, casteValues, "queen/male");

      return casteValues;
    }

    private static void debug(String casteNotes, String[] casteValues, String test) {
      if (casteNotes.equals(test)) A.log("degug() casteNotes:" + test + " isWorker:" + isWorker(casteNotes) + " isQueen:" + isQueen(casteNotes) + " casteValues[0]:" + casteValues[0] + " casteValues[1]:" + casteValues[1]);    
    }
    
}

/* Good for testing...
// Unidentified
select count(*), caste from specimen where caste is not null and caste is null and access_group = 2 group by caste;

// All Queens? Do same for male and worker (with subcaste = "minor/major")
select distinct caste from specimen where access_group = 2 and caste = "queen";

// These shouldn't exist...
select caste from specimen where caste = "queen" and subcaste is null;

*/
