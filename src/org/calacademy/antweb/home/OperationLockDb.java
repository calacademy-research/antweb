package org.calacademy.antweb.home;

import java.util.Date;
import java.sql.*;

//import org.apache.regexp.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

public class OperationLockDb extends AntwebDb {

/* 
Useful query to see how lock the locks have lasted:

    select id, operation, duration_millis, locked, curator_id, curator, created, modified, unlock_op, timestampdiff(second, created, modified) as seconds, timestampdiff(minute, created, modified) as minutes from operation_lock;
*/


    public OperationLockDb(Connection connection) {
      super(connection);
    }

    private static Log s_log = LogFactory.getLog(OperationLockDb.class);

    public void setOperationLock(OperationLock operationLock) throws SQLException {
        int locked = operationLock.isLocked() ? 1 : 0;
        String createdStr = DateUtil.getFormatDateTimeStr(operationLock.getCreated());
        
        //s_log.warn("setOperationLock() createdStr:" + createdStr + " created:" + operationLock.getCreated());            

        String theInsert = "insert into operation_lock (operation, duration_millis, locked, curator_id, curator, created) " 
          + " values (" + operationLock.getOperation() + ", " + operationLock.getDurationMillis() + ", " 
          + locked + ", " + operationLock.getCuratorId() + ", '" + operationLock.getCurator() + "', " + "'" + createdStr + "')";
                   
        //s_log.info("setOperationLock() insert:" + theInsert);
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "setOperationLock()");  
            stmt.executeUpdate(theInsert);
        } catch (SQLException e) {
            s_log.error("setOperationLock() theInsert:" + theInsert);
            throw e;
        } finally {
            DBUtil.close(stmt, "setOperationLock()");
        }
    }

    public OperationLock getOperationLock() throws SQLException {
      OperationLock operationLock = new OperationLock();
    
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getOperationLock()");      

        String theQuery = "select operation, duration_millis, locked, now() as now, curator_id, curator, created from operation_lock order by created desc limit 1";
        rset = stmt.executeQuery(theQuery);
        while (rset.next()) {
           operationLock.setOperation(rset.getInt("operation"));
           operationLock.setDurationMillis(rset.getLong("duration_millis"));
           boolean locked = rset.getBoolean("locked");
           operationLock.setLocked(locked);
           // s_log.warn(" getOperationLock() locked:" + locked + " setLocked:" + OperationLock.isLocked());   
           operationLock.setNow(rset.getTimestamp("now"));
           operationLock.setCreated(rset.getTimestamp("created"));
           //s_log.warn("getOperationLock() created:" + AntwebUtil.getFormatDateTimeStr(OperationLock.getCreated()));       
           operationLock.setCuratorId(rset.getInt("curator_id"));
           operationLock.setCurator(rset.getString("curator"));
        }
      } finally {
        DBUtil.close(stmt, rset, "getOperationLock()");
      }
      
      return operationLock;
    }

    public void unlock(int operation, int curatorId) throws SQLException  {
      //String modifiedStr = AntwebUtil.getFormatDateTimeStr(new Date());    
      Statement stmt = getConnection().createStatement();
      String theUpdate = "update operation_lock set locked=0, modified=now(), unlock_op='unlock' where locked=1"
        + " and operation = '" + operation + "'" 
        //+ "  and curator_id = " + curatorId
        ;
      stmt.executeUpdate(theUpdate);    
      stmt.close();
    }
    
    public void enable(int operation) throws SQLException  {
      String modifiedStr = DateUtil.getFormatDateTimeStr(new Date());
      Statement stmt = getConnection().createStatement();
      String theUpdate = "update operation_lock set locked=0, modified=now(), unlock_op='enable'   where locked=1 and operation = '" + operation + "'";
      stmt.executeUpdate(theUpdate);    
      stmt.close();
    }

    public void disable(int operation, int curatorId) throws SQLException  {
      String modifiedStr = DateUtil.getFormatDateTimeStr(new Date());
      Statement stmt = getConnection().createStatement();
      String theUpdate = "update operation_lock set duration_millis=0, modified=now() where locked=1 and operation = '" + operation + "' and curator_id = " + curatorId;
      stmt.executeUpdate(theUpdate);        
      stmt.close();
    }
}
