package org.calacademy.antweb.util;

import java.util.*;
import java.time.*;

import java.text.*;

import org.calacademy.antweb.util.AntwebUtil;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


public abstract class DateUtil {

  private static final Log s_log = LogFactory.getLog(DateUtil.class);
  
    public static int getYear() {
      return Year.now().getValue();
    }
    
    public static void runTests() {
        DateUtil.d("1 May 2011");
        DateUtil.d("2011-05-01");
        DateUtil.d("2011-05-00");    
		DateUtil.d("2011-00-00");    
		DateUtil.d("2011-05");    
		DateUtil.d("2011");
		DateUtil.d("11 Dec 1995");
		DateUtil.d("May 1 2011");
		DateUtil.d("May 1, 2011");
		DateUtil.d("29.v.1986");
		DateUtil.d("iv.1976");
		DateUtil.d("11-14.ii.2007");
		DateUtil.d("10.VIII.1987");
		DateUtil.d("12 2012");
		DateUtil.d("10 12 2012"); // How do we know? Flag this!
		DateUtil.d("21-Jul-99");
    }

    public static void d(String d) {
		A.p("date:" + d + " formatted:" + DateUtil.constructDateStr(d));
    }

    public static Date getFormatDate(String dateStr) {
      Date theDay = null;
      try {
        // handle date in the format like log4j: 2008-08-28 09:46:55
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        theDay = (Date)formatter.parse(dateStr);
        //A.log("DateUtil.getFormatDate() theDayStr:" + dateStr + " theDay:" + theDay);
      } catch (ParseException e) {
        return null;
      }
      return theDay;
    }	

    public static Date getFormatDateShort(String dateStr) {
      Date theDay = null;
      try {
        // handle date in the format like log4j: 2008-08-28 09:46:55
        DateFormat formatter = new SimpleDateFormat("yyyy-MM");
        theDay = (Date)formatter.parse(dateStr);
        //A.log("DateUtil.getFormatDateShort() theDayStr:" + dateStr + " theDay:" + theDay);
      } catch (ParseException e) {
        return null;
      }
      return theDay;
    }	
    
    public static Date getFormatDateShortShort(String dateStr) {
      Date theDay = null;
      try {
        // handle date in the format like log4j: 2008-08-28 09:46:55
        DateFormat formatter = new SimpleDateFormat("yyyy");
        theDay = (Date)formatter.parse(dateStr);
        //A.log("DateUtil.getFormatDateShortShort() theDayStr:" + dateStr + " theDay:" + theDay);
      } catch (ParseException e) {
        return null;
      }
      return theDay;
    }	
        
    public static Date getFormatDate() {
      return getFormatDateOrNow(new Date().toString());
    }	     

    public static Date getFormatDateOrNow(String dateStr) {
      Date theDay = null;
      try {
        // handle date in the format like log4j: 2008-08-28 09:46:55
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        theDay = (Date)formatter.parse(dateStr);
        //A.log("DateUtil.getFormatDateOrNow() theDayStr:" + dateStr + " theDay:" + theDay);
      } catch (ParseException e) {
        theDay = new Date();
      }
      return theDay;
    }	 

    public static String getEolFormatDateStr() {
      // Used by Encyclopedia of Life
      Date theDate = new Date();
      return getFormatDateStr(theDate, "yyyyMMdd-hh:mm");
    }

    public static String getAccessFormatDateTimeStr(Date theDate) {
      // Not used.  This is the format of access_log.
      return getFormatDateStr(theDate, "dd/mon/yyyy:HH:mm:ss");
    }

    public static String getFormatDateTimeStr() {
      Date theDate = new Date();
      return getFormatDateStr(theDate, "yyyy-MM-dd HH:mm:ss");
    }

    public static String getWebFormatDateTimeStr() {
      Date theDate = new Date();
      return getFormatDateStr(theDate, "yyyy-MM-dd:HH:mm:ss");
    }

    public static String getFormatDateTimeStr(Date theDate) {
      return getFormatDateStr(theDate, "yyyy-MM-dd HH:mm:ss");
    }

    public static String getFormatDateStr() {
      Date theDate = new Date();
      return getFormatDateStr(theDate, "yyyyMMdd");
    }

    public static String getFormatDateStr(String dateStr) {
      // With an input like: 2012-03-27 15:48:09.0 will return: 2012-03-27
      Date theDate = null;
      try {
        theDate = (new SimpleDateFormat("yyyy-MM-dd")).parse(dateStr);
      } catch (ParseException e) {
        s_log.error("e:" + e + " theDate:" + dateStr);
      }
      String formatDate = getFormatDateStr(theDate, "yyyy-MM-dd");
      //A.log("getFormatDateStr() dateStr:" + dateStr + " theDate:" + theDate + " formatDate:" + formatDate);
      return formatDate;
    }	 


    // Here we allow a date like 1980-09-00. The day can be 0 meaning unspecified.
    public static String formatDateStr(String theDate) {
	  if (theDate == null) return null;
      try {
		   java.util.Date utilDate = (new SimpleDateFormat("yyyy-MM-dd")).parse(theDate);
		   // A.log("formatDateStr() dateCollectedStartStr:" + dateCollectedStartStr + " utilDate:" + utilDate);
		   return (utilDate.toString());
      } catch (ParseException e) {
		   A.log("formatDateStr() theDate:" + theDate + " e:" + e);
		   // no action taken.
      }
      return null;
    }
    
    public static String getFormatDateStr(Date theDate) {
      String formatDate = getFormatDateStr(theDate, "yyyy-MM-dd");
      //A.log("DateUtil.getFormatDateStr() theDate:" + theDate + " formatDate:" + formatDate);
      return formatDate; 
    }	 

