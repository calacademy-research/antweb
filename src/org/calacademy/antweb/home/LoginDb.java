package org.calacademy.antweb.home;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.Curator;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.SpeciesListable;
import org.calacademy.antweb.util.*;
import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpSession;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class LoginDb extends AntwebDb {
    
    private static final Log s_log = LogFactory.getLog(LoginDb.class);
        
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
        //if (AntwebProps.isDevMode()) AntwebUtil.logShortStackTrace(3);
        Login login = null;
        if (id > 0) {
            String theQuery = "select * from login where id = ?";
            PreparedStatement stmt = null;
            ResultSet rset = null;
            try {
                stmt = DBUtil.getPreparedStatement(getConnection(), "getLogin()", theQuery);
                stmt.setInt(1, id);
                rset = stmt.executeQuery();
                login = instantiateLogin(rset);
            } finally {
                DBUtil.close(stmt, rset, this, "getLogin()");
            }
        }
        return login;
    }
    public Login getDeepLogin(int id) throws SQLException {
        Login login = getLogin(id);
        postInstantiate(login);
        return login;
    }

    public Curator getCurator(int id) throws SQLException {
        //if (AntwebProps.isDevMode()) AntwebUtil.logShortStackTrace(6);
        Curator curator = null;
        if (id > 0) {
            String theQuery = "select * from login where id = ?";
            PreparedStatement stmt = null;
            ResultSet rset = null;
            try {
                stmt = DBUtil.getPreparedStatement(getConnection(), "getCurator()", theQuery);
                stmt.setInt(1, id);
                rset = stmt.executeQuery();
                curator = instantiateCurator(rset);
            } finally {
                DBUtil.close(stmt, rset, this, "getCurator()");
            }
        }
        return curator;
    }
    public Curator getDeepCurator(int id) throws SQLException {
        Curator curator = getCurator(id);
        postInstantiate(curator);
        return curator;
    }

    // Potentially called at login.
    public Login getLoginByName(String name) throws SQLException {
        Login login = null;
        if (name != null) {
//            name = DBUtil.escapeQuotes(name);
            String theQuery = "select * from login where name = ?";
            PreparedStatement stmt = null;
            ResultSet rset = null;
            try {
                stmt = DBUtil.getPreparedStatement(getConnection(), "getLoginByName()", theQuery);
                stmt.setString(1, name);
                rset = stmt.executeQuery();
                login = instantiateLogin(rset);
                if (login != null)
                  postInstantiate(login);
            } finally {
                DBUtil.close(stmt, rset, this, "getLoginByName()");
            }
        }
        return login;
    }

    // Potentially called at login.
    public Login getLoginByEmail(String email) throws SQLException {
        Login login = null;
        if (email != null) {
            String theQuery = "select * from login where email = ?";
            PreparedStatement stmt = null;
            ResultSet rset = null;
            try {
                stmt = DBUtil.getPreparedStatement(getConnection(), "getLoginByEmail()", theQuery);
                stmt.setString(1, email);
                rset = stmt.executeQuery();
                //A.log("getLoginByEmail() 1 email:" + email + " query:" + theQuery);                
                login = instantiateLogin(rset);
                if (login != null)
                    postInstantiate(login);
                //A.log("getLoginByEmail() 2 email:" + email + " login:" + login);                
            } catch (SQLSyntaxErrorException e) {
              s_log.error("getLoginByEmail() email:" + email + " e:" + e);
            } finally {
              DBUtil.close(stmt, rset, this, "getLoginByEmail()");
            }
        }
        return login;
    }
    
    public ArrayList<Login> getAllLogins() throws SQLException {          
        ArrayList<Login> loginList = new ArrayList<>();
        String theQuery = "select * from login";
        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getPreparedStatement(getConnection(), "getAllLogins()", theQuery);
            rset = stmt.executeQuery();
        
            while (rset.next()) {
                Login login = instantiateLoginRow(rset);
                loginList.add(login);
            }
            Collections.sort(loginList);  
        } finally {
            DBUtil.close(stmt, rset, this, "getAllLogins()");
        }
        return loginList;      
    }

    public ArrayList<Curator> getAllCurators() throws SQLException {
        ArrayList<Curator> curatorList = new ArrayList<>();
        //String theQuery = "select id from login where is_upload_images = 1 or is_upload_images = 1;"; // or  + " and group_id > 0"
        String theQuery = "select id from login where group_id > 0;"; // or  + " and group_id > 0"
        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getPreparedStatement(getConnection(), "getAllCurators()", theQuery);
            rset = stmt.executeQuery();
            // A.log("getAllCurators() theQuery:" + theQuery);
            while (rset.next()) {
                Curator curator = getCurator(rset.getInt("id"));
                curatorList.add(curator);
                //if (curator.getId() == 1752) A.log("getAllCurators() curator Gibb:" + curator + " uploadSpecimens:" + curator.isUploadSpecimens());
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

    /**
     * Instantiate a Login from a row of a ResultSet.
     *
     * The ResultSet cursor is not incremented in this method, it must be done prior to calling this function.
     *
     * @param rset The ResultSet to get data from
     * @return A Login with data from the current ResultSet row
     * @throws SQLException
     */
    private @Nullable Login instantiateLoginRow(ResultSet rset) throws SQLException {

        Login login = new Login();

        login.setId(rset.getInt("id"));
        login.setName(rset.getString("name"));
        login.setPassword(rset.getString("password"));
        login.setFirstName(rset.getString("first_name"));
        login.setLastName(rset.getString("last_name"));
        login.setEmail(rset.getString("email"));
//if (!AntwebProps.isDevMode())

        login.setCreated(rset.getTimestamp("created"));
        //A.log("instantiateLoginRow() Created:" + login.getCreated());

        login.setIsAdmin(rset.getBoolean("is_admin"));
        int groupId = rset.getInt("group_id");
        login.setGroupId(groupId);

        login.setIsUploadSpecimens(rset.getBoolean("is_upload_specimens"));
        login.setIsUploadImages(rset.getBoolean("is_upload_images"));

        if (login.getId() == 0) return null;
        return login;
    }
        
    private Login instantiateLogin(ResultSet rset)
      throws SQLException {
        Login login = new Login();
        login = instantiate(login, rset);
        if (login.getId() == 0) return null;
        return login;
    }

    /**
     * Instantiate a single Login from a ResultSet.
     *
     * The ResultSet should have only one row. For instantiating multiple logins in a loop, see instantiateLoginRow
     *
     * @param login The login to add data to
     * @param rset  The ResultSet to get data from
     * @return  A Login with data from the ResultSet's row.
     * @throws SQLException
     */
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
            login.setCreated(rset.getTimestamp("created"));
            //A.log("instantiateLogin() created:" + login.getCreated());
            login.setIsAdmin(rset.getBoolean("is_admin"));
            int groupId = rset.getInt("group_id");
            login.setGroupId(groupId);

            //curator.getGroup().setCurator(curator);  // backwards, but allows code to remain unchanged.                    
            login.setIsUploadSpecimens(rset.getBoolean("is_upload_specimens"));
            login.setIsUploadImages(rset.getBoolean("is_upload_images"));
        }
        return login;
    }

    // Expensive. Don't want to do this in the midst of server startup.
    public void postInstantiate(Login login) throws SQLException {
        if (login.isCurator()) {
            ProjectDb projectDb = new ProjectDb(getConnection());
            login.setProjects(projectDb.fetchSpeciesLists(login));

            GeolocaleDb geolocaleDb = new GeolocaleDb(getConnection());
            login.setCountries(geolocaleDb.fetchSpeciesLists(login, false));

            // really slow to do this here.
            login.setGeolocales(geolocaleDb.fetchSpeciesLists(login));

            //A.log("postInstantiate() 2 login:" + login);
        }
    }



    public void setImageUploadCount(Curator curator) {

      //  select count(*), upload_date, group_concat(distinct access_group) from image i, specimen s where i.image_of_id = s.code group by upload_date order by group_concat(distinct access_group);
      // Perhaps the best we can do. Not having uploader ID. Trusting that the owner of the specimen is the uploader of the image.
      
      String query = "select count(*) count from image_upload where curator_id = ?";
      
      ResultSet rset = null;
      PreparedStatement stmt = null;
      try {

        stmt = DBUtil.getPreparedStatement(getConnection(), "setImageUploadCount()", query);
        stmt.setInt(1, curator.getId());
        stmt.executeQuery();
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
      
      String query = "select count(*) count from image_upload, image_uploaded where image_upload.id = image_uploaded.image_upload_id and curator_id = ?";
      
      ResultSet rset = null;
      PreparedStatement stmt = null;
      try {

        stmt = DBUtil.getPreparedStatement(getConnection(), "setImagesUploadedCount()", query);

        stmt.setInt(1, curator.getId());
        stmt.executeQuery();
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

      String query = "select count(*) uploadCount from upload where login_id = ?";
      ResultSet rset = null;
      PreparedStatement stmt = null;
      try {

        UploadDb uploadDb = new UploadDb(getConnection());
        curator.setLastUpload(uploadDb.getLastUploadByLogin(curator.getId()));

        stmt = DBUtil.getPreparedStatement(getConnection(), "getUploadCounts()", query);
        stmt.setInt(1, curator.getId());
        stmt.executeQuery();
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
      
      String query = "select count(*) count from description_edit where access_login = ?";
      ResultSet rset = null;
      PreparedStatement stmt = null;
      try {
        stmt = DBUtil.getPreparedStatement(getConnection(), "getDescEditCounts()", query);
        stmt.setInt(1, curator.getId());
        stmt.executeQuery();
        rset = stmt.getResultSet();

        while (rset.next()) {
            count = rset.getInt("count");
        }
      } catch (SQLException e) {
          s_log.error("getDescEditCounts() e:" + e + " query:" + query);
      } finally {
        DBUtil.close(stmt, rset, "LoginDb", "getDescEditCounts()");
      }

      query = "select count(*) count from description_hist where access_login = ?";
      try {
        stmt = DBUtil.getPreparedStatement(getConnection(), "getDescEditCounts()", query);
        stmt.setInt(1, curator.getId());
        stmt.executeQuery();
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
        ArrayList<Curator> curators = new ArrayList<>();
        String theQuery = "select id from login where group_id = ?";
        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = getConnection().prepareStatement(theQuery);
            stmt.setInt(1, groupId);
            rset = stmt.executeQuery();
        
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
        HashMap<Integer, Login> curators = new HashMap<>();
        String theQuery = "select id from login where group_id >= 0";
        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = getConnection().prepareStatement(theQuery);
            rset = stmt.executeQuery();
        
            while (rset.next()) {
                int id = rset.getInt("id");
                Login login = getLogin(id);
                curators.put(id, login);
            } 
        } finally {
            DBUtil.close(stmt, rset, this, "getCuratorMap()");
        }
        return curators;      
    }

    public int getNewLoginId() {
        int newLoginId = 0;
        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            String theQuery = "select max(id) as maxid from login";
            stmt = DBUtil.getPreparedStatement(getConnection(), "getNewLoginId", theQuery);
            rset = stmt.executeQuery();
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
            int isAdmin = login.isAdmin() ? 1 : 0;
            int uploadSpecimens = login.isUploadSpecimens() ? 1 : 0;
            int uploadImages = login.isUploadImages() ? 1 : 0;
        
            if (!isLoginTaken(login)) {
              throw new AntwebException("name or email already in use. id:" + login.getId() + " name:" + login.getName() + " email:" + login.getEmail());
            }
        
            String theInsert = "insert into login" +
                    " (id, name, first_name, last_name, email, password, group_id, is_admin, is_upload_specimens, is_upload_images)" +
                    " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                       
            //s_log.info("saveLogin() insert:" + theInsert);
             
            PreparedStatement stmt = null;
            try {
                stmt = getConnection().prepareStatement(theInsert);
                                                                                                                         
                s_log.info("saveLogin() insert:" + theInsert);

                stmt.setInt(1, login.getId());
                stmt.setString(2, login.getName());
                stmt.setString(3, login.getFirstName());
                stmt.setString(4, login.getLastName());
                stmt.setString(5, login.getEmail());
                stmt.setString(6, login.getPassword());
                stmt.setInt(7, login.getGroupId());
                stmt.setInt(8, isAdmin);
                stmt.setInt(9, uploadSpecimens);
                stmt.setInt(10, uploadImages);

                stmt.executeUpdate();
//s_log.warn("saveLogin() isAdmin:" + login.isAdmin() + " projects:" + login.getProjects());

                updateLoginProjects(login);
                updateLoginCountries(login);
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
    
        PreparedStatement stmt = null;
        try {
            String theUpdate = "update login set last_login = now() where id = ?";
            stmt = getConnection().prepareStatement(theUpdate);

            stmt.setInt(1, login.getId());

            stmt.executeUpdate();
        } catch (SQLException e) {
            s_log.error("updateLastLogin() id:" + login.getId() + " e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, null, this, "updateLastLogin()");
        }    
    }

    public void updateLogin(Login login, Login accessLogin) throws SQLException {

        boolean isAdminUpdate = accessLogin.isAdmin();

        boolean isSelfUpdate = (login.getId() == accessLogin.getId());

        // True? If isAdminUpdate, password will not be modified.

        if (login.getId() != 0) {

            // Use of ternary operator.  short conditional statement
            //s_log.warn("isUploadSpecimens:" + login.isUploadSpecimens());
            //s_log.warn("isUploadImages:" + login.isUploadImages());
            int uploadSpecimens = login.isUploadSpecimens() ? 1 : 0;
            int uploadImages = login.isUploadImages() ? 1 : 0;
            int isAdmin = login.isAdmin() ? 1 : 0;

            // A.log("updateLogin() isAdminUpdate:" + isAdminUpdate + " groupId:" + login.getGroupId() + " uploadSpecimens:" + uploadSpecimens + " uploadImages:" + uploadImages);

            if (!isAdminUpdate && !isSelfUpdate) {
                throw new SQLException("name:" + login.getName() + " or email:" + login.getEmail() + " already in use.");
            }

            String theUpdate = null;
            PreparedStatement stmt = null;
            try {
                if (isSelfUpdate && !isAdminUpdate) {
                    String selfUpdate = "update login "
                            + "set name = ?, first_name = ?, last_name = ?, email = ?, password = ? "
                            + "where id = ?";

                    theUpdate = selfUpdate;

                    stmt = DBUtil.getPreparedStatement(getConnection(), "updateLogin()", selfUpdate);
                    stmt.setString(1, login.getName());
                    stmt.setString(2, login.getFirstName());
                    stmt.setString(3, login.getLastName());
                    stmt.setString(4, login.getEmail());
                    stmt.setString(5, login.getPassword());
                    stmt.setInt(6, login.getId());
                } else if (isAdminUpdate && !isSelfUpdate) {
                    String adminUpdate = "update login "
                            + "set name = ?, first_name = ?, last_name = ?, email = ?, group_id = ?, is_admin = ?, is_upload_specimens = ?, is_upload_images = ? "
                            + "where id = ?";

                    theUpdate = adminUpdate;

                    stmt = DBUtil.getPreparedStatement(getConnection(), "updateLogin()", adminUpdate);
                    stmt.setString(1, login.getName());
                    stmt.setString(2, login.getFirstName());
                    stmt.setString(3, login.getLastName());
                    stmt.setString(4, login.getEmail());
                    stmt.setInt(5, login.getGroupId());
                    stmt.setInt(6, isAdmin);
                    stmt.setInt(7, uploadSpecimens);
                    stmt.setInt(8, uploadImages);
                    stmt.setInt(9, login.getId());
                } else if (isAdminUpdate && isSelfUpdate) {
                    String adminSelfUpdate = "update login "
                            + "set name = ?, first_name = ?, last_name = ?, email = ?, password = ?, group_id = ?, is_admin = ?, is_upload_specimens = ?, is_upload_images = ? "
                            + "where id = ?";

                    theUpdate = adminSelfUpdate;

                    stmt = DBUtil.getPreparedStatement(getConnection(), "updateLogin()", adminSelfUpdate);
                    stmt.setString(1, login.getName());
                    stmt.setString(2, login.getFirstName());
                    stmt.setString(3, login.getLastName());
                    stmt.setString(4, login.getEmail());
                    stmt.setString(5, login.getPassword());  // distinct from above
                    stmt.setInt(6, login.getGroupId());
                    stmt.setInt(7, isAdmin);
                    stmt.setInt(8, uploadSpecimens);
                    stmt.setInt(9, uploadImages);
                    stmt.setInt(10, login.getId());
                }

                //A.log("updateLogin() update:" + DBUtil.getPreparedStatementString(stmt));

                stmt.executeUpdate();

                if (isAdminUpdate) {
                  //A.log("updateLogin() update Login Projects and Countries");
                  updateLoginProjects(login);
                  updateLoginCountries(login);
                }

                //LoginMgr.reload(login, getConnection());

            } catch (SQLException e) {
                s_log.error("updateLogin() name:" + login.getName() + " e:" + e + " query:" + theUpdate);
                throw e;
            } finally {
                DBUtil.close(stmt, null, this, "updateLogin()");
            }
        }
    }

    private void updateLoginProjects(Login login) throws SQLException {

        if (login.isAdmin())
            return; // Admins get all options automatically.  No need to store.

        String theStatement = "delete from login_project where login_id = ?";
        PreparedStatement stmt = null;

        ArrayList<SpeciesListable> projects = login.getProjects();
        if (projects == null) return;
        
        try {
            A.log("updateLoginProjects() projects:" + projects + " size:" + projects.size());

            stmt = DBUtil.getPreparedStatement(getConnection(), "updateLoginProjects()", theStatement);

            stmt.setInt(1, login.getId());
            stmt.executeUpdate();
            stmt.close();       // not sure if I need to do this, I don't know if reassigning stmt closes it automatically

            //From SaveLoginAction, the list of projects comes from a form.  They are strings.
            String projectName;
            theStatement = "insert into login_project (login_id, project_name) values (?, ?)";

            stmt = DBUtil.getPreparedStatement(getConnection(), "updateLoginProjects()", theStatement);

            for (SpeciesListable project : projects) {
                if (project == null) continue;
                projectName = project.getName();

                stmt.setInt(1, login.getId());
                stmt.setString(2, projectName);
                stmt.addBatch();
                //s_log.info("updateProjects() theStatement:" + theStatement);
            }
            stmt.executeBatch();

        } catch (SQLException e) {
            s_log.error("updateLoginProjects() for login:" + login.getName() + " theStatement:" + theStatement);
            throw e;
        } finally {
            DBUtil.close(stmt, null, this, "updateLoginProjects()");
        }
    }

    private void updateLoginCountries(Login login) throws SQLException {
        if (login.isAdmin())
            return; // Admins get all options automatically.  No need to store.

        ArrayList<SpeciesListable> countries = login.getCountries();
        if (countries == null) return;

        String theStatement = "delete from login_country where login_id = ?";
        PreparedStatement stmt = null;
        try {

            stmt = DBUtil.getPreparedStatement(getConnection(), "updateLoginCountries()", theStatement);
            stmt.setInt(1, login.getId());
            stmt.executeUpdate();

            stmt.close();   // Close statement so we can create new one with same variable

            theStatement = "insert into login_country (login_id, country)  values (?, ?)";
            String name;
            stmt = DBUtil.getPreparedStatement(getConnection(), "updateLoginCountries()", theStatement);

            A.log("updateLoginCountries() countries:" + countries + " size:" + countries.size());
            for (SpeciesListable country : countries) {
                if (country == null) continue;
                name = country.getName();
                stmt.setInt(1, login.getId());
                stmt.setString(2, name);
                stmt.addBatch();

                //s_log.info("updateLoginCountries() theStatement:" + theStatement);
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            s_log.error("updateLoginCountries() for login:" + login.getName() + " theStatement:" + theStatement);
            throw e;
        } finally {
            DBUtil.close(stmt, null, this, "updateLoginCountries()");
        }
    }

    private boolean isLoginTaken(Login login) throws SQLException {
        //Verify that the email and login have not been used before by another account

        boolean namePresent = !StringUtils.isBlank(login.getName());
        boolean emailPresent = !StringUtils.isBlank(login.getEmail());

        if (!namePresent && !emailPresent) {
            return false;
        }

        String query;

        if (namePresent && emailPresent) {
            query = "select count(*) as num from login where (name = ? or email = ?) or (name = ? or email = ?) and id != ?";
        } else {
            query = "select count(*) as num from login where (name = ? or email = ?) and id != ?";   // we can fill with either email or name
        }

        A.log("isLoginTaken() namePresent:" + namePresent + " emailPresent:" + emailPresent + " query:" + query);

        PreparedStatement stmt = null;
        ResultSet rset = null;
        boolean returnVal;
        try {
            stmt = getConnection().prepareStatement(query);

            if (namePresent && emailPresent) {
                stmt.setString(1, login.getName());
                stmt.setString(2, login.getName());
                stmt.setString(3, login.getEmail());
                stmt.setString(4, login.getEmail());
                stmt.setInt(5, login.getId());
            } else if (!namePresent) {
                stmt.setString(1, login.getEmail());
                stmt.setString(2, login.getEmail());
                stmt.setInt(3, login.getId());
            } else {
                stmt.setString(1, login.getName());
                stmt.setString(2, login.getName());
                stmt.setInt(3, login.getId());
            }

            rset = stmt.executeQuery();

            int num = 0;
            while (rset.next()) {
                num = rset.getInt("num");
            }

            returnVal = num == 0;

            if (AntwebProps.isDevMode() && !returnVal) {
                s_log.warn("isLoginTaken() returnVal: false for query:" + DBUtil.getPreparedStatementString(stmt));
                //AntwebUtil.logShortStackTrace(6);
            }

        } catch (SQLException e) {
            s_log.error("isLoginTaken() e:" + e + " query:" + query);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "isLoginTaken()");
        }
        return returnVal;
    }

    public String findInviteId(String email) throws SQLException {
        String theQuery = "select id from login where email = ? and password = ''";
        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = getConnection().prepareStatement(theQuery);
            stmt.setString(1, email);
            rset = stmt.executeQuery();
            int records = 0;
            int num = 0;
            while (rset.next()) {
                ++records;
                num = rset.getInt("id");
            }

            s_log.info("findInviteId() email:" + email + " num:" + num + " + records:" + records + " query:" + theQuery);
                           
            if (num > 0) return Integer.valueOf(num).toString();
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

        PreparedStatement stmt = null;
        try {
            String theUpdate = "update login set password = ? where id = ?";
            stmt = getConnection().prepareStatement(theUpdate);

            stmt.setString(1, newPassword);
            stmt.setInt(2, login.getId());

            stmt.executeUpdate();
        } catch (SQLException e) {
            s_log.error("changePassword() id:" + login.getId() + " e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, null, this, "changePassword()");
        }
    }        
    
    public void deleteById(int id) throws SQLException {
        String theQuery = "delete from login where id = ?";
        PreparedStatement stmt = null;
        try {
            stmt = getConnection().prepareStatement(theQuery);
            stmt.setInt(1, id);
            int returnVal = stmt.executeUpdate();
        } catch (SQLException e) {
            s_log.error("deleteById(" + id + ") e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, null, this, "deleteById()");
        }

    }        
    
    public static ArrayList<String> getUsrAdmList(Connection connection) throws SQLException {
        ArrayList<String> usrAdmList = new ArrayList<>();
        String theQuery = "select login.name, password, group_id, ant_group.name from login, ant_group where login.group_id = ant_group.id ";

        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = connection.prepareStatement(theQuery);
            rset = stmt.executeQuery();

            while (rset.next()) {
                String name = rset.getString("login.name");
                String password = rset.getString("password");
                int groupId = rset.getInt("group_id");
                String groupName = rset.getString("ant_group.name");
                String url = AntwebProps.getSecureDomainApp() + "/login.do?userName=" + name + "&password=" + password;
                String groupStr = "";
                if (!"Default Group".equals(groupName)) {
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

    public static ArrayList<String> getUsrAdmLastLoginList(Connection connection) throws SQLException {
        ArrayList<String> usrAdmList = new ArrayList<>();
        String theQuery = "select login.name, password, group_id, ant_group.name, login.last_login as lastLogin "
                + "from login, ant_group where login.group_id = ant_group.id and last_login IS NOT NULL "
                + "order by last_login";

        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = connection.prepareStatement(theQuery);
            rset = stmt.executeQuery();

            while (rset.next()) {
                String name = rset.getString("login.name");
                String password = rset.getString("password");
                int groupId = rset.getInt("group_id");
                String groupName = rset.getString("ant_group.name");
                //String lastLogin = rset.getTimestamp("login.last_login");
                Timestamp lastLogin = null;

                lastLogin = rset.getTimestamp("lastLogin");

                String url = AntwebProps.getDomainApp() + "/login.do?userName=" + name + "&password=" + password;
                String groupStr = "";
                if (!"Default Group".equals(groupName)) {
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