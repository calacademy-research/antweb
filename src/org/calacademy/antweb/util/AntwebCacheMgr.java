package org.calacademy.antweb.util;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

import javax.servlet.http.*;

import java.sql.*;
import java.util.Date;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


/**
AntwebCacheMgr manages to cache requests for specified request types (browse.do, description.do, images.do, 
fieldGuide.do, specimenList.do, ... so that pages do not have to be calculated for each request.
It does so intelligently, in the following fashion.  When a page is requested by an unlogged in
user, if it is already cached, the cached page is simply returned.  When a non-cached page is
returned to a user, if it's calculation time was above the MAX_REQUEST_TIME, then the url
is recorded in the long_request table.  Periodically the genCache operation is invoked during
which the most expensive recent pages will be cached, and the oldest cached pages will be
uncached.  The genCache process can be optimized by modifying the static variables below.
*/
public class AntwebCacheMgr {

  private static final Log s_log = LogFactory.getLog(AntwebCacheMgr.class);

    public static boolean CACHING_OFF = true;

    public static int MILLIS = 1000;
    
    // Above this threshold, queries will be recorded in the long_request table.
    public static int MAX_REQUEST_TIME = MILLIS * 10;  // number of seconds.
    
    // One of these will be operative in the generateCachedContent() method below
    // If upon genCache we care to generate as many as possible within the given time period
   // private static int MAX_GENCACHE_TIME = 1000 * 60 * 10;  // 10 MIN
    // Or if we want to generate a certain number of cache items.
   // private static int MAX_REQ_PER_GENCACHE = 50;  
  
    private static final int DELETE_CACHE_INTERVAL = 1; // 7;    // days.

    // All records older than this will be deleted at genCache time.  Should be greater than DELETE_CACHE_INTERVAL
    private static final int PURGE_CACHE_INTERVAL = 2;    // days.

    // These are used in the long request display query  
    private static final int LONG_REQUEST_LIST_LIMIT = 1000;
    private static final int LONG_REQUEST_CREATED_INTERVAL = 7;
  
    private static int EXPIRED_DAYS_AGO = 3;
   
    // valid cacheTypes are fieldGuide, taxaPage, specimenList, images, browse, description 

/*
------------- Upon Slow Request ----------------

  If a request is deemed long, then it is entered into the long_request database to be
  used at generateCacheContent time to generate the cached content 
*/
    public static void finish(HttpServletRequest request, Connection connection, int busyConnections,
        java.util.Date startTime, String cacheType, Overview overview, String param1) 
        throws SQLException {
      
      // param1 is taxonName, or in the case of taxaPage, it is rank.

      HttpUtil.finish(request, startTime);
      
      long millis = AntwebUtil.millisSince(startTime);
      s_log.debug("finish() millis:" + millis);

      if (CACHING_OFF) return;
      
      if (AntwebProps.isDevMode()) {
        MAX_REQUEST_TIME = 1;  // for testing
      }
      if (millis > MAX_REQUEST_TIME) {
        //s_log.info("finish() 1 insert into longRequest table");
        Login accessLogin = LoginMgr.getAccessLogin(request);
        String dirFile = "data/" + cacheType + "/" + overview.getName() + "/" + param1 + ".txt";      
        insertLongRequest(request, connection, cacheType, dirFile, millis, accessLogin, busyConnections);
      }
    }

    /* Used for fieldGuides*/
    public static void finish(HttpServletRequest request, Connection connection, int busyConnections,
        java.util.Date startTime, String cacheType, String subfamily, String genus, String rank, Overview overview) 
        throws SQLException {

      HttpUtil.finish(request, startTime);

      if (CACHING_OFF) return;
            
      long millis = AntwebUtil.millisSince(startTime);
      if (AntwebProps.isDevMode()) {
        MAX_REQUEST_TIME = 1;  // for testing
      }
      if (millis > MAX_REQUEST_TIME) {
        String dirFile = "data/" + cacheType + "/" + overview.getName();     
        if (subfamily != null) dirFile += "/" + subfamily;
        if (genus != null) dirFile += "/" + genus;
        dirFile +=  "/fieldGuide.txt";
        // Or if a project
        if ((subfamily == null) && (genus == null)) {
          dirFile = "data/" + cacheType + "/" + overview.getName() + "/" + rank + ".txt";
        }
        s_log.debug("finish() fieldGuide insert into longRequest table dirFile:" + dirFile);
      }
    }
        
