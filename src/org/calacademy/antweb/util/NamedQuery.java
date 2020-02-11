package org.calacademy.antweb.util;

import java.io.*;
import java.net.*;
import java.util.*;

import javax.servlet.http.*;
import javax.servlet.*;

import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;

import org.calacademy.antweb.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class NamedQuery  //implements Iterable 
{

  private static final Log s_log = LogFactory.getLog(NamedQuery.class);
    
  private String name = "";  
  private String desc = "";
  private String query = "";
  private String header = "";
  private String detailQuery = null;

  NamedQuery(String name, String desc, String header, String query) {
      this.name = name;
      this.desc = desc;
      this.header = header;
      this.query = query;
  }

  NamedQuery(String name, String desc, String header, String query, String detailQuery) {
      this(name, desc, header, query);
      this.detailQuery = detailQuery;
  }

  public String getName() { 
    return name; 
  }
  public void setName(String name) { 
    this.name = name; 
  }
    public String getDesc() { 
    return desc; 
  }
  public void setDesc(String desc) { 
    this.desc = desc; 
  }

  public String getHeader() { 
    return header; 
  }
  public void setHeader(String header) 
  { 
    this.header = header; 
  }

  public String getQuery() { 
    return query; 
  }
  public void setQuery(String query) 
  { 
    this.query = query; 
  }    

  public String getDetailQuery() { 
    return detailQuery; 
  }
  public void setDetailQuery(String detailQuery) 
  { 
    this.detailQuery = detailQuery; 
  }    
  
  public String toString() {
    return getName();
  }
}

