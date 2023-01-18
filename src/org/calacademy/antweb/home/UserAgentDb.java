package org.calacademy.antweb.home;

import java.sql.*;

import java.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.AntFormatter;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.ServerStatusAction;

public class UserAgentDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(UserAgentDb.class);

    public UserAgentDb(Connection connection) {
        super(connection);
    }

    public Set<String> getKnownAgents() {
        Set<String> knownAgentsSet = new HashSet<>();

        String query = "select name from user_agent";
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getKnownAgents()");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                String agent = rset.getString("name");
                knownAgentsSet.add(agent);
            }
        } catch (SQLException e) {
            s_log.warn("getKnownAgents() query:" + query + " e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "getKnownAgents()");
        }
        return knownAgentsSet;
    }

    public void saveAgent(String agent) {
        String dml = "";
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "saveAgent()");
            String val = AntFormatter.escapeQuotes(agent);
            dml = "insert into user_agent (name) values ('" + val + "')";
            stmt.execute(dml);

            //A.log("saveAgent() agent:" + agent);
        } catch (SQLIntegrityConstraintViolationException e) {
        } catch (SQLException e) {
            s_log.error("saveAgent() e:" + e + " dml:" + dml);
        } finally {
            DBUtil.close(stmt, "saveAgent()");
        }
    }

    public Set<String> getWhiteList() {
        Set<String> whiteList = new HashSet<>();

        String query = "select name from user_agent_whitelist";
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getWhiteList()");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                String agent = rset.getString("name");
                whiteList.add(agent);
            }
        } catch (SQLException e) {
            s_log.warn("getWhiteList() query:" + query + " e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "getWhiteList()");
        }
        return whiteList;
    }

    public void addToWhiteList(String agent) {
        String dml = "";
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "addToWhiteList()");
            String val = AntFormatter.escapeQuotes(agent);
            dml = "insert into user_agent_whitelist (name) values ('" + val + "')";
            stmt.execute(dml);

            //A.log("saveAgent() agent:" + agent);
        } catch (SQLIntegrityConstraintViolationException e) {
        } catch (SQLException e) {
            s_log.error("addToWhiteList() e:" + e + " dml:" + dml);
        } finally {
            DBUtil.close(stmt, "addToWhiteList()");
        }
    }


}

