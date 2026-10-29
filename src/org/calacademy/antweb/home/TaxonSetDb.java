package org.calacademy.antweb.home;

import java.util.*;

//import org.apache.regexp.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import java.sql.*;

public abstract class TaxonSetDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(TaxonSetDb.class);

    public TaxonSetDb(Connection connection) {
      super(connection);
    }

    public abstract ArrayList<Taxon> getTaxa(String name) throws SQLException ;
    
    public ArrayList<Taxon> getTaxa(Overview overview) throws SQLException {
        ArrayList<Taxon> taxa = new ArrayList<>();
        
        String theQuery = "";
        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getTaxa()");

            theQuery = " select taxon.taxon_name, taxon.subfamily, taxon.genus, taxon.species, taxon.subspecies, taxon.author_date" 
              + " from taxon" 
              + overview.getFetchChildrenClause()
              + " and taxon.taxarank in ('species', 'subspecies')";
            theQuery += " order by subfamily, genus, species, subspecies, author_date";

            s_log.debug("getTaxa() query:" + theQuery);
            rset = stmt.executeQuery(theQuery);

            int count = 0;
            while (rset.next()) {
                ++count;
                Taxon taxon = new Taxon();
                //taxon.setTaxonName(rset.getString("taxon_name"));
                taxon.setSubfamily(rset.getString("subfamily"));
                taxon.setGenus(rset.getString("genus"));
                taxon.setSpecies(rset.getString("species"));
                taxon.setSubspecies(rset.getString("subspecies"));
                taxon.setAuthorDate(rset.getString("author_date"));                
                taxa.add(taxon);
            }
            if (AntwebProps.isDevMode()) if (count == 0) s_log.error("getTaxa() not found overview:" + overview);
        } catch (SQLException e) {
            s_log.error("getTaxa() overview:" + overview + " exception:" + e + " theQuery:" + theQuery);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "getTaxa()");
        }

        if (AntwebProps.isDevMode()) s_log.info("getTaxa() overview:" + overview + " query:" + theQuery);        
        return taxa;
    }       

