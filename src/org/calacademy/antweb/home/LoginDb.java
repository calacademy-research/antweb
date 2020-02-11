package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import javax.servlet.http.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.upload.*;

public class LoginDb extends AntwebDb {
    
    private static Log s_log = LogFactory.getLog(LoginDb.class);
        
    public LoginDb(Connection connection) {
      super(connection);
    }

    public void refreshLogin(HttpSession session) throws SQLException {
        Login accessLogin = (Login) session.getAttribute("accessLogin");
        Login login = getLogin(accessLogin.getId());
        session.setAttribute("thisLogin", login);
        session.setAttribute("accessLogin", login);
    }


    public Login getLogin(int id) throws SQLException {
        Login login = null;
        if (id > 0) {
            String theQuery = "select * from login where id = " + id;
            Statement stmt = null;
            ResultSet rset = null;
            try {
                stmt = DBUtil.getStatement(getConnection(), "getLogin()");     
                rset = stmt.executeQuery(theQuery);                
                login = instantiateLogin(rset);
            } finally {
                DBUtil.close(stmt, rset, this, "getLogin()");
            }
        }
        return login;
    }    
            
    public Curator getCurator(int id) throws SQLException {
        Curator curator = null;
        if (id > 0) {
            String theQuery = "select * from login where id = " + id;
            Statement stmt = null;
            ResultSet rset = null;
            try {
                stmt = DBUtil.getStatement(getConnection(), "getCurator()");            
                rset = stmt.executeQuery(theQuery);                
                curator = instantiateCurator(rset);
            } finally {
                DBUtil.close(stmt, rset, this, "getCurator()");
            }
        }
        return curator;
    }    
            
    public Login getLoginByName(String name) throws SQLException {
        Login login = null;
        if (name != null) {
            name = DBUtil.escapeQuotes(name);
            String theQuery = "select * from login where name = '" + name + "'";
            Statement stmt = null;
            ResultSet rset = null;
            try {
                stmt = DBUtil.getStatement(getConnection(), "getLoginByName()");            
                rset = stmt.executeQuery(theQuery);
                login = instantiateLogin(rset);
            } finally {
                DBUtil.close(stmt, rset, this, "getLoginByName()");
            }
        }
        return login;
    }

    public Login getLoginByEmail(String email) throws SQLException {
        Login login = null;
        if (email != null) {
            String theQuery = "select * from login where email = '" + email + "'";
            Statement stmt = null;
            ResultSet rset = null;
            try {
                stmt = DBUtil.getStatement(getConnection(), "getLoginByEmail()");            
                rset = stmt.executeQuery(theQuery);
                //A.log("getLoginByEmail() 1 email:" + email + " query:" + theQuery);                
                login = instantiateLogin(rset);
                //A.log("getLoginByEmail() 2 email:" + email + " login:" + login);                
            } finally {
              DBUtil.close(stmt, rset, this, "getLoginByEmail()");
            }
        }
        return login;
    }
    
