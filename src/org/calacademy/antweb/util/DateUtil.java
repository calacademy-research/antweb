package org.calacademy.antweb.util;

import java.util.*;
import java.time.*;
import java.time.format.*;

import java.text.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

//import com.joestelmach.natty.*;
//import com.joestelmach.natty.generated.*;

public abstract class DateUtil {

  private static final Log s_log = LogFactory.getLog(DateUtil.class);

  public static int getYear() {
    return Year.now().getValue();
  }

  public static void runTests() {
    DateUtil.testConstructDateStr("1 May 2011");
    DateUtil.testConstructDateStr("2011-05-02");
    DateUtil.testConstructDateStr("2011-05-00");
    DateUtil.testConstructDateStr("2012-00-00");
    DateUtil.testConstructDateStr("2011-05");
    DateUtil.testConstructDateStr("2011");
    DateUtil.testConstructDateStr("11 Dec 1995");
    DateUtil.testConstructDateStr("May 1 2011");
    DateUtil.testConstructDateStr("May 1, 2011");
    DateUtil.testConstructDateStr("29.v.1986");
    DateUtil.testConstructDateStr("iv.1976");
    DateUtil.testConstructDateStr("11-14.ii.2007");
    DateUtil.testConstructDateStr("10.VIII.1987");
    DateUtil.testConstructDateStr("12 2012");
    DateUtil.testConstructDateStr("10 12 2012"); // How do we know? Flag this!
    DateUtil.testConstructDateStr("21-Jul-99");
    DateUtil.testConstructDateStr("21-Jul-1699");
    DateUtil.testConstructDateStr("21-Jul-2222");
    DateUtil.testConstructDateStr("08/1957");
    DateUtil.testConstructDateStr("1986/00/00");
    DateUtil.testConstructDateStr("2017/");
    DateUtil.testConstructDateStr("1986/10/");
    DateUtil.testConstructDateStr("1898/1899");
    DateUtil.testConstructDateStr("02/1933");
    DateUtil.testConstructDateStr("19/10/2013");
    DateUtil.testConstructDateStr("1915-7-00");
    DateUtil.testConstructDateStr("1995-11-27");
    DateUtil.testConstructDateStr("2010/08/12");
    DateUtil.testConstructDateStr("11/08/2011");
    DateUtil.testConstructDateStr("11-Oct-2015");
    DateUtil.testConstructDateStr("1996-10-06");
    DateUtil.testConstructDateStr("4/2/69");
    DateUtil.testConstructDateStr("04/02/69");
    DateUtil.testConstructDateStr("1995-08-31");
    s_log.debug(DateUtil.getConstructDateStr("1962-08-28"));
    s_log.debug(DateUtil.getConstructDateStr("7/28/1958"));
  }

  public static Date getFormatDateShort(String dateStr) {
    Date theDay = null;
    try {
      // handle date in the format like log4j: 2008-08-28 09:46:55
      DateFormat formatter = new SimpleDateFormat("yyyy-MM");
      theDay = formatter.parse(dateStr);
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
      theDay = formatter.parse(dateStr);
      //A.log("DateUtil.getFormatDateShortShort() theDayStr:" + dateStr + " theDay:" + theDay);
    } catch (ParseException e) {
      return null;
    }
    return theDay;
  }

  public static String getAccessFormatDateTimeStr(Date theDate) {
    // Not used.  This is the format of access_log.
    return getFormatDateStr(theDate, "dd/mon/yyyy:HH:mm:ss");
  }

  public static String getFormatDateTimeStr() {
    Date theDate = new Date();
    return getFormatDateStr(theDate, "yyyy-MM-dd HH:mm:ss");
  }

  public static String getFormatDateTimeMilliStr() {
    Date theDate = new Date();
    return getFormatDateStr(theDate, "yyyy-MM-dd HH:mm:ss.SSS");
  }

  public static String getFormatDateStr() {
    Date theDate = new Date();
    return getFormatDateStr(theDate, "yyyy-MM-dd");
  }