    private static void insertLongRequest(HttpServletRequest request, Connection connection,
        String cacheType, String dirFile, long millis, Login accessLogin, int busyConnections)
        throws SQLException {

        String url = HttpUtil.getRequestURL(request) + "?" + request.getQueryString();

        if (AntwebCacheMgr.uncacheableUrl(url)) {
          s_log.info("insertLongRequest() uncacheableUrl:" + url);
          return;
        }

        boolean isLoggedIn = (accessLogin != null);
        int loginId = 0;
        if (isLoggedIn) {
          loginId = accessLogin.getId();
        }
            
        String theInsert = "insert into long_request (cache_type, url, dir_file, millis, " 
            + " is_logged_in, curator_id, request_info, busy_connections, is_bot) " 
            + " values ('" + cacheType + "', '" + url + "', '" + dirFile + "', " + millis 
            + ", " + (isLoggedIn ? 1 : 0) + ", " + loginId  + ", '" + HttpUtil.getShortRequestInfo(request) 
            + "', " + busyConnections + ", " + HttpUtil.getIsBot(request) + ")";

        //s_log.warn("insertLongRequest() query:" + theInsert);
                             
        Statement stmt = null;
        try {
            stmt = connection.createStatement();
            stmt.executeUpdate(theInsert);
        } catch (Exception e) {
            s_log.warn("insertLongRequest e:" + e);    
        } finally {
            stmt.close();   
        }
    }

/* This query will exclude http://antweb.org, and long_requests created on other servers.  Not perfect,
   but will prevent getting hung on bad ones like http://12.189.20.11   */

    private static String getLongRequestsQuery() {
      return AntwebCacheMgr.getLongRequestsQuery(true, " and cached = '0000-00-00 00:00:00'");
    }

    // called from genCacheItem() below
    private static String getLongRequestsQuery(boolean shouldLimit, String unCachedOnly) {
      String limitClause = "";
      if (shouldLimit) limitClause = " limit " + LONG_REQUEST_LIST_LIMIT;
      String theQuery = "select cache_type, url, dir_file, max(millis) as millis, max(cache_millis) as cache_millis, cached, max(busy_connections) as busy_connections " 
         + " from long_request " 
         + " where "
         //+ " is_logged_in != 1 and "
         + " (url like \"" + AntwebProps.getDomainApp() + "%\") "
         + " and created >= DATE_SUB(SYSDATE(), INTERVAL " + LONG_REQUEST_CREATED_INTERVAL + " DAY) " 
         + unCachedOnly       
         + " group by cache_type, url, dir_file, cached"
         + " order by max(millis) desc, created desc " 
         + limitClause; 
         	
      s_log.debug("theQuery:" + theQuery);
      return theQuery;
    }

    public static ArrayList getLongRequests(Connection connection) 
      throws SQLException {
            ArrayList longRequests = new ArrayList();

            String query = AntwebCacheMgr.getLongRequestsQuery(true, "");
            return getRequests(connection, query);
	}

