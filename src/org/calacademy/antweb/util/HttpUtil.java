package org.calacademy.antweb.util;
	
import java.io.*;
import java.net.*;
import java.util.*;

import javax.servlet.http.*;
import javax.servlet.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;


import org.apache.struts.action.*;

import org.calacademy.antweb.Utility;
import org.calacademy.antweb.util.AntwebUtil;

import javax.sql.DataSource;
import java.sql.SQLException;

import java.util.regex.*;

import org.calacademy.antweb.AntFormatter;
import org.apache.commons.httpclient.util.URIUtil;


public abstract class HttpUtil {

    private static final Log s_log = LogFactory.getLog(HttpUtil.class);  

	private final static String USER_AGENT = "Mozilla/5.0";
	
/*
     If server is suspected of being taken down by ungoverned traffic... 

     if there are a variety of errors in the logs:
        
          tail --lines 1000 /antweb/links/antweb.log
       
      and if the database connection pool is filling up quickly to the point of non-functioning....

          in mysql $ Show full processlist;
          
     Look at the bottom of log file for offenders:
       https://www.antweb.org/web/log/accessLog.txt
     
     Look for something unique in the request to use to block
       2016-10-18 14:40:48 url:https://www.antweb.org/specimenImages.do?code=casent0107470?code=casent0107470 referer:https://www.antweb.org/specimenImages.do?name=casent0107470&project=allantwebants user-agent:Mozilla/5.0 (compatible; archive.org_bot +http://www.archive.org/details/archive.org_bot)
     
     To see the extent of the problem, grep for your current hour in the access_log and ssl_access_log to see the number of hits.

       cd /antweb/links
       sudo grep "18/Oct/2016:00" ssl_access_log | wc

     If much more than 10,000 / hour, it seems like someone is hammering the server.

     To block, in invalidRequest below add a condition that will weed out...
       For example see *1 in src/org/calacademy/antweb/util/HttpUtil.java.
 
     See the weeded out records here:
       https://www.antweb.org/web/log/invalid.log
*/

    // Useful for diagnostics in a jsp file: <%= HttpUtil.showEncodings(request, response) %>
    public static String showEncodings(HttpServletRequest request, HttpServletResponse response) {
      return "Encodings - request:" + request.getCharacterEncoding() + " response:" + response.getCharacterEncoding() + " system:" + System.getProperty("file.encoding");
    }
    
	public static void setUtf8(HttpServletRequest request, HttpServletResponse response) {
		try {
		  request.setCharacterEncoding("UTF-8");
		  response.setCharacterEncoding("UTF-8");
		} catch (UnsupportedEncodingException ex) {
		}
	}
    

    public static ActionForward invalidRequest(HttpServletRequest request, ActionMapping mapping) {

      String invalidMessage = null;
      String targetSic = HttpUtil.getTarget(request); //Sic

      invalidMessage = HttpUtil.isLegitRequest(request);
      if (AntwebProps.isDevMode() && (invalidMessage != null)) s_log.warn("invalidRequest() invalidMessage:" + invalidMessage);
      if (invalidMessage == null && (HttpUtil.getIsBot(request) && HttpUtil.isPost(request))) {
          invalidMessage = "Bot posts not allowed.  " + targetSic;
      }

      // *1 Catch offending spammers.  This was added to catch offender on Oct 18 2016.
      String userAgent = request.getHeader("User-Agent");
      if (invalidMessage == null && userAgent != null && userAgent.contains("Pcore-HTTP")) {
        invalidMessage = "disallowed *1. " + HttpUtil.getHeadersStr(request);
      }
      
      if (invalidMessage == null) {
          int questionMarkI = targetSic.indexOf("?");
          if (targetSic.indexOf("?", questionMarkI + 1) > 0) {
              invalidMessage = "Multiple ? in request:" + targetSic;
          }
      }

      if (invalidMessage == null && targetSic.contains("jsessionid") && !targetSic.contains("?")) {
          invalidMessage = "Invalid request:" + targetSic;
      }

/*
      // This test was breaking Compare Images feature.  By omission, what invalid request is allowed?
      if (invalidMessage == null && request.getQueryString() == null) {
          if (!targetSic.contains("groups.do")) {
            s_log.warn("invalidRequest() Investigate.  Null queryString for targetSic:" + targetSic);          
            String message = "";
            if (targetSic.contains("antweb.org")) message = "invalidRequest() " + AntwebUtil.getShortStackTrace();
            s_log.info(message);
          }
      } 
*/
      
      String queryString = request.getQueryString();
      if (queryString == null) {
          return null;
      }

      String message = BadActorMgr.ifBadActorBlockedGetMessage(request);
      if (message != null) {
          request.setAttribute("message", message);
          return (mapping.findForward("message"));
      }

      if (HttpUtil.hasIllegalStr(queryString, request)) {
            request.setAttribute("message", "Illegal characters.");
            return (mapping.findForward("message"));
      }

      boolean hasSpecialChars = false;    
      if (targetSic.contains("locality.do")) {
        hasSpecialChars = AntFormatter.hasTextSpecialCharacter(queryString);      
      } if (targetSic.contains("login.do")) {
        hasSpecialChars = AntFormatter.hasLoginSpecialCharacter(queryString);
      } else {
        hasSpecialChars = AntFormatter.hasWebSpecialCharacter(queryString);
      }
 
      //if (queryString.contains("%20and%20")) hasSpecialChars = true;
        // Not sure why had this, but can't because of pages like this...
        //https://www.antweb.org/taxonomicPage.do?rank=genus&countryName=Saint%20Vincent%20and%20the%20Grenadines

      if (queryString.contains("%27")) hasSpecialChars = true;
      //if (queryString.contains("%28")) hasSpecialChars = true; // parens
      //if (queryString.contains("%29")) hasSpecialChars = true;
 
      if (hasSpecialChars) {
          request.setAttribute("message", "Unallowed characters.");
          s_log.info("invalidRequest() unallowed characters: " + HttpUtil.getTarget(request));
          return mapping.findForward("message");
      }
      if (invalidMessage != null) {
          //LogMgr.appendLog("badRequest.log", targetSic);
          LogMgr.appendLog("invalid.log", invalidMessage);
          request.setAttribute("message", invalidMessage);
          return mapping.findForward("message");
      }
    
      return null; 
    }     
        
    
    public static boolean isBot(HttpServletRequest request) {
      return HttpUtil.getIsBot(request);
    }    
        
