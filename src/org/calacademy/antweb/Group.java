package org.calacademy.antweb;

import java.sql.*;
import java.util.ArrayList;

import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.util.*;

import java.util.Comparator;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public class Group implements Comparable {

    private static Log s_log = LogFactory.getLog(Group.class);
    
    private int id;
    private String name;
    private int adminLoginId;  
    private String abbrev;
    //private String adminEmail;
        
    public static int ADMINGROUP = 1;
    public static int TESTGROUP = 25;
    
    // Transient field
    //private Login login;   
    private Upload upload;
    private Timestamp firstUpload;
    private Timestamp lastUpload;
    private int uploadCount = 0;
    private String curatorList;
    private ArrayList<Curator> curators;

/* This can go away, along with Login.getGroups logic, when we stop getting the accessLogin
   from the accessGroup.
   */
/*   
    public Group clone() {
      Group clone = new Group();
      clone.setId(getId());
      clone.setName(getName());
      clone.setAdminLoginId(getAdminLoginId());
      clone.setFirstUploadDate(getFirstUploadDate());
      clone.setLastUploadDate(getLastUploadDate());
      clone.setUploadCount(getUploadCount());
      clone.setCuratorList(getCuratorList());
      clone.setCurators(getCurators());
      return clone;
    }
*/
    
    public Group() {
        super();
    }
    public Group(int id) {
        setId(id);
    }
    
    public void init() {
        name = "";
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

    public String getAbbrev() {
        String retVal = null;
        if (abbrev == null) {
          retVal = "" + getId();
          Login adminLogin = getAdminLogin();
          if (adminLogin != null) retVal = adminLogin.getDisplayName();  
        } else {
          retVal = abbrev;
        }
        return retVal;
    }
    public void setAbbrev(String abbrev) {
        this.abbrev = abbrev;
    }

    public int getAdminLoginId() {
        return adminLoginId;
    }
    public void setAdminLoginId(int adminLoginId) {
        this.adminLoginId = adminLoginId;
    }
    public Login getAdminLogin() {
        return LoginMgr.getLogin(adminLoginId);
    }

    public String getAdminEmail() {
        Login adminLogin = getAdminLogin();
        if (adminLogin != null) return adminLogin.getEmail();
        return "";
    }


    public String getCuratorList() {
      return curatorList;
    } 
    public void setCuratorList(String curatorList) {
      this.curatorList = curatorList;
    }
    
    public void setCurators(ArrayList<Curator> curators) {
      this.curators = curators;
    }
    public ArrayList<Curator> getCurators() {
      return curators;
    }

    public String getCuratorLinks() {
      String links = "";
      int i = 0 ;
      for (Login curator : getCurators()) {
        ++i;
        if (i > 1) {
          links += ", ";
        }
//        links += curator.getDisplayName() + " [<a href='" + AntwebProps.getDomainApp() + "/login.do?userName=" + curator.getName() + "&password=" + curator.getPassword() + "'>Adm login as</a>]";
        links += "<a href='" + AntwebProps.getDomainApp() + "/curator.do?id=" + curator.getId() + "'>" + curator.getDisplayName() + "</a>";

      }
      return links;
    } 
      
    public String getLink() {
      String link = "";
      link += "<a href='" + AntwebProps.getDomainApp() + "/group.do?id=" + getId() + "'>" + getName() + "</a>";
      //A.log("getLink() link:" + link);
      return link;
    } 
    public String getLinkAbbrev() {
      String link = "";
      link += "<a href='" + AntwebProps.getDomainApp() + "/group.do?id=" + getId() + "'>" + getAbbrev() + "</a>";
      //A.log("getLink() link:" + link);
      return link;
    } 
            
    public Upload getLastUpload() {
      return this.upload;
    }    
    public void setLastUpload(Upload upload) {
      this.upload = upload;
    }
    
    public Timestamp getFirstUploadDate() {
      return this.firstUpload;
    }
    public void setFirstUploadDate(Timestamp firstUpload) {
      this.firstUpload = firstUpload;
    }

    public Timestamp getLastUploadDate() {
      return this.lastUpload;
    }
    public void setLastUploadDate(Timestamp lastUpload) {
      this.lastUpload = lastUpload;
    }

    public int getUploadCount() {
      return this.uploadCount;
    }
    public void setUploadCount(int uploadCount) {
      this.uploadCount = uploadCount;
    }    


    public static Comparator<Group> getGroupNameComparator = new Comparator<>() {

        public int compare(Group a1, Group a2) {
            String name1 = a1.getName().toUpperCase();
            String name2 = a2.getName().toUpperCase();

            //ascending order
            return name1.compareTo(name2);

            //descending order
            //return name2.compareTo(name1);
        }
    };
    
     public int compareTo(Object o) throws ClassCastException {
         Group compareGroup = (Group) o;
         int compareId = compareGroup.getId();
         int result = getId() - compareId;
         s_log.debug("compareTo() compareId:" + compareId + " id:" + getId());
         return result;
     }

     public boolean isCurator() {
       return (getId() >= 0);
     }
     
    public boolean equals(Object o) {
        Group compareGroup = (Group) o;
        int compareId = compareGroup.getId(); 
        s_log.debug("equals() compareId:" + compareId + " id:" + getId());
        return compareGroup.getId() == getId();
    }     

    public String toString() {
      return name;
      //return "Group id:" + id + " name:" + name + " adminLoginId:" + adminLoginId + " transientLogin:" + login;
    }

}