    // called by http://www.antweb.org/cache.do?action=display
    public static ArrayList getLongRequestDetails(Connection connection, String url, String orderBy) 
      throws SQLException {
        ArrayList longRequests = new ArrayList();
        String urlClause = "";
        if (!"all".equals(url)) urlClause = " and url = \"" + url + "\"";
        String orderByClause = "";
        if ((orderBy != null) && (!"".equals(orderBy))) orderByClause = " order by " + orderBy;
        String query = "select id, cache_type, url, dir_file, millis, cache_millis, created, cached, request_info, busy_connections, is_logged_in, is_bot from long_request where " 
          //+ " is_logged_in != 1 and "
          + " created >= DATE_SUB(SYSDATE(), INTERVAL " + LONG_REQUEST_CREATED_INTERVAL + " DAY) " 
          + urlClause
          + orderByClause;
          // + " limit " + LONG_REQUEST_LIST_LIMIT;
          
        if (AntwebProps.isDevMode()) s_log.info("getLongRequestDetails() query:" + query);   
        return getRequests(connection, query);
	}

    public static ArrayList getRequests(Connection connection, String query)
      throws SQLException {
        ArrayList longRequests = new ArrayList();

        Statement stmt = null;       
        ResultSet resultSet = null;
        try {   
            stmt = DBUtil.getStatement(connection, "AntwebCacheMgr.getRequests()");         
            resultSet = stmt.executeQuery(query);
            while (resultSet.next()) {
              String cacheType = resultSet.getString("cache_type");
              String url = resultSet.getString("url");
              String dirFile = resultSet.getString("dir_file");
              long maxMillis = resultSet.getLong("millis");              
              long cacheMillis = resultSet.getLong("cache_millis");
              int busyConnections = resultSet.getInt("busy_connections");
              int isLoggedIn = resultSet.getInt("is_logged_in");
              int isBot = resultSet.getInt("is_bot");

              LongRequest longRequest = null;
              if (query.contains("request_info")) {
                int id = resultSet.getInt("id");
                String requestInfo = resultSet.getString("request_info");
                String createDate = AntwebCacheMgr.getCacheDate(resultSet, "created");
                String cacheDate = AntwebCacheMgr.getCacheDate(resultSet, "cached");
                longRequest = new LongRequest(id, cacheType, url, dirFile, maxMillis, cacheMillis, createDate, cacheDate, busyConnections, requestInfo, isLoggedIn, isBot);
              } else {
                String cacheDate = AntwebCacheMgr.getCacheDate(resultSet, "cached");
                longRequest = new LongRequest(cacheType, url, dirFile, maxMillis, cacheMillis, cacheDate, busyConnections);
              }
              longRequests.add(longRequest);
            }
        } catch (SQLException e) {
            s_log.error("getRequests() e:" + e );
        } finally {
            DBUtil.close(stmt, resultSet, "AntwebCacheMgr.getRequests()");
        }
        return longRequests;
	}

    private static String getCacheDate(ResultSet resultSet, String field) {
        String cacheDate = ""; 
        try {
          Timestamp cachedDate = resultSet.getTimestamp(field);  // "cached"  or "created", depending.
          if (cachedDate != null) cacheDate = cachedDate.toString();
        } catch (SQLException e) {
          // if the field is empty, and exception will be thrown and ignored
          // It will be logged to the localhost.[date] file with info level.  Can not avoid.
          // s_log.error("e:" + e); 
        } 
        return cacheDate;
    }

    public static void forgetCaching(Connection connection) throws SQLException {
        // Then delete all very old records from the table, regardless of cached or logged in, over a certain age.
        String theUpdate = "update long_request set cached = default";
        Statement stmt = null;
        try {
          stmt = connection.createStatement();
          stmt.executeUpdate(theUpdate);
        } finally {
          stmt.close();            
        }
    }
    
/*
    //This deletes all cached copies on disk.            
    public static void deleteCaches(Connection connection) throws SQLException {
        // Then delete all very old records from the table, regardless of cached or logged in, over a certain age.
        String theDelete = "update long_request set cached = default";
        Statement stmt = connection.createStatement();
        stmt.executeUpdate(theDelete);
        stmt.close();            
        
        AntwebCacheMgr.deleteCaches();
    }
*/    
   
