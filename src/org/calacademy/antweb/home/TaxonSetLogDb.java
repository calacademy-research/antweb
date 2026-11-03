package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

//import org.apache.regexp.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;

/**
 * Base class of the history and dispute tables of curated species lists: GeolocaleTaxonLogDb (geolocale lists)
 * and ProjTaxonLogDb (project lists). Declares archiveSpeciesList and the dispute methods; no code of its own.
 */
public abstract class TaxonSetLogDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(TaxonSetLogDb.class);

    public TaxonSetLogDb(Connection connection) {
      super(connection);
    }

    public abstract void archiveSpeciesList(String speciesListName, Login curatorLogin) throws SQLException;
    public abstract void insertDispute(TaxonSet taxonSet) throws SQLException;
    public abstract void removeDispute(String speciesListName, String taxonName) throws SQLException;
    public abstract TaxonSet getDispute(String speciesListName, String taxonName) throws SQLException ;
    public abstract ArrayList<TaxonSet> getDisputes(String speciesListName, String taxonName) throws SQLException ;

    /** The list type (ListRemovalLog.GEOLOCALE, PROJECT) and key (geolocale id, project name) of a list row. */
    public abstract String[] listTypeAndKey(TaxonSet taxonSet);

    /**
     * Brian Fisher, 2026-11-03 (transparency): a dispute (a name a curator removed from the list) with the curator's reason,
     * shown with the name (the view All specimen names). reason: optional, at most 255 characters (ListRemovalLog.clip).
     */
    public void insertDispute(TaxonSet taxonSet, String reason) throws SQLException {
        insertDispute(taxonSet);
        String r = ListRemovalLog.clip(reason, 255);
        if (r == null || taxonSet == null) return;
        ListRemovalLog.ensureSchema(getConnection());   // the reason column (normally added at startup)
        String[] tk = listTypeAndKey(taxonSet);
        String table = ListRemovalLog.GEOLOCALE.equals(tk[0]) ? "geolocale_taxon_dispute" : "proj_taxon_dispute";
        try (java.sql.PreparedStatement stmt = getConnection().prepareStatement("update " + table + " set reason = ? where "
                + ListRemovalLog.keyColumn(tk[0]) + " = ? and taxon_name = ?")) {
            stmt.setString(1, r);
            stmt.setString(2, tk[1]);
            stmt.setString(3, taxonSet.getTaxonName());
            stmt.executeUpdate();
        }
    }

}
