package org.calacademy.antweb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.util.AntwebUtil;

public class OrphanedImages {

    private static final Log s_log = LogFactory.getLog(OrphanedImages.class);

	Connection connection;
	ArrayList<String> orphans = new ArrayList<>();
	
	public ArrayList<String> getOrphans() {
		return orphans;
	}

	public void setOrphans(ArrayList<String> orphans) {
		this.orphans = orphans;
	}

	public void setOrphans() {
		
		String theQuery = "select distinct image.image_of_id from " + 
			" image left join specimen on image.image_of_id = specimen.code where " +
			" code is null";
		
		try {
			Statement stmt = connection.createStatement();
			ResultSet rset = stmt.executeQuery(theQuery);
			while (rset.next()) {
				orphans.add(rset.getString(1));
			}
			rset.close();
			stmt.close();
		} catch (SQLException e) {
			s_log.error("setOrphans() e: " + e);
			AntwebUtil.logStackTrace(e);
		}		
	}

	public Connection getConnection() {
		return connection;
	}
	public void setConnection(Connection connection) {
		this.connection = connection;
	}
}