    public static boolean getIsBot(HttpServletRequest request) {
      boolean isBot = false;
      String userAgent = (String) request.getHeader("user-agent");
      if (userAgent != null) {
        userAgent = userAgent.toLowerCase();
        if ( (userAgent.contains("bot")) 
          || (userAgent.contains("spider"))
          || (userAgent.contains("slurp"))
          || (userAgent.contains("ahrefs"))
          || (userAgent.contains("baidu"))
          || (userAgent.contains("The Knowledge AI"))
          || (userAgent.contains("opensiteexplorer"))
          || (userAgent.contains("Gigabot"))
          || (userAgent.contains("SemrushBot"))
          || (userAgent.contains("centurybot"))
          || (userAgent.contains("bingbot"))
            || (userAgent.contains("naver.me"))
          //|| (userAgent.contains())
           ) {
          isBot = true;   
        }
        
        if (UserAgentTracker.isOveractive(request)) {
          isBot = true;
        }        
      }
      return isBot;
    }
    
    public static boolean isInWhiteList(String input) {
      // This is used for validating query strings
      if (input == null) return true;
      String clean = input.replaceAll("[^A-Za-z0-9\\[\\]=]", "");
        return input.equals(clean);
    }

    public static boolean isInWhiteListCheck(String input, HttpServletResponse response) {    
        // See PageAction.java.
        //s_log.warn("execute() url:" + url);
      boolean isInWhiteList = HttpUtil.isInWhiteList(input);
      if (!isInWhiteList) {
        try { 
          String output = "Illegal characters in input:" + input;
          HttpUtil.write(output, response);
        } catch (IOException e) {
          String message = "isInWhiteListCheck input:" + input + " e:" + e;
          s_log.warn("isInWhiteListCheck() " + message);
          AdminAlertMgr.log(message);
        }
      }
      return isInWhiteList;
    }
    
    public static String isLegitRequest(HttpServletRequest request) {
        boolean botAttackDefence = HttpUtil.isBotAttackDefence(request);
        if (botAttackDefence) {
          return "Invalid request.";
        }    
        String queryString = request.getQueryString();
        if (queryString == null) {
          return null;
        }
        int reasonCode = 0;
        // Allowed because of locality names: http://localhost/antweb/locality.do?name=Montagne%20d%27Ambre%20975
          //if (queryString.contains("%27")) reasonCode = 1;    // single quote
        if (queryString.contains("%22")) reasonCode = 2;    // double quote
        if (queryString.contains("script")) reasonCode = 3; 
        if (queryString.contains("..%2f")) reasonCode = 4; 
        if (queryString.contains("./")) reasonCode = 5; 
        if (queryString.contains("../")) reasonCode = 6; 
        if (queryString.contains("%2e")) reasonCode = 7;
        //if (queryString.contains("=")) reasonCode = 8;
        
        if (reasonCode > 0) {
          s_log.info("isLegitRequest() Invalid query string reasonCode:" + reasonCode + " RequestInfo:" + HttpUtil.getRequestInfo(request));
          return "Invalid query string (" + reasonCode + ")"; 
        }

        //A.log("isLegitRequest() legit:yes queryString:" + queryString + " reasonCode:" + reasonCode);
        return null;
    }


  
    private static boolean s_isOffline = false;
    // In order to set to offline, hardcode it above, or turn s_showResults to true and look 
    // at the log results to find a string that will uniquely identify the service
    // (such as gogoinflight or unitied-wifi).
    private static boolean s_showResults = false;
    private static String[] s_services = {"gogoinflight", "united-wifi"};
      // This is the persisted property resulting from the various conditions that may indicate
    // on of offline.
    private static Boolean s_isOnline = null;
  
    public static boolean isOnline() {
      if (!AntwebProps.isDevMode()) {
        // This is only for dev, when working offline.  Server is always online.
        return true;
      }
      if (s_isOnline != null) {
        // Only need to test once.
        return s_isOnline.booleanValue();
      }
      if (testOnline()
          && true
        // && can fetch a page, ping something, once, at startup, store it.
        ) {
        s_isOnline = Boolean.TRUE;
      } else {
        s_isOnline = Boolean.FALSE;
        s_log.warn("Warning... Running in Offline Mode.");
      }
      return s_isOnline.booleanValue();
    }
  
    public static boolean isOffline() {
      return !HttpUtil.isOnline();
    }

    private static boolean testOnline() {  
      if (AntwebProps.isDevMode() && s_isOffline) {
        s_log.warn("testOnline() Offline is hardcoded.  Offline.");
        return false;
      }
      try {
        String content = HttpUtil.getUrl("http://google.com");
        if (content == null) {
          s_log.warn("testOnline() Null returned.  Offline.");
          return false;
        }
        if ("".equals(content)) {
          s_log.warn("testOnline() Empty string returned.  Offline.");
          return false;
        }
        for (String service : s_services) {
          if (content.contains(service)) {
            s_log.warn("testOnline() service:" + service + ".  Offline.");
            return false; 
          }
        }
        if (AntwebProps.isDevMode()) {
            // Change this to true if you want to see the page returned
            // in order to create a condition to return false (as in gogoinflight).
          if (s_showResults) {   
            s_log.warn("testOnline content:" + content);
          }
        }
      } catch (IOException e) {
        return false;
      }
      return true;
    }
    
    public static boolean isDisallowedFileType(String fileName) {
        return (fileName != null) &&
                ((fileName.contains(".jsp"))
                        || (fileName.contains(".JSP"))
                        || (fileName.contains(".php"))
                        || (fileName.contains(".PHP"))
                        || (fileName.contains(".pl"))
                        || (fileName.contains(".PL"))
                        || (fileName.contains(".sh"))
                        || (fileName.contains(".SH"))
                );
    }
    
    public static boolean isAlphaNumeric(String str) {
      // str = "abcdefà";
      if (str == null) return true;
      Pattern p = Pattern.compile("[^a-zA-Z0-9@.]");
      boolean hasSpecialChar = p.matcher(str).find();
      return !hasSpecialChar;
    }
    
    public static boolean hasIllegalChars(String[] strings, HttpServletRequest request) {
      //A.log("hasIllegalChars() strings:" + strings);
      for (String str : strings) {
        if (str == null) continue;
        if (str.contains("&") || isIllegalStr(str)) {
          AntwebUtil.log("HttpUtil.hasIllegalChars() 1 true str:" + str + " target:" + HttpUtil.getTarget(request));
          BadActorMgr.addBadActor(request);
         return true;
        }
      }
      return false;
    }

    public static boolean hasIllegalStr(String str, HttpServletRequest request) {
        if (isIllegalStr(str)) {
            AntwebUtil.log("HttpUtil.hasIllegalChars() 2 true str:" + str + " target:" + HttpUtil.getTarget(request));
            BadActorMgr.addBadActor(request);
            return true;
        }
        return false;
    }

    public static boolean isIllegalStr(String string) {
        String str = string.toLowerCase();
        return (str.contains("sleep") && !(str.contains("sleeping") || str.contains("kameelsleep")))
                || str.contains("case%20")
                || str.contains("select%20")
                || (str.contains("order%20") && !str.contains("border"))
                || str.contains("3ddbms_pipe.receive_message")
                || str.contains("waitfor  ");
    }
    private static final String[] HEADERS_TO_TRY = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR" };

