package org.calacademy.antweb.home;

import java.util.*;
import java.text.DecimalFormat;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

public class QueryReport {

    private static Log s_log = LogFactory.getLog(QueryReport.class);

    private String name;
    private String query = null;
    private String subquery = null;
    private String desc;
    private String heading;
    ArrayList<String> list = new ArrayList<String>();
    private String error;

    public QueryReport() {
    }

    public String getName() {
        return this.name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getQuery() {
        return this.query;
    }
    public void setQuery(String query) {
        this.query = query;
    }

    public String getSubquery() {
        return this.subquery;
    }
    public void setSubquery(String subquery) {
        this.subquery = subquery;
    }

    public String getDesc() {
        return this.desc;
    }
    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getHeading() {
        return this.heading;
    }
    public void setHeading(String heading) {
        this.heading = heading;
    }

    public String getError() {
        return this.error;
    }
    public void setError(String error) {
        this.error = error;
    }

    public ArrayList<String> getList() {
        return list;
    }
    public void setList(ArrayList<String> list) {
          this.list = list;
    }

    public String toString() {
      return "queryReport name:" + getName() + " size:" + getList().size();
    }    
}