    public static void deleteLongRequests(Connection connection) throws SQLException {
        String theDelete = "delete from long_request";
        Statement stmt = null;
        try {
          stmt = connection.createStatement();
          stmt.executeUpdate(theDelete);
        } finally {
          stmt.close();            
        }
    }

    static void deleteCaches() {
        Utility util = new Utility();
        File file = new File(AntwebProps.getDocRoot() + "web/data/");
        util.deleteDirectory(file);       
    }    
    
    // This gets rid of old long requests from the database table.  Also deletes cache files older than threshold.
    // Triggered by the genCache call.
    public static void deleteOldLongRequests(Connection connection)
        throws SQLException {
       
        // First delete all of the cache files for the long_requests that have been cached and have expired
        // and then delete all old records from the database.
           
        // select the distinct set of cached pages over (interval) age.
        // select dir_file from long_request where  is_logged_in != 1 and  created <= DATE_SUB(SYSDATE(), INTERVAL 0 DAY) and cached != '0000-00-00 00:00:00' group by dir_file


        String from = " from long_request where " 
              + " is_logged_in != 1 and "
              + " created <= DATE_SUB(SYSDATE(), INTERVAL " + DELETE_CACHE_INTERVAL + " DAY)"
              + " and cached != '0000-00-00 00:00:00'"; 
        String theQuery = "select dir_file " 
              + from
              + " group by dir_file";
        Statement stmt = null;
        ResultSet rset = null;

		try {
          if (false) {  // Now we will not delete the old files.  They are just for bots now.
			  stmt = DBUtil.getStatement(connection, "deleteOldLongRequests()");
			  rset = stmt.executeQuery(theQuery);
			  while (rset.next()) {
				String dirFile = rset.getString("dir_file");    
				Utility util = new Utility();
				File file = new File(AntwebProps.getDocRoot() + "web/" + dirFile);
				util.deleteFile(file);                       
				//s_log.warn("deleteOldLongRequests() dirFile:" + dirFile);
			  }
          }
		} finally {
		  DBUtil.close(stmt, rset, "AntwebCacheMgr", "deleteOldLongRequests()");
		}  

/*       

        // Then delete all of those records from the long_requests table.
        String theDelete = "delete " + from;
        s_log.warn("deleteOldLongRequests() not done:" + theDelete);
        try {
          stmt = connection.createStatement();
          stmt.executeUpdate(theDelete);
        } finally {
          stmt.close();   
        }
        // Then delete all very old records from the table, regardless of cached or logged in, over a certain age.
        theDelete = "delete from long_request where created <= DATE_SUB(SYSDATE(), INTERVAL " + PURGE_CACHE_INTERVAL + " DAY)";
        try {
          stmt = connection.createStatement();
          stmt.executeUpdate(theDelete);
        } finally {
          stmt.close();   
        }
*/        
    }    


  private static boolean isExpired(String cacheDate) {
    try {
      java.util.Date expireDate = AntwebUtil.getDateNDaysAgo(new java.util.Date(), EXPIRED_DAYS_AGO);
      java.util.Date cachedDate = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse(cacheDate);
      return (cachedDate.compareTo(expireDate) < 0);
    } catch (ParseException e) {
      s_log.error("isExpired() e:" + e);
      return true;  // arbitrary.  Should not happen.
    }
  }

