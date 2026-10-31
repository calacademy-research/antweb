package org.calacademy.antweb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.util.AntwebUtil;

/**
 * The orphaned images: image rows whose specimen code (image_of_id) is no specimen's code. setOrphans reads the
 * codes (CODES_SQL, the page's list) and the number of images (COUNTS_SQL). Used by OrphanedImagesAction
 * (orphanedImages.do) and the count check (NumberChecks). Tables image and specimen. Docs: docs/numbers.md row 99
 * and Q9.
 */
public class OrphanedImages {

    private static final Log s_log = LogFactory.getLog(OrphanedImages.class);

	Connection connection;
	ArrayList<String> orphans = new ArrayList<>();
	long imageCount = 0;

	/*
	 * Brian Fisher, 2026-10-22 (docs/numbers.md Q9): the page says how many images and how many specimen codes:
	 * "N images of M specimen codes that are not in AntWeb". An orphaned image is an image row whose image_of_id is
	 * no specimen's code. One rule for the page and the count check (NumberChecks stats.images, admin.orphans).
	 */
	/** The specimen codes of orphaned images, each once (the list on the page). */
	public static final String CODES_SQL = "select distinct i.image_of_id from image i left join specimen s on s.code = i.image_of_id where s.code is null";
	/** Orphaned images and their specimen codes: count(*), count(distinct code). */
	public static final String COUNTS_SQL = "select count(*), count(distinct i.image_of_id) from image i left join specimen s on s.code = i.image_of_id where s.code is null";

	public ArrayList<String> getOrphans() {
		return orphans;
	}

	public void setOrphans(ArrayList<String> orphans) {
		this.orphans = orphans;
	}

	/** Orphaned images (files), as the page says "N images of M specimen codes". */
	public long getImageCount() {
		return imageCount;
	}

	public void setOrphans() {
		try (Statement stmt = connection.createStatement()) {
			try (ResultSet rset = stmt.executeQuery(CODES_SQL)) {
				while (rset.next()) {
					orphans.add(rset.getString(1));
				}
			}
			try (ResultSet rset = stmt.executeQuery(COUNTS_SQL)) {
				if (rset.next()) imageCount = rset.getLong(1);
			}
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
