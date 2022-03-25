package org.calacademy.antweb.curate.speciesList;

import java.util.*;

import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.util.*;


public class SpeciesListToolProps extends SpeciesListToolForm {

    private ArrayList<String> noPassWorldantsSpeciesList;
    private ArrayList<String> refListList;
    private ArrayList<String> refListSubfamilies;

    private String refSpeciesListParams;

    private ArrayList<Taxon> mapSpeciesList1;
    private ArrayList<Taxon> mapSpeciesList2;
    private ArrayList<Taxon> mapSpeciesList3;

    private ArrayList<Taxon> refSpeciesList;
    private ArrayList<Taxon> sumSpeciesList;
    
    private ArrayList<String> oldChosenList1;
    private ArrayList<String> oldChosenList2;
    private ArrayList<String> oldChosenList3;

    private ArrayList<Taxon> advSearchTaxa;
    

    public String getRefSpeciesListParams() {
	    return refSpeciesListParams;
    }
    public void setRefSpeciesListParams(String params) {
	    if ("null".equals(params)) return;
    	this.refSpeciesListParams = params;
    }  
    
    public ArrayList<String> getNoPassWorldantsSpeciesList() {
      return noPassWorldantsSpeciesList;
    }
    public void setNoPassWorldantsSpeciesList(ArrayList<String> list) {
      noPassWorldantsSpeciesList = list;
    }

    public ArrayList<String> getRefListList() {
      return refListList;
    }
    public void setRefListList(ArrayList<String> list) {
      refListList = list;
    }

    public ArrayList<String> getRefListSubfamilies() {
      //A.log("getRefListSubfamilies:" + refListSubfamilies);
      return refListSubfamilies;
    }
    public void setRefListSubfamilies(ArrayList<String> list) {
      refListSubfamilies = list;
    }
    
    public ArrayList<Taxon> getMapSpeciesList1() {
      return mapSpeciesList1;
    }
    public void setMapSpeciesList1(ArrayList<Taxon> list) {
      mapSpeciesList1 = list;
    }
    public ArrayList<Taxon> getMapSpeciesList2() {
      return mapSpeciesList2;
    }
    public void setMapSpeciesList2(ArrayList<Taxon> list) {
      mapSpeciesList2 = list;
    }
    public ArrayList<Taxon> getMapSpeciesList3() {
      return mapSpeciesList3;
    }
    public void setMapSpeciesList3(ArrayList<Taxon> list) {
      mapSpeciesList3 = list;
    }

    public ArrayList<Taxon> getRefSpeciesList() {
      return refSpeciesList;
    }
    public void setRefSpeciesList(ArrayList<Taxon> list) {
      refSpeciesList = list;
    }

    public ArrayList<Taxon> getSumSpeciesList() {
      return sumSpeciesList;
    }
    public void setSumSpeciesList(ArrayList<Taxon> list) {
      sumSpeciesList = list;
    }
    
    public ArrayList<String> getOldChosenList1() {
      return oldChosenList1;
    }
    public void setOldChosenList1(ArrayList<String> list) {
      oldChosenList1 = list;
    }
    public ArrayList<String> getOldChosenList2() {
      return oldChosenList2;
    }
    public void setOldChosenList2(ArrayList<String> list) {
      oldChosenList2 = list;
    }
    public ArrayList<String> getOldChosenList3() {
      return oldChosenList3;
    }
    public void setOldChosenList3(ArrayList<String> list) {
      oldChosenList3 = list;
    }

    public ArrayList<Taxon> getAdvSearchTaxa() {
      return advSearchTaxa;    
    }
    public void setAdvSearchTaxa(ArrayList<Taxon> advSearchTaxa) {
      this.advSearchTaxa = advSearchTaxa;
    }
    
    
    public void persist(SpeciesListToolForm toolForm) {
        super.persist(toolForm);

		// And to have it in a pretty way.  
		String refSpeciesListParams = getRefSpeciesListParams(toolForm);
		if (refSpeciesListParams != null)
		  setRefSpeciesListParams(refSpeciesListParams);        		  
    }
    