    public static String getFormatDateStr(Date theDate, String format) {
      return (new SimpleDateFormat(format)).format(theDate);
    }

    // Method designed to take scrappy user entered dates and return Antweb formatted Date
    public static Date constructDate(String dateStr) {
      Date returnDate = null;
      try {
        //s_log.warn("constructDate() deprecated dateStr:" + dateStr);
        //AntwebUtil.logShortStackTrace();
        //returnDate = new Date(dateStr);
        returnDate = (new SimpleDateFormat("yyyy-mm-dd").parse(dateStr));
      } catch (ParseException e) {
      //} catch (IllegalArgumentException e) {
      }

      if (returnDate == null) {
        try {
          returnDate = (new SimpleDateFormat("yyyy-MM-dd")).parse(dateStr);
        } catch (java.text.ParseException e) {
          //s_log.info("constructDate() 1 ParseException on dateStr:" + dateStr);                  
        }    

        if (returnDate == null) {
          try {
            returnDate = (new SimpleDateFormat("yyyy-MM")).parse(dateStr);  
            // The above line was changed to MM at the same time (Oct 7, 2013) as the case above, which was actually tested.
          } catch (java.text.ParseException e) {
            //s_log.info("constructDate() 2 ParseException on dateStr:" + dateStr);                  
          }  

          if (returnDate == null) {
            try {
              returnDate = (new SimpleDateFormat("yyyy")).parse(dateStr);
            } catch (java.text.ParseException e) {
              //s_log.info("constructDate() 3 ParseException on dateStr:" + dateStr);                  
            }  

            if (returnDate == null) {
              s_log.warn("constructDate() did not figure:" + dateStr);
            }
          }
        }
      }
      return returnDate;
    }

    
    public static Date getDate(String dateStr) {
      Date returnDate = null;
      try {
        returnDate = new Date(dateStr);
      } catch (IllegalArgumentException e) {
      }
      return returnDate;
    }
     
    public static boolean isDate(String dateStr) {
      Date date = DateUtil.getDate(dateStr);
      return (date != null);    
    }

    private static String trimDay(String day) {
      if (day == null) return day;
      if (day.contains("-")) day = day.substring(0, day.indexOf("-"));
      return day;
    }
    
    private static String romanConvertDate(String theDate) {
      if (theDate.contains(".")) {
        int firstPeriodI = theDate.indexOf(".");
        int secondPeriodI = theDate.indexOf(".", firstPeriodI + 1);
        String day = null, mo = null, year = null;
        if (secondPeriodI > 0) {
          day = theDate.substring(0, firstPeriodI);
          day = DateUtil.trimDay(day);
          mo = theDate.substring(firstPeriodI + 1, secondPeriodI);
          mo = getRomanMonth(mo);
          year = theDate.substring(secondPeriodI + 1);
          //A.p("romanConvertDate() firstPeriodI:" + firstPeriodI + " secondPeriodI:" + secondPeriodI + " year:" + year + " mo:" + mo + " day:" + day);
          return year + "-" + mo + "-" + day;        
        } else {
          mo = theDate.substring(0, firstPeriodI);
          mo = getRomanMonth(mo);
          year = theDate.substring(firstPeriodI + 1);
          return year + "-" + mo;
        }        
      }
      return theDate;
    }

    private static String getRomanMonth(String mo) {
      String month = mo;
      if (mo == null) return null;
      mo = mo.toLowerCase();
      if (mo.equals("i")) month = "01";
      if (mo.equals("ii")) month = "02";
      if (mo.equals("iii")) month = "03";
      if (mo.equals("iv")) month = "04";
      if (mo.equals("v")) month = "05";
      if (mo.equals("vi")) month = "06";
      if (mo.equals("vii")) month = "07";
      if (mo.equals("viii")) month = "08";
      if (mo.equals("ix")) month = "09";
      if (mo.equals("x")) month = "10";
      if (mo.equals("xi")) month = "11";
      if (mo.equals("xii")) month = "12";
      return month;
    }


    // Method designed to take scrappy user entered dates and return Antweb formatted Date String
    public static String constructDateStr(String dateStr) {
    
      if (dateStr == null) return null;
    
      if (DateUtil.isDate(dateStr)) {  // Like: 1 May 2011
        Date date = DateUtil.constructDate(dateStr);
        String formatDate = DateUtil.getFormatDateStr(date);
        return formatDate;
      }

      if (dateStr.contains(".")) {
        return DateUtil.romanConvertDate(dateStr);
      }
      
      if (DateUtil.getFormatDate(dateStr) != null) { // Like: 2011-05-01
        return dateStr;
      }
      if (DateUtil.getFormatDateShort(dateStr) != null) {  // Like: 2011-05
        return dateStr + "-00";      
      }

      if (dateStr.contains(" ")) {
        int firstSpaceI = dateStr.indexOf(" ");
        int secondSpaceI = dateStr.indexOf(" ", firstSpaceI + 1);
        if (firstSpaceI > 0) {
           if (secondSpaceI < 0) {
             A.p("constructDateStr(" + dateStr + ") mo year");    
             return dateStr;
           } else {
             A.p("constructDateStr(" + dateStr + ") day mo year");    
             // if mo > 12 error
             return dateStr;
           }
        }      
        return null;
      }

      if (DateUtil.getFormatDateShortShort(dateStr) != null) {  // Like: 2011
        return dateStr + "-00-00";      
      }
  
      return null;
    }
}