    public static String getClientIpAddress(HttpServletRequest request) {
         if (AntwebProps.isLocal()) return getDevIpAddress(request);

         for (String header : HEADERS_TO_TRY) {
            String ip = request.getHeader(header);
            if (ip != null && ip.length() != 0 && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }
        }
        return request.getRemoteAddr();
    }
    public static String getDevIpAddress(HttpServletRequest request)
    {
        String appUrl = request.getScheme() + "://"+ request.getLocalAddr();
        return appUrl;
    }

    private static boolean isBadActorBlocked(HttpServletRequest request) {
      return false; // To be...
    }

    public static boolean abortAction(String content) {
      // This method looks for jsp injection code.  True to abort.
        return (content != null) &&
                ((content.contains("Loesch")) // This is the author of Browser.jsp
                        || (false)
                );
    }
    
    public static void blockFishingAttack(HttpServletRequest request, ActionErrors errors) {
        //if (AntwebProps.isDevMode()) {
          String requestString = HttpUtil.getQueryString(request); //Info(request);

          requestString = HttpUtil.getRequestInfo(request);

          //s_log.warn("blockFishingAttack() hack attempt.  request:" + requestString.substring(0, 25));

          if (requestString != null && requestString.contains("..")) {
            LogMgr.appendLog("hacks.log", DateUtil.getFormatDateTimeStr() + " - " + requestString);
            errors.add("name", new ActionError("error.longfield"));
          }           
        //}
    }

    public static int botAttackCount = 0;    
    
    public static boolean isBotAttackDefence(HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (queryString == null) return false;
        if ( 
             (queryString.contains("%2Cnull%2Cnull%2"))
          || (queryString.contains("union%20all"))
          || (queryString.contains("ascii"))
          ) {
          ++botAttackCount;
          if ((botAttackCount % 100 ) == 0) {
            s_log.error("isBotAttackDefence() count:" + botAttackCount);
          }
          return true;
        }
        return false;
    }
    
    public static int serverBusyCount = 0;
    
    public static boolean tooBusyForBots(DataSource dataSource, HttpServletRequest request)
       throws SQLException {
        boolean isServerBusy = DBUtil.isServerBusy(dataSource, request);                                  		  
        if (HttpUtil.getIsBot(request) && isServerBusy) {
          ++serverBusyCount;
          if ((serverBusyCount % 100 ) == 0) {
              s_log.warn("tooBusyForBots() serverBusyCount:" + serverBusyCount);
          }
          return true;
        }
        return false;    
    }
    
    public static ActionForward sendMessage(HttpServletRequest request, ActionMapping mapping, String message) {
      request.setAttribute("message", message);
      return mapping.findForward("message");
    }
    
    public static Map<String, String> getHeadersInfo(HttpServletRequest request) {
		Map<String, String> map = new HashMap<>();

		Enumeration headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String key = (String) headerNames.nextElement();
			String value = request.getHeader(key);
			map.put(key, value);			
		}
        return map;
    }    

    public static String getHeadersStr(HttpServletRequest request) {
        String headerStr = "headerStr:";
		Enumeration headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String key = (String) headerNames.nextElement();
			String value = request.getHeader(key);
            headerStr += " " + key + ":" + value;
		}
        return headerStr;
    }           
    
    public static boolean isPost(HttpServletRequest request) {
        if (request.getContentType() != null) {
          String contentType = request.getContentType().toLowerCase();
            //A.log("isPost:" + request.getContentType());
            return (contentType.contains("multipart/form-data"))
                    || (contentType.contains("application/x-www-form-urlencoded"))
                    || (contentType.contains("text/plain"));
        }
        return false;
    }

    public static String encodePath(String str) {
        String target = null;
        if (str != null) {
            try {
                target = URIUtil.encodePath(str, "ISO-8859-1");
            } catch (org.apache.commons.httpclient.URIException e) {
                // do nothing.
            }
        }
        return target;
    }

