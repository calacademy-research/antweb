package org.calacademy.antweb.home;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.Artist;
import org.calacademy.antweb.Curator;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.util.A;
import org.calacademy.antweb.util.AntwebProps;
import org.calacademy.antweb.util.DBUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ArtistDb extends AntwebDb {

    private static Log s_log = LogFactory.getLog(ArtistDb.class);
    //private Connection connection = null;
    
    public ArtistDb(Connection connection) {
      super(connection);
    }


    public Artist getArtist(String criteria) throws SQLException {
        Statement stmt = null;
        ResultSet rset = null;
        try {
			String query = "select id, name, created, active, curator_id from artist where " + criteria;
            stmt = DBUtil.getStatement(getConnection(), "getArtist()");
 
			rset = stmt.executeQuery(query);

            //A.log("getArtist() query:" + query);

			Artist artist = null;
			while (rset.next()) {
				artist = new Artist();
				artist.setId(rset.getInt("id"));
				artist.setName(rset.getString("name"));
				Object o = null;
				try {
				  o = rset.getTimestamp("created");
                  //A.log("getArtists() created:" + o);
                  if (!o.toString().equals("0000-00-00 00:00:00")) {
                    artist.setCreated((Date) o);
                  }
                } catch (SQLException e) {
                  // do nothing
                }
//A.log("getArtist() created:" + artist.getCreated());                
				artist.setIsActive(rset.getBoolean("active"));
                artist.setCuratorId(rset.getInt("curator_id"));
				//A.log("getArtists() id:" + artist.getId() + " name:" + artist.getName());

                return artist;				
			}
        } finally {
            DBUtil.close(stmt, rset, this, "getArtist()");
        }
        return null;
    }

    public void postInstantiate(ArrayList<Artist> artists) {
        setAllArtistCounts(artists);
    }
    
    public ArrayList<Artist> getArtists() throws SQLException {
        ArrayList<Artist> artists = new ArrayList<>();
        Statement stmt = null;
        ResultSet rset = null;
        try {
			String query = "select id, name, created, active, curator_id from artist";
            stmt = DBUtil.getStatement(getConnection(), "getArtists()");
 
			rset = stmt.executeQuery(query);
            //A.log("getArtists() query:" + query);
            
			Artist artist = null;
			while (rset.next()) {
				artist = new Artist();
				artist.setId(rset.getInt("id"));
				artist.setName(rset.getString("name"));
				
				Object o = rset.getTimestamp("created");
                artist.setCreated((Date) o);
                //A.log("getArtists() created:" + artist.getCreated() + " o:" + o);                
                
				artist.setIsActive(rset.getBoolean("active"));
				//A.log("getArtists() id:" + artist.getId() + " name:" + artist.getName());
                artist.setCuratorId(rset.getInt("curator_id"));

				//setArtistCounts(artist); // Now called in postInstantiate()
				
				artists.add(artist);
			}
        } finally {
            DBUtil.close(stmt, rset, this, "getArtists()");
        }
        return artists;
    }
    
    public int getMaxArtistId() {
      int id = 0;
      String query = "select max(id) id from artist";    
      ResultSet rset = null;
      Statement stmt = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getMaxArtistId()");
        stmt.executeQuery(query);
        rset = stmt.getResultSet();

        while (rset.next()) {
            id = rset.getInt("id");
        }
      } catch (SQLException e) {
          s_log.error("getMaxArtistId() e:" + e + " query:" + query);
      } finally {
        DBUtil.close(stmt, rset, "ImageDb", "getMaxArtistId()");
      }
      return id + 1;
    }
    
    
    // Add groupId to insert statement and database table. Login Id too? Why not.
    
    public String createArtist(String name, Curator curator) throws SQLException {
    
        Artist artist = getArtist("name = '" + name + "'");
        if (artist != null) {
          return "Artist already exists with name:" + name;
        }
        
        Statement stmt = null;
        String dml = null;
    	try {

            int artistId = getMaxArtistId() + 1;
    	
            stmt = DBUtil.getStatement(getConnection(), "createArtist()");            
            dml = "insert into artist (id, name, active, curator_id)" 
              + " values (" + artistId
                  + ", '" + name + "'"
                  + ", 1"
                  + ", " + curator.getId()
                  + ")";

    	    stmt.executeUpdate(dml);
    	    s_log.debug("insertImage() 1 dml:" + dml);
    	    artist = new Artist(artistId, name);
            return "Created artist:" + artist.getLink() + ".";
		} catch (SQLException e) {
			s_log.error("createArtist() dml" + dml + " e:" + e);
            return "error:" + e;
		} finally { 		
			DBUtil.close(stmt, "createArtist()");
		}
    }

    private int moveToArtist(Artist artist, Artist moveToArtist) throws SQLException {
        String returnVal = "";
        Statement stmt = null;
        String dml = null;
    	try {
            stmt = DBUtil.getStatement(getConnection(), "moveToArtist()");

            dml = "update image set artist = " + moveToArtist.getId() + " where artist = " + artist.getId();
            s_log.warn("moveToArtist() dml:" + dml);
    	    int i = stmt.executeUpdate(dml); 
    	    return i;
		} catch (SQLException e) {
			s_log.error("moveToArtist() dml" + dml + " e:" + e);
			throw e;
		} finally { 		
			DBUtil.close(stmt, "moveToArtist()");
		}
    }

    public String saveArtist(Artist artist, Login accessLogin) throws SQLException {
        String returnVal = "";
        Statement stmt = null;
        String dml = null;
    	try {
            stmt = DBUtil.getStatement(getConnection(), "saveArtist()");

            dml = "update artist set name = '" + artist.getName() + "' where id = " + artist.getId();
    	    int i = stmt.executeUpdate(dml); 
            s_log.debug("saveArtist() dml:" + dml + " i:" + i + " returnVal:" + returnVal);
		} catch (SQLException e) {
			s_log.error("saveArtist() dml" + dml + " e:" + e);
			return "e:" + e;
		} finally { 		
			DBUtil.close(stmt, "saveArtist()");
		}    
		return returnVal;
    }  

    //Add ownship clause for nonadmin.
    public String removeArtist(Artist artist, Artist moveToArtist, Login accessLogin) throws SQLException {

        String returnVal = "";
        Statement stmt = null;
        String dml = null;
        try {
            if (moveToArtist != null) {
                int i = moveToArtist(artist, moveToArtist);
                if (i > 0) {
                    String moveToArtistLink = "<a href='" + AntwebProps.getDomainApp() + "/artist.do?id=" + moveToArtist.getId() + "'>" + moveToArtist + "</a>";            
                    if (i == 1) returnVal = i + " image attributed to " + moveToArtistLink + ".";
                    if (i > 1) returnVal = i + " images attributed to " + moveToArtistLink + ".";
                    s_log.debug("moveToArtist() dml:" + dml + " i:" + i + " returnVal:" + returnVal);
                }
            }

            int artistImageCount = getArtistImageCount(artist.getId());
            if (artistImageCount > 0) {
              returnVal += " Can't delete an artist:" + artist.getLink() + " if images are attributed.";
              return returnVal;
            }
 
            boolean deleted = deleteArtist(artist, accessLogin);
            if (deleted) {
              returnVal += " Artist:" + artist + " removed.";
              return returnVal;
            } else {
              returnVal += " Artist:" + artist + " NOT deleted.";
            }
        } catch (Exception e) {
            return "error:" + e;
        }
        return returnVal;
    }
    
    public boolean deleteArtist(Artist artist, Login accessLogin) throws SQLException {

        String ownershipClause = "";
        if (accessLogin.isAdmin()) {
          // all good.
        } else {
          ownershipClause = " and curator_id = " + accessLogin.getId();
        }
        Statement stmt = null;
        String dml = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "deleteArtist()");            
        
            dml = "delete from artist " 
              + " where id = " + artist.getId()
              + ownershipClause
            ;

    	    int i = stmt.executeUpdate(dml);
    	    if (i == 0) return false; 
        
            s_log.debug("deleteArtist() dml:" + dml + " i:" + i);

            return true;
		} catch (SQLException e) {
			s_log.error("deleteArtist() dml: " + dml + " e:" + e);
		} finally { 		
			DBUtil.close(stmt, "deleteArtist()");
		}    
		return false;
    }    

    public int getArtistImageCount(int artistId) {
    
      String query = "select count(*) count from image where artist = " + artistId;    
      ResultSet rset = null;
      Statement stmt = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getArtistImageCount()");
        stmt.executeQuery(query);
        rset = stmt.getResultSet();
        s_log.debug("getArtistImageCount() query:" + query);
        while (rset.next()) {
            return rset.getInt("count");
        }
      } catch (SQLException e) {
          s_log.error("getArtistImageCount() e:" + e + " query:" + query);
      } finally {
        DBUtil.close(stmt, rset, "ImageDb", "getArtistImageCount()");
      }
      return 0;
    }

    private void setAllArtistCounts(List<Artist> artists) {

        String query = "select artist, count(id) images, count(distinct image_of_id) specimen from image group by artist";
        ResultSet rset = null;
        Statement stmt = null;

        Map<Integer, Artist> artistMap = artists.stream().collect(Collectors.toMap(Artist::getId, Function.identity()));

        try {
            stmt = DBUtil.getStatement(getConnection(), "getArtistCounts()");
            stmt.executeQuery(query);
            rset = stmt.getResultSet();

            while (rset.next()) {
                Artist artist = artistMap.get(rset.getInt("artist"));

                artist.setImageCount(rset.getInt("images"));
                artist.setSpecimenCount(rset.getInt("specimen"));
            }
        } catch (SQLException e) {
            s_log.error("getArtistCounts() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "ImageDb", "getArtistCounts()");
        }
    }
}
