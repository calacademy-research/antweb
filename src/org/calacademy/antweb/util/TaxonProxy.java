    package org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.Collection;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class TaxonProxy {

    private Connection m_connection = null;

    private static final Log s_log = LogFactory.getLog(TaxonProxy.class);

    /**
     *  TaxonProxy handles Taxon fetches for the app. It goes to the TaxonMgr when it is loaded.
     *  Otherwise it will go to TaxonHome, to fetch from the database.
     */

    public TaxonProxy() {
    }

    /*
    public static Collection<Taxon> getTaxa() {
        return TaxonMgr.getTaxa();
    }

    public static int getValidTaxonCount() {
      return TaxonMgr.getValidTaxonCount();
    }
*/


    public static ArrayList<Taxon> getSubfamilies() {
        ArrayList<Taxon> subfamilies = null;
        Connection connection = null;
        if (TaxonMgr.isPopulated()) {
            subfamilies = TaxonMgr.getSubfamilies();
        }
        if (!TaxonMgr.isPopulated() || subfamilies == null) {
            try {
                connection = ConnectionFactory.getConnection();
                subfamilies = TaxonMgr.popSubfamilies(connection);
            } catch (SQLException e) {
                s_log.warn("getSubfamilies() e:" + e);
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        s_log.error("getSubfamily() e:" + e);
                    }
                }
            }

        }
        return subfamilies;
    }

    public static Subfamily getSubfamily(String subfamilyName) {
        Subfamily subfamily = null;
        Connection connection = null;
        if (TaxonMgr.isPopulated()) {
            subfamily = TaxonMgr.getSubfamily(subfamilyName);
        }
        // if TaxonMgr is populated but return fails. Maybe HttpUtil.isOffline()?
        if (!TaxonMgr.isPopulated() || subfamily == null) {
            try {
                connection = ConnectionFactory.getConnection();
                subfamily = (new TaxonDb(connection)).getSubfamily(subfamilyName);
            } catch (SQLException e) {
                s_log.warn("getSubfamily() subfamilyName:" + subfamilyName + "e:" + e);
            } catch (AntwebException e) {
                s_log.warn("getSubfamily() subfamilyName:" + subfamilyName + "e:" + e);
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        s_log.error("getSubfamily() e:" + e);
                    }
                }
            }
        }
        A.log("getSubfamily() subfamilyName:" + subfamilyName + " subfamily:" + subfamily + " taxonMgr.isPopulated:" + TaxonMgr.isPopulated() + " connection:" + connection);
        return subfamily;
    }

    /*
    public static ArrayList<Genus> getGenera() {
        ArrayList<Genus> genera = null;
        Connection connection = null;
        if (TaxonMgr.isPopulated()) {
            genera = TaxonMgr.getGenera();
        }
        if (!TaxonMgr.isPopulated() || genera == null) {
            try {
                connection = ConnectionFactory.getConnection();
                genera = TaxonMgr.popGenera(connection);
            } catch (SQLException e) {
                s_log.warn("getGenera() e:" + e);
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        s_log.error("getGenera() e:" + e);
                    }
                }
            }
        }
        return genera;
    }
*/

    public static Genus getGenus(String genusName) {
        //String genusName = "camponotus";
        Genus genus =  null;
        Connection connection = null;
        if (TaxonMgr.isPopulated()) {
            genus = TaxonMgr.getGenus(genusName);
        }
        // if TaxonMgr is populated but return fails. Maybe HttpUtil.isOffline()?
        if (!TaxonMgr.isPopulated() || genus == null) {
            try {
                connection = ConnectionFactory.getConnection();
                //A.log("getGenus() connection:" + connection);
                if (connection != null)
                    genus = (new TaxonDb(connection)).getGenus(genusName);
            } catch (SQLException e) {
                A.log("getGenus() genusName:" + genusName + "e:" + e);
            } catch (AntwebException e) {
                A.log("getGenus() genusName:" + genusName + "e:" + e);
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        A.log("getGenus() e:" + e);
                    }
                }
            }
        }
        //A.log("getGenus() genusName:" + genusName + " genus:" + genus + " taxonMgr.isPopulated:" + TaxonMgr.isPopulated() + " connection:" + connection);
        return genus;
    }

    public static String inferSubfamily(String genusName) {
        Genus genus = getGenus(genusName);
        //A.log("inferSubfamily() genusName:" + genusName + " genus:" + genus);
        if (genus == null) return null;
        //A.log("inferSubfamily() genusName:" + genusName + " genus:" + genus + " subfamily:" + genus.getSubfamily());
        return genus.getSubfamily();
    }

    /*
    public static Genus getGenusFromName(String genusName) {
        return TaxonMgr.getGenusFromName(genusName);
    }
*/

    public static Taxon getTaxon(String taxonName) {
        Taxon taxon = null;
        Connection connection = null;
        if (TaxonMgr.isPopulated()) {
            taxon = TaxonMgr.getTaxon(taxonName);
        }
        if (!TaxonMgr.isPopulated() || taxon == null) {
            try {
                connection = ConnectionFactory.getConnection();
                //A.log("getTaxon() connection:" + connection);
                if (connection != null)
                    taxon = (new TaxonDb(connection)).getTaxon(taxonName);
            } catch (SQLException e) {
                A.log("getTaxon() genusName:" + taxonName + "e:" + e);
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        A.log("getTaxon() e:" + e);
                    }
                }
            }
        }
        A.log("getTaxon() taxonName:" + taxonName + " taxon:" + taxon + " taxonMgr.isPopulated:" + TaxonMgr.isPopulated() + " connection:" + connection);
        return taxon;
    }

    /*
    public static String getSubgenus(String taxonName) {
        return TaxonMgr.getSubgenus(taxonName);
    }

    public static List<String> getSubgenera(String genusName) {
        return TaxonMgr.getSubgenera(genusName);
    }

    public static Species getSpecies(Connection connection, String taxonName) {
        return TaxonMgr.getSpecies(connection, taxonName);
    }
*/
    public void setConnection(Connection connection) {
        m_connection = connection;
    }
    public Connection getConnection() {
        return m_connection;
    }


}