  public static String getFormatDateTimeStr(Date theDate) {
    return getFormatDateStr(theDate, "yyyy-MM-dd HH:mm:ss");
  }

  public static String getFormatDateStr(String dateStr) {
    // With an input like: 2012-03-27 15:48:09.0 will return: 2012-03-27
    Date theDate = null;
    try {
      theDate = new SimpleDateFormat("yyyy-MM-dd").parse(dateStr);
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
      Date utilDate = new SimpleDateFormat("yyyy-MM-dd").parse(theDate);
      // A.log("formatDateStr() dateCollectedStartStr:" + dateCollectedStartStr + " utilDate:" + utilDate);
      return utilDate.toString();
    } catch (ParseException e) {
      s_log.debug("formatDateStr() theDate:" + theDate + " e:" + e);
      // no action taken.
    }
    return null;
  }

  // Antweb Preferred format. How the String dates are stored in specimen table dateCollectedStart and dateCollectedEnd
  public static String getFormatDateStr(Date theDate) {
    String formatDate = getFormatDateStr(theDate, "yyyy-MM-dd");
    //A.log("DateUtil.getFormatDateStr() theDate:" + theDate + " formatDate:" + formatDate);
    return formatDate;
  }

  public static String getFormatDateStr(Date theDate, String format) {
    return new SimpleDateFormat(format).format(theDate);
  }

  // Take an String of unknown date format and return a string in the correct format. Used for Specimen upload.
  public static String getConstructDateStr(String dateStr) {
    if (dateStr == null) return null;
    Date date = constructDate(dateStr);
    if (date == null) return null;
    String formatString = getFormatDateStr(date);
    return formatString;
  }

  private static Date format(String format, String dateStr) {
    Date returnDate = null;
    try {
      returnDate = new SimpleDateFormat(format).parse(dateStr);
    } catch (ParseException e) {
      //if ("2010/08/12".equals(dateStr)) A.log("format() NOT found:" + dateStr + " format:" + format + " e:" + e);
    }
    return returnDate;
  }

  private static Date newFormat(String format, String dateStr) {
    Date date = null;
    try {
      //if ("2010/08/12".equals(dateStr)) A.log("newFormat() dateStr:" + dateStr + " date:" + date);
      ZoneId defaultZoneId = ZoneId.systemDefault();
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
      LocalDate localDate = LocalDate.parse(dateStr, formatter);
      date = Date.from(localDate.atStartOfDay(defaultZoneId).toInstant());
      //A.log("newFormat() dateStr:" + dateStr + " date:" + date);
    } catch (Exception e) {
      //A.log("newFormat() dateStr:" + dateStr + " step:" + s_debugStep + " e:" + e);
      //AntwebUtil.logShortStackTrace();
    }
    return date;
  }

  private static Date handleDdMmmYy(String dateStr) {
    //String dateStr = "21-Jul-99"; Because of University of Pretoria
    int indexOfSecondHyphen = dateStr.indexOf('-', 6);
    if (indexOfSecondHyphen < 6) return null;
    if (dateStr.length() > 9) return null;
    String shortYear = dateStr.substring(indexOfSecondHyphen + 1);
    int year = Integer.parseInt(shortYear);
    if (year > 50) {
      year = year + 1900;
    } else {
      year = year + 2000;
    }
    dateStr = dateStr.substring(0, indexOfSecondHyphen + 1) + year;
    //A.log("message-body.jsp i:" + indexOfSecondHyphen + " year:" + year + " dateStr:" + dateStr + " l:" + dateStr.length());
    Date date = format("dd-MMM-yyyy", dateStr);
    return date;
  }