// ---------------------------------------------------------------------------------------


    // This should only need to be run once. Run from UtilData.
    // Retained in scheduler because a specimen could be removed from a specimen list.
    public static String dataCleanup(Connection connection) throws SQLException {
      return dataCleanup(connection, BlankNameCleanup.isOn());
    }

    // blankSafe: the morphospecies cleanups use BlankNameCleanup's rule (decided once by set 2's dataCleanup).
    public static String dataCleanup(Connection connection, boolean blankSafe) throws SQLException {
      String message = "";
      // Curator rows whose taxon row is gone (for example a morphospecies whose specimens are
      // missing tonight) are parked first and come back when the name exists again
      // (CuratorTaxonSetDb). The deletes below then only remove what the system can rebuild.
      message += "parked:" + new CuratorTaxonSetDb(connection).parkCuratorRowsWithoutTaxon() + " ";
      // Only operating on the Editable ones.
      GeolocaleTaxonDb geolocaleTaxonDb = new GeolocaleTaxonDb(connection);
      message += geolocaleTaxonDb.deleteUncuratedMorphosWithoutSpecimen(blankSafe);
      message += " " + new ProjTaxonDb(connection).deleteUncuratedMorphosWithoutSpecimen(blankSafe);
      message += " " + geolocaleTaxonDb.deleteGeolocaleTaxaWithoutTaxon();     

      int c = new UtilDb(connection).deleteFrom("proj_taxon", "where taxon_name not in (select taxon_name from taxon) and project_name not in ('worldants')");
      s_log.debug("dataCleanup() records deleted:" + c);

      return message;   
    }

    //update the taxon_names with the current_valid_names
    abstract String updateTaxonNames() throws SQLException;
    public static String updateTaxonSetTaxonNames(Connection connection) throws SQLException {
      String message = "";
      message += new MuseumTaxonDb(connection).updateTaxonNames();
      message += " " + new BioregionTaxonDb(connection).updateTaxonNames();
      message += " " + new GeolocaleTaxonDb(connection).updateTaxonNames();
      message += " " + new ProjTaxonDb(connection).updateTaxonNames();
      
      return message;   
    }

    /**
     * Renames the rows of one place's list (key; null: every place of the table) whose name is a synonym or another name
     * with a current valid name (PlaceTotals.renamedCondition) to that valid name; when the valid name is on the list
     * already, the old row is deleted (merged: the counts that follow count its specimens under the valid name,
     * PlaceTotals.specimensByListNameSql). Returns the rows renamed or merged. Runs before the list is counted
     * (2026-10-22: museum and bioregion lists were renamed after their counts and totals, nightly set 3).
     */
    public int renameToValid(String type, String key) throws SQLException {
        String table = PlaceTotals.listTable(type);
        String keyColumn = PlaceTotals.listKey(type);
        String query = "select l." + keyColumn + ", t.taxon_name, t.current_valid_name from " + table + " l"
            + " join taxon t on t.taxon_name = l.taxon_name where " + PlaceTotals.renamedCondition("t")
            + (key != null ? " and l." + keyColumn + " = ?" : "")
            + (PlaceTotals.PROJECT.equals(type) ? " and l.project_name not in ('worldants', 'allantwebants')" : "");
        List<String[]> rows = new ArrayList<>();
        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            if (key != null) stmt.setString(1, key);
            try (ResultSet rset = stmt.executeQuery()) {
                while (rset.next()) rows.add(new String[] {rset.getString(1), rset.getString(2), rset.getString(3)});
            }
        }
        // Merge explicitly (a list table may have no unique key on place and name, so an update could make a duplicate).
        String exists = "select count(*) from " + table + " where " + keyColumn + " = ? and taxon_name = ?";
        String delete = "delete from " + table + " where " + keyColumn + " = ? and taxon_name = ?";
        String rename = "update " + table + " set taxon_name = ? where " + keyColumn + " = ? and taxon_name = ?";
        for (String[] row : rows) {
            boolean there;
            try (PreparedStatement stmt = getConnection().prepareStatement(exists)) {
                stmt.setString(1, row[0]);
                stmt.setString(2, row[2]);
                try (ResultSet r = stmt.executeQuery()) { there = r.next() && r.getInt(1) > 0; }
            }
            if (there) {
                try (PreparedStatement stmt = getConnection().prepareStatement(delete)) {
                    stmt.setString(1, row[0]);
                    stmt.setString(2, row[1]);
                    stmt.executeUpdate();
                }
            } else {
                try (PreparedStatement stmt = getConnection().prepareStatement(rename)) {
                    stmt.setString(1, row[2]);
                    stmt.setString(2, row[0]);
                    stmt.setString(3, row[1]);
                    stmt.executeUpdate();
                }
            }
        }
        return rows.size();
    }

    /*
    public boolean exists(String tableName, String taxonName, String whereClause) throws SQLException {
        Statement stmt = null;
        ResultSet rset = null;
        String query = "select * from " + tableName + " where taxon_name = " + DBUtil.sqlQuote(taxonName) + " and " + whereClause;
        try {
            stmt = DBUtil.getStatement(getConnection(), "exists()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                return true;
            }
        } catch (SQLException e) {
            s_log.error("exists() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "exists()");
        }
        return false;
    }

    // Can be tested with: https://localhost/utilData.do?action=updateTaxonSetTaxonNames
    protected int updateTaxonSetTaxonName(String tableName, String taxonName, String currentValidName, String whereClause) throws SQLException {
      // Update can trigger SQLIntegrityConstraintViolationException so check for existence first.
      // If exists, just delete the old one. If it doesn't, the update will work.
        int c = 0;
        int x = 0;
        if (exists(tableName, currentValidName, whereClause)) {
            x = deleteTaxonSetTaxonName(tableName, taxonName, whereClause);
        } else {
            c = updateTaxonSetTaxonNameExec(tableName, taxonName, currentValidName, whereClause);
        }
        A.log("updateTaxonSetTaxonName() c:" + c + " x:" + x + " tableName:" + tableName + " taxonName:" + taxonName + " currentValidName:"+ currentValidName + " whereClause:" + whereClause);
        return 1;
    }
*/

    //protected int updateTaxonSetTaxonNameExec(String tableName, String taxonName, String currentValidName, String whereClause) throws SQLException {
    protected int updateTaxonSetTaxonName(String tableName, String taxonName, String currentValidName, String whereClause) throws SQLException {
      int c = 0;
      Statement stmt = null;
	  String dml = "update " + tableName + " set taxon_name = " + DBUtil.sqlQuote(currentValidName) + " where taxon_name = " + DBUtil.sqlQuote(taxonName) + " and " + whereClause;
      try {
          stmt = DBUtil.getStatement(getConnection(), "TaxonSetDb.updateTaxonSetTaxonName()");
          c = stmt.executeUpdate(dml);
          //A.log("updateTaxonSetTaxonName() c:" + c + " dml: " + dml);
      } catch (SQLIntegrityConstraintViolationException e) {
          // This will slow things down, but tricky to replace.
          // We delete the record that we could not update to point at the new key which already exists.
          deleteTaxonSetTaxonName(tableName, taxonName, whereClause);
      } catch (SQLException e) {
        s_log.error("updateTaxonSetTaxonName() e:" + e);
        throw e;
      } finally {
        DBUtil.close(stmt, null, "TaxonSetDb.updateTaxonSetTaxonName()");
      }
      return c;
    }

    protected int deleteTaxonSetTaxonName(String tableName, String taxonName, String whereClause) throws SQLException {
      int x = 0;
      Statement stmt = null;
      try {
          String dml = "delete from " + tableName + " where taxon_name = " + DBUtil.sqlQuote(taxonName) + " and " + whereClause;
          stmt = DBUtil.getStatement(getConnection(), "TaxonSetDb.deleteTaxonSetTaxonName()");
          x = stmt.executeUpdate(dml);
      } catch (SQLException e) {
        s_log.error("TaxonSetDb.deleteTaxonSetTaxonName() e:" + e);
        throw e;
      } finally {
        DBUtil.close(stmt, null, "TaxonSetDb.deleteTaxonSetTaxonName()");
      }
      return x;
    }


    public static String updateTaxonSetSources(Connection connection) throws SQLException {
        int pc = updateTaxonSetSource("proj_taxon", connection);
        int bc = updateTaxonSetSource("bioregion_taxon", connection);
        int gc = updateTaxonSetSource("geolocale_taxon", connection);
        String message = "updateTaxonSetSources pc:" + pc + " bc:" + bc + " gc:" + gc;
        s_log.warn(message);
        return message;
    }

    protected static int updateTaxonSetSource(String tableName, Connection connection) throws SQLException {
        int c = 0;
        Statement stmt = null;
        String dml = "update " + tableName + " set source = 'specimen' where specimen_count > 0 and source = 'antcat'";
        try {
            stmt = DBUtil.getStatement(connection, "TaxonSetDb.updateTaxonSetSource()");
            c = stmt.executeUpdate(dml);
            //A.log("updateTaxonSetSource() c:" + c + " dml: " + dml);
        } catch (SQLException e) {
            s_log.error("updateTaxonSetSource() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, null, "TaxonSetDb.updateTaxonSetSource()");
        }
        return c;
    }

}
