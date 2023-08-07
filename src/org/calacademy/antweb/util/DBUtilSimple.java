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

    /* */
        // Was called from SessionRequestFilter.init() because it can not call getDataSource as a struts action class can.
        public static DataSource getDataSource() {
            MysqlDataSource ds = null;
            String jdbcUrl = "jdbc:mysql://mysql:3306/ant?autoReconnect=true&useUnicode=true&characterEncoding=UTF-8&characterSetResults=utf8&connectionCollation=utf8_general_ci";
            ds = new MysqlDataSource();
            ds.setURL(jdbcUrl);
            ds.setUser("antweb");
            ds.setPassword(AntwebProps.getDbPwd());
            return ds;
        }



    // Using C3P0. Should be better, but causes errors. Such as:
    // 2023-07-27T04:57:40,353 ERROR http-nio-8080-exec-155 org.calacademy.antweb.util.SessionRequestFilter.class - doFilter() 2023-07-27 04:57:40 See https://www.antweb.org/web/log/srfExceptions.jsp for case#:389430164 e:com.mysql.cj.jdbc.exceptions.CommunicationsException: The last packet successfully received from the server was 109,445,918 milliseconds ago. The last packet sent successfully to the server was 109,445,918 milliseconds ago. is longer than the server configured value of 'wait_timeout'. You should consider either expiring and/or testing connection validity before use in your application, increasing the server configured values for client timeouts, or using the Connector/J connection property 'autoReconnect=true' to avoid this problem. target:https://www.antweb.org/locality.do?code=JTL052550 startTime:Thu Jul 27 04:57:40 PDT 2023<br><b>Exception:</b>com.mysql.cj.jdbc.exceptions.CommunicationsException: The last packet successfully received from the server was 109,445,918 milliseconds ago. The last packet sent successfully to the server was 109,445,918 milliseconds ago. is longer than the server configured value of 'wait_timeout'. You should consider either expiring and/or testing connection validity before use in your application, increasing the server configured values for client timeouts, or using the Connector/J connection property 'autoReconnect=true' to avoid this problem. userAgent:Mozilla/5.0 (compatible; AhrefsBot/7.0; +http://ahrefs.com/robot/) info: referer:null user-agent:Mozilla/5.0 (compatible; AhrefsBot/7.0; +http://ahrefs.com/robot/) ----Params:code=JTL052550 ----Headers: host:www.antweb.org user-agent:Mozilla/5.0 (compatible; AhrefsBot/7.0; +http://ahrefs.com/robot/) accept: accept-encoding:deflate, gzip, br x-forwarded-for:51.222.253.7 x-forwarded-proto:https

    /*

    public static Date fetchDate = null;
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

    Maybe this would help with the stacktraces:
 2023-07-25 11:03:47 See https://www.antweb.org/web/log/srfExceptions.jsp for case#:238597394 e:com.mysql.cj.jdbc.exceptions.CommunicationsException: The last packet successfully received from the server was 30,941,989 milliseconds ago. The last packet sent successfully to the server was 30,941,990 milliseconds ago. is longer than the server configured value of 'wait_timeout'. You should consider either expiring and/or testing connection validity before use in your application, increasing the server configured values for client timeouts, or using the Connector/J connection property 'autoReconnect=true' to avoid this problem. target:https://www.antweb.org/images.do?rank=subspecies&genus=polyrhachis&species=mucronata&subspecies=bismarckensis&project=worldants&caste=brachypterous startTime:Tue Jul 25 11:03:47 PDT 2023
 Exception:com.mysql.cj.jdbc.exceptions.CommunicationsException: The last packet successfully received from the server was 30,941,989 milliseconds ago. The last packet sent successfully to the server was 30,941,990 milliseconds ago. is longer than the server configured value of 'wait_timeout'. You should consider either expiring and/or testing connection validity before use in your application, increasing the server configured values for client timeouts, or using the Connector/J connection property 'autoReconnect=true' to avoid this problem. userAgent:Mozilla/5.0 (compatible; DataForSeoBot/1.0; +https://dataforseo.com/dataforseo-bot) stacktrace:

 StackTrace:com.mysql.cj.jdbc.exceptions.CommunicationsException: The last packet successfully received from the server was 30,941,989 milliseconds ago. The last packet sent successfully to the server was 30,941,990 milliseconds ago. is longer than the server configured value of 'wait_timeout'. You should consider either expiring and/or testing connection validity before use in your application, increasing the server configured values for client timeouts, or using the Connector/J connection property 'autoReconnect=true' to avoid this problem.
	at com.mysql.cj.jdbc.exceptions.SQLError.createCommunicationsException(SQLError.java:174)
	at com.mysql.cj.jdbc.exceptions.SQLExceptionsMapping.translateException(SQLExceptionsMapping.java:64)
	at com.mysql.cj.jdbc.StatementImpl.executeQuery(StatementImpl.java:1198)
	at com.mchange.v2.c3p0.impl.NewProxyStatement.executeQuery(NewProxyStatement.java:35)
	at org.calacademy.antweb.home.ServerDb.getDebug(ServerDb.java:42)
	at org.calacademy.antweb.util.SessionRequestFilter.doFilter(SessionRequestFilter.java:94)
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:189)

        } else {
            if (AntwebUtil.hrsSince(fetchDate) > 5) {
                ComboPooledDataSource retVal = cpds;
                cpds = null;
                fetchDate = new Date();
                return retVal;
            }
        */

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