/*
          // This will turn spaces into +
          //import org.apache.commons.httpclient.util.URIUtil;
          //url = URIUtil.encodeQuery(url);
 */
    public static String encode(String toEncode) {
        String encoded = null;
        try {
            // ADDED the utf8 below 20200216.
            encoded = java.net.URLEncoder.encode(toEncode, "utf8");
        } catch (java.io.UnsupportedEncodingException e) {
            s_log.error("encode() e:" + e);
        }
        return encoded;
    }

    public static String decode(String toDecode) {
        String decoded = null;
        try {
            // ADDED the utf8 below 20200216.
            decoded = java.net.URLDecoder.decode(toDecode, "utf8");
        } catch (java.io.UnsupportedEncodingException e) {
            s_log.error("decode() e:" + e);
        }
        return decoded;
    }


  public static String getParamString(HttpServletRequest request) 
      throws java.net.SocketTimeoutException {
   // This gets used by UgSessionRequestFilter to block SQLInjection attacks.
    String paramString = "";            
    Enumeration names = request.getParameterNames();
    while (names.hasMoreElements()) {
      String name = (String) names.nextElement();
      String[] values = request.getParameterValues(name);    
      String value = values[0];
      if (values.length > 1) value += ",...";
      paramString += name + ":" + value + " ";
    }
    return paramString;
  }

  // Could be multiple ones? This just gets first.
  public static String getParamValue(String param, HttpServletRequest request) {
    String paramsStr = "";            
    Enumeration names = request.getParameterNames();
    while (names.hasMoreElements()) {
      String name = (String) names.nextElement();
      if (param.equals(name)) {
        String[] values = request.getParameterValues(name);    
        for (String value : values) {
          return value;
        }
      }
    }
    return paramsStr;
  }

  // WTF? Based on faulty method?
  public static String getParamsString(String param, HttpServletRequest request) {
    return HttpUtil.getParamsStr(param, request);
  }
  // WTF? This doesn't seem to use param?
  public static String getParamsStr(String param, HttpServletRequest request) {
    // Used by oneView.jsp to get the multiple "chosen" params.
    String paramsStr = "";            
    Enumeration names = request.getParameterNames();
    while (names.hasMoreElements()) {
      String name = (String) names.nextElement();
      if ("chosen".equals(name)) {
        String[] values = request.getParameterValues(name);    
        for (String value : values) {
          paramsStr += "&" + name + "=" + value;
        }
      }
    }
    return paramsStr;
  }
  
    public static boolean isMobileTest(HttpServletRequest request) {
      if (LoginMgr.isDeveloper(request)) return true; // for testing

      if (HttpUtil.getTarget(request).contains("testMobile.do")) return true; // for testing
        return HttpUtil.getTarget(request).contains("mobile.do"); // for testing
    }
    
    public static boolean isMobile(HttpServletRequest request) {
      if (request == null) AntwebUtil.log("isMobile() request is null");
      //if (AntwebProps.isDevMode()) return true; // for testing
      boolean returnVal = false;
      try { // To handle http://localhost/antweb/citing_antweb.jsp
          String target = HttpUtil.getTarget(request);
          if (target == null) {
            //AntwebUtil.log("isMobile() target is null");
            return false;
          }

          String userAgent = request.getHeader("User-Agent");

          //A.log("isMobile() target:" + target + " userAgent:" + userAgent);      

          if (target.contains("mobile.do")) returnVal = true; // for testing
            // for testing on https://www.antweb.org/mobile.do
      
          //A.log("isMobile() userAgent:" + userAgent + " deviceWidth:" + device-width);
          if (userAgent != null && userAgent.contains("Mobile")) {
            returnVal = true;
          }
      } catch (Exception e) {
        s_log.warn("isMobile() e:" + e);
      }
      return returnVal;
    }

    public static boolean isIphone(HttpServletRequest request) {
      String userAgent = request.getHeader("User-Agent");
        return userAgent != null && userAgent.contains("iPhone");
    }
  
    public static boolean isSecure(HttpServletRequest request) {
        String target = HttpUtil.getTarget(request);
        if ("https://".equals(target.substring(0, 8))) return true;      
        if (!AntwebProps.isDevMode() && target.contains("http://")) s_log.warn("isSecure() http found. Target:" + target);
        return false;
    }
  
    /**
     * this is because those crazy browsers don't send the param
     * name back straight if its an image button
     */
    public static boolean requestParameter(ServletRequest request, String param) {
        Object val = request.getParameter(param);
        if (val != null && !( val.equals("false") ))
            return true;
        return request.getParameter(param + ".x") != null;
    }

  public static boolean redirectPostToGet(HttpServletRequest request
    , HttpServletResponse response
    , String requestedPathWithQuery) 
    throws IOException, MalformedURLException {

      String target = AntwebProps.getDomainApp() + requestedPathWithQuery;
      HttpUtil.sendRedirect(target, request, response);
      return true;
  }  

  public static boolean redirectSecure(HttpServletRequest request
    , HttpServletResponse response
    , String requestedPathWithQuery) 
    throws IOException, MalformedURLException {

    if (!HttpUtil.isSecure(request)) {
      String target = "https://" + request.getServerName() + requestedPathWithQuery;
      HttpUtil.sendRedirect(target, request, response);
      return true;
    } else {
      return false;
    }
  }  

  //   HttpUtil.getRealPath(request);
  public String getRealPath(HttpServletRequest request) {
      return request.getSession().getServletContext().getRealPath("/");
  }

  public static void sendRedirect(String target, HttpServletRequest request, HttpServletResponse response) 
    throws IOException {
      PageTracker.remove(request);  
      response.sendRedirect(target);
  }

    
  public static String redirectCorrectedUrl(HttpServletRequest request  
    , HttpServletResponse response)  
    throws IOException, MalformedURLException { 

    /* Called from BrowseAction in case where rank is null */
    //        //response.sendRedirect(newPath);  // can't. Response committed.

    String requestedPath = HttpUtil.getRequestURL(request) + "?" + request.getQueryString();

    if (requestedPath != null) {
      if (requestedPath.contains("%22")) {
        String newPath = requestedPath.replaceAll("%22", "");
        String message = "redirectCorrectedURL() requestedPathWithQUery:" + requestedPath + " has \".  Should be:" + newPath
          + ".  referrer:" + request.getHeader("referer") + " user-agent:" + request.getHeader("user-agent");
        s_log.info(message);
        return newPath;
      }
/*
    s_log.warn("redirectCorrectedUrl() requestUrl:" + requestUrl + " servletPath:" + servletPath + " domainApp:" + AntwebProps.getDomainApp() + " requestedPath:" + requestedPath);
*/
      if (requestedPath.contains("&amp")) {
        String newPath = requestedPath.replaceAll("&amp;", "&");
        String message = "redirectCorrectedUrl() requestedPathWithQUery:" + requestedPath + " has &amp;'s.  Should be:" + newPath
          + ".  referrer:" + request.getHeader("referer") + " user-agent:" + request.getHeader("user-agent");
        s_log.info(message);
        return newPath;
      }
    }
    return null;
  }      
    
  public static String getRequestURL(HttpServletRequest request) {

    StringBuffer requestUrlBuffer = request.getRequestURL();  // This returns NULL sometimes. 
    if (requestUrlBuffer != null) {
        return requestUrlBuffer.toString();
    }

    String requestUrl = AntwebProps.getDomainApp() + request.getServletPath();
    return requestUrl;
  }
      
  public static String getRequestInfo(HttpServletRequest request) {
    String requestInfo = "url:" +  HttpUtil.getTarget(request); // HttpUtil.getRequestURL(request); //request.getRequestURL();
//    requestInfo += " queryString:" + request.getQueryString();
    requestInfo += " shortRequestInfo:" + HttpUtil.getShortRequestInfo(request);
    return requestInfo;
  }

  public static String getShortRequestInfo(HttpServletRequest request) {
    int maxLength = 1800;
    String requestInfo = " referer:" + request.getHeader("referer");
    requestInfo += " user-agent:" + request.getHeader("user-agent"); 
    if (requestInfo.length() > maxLength) requestInfo = requestInfo.substring(0, maxLength);
    return requestInfo;
  }

  public static String getRequestReferer(HttpServletRequest request) {
    int maxLength = 1800;
    String requestInfo = " url:" +  HttpUtil.getTarget(request) + " referer:" + request.getHeader("referer");
    return requestInfo;
  }
    
    public static String getQueryString(HttpServletRequest request) {
      // Returning null indicates an "issue".  Empty is fine.
      // This will not work when called directly from a jsp page.
      String queryString = (String) request.getAttribute("javax.servlet.forward.query_string");

//      A.log("getQueryString() 1 queryString:" + queryString);
      
      if (queryString == null) {
        // using getAttribute allows us to get the orginal url out of the page when a include has taken place.
        queryString = (String) request.getAttribute("javax.servlet.include.query_string");
      }

      //A.log("getQueryString() 2 queryString:" + queryString);

      if (queryString == null) {
        queryString = request.getQueryString();
      }

      //A.log("getQueryString() 3 queryString:" + queryString);

      if("null".equals(queryString)) {
        s_log.debug("getQueryString() queryString:" + queryString);
        queryString = "";
      }    
      
      //A.log("getQueryString() 4 queryString:" + queryString);
      
      if (queryString != null && !"null".equals(queryString)) {
        queryString = "?" + queryString;
      } else {
        queryString = "";
      }

      //if (AntwebProps.isDevMode()) AntwebUtil.logShortStackTrace(7);
      //A.log("getQueryString() 5 queryString:" + queryString);

      return queryString;
    }

    // Like to call this already having a full target (minus a param). Get the queryString from a target via string manipulation.
    public static String getQueryString(String target) {
        String queryString = null;
        //A.log("getQueryString(String) target:" + target);

        int questionMark = target.indexOf("?");
        if (questionMark < 1) return null;
        queryString = target.substring(questionMark + 1);
        s_log.debug("getQueryString(String)"); // queryString:" + queryString);
        return queryString;
    }
    /**/
    public static String getQueryStringNoQuestionMark(String target) {
        return getAfterQuestionMark(target);
    }

    // Original function
    public static String getQueryStringNoQuestionMark(HttpServletRequest request) {
        // Returning null indicates an "issue".  Empty is fine.
        String queryString = HttpUtil.getQueryString(request);
        return getAfterQuestionMark(queryString);
    }

    public static String getAfterQuestionMark(String queryString) {
        String afterQuestionMark = null;
        // Might just be "?..." or could hav a domain and protocol. Careful not to remove subsequent question marks.
        //A.log("getAfterQuestionMark() queryString:" + queryString);

        if (queryString == null)
            return null;

        if (queryString.substring(0, 1).equals("?")) {
            afterQuestionMark = queryString.substring(1);
        } else { // Might be string domain.
            if (queryString.contains(AntwebProps.getDomainApp())) {
                afterQuestionMark = getQueryString(queryString);
            }
        }

        //A.log("getAfterQuestionMark() queryString:" + queryString);
        return afterQuestionMark;
    }
    
    public static String getRequestURI(HttpServletRequest request) {
      // using getAttribute allows us to get the orginal url out of the page when a forward has taken place.
      String requestURI = "" + request.getAttribute("javax.servlet.forward.request_uri");
      
      //A.log("getRequestURI() 1 requestURI:" + requestURI);      
      
      if (requestURI.equals("null")) {
              // using getAttribute allows us to get the orginal url out of the page when a include has taken place.
        requestURI = "" + request.getAttribute("javax.servlet.include.request_uri");
        //A.log("getRequestURI() 2 requestURI:" + requestURI);      
      }

      if (requestURI.equals("null")) {
        requestURI = request.getRequestURI();
        //A.log("getRequestURI() 3 requestURI:" + requestURI);      
      }    

      //A.log("getRequestURI() 4 requestURI:" + requestURI);      

      return requestURI;
    }

    public static String getJspTarget(HttpServletRequest request) {
      return HttpUtil.getRequestURI(request) + HttpUtil.getQueryString(request);
    }
    public static String getFullJspTarget(HttpServletRequest request) {
      return AntwebProps.getDomain() + HttpUtil.getRequestURI(request) + HttpUtil.getQueryString(request);
    }
        
    public static String getTarget(HttpServletRequest request) {
        if (request == null) return "";
      String target = "";
      String queryString = HttpUtil.getQueryString(request);
      if (queryString == null) {
        if (AntwebProps.isDevMode()) {
           s_log.warn("getTarget() queryString is null");
           //AntwebUtil.logStackTrace();
        }
        return null;
      }
      String requestURI = HttpUtil.getRequestURI(request);
      
      if (requestURI.contains("academyHeader.jsp")) return null;
      
      if ((requestURI == null) || (requestURI.equals("null"))) {
        //A.log("devMode Note: no requestURI");
        return null;
      }
      
      target = (new Utility()).getDomain() + requestURI;
      if (queryString != null)
        target += queryString; 

      if (AntwebProps.isDevMode()) { 
        //s_log.warn("getTarget() target:" + target + " queryString:" + queryString + " requestURI:" + requestURI);      
        //AntwebUtil.logStackTrace();  // This is called on dev machines from academyHeader.jsp, and navCombo.jsp
      }
      return target;
    }
    
    public static String getReferrerUrl(HttpServletRequest request) {
      if (request == null) return null;
      String target = request.getHeader("referer");  //HttpUtil.getTarget(request);
      String domainApp = AntwebProps.getDomainApp();
      //s_log.warn("target:" + target + " domainApp:" + domainApp);
      if (target != null && target.contains(domainApp)) {
        target = target.substring(domainApp.length());
      }
      return target;
    }

    /*
    To do. It seems the code below is faulty.

        public static String getTargetMinusParam(String target, String param) {
          int i = target.indexOf("&" + param);
          String newTarget = target;
          while (i > 0) {
            newTarget = target.substring(0, i);
            i = newTarget.indexOf("&" + param);
          }
          return newTarget;
        }

       have a removeParam method. Get called from both methods below.
     */


    // These are not (yet) designed to handle if the parameter immediately follows the ?
    public static String getTargetMinusParam(HttpServletRequest request, String param) {
        String target = HttpUtil.getTarget(request);
        return getTargetMinusParam(target, param);
    }
    public static String getTargetMinusParams(HttpServletRequest request, String param1, String param2) {
        String target = HttpUtil.getTarget(request);
        target = getTargetMinusParams(target, param1, param2);
        return target;
    }
    public static String getTargetMinusParams(String target, String param1, String param2) {
        target = HttpUtil.getTargetMinusParam(target, param1);
        target = HttpUtil.getTargetMinusParam(target, param2);
        return target;
    }
    // will remove all instances.
    public static String getTargetMinusParam(String target, String param) {
        int i1 = target.indexOf("?" + param);
        boolean isFirstParam = (i1 > 0);
        if (!isFirstParam) i1 = target.indexOf("&" + param);
        int j1 = target.indexOf("&", i1 + 1);
        String newTarget = target;
        String lastTarget = target;
        while (i1 > 0) {
            newTarget = newTarget.substring(0, i1);
            if (isFirstParam) {
                if (j1 > i1) newTarget += "?" + lastTarget.substring(j1 + 1);
            } else {
                if (j1 > i1) newTarget += lastTarget.substring(j1);
            }
            i1 = newTarget.indexOf("&" + param);
            j1 = newTarget.indexOf("&" + param, i1 + 1);
        }
        return newTarget;
    }
    // newParam should be of the format: &param=value (either works).
    public static String getTargetReplaceParam(HttpServletRequest request, String oldParam, String newParam) {
      if (oldParam == null || newParam == null || !newParam.contains("=")) return null;
      String target = HttpUtil.getTarget(request);
      target = HttpUtil.getTargetReplaceParam(target, oldParam, newParam);
      return target;
    }
    public static String getTargetReplaceParam(String target, String oldParam, String newParam) {
        if (oldParam == null || newParam == null || !newParam.contains("=")) return null;
        if (!newParam.contains("&")) newParam = "&" + newParam;
        target = HttpUtil.getTargetMinusParam(target, oldParam);
        if (newParam != null && !"".equals(newParam)) {
            target += newParam;
        }
        return target;
    }


    public static int MILLIS = 1000;
    public static int SECS = 60;
    public static int MAX_REQUEST_TIME = MILLIS * 20;