    public ArrayList<Login> getAllLogins() throws SQLException {          
        ArrayList<Login> loginList = new ArrayList<Login>();
        String theQuery = "select id from login";
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getAllLogins()");
            rset = stmt.executeQuery(theQuery);
        
            while (rset.next()) {
                Login login = getLogin(rset.getInt("id"));
                loginList.add(login);
            }
            Collections.sort(loginList);  
        } finally {
            DBUtil.close(stmt, rset, this, "getAllLogins()");
        }
        return loginList;      
    }

    public ArrayList<Curator> getAllCurators() throws SQLException {          
        ArrayList<Curator> curatorList = new ArrayList<Curator>();
        //String theQuery = "select id from login where is_upload_images = 1 or is_upload_images = 1;"; // or  + " and group_id > 0"
        String theQuery = "select id from login where group_id > 0;"; // or  + " and group_id > 0"
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getAllCurators()");
            rset = stmt.executeQuery(theQuery);
            // A.log("getAllCurators() theQuery:" + theQuery);
            while (rset.next()) {
                Curator curator = getCurator(rset.getInt("id"));
                curatorList.add(curator);
                if (curator.getId() == 1752) A.log("getAllCurators() curator Gibb:" + curator + " uploadSpecimens:" + curator.isUploadSpecimens());
            }
            Collections.sort(curatorList);  
        } finally {
            DBUtil.close(stmt, rset, this, "getAllCurators()");
        }
        return curatorList;      
    }

    private Curator instantiateCurator(ResultSet rset)
      throws SQLException {
        Curator curator = new Curator();
        curator = (Curator) instantiate(curator, rset);

        setUploadCounts(curator);
      
        // See getImageUploadCounter() below. Called at startup of LoginMgr to set the following:                
        setImageUploadCount(curator);  //LoginMgr.getImageUploadCount(curator));
        //A.log("instantiateFromResultSet() curator:" + curator.getId() + " set imageUploadCount:" + curator.getImageUploadCount());
        setImagesUploadedCount(curator);
      
        setDescEditCounts(curator);
        
        return curator;
    }
        
    private Login instantiateLogin(ResultSet rset)
      throws SQLException {
        Login login = new Login();
        login = instantiate(login, rset);
        if (login.getId() == 0) return null;
        return login;
    }
    
    // The login could be a Curator.
    private Login instantiate(Login login, ResultSet rset)
      throws SQLException {
        //A.log("instantiate() BEFORE login:" + login + " rset:" + rset);            

        while (rset.next()) {
            login.setId(rset.getInt("id"));
            //A.log("instantiate() INSIDE id:" + login.getId());            
            login.setName(rset.getString("name"));
            login.setPassword(rset.getString("password"));
            login.setFirstName(rset.getString("first_name"));
            login.setLastName(rset.getString("last_name"));
            login.setEmail(rset.getString("email"));
            login.setCreated(rset.getDate("created"));
            //A.log("instantiateLogin() created:" + login.getCreated());
            login.setIsAdmin(rset.getBoolean("is_admin"));
            int groupId = rset.getInt("group_id");
            login.setGroupId(groupId);

            //curator.getGroup().setCurator(curator);  // backwards, but allows code to remain unchanged.                    
            login.setIsUploadSpecimens(rset.getBoolean("is_upload_specimens"));
            login.setIsUploadImages(rset.getBoolean("is_upload_images"));            
        }    
        //A.log("instantiate() login:" + login);
        if (login.isCurator()) {
            ProjectDb projectDb = new ProjectDb(getConnection());
            login.setProjects(projectDb.fetchSpeciesLists(login));

            GeolocaleDb geolocaleDb = new GeolocaleDb(getConnection());
            login.setCountries(geolocaleDb.fetchSpeciesLists(login, false));
            //A.log("instantiateFromResultSet() countries:" + curator.getCountries().size());
        
            login.setGeolocales(geolocaleDb.fetchSpeciesLists(login));
            //A.log("instantiateFromResultSet() geolocales:" + curator.getGeolocales()); //.size()
        } 

        //A.log("instantiate() AFTER login:" + login + " rset:" + rset);            

        return login;
    }    

    public void setImageUploadCount(Curator curator) {

      //  select count(*), upload_date, group_concat(distinct access_group) from image i, specimen s where i.image_of_id = s.code group by upload_date order by group_concat(distinct access_group);
      // Perhaps the best we can do. Not having uploader ID. Trusting that the owner of the specimen is the uploader of the image.
      
      String query = "select count(*) count from image_upload where curator_id = " + curator.getId();
      
      ResultSet rset = null;
      Statement stmt = null;
      try {

        stmt = DBUtil.getStatement(getConnection(), "setImageUploadCount()");
        stmt.executeQuery(query);
        rset = stmt.getResultSet();

        while (rset.next()) {
            int count = rset.getInt("count");
            curator.setImageUploadCount(count);
        }
      } catch (SQLException e) {
          s_log.error("setImageUploadCount() e:" + e + " query:" + query);
      } finally {
        DBUtil.close(stmt, rset, "LoginDb", "setImageUploadCount()");
      }
    }        
    
    public void setImagesUploadedCount(Curator curator) {

      //  select count(*), upload_date, group_concat(distinct access_group) from image i, specimen s where i.image_of_id = s.code group by upload_date order by group_concat(distinct access_group);
      // Perhaps the best we can do. Not having uploader ID. Trusting that the owner of the specimen is the uploader of the image.
      
      String query = "select count(*) count from image_upload, image_uploaded where image_upload.id = image_uploaded.image_upload_id and curator_id =" + curator.getId();
      
      ResultSet rset = null;
      Statement stmt = null;
      try {

        stmt = DBUtil.getStatement(getConnection(), "setImagesUploadedCount()");
        stmt.executeQuery(query);
        rset = stmt.getResultSet();

        while (rset.next()) {
            int count = rset.getInt("count");
            curator.setImagesUploadedCount(count);
        }
      } catch (SQLException e) {
          s_log.error("setImagesUploadedCount() e:" + e + " query:" + query);
      } finally {
        DBUtil.close(stmt, rset, "LoginDb", "setImagesUploadedCount()");
      }
    }            
    
    public void setUploadCounts(Curator curator) {

      String query = "select count(*) uploadCount from upload where login_id = " + curator.getId();    
      ResultSet rset = null;
      Statement stmt = null;
      try {

        UploadDb uploadDb = new UploadDb(getConnection());
        curator.setLastUpload(uploadDb.getLastUploadByLogin(curator.getId()));

        stmt = DBUtil.getStatement(getConnection(), "getUploadCounts()");
        stmt.executeQuery(query);
        rset = stmt.getResultSet();

        while (rset.next()) {
            curator.setSpecimenUploadCount(rset.getInt("uploadCount"));
        }
      } catch (SQLException e) {
          s_log.error("getUploadCounts() e:" + e + " query:" + query);
      } finally {
        DBUtil.close(stmt, rset, "LoginDb", "getUploadCounts()");
      }
    }    

    public void setDescEditCounts(Curator curator) {
      int count = 0;
      
      String query = "select count(*) count from description_edit where access_login = " + curator.getId();    
      ResultSet rset = null;
      Statement stmt = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getDescEditCounts()");
        stmt.executeQuery(query);
        rset = stmt.getResultSet();

        while (rset.next()) {
            count = rset.getInt("count");
        }
      } catch (SQLException e) {
          s_log.error("getDescEditCounts() e:" + e + " query:" + query);
      } finally {
        DBUtil.close(stmt, rset, "LoginDb", "getDescEditCounts()");
      }

      query = "select count(*) count from description_hist where access_login = " + curator.getId();    
      try {
        stmt = DBUtil.getStatement(getConnection(), "getDescEditCounts()");
        stmt.executeQuery(query);
        rset = stmt.getResultSet();
        while (rset.next()) {
            count += rset.getInt("count");
        }
      } catch (SQLException e) {
          s_log.error("getDescEditCounts() e:" + e + " query:" + query);
      } finally {
        DBUtil.close(stmt, rset, "LoginDb", "getDescEditCounts()");
      }

      curator.setDescEditCount(count);
    }        

    // Doesn't really need curators. Logins are fetched more quickly.
    public ArrayList<Curator> getCurators(int groupId) throws SQLException {          
        ArrayList<Curator> curators = new ArrayList<Curator>();
        String theQuery = "select id from login where group_id = " + groupId;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = getConnection().createStatement();
            rset = stmt.executeQuery(theQuery);
        
            while (rset.next()) {
                int id = rset.getInt("id");
                Curator curator = getCurator(id);
                curators.add(curator);
            } 
        } finally {
            DBUtil.close(stmt, rset, this, "getCurators()");
        }
        return curators;      
    }

    public HashMap<Integer, Login> getCuratorMap() throws SQLException {          
        HashMap<Integer, Login> curators = new HashMap<Integer, Login>();
        String theQuery = "select id from login where group_id >= 0";
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = getConnection().createStatement();
            rset = stmt.executeQuery(theQuery);
        
            while (rset.next()) {
                int id = rset.getInt("id");
                Login login = getLogin(id);
                curators.put(new Integer(id), login);
            } 
        } finally {
            DBUtil.close(stmt, rset, this, "getCuratorMap()");
        }
        return curators;      
    }

    public int getNewLoginId() {
        int newLoginId = 0;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            String theQuery = "select max(id) as maxid from login";
            stmt = getConnection().createStatement();
            rset = stmt.executeQuery(theQuery);
            int max = 0;
            while (rset.next()) {
                max = rset.getInt("maxid");
            }
            if (max > 0) {
                newLoginId = max + 1;
            }  
        } catch (SQLException e) {
            s_log.error("getNextLoginId() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, this, "getNewLoginId()");
        }
        return newLoginId;    
    }
    
    public Login createAccount(String name, String password) throws SQLException, AntwebException {
      // This is used by web users...
      Login login = new Login();
      login.setId(getNewLoginId());
      login.setName(name);
      login.setFirstName("");
      login.setLastName("");
      login.setPassword(password);
      login.setGroupId(-1);
      login.setEmail("");
      saveLogin(login);
      return login;
    }
  
    public void saveLogin(Login login) throws SQLException, AntwebException {
        if (login.getId() != 0) {
        
            // Use of ternary operator.  short conditional statement
            int isAdmin = (login.isAdmin()) ? 1 : 0;
            int uploadSpecimens = (login.isUploadSpecimens()) ? 1 : 0;
            int uploadImages = (login.isUploadImages()) ? 1 : 0;
        
            if (!isLegalLogin(login)) {
              throw new AntwebException("name or email already in use. id:" + login.getId() + " name:" + login.getName() + " email:" + login.getEmail());
            }
        
            String theInsert = "insert into login (id, name, first_name, last_name, " +
              "email, password, group_id, is_admin, is_upload_specimens, is_upload_images) values ("+ login.getId() +", '" + login.getName() + "', " +
              "'" + login.getFirstName() + "', '" + login.getLastName() + "', '" + login.getEmail() 
              + "', '" + login.getPassword() + "', " + login.getGroupId() 
              + ", " + isAdmin + ", " + uploadSpecimens + ", " 
              + uploadImages + ")";
                       
            //s_log.info("saveLogin() insert:" + theInsert);
             
            Statement stmt = null;
            try {
                stmt = getConnection().createStatement();
                                                                                                                         
                s_log.info("saveLogin() insert:" + theInsert);
                stmt.executeUpdate(theInsert);
//s_log.warn("saveLogin() isAdmin:" + login.isAdmin() + " projects:" + login.getProjects());

                updateLoginProjects(login);
            } catch (SQLException e) {
                s_log.error("saveLogin() name:" + login.getName() + " query:" + theInsert);
                throw e;
            } finally {
                DBUtil.close(stmt, null, this, "saveLogin()");
            }
                        
        } else {
            s_log.error("saveLogin() Could not save login.  Id = 0");
        }
    }

    public void updateLastLogin(Login login) throws SQLException {
    
        //if (true) return; // update login turned off. Can be derived from the logs: /data/antweb/web/log/bak/*/logins.txt
    
        Statement stmt = null;
        try {
            stmt = getConnection().createStatement();            
            String theUpdate = "update login set last_login = now() where id = " + login.getId();
            
            stmt.executeUpdate(theUpdate);
        } catch (SQLException e) {
            s_log.error("updateLastLogin() id:" + login.getId() + " e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, null, this, "updateLastLogin()");
        }    
    }

    public void userUpdateLogin(Login login) throws SQLException {
      updateLogin(login, false);
    }
    
    public void adminUpdateLogin(Login login) throws SQLException {
       // This is just like updateLogin but will not modify password.  Called by administrators
      updateLogin(login, true);
    }
    
    private void updateLogin(Login login, boolean isAdminUpdate) throws SQLException {
    
        if (login.getId() != 0) {

            // Use of ternary operator.  short conditional statement
            //s_log.warn("isUploadSpecimens:" + login.isUploadSpecimens());
            //s_log.warn("isUploadImages:" + login.isUploadImages());
            int uploadSpecimens = (login.isUploadSpecimens()) ? 1 : 0;
            int uploadImages = (login.isUploadImages()) ? 1 : 0;
            int isAdmin = (login.isAdmin()) ? 1 : 0;        

            String theUpdate = "update login " 
              + " set name='" + login.getName() + "'"
              + ", first_name='" + login.getFirstName() + "'"
              + ", last_name='" + login.getLastName() + "'" 
              + ", email='" + login.getEmail() + "'";

            if (!isAdminUpdate) {
              theUpdate += ", password='" + login.getPassword() + "'";
            }

            if (isAdminUpdate) {
              theUpdate = theUpdate 
                + ", group_id='" + login.getGroupId() + "'"
                + ", is_admin='" + isAdmin + "'"
                + ", is_upload_specimens='" + uploadSpecimens + "'" 
                + ", is_upload_images='" + uploadImages + "'";               
            }

            theUpdate = theUpdate 
              + " where id=" + login.getId();
                         
             s_log.info("updateLogin() isAdminUpdate:" + isAdminUpdate + " update:" + theUpdate);

             if (!isAdminUpdate && !isLegalLogin(login)) {
               throw new SQLException("name:" + login.getName() + " or email:" + login.getEmail() + " already in use.");
             }
 
            Statement stmt = null;
            try {
              stmt = DBUtil.getStatement(getConnection(), "updateLogin()");
              s_log.info("updateLogin() update:" + theUpdate);
              stmt.executeUpdate(theUpdate);
              if (isAdminUpdate) {
  // s_log.info("updateLogin() updateProjects()");
                updateLoginProjects(login);
                updateLoginCountries(login);
              }
            } catch (SQLException e) {
              s_log.error("updateLogin() name:" + login.getName() + " e:" + e + " query:" + theUpdate);
              throw e;
            } finally {
                DBUtil.close(stmt, null, this, "updateLogin()");
            }
        }
    }
   
    private void updateLoginProjects(Login login) throws SQLException {
      String theStatement = "delete from login_project where login_id=" + login.getId();
      
      if (login.isAdmin()) return; // Admins get all options automatically.  No need to store.                                                            
                                                                                                                         
	  Statement stmt = null;
	  try {
        ArrayList<SpeciesListable> projects = login.getProjects();
        if (projects == null) return;                

        //A.log("updateLoginProjects() projects:" + projects + " size:" + projects.size());

		stmt = DBUtil.getStatement(getConnection(), "updateLoginProjects()");
        stmt.executeUpdate(theStatement);

        //From SaveLoginAction, the list of projects comes from a form.  They are strings.
        String projectName = null;
        for (SpeciesListable project : projects) {
          if (project == null) continue;
          projectName = project.getName();

          theStatement = "insert into login_project (login_id, project_name) " 
            + " values (" + login.getId() + ",'" + projectName + "')";            
            //s_log.info("updateProjects() theStatement:" + theStatement);
          stmt.executeUpdate(theStatement);        
        }
      } catch (SQLException e) {
        s_log.error("updateLoginProjects() for login:" + login.getName() + " theStatement:" + theStatement);
        throw e;
      } finally {
        DBUtil.close(stmt, null, this, "updateLoginProjects()");
      }
    }

    private void updateLoginCountries(Login login) throws SQLException {      
      if (login.isAdmin()) return; // Admins get all options automatically.  No need to store.                                                            
                                                                                                                         
      String theStatement = "delete from login_country where login_id=" + login.getId();
	  Statement stmt = null;
	  try {
        ArrayList<SpeciesListable> countries = login.getCountries();
        if (countries == null) return;                

		stmt = DBUtil.getStatement(getConnection(), "updateLoginCountries()");
        stmt.executeUpdate(theStatement);

        String name = null;
        //A.log("updateLoginCountries() countries:" + countries + " size:" + countries.size());
        for (SpeciesListable country : countries) {
          if (country == null) continue;
          name = country.getName();

          theStatement = "insert into login_country (login_id, country) " 
            + " values (" + login.getId() + ",'" + name + "')";            
            //s_log.info("updateLoginCountries() theStatement:" + theStatement);
          stmt.executeUpdate(theStatement);
        }
      } catch (SQLException e) {
        s_log.error("updateLoginCountries() for login:" + login.getName() + " theStatement:" + theStatement);
        throw e;
      } finally {
        DBUtil.close(stmt, null, this, "updateLoginCountries()");
      }
    }

    public boolean isLegalLogin(Login login) throws SQLException {
        //Verify that the email and login have not been used before by another account 
        boolean returnVal = false;
 
        String theQuery = "select count(*) as num from login " 
            + " where (";
            
        String theConditions = "";             
        String nameCondition = null;
        if ((login.getName() != null) && (!login.getName().equals(""))) {
            nameCondition = " (name = '" + login.getName() + "' or email = '" + login.getName() + "') ";
        }
        if (nameCondition != null) theConditions += nameCondition;
        
        String emailCondition = null;
        if ((login.getEmail() != null) && (!login.getEmail().equals(""))) {
            emailCondition = " (email = '" + login.getEmail() + "' or name = '" + login.getEmail() + "') ";
        }
        if (emailCondition != null) {
          if (nameCondition != null) {  
            theConditions += " or ";        
          }
          theConditions += emailCondition;
        }

        if (nameCondition == null && emailCondition  == null) return false; 
        theQuery += theConditions + ") and id !=" + login.getId();
                 
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = getConnection().createStatement();
            rset = stmt.executeQuery(theQuery);
              
            int records = 0;
            int num = 0;
            while (rset.next()) {
                ++records;
                num = rset.getInt("num");
            }

            //A.log("isLegalLogin() records:" + records + " num:" + num + " query:" + theQuery);
            
            returnVal = (num == 0);
        } catch (SQLException e) {
            s_log.error("isLegalLogin() e:" + e + " query:" + theQuery);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "isLegalLogin()");
        }       
            
        if (AntwebProps.isDevMode() && !returnVal) {
          s_log.warn("isLegalLogin() returnVal:" + returnVal + " query:" + theQuery);
          //AntwebUtil.logShortStackTrace(6);
        }    
        return returnVal;
    }

    public String findInviteId(String email) throws SQLException {
        String theQuery = "select id from login " 
            + " where email='" + email + "' and password = '' "; 
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = getConnection().createStatement();
            rset = stmt.executeQuery(theQuery);
            int records = 0;
            int num = 0;
            while (rset.next()) {
                ++records;
                num = rset.getInt("id");
            }

            s_log.info("findInviteId() email:" + email + " num:" + num + " + records:" + records + " query:" + theQuery);
                           
            if (num > 0) return (new Integer(num)).toString();
        } catch (SQLException e) {
            s_log.error("findInviteId() email:" + email + " query:" + theQuery);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "findInviteId()");
        }
        return null;
    }
    
    
    // This method is called from ChangePasswordAction.java, but that class is not currently used.
    public void changePassword(Login login, String newPassword) throws SQLException {

        Statement stmt = null;
        try {
            stmt = getConnection().createStatement();            
            String theUpdate = "update login set password = '" + newPassword + "' where id = " + login.getId();
            
            stmt.executeUpdate(theUpdate);
        } catch (SQLException e) {
            s_log.error("changePassword() id:" + login.getId() + " e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, null, this, "changePassword()");
        }
    }        
    
    public void deleteById(int id) throws SQLException {
        String theQuery = "delete from login where id = " + id;
        Statement stmt = null;
        try {
            stmt = getConnection().createStatement();
            int returnVal = stmt.executeUpdate(theQuery);
        } catch (SQLException e) {
            s_log.error("deleteById(" + id + ") e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, null, this, "deleteById()");
        }

    }        
    
    public static ArrayList getUsrAdmList(Connection connection) throws SQLException { 
        ArrayList<String> usrAdmList = new ArrayList();
        String theQuery = "select login.name, password, group_id, groups.name from login, groups where login.group_id = groups.id ";

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                String name = rset.getString("login.name");
                String password = rset.getString("password");
                int groupId = rset.getInt("group_id");
                String groupName = rset.getString("groups.name");
                String url = AntwebProps.getSecureDomainApp() + "/login.do?userName=" + name + "&password=" + password;
                String groupStr = "";
                if (!("Default Group".equals(groupName))) { 
                  groupStr = ":" + groupName;
                }
                String anchor = "<a href=\"" + url + "\">" + name + "(" + groupId + groupStr + ")" + "</a>";
                usrAdmList.add(anchor);
            }
        } catch (SQLException e) {
            s_log.error("getUsrAdmList() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "getUsrAdmList", "getUsrAdmList()");
        }
     
        return usrAdmList;
    }

    public static ArrayList getUsrAdmLastLoginList(Connection connection) throws SQLException {
        ArrayList<String> usrAdmList = new ArrayList();
        String theQuery = "select login.name, password, group_id, groups.name, login.last_login as lastLogin " 
          + " from login, groups where login.group_id = groups.id and last_login != '0000-00-00 00:00:00' " 
          + " order by last_login";

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                String name = rset.getString("login.name");
                String password = rset.getString("password");
                int groupId = rset.getInt("group_id");
                String groupName = rset.getString("groups.name");
                //String lastLogin = rset.getTimestamp("login.last_login");
                Timestamp lastLogin = rset.getTimestamp("lastLogin");
                 String url = AntwebProps.getDomainApp() + "/login.do?userName=" + name + "&password=" + password;
                String groupStr = "";
                if (!("Default Group".equals(groupName))) { 
                  groupStr = ":" + groupName;
                }
                String anchor = "<a href=\"" + url + "\">" + name + "(" + groupId + groupStr + ")" + "</a> " + lastLogin;
                usrAdmList.add(anchor);
            }
        } catch (SQLException e) {
            s_log.error("getUsrAdmLastLoginList() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "getUsrAdmList", "getUsrAdmList()");
        }
     
        return usrAdmList;
    }
}