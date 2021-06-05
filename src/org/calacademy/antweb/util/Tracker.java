package org.calacademy.antweb.util;

import java.util.*;


import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class Tracker implements Comparable<Tracker> {
  private static final Log s_log = LogFactory.getLog(Tracker.class);

  public int compareTo(Tracker other) {
    if (getMillis() > other.getMillis()) return 1;
    if (getMillis() == other.getMillis()) return 0;
    if (getMillis() < other.getMillis()) return -1;
    return 0;
  }

  String target;  
  public void setTarget(String target) {
    this.target = target;
  }
  public String getTarget() {
    return this.target;
  }

  Date startTime = null;
  public void setStartTime(Date startTime) {
    this.startTime = startTime;
  }
  public Date getStartTime() {
    return this.startTime;
  }
  
  int code = 0; // will be the request object's hashcode.
  public int getCode() {
    return this.code;
  }
  public void setCode(int code) {
    this.code = code;
  }    
  
  String requestString = null;
  public void setRequestString(String str) {
    this.requestString = str;
  }
  public String getRequestString() {
    return this.requestString;
  }
  
  public String getKey() {
    return getTarget() + " " + getCode(); //getRequestString();
  }

  public long getMillis() {
    long millis = AntwebUtil.millisSince(startTime);
    return millis;
  }

  public String getSinceStartTime() {
      String execTime = "";
      long millis = AntwebUtil.millisSince(startTime);
      if (millis > 2000) {
        execTime = AntwebUtil.secsSince(startTime) + " secs";
      } else {
        execTime = millis + " millis";      
      }
      return execTime;
  }
  
  public String toString() {
    return getTarget() + " runtTime:" + getSinceStartTime();
  }
  
}