/*
    public static String finish(java.util.Date startTime) {
      String execTime = "";
      long millis = AntwebUtil.millisSince(startTime);
      if (millis > 2000) {
        execTime = AntwebUtil.secsSince(startTime) + " secs";
      } else {
        execTime = millis + " millis";      
      }
      String message = (new Date()).toString() + " time:" + execTime;
      if (AntwebProps.isDevMode()) {
        //s_log.warn(message);
        MAX_REQUEST_TIME = 1;
      }
      if (millis > MAX_REQUEST_TIME) LogMgr.appendDataLog("longRequest.log", message);
      return execTime; 
    }
*/
    public static String getExecTime(java.util.Date startTime) {
        String execTime = "";
        long millis = AntwebUtil.millisSince(startTime);
        if (millis > 2000) {
            execTime = AntwebUtil.secsSince(startTime) + " secs";
        } else {
            execTime = millis + " millis";
        }
        return execTime;
    }

    public static String finish(HttpServletRequest request, java.util.Date startTime) {
      return getExecTime(startTime);
    }


    //  Add the following code to JSPs...
    //    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
    public static boolean isStaticCallCheck(HttpServletRequest request, javax.servlet.jsp.JspWriter out) {
      if (HttpUtil.isStaticCall(request)) {
        try {
          out.println("Invalid URL.  Direct JSP calls unsupported.");
          s_log.debug("isStaticCallCheck() target:" + HttpUtil.getTarget(request));
          //AntwebUtil.logStackTrace();
        } catch (IOException e) {
          s_log.error("isStaticCallCheck() e:" + e);
        }
        return true;
      }
      return false;
    }
    
    // jspAccess indirect
    public static boolean isStaticCall(HttpServletRequest request) {
    // isStaticCall directAccess not allowed
      /* To be used on JSP pages, to prevent JPS access, as such:
  
           if (HttpUtil.isStaticCall(request)) return;
  
         to handle cases where jsps are called directly without beans, properties, query strings, etc...
         Unceremonially returns a white page, but these requests are erroneous anyway.
      */
      
      //A.log("isStaticCall() 1 post:" + HttpUtil.isPost(request));      
      if (HttpUtil.isPost(request)) return false;
      
      String requestedPath = HttpUtil.getRequestURI(request);
      //(String) request.getAttribute("javax.servlet.forward.request_uri");

      //A.log("isStaticCall() 2 requestedPath:" + requestedPath);
      
      if ((requestedPath != null) && (requestedPath.contains(".jsp"))) {
 
        // May get called twice.  Once for page and once for -body.
        //AntwebUtil.logStackTrace();

        // This check added Oct 27, 2014.  Would have always returned true

          return !isStaticCallException(request);

        //s_log.warn("isStaticCall() requestInfo:" + HttpUtil.getRequestInfo(request));
        // This may happen from base= in taxonPage.jsp or specimen.jsp.  Not a problem.  Still works.
      }

      if (request.getQueryString() == null) {
        // s_log.info("isStaticCall()  requestUrl:" + request.getRequestURL()); 
        // This check added Oct 27, 2014.  Would have always returned true
        String target = HttpUtil.getTarget(request);
          //A.log("isStaticCall()  target:" + target);
          //if (isStaticCallException(request)) return false;
          return target != null && target.contains(".jsp");
      }
      return false;
  }

  private static boolean isStaticCallException(HttpServletRequest request) {
      String target = HttpUtil.getTarget(request);
      if (target == null) return false;

      // Here we make exceptions...
      // Change false to true to find out where the offline functionality is called.
      // See advancedSearchResults-body.jsp:90
      if (false && AntwebProps.isDevMode()) {
        if (HttpUtil.isOffline()) {
          AntwebUtil.logStackTrace();
        }
      }
      /*
      if (target.contains("advancedSearchResults.jsp")) {  // # is truncated in target for some reason.
        A.log("isStaticCall() exception made for:" + target);
        return true;
      }          
      */

      // This is allowed because Page.do will forward to a jsp, with no parameters.
      // This means we are safe from fishing and parameter attacks
      return target.contains("speciesList") && "".equals(HttpUtil.getQueryString(request));
      //A.log("isStaticCall() exception made ? for:" + target);
  }

  public static void fetchAndWrite(String url, HttpServletResponse response) {  
        // See PageAction.java.
        //s_log.warn("execute() url:" + url);
        try { 
          String output = HttpUtil.fetchUrl(url);
          HttpUtil.write(output, response);
        } catch (IOException e) {
          String message = "url:" + url + " e:" + e;
          s_log.warn("fetchAndWrite() " + message);
          AdminAlertMgr.log(message);
        }
  }
  
  public static void write(String output, HttpServletResponse response) throws IOException {
        response.setContentType("text/html");
        Writer writer = response.getWriter();
        writer.write(output);
  }

  public static boolean s_beenReported = false;


  public static String getUrlIso(String theUrl) 
    throws IOException {
    return HttpUtil.getUrl(theUrl, "ISO-8859-1");
  }

    public static boolean urlExists(String url) {
      String content = null;
      try {
          // This will turn spaces into +
          url = URIUtil.encodeQuery(url);

          content = fetchUrl(url);
      } catch (IOException e) {
        s_log.debug("urlExists() e:" + e + " url:" + url);
        return false;
      }
        return content != null;
        //A.log("urlExists() content:" + content);
    }

  
  public static String getUrl(String theUrl) 
    throws IOException {
    return HttpUtil.getUrl(theUrl, "UTF-8");
  }

    public static String getJson(String url) {

		String json = null;
		try {		
		  //json = HttpUtil.getUrl(url);
          json = HttpUtil.getUtf8Url(url);
 		  //A.log("getJson() fetched:" + url);
        } catch (IOException e) {
          s_log.warn("getJson() e:" + e + " url:" + url);
          return null;
        }
        return json;    
    }

  public static String getUrl(String theUrl, String encode)   
    throws IOException {  
    
      String output = "";
      
      LogMgr.appendLog("getUrl.txt", DateUtil.getFormatDateTimeStr(new java.util.Date()) + " " + theUrl);

      if ((false) && (AntwebProps.isDevMode())) {
        s_log.warn("getUrl() the url:" + theUrl);
        return "";
      }
      
      if (AntwebProps.isStageMode()) {
        if (!s_beenReported) {
          if (theUrl.contains("https:")) {
            s_log.error("getUrl() contains https.  May not work on stage.  url:" + theUrl);
            s_beenReported = true;         
          }
        }
      }
      
      StringBuffer strVal = new StringBuffer();
      URL url = null;

      //if (AntwebProps.isDevMode()) theUrl = "http://localhost/antweb/specimen.do?name=CASENT0915637";
	  try {
		//A.log("getUrl:" + theUrl);
		//InputStream is = new FileInputStream(theUrl);
		String UTF8 = "utf8";
		int BUFFER_SIZE = 8192;
		url = new URL(theUrl) ;
				
		//BufferedReader br = new BufferedReader(new InputStreamReader(is, UTF8), BUFFER_SIZE);
		BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream(), encode));  //"UTF-8" ?

		String str = null;
		while ((str = in.readLine()) != null) {
			//A.log("getUrl() str:" + str);
			strVal.append(str);
			//output += str;
		}   
      } catch (Exception e) {
		String message = "e:" + e.toString();
		//AntwebUtil.logShortStackTrace();
		//if (AntwebProps.isDevMode()) 
		message += " url:" + url;
		s_log.error("getUrl() " + message);
	  }
	  output = strVal.toString();
      return output;
  }

  // fetchUrl is like getUrl but better because it doesn't trap and log exceptions.
  // SpecimenFetch uses it.
  public static String fetchUrl(String theUrl)   
    throws IOException {  

      String encode = "UTF-8";
    
      String output = "";
      
      LogMgr.appendLog("getUrl.txt", DateUtil.getFormatDateTimeStr(new java.util.Date()) + " " + theUrl);

      if ((false) && (AntwebProps.isDevMode())) {
        s_log.warn("getUrl() the url:" + theUrl);
        return "";
      }
      
      if (AntwebProps.isStageMode()) {
        if (!s_beenReported) {
          if (theUrl.contains("https:")) {
            s_log.error("getUrl() contains https.  May not work on stage.  url:" + theUrl);
            s_beenReported = true;         
          }
        }
      }
      
      StringBuffer strVal = new StringBuffer();
      URL url = null;

      //if (AntwebProps.isDevMode()) theUrl = "http://localhost/antweb/specimen.do?name=CASENT0915637";

      //A.log("getUrl:" + theUrl);
      //InputStream is = new FileInputStream(theUrl);
      String UTF8 = "utf8";
      int BUFFER_SIZE = 8192;
      url = new URL(theUrl) ;
              
      //BufferedReader br = new BufferedReader(new InputStreamReader(is, UTF8), BUFFER_SIZE);
      BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream(), encode));  //"UTF-8" ?

      String str = null;
      while ((str = in.readLine()) != null) {
          //A.log("getUrl() str:" + str);
          strVal.append(str);
          //output += str;
      }   

	  output = strVal.toString();
      return output;
  }

  public static String getUrlOld(String theUrl)
    throws AntwebException {
      URL url = null;
      StringBuffer strVal = new StringBuffer();
      String output = null;
      try {
        url = new URL(theUrl) ;
      
        InputStream is = url.openConnection().getInputStream();

        int c = 0;      
        //A.log("getUrl() url:" + theUrl);
        while ((c = is.read()) != -1) {
          strVal.append((char) c);
        }
      } catch (Exception e) {
        s_log.warn("getUrlOld() url:" + url + " e:" + e);
        throw new AntwebException(e.toString());
      }       
      output = strVal.toString();
      return output;
  }

  public static void hitUrl(String url)   
    throws IOException {  

    new HitUrlThread(url).start();
    //A.log("hitUrl() done");
  }

