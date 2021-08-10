package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

//import org.apache.regexp.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;

public abstract class TaxonSetLogDb extends AntwebDb {

    private static Log s_log = LogFactory.getLog(TaxonSetLogDb.class);

    public TaxonSetLogDb(Connection connection) {
      super(connection);
    }

    public abstract void archiveSpeciesList(String speciesListName, Login curatorLogin) throws SQLException;
    public abstract void insertDispute(TaxonSet taxonSet) throws SQLException;
    public abstract void removeDispute(String speciesListName, String taxonName) throws SQLException;
    public abstract TaxonSet getDispute(String speciesListName, String taxonName) throws SQLException ;
    public abstract ArrayList<TaxonSet> getDisputes(String speciesListName, String taxonName) throws SQLException ;

}
