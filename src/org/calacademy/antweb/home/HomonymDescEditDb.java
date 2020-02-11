package org.calacademy.antweb.home;

import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class HomonymDescEditDb extends DescEditDb{

    private static Log s_log = LogFactory.getLog(HomonymDescEditDb.class);
    
    public HomonymDescEditDb(Connection connection) {
      super(connection);
    }

    // To be overrides by DescEditDb.java
    public String getTableName() {
      return "description_homonym";
    }
}