  private static int s_debugStep = 0;
  public static String testConstructDateStr(String dateStr) {
    if (dateStr == null) return null;
    // try {
    String constructedDate = getConstructDateStr(dateStr);
    if (constructedDate == null) s_log.debug("testConstructDateStr() dateStr:" + dateStr + " formatted:" + constructedDate + " step:" + s_debugStep);
    return constructedDate;
  }
  // Method designed to take scrappy user entered dates and return Antweb formatted Date
    public static Date constructDate(String dateStr) {

      Date returnDate = null;

      // Antweb preferred format
      if (returnDate == null) returnDate = format("yyyy-MM-dd", dateStr);
      if (returnDate == null) returnDate = format("dd-MMM-yyyy", dateStr);
      if (returnDate == null) returnDate = format("yyyy-MMM-dd", dateStr);
      if (returnDate == null) returnDate = format("dd MMM yyyy", dateStr);
      if (returnDate == null) returnDate = format("MMM dd yyyy", dateStr);
      if (returnDate == null) returnDate = format("dd-MMM-yy", dateStr);
      if (returnDate == null) returnDate = format("MMM dd, yyyy", dateStr);
      if (returnDate == null) returnDate = newFormat("yyyy/MM/dd", dateStr);

      if (returnDate == null) returnDate = format("MM/dd/yyyy", dateStr); // Was dd/MM/yyyy

      if (returnDate == null) returnDate = format("MM/yyyy", dateStr);
      if (returnDate == null) returnDate = format("yyyy/MM", dateStr);
      if (returnDate == null) returnDate = format("yyyy/", dateStr);
      if (returnDate == null) returnDate = format("MM yyyy", dateStr);
      if (returnDate == null) returnDate = format("dd MM yyyy", dateStr);

      if (returnDate != null) s_debugStep = 1;

      // NOT preferred. JOrivel uses them. Can not be parsed by Natty below.
      if (returnDate == null) {
        returnDate = format("dd/MM/yyyy", dateStr);
        s_debugStep = 6;
      }

      if (returnDate == null) {
        // Not a simply parsed date.  perhaps it is like: 8-11 Feb 2010   or like: 1 Feb - Mar 2010
        // Take what is after the hyphen and see if that can work.
        if (dateStr.contains("-")) {
          String substring = dateStr.substring(dateStr.indexOf("-") + 1);
          //A.log("constructDate() hypen removed from origDatesCollected:" + dateStr + " substring:" + substring);
          returnDate = constructDate(substring);
          s_debugStep = 7;
        }
      }

      if (returnDate == null) {
        if (DateUtil.getFormatDateShort(dateStr) != null) {  // Like: 2011-05
          returnDate = getDate(dateStr + "-01");
          s_debugStep = 8;
        }
      }

      if (returnDate == null) {
        if (dateStr.contains(" ")) {
          int firstSpaceI = dateStr.indexOf(" ");
          int secondSpaceI = dateStr.indexOf(" ", firstSpaceI + 1);
          if (firstSpaceI > 0) {
            if (secondSpaceI < 0) {
              A.p("constructDateStr(" + dateStr + ") mo year");
            } else {
              A.p("constructDateStr(" + dateStr + ") day mo year");
              // if mo > 12 error
            }
            returnDate = getDate(dateStr);
          }
          s_debugStep = 9;
        }
      }

      if (returnDate == null) {
        if (DateUtil.getFormatDateShortShort(dateStr) != null) {  // Like: 2011
          returnDate = getDate(dateStr + "-01-01");
          s_debugStep = 10;
        }
      }

      if (returnDate == null) {
        if (dateStr.contains(".")) {
          returnDate = DateUtil.romanConvertDate(dateStr);
          s_debugStep = 11;
        }
      }

/*
      if (returnDate == null) {
        // Using here the Natty date parser. http://natty.joestelmach.com/
        Parser parser = new Parser();
        List<DateGroup> groups = parser.parse(dateStr); //"the day before next thursday");
        for (DateGroup group : groups) {
          List<Date> dates = group.getDates();
          if (dates.get(0) != null) returnDate = dates.get(0);
          s_debugStep = 12;
          A.log("constructDate() 12 dateStr:" + dateStr + " returnDate:" + returnDate);
          break;

        //int line = group.getLine();
        //int column = group.getPosition();
        //String matchingValue = group.getText();
        //String syntaxTree = group.getSyntaxTree().toStringTree();
        //Map> parseMap = group.getParseLocations();
        //boolean isRecurreing = group.isRecurring();
        //Date recursUntil = group.getRecursUntil();
        }
      }
*/

      // This is the new way. Pretty cumbersome, right?
      if (returnDate == null) {
        try {
          DateTimeFormatter formatter = DateTimeFormatter.ofPattern("E MMM dd HH:mm:ss z uuuu").withLocale(Locale.US);
          ZonedDateTime zdt = ZonedDateTime.parse(dateStr, formatter);
          LocalDate ld = zdt.toLocalDate();
          DateTimeFormatter fLocalDate = DateTimeFormatter.ofPattern("dd/MM/uuuu");
          String output = ld.format(fLocalDate);
          returnDate = getDate(output);
          s_debugStep = 13;
        } catch (DateTimeParseException e) {
        } catch (Exception e) {
          s_log.debug("constructDate() 1 dateStr:" + dateStr + " e:" + e);
        }
      }

      // Jul 2002
      if (returnDate == null) {
        returnDate = format("MMM yyyy", dateStr);
        s_debugStep = 14;
      }

      // 1995
      if (returnDate == null) {
        returnDate = format("yyyy", dateStr);
        s_debugStep = 15;
      }

      if (returnDate == null) {
        s_debugStep = 16;
        return null;
      }

      if (returnDate.after(new Date())) {
        s_debugStep = 17;
        return null;
      }

      Date validCutoff = getDate("1700-0-01");
      if (returnDate.before(validCutoff)) {
          //A.log("constructDate() dateStr:" + dateStr + " returnDate:" + returnDate + " is before validCutoff:" + validCutoff);

          // If Ex: 21-Jul-99 misread earlier, here we fix.
          returnDate = handleDdMmmYy(dateStr);
          if (returnDate != null) {
            s_debugStep = 18;
            //A.log("constructDate() 1 dateStr:" + dateStr + " returnDate: " + returnDate);
            return returnDate;
          }

          // If Ex: 4/6/66 misread earlier, here we fix.
          returnDate = handleDMYy(dateStr);
          //A.log("constructDate() 2 dateStr:" + dateStr + " returnDate: " + returnDate);
          s_debugStep = 21;
          if (returnDate != null) return returnDate;

          s_debugStep = 19;
          return null;
      }

      return returnDate;
    }


