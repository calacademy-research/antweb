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
    
  private String name = null;
  private String param = null;
  private String desc = null;
  private String query = null;
  private String headerHtml = null;
  private String[] headerArray = null;
  private String detailQuery = null;
  private String result = null;
  private int rowCount = 0;
  private String timePassedNote = null;
  private String fileName = null;

  NamedQuery(String name, String param, String fileName, String desc, String[] headerArray, String query) {
    this.name = name;
    this.param = param;
    this.fileName = fileName;
    this.desc = desc;
    this.headerArray = headerArray;
    this.query = query;
  }

  NamedQuery(String name, String desc, String headerHtml, String query) {
      this.name = name;
      this.desc = desc;
      this.headerHtml = headerHtml;
      this.query = query;
  }

  NamedQuery(String name, String desc, String headerHtml, String query, String detailQuery) {
      this(name, desc, headerHtml, query);
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

  public String[] getHeaderArray() {
    return headerArray;
  }
  public void setHeaderArray(String[] headerArray)
  {
    this.headerArray = headerArray;
  }
  public String getHeader() {
    String header = "";
    if (headerArray != null) {
      for (String col : headerArray) {
        header += col + "\t";
      }
    }
    return header;
  }

  public String getHeaderHtml() {
    if (headerArray != null) {
      headerHtml = "";
      for (String col : headerArray) {
        headerHtml += "<th>" + col + "</th>";
      }
    }
    return headerHtml;
  }
  public void setHeaderHtml(String headerHtml)
  { 
    this.headerHtml = headerHtml;
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

  public String getResult() {
    return result;
  }
  public void setResult(String result)
  {
    this.result = result;
  }

  public int getRowCount() { return rowCount; }
  public void setRowCount(int rowCount) { this.rowCount = rowCount; }

  public String getTimePassedNote() {
    return timePassedNote;
  }
  public void setTimePassedNote(String timePassedNote)
  {
    this.timePassedNote = timePassedNote;
  }

  public String getParam() {
    return param;
  }
  public void setParam(String param)
  {
    this.param = param;
  }

  public String getFileName() {
    return fileName;
  }
  public void setFileName(String fileName)
  {
    this.fileName = fileName;
  }

  public String toString() {
    return getName();
  }
}