/*     
	// HTTP POST request
	public static void sendPost() {
	 //throws Exception {
      try {
//		String url = "https://selfsolve.apple.com/wcResults.do";
        String url = "https://www.mapdevelopers.com/geocode_bounding_box.php";
 		URL obj = new URL(url);
		HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

		//add reuqest header
		con.setRequestMethod("POST");
		con.setRequestProperty("User-Agent", USER_AGENT);
		con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

//		String urlParameters = "sn=C02G8416DRJM&cn=&locale=&caller=&num=12345";
        String urlParameters = "address=Yolo, California&submit=Go!";
		// Send post request
		con.setDoOutput(true);
		DataOutputStream wr = new DataOutputStream(con.getOutputStream());
		wr.writeBytes(urlParameters);
		wr.flush();
		wr.close();

		int responseCode = con.getResponseCode();
		s_log.warn("\nSending 'POST' request to URL : " + url);
		s_log.warn("Post parameters : " + urlParameters);
		s_log.warn("Response Code : " + responseCode);

		BufferedReader in = new BufferedReader(
		        new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
        String theResponse = response.toString();
		s_log.warn("sendPost() contains Yolo:" + theResponse.contains("Yolo") + " response:" + theResponse);
      } catch (Exception e) {
        s_log.warn("sendPost()");
      }
	}
*/

  public static String getUtf8Url(String theUrl)
    throws IOException {  
      return getUtf8Url(theUrl, null);  
  }
  
  public static String getUtf8Url(String theUrl, String delimit) 
    throws IOException {  
      LogMgr.appendLog("getUrl.txt", DateUtil.getFormatDateTimeStr(new java.util.Date()) + " " + theUrl);

      if ((false) && (AntwebProps.isDevMode())) {
        s_log.warn("getUtf8Url() the url:" + theUrl);
        return "";
      }
      
      if (AntwebProps.isStageMode()) if (theUrl.contains("https:")) s_log.error("getUtf8UrlUrl() contains https.  May not work on stage.  url:" + theUrl);
      
      StringBuffer strVal = new StringBuffer();
      URL url = new URL(theUrl) ;
      
      java.net.URLConnection urlConn = url.openConnection();
      urlConn.setRequestProperty("Accept-Charset", "UTF-8");

      try {
        BufferedReader input = new BufferedReader(
            new InputStreamReader(urlConn.getInputStream(), "UTF-8")); 
        StringBuilder strB = new StringBuilder();
        String str;
        
        //int i = 0;
        while (null != (str = input.readLine())) {
          //if (AntwebProps.isDevMode()) if (str.contains("land Islands")) { // || i < 5) s_log.warn("getUtf8Url() str:" + str);
          strB.append(str); 
          if (delimit != null) strB.append(delimit); 
        }
        input.close();
      
        return strB.toString();
      } catch (IOException e) {
        s_log.error("getUtf8Url() e:" + e);
      }      
      return null;    
    }

  public static ArrayList<String> getUtf8UrlLines(String theUrl) 
    throws IOException {  
      ArrayList<String> lines = new ArrayList<>();

      StringBuffer strVal = new StringBuffer();
      URL url = new URL(theUrl) ;
      
      java.net.URLConnection urlConn = url.openConnection();
      urlConn.setRequestProperty("Accept-Charset", "UTF-8");

      try {
        BufferedReader input = new BufferedReader(
            new InputStreamReader(urlConn.getInputStream(), "UTF-8")); 
        String str;
        
        //int i = 0;
        while (null != (str = input.readLine())) {
          //if (AntwebProps.isDevMode()) if (str.contains("land Islands")) { // || i < 5) s_log.warn("getUtf8Url() str:" + str);
          lines.add(str); 
        }
        input.close();
      
        return lines;
      } catch (IOException e) {
        s_log.error("getUtf8UrlLines() e:" + e);
      }      
      return null;    
    }

	public static boolean writeUrlContents(String theUrl, String fileName) {   //String[] args) {

		URL url = null;
        //A.log("writeUrlContents() IN url:" + theUrl + " toFile:" + fileName);
 
		try {
			// get URL content
			url = new URL(theUrl);
			URLConnection conn = url.openConnection();
 
			// open the stream and put it into BufferedReader
			BufferedReader br = new BufferedReader(
                               new InputStreamReader(conn.getInputStream())); 
			String inputLine;
 
			//save to this filename
			//String fileName = "/users/mkyong/test.html";
			File file = new File(fileName);
 
			if (!file.exists()) {
				file.createNewFile();
			}
 
			//use FileWriter to write file
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
 
            int count = 0;
			while ((inputLine = br.readLine()) != null) {
                ++count;
                if ((count % 10000 == 0)) s_log.debug("writeUrlContents() count" + count);
				bw.write(inputLine + "\n");
			}
 
			bw.close();
			br.close();
 
            s_log.debug("writeUrlContents() url:" + theUrl + " toFile:" + fileName);
            return true;
 
		} catch (IOException e) {
			e.printStackTrace();
		}
        return false;         
	}  

    private static int MAX_ITERATION = 60;  // This should cover all adm1
      // This should be multiplied by 27 to have the actual count.
    private static String[] uniqueColors = {"#0c6197", "#4daa4b", "#90c469", "#daca61", "#e4a14b", "#e98125", "#cb2121", "#830909", "#923e99", "#ae83d5", "#bf273e", "#ce2aeb", "#bca44a", "#618d1b", "#1ee67b", "#b0ec44", "#a4a0c9", "#322849", "#86f71a", "#d1c87f", "#7d9058", "#44b9b0", "#7c37c0", "#cc9fb1", "#e65414", "#8b6834", "#248838"};
    private static String[] colors = null;    
    public static String[] getColors() {
      if (colors != null) return colors;
      ArrayList<String> colorList = new ArrayList<>();
      for (int i=0 ; i <=MAX_ITERATION ; ++i) {
          //A.log("getColors() i:" + i + " j:" + j + " color:" + uniqueColors[j]);
          colorList.addAll(Arrays.asList(uniqueColors));
      }      
      colors = colorList.toArray(new String[colorList.size()]);
      //A.log("getColors:" + colorList);
      return colors;
    }
    
    public static String getJsonElement(int i, String label, int count, String chartColor) {
        String json = "";
        //String[] colors = HttpUtil.getColors();
        
        json += "{"
               + "\"label\": \"" + label + "\","
               + "\"value\": " + count + ","
               + "\"color\": \"" + chartColor + "\""   // was colors[i]
               + "}"
             ;
        return json;
    }    
    
    public static String getFacet(HttpServletRequest request) {
	  String queryString = HttpUtil.getQueryString(request);
	  String requestURI = request.getRequestURI();
      String slash = "/";
	  String facet = null;
  
	  if ( queryString.contains("pr=b")
		|| requestURI.contains("showBrowse.jsp")
		|| requestURI.contains("browse.do")
		 ) {
		  facet = slash + "browse.do";
	  } else if (
		requestURI.contains("/region.jsp")
	  ) {
        facet = slash + "region.do";	  
	  } else if (
		requestURI.contains("bioregion.jsp")
	  ) {
        facet = slash + "bioregion.do";	  
	  } else if (
		   queryString.contains("pr=d")
		|| requestURI.contains("mapComparison.jsp")
		|| requestURI.contains("dynamicMap.jsp")
		|| requestURI.contains("taxonPage.jsp")
		|| requestURI.contains("specimen.jsp")
		|| requestURI.contains("homonymPage.jsp")
		|| requestURI.contains("description.do")
		 ) {
		  facet = slash + "description.do";
	  } else if (
		   queryString.contains("pr=i")
		|| requestURI.contains("imagePage.jsp")
		|| requestURI.contains("oneView.jsp")
		|| requestURI.contains("bigPicture.jsp")
		|| requestURI.contains("specimenImages.jsp")
		|| requestURI.contains("images.do")
		 ) {
		  facet = slash + "images.do";
	  } else {
		s_log.debug("getFacet() not found.  requestURI:" + requestURI + " queryString:" + queryString);
	  }
	  return facet;  
    }
    
    public static String removeOverview(String url) {
      if (url == null) return null;
      if (!url.contains("description.do") 
        && !url.contains("browse.do")
        && !url.contains("images.do")
        && !url.contains("specimenImages.do")
        && !url.contains("specimen.do")
        && !url.contains("bigPicture.do")
        ) {
        url = HttpUtil.removeParam(url, "name");
      }
      url = HttpUtil.removeParam(url, "projectName");
      url = HttpUtil.removeParam(url, "project");
      url = HttpUtil.removeParam(url, "museumCode");
      url = HttpUtil.removeParam(url, "bioregionName");
      url = HttpUtil.removeParam(url, "regionName");
      url = HttpUtil.removeParam(url, "subregionName");
      url = HttpUtil.removeParam(url, "countryName");
      url = HttpUtil.removeParam(url, "adm1Name");
      //A.log("HttpUtil.removeOverview url:" + url);        
      return url;
    }

    // The similar methods above might be better. For instance: getTargetMinusParam()
    public static String removeParam(String url, String paramName) {
      String newUrl = url;
      String param = "?" + paramName + "=";
      int overviewIndex = url.indexOf(param);

      //A.log("removeParam() url:" + url + " overviewIndex:" + overviewIndex); 

      if (overviewIndex < 0) {
        param = "&" + paramName + "=";
        overviewIndex = url.indexOf(param);
      }
      if (overviewIndex < 0) {
          return url;
      }
      int endOverviewParam = 0;
      // Then find the end of the overview param which is either & or end of line.
      if (overviewIndex > 0) endOverviewParam = url.indexOf("&", overviewIndex + 1);
      if (endOverviewParam < 0) {
        newUrl = url.substring(0, overviewIndex);
      } else {
        int overviewParamLength = endOverviewParam - overviewIndex;
        newUrl = url.substring(0, overviewIndex) + url.substring(overviewIndex + overviewParamLength);
      }

      if (!newUrl.contains("?")) {
        //A.log("removeParam() before newUrl:" + newUrl); 
        newUrl = AntFormatter.replaceOne(newUrl, "&", "?");
        //A.log("removeParam() i:" + overviewIndex + " e:" + endOverviewParam + " newUrl:" + newUrl); 
      }
      return newUrl;
    }

    public static String trimDomainApp(String url) {
      //A.log("trimDomainApp() url:" + url + " i:" + url.indexOf("localhost/antweb/"));
      int startI = 0;
      if (url.contains("localhost/antweb/")) url = url.substring(url.indexOf("localhost/antweb/") + 17);
      if (url.contains("antweb-dev/")) url = url.substring(url.indexOf("antweb-dev/") + 11);
      if (url.contains("antweb-stg/")) url = url.substring(url.indexOf("antweb-stg/") + 11);
      if (url.contains("antweb.org/")) url = url.substring(url.indexOf("antweb.org/") + 11);

      //A.log("trimDomainApp() after url:" + url);

      return url;
    }
    
    public static String verbatimify(String content) {
      if (content != null) {
        content.replaceAll("<", "&lt;");
        content.replaceAll(">", "&gt;");
      }
      return content;
    }
    
}



