package org.calacademy.antweb.home;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.AntFormatter;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.geolocale.AntwikiTaxonCountry;
import org.calacademy.antweb.util.DBUtil;

import java.sql.*;
import java.util.ArrayList;

public class AntwikiTaxonCountryDb extends AntwebDb {
    
    private static final Log s_log = LogFactory.getLog(AntwikiTaxonCountryDb.class);
        
    public AntwikiTaxonCountryDb(Connection connection) {
      super(connection);
    }

    // deleteValidTaxa/insertValidTaxa and deleteFossilTaxa/insertFossilTaxa were used only by
    // upload.do's "Upload a Data File" tool, removed in 2026-10.

    //----------------------------------------------------------------------------------------

    
    
/*    
    // Data has been loaded to antwiki_Taxon_country, now needs to be pushed to...
    public String finishCountryUpload() throws SQLException {
        String messageStr = "";

        GeolocaleTaxonDb geolocaleTaxonDb = new GeolocaleTaxonDb(getConnection());
        return geolocaleTaxonDb.finishCountryUpload();
    }
 */
 
    public String setIntroducedSpecimen() {
        String message = "Specimen flagged as introduced:";
        String query = "";
        ResultSet rset = null;
        Statement stmt = null;
        int c = 0;
        try {
            ProjectDb projectDb = new ProjectDb(getConnection());
            stmt = DBUtil.getStatement(getConnection(), "setIntroducedSpecimen()");
            query = "select s.code from antwiki_taxon_country a, specimen s where a.taxon_name = s.taxon_name and a.country = s.country and a.is_introduced = 1;";
            s_log.debug("updateIntroducedSpecimen() query:" + query);
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                ++c;
                String code = rset.getString("code");
                updateIntroducedSpecimen(code);
            }
        } catch (SQLException e) {
            s_log.error("setIntroducedSpecimen() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "setIntroducedSpecimen()");
        }
        message += "" + c;
        return message;
    }

    private void updateIntroducedSpecimen(String code) throws SQLException {
        PreparedStatement stmt = null;
        String dml = null;
        try {
            dml = "update specimen set is_introduced = 1 where code = ?";
            stmt = DBUtil.getPreparedStatement(getConnection(), "updateIntroducedSpecimen()", dml);
            stmt.setString(1, code);
            stmt.executeUpdate();
        } catch (SQLException e) {
            s_log.warn("updateIntroducedSpecimen() e:" + e);
        } finally {
            DBUtil.close(stmt, "updateIntroducedSpecimen()");
        }
    }
    
}