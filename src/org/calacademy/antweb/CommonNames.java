package org.calacademy.antweb;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class CommonNames {

  private static final Log s_log = LogFactory.getLog(CommonNames.class);
  private static boolean isInitialized = false;
  private static HashMap<String, String> nameMap;
  private static ArrayList<String> namesList;

  public static void init() {
    isInitialized = true;
    nameMap = new HashMap<>();

    nameMap.put("Argentine ant", "dolichoderinaelinepithema humile");
    nameMap.put("Fire ant", "myrmicinaesolenopsis");
    nameMap.put("Carpenter ant", "formicinaecamponotus");
    nameMap.put("Trap-jaw ants", "ponerinaeodontomachus");
    nameMap.put("Bullet ant", "paraponerinaeparaponera clavata");
    nameMap.put("Driver ant", "dorylinaedorylus");
    nameMap.put("Safari ant", "dorylinaedorylus");
    nameMap.put("Siafu", "dorylinaedorylus");
    nameMap.put("Vampire ant", "amblyoponinaestigmatomma silvestrii"); // Correct? Google says: Amblyoponinae or more specifically: Amblypone silvestrii
    nameMap.put("Banded sugar ant", "formicinaecamponotus consobrinus");    
    nameMap.put("Red imported fire ant", "myrmicinaesolenopsis invicta");

    // from ant trap packaging
    nameMap.put("Acrobat ant", "myrmicinaecrematogaster");   
    nameMap.put("Big-headed ant", "myrmicinaepheidole megacephala ");   
    nameMap.put("Ghost ant", "dolichoderinaetapinoma melanocephalum");   
    nameMap.put("Cornfield abnt", "formicinaelasius alienus");   
    nameMap.put("Crazy ant", "formicinaenylanderia fulva");
    nameMap.put("Rasberry crazy ant", "formicinaenylanderia fulva");
    nameMap.put("Tawny crazy ant", "formicinaenylanderia fulva");
    nameMap.put("Little Black ant", "myrmicinaemonomorium minimum ");   
    nameMap.put("Odorous house ant", "dolichoderinaetapinoma sessile ");   
    nameMap.put("Pavement ant", "myrmicinaetetramorium caespitum");   
    nameMap.put("White-footed ant", "dolichoderinaetechnomyrmex albipes");   

    // Found on google via "common ant names"
    nameMap.put("Black garden ant", "formicinaelasius niger");
    nameMap.put("Pharoah ant", "myrmicinaemonomorium pharaonis");
    nameMap.put("Yellow crazy ant", "formicinaeanoplolepis gracilipes");
    nameMap.put("Jack jumper ant", "myrmeciinaemyrmecia pilosula");
    nameMap.put("Green tree ant", "formicinaeoecophylla smaragdina");
    nameMap.put("Weaver ant", "formicinaeoecophylla");

    // Random source
    nameMap.put("Dracula ant", "amblyoponinaeadetomyrma");
    nameMap.put("Tocandiras", "ponerinaedinoponera");
    nameMap.put("Giant Amazonian ant", "ponerinaedinoponera");
    nameMap.put("exploding ant", "formicinaecolobopsis saundersi");
    nameMap.put("turtle ant", "myrmicinaecephalotes");
    
    //nameMap.put("", "");
        
    namesList = new ArrayList<>();
      namesList.addAll(nameMap.keySet());
    //A.log("CommonNames() namesList:" + namesList);
  }

  public static ArrayList<String> getNames() {
    //A.log("getNames() initialized:" + isInitialized + " nameList:" + namesList);
    if (!isInitialized) init();
    return namesList;
  }

  public static String get(String commonName) {
    if (!isInitialized) init();
    return nameMap.get(commonName);
  }

  /**
   * Get the common names of a species from the taxonName
   *
   * @param taxonName the species name to look up
   * @return A comma + space separated list of common names for the taxonName. Returns null if no common names found.
   */
  public static String getCommonNames(String taxonName) {
    if (!isInitialized) init();
    String commonNames = nameMap.entrySet().stream()  // get common name, taxonName entry pairs from map
            .filter(e -> e.getValue().equals(taxonName))  // keep entries where taxonName matches map value
            .map(Map.Entry::getKey) // get common names
            .collect(Collectors.joining(", ")); // join all together into a comma separated list

    if (commonNames.isEmpty()) commonNames = null;  // if no names matched, collect() returns an empty string, we want null

    return commonNames;
  }  

  public static HashMap<String, String> getNameMap() {
    return nameMap;
  }

}
