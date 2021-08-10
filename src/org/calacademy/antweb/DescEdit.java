package org.calacademy.antweb;

import java.util.Date;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public class DescEdit {

    private static Log s_log = LogFactory.getLog(DescEdit.class);

    private String taxonName = null;
    private String code = null;
    private String title = null;
    private String content = null;
    private int editId = 0;
    private Date created = null;
    private int taxonId = 0;
    private boolean isManualEntry = false;
    private int accessGroupId = 0;
    private int accessLoginId = 0;
    private String prettyName = null;
    
    private Login accessLogin = null;
    
    public DescEdit() {
    }
    
    public String getAntPage() {
       if (getCode() != null) {
         return getCode();
       } else {
         return getTaxonName();
       }
    }
    
    public String getAntLink() {
      String base = "<a href=\"" + AntwebProps.getDomainApp();
      if (getCode() == null) {
         return base + "/description.do?taxonName=" + getTaxonName() + "\">" + getTaxonPrettyName() + "</a>";              
      } else {
         return base + "/specimen.do?code=" + getCode() + "\">" + Formatter.initCap(getCode()) + "</a>";              
      }    
    }
    
    public boolean isSpecimen() {
      if (getCode() != null) return true;
      return false;
    }
    
    public String getTaxonName() {
        return this.taxonName;
    }
    public void setTaxonName(String taxonName) {
        this.taxonName = taxonName;
    }

    public String getCode() {
        return this.code;
    }
    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return this.title;
    }
    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return this.content;
    }
    public void setContent(String content) {
        this.content = content;
    }

    public int getEditId() {
        return this.editId;
    }
    public void setEditId(int editId) {
        this.editId = editId;
    }
    public Date getCreated() {
        return this.created;
    }
    public void setCreated(Date created) {
        this.created = created;
    }
    public int getTaxonId() {
        return this.taxonId;
    }
    public void setTaxonId(int taxonId) {
        this.taxonId = taxonId;
    }
    public boolean getIsManualEntry() {
        return this.isManualEntry;
    }
    public void setIsManualEntry(boolean isManualEntry) {
        this.isManualEntry = isManualEntry;
    }
    public int getAccessGroupId() {
        return this.accessGroupId;
    }
    public void setAccessGroupId(int accessGroupId) {
        this.accessGroupId = accessGroupId;
    }
    public int getAccessLoginId() {
        return this.accessLoginId;
    }
    public void setAccessLoginId(int accessLoginId) {
        this.accessLoginId = accessLoginId;
    }
    public Login getAccessLogin() {
        return this.accessLogin;
    }
    public void setAccessLogin(Login accessLogin) {
        this.accessLogin = accessLogin;
    }
    
    public void setTaxonPrettyName(String prettyName) {
        this.prettyName = prettyName;
    }
    public String getTaxonPrettyName() {
        return this.prettyName;
    }

    public static String getPrettyTitle(String title) {
      if (title.equals("taxonomichistory")) return "Taxonomic History";
      if (title.equals("taxonomictreatment")) return "Taxonomic Treatment";
      if (title.equals("taxonomicnotes")) return "Taxonomic Notes";
      if (title.equals("taxanomicnotes")) return "Taxonomic Notes";   // deprecated
      if (title.equals("textauthor")) return "Author";
      if (title.equals("revdate")) return "Rev Date";
      return title.substring(0, 1).toUpperCase() + title.substring(1).toLowerCase();
    }
    
}
