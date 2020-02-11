package org.calacademy.antweb;

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/** Class Search does the searching of the specimen data */
public class SlideShowSearch implements Serializable {

    private static Log s_log = LogFactory.getLog(SlideShowSearch.class);

	private String rank;
	private Connection connection;

	public ArrayList getSlides() {
		ArrayList slides = new ArrayList();

		String theQuery;

//		if (project == null) {
//			theQuery =
//					"select distinct sp." + rank
//						+ " from specimen sp, image where "
//						+ "sp.code = image.image_of_id order by sp." + rank;
//		} else {
			theQuery = "select distinct sp." + rank
				+ " from specimen sp, image, proj_taxon, taxon "
				+ "where sp.code = image.image_of_id " 
				+ " and taxon.taxon_name = sp.taxon_name "
				+ "and proj_taxon.project_name = '" + Project.WORLDANTS + "' " 
				+ "and proj_taxon.taxon_name = sp.taxon_name "
				+ "and taxon.status = 'valid' "
				+ "order by sp." + rank;
//		}
A.log("getSlides() query:" + theQuery);


        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);

			String thisName = null;
			while (rset.next()) {
				thisName = rset.getString(1);
				if ((thisName != null) && (thisName.length() > 0) && (!thisName.equals("null"))) {
					slides.add(rset.getString(1));
				}
			}
		} catch (SQLException e) {
			s_log.error("error in  setDescription " + e);
        } finally {
            DBUtil.close(stmt, rset, this, "getSlides()");
        }
		return slides;
	}

	public String getRank() {
		return (this.rank);
	}

	public void setRank(String rank) {
		this.rank = rank;
	}

	public Connection getConnection() {
		return connection;
	}

	public void setConnection(Connection connection) {
		this.connection = connection;
	}
}
