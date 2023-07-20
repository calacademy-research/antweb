package org.calacademy.antweb.util;

import com.mchange.v2.c3p0.ComboPooledDataSource;

import java.sql.*;
import javax.sql.*;

import org.calacademy.antweb.util.*;

import com.mchange.v2.c3p0.*;
import com.mysql.cj.jdbc.MysqlDataSource;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.mchange.v2.c3p0.impl.*;

import org.calacademy.antweb.AntFormatter;
import org.jetbrains.annotations.Nullable;

public class DBUtilSimple {

    private static ComboPooledDataSource cpds;

    private static final Log s_log = LogFactory.getLog(DBUtil.class);

    public static DataSource getDataSource() throws SQLException, java.beans.PropertyVetoException {
        if (cpds == null) {
            String jdbcUrl = "jdbc:mysql://mysql:3306/ant?autoReconnect=true&useUnicode=true&characterEncoding=UTF-8&characterSetResults=utf8&connectionCollation=utf8_general_ci";
            cpds = new ComboPooledDataSource();
            cpds.setDriverClass("com.mysql.jdbc.Driver");
            cpds.setJdbcUrl(jdbcUrl);
            cpds.setUser("antweb");
            cpds.setPassword(AntwebProps.getDbPwd());

            cpds.setInitialPoolSize(5);
            cpds.setMinPoolSize(5);
            cpds.setMaxPoolSize(20);
            cpds.setMaxStatements(100);
            cpds.setMaxIdleTime(0);
            A.log("getDataSource() cpds:" + cpds);
        }
        return cpds;
    }


    public static Connection getConnection(DataSource dataSource) throws SQLException {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
        } catch (Exception e) {
            // Fail gracefully, without stacktrace, upon server shutdown
            s_log.error("getConnection() e:" + e);
        }

        return connection;
    }

    public static boolean close(Connection conn, Statement stmt, ResultSet rset, Object object) {
        boolean success = true;
        String objectName = null;
        if (object != null) objectName = object + " ";

        try {
            if (rset != null) rset.close();
        } catch (SQLException e) {
            success = false;
            s_log.error("close() " + objectName + " resultSet close Failure:" + e);
        }

        try {
            if (stmt != null) stmt.close();
        } catch (SQLException e) {
            success = false;
            s_log.error("close() " + objectName + "statement close Failure:" + e);
        }

        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e)  {
            success = false;
            s_log.error("close() " + objectName + "connection close Failure:" + e);
        }

        return success;
    }
}
