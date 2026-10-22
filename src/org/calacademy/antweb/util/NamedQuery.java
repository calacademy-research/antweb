package org.calacademy.antweb.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class NamedQuery  //implements Iterable 
{

  private static final Log s_log = LogFactory.getLog(NamedQuery.class);
    
  private String name;
  private String param;
  private String desc;
  private String query;
  private String headerHtml;
  private String[] headerArray;
  private String detailQuery;
  private String result;
  private int rowCount = 0;
  private String timePassedNote;
  private String fileName;

  NamedQuery(String name, String param, String fileName, String desc, String[] headerArray, String query) {
    this.name = name;
    this.param = param;
    this.fileName = fileName;
    this.desc = desc;
    this.headerArray = headerArray;
    this.query = query;
  }

  /**
   * A named query of Queries.java: its name, a plain-language explanation (the first sentence is its summary),
   * one heading per column of the select, and the (read-only) SQL.
   */
  NamedQuery(String name, String desc, String[] headerArray, String query) {
      this.name = name;
      this.desc = desc;
      this.headerArray = headerArray;
      this.query = query;
  }

  NamedQuery(String name, String desc, String[] headerArray, String query, String detailQuery) {
      this(name, desc, headerArray, query);
      this.detailQuery = detailQuery;
  }

  /** The first sentence of the explanation: the line shown in lists of queries. */
  public String getSummary() {
    return summary(desc);
  }

  public static String summary(String desc) {
    if (desc == null) return "";
    String d = desc.trim();
    int depth = 0;
    for (int i = 0; i < d.length(); i++) {
      char c = d.charAt(i);
      if (c == '(') depth++;
      else if (c == ')' && depth > 0) depth--;
      else if (c == '.' && depth == 0 && (i == d.length() - 1 || d.charAt(i + 1) == ' ')) return d.substring(0, i + 1);
    }
    return d;
  }

  /** The number of headings (0 without any). */
  public int getHeadingCount() {
    return headerArray == null ? 0 : headerArray.length;
  }

  /**
   * The number of columns of a select: the commas of the first select list (up to its "from") that are not
   * inside parentheses or quotes, plus one. -1 when the text does not start with select.
   */
  public static int selectColumnCount(String sql) {
    if (sql == null) return -1;
    String s = sql.trim();
    if (!s.toLowerCase().startsWith("select")) return -1;
    int depth = 0, count = 1;
    char quote = 0;
    for (int i = "select".length(); i < s.length(); i++) {
      char c = s.charAt(i);
      if (quote != 0) {
        if (c == '\\' && quote != '`') i++;
        else if (c == quote) {
          if (i + 1 < s.length() && s.charAt(i + 1) == quote) i++;
          else quote = 0;
        }
        continue;
      }
      if (c == '\'' || c == '"' || c == '`') quote = c;
      else if (c == '(') depth++;
      else if (c == ')') depth--;
      else if (c == ',' && depth == 0) count++;
      else if (depth == 0 && Character.isWhitespace(c) && s.regionMatches(true, i + 1, "from", 0, 4)
          && (i + 5 >= s.length() || Character.isWhitespace(s.charAt(i + 5)) || s.charAt(i + 5) == '(')) break;
    }
    return count;
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
    if (headerArray != null) headerHtml = headerHtml(headerArray);
    return headerHtml;
  }

  /** Table headings for column names, as text (escaped). */
  public static String headerHtml(String[] columns) {
    StringBuilder html = new StringBuilder();
    for (String col : columns) html.append("<th>").append(Html.esc(col)).append("</th>");
    return html.toString();
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

