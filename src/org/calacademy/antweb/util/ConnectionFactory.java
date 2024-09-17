package org.calacademy.antweb.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import java.beans.PropertyVetoException;

public class ConnectionFactory {

    private static ComboPooledDataSource dataSource;

    private static final Log s_log = LogFactory.getLog(ConnectionFactory.class);

    static {
        try {
            // Initialize C3P0 datasource
            dataSource = new ComboPooledDataSource();
            dataSource.setDriverClass("com.mysql.cj.jdbc.Driver"); // MySQL driver
            dataSource.setJdbcUrl("jdbc:mysql://mysql:3306/ant");
            dataSource.setUser("antweb");
            dataSource.setPassword("f0rm1c6");

            // Optional C3P0 settings
            dataSource.setMinPoolSize(10);
            dataSource.setAcquireIncrement(5);
            dataSource.setMaxPoolSize(100);
            dataSource.setMaxIdleTime(1000);
        } catch (PropertyVetoException e) {
            s_log.error("static block e: " + e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

}


    // Use this way (See TaxonProxy.java for example implemntation):
    /*
            Connection connection = null;
            try {
                connection = ConnectionFactory.getConnection();
                // Perform database operations here
            } catch (SQLException e) {
                // Handle exceptions
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        s_log.error("() e:" + e);
                    }
                }
            }
     */


    /* In the WEB-INF/web.xml
    <resource-ref>
    <description>DB Connection</description>
    <res-ref-name>jdbc/YourDataSource</res-ref-name>
    <res-type>javax.sql.DataSource</res-type>
    <res-link>jdbc/YourDataSource</res-link>
    </resource-ref>
    */
