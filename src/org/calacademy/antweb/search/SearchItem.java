package org.calacademy.antweb.search;

import org.calacademy.antweb.*;

import java.io.Serializable;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public class SearchItem extends Item implements Serializable {

    private static Log s_log = LogFactory.getLog(SearchItem.class);

    // These are not found in ResultItem
	private SearchItem synonym;
	private String typeOriginalCombination;
	public SearchItem getSynonym() {
		return synonym;
	}
	public void setSynonym(SearchItem item) {
		synonym = item;
	}

	public String getTypeOriginalCombination() {
		return typeOriginalCombination;
	}
	public void setTypeOriginalCombination(String string) {
		typeOriginalCombination = string;
	}
	
    public SearchItem (String name, String code, String family, String subfamily, String genus, String species, String subspecies,
		String rank, SearchItem synonym, boolean hasImages, String type, String favoriteImage,
		String typeOriginalCombination, String status, String country, String adm1, String adm2) {   //int valid, 
      
      // Called by BayAreaSearch and ImagePickSearch.  Searches that do not need specimen level data.
		this.name = name;
		this.code = code;
		this.family = family;		
		this.subfamily = subfamily;
		this.genus = genus;
		this.species = species;
		this.subspecies = subspecies;
		this.rank = rank;
		this.synonym = synonym;
		this.hasImages = hasImages;
		this.type = type;
		this.favoriteImage = favoriteImage;
		this.typeOriginalCombination = typeOriginalCombination;
		//this.valid = valid;
		this.status = status;
		this.country=country;
		this.adm1=adm1;
		this.adm2=adm2;
    }



	public String getNameOfRank(String rank) {
		if (rank.equals("family")) {
			return family;
	    } else if (rank.equals("subfamily")) {
			return subfamily;
		} else if (rank.equals("genus")) {
			return genus;
		} else if (rank.equals("species")) {
			return species;
		} else {
			return null;
		}
	}

	public String toString() {
		return name;
	}
	
	public String toDebugString() {
		return "SearchItem name:" + name + " group:" + group + " groupName:" + groupName;
	}
	
}
