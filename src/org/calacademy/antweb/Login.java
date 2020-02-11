package org.calacademy.antweb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Date;

import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public class Login implements Comparable {

    private static Log s_log = LogFactory.getLog(Login.class);
    
    public static int BRIAN = 1;
    public static int MARK = 22;
    public static int MICHELE = 23;
    public static int TEST_LOGIN = 50;
    
    private int id;
    private String firstName;
    private String lastName;
    private String email;
    private String name;
    private String password;
    
    private int groupId;
    private boolean isAdmin;
    private boolean isUploadSpecimens;
    private boolean isUploadImages;
  
    private ArrayList<SpeciesListable> projects;
    private ArrayList<SpeciesListable> countries;  // These are only countries!  No adm1 due to rollup.
    private ArrayList<SpeciesListable> geolocales; // Contains adm1.  Nested ordering.

    private Date created;

    public static String MUST_LOGIN_MESSAGE = "You must log in to access this functionality.  You may create an account, or log in anonymously, <a href='" + AntwebProps.getDomainApp() + "/login.do'>here</a>.";    
    
    public Login() {
        super();
    }
    
    public void init() {
        setEmail("");
        setName("");
        setPassword("");
        setFirstName("");
        setLastName("");
        setProjects(new ArrayList<SpeciesListable>());
        setCountries(new ArrayList<SpeciesListable>());
        setGeolocales(new ArrayList<SpeciesListable>());
    }
    public String getFullName() {
      return getFirstName() + " " + getLastName();
    }
    public String getFirstName() {
        return firstName;
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    public String getLastName() {
        return lastName;
    }
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    
    public int getGroupId() {
        return groupId;
    }
    public void setGroupId(int id) {
        this.groupId = id;
    }
    public Group getGroup() {
        return GroupMgr.getGroup(groupId);
    }
    
    public Boolean isAdmin() {
        return this.isAdmin;
    }
    public void setIsAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }
    
    public boolean isCurator() {
        if (isAdmin()) return true;
        
        //if (getProjects() != null && getProjects().size() > 0) return true;
        //if (isUploadSpecimens() || isUploadImages()) return true;
        
        if (getGroupId() > 0) return true;
        return false;
    }
    
    public boolean isDeveloper() {
        // This should probably be a field in the database.  But this is simpler.
        if (getId() == 22) return true;
        return false;
    }
    
    public Boolean isUploadSpecimens() {
        return this.isUploadSpecimens;
    }
    public void setIsUploadSpecimens(boolean isUploadSpecimens) {
      this.isUploadSpecimens = isUploadSpecimens;
    }
    
    public Boolean isUploadImages() {
        return this.isUploadImages;
    }
    public void setIsUploadImages(boolean isUploadImages) {
      this.isUploadImages = isUploadImages;
    }
    
    
    public ArrayList<SpeciesListable> getProjects() {
        return projects;
    }
    public void setProjects(ArrayList<SpeciesListable> projects) {
        this.projects = projects;
    }
    // Convenience method:
    public ArrayList<String> getProjectNames() {
        ArrayList<String> projectNames = new ArrayList<String>();
		if (getProjects() != null) {
			for (SpeciesListable project : getProjects()) {
			  projectNames.add(project.getName());
			}
        }
        return projectNames;
    }    
    public String getProjectNamesStr() {
        String namesStr = "";
        int i = 0;
        ArrayList<SpeciesListable> projects = getProjects();
        if (projects == null) return "";
		for (SpeciesListable project : projects) {
          ++i;
          if (i > 1) namesStr += ", ";
		  namesStr += "<a href='" + AntwebProps.getDomainApp() + "/project.do?name=" + project.getName() + "'>" + project.getTitle() + "</a>";
		}
        return namesStr;
    }
    public String getProjectNamesShort() {
        String namesStr = "";
        int i = 0;
        ArrayList<SpeciesListable> projects = getProjects();
        if (projects == null) return "";
		for (SpeciesListable project : projects) {
          ++i;
          if (i > 1) namesStr += ", ";
		  namesStr += "<a href='" + AntwebProps.getDomainApp() + "/project.do?name=" + project.getName() + "'>" + project.getTitle() + "</a>";
          if (i >= 3) {
            namesStr += "...";
		    return namesStr;
		  }
		}
        return namesStr;
    }
    
    // This is used in the Admin interface where adm1 are not specified.
    public ArrayList<SpeciesListable> getCountries() {
        return countries;
    }
    public void setCountries(ArrayList<SpeciesListable> countries) {
        this.countries = countries;
    }
    // Convenience method:
    public ArrayList<String> getCountryNames() {
        ArrayList<String> countryNames = new ArrayList<String>();
        ArrayList<SpeciesListable> countries = getCountries();
        if (countries == null) {
          return countryNames;
        }
        for (SpeciesListable country : countries) {
          countryNames.add(country.getName());
        }
        return countryNames;
    }

    public ArrayList<SpeciesListable> getGeolocales() {
        return geolocales;
    }
    public void setGeolocales(ArrayList<SpeciesListable> geolocales) {
        this.geolocales = geolocales;
    }    

    // This is used in the drop down lists of the curate page and the Species List Tool
    public ArrayList<SpeciesListable> getSpeciesListList() {
      ArrayList<SpeciesListable> speciesLists = new ArrayList<SpeciesListable>();
      speciesLists.addAll(getProjects());
      speciesLists.addAll(getGeolocales());
      return speciesLists;    
    }
    
    public Date getCreated() {
        return this.created;
    }
    public void setCreated(Date created) {
        this.created = created;
    }

     public int compareTo(Object o) throws ClassCastException {
         Login compareLogin = (Login)o;
         int compareId = compareLogin.getId();
         int result = getId() - compareId;
         return result;
     }
     

    public String toString() {
      return "Login id:" + id + " name:" + name + " email:" + email + " groupId:" + groupId 
        + " isAdmin:" + isAdmin + " isUploadImages:" + isUploadImages + " isUploadSpecimens:" + isUploadSpecimens 
        + " projects:" + projects;
    }


     public String getDisplayNameEmailFirst() {
       if (email != null && !"null".equals(email) && !"".equals(email)) return email;
       if ((this.name != null) && (!this.name.equals(""))) return this.name;
       String displayName = "";
       if (getFirstName() != null && !"".equals(getFirstName()))
         displayName += getFirstName();
       if (getLastName() != null && !"".equals(getLastName()))
         displayName += " " + getLastName();
       return displayName;
     }    
         
     public String getDisplayName() {
       String displayName = "";
       if (getFirstName() != null && !"".equals(getFirstName()))
         displayName += getFirstName();
       if (getLastName() != null && !"".equals(getLastName()))
         displayName += " " + getLastName();
       if ("".equals(displayName)) {
         displayName = toDisplayString();
       }
       displayName = Formatter.initCap(displayName);
       //A.log("getDisplayName() displayName:" + displayName);
       return displayName;
     }    
     
     public String getLink() {
       String link = "";
       link += "<a href='" + AntwebProps.getDomainApp() + "/login.do?id=" + getId() + "'>" + getDisplayName() + "</a>";
       //A.log("getLink() link:" + link);
       return link;
     }     
    
     public String getGroupLink() {
       String link = "";
       Group group = getGroup();
       if (group != null) link = group.getLink();
       return link;
     }
         
     public String toDisplayString() {
         // Display name how the user likes.  If they enter a username, display it, otherwise email.
         if ((this.name != null) && (!this.name.equals(""))) {
           return this.name;
         } else {
           return this.email;
         }
     }

}