  public static boolean genCacheItem(Connection connection) 
  throws SQLException {
  /* 
    This is an implementation of one of the caching strategies.  An individual item will be
    cached.  Either a new item, or an item to recache because it has expired.
    */
    
      s_log.debug("getCacheItem()");

      boolean itemCached = false;
      Date startTime = new Date();

      //AntwebCacheMgr.deleteOldLongRequests(connection);

      //cacheItem("http://localhost/antweb/getSpecimenList.do?projectName=worldants&taxonName=myrmicinaeacanthognathus"); 
	
      String theQuery = AntwebCacheMgr.getLongRequestsQuery();      
      Statement stmt = null;
      ResultSet rset = null;
      int reqCount = 0;
      try {
        stmt = DBUtil.getStatement(connection, "genCacheItem()");

        if (stmt == null) {
          //s_log.warn("genCacheItem() stmt is null. Connection closed?: " + connection.isClosed());
          //AntwebUtil.logShortStackTrace();
          return false;
        }

        // Get the request that have collectively cost the most time on the server.
        rset = stmt.executeQuery(theQuery);
        while (rset.next()) {   
          ++reqCount;        
          String cacheDate = getCacheDate(rset, "cached");

          if (("".equals(cacheDate)) || (isExpired(cacheDate))) {            
            // if not cached, or cache is expired, then cache
            String dirFile = rset.getString("dir_file");
            String url = rset.getString("url");

            if (AntwebCacheMgr.uncacheableUrl(url)) continue;

            boolean success = cacheItem(rset.getString("cache_type"), url, dirFile);
            long sinceStart = AntwebUtil.millisSince(startTime);
            //s_log.info("genCacheItem() success:" + success + " sinceStart:" + sinceStart + " dirFile:" + dirFile);
            if (success) { 
              updateCachedItems(connection, url, sinceStart);
              itemCached = true;
            } else {
              s_log.warn("getCacheItem() No success:" + url);
            }
            break;
          }
          // otherwise check for the next item in the results set for an item we shall cache.
        }
      } finally {
        DBUtil.close(stmt, rset, "AntwebCacheMgr", "genCacheItem()");
      }
      long millis = AntwebUtil.millisSince(startTime);
      s_log.info("genCacheItem() sinceStartMillis:" + millis + " reqCount:" + reqCount + " itemCached:" + itemCached);

      /*
      If we make it through the whole list, it would be nice to wait a while before trying again.  Every 2 seconds seems extreme.  5 minutes?
      */
      return itemCached;
  }

  private static boolean uncacheableUrl(String url) {
    if (
        (url.contains("statusSet="))
       ) {
         s_log.info("uncacheableUrl() url:" + url);
         return true;
       } else {
         return false;
       }  
  }

  public static void reCacheItem(Connection connection, HttpServletRequest request) throws SQLException {     
      String url = HttpUtil.getRequestURL(request) + "?" + request.getQueryString();

      String theUpdate = "update long_request set cached = default where url = '" + url + "'";                                   
      Statement stmt = null;
      try {
        stmt = DBUtil.getStatement(connection, "reCacheItem");
        int result = stmt.executeUpdate(theUpdate);
        s_log.debug("reCacheItem() result:" + result + " query:" + theUpdate);
      } finally {
        DBUtil.close(stmt, null, "AntwebCacheMgr", "reCacheItem");
      }
  }

  private static int s_isCachingCount = 0;
  private static String s_lastCacheItem = "";
 
