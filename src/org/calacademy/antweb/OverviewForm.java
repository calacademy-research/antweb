package org.calacademy.antweb;


import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class OverviewForm extends DescriptionForm {

    private static Log s_log = LogFactory.getLog(OverviewForm.class);

    private int id = 0;
    private String name;
    private String title;
    private String code;
    private String action;
    private String country;
    private String adm1Name;
    private String countryName;
    private String subregionName;
    private String regionName;

    private String placeName;

    public void reset(ActionMapping mapping, HttpServletRequest request) {
        this.name = null;
    }

    public int getId() {
        return this.id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public String getName() {
        return (this.name);
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return (this.code);
    }
    public void setCode(String code) {
        this.code = code;
    }

    public String getMuseumCode() {
        return getCode();
    }
    public void setMuseumCode(String code) {
        setCode(code);
    }

    public String getTitle() {
        return (this.title);
    }
    public void setTitle(String title) {
        this.title = title;
    }

    public String getAction() {
        return (this.action);
    }
    public void setAction(String action) {
        this.action = action;
    }

    public String getCountry() {
        return (this.country);
    }
    public void setCountry(String country) {
        this.country = country;
    }   

    public String getAdm1Name() {
        return (this.adm1Name);
    }
    public void setAdm1Name(String adm1Name) {
        this.adm1Name = adm1Name;
    }     
    public String getCountryName() {
        return (this.countryName);
    }
    public void setCountryName(String countryName) {
        this.countryName = countryName;
    } 
    public String getSubregionName() {
        return (this.subregionName);
    }
    public void setSubregionName(String subregionName) {
        this.subregionName = subregionName;
    } 
    public String getRegionName() {
        return (this.regionName);
    }
    public void setRegionName(String regionName) {
        this.regionName = regionName;
    } 
        
    public String getPlaceName() {
        return (this.placeName);
    }
    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }    
}