  private static Date handleDMYy(String dateStr) {
    //String dateStr = "1/6/66"; // Would be Jan 6, 1966. Takes a bit of work.
    //A.log("handleDMYy() dateStr:" + dateStr);

    int indexOfFirstHyphen = dateStr.indexOf('/');
    if (indexOfFirstHyphen < 1 || indexOfFirstHyphen > 3) return null; // Nope, won't support 1985/10

    int indexOfSecondHyphen = dateStr.indexOf('/', indexOfFirstHyphen + 1);

    //A.log("handleDMYy() dateStr:" + dateStr + " 11:" + indexOfFirstHyphen + " i2:" + indexOfSecondHyphen);

    if (indexOfSecondHyphen < 2) return null;
    if (dateStr.length() > 8) return null;
    String shortYear = dateStr.substring(indexOfSecondHyphen + 1);

    //A.log("handleDMYy() dateStr:" + dateStr + " shortYear:" + shortYear);

    int year = Integer.parseInt(shortYear);
    if (year > 50) {
      year = year + 1900;
    } else {
      year = year + 2000;
    }
    dateStr = dateStr.substring(0, indexOfSecondHyphen + 1) + year;
    String firstNum = dateStr.substring(0, indexOfFirstHyphen);
    int firstNumInt = Integer.parseInt(firstNum);
    Date date = null;
    if (firstNumInt <= 12) {
      date = format("MM/dd/yyyy", dateStr);
    } else {
      date = format("dd/MM/yyyy", dateStr);
    }
    s_log.debug("handleDMYy() i:" + indexOfSecondHyphen + " year:" + year + " dateStr:" + dateStr + " l:" + dateStr.length() + " firstNum:" + firstNum + " date:" + date);
    return date;
  }