  private static boolean cacheItem(String cacheType, String theUrl, String dirFile) {
    //String theUrl ="http://localhost:8080/PGAC/Jsps/JSP/PGACMultiItemCriteriaPage.jsp";


    s_log.debug("cacheItem() caching:" + theUrl);
        
    boolean didCache = false;

    if (s_isCachingCount > 0) {
      //s_log.error("!!! cacheItem isCachingCount:" + s_isCachingCount + " lastCacheItem:" + s_lastCacheItem);
      return false;
    }
    
    String dataFile = null;
    String tempDataFile = null;
    try {

      String docRoot = AntwebProps.getDocRoot();
      String dataRoot = docRoot + "web/";
      dataFile = dataRoot + dirFile;

s_log.debug("cacheItem() dataRoot:" + dataRoot);
s_log.debug("cacheItem() dataFile:" + dataFile);

      ++s_isCachingCount;
      s_lastCacheItem = dataFile;

      tempDataFile = dataFile + "T";
      (new Utility()).makeDirTree(dataFile); 
      s_log.debug("writeDataFile() 1 dataRoot:" + dataRoot + " file:" + dataFile);

      BufferedWriter out = new BufferedWriter(new FileWriter(tempDataFile, false));

      theUrl += "&genCache=true";
      
      LogMgr.appendLog("getUrl.txt", DateUtil.getFormatDateTimeStr(new Date()) + " Caching:" + theUrl);
      
      URL url = new URL(theUrl) ;
      InputStream is = url.openConnection().getInputStream();
      int c = 0;
      while ((c = is.read()) != -1) {
        out.write((char) c);
      }
      out.close();
      Utility utility = new Utility();
      if (!AntwebCacheMgr.badFileDontCache(tempDataFile)) {
        utility.copyFile(tempDataFile, dataFile);
        didCache = true;
        s_log.debug("cacheItem() caching:" + dataFile);
      } else {
        //s_log.warn("cacheItem() url not cached due to error file:" + theUrl);
        didCache = false;
      }
      utility.deleteFile(tempDataFile);
    } catch (IOException e) {
      s_log.warn("cacheItem():" + e + " theUrl:" + theUrl);
    } catch (Exception e) {
      s_log.warn("cacheItem():" + e + " dataFile:" + dataFile);
    } finally {
      --s_isCachingCount; 
    }
    return didCache;
  }

  private static boolean badFileDontCache(String dataFile) {
    Utility utility = new Utility();
    String message = null;
    if (utility.fileContains(dataFile, "Please try again")) {
      message = " Please try again";
    }
    if (utility.fileContains(dataFile, "ServletException")) {
      message = " ServletException";
      s_log.info("badFileDontCache dataFile:" + dataFile);
    }
      return message != null;
  }

  private static void updateCachedItems(Connection connection, String url, long sinceStart)
    throws SQLException {
        String theUpdate = "update long_request set cached = now(), cache_millis = " + sinceStart 
          + " where url = '" + url + "'";

        s_log.debug("updateCachedItems() query:" + theUpdate);

        Statement stmt = null;
        try {
          stmt = connection.createStatement();
          stmt.executeUpdate(theUpdate);
        } finally {
          stmt.close();     
        }
  }

/*
------------- At Fetch Time ----------------
*/
  public static boolean isFetchFromCache(Login accessLogin, boolean isGetCache) {
    boolean fetchFromCache = false;

    if (CACHING_OFF) return false;
      
    // Not used:  AntwebUtil.getIsBot(request)
    
    if (isGetCache) {
      fetchFromCache = true;
    } else {
      boolean isLoggedIn = (accessLogin != null);  
      if (!isLoggedIn) fetchFromCache = true;
    }
    return fetchFromCache;
  }
  
  private static String getDir(String cacheType, String param1, String param2) {
      String dir = null;
  
      if ("taxaPage".equals(cacheType)) {
        String overviewName = param1;
        String rank = param2;

        dir = "/web/data/taxaPage/" + overviewName + "/";

      } else {
        String taxonName = param1;
        String overviewName = param2;  
      
        if (cacheType.equals("specimenData")) {
            dir = "/web/data/specimenData/";
        }
        if (cacheType.equals("description")) {
            dir = "/web/data/description/" + overviewName + "/";
        }
        if (cacheType.equals("images")) {
            dir = "/web/data/images/" + overviewName + "/";
        }
        if (cacheType.equals("browse")) {
            dir = "/web/data/browse/" + overviewName + "/";
        }
     }
     return dir;  
  }
  
  private static String getFileName(String cacheType, String param1, String param2) {
      String fileName = null;
  
      if ("taxaPage".equals(cacheType)) {
        String overviewName = param1;
        String rank = param2;

        fileName = rank + ".txt";      

      } else {
        String taxonName = param1;
        String overviewName = param2;  
      
        if (cacheType.equals("specimenData")) {
            fileName = overviewName + "-" + taxonName + ".txt";
        }
        if (cacheType.equals("description")) {
            fileName = taxonName + ".txt";
        }
        if (cacheType.equals("images")) {
            fileName = taxonName + ".txt";
        }
        if (cacheType.equals("browse")) {
            fileName = taxonName + ".txt";
        }
      }
      return fileName;  
  }

