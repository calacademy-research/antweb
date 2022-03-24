package org.calacademy.antweb;

import java.util.*;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CommonNames {

  private static Log s_log = LogFactory.getLog(CommonNames.class);
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
    nameMap.put("Banded sugar ant", "formicinaecamponotus consobrinus");
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
    String taxonName = nameMap.get(commonName);
    return taxonName;  
  }  

  public static String getCommonNames(String taxonName) {
    if (!isInitialized) init();
    String commonNames = "";

    int i = 0;
	for(Map.Entry entry: nameMap.entrySet()){
		if(taxonName.equals(entry.getValue())){
			++i;
			if (i > 1) commonNames += ", ";
			commonNames += entry.getKey();
		}
	}
    if (i == 0) return null;
    return commonNames;
  }  

  public static HashMap<String, String> getNameMap() {
    return nameMap;
  }

}
