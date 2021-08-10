package org.calacademy.antweb.data;
    
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;  
    
public class DataPlace {
    
    private static Log s_log = LogFactory.getLog(DataPlace.class);

    public static String getValidName(String country) {
      String use = null;
      switch (country) {
            case "Saint Helena, Ascension, and Tristan da Cunha":  use = "Saint Helena, Ascension and Tristan da Cunha"; break;   
            case "Bahamas, The":  use = "Bahamas"; break;   
            case "Burma":  use = "Myanmar"; break;
            case "Cabo Verde":  use = "Cape Verde"; break;   
            case "Congo, Democratic Republic of the":  use = "Democratic Republic of the Congo"; break;   
            case "Côte d’Ivoire":  use = "Ivory Coast"; break;   
            case "Czechia":  use = "Czech Republic"; break;   
            case "Falkland Islands (Islas Malvinas)":  use = "Falkland Islands"; break;   
            case "Gambia, The":  use = "Gambia"; break;   
            case "Jan Mayen":  use = "Svalbard and Jan Mayen Islands"; break;   
            case "Macau":  use = "Macao"; break;   
            case "Micronesia, Federated States of":  use = "Micronesia"; break;   
            case "Pitcairn Islands":  use = "Pitcairn"; break;   
            case "Saint Barthelemy":  use = "Saint Barthélemy"; break;   
            case "Saint Helena":  use = "Saint Helena, Ascension and Tristan da Cunha"; break;   
            case "South Georgia and South Sandwich Islands":  use = "South Georgia and the South Sandwich Islands"; break;   
            case "Svalbard":  use = "Svalbard and Jan Mayen Islands"; break;   
            case "The Gaza Strip":  use = "Palestine"; break;   
            case "Virgin Islands":  use = "United States Virgin Islands/British Virgin Islands"; break;   
            case "West Bank":  use = "Palestine"; break;   
      }
      if (use == null) return country;
      return use;
    }

    public static boolean skipAdm1(String adm1) {
      String[] adm1List = {
          "Lima (region)"
      };
      ArrayList<String> skipAdm1s = new ArrayList<>(Arrays.asList(adm1List));
      if (skipAdm1s.contains(adm1)) {
        return true;
      }
      return false;
    }

    public static boolean skipCountry(String country) {
      String[] countryList = {
          "Akrotiri"
        , "Saint Helena, Ascension, and Tristan da Cunha"
        , "Ashmore and Cartier Islands"
        , "Clipperton Island" 
        , "Congo, Republic of the	Congo"
        , "Coral Sea Islands"
        , "Dhekelia"
        , "French Southern and Antarctic Lands"
        , "Kingman Reef"
        , "Midway Islands"
        , "No Man's Land"
        , "Oceans"
        , "Paracel Islands"
        , "Spratly Islands"
        , "Undersea Features"
        , "Wake Island"
      };
      ArrayList<String> skipCountries = new ArrayList<>(Arrays.asList(countryList));
      if (skipCountries.contains(country)) {
        return true;
      }
      return false;
    }

    public static String cleanName(String name) {
      
      String cleanName = DataPlace.cleanName(name, 1);   
      
      if (name.equals(cleanName)) {
        //A.log("cleanName unchanged:" + name);
        return cleanName; 
      } 
      cleanName = DataPlace.cleanName(cleanName, 2);    

      if (name.equals(cleanName)) {
        //A.log("cleanName 2nd pass unchanged:" + name);
        return cleanName; 
      } 
      cleanName = DataPlace.cleanName(cleanName, 2);    
	  
	  return cleanName;
	}

    public static String cleanName(String name, int call) {

      if (name.contains("Republic of")) return name;
      // leave alone:
        // Democratic Republic of Congo
        // Democratic Peoples Republic of Korea
        // United Republic of Tanzania

      String cleanName = name;

      // For strings "at the end", always have the superset string first. For instance, " Rayonu" comes before " Rayon".
      String[] list = {
          " Region"
        , " Governorate"
        , " Municipality"
        , " District"
        , " Province"
        , "Province of the "
        , "Province of "
        , " province"
        , " Department"
        , " Oblast"
        , " Region"
        , " County "
        , "Canton of "
        , "Republic of "
        , " Republic"
        , "Arrondissement of "
        , " Novads"
        , " Prefecture"
        , " Rayonu"  // These coming from Geonames data.
        , " Rayong"
        , " Rayon" 
        , " Parish"
        , "Parish of "
        , "Provincia del "
        , "Província del "
        , "Provincia de "
        , "Província de "
        , "Provincia "
        , "Província "
        , "Departamento del "
        , "Departamento de la "
        , "Departamento de "
        , "Departamento "
	    , "Región del "
	    , "Región de la "
        , "Región de "
        , "Region Autonoma Del "
	    , "Région de "
	    , "Provincie "
	    , "State of "
	    , " Municipio"
	    , "Judeţul "
	    , "skaya Oblast’"
	    , " Oblast’"
	    , "Občina "
	    , "Distrikt "
	    , "Changwat "
	    , "Tỉnh "
	    , "Qarku i "
	    , "Ostān-e "
	    , " Autonomous Okrug"
        , "aya Voblasts'"	    
	    , "skaya"
	    , " kraj"
	    , " Voivodeship"
	    , " Respublika"
	    , " Avtonomnyy Okrug"
	    , " Autonomous Okrug"
	    , "Muḩāfaz̧at "
	    , ", Federation of"
	    , " ["
	    , " State"
	    , " Županija"
	    , " Kraj"
	    , " Miestas"
	    , " (province)"
	    , " Kray"
        , "//"
        , ", The"
      };
      ArrayList<String> cleanPhrases = new ArrayList<>(Arrays.asList(list));
      for (String cleanPhrase : cleanPhrases) {
        if (name.contains(cleanPhrase)) {
          int i = name.indexOf(cleanPhrase);
          if (i > 0) {
            // Phrase is found later in the string.
            if (i + cleanPhrase.length() == name.length()) {
              // phrase is at end.
              //if (cleanPhrase.contains("skaya")) i = i+2;  // Leave the "sk" on the end.
              cleanName = name.substring(0, i);               
              //if (name.contains("Jalal-Abad")) A.log("DataPlace.cleanName() call:" + call + " log:1 was:" + name + " cleaned:" + cleanName + " i:" + i);
              break;
            } else {
              // There is text after the phrase. Abort if not in this set...
              String[] deleteAfterArray = {" [", " Oblast"};
			  ArrayList<String> deleteAfterList = new ArrayList<>(Arrays.asList(deleteAfterArray));
			  for (String deleteAfter : deleteAfterList) {
                if (cleanPhrase.equals(deleteAfter)) {
				  cleanName = name.substring(0, i);               
				  //A.log("DataPlace.cleanName() call:" + call + " log:3 was:" + name + " cleaned:" + cleanName + " i:" + i);
                  return cleanName;
                }
              }
            }
          } else {
            // Phrase is at the beginning.
            cleanName = name.substring(cleanPhrase.length()); 
            //if (name.contains("Jalal-Abad")) A.log("DataPlace.cleanName() call:" + call + " log:5 name:" + name + " cleanPhrase:" + cleanPhrase + " i:" + i  + " len:" + cleanPhrase.length() + " clearName:" + cleanName);      
            return cleanName;
            //A.log("DataPlace.cleanName() 2 was:" + name + " cleaned:" + cleanName);
          }
        }      
      }

      //A.log("DataPlace.cleanName() call:" + call + " log:6 name:" + name + " call:" + call);
      return cleanName;
    }

}