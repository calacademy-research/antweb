package org.calacademy.antweb.curate;

import java.util.*;
import java.sql.*;

import org.apache.struts.action.*;
import javax.servlet.http.*;

import org.apache.struts.action.ActionForm;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;

public final class BioregionMapMgrForm extends ActionForm {

    private static Log s_log = LogFactory.getLog(BioregionMapMgrForm.class);

    protected String taxonName;
    protected boolean isAntarctica = false;
    protected boolean isNeotropical = false;
    protected boolean isAfrotropical = false;
    protected boolean isMalagasy = false;
    protected boolean isAustralasia = false;
    protected boolean isOceania = false;
    protected boolean isIndomalaya = false;
    protected boolean isPalearctic = false;
    protected boolean isNearctic = false;

    private String orderBy = "subfamily";
    
    private boolean isRefresh;

    public BioregionMapMgrForm() {
    }
    
    public String getTaxonName() {
        return taxonName;
    }
    public void setTaxonName(String taxonName) {
        this.taxonName = taxonName;
    }

    public boolean isAntarctica() {
        return isAntarctica;
    }
    public void setIsAntarctica(boolean isAntarctica) {
        this.isAntarctica = isAntarctica;
    }

    public boolean isNeotropical() {
        return isNeotropical;
    }
    public void setIsNeotropical(boolean isNeotropical) {
        //	if (AntwebProps.isDevMode()) AntwebUtil.logStackTrace();
        this.isNeotropical = isNeotropical;
    }

    public boolean isAfrotropical() {
        return isAfrotropical;
    }
    public void setIsAfrotropical(boolean isAfrotropical) {
        this.isAfrotropical = isAfrotropical;
    }

    public boolean isMalagasy() {
        return isMalagasy;
    }
    public void setIsMalagasy(boolean isMalagasy) {
        this.isMalagasy = isMalagasy;
    }

    public boolean isAustralasia() {
        return isAustralasia;
    }
    public void setIsAustralasia(boolean isAustralasia) {
        this.isAustralasia = isAustralasia;
    }

    public boolean isOceania() {
        return isOceania;
    }
    public void setIsOceania(boolean isOceania) {
A.log("setIsOceania() val:" + isOceania);
        this.isOceania = isOceania;
    }    
    
    public boolean isIndomalaya() {
        return isIndomalaya;
    }
    public void setIsIndomalaya(boolean isIndomalaya) {
        this.isIndomalaya = isIndomalaya;
    }
    
    public boolean isPalearctic() {
        return isPalearctic;
    }
    public void setIsPalearctic(boolean isPalearctic) {
        this.isPalearctic = isPalearctic;
    }    
    
    public boolean isNearctic() {
        return isNearctic;
    }
    public void setIsNearctic(boolean isNearctic) {
        this.isNearctic = isNearctic;
    }           

    public boolean isRefresh() {
        return isRefresh;
    }
    public void setIsRefresh(boolean isRefresh) {
        this.isRefresh = isRefresh;
    }           

    public String getOrderBy() {
        return orderBy;
    }
    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }           
    
	public void reset(ActionMapping mapping, HttpServletRequest request) {
      super.reset(mapping, request);
	
	  setIsRefresh(false);
	    
	  isAntarctica = false;
      isNeotropical = false;
      isAfrotropical = false;
      isMalagasy = false;
      isAustralasia = false;
      isOceania = false;
      isIndomalaya = false;
      isPalearctic = false;
      isNearctic = false;	
	}       

    public String getValues() {

      return " Afrotropical:" + isAfrotropical + " Antarctica:" + isAntarctica + " Australasia:" + isAustralasia 
        + " Indomalaya:" + isIndomalaya + " Malagasy:" + isMalagasy + " Nearctic:" + isNearctic 
        + " Neotropical:" + isNeotropical + " Oceania:" + isOceania + " Palearctic:" + isPalearctic;    
    }    
        
    public String toString() {
      return "taxonName:" + getTaxonName() + " values:" + getValues();
    }
}

