package org.calacademy.antweb.home;

import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;


public class DummyTaxonDb extends AntwebDb {

    public static int s_dummyTaxonFetchCount = 0;
    public static int s_dummyHomonymFetchCount = 0;

    private static Log s_log = LogFactory.getLog(DummyTaxonDb.class);

    public DummyTaxonDb(Connection connection) {
      super(connection);
    }

    /* TO BE DEPRECATED. DummyTaxons should not be fetched and constructed. Just use getTaxon();
     * Dummy taxons should not be fetched from the database. In that case just use getTaxon.
     * GetTaxon has all the data of getDummyTaxon and more, with a single record fetch.
     * DummyTaxon are to be used when we want a taxon object constructed with data
     */
    public DummyTaxon getDummyTaxon(String taxonName) throws SQLException{
        return getDummyTaxon(taxonName, "taxon");
    }
    public DummyTaxon getDummyTaxon(String taxonName, String table)
            throws SQLException {

        if ("taxon".equals(table)) ++s_dummyTaxonFetchCount;
        if ("homonym".equals(table)) ++s_dummyHomonymFetchCount;

        // Not a fully instantiated one.  Just contains...
        DummyTaxon taxon = null;
        String query = "select family, subfamily, tribe, genus, subgenus, species, subspecies, status, "
                + " source, insert_method, line_num, access_group, current_valid_name, parent_taxon_name, fossil "
                + " from " + table + " where taxon_name = '" + taxonName + "'";
        Statement stmt = null;
        ResultSet rset = null;
        try {
            Connection connection = getConnection();
            stmt = DBUtil.getStatement(connection, "AntwebDb.getDummyTaxon()");
            stmt.execute(query);

            rset = stmt.getResultSet();

            while (rset.next()) {
                taxon = new DummyTaxon();
                taxon.setTaxonName(taxonName);
                taxon.setFamily(rset.getString("family"));
                taxon.setSubfamily(rset.getString("subfamily"));
                taxon.setTribe(rset.getString("tribe"));
                taxon.setGenus(rset.getString("genus"));
                taxon.setSubgenus(rset.getString("subgenus"));
                taxon.setSpecies(rset.getString("species"));
                taxon.setSubspecies(rset.getString("subspecies"));
                taxon.setStatus(rset.getString("status"));
                taxon.setSource(rset.getString("source"));
                taxon.setInsertMethod(rset.getString("insert_method"));
                taxon.setLineNum(rset.getInt("line_num"));
                taxon.setGroupId(rset.getInt("access_group"));
                taxon.setIsFossil(rset.getInt("fossil") == 1);

                String currentValidName = rset.getString("current_valid_name");
                if (currentValidName != null && !taxonName.equals(currentValidName.toLowerCase())) {
                    taxon.setCurrentValidName(currentValidName);
                }
                taxon.setParentTaxonName(rset.getString("parent_taxon_name"));

            }

            if (AntwebProps.isDevMode()
                    && false
                // && taxon.getIsFossil()
                // && "myrmicinaecrematogaster jtl-022".equals(taxonName)
            ) s_log.warn("getDummyTaxon() 1 taxonName:" + taxonName + " query:" + query);


        } catch (Exception e) {
            s_log.warn("getDummyTaxon() taxonName:" + taxonName + " table:" + table + " e:" + e);
        } finally {
            DBUtil.close(stmt, "AntwebDb.getDummyTaxon()");
        }

        //if (AntwebProps.isDevMode() && "myrmicinaecrematogaster jtl-022".equals(taxonName)) s_log.warn("getDummyTaxon() 3 taxonName:" + taxonName + " taxon:" + taxon);

        return taxon;
    }
}
