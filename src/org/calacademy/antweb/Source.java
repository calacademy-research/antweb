package org.calacademy.antweb;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Source {

    private static Log s_log = LogFactory.getLog(Source.class);
    
    
    public Source() {
      
    }
    
/*
World Ants: 
  always AntCat as source [with link to taxon page]: 
    antcat.org/catalog/508050

Fossil, 
Antcat for all valid fossil names found in Antcat otherwise Curator

Introduced
only Curator

All AntWeb
Specimen or Antcat

Georegion or Bioregion

Priorities:
  Specimen > Antcat > Literature > Curator
*/

    public final static String SPECIMEN = "specimen";
    public final static String ANTCAT = "antcat";
    public final static String LITERATURE = "literature";
    public final static String CURATOR = "curator";

    public static boolean aTrumpsB(String aSource, String bSource) {
        if (aSource == null) return false;
        if (bSource == null) return true;
        
        aSource = getSourceCanonical(aSource);
        bSource = getSourceCanonical(bSource);
        
        switch(aSource) {
			case SPECIMEN:
                return !SPECIMEN.equals(bSource);
			case ANTCAT:
                return !SPECIMEN.equals(bSource) && !ANTCAT.equals(bSource);
			case LITERATURE:
                return !SPECIMEN.equals(bSource) && !ANTCAT.equals(bSource) && !LITERATURE.equals(bSource);
			case CURATOR:
			  return false;
        }
        return false;
    }

    public static String getSourceDisplay(String source) {
      return Formatter.initCap(getSourceCanonical(source));
    }
    public static String getSourceCanonical(String source) {
       // Specimen trumps curator, antcat and lit.
       // Antcat trumps literature and curator.
       // Lit and Antcat trump curator.
       
       if (SPECIMEN.equals(source)) return SPECIMEN;
       if ("adm1Specimen".equals(source)) return SPECIMEN;
       if ("proxyspecimen".equals(source)) return SPECIMEN;

       if (ANTCAT.equals(source)) return ANTCAT;
       if ("worldants".equals(source)) return ANTCAT;
       if ("proxyantcat".equals(source)) return ANTCAT;

       if (LITERATURE.equals(source)) return LITERATURE;
       if ("antwiki".equals(source)) return LITERATURE;
       if ("proxyantwiki".equals(source)) return LITERATURE;
       if ("proxyliterature".equals(source)) return LITERATURE;

       if (CURATOR.equals(source)) return CURATOR;       
       if ("speciesList".equals(source)) return CURATOR;
       if ("speciesListTool".equals(source)) return CURATOR;  
       if ("proxyspeciesList".equals(source)) return CURATOR;
       if ("proxyspeciesListTool".equals(source)) return CURATOR;  
       if ("speciesListUpload".equals(source)) return CURATOR;
       if ("proxycurator".equals(source)) return CURATOR;       

       // Deprecate
       //if ("antmaps".equals(source)) return "AntMaps";
       if ("hasCountrySpecimen".equals(source)) return SPECIMEN;
       if ("hasAdm1Specimen".equals(source)) return SPECIMEN;
       
       // These won't happen. Deprecated.
       if ("fixGeolocaleParentage".equals(source)) return "";
       if ("fixTaxonParentage".equals(source)) return "";
       
       if ("regenerateAllAntweb".equals(source)) return "";

       return "";    
    }

/*
    public static String getSourceAnchor() {
       if ("antwiki".equals(getSource())) return "<a title='From Antwiki data'>A</a>";
       if ("specimen".equals(getSource())) return "<a title='From specimen data'>S</a>";
       if ("adm1Specimen".equals(getSource())) return "<a title='From specimen adm1 data'>s</a>";
       if ("speciesListTool".equals(getSource())) return "<a title='Antweb curator'>T</a>";
       if ("curator".equals(getSource())) return "<a title='Antweb curator'>T</a>";
       if ("speciesListUpload".equals(getSource())) return "<a title='Antweb curator'>T</a>";
       return "";
    }
*/

    public static String getSourceStr(String source) {
       if ("antwiki".equals(source)) return "Source = AntWiki.";
       if ("specimen".equals(source)) return "Source = AntWeb specimen.";
       if ("adm1Specimen".equals(source)) return "Source = AntWeb specimen (adm1).";
       if ("speciesListTool".equals(source)) return "Source = AntWeb curator.";
       if ("curator".equals(source)) return "Source = AntWeb curator.";
       if ("speciesListUpload".equals(source)) return "Source = AntWeb curator upload.";
       return "";
    }
        
    
    
}
