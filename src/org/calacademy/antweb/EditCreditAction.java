package org.calacademy.antweb;

import java.io.*;
import javax.servlet.http.*;
import org.apache.struts.action.*;
import org.apache.regexp.*;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

/**
 * Deprecated? editCredit.do? web/edit_credit.jsp
 *
 *
 * This class takes the UploadForm and retrieves the text value
 * and file attributes and puts them in the request for the display.jsp
 * page to display them
 *
 */

public class EditCreditAction extends Action {

    private static Log s_log = LogFactory.getLog(EditCreditAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response) {

		java.sql.Connection connection = null;
		String query;

		try {
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "EditCreditAction");

			connection.setAutoCommit(true);

			if (form instanceof EditCreditForm) {
				EditCreditForm theForm = (EditCreditForm) form;

				String changeType = theForm.getChangeType();
				String changeField = theForm.getChangeField();
				String newValue = theForm.getNewValue();
				String selectedValue = theForm.getSelectedValue();
				int selectedIntValue=0;
				if ((selectedValue != null) && (!"".equals(selectedValue))) {
					selectedIntValue = Integer.parseInt(selectedValue);
				}
				if (changeType == null)
					return null;

				if (changeType.equals("add")) {
					addNewCredit(connection, changeField, newValue);
				} else if (changeType.equals("edit")) {
					editCredit(connection, changeField, selectedIntValue, newValue);
				}
			
				//String docBase = request.getRealPath("/");
                String docBase = AntwebProps.getDocRoot();
        
				writeUniqValues(connection, docBase, changeField);
			}

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return (mapping.findForward("error"));
        } finally { 		
            DBUtil.close(connection, this, "EditCreditAction");
        }

		return (mapping.findForward("success"));
	}


