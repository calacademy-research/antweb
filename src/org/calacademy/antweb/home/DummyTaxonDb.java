package org.calacademy.antweb.home;

import java.util.*;
import java.util.Date;
import java.sql.*;

import javax.servlet.http.*;
import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;

public class DummyTaxonDb extends AntwebDb {

    private static Log s_log = LogFactory.getLog(DummyTaxonDb.class);

    public DummyTaxonDb(Connection connection) {
      super(connection);
    }

}
