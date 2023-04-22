package org.calacademy.antweb;

import java.util.*;
import java.io.Serializable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public final class Rank implements Serializable {

    private static final Log s_log = LogFactory.getLog(Rank.class);

    public static final String KINGDOM = "kingdom";
    public static final String PHYLUM = "phylum";
    public static final String CLASS = "class";
    public static final String ORDER = "order";
    public static final String FAMILY = "family";
    public static final String SUBFAMILY = "subfamily";
    public static final String TRIBE = "tribe";
    public static final String GENUS = "genus";
    public static final String SUBGENUS = "subgenus";
    public static final String SPECIES = "species";
    public static final String SUBSPECIES = "subspecies";
    public static final String SPECIMEN = "specimen";
    public static final String LOCALITY = "locality";
    

    public Rank() {
    }

    public static boolean isLegit(String rank) {
        int level = getRankLevel(rank);
        return level != 0;
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


    //Don't add a criteria if we have criteria but are asking for a higher rank.
    public static boolean isSubfamilyOrBelow(String rank) {
        int rankInt = Rank.getRankLevel(rank);
        return rankInt <= Rank.getRankLevel(Rank.SUBFAMILY);
    }
    public static boolean isGenusOrBelow(String rank) {
        int rankInt = Rank.getRankLevel(rank);
        return rankInt <= Rank.getRankLevel(Rank.GENUS);
    }
    // if I have a speciesName but am asking for a genus, do not add the speciesName to the critiera.
    public static boolean isSpeciesOrBelow(String rank) {
        int rankInt = Rank.getRankLevel(rank);
        return rankInt <= Rank.getRankLevel(Rank.SPECIES);
    }
    public static boolean isSubspeciesOrBelow(String rank) {
        int rankInt = Rank.getRankLevel(rank);
        return rankInt <= Rank.getRankLevel(Rank.SUBFAMILY);
    }



    public static boolean isValid(String rank) {
      int rankLevel = getRankLevel(rank);
        return rankLevel > 0;
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
        switch (rank) {
            case "Families":
                singularRank = "Family";
                break;
            case "Subfamilies":
                singularRank = "Subfamily";
                break;
            case "Tribes":
                singularRank = "Tribe";
                break;
            case "Genera":
                singularRank = "Genus";
                break;
            case "Species":
                singularRank = "Species";
                break;
            case "Subspecies":
                singularRank = "Subspecies";
                break;
            case "Specimens":
                singularRank = "Specimen";
                break;
            case "Localities":
                singularRank = "Locality";
                break;
            default:
                singularRank = rank;
                break;
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
        if (subfamily != null && subfamily.contains(name)) {
            rank.add("subfamily");
        }

        if (genus != null && genus.contains(name)) {
            rank.add("genus");
        }

        if (species != null && species.contains(name)) {
            rank.add("species");
        }

        if (subspecies != null && subspecies.contains(name)) {
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
        if (Utility.notBlank(subspecies)) return SUBSPECIES;
        if (Utility.notBlank(species)) return SPECIES;
        if (Utility.notBlank(genus)) return GENUS;
        if (Utility.notBlank(subfamily)) return SUBFAMILY;
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
