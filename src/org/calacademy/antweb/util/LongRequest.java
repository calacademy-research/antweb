package org.calacademy.antweb.util;


import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public class LongRequest {

    private static final Log s_log = LogFactory.getLog(LongRequest.class);
    
    private int id;
    private String cacheType;
    private String url;
    private String dirFile;
    private long maxMillis;
    private long cacheMillis;
    private String createDate;
    private String cacheDate;
    private String requestInfo;
    private int busyConnections;
    private int isLoggedIn;
    private int isBot;

    public LongRequest() {
        super();
    }

    public LongRequest(String cacheType, String url, String dirFile, long maxMillis,
        long cacheMillis, String cacheDate, int busyConnections) {
      setCacheType(cacheType);
      setUrl(url);
      setDirFile(dirFile);
      setMaxMillis(maxMillis);
      setCacheMillis(cacheMillis);
      setCacheDate(cacheDate);
      setBusyConnections(busyConnections);
    }
        
    public LongRequest(int id, String cacheType, String url, String dirFile, long maxMillis,
        long cacheMillis, String createDate, String cacheDate, int busyConnections, String requestInfo,
        int isLoggedIn, int isBot) {
      setId(id);
      setCacheType(cacheType);
      setUrl(url);
      setDirFile(dirFile);
      setMaxMillis(maxMillis);
      setCacheMillis(cacheMillis);
      setCreateDate(createDate);
      setCacheDate(cacheDate);
      setRequestInfo(requestInfo);
      setBusyConnections(busyConnections);
      setIsLoggedIn(isLoggedIn);
      setIsBot(isBot);
    }


    public int getId() {
      return id;
    }
    public void setId(int id) {
      this.id = id;
    }
    
    public String getCacheType() {
        return cacheType;
    }
    public void setCacheType(String cacheType) {
        this.cacheType = cacheType;
    }

    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }

    public String getDirFile() {
        return dirFile;
    }
    public void setDirFile(String dirFile) {
        this.dirFile = dirFile;
    }
        
    public long getMaxMillis () {
        return maxMillis;
    }
    public void setMaxMillis(long maxMillis) {
        this.maxMillis = maxMillis;
    }    
        
    public long getCacheMillis () {
        return cacheMillis;
    }
    public void setCacheMillis(long cacheMillis) {
        this.cacheMillis = cacheMillis;
    }    

    public String getCacheDate() {
        return cacheDate;
    }
    public void setCacheDate(String cacheDate) {
        this.cacheDate = cacheDate;
    }

    public String getCreateDate() {
        return createDate;
    }
    public void setCreateDate(String createDate) {
        this.createDate = createDate;
    }

    public String getRequestInfo() {
        return requestInfo;
    }
    public void setRequestInfo(String requestInfo) {
        this.requestInfo = requestInfo;
    }

    public int getBusyConnections() {
      return busyConnections;
    }
    public void setBusyConnections(int busyConnections) {
      this.busyConnections = busyConnections;
    }

    public int getIsLoggedIn() {
      return isLoggedIn;
    }
    public void setIsLoggedIn(int isLoggedIn) {
      this.isLoggedIn = isLoggedIn;
    }
    
    public int getIsBot() {
      return isBot;
    }
    public void setIsBot(int isBot) {
      this.isBot = isBot;
    }
}