  public static boolean hasInCache(String cacheType, String param1, String param2) {
      String dir = getDir(cacheType, param1, param2);
      String fileName = getFileName(cacheType, param1, param2);
  
      int sec = 1000;  // milliseconds;
      int min = 60;    // seconds
      int hour = 60;   // minutes
      int day = 24;    // hours
      int week = 7;    // dauys
      long expirePeriod = sec * min * hour * day * week;

      boolean isExpired = AntwebUtil.isExpired(dir, fileName, expirePeriod);
      if (isExpired) {
        s_log.info("fetchFromCache() isExpired:" + isExpired + " fileName:" + fileName);
        return false;
      }
                
      boolean hasInCache = (new File(AntwebProps.getDocRoot() + dir, fileName)).exists();
      //s_log.warn("hasInCache() hasInCache:" + hasInCache + " cacheType:" + cacheType + " dir:" + dir + " fileName:" + fileName);
      return hasInCache;
  }

  //public static String fetchFromCache(String cacheType, Overview overview, String param2) {
  //  return fetchFromCache(cacheType, overview.getName(), param2);
  //}
  
  public static String fetchFromCache(String cacheType, String param1, String param2) {
      String dir = getDir(cacheType, param1, param2);
      String fileName = getFileName(cacheType, param1, param2);

      int sec = 1000;  // milliseconds;
      int min = 60;    // seconds
      int hour = 60;   // minutes
      int day = 24;    // hours
      int week = 7;    // dauys
      long expirePeriod = sec * min * hour * day * week;

      boolean isExpired = AntwebUtil.isExpired(dir, fileName, expirePeriod);
      if (isExpired) {
        s_log.info("fetchFromCache() isExpired:" + isExpired + " fileName:" + fileName);
        return null;
      }
                
      String fetchedContent = AntwebUtil.readFile(dir, fileName);
      String contentSize = null;
      if (fetchedContent != null) contentSize = "" + fetchedContent.length();
      //s_log.warn("fetchFromCache() cacheType:" + cacheType + " dir:" + dir + " fileName:" + fileName + " fetchedContent.size:" + contentSize);
      return fetchedContent;
  }

  //fetchFromCache() cacheType:fieldGuide projectName:allantwebants dir:/web/data/fieldGuide/allantwebants/agroecomyrmecinae/null/ fileName:fieldGuide.txt fetchedContent.size:null
  //http://www.antweb.org/browse.do?subfamily=formicinae&genus=plagiolepis&name=alluaudi&rank=species&project=poeants 
   
  public static String fetchFromCache(String cacheType, String subfamily, String genus, String rank, Overview overview) {
  
      String dir = null;
      String fileName = null;
      
      if (cacheType.equals("fieldGuide")) {
        if ((subfamily == null) && (genus == null)) {
          dir = "/web/data/fieldGuide/" + overview.getName() + "/";
          fileName = rank + ".txt";
        } else {
          dir = "/web/data/fieldGuide/" + overview.getName() + "/" + subfamily + "/";
          if (genus != null) dir += genus + "/" ;
          fileName = "fieldGuide.txt";        
        }
        //String fullPath = AntwebProps.getDocRoot() + dir + fileName;
      }
      
      String fetchedContent = AntwebUtil.readFile(dir, fileName);
      String contentSize = null;
      if (fetchedContent != null) contentSize = "" + fetchedContent.length();
      if (AntwebProps.isDevMode()) {
        s_log.warn("fetchFromCache() cacheType:" + cacheType + " overview:" + overview.getName()
          + " dir:" + dir + " fileName:" + fileName + " fetchedContent.size:" + contentSize);
      }
      return fetchedContent;
  }
}
