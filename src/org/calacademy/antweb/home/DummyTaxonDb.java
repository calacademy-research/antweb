package org.calacademy.antweb.home;

import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class DummyTaxonDb extends AntwebDb {

    private static Log s_log = LogFactory.getLog(DummyTaxonDb.class);

    public DummyTaxonDb(Connection connection) {
      super(connection);
    }

}