   // Basciallly just for validation. Have a dateStr in our preferred format and want a date object.
    public static Date getDate(String dateStr) {
        Date returnDate = null;
        if (dateStr != null) {
          try {
            returnDate = new SimpleDateFormat("yyyy-MM-dd").parse(dateStr);
            //A.log("constructDate() 2 dateStr:" + dateStr + " returnDate:" + returnDate);
          } catch (ParseException e) {
            //A.log("constructDate() 2 dateStr:" + dateStr + " e:" + e);
          }
        }
        return returnDate;
    }

    public static boolean isDate(String dateStr) {
      Date date = DateUtil.constructDate(dateStr);
      return date != null;
    }

  private static Date getTruncatedDate(String truncDatesCollected) {
    // Perhaps it's like: may 2003
    Date returnDate = getDate("1 " + truncDatesCollected);
    if (returnDate != null) return returnDate;

    // Perhaps it's like: 2003
    returnDate = getDate("1 Jan " + truncDatesCollected);
      return returnDate;
  }

    private static String trimDay(String day) {
      if (day == null) return day;
      if (day.contains("-")) day = day.substring(0, day.indexOf("-"));
      return day;
    }

    private static Date romanConvertDate(String theDate) {
      String dateStr = romanConvertDateStr(theDate);
      Date date = getDate(dateStr);
      return date;
    }
    private static String romanConvertDateStr(String theDate) {
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


/*
    public static void d(String d) {
		Date theDate = DateUtil.constructDate(d);

        //(new SimpleDateFormat("yy"))

		A.log("date:" + d + " formatted:" + getFormatDateStr(theDate));

    }
*/

  /* // DEPRECATED ALMOST THERE
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
*/


/*
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
*/

/*
  private Date getParseDate(String thedate) {
    Date returnDate = null;

    thedate = thedate.trim();
    if (thedate.equals("")) return null;

    A.log("getParseDate(" + thedate + ") 1 ");

    // many possibilities here...
    returnDate = getDate(thedate);
    if (returnDate != null) return returnDate;
    A.log("getParseDate(" + thedate + ") 1 ");

    // Not a simply parsed date.  perhaps it is like: 8-11 Feb 2010   or like: 1 Feb - Mar 2010
    if (thedate.contains("-")) {
      String t = thedate.substring(thedate.indexOf("-") + 1);
      //s_log.info("getDateCollected() hypen removed from origDatesCollected:" + thedate + " making:" + t);
      returnDate = getDate(t);
      if (returnDate != null) return returnDate;

      returnDate = getTruncatedDate(t);
      if (returnDate != null) return returnDate;
    }

    A.log("getParseDate(" + thedate + ") 1 ");

    // Maybe it is simply like: Mar 2011   or like: 2010
    returnDate = getTruncatedDate(thedate);

    A.log("getParseDate(" + thedate + ") 1 ");

    if (returnDate != null) return returnDate;

    s_log.warn("getParseDate() Date not found for date:" + thedate);

    return null;
  }
*/

  /*
  private static Date parseDate(String dateStr) {
    try {
      Date d = new Date(dateStr);
      if (AntwebProps.isDevMode()) {
        s_log.warn("parseDate() DEPRECATED! Found from dateStr:" + dateStr + " date:" + d);
      }
      return d;
    } catch (Exception e) {
      // These are expected to occur with our data.
      A.log("parseDate() failed on datesCollected:" + dateStr + " e:" + e);
    }
    return null;
  }
  */

/*
// To Deprecate! Almost there
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
*/
}


