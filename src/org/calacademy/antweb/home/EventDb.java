package org.calacademy.antweb.home;

import java.util.*;
import java.util.Date;
import java.sql.*;

import javax.servlet.http.*;
import org.apache.struts.action.*;
//import org.apache.regexp.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;

public class EventDb extends AntwebDb {

    private static Log s_log = LogFactory.getLog(EventDb.class);

    public EventDb(Connection connection) {
      super(connection);
    }

    public void addEvent(Event event) throws SQLException {
        String createdStr = DateUtil.getFormatDateTimeStr(event.getCreated());
            
        //s_log.warn("addEvent() createdStr:" + createdStr + " created:" + operationLock.getCreated());            

        String theInsert = "insert into event(operation, curator_id, name, created)  values (?, ?, ?, ?)";
                       
        A.log("addEvent() insert:" + theInsert);

        try (PreparedStatement stmt = getConnection().prepareStatement(theInsert)) {
            stmt.setString(1, event.getOperation());
            stmt.setInt(2, event.getCuratorId());
            stmt.setString(3, event.getName());
            stmt.setString(4, createdStr);

            stmt.executeUpdate();
        } catch (SQLException e) {
            s_log.error("addEvent() e:" + e);
            throw e;
        }
    }

    public ArrayList<Event> getEvents() throws SQLException {
      ArrayList<Event> events = new ArrayList<>();
      PreparedStatement stmt = null;
      ResultSet rset = null;           
      try {
        String query = "select id, operation, curator_id, name, created from event order by created desc";            
        stmt = DBUtil.getPreparedStatement(getConnection(), "EventDb.getEvents()", query);
        rset = stmt.executeQuery();
        Event event;
        while (rset.next()) {
           event = new Event();
           event.setId(rset.getInt("id"));
           event.setOperation(rset.getString("operation"));
           event.setCuratorId(rset.getInt("curator_id"));
           event.setName(rset.getString("name"));
           event.setCreated(rset.getTimestamp("created"));
           events.add(event);
        }
      } finally {
          DBUtil.close(stmt, rset, "EventDb.getEvents()");
      }
      
      return events;
    }

}

