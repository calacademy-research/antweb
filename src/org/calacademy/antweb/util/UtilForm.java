package org.calacademy.antweb.util;

import org.apache.struts.action.ActionForm;
import org.apache.struts.upload.FormFile;

public class UtilForm extends ActionForm {
  protected String action;
  protected String field;
  protected int num;
  protected int id;
  protected int groupId;
  protected String name;
  protected String code;
  protected String country;
  protected String taxonName;
  protected String prop;
  protected String param;
  protected String param2;
  protected String text;
  protected String reload;
  protected int secureCode;
      
  public String getField() {
	return field;
  }
  public void setField(String field) {
	this.field = field;
  }

  public String getAction() {
	return action;
  }
  public void setAction(String action) {
	this.action = action;
  }

  public int getNum() {
    return num;
  }
  public void setNum(int num) {
    this.num = num;
  }
   
  public int getId() {
    return id;
  }
  public void setId(int id) {
    this.id = id;
  }
   
  public int getGroupId() {
    return groupId;
  }
  public void setGroupId(int groupId) {
    this.groupId = groupId;
  }
       
  public String getName() {
    return name;
  } 
  public void setName(String name) {
    this.name = name;
  }  

  public String getCode() {
    return code;
  } 
  public void setCode(String code) {
    this.code = code;
  }  

  public String getCountry() {
    return country;
  } 
  public void setCountry(String country) {
    this.country = country;
  }
  

  public String getTaxonName() {
    return taxonName;
  } 
  public void setTaxonName(String taxonName) {
    this.taxonName = taxonName;
  }
  public String getProp() {
    return prop;
  } 
  public void setProp(String prop) {
    this.prop = prop;
  }  
    
  
  public String getParam() {
    return param;
  } 
  public void setParam(String param) {
    this.param = param;
  }  
  public String getParam2() {
    return param2;
  } 
  public void setParam2(String param2) {
    this.param2 = param2;
  }  
  
  public String getText() {
    return text;
  }
  public void setText(String text) {
    this.text = text;
  }

  public String getReload() {
    return reload;
  }
  public void setReload(String reload) {
    this.reload = reload;
  }

  public int getSecureCode() {
    return secureCode;
  }
  public void setSecureCode(int secureCode) {
    this.secureCode = secureCode;
  }

  public String toString() {
    String retVal = "";
    
    if (action != null) retVal += "action:" + action + " ";
    if (field != null) retVal += "field:" + field + " ";
    if (num != 0) retVal += "num:" + num + " ";
    if (id != 0) retVal += "id:" + id + " ";
    if (groupId != 0) retVal += "groupId:" + groupId + " ";
    if (code != null) retVal += "code:" + code + " ";
    if (country != null) retVal += "country:" + country + " ";
    if (taxonName != null) retVal += "taxonName:" + taxonName + " ";
    if (prop != null) retVal += "prop:" + prop + " ";
    if (param != null) retVal += "param:" + param + " ";
    if (param2 != null) retVal += "param2:" + param2 + " ";
    if (secureCode != 0) retVal += "secureCode:" + secureCode + " ";
    return retVal;
  }
}


