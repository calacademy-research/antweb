package org.calacademy.antweb;

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
        
import org.calacademy.antweb.util.*;

public final class Rank implements Serializable {

    private static Log s_log = LogFactory.getLog(Rank.class);

    public static String KINGDOM = "kingdom";
    public static String PHYLUM = "phylum";
    public static String CLASS = "class";
    public static String ORDER = "order";
    public static String FAMILY = "family";
    public static String SUBFAMILY = "subfamily";
    public static String TRIBE = "tribe";
    public static String GENUS = "genus";
    public static String SUBGENUS = "subgenus";
    public static String SPECIES = "species";
    public static String SUBSPECIES = "subspecies";
    public static String SPECIMEN = "specimen";
    public static String LOCALITY = "locality";
    

    public Rank() {
    }

    public static boolean isLegit(String rank) {
        int level = getRankLevel(rank);
        if (level == 0) return false;
        return true;
    }

    public static int getRankLevel(String rank) {
      // These are relative and can be changed here safely.  Should end at 1.
      if (KINGDOM.equals(rank)) return 11;
      if (PHYLUM.equals(rank)) return 10;
      if (CLASS.equals(rank)) return 9;
      if (ORDER.equals(rank)) return 8;
      if (FAMILY.equals(rank)) return 7;
      if (SUBFAMILY.equals(rank)) return 6;
      if (TRIBE.equals(rank)) return 5;
      if (GENUS.equals(rank)) return 4;
      if (SPECIES.equals(rank)) return 3;
      if (SUBSPECIES.equals(rank)) return 2;
      if (SPECIMEN.equals(rank)) return 1;

      //if (SPECIMEN.equals(rank)) return 1;
      return 0;
    }
    
    public static boolean isValid(String rank) {
      int rankLevel = getRankLevel(rank);
      if (rankLevel <= 0) return false;
      return true;
    }
	
	public static String getPluralOf(String rank) {
      return getPluralRank(rank);
    }	
	public static String getPluralRank(String rank) {
		if (rank == null)
			return null;
		String pluralRank;
		if (FAMILY.equals(rank)) {
			pluralRank = "families";
		} else if (SUBFAMILY.equals(rank)) {
			pluralRank = "Subfamilies";
		} else if (TRIBE.equals(rank)) {
			pluralRank = "Tribes";
		} else if (GENUS.equals(rank)) {
			pluralRank = "Genera";
		} else if (SPECIES.equals(rank)) {
			pluralRank = "Species";	
		} else if (SUBSPECIES.equals(rank)) {
			pluralRank = "Subspecies";
		} else if (SPECIMEN.equals(rank)) {
			pluralRank = "Specimens";
		} else if (LOCALITY.equals(rank)) {
			pluralRank = "Localities";
		} else {
			pluralRank = rank;
		}
		return pluralRank;
	}

	public static String getSingularOf(String rank) {
      return getSingularRank(rank);
    }
	public static String getSingularRank(String rank) {
		if (rank == null)
			return null;
		String singularRank;
		if ("Families".equals(rank)) {
			singularRank = "Family";
		} else if ("Subfamilies".equals(rank)) {
			singularRank = "Subfamily";
		} else if ("Tribes".equals(rank)) {
			singularRank = "Tribe";
		} else if ("Genera".equals(rank)) {
			singularRank = "Genus";
		} else if ("Species".equals(rank)) {
			singularRank = "Species";
		} else if ("Subspecies".equals(rank)) {
			singularRank = "Subspecies";
		} else if ("Specimens".equals(rank)) {
			singularRank = "Specimen";
		} else if ("Localities".equals(rank)) {
			singularRank = "Locality";
		} else {
			singularRank = rank;
		}
		return singularRank;
	}
	
	// a plurality sensitive version of getNextRank().
    public static String getRankPl(String rank, int count) {
    
      //A.log("Rank.getRankPl rank:" + rank + " count:" + count);
      if (1 == count) {
        if ("Specimens".equals(rank)) return "Specimen";
        //if ("subspecies".equals(rank)) return "subspecies";
        //if ("species".equals(rank)) return "species";
        if ("Genera".equals(rank)) return "Genus";
        if ("Subfamilies".equals(rank)) return "Subfamily";
        if ("Families".equals(rank)) return "Family";
      }
      return rank;
    }  	

    public static String getChildRank(String rank) {
      return getNextRank(rank, 0);
    }  
	
    public static String getNextRank(String rank) {
      return getNextRank(rank, 0);
    }  
    
    public static String getNextRank(String rank, int depth) {
      String nextRank = null;
      if (FAMILY.equals(rank)) nextRank = SUBFAMILY;
      if (SUBFAMILY.equals(rank)) nextRank = GENUS;
      if (GENUS.equals(rank)) nextRank = SPECIES;
      if (SPECIES.equals(rank)) nextRank = SPECIMEN;      
      
      // NOTE: subspecies is included but not in the ranks above
      if (SUBSPECIES.equals(rank)) nextRank = SPECIMEN;      

      if (SPECIMEN.equals(rank)) nextRank = null;  // still

      --depth;
      if (depth > 0) {
        return getNextRank(nextRank, depth);
      } else {
        return nextRank;
      }
    }
    
    public static String getNextPluralRank(String rank) {
      return getNextPluralRank(rank, 0);
    }      
    public static String getNextPluralRank(String rank, int depth) {
      return Rank.getPluralRank(Rank.getNextRank(rank, depth));
    }

    // Probematic.  Name is going away instead of taxonName.  Logic must change.
    public static ArrayList<String> getRankList(String name, String subfamily, String genus, String species, String subspecies) {
    // This is used in the search mechanism 
        ArrayList rank = new ArrayList();
/*
        if ((family != null) && (family.indexOf(name) != -1)) {
            rank.add("family");
        }
*/
        if ((subfamily != null) && (subfamily.indexOf(name) != -1)) {
            rank.add("subfamily");
        }

        if ((genus != null) && (genus.indexOf(name) != -1)) {
            rank.add("genus");
        }

        if ((species != null) && (species.indexOf(name) != -1)) {
            rank.add("species");
        }

        if ((subspecies != null) && (subspecies.indexOf(name) != -1)) {
            rank.add("subspecies");
        }
        
        if (rank.size() == 0) {
            rank.add("species");
        }
        return rank;
    }
      /*  */
    
    public static String getRank(String subfamily, String genus, String species, String subspecies) {
    // This is used in the search mechanism 
        Utility utility = new Utility();
        if (utility.notBlank(subspecies)) return SUBSPECIES;
        if (utility.notBlank(species)) return SPECIES;
        if (utility.notBlank(genus)) return GENUS;
        if (utility.notBlank(subfamily)) return SUBFAMILY;
        //if (utility.notBlank(family)) return FAMILY;
        return "not found";
    }

    
    public static String getRankClause(String rank) {
      if (FAMILY.equals(rank)) return " taxarank = 'family'";
      if (SUBFAMILY.equals(rank)) return " taxarank = 'subfamily'";
      if (GENUS.equals(rank)) return " taxarank = 'genus'";
      if (SPECIES.equals(rank)) return " (taxarank = 'species' or taxarank = 'subspecies')";
      s_log.warn("getRankClause() should never happen rank:" + rank);
      return null; // will never happen
    }
    
}