    public String getDisplayRefSpeciesListParams() {
      int MAX = 35;  // Maximum length of display name
      String refSpeciesListParams = getRefSpeciesListParams();
      if (refSpeciesListParams == null) return "";
      if (refSpeciesListParams.length() > MAX) refSpeciesListParams = refSpeciesListParams.substring(0 , 24) + "...";
      return refSpeciesListParams;
    }    
    public String getRefSpeciesListParams(SpeciesListToolForm toolForm) {
        if (toolForm.getRefSpeciesListName() != null && !"".equals(toolForm.getRefSpeciesListName())) return toolForm.getRefSpeciesListName();
        String params = "";
        if (toolForm.getName() != null && !"".equals(toolForm.getName())) params += "," + toolForm.getName();
        if (toolForm.getSubfamily() != null && !"".equals(toolForm.getSubfamily())) params += "," + toolForm.getSubfamily();
        if (toolForm.getGenus() != null && !"".equals(toolForm.getGenus())) params += "," + toolForm.getGenus();
        if (toolForm.getSpecies() != null && !"".equals(toolForm.getSpecies())) params += "," + toolForm.getSpecies();
        if (toolForm.getSubspecies() != null && !"".equals(toolForm.getSubspecies())) params += "," + toolForm.getSubspecies();
        
        if (toolForm.getBioregion() != null && !"".equals(toolForm.getBioregion())) params += "," + toolForm.getBioregion();
        if (toolForm.getCountry() != null && !"".equals(toolForm.getCountry())) params += "," + toolForm.getCountry();
        if (toolForm.getAdm1() != null && !"".equals(toolForm.getAdm1())) params += "," + toolForm.getAdm1();
        if (toolForm.getAdm2() != null && !"".equals(toolForm.getAdm2())) params += "," + toolForm.getAdm2();
        if (toolForm.getLocalityName() != null && !"".equals(toolForm.getLocalityName())) params += "," + toolForm.getLocalityName();
        if (toolForm.getSpecimenCode() != null && !"".equals(toolForm.getSpecimenCode())) params += "," + toolForm.getSpecimenCode();
        if (toolForm.getLocatedAt() != null && !"".equals(toolForm.getLocatedAt())) params += "," + toolForm.getLocatedAt();        
        
        if (toolForm.getGeoLogId() != 0) params += ",geoLogId=" + toolForm.getGeoLogId();
        if (toolForm.getProjLogId() != 0) params += ",projLogId=" + toolForm.getProjLogId();      

        if (params.length() > 1) {
          params = params.substring(1);
          return params;
        }
        return null;
    }  

    public void resetSearch() {
      super.resetSearch();
      setRefSpeciesListParams("");
      setAdvSearchTaxa(null);
    }

    public String getLinkParams() {
      return super.getLinkParams();
    }

    public String getPermaLink() {
      return AntwebProps.getDomainApp() + "/speciesListTool.do?" + getLinkParams();
    }

    public String getPermaLinkTag() {
      String tag = "<a href='" + getPermaLink() + "'>[permalink]</a>";
      return tag;
    }
            
    public String toString() {
      String returnStr = super.toString()
        + " sumSpeciesList:" + (getSumSpeciesList() == null ? "null" : getSumSpeciesList().size())
        + " refSpeciesList:" + (getRefSpeciesList() == null ? "null" : getRefSpeciesList().size())
        + " advSearchTaxa:" + (getAdvSearchTaxa() == null ? "null" : getAdvSearchTaxa().size())
        + " mapSpeciesList1:" + (getMapSpeciesList1() == null ? "null" : getMapSpeciesList1().size())
        + " mapSpeciesList2:" + (getMapSpeciesList2() == null ? "null" : getMapSpeciesList2().size())
        + " mapSpeciesList3:" + (getMapSpeciesList3() == null ? "null" : getMapSpeciesList3().size())
        + " oldChosenList1:" + (getOldChosenList1() == null ? "null" : getOldChosenList1().size())
        + " oldChosenList2:" + (getOldChosenList2() == null ? "null" : getOldChosenList2().size())
        + " oldChosenList3:" + (getOldChosenList3() == null ? "null" : getOldChosenList3().size())
        + " refListSubfamilies:" + (getRefListSubfamilies() == null ? "null" : getRefListSubfamilies().size())
        + " refListList:" + (getRefListList() == null ? "null" : getRefListList().size())
        
        + " noPassWorldantsSpeciesList:" + (getNoPassWorldantsSpeciesList() == null ? "null" : getNoPassWorldantsSpeciesList().size())
        ;
        
      return returnStr;   
    }    
}