// dataSource.getConnection() xxx what about the stmts and rsets below?

	private void addNewCredit(Connection connection, String changeField, String newValue) {

		if ((changeField != null)
			&& (newValue != null)
			&& (connection != null)) {

            Statement stmt = null;
            ResultSet rset = null;
			try {
				// get the max index
				String query = "select max(id) from " + changeField;
				stmt = connection.createStatement();
				rset = stmt.executeQuery(query);
				int maxId=0;
				while (rset.next()) {
					maxId = rset.getInt(1);
				}
				stmt.close();

				maxId++;
				query =
					"insert into " + changeField
						+ " (id," + changeField + ") " 
						+ " values (" + maxId + ", '" + newValue + "')";
				stmt = DBUtil.getStatement(connection, "addNewCredit()");
				stmt.execute(query);
			} catch (SQLException e) {
				s_log.error("addNewCredit() e:" + e);
				org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
			} finally {
                DBUtil.close(stmt, rset, this, "addNewCredit()");			
			}
		}
	}
	
	private void editCredit(Connection connection, String changeField, int selectedValue, String newValue) {
		if ((changeField != null)
			&& (newValue != null)
			&& (connection != null) && (selectedValue > 0)) {

            String theQuery = "";
            Statement stmt = null;
			try {
				// get the max index
				theQuery = "update  " + changeField + " set " + changeField + " ='" + newValue + "' where id = " + selectedValue;
				stmt = DBUtil.getStatement(connection, "editCredit()"); 
				stmt.execute(theQuery);
			} catch (SQLException e) {
				s_log.error("editCredit() e:" + e + " theQuery:" + theQuery);
				org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
			} finally {
                DBUtil.close(stmt, this, "editCredit()");			
			}
		}
	}
		
	private boolean importImages(Connection connection, int artist, int copyright, int license) {
		boolean found_images = false;

		// go through each image in the images directory
		File dir = new File("/home/antweb/images");

		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return !name.startsWith(".");
			}
		};

		RE dot = null;
		RE underscore = null;
		try {
			dot = new RE(".tif");
			underscore = new RE("_");
		} catch (RESyntaxException e) {
		  // was unlogged.  Mark.
		  s_log.error("importImages() e:" + e);
		}

		String[] children = dir.list(filter);
		if (children == null) {
			// Either dir does not exist or is not a directory
		} else {
			found_images = true;

			String filename = null;
			String[] components = null;
			String specimen = null;
			String shot = null;
			int shot_number = 0;
			String[] firstBreak = null;
			String fileStart = null;

			int id = 0;
			int maxId = 0;
			for (int i = 0; i < children.length; i++) {
				// Get filename of file or directory
				filename = children[i];
				filename = filename.toLowerCase();

				// get the specimen and shot type
				components = null;
				specimen = "";
				shot = "";
				shot_number = 1;

				firstBreak = dot.split(filename);
				fileStart = firstBreak[0];
				components = underscore.split(fileStart);
				specimen = components[0];
				shot = components[1];
				if (components.length > 2) {
					shot_number = Integer.parseInt(components[2]);
				} else {
					shot_number = 1;
				}

				// if there's an image from this specimen with this shot, delete it
                String query = null;
                Statement stmt = null;
                ResultSet rset = null;
                Statement delstmt = null;
				try {
					query =
						"select id from image "
							+ " where image_of_id='" + specimen
							+ "' and shot_type = '" + shot
							+ "' and shot_number = " + shot_number
							+ " and source_table='specimen'";

					rset = stmt.executeQuery(query);
					while (rset.next()) {
						id = rset.getInt(1);
						delstmt = connection.createStatement();
						query = "delete from image where id = " + id;
						delstmt.executeUpdate(query);
						delstmt.close();
					}

					// insert this image into the db

					// first get the new id
					query = "select max(id) from image";

					stmt = connection.createStatement();
					rset = stmt.executeQuery(query);
					maxId = 0;

					while (rset.next()) {
						maxId = rset.getInt(1);
					}
					maxId++;

					// now stick this thing into the db
					query =
						"insert into image "
							+ "(id, shot_type, source_table, image_of_id, shot_number, artist, copyright, license) " 
							+ " values (" + maxId + ", '" + shot + "', 'specimen','" + specimen + "', "
							+ shot_number + "," + artist + "," + copyright + "," + license + ")";
                    stmt = DBUtil.getStatement(connection, "importImages"); 
					stmt.executeUpdate(query);
				} catch (Exception e) {
					s_log.error("importImages() e:" + e);
				} finally {
                    DBUtil.close(stmt, rset, this, "importImages()");			
			    }
			}
		}
		return found_images;
	}

	private String getFromDB(Connection connection, String table, int id) {

		String query;
		String value = "";
        Statement stmt = null;
        ResultSet rset = null;
		try {
			query = "select " + table + "  from " + table
					+ " where " + id + " = " + id;

			stmt = DBUtil.getStatement(connection, "importImages");
			rset = stmt.executeQuery(query);
			while (rset.next()) {
				value = rset.getString(1);
			}
		} catch (Exception e) {
			s_log.error("getFromDB() e:" + e);
		} finally {
            DBUtil.close(stmt, rset, this, "importImages()");			
		}
		return value;
	}

	private void writeUniqValues(Connection connection,
		String docBase, String column) {
		
        Statement stmt = null;
        ResultSet rset = null;
		try {
			File outputFile = new File(docBase + "/" + column + "_gen_inc.jsp");
			FileWriter outFile = new FileWriter(outputFile);

			stmt = DBUtil.getStatement(connection, "writeUniqValues");
			String query = "select distinct id," + column
					+ " from " + column
					+ " order by " + column;
			rset = stmt.executeQuery(query);
			String theValue;
			int theId;
			boolean first = true;
			outFile.write(
				"<%@ taglib uri=\"/WEB-INF/struts-bean.tld\" prefix=\"bean\" %>\n");
			outFile.write(
				"<%@ taglib uri=\"/WEB-INF/struts-html.tld\" prefix=\"html\" %>\n");
			outFile.write("<html:select property =\"" + column + "\">\n");
			while (rset.next()) {
				theId = rset.getInt(1);
				theValue = rset.getString(2);
				if ((theValue != null) && (!theValue.equals(""))) {
					outFile.write("<html:option value=\"" + theId + "\"");
					outFile.write(">" + theValue + "</html:option>\n");
				}
			}
			outFile.write("</html:select>\n");
			outFile.close();
		} catch (Exception e) {
			s_log.error("writeUniqValues e:" + e);
		} finally {
            DBUtil.close(stmt, rset, this, "writeUniqValues()");			
		}
	}

}
