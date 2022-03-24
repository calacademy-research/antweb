package org.calacademy.antweb.home;

import java.util.*;
import java.text.DecimalFormat;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class OperationLock {

    private static Log s_log = LogFactory.getLog(OperationLock.class);

    public static int IMAGE_UPLOAD_LOCK = 1;

      private static long minute = 60 * 1000;
      public static long FIFTEENMIN = 15 * minute;
      public static long ONEHOUR = FIFTEENMIN * 4;

      private int operation = IMAGE_UPLOAD_LOCK;  // Default (and only so far).
      private boolean locked = false;
      private Date now;
      private Date created;
      private long durationMillis = ONEHOUR;
      private int curatorId;
      private String curator;  // This will be group - curator
      
      //private double maxImageLockTime = minute * .5;  // 30 seconds - for testing.
      
      public OperationLock() {
      }
      
      public OperationLock(int operation, Date created, int curatorId, String curator) {
        setOperation(operation);
        setLocked(true);
        setCreated(created);
        setCuratorId(curatorId);
        setCurator(curator);
      }
        
      private String convertMillisToMinStr(double millis) {
        double minDbl = millis / minute;

        DecimalFormat decForm = new DecimalFormat("0.0");
        String minStr = decForm.format(minDbl) + " minutes";
        
        //s_log.warn("convertMillisToMinStr() minStr:" + minStr + " millis:" + millis);
        return minStr;
      }
      
      private String getElapsedTimeHoursMinutesSecondsString(double elapsedTimeDbl) {       
        long elapsedTime = Double.valueOf(elapsedTimeDbl).longValue();
        String format = String.format("%%0%dd", 2);  
        elapsedTime = elapsedTime / 1000;  
        String seconds = String.format(format, elapsedTime % 60);  
        String minutes = String.format(format, elapsedTime % 3600 / 60);
        String hours = String.format(format, elapsedTime / 3600);  
        String time =  hours + ":" + minutes + ":" + seconds;  
        return time;  
      }        
        
      public double timeToExpire() {
        if (durationMillis == 0) return 0;
        
        double nowMillis = new Date().getTime();
        //double nowMillis = getNow().getTime();
        long nowMillisLong = Double.valueOf(nowMillis).longValue();
        // s_log.warn("timeToExpire: now:" + AntwebUtil.getFormatDateTimeStr(new Date(nowMillisLong)) + " nowMillis:" + nowMillis);        
        
        double createdMillis = getCreated().getTime();
        long createdMillisLong = Double.valueOf(createdMillis).longValue();
        // s_log.warn("timeToExpire: created:" + AntwebUtil.getFormatDateTimeStr(new Date(createdMillisLong)) + " createdMillis:" + createdMillis);        

        double sinceLockTime = nowMillis - createdMillis;
        //s_log.warn("timeToExpire:" + getElapsedTimeHoursMinutesSecondsString(sinceLockTime));
        
        double timeToExpire = durationMillis - sinceLockTime;
        
        //s_log.warn("timeToExpire() sinceLockTime:" + convertMillisToMinStr(sinceLockTime) + " sinceLockTimeMillis:" + sinceLockTime + " timeToExpire:" + convertMillisToMinStr(timeToExpire));
        return timeToExpire;
      }
      
      public String timeToExpireStr() {
        return convertMillisToMinStr(timeToExpire());
      }
        
      public int getOperation() {
        return operation;
      }
      public void setOperation(int operation) {
        this.operation = operation;
      }
        
      public boolean isExpired() {
        if (durationMillis == 0) return false;
        if (getCreated() == null) return false;  // to avoid an NPE
        double timeToExpire = timeToExpire();
        return timeToExpire < 0;
      }
            
      public long getDurationMillis() {
        return this.durationMillis;
      }
      public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
      }
                  
      public boolean isLocked() {
        return this.locked;
      }      
      public void setLocked(boolean locked) {
        this.locked = locked;
      }

      public Date getNow() {
        return this.now;
      }
      public void setNow(Date now) {
        this.now = now;
      }
      
      public Date getCreated() {
        return this.created;
      }
      public void setCreated(Date created) {
        this.created = created;
      }
      
      public int getCuratorId() {
        return curatorId;
      }
      public void setCuratorId(int curatorId) {
        this.curatorId = curatorId;
      }
      
      public String getCurator() {
        return this.curator;
      }
      public void setCurator(String curator) {
        this.curator = curator;
      }
}
