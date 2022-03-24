package org.calacademy.antweb;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;

import org.calacademy.antweb.Formatter;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
import org.calacademy.antweb.util.AntwebProps;

public class SpecimenFieldSummary {

    private static Log s_log = LogFactory.getLog(SpecimenFieldSummary.class);
	
	String subfamily;
	String genus;
	String species;
	String field;
	String project;
	Connection connection;
	HashMap results;
	
	public Connection getConnection() {
		return connection;
	}
	public void setConnection(Connection connection) {
		this.connection = connection;
	}

	public String getField() {
		return field;
	}
	public void setField(String field) {
		this.field = field;
	}

	public String getGenus() {
		return genus;
	}
	public void setGenus(String genus) {
		this.genus = genus;
	}

	public HashMap getResults() {
		return results;
	}
	
	public void setResults() {

		String theQuery = null;
		results = new HashMap();

		if (Utility.blank(subfamily) && Utility.blank(genus) && Utility.blank(species)
				|| Utility.blank(field)) {
			// do nothing
			
		} else {
			
			ResultSet rset = null;
			try {

				if (Utility.blank(project)) {
					theQuery = getQueryWithoutProject();
				} else {
					theQuery = getQueryWithProject();
				}
				
				Statement stmt = connection.createStatement();
				rset = stmt.executeQuery(theQuery);
				String fieldValue = "";
				Formatter formatter = new Formatter();	
				while (rset.next()) {
					fieldValue = formatter.capitalizeFirstLetter(rset.getString(2));
					if (!results.containsKey(fieldValue)) {
						results.put(fieldValue, new ArrayList());
					}
					((ArrayList) results.get(fieldValue)).add(rset.getString(1));
				}
				stmt.close();
				
				
			} catch (Exception e) {
				s_log.error("setResults() e:" + e);
			}
		}
	}
	
	private String getQueryWithoutProject() {
		ArrayList where = new ArrayList();
		String theQuery;
		
		if (Utility.notBlank(subfamily)) {
			where.add("subfamily='" + subfamily + "'");
		}
		if (Utility.notBlank(genus)) {
			where.add("genus='" + genus  + "'");
		}
		if (Utility.notBlank(species)) {
			where.add("species='" + species + "'");
		}

		String whereString = Utility.andify(where);

		theQuery = "select code, " + field + " from specimen where "
				+ whereString;
		
		return theQuery;
	}

	private String getQueryWithProject() {
		ArrayList where = new ArrayList();
		String theQuery;
		
		if (Utility.notBlank(subfamily)) {
			where.add("subfamily='" + subfamily + "'");
		}
		if (Utility.notBlank(genus)) {
			where.add("genus='" + genus  + "'");
		}
		if (Utility.notBlank(species)) {
			where.add("species='" + species + "'");
		}
		
		String localitySpecifier = AntwebProps.getProp(project + ".locality");
		if (Utility.notBlank(localitySpecifier)) {
			where.add(localitySpecifier);
		}


		String whereString = Utility.andify(where);

		theQuery = "select code, " + field + " from specimen where "
				+ whereString;
		
		return theQuery;
	}
	
	private String getQueryWithProject2() {
		ArrayList where = new ArrayList();
		String theQuery;
		
		if (Utility.notBlank(subfamily)) {
			where.add("specimen.subfamily='" + subfamily + "'");
		}
		if (Utility.notBlank(genus)) {
			where.add("specimen.genus='" + genus  + "'");
		}
		if (Utility.notBlank(species)) {
			where.add("specimen.species='" + species + "'");
		}
		
		where.add("specimen.taxon_name = proj_taxon.taxon_name");
		where.add("proj_taxon.project_name ='" + project + "'");

		String whereString = Utility.andify(where);

		theQuery = "select specimen.code, specimen." + field + " from specimen, proj_taxon where "
				+ whereString;
		
		return theQuery;
	}
	
	public void setResults(HashMap results) {
		this.results = results;
	}

	public String getSpecies() {
		return species;
	}
	public void setSpecies(String species) {
		this.species = species;
	}

	public String getSubfamily() {
		return subfamily;
	}
	public void setSubfamily(String subfamily) {
		this.subfamily = subfamily;
	}

	public String getProject() {
		return project;
	}
	public void setProject(String project) {
		this.project = project;
	}


}
