package org.calacademy.antweb.util;

import java.util.*;

import org.calacademy.antweb.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.apache.struts.action.*;

public abstract class AntwebProps {

  private static final Log s_log = LogFactory.getLog(AntwebProps.class);
    
    public static String guiDefaultContent = "Add your content here";    
    
    private static ResourceBundle s_appResources = null;
    private static ResourceBundle s_antwebResources = null;
    
    // This is the only place we should call ResourceBundle.getBundle()
    public static ResourceBundle getAppResources() {
      // This should be deprecated, as misleading.
      return AntwebProps.getAppResourceBundle();
    }
    
    private static ResourceBundle getAppResourceBundle() {
      if (s_appResources == null) {
        try {
          s_appResources =  ResourceBundle.getBundle("ApplicationResources");
        } catch (java.util.MissingResourceException e) {
          s_log.warn("getAppResourceBundle() e:" + e);       
        }
      }
      return s_appResources;
    }  
    private static ResourceBundle getAntwebResources() {
      // This should be deprecated, as misleading.
      return AntwebProps.getAntwebResourceBundle();
    }
    
    private static ResourceBundle getAntwebResourceBundle() {
      if (s_antwebResources == null) {
        s_antwebResources =  ResourceBundle.getBundle("AntwebResources");
      }
      // Can't do A.log() here.
      //s_log.warn("getAntwebResourceBundle() s_antwebResources:" + s_antwebResources);
      return s_antwebResources;
    }  
        	
	public static Boolean isProp(String isProp) {
		String prop = AntwebProps.getProp(isProp);
        if ("true".equals(prop)) {
          return true;
        } else {
          return false;
        }
	}
	
	public static String getAntwebVersion() {
	  return AntwebUtil.getReleaseNum();
	}

	public static String getProp(String prop) {
     	ResourceBundle resources = AntwebProps.getAppResources();
	    if (resources == null) {
	      s_log.warn("getProp() ApplicationResources.properties not found in WEB-INF/classes.");
          return null;
	    }
        String value = getProp(prop, "appResources", resources); // default to the specific platform
        if (value != null) {
           //s_log.warn("getProp() 1 AppResource " + prop + "=" + value + "-");
           return value;
        }
        value = getProp(prop, "antResources", AntwebProps.getAntwebResources());  // fall back on global defaults.
        //s_log.warn("getProp() 3 AntwebResources " + prop + "=" + value);
        return value;
	}

	private static String getProp(String prop, String resource, ResourceBundle resources) {
      String propVal = null;
      try {
          propVal = resources.getString(prop);
	    } catch (MissingResourceException e) {
          if ("antResources".equals(resource)) {
              s_log.warn("getProp(" + prop + ", " + resource + ", " + resources + ") not found in either resource.  e:" + e );
          }
	    }
      return propVal;
	}
	
    /* If a property is not found in the ApplicationResources.properties file it is returned as "" */

    public static String getYuiVersion() {
        //if (AntwebProps.isDevMode()) return "3.5.1";
        //if (!AntwebProps.isDevMode()) return "2.7.0";
        return "2.9.0";    
    }

    public static String getAntwebDir() {
        if (AntwebProps.isDevMode()) {
          // This should be: return AntwebProps.getProp("site.docroot"); with in AppResMarkAntweb.properties
          // This: #site.antwebDir=/Users/remarq/IdealProject/antweb/
          // Or a standard url like /usr/local/antwebDeploy -> 
          return "/Users/remarq/IdeaProjects/antweb/";
        } else {
          return "/antweb/antweb_deploy/";    
        }
    }

	public static String getDocRoot() {
	    // always:  /usr/local/antweb/ points to either /data/antweb (on server) or /usr/local/tomcat/webapps/antweb on dev.
		//return AntwebProps.getProp("site.docroot");
        return "/usr/local/antweb/";
	}

    public static String getTomcatDir() {
        return "/usr/local/tomcat/";
    }

	public static String getImagesDir() {
	    return getDocRoot() + "images/";
	}

	public static String getWebDir() {
		return AntwebProps.getDocRoot() + "web/";
	}

	public static String getPlaziDir() {
	
	    if (false && isDevMode()) {
	      return "/Users/remarq/dev/calAcademy/plazi/";
	    }
	
		return AntwebProps.getDocRoot() + "plazi/";
	}

	public static String getInputFileHome() {
	    if (AntwebProps.isDevMode()) {
	        return "/Users/mark/dev/calAcademy/workingdir/";
        } else {
	        return "/antweb/workingdir/";
	    }
	}

	public static String getGoogleMapKey() {
	    if (AntwebProps.isGoogleMapsV3()) {
	        return AntwebProps.getProp("googleMapsV3.key");
        } else {
	        return AntwebProps.getProp("googlemap.key");
        }
	}

	public static Boolean isGoogleMapsV3() {
		String v3 = AntwebProps.getProp("isGoogleMapsV3");
		//s_log.warn("isGoogleMapsV3() v3:" + v3);
        if (v3.equals("true")) {
          return true;
        } else {
          return false;
        }
	}

	public static int rev = 0;

	// Get Random Number
	public static int getRev() {
	    if (rev == 0) {
	      rev = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
	    }
	    return rev;
	}

    private static String s_imgDomainApp = null;
    public static String getImgDomainApp() {
        if (s_imgDomainApp != null) return s_imgDomainApp;
        /* Use this method to get something like: http://localhost/antweb or http://antweb.org   Not end with slash.  */
        String imgDomainApp = AntwebProps.getProp("site.imgDomainApp");    
        if (imgDomainApp == null || "".equals(imgDomainApp)) { 
          // if one is not specified in the property file just use the app
          imgDomainApp = AntwebProps.getApp();
        } else {
          imgDomainApp = AntwebProps.getProtocol() + "://" + imgDomainApp; //imgDomainApp; // "http://" + 
        }
        s_imgDomainApp = imgDomainApp;
        //s_log.warn("getImgDomainApp() imgDomain:" + imgDomainApp);        
        return imgDomainApp;
    }

    public static String getApp() {
        /* Use this method to get something like: /antweb or the empty string  */
        String app = "";
        if (AntwebProps.isDevMode()) {
          app = AntwebProps.getProp("site.app");    
          if ((app != null) && (!app.equals(""))) {
            app = "/" + app;
          }
        }
        return app;
    }

    public static boolean isProtocolSecure() {
      if ("https".equals(getProtocol())) return true;
      return false;
    }
    
    public static String getProtocol() {
        // notice that this does not contain a follow / as does getSiteURL.  
        String protocol = AntwebProps.getProp("site.protocol");
        //A.log("getProtocol() protocol:"+ protocol);
        return protocol;
    }
    
    // This is used for self reflexive requests. When the server calls itself,
    //   possible it can't through ssh. Use http, on localhost, with app (if relevant).
    public static String getThisDomainApp() {
      //String thisDomainApp = "https://www.antweb.org" + AntwebProps.getApp();
      //return thisDomainApp;
      // This was useful when the live server needed to call itself via http.
      return getDomainApp();
    }
       
    public static String getDomain() {
        // notice that this does not contain a follow / as does getSiteURL.  
        // This is called from AntwebUtil.getTarget()
        String protocol = AntwebProps.getProtocol();
        String domain = AntwebProps.getProp("site.domain");
        return protocol + "://" + domain;
    }
    
    public static String s_domainApp = null;
    public static String s_secureDomainApp = null;

    public static String getInsecureDomainApp() {
        String domain = AntwebProps.getProp("site.domain");
        String domainApp =  "http://" + domain;
        String app = AntwebProps.getProp("site.app");    
        if ((app != null) && (!app.equals(""))) {
          domainApp += "/" + app;
        }
        A.log("getDomainApp() domainApp:" + domainApp);        
        return domainApp;
    }
        
    public static String getDomainApp() {
        /** notice that this does not contain a follow / as does getSiteURL.   This 
            should always be used instead of getSiteURL()
            This will return something like: 
                 http://www.antweb.org  
              of http://localhost/antweb
              or http://www.antweb.org/antweb_test
              or http://10.2.22.106
        */

        //if (true) s_log.warn("getDomainApp()");        
        //if (true) return "http://localhost/antweb";
        
        if (s_domainApp != null) return s_domainApp;
        
        String domain = AntwebProps.getDomain();    
        String app = AntwebProps.getProp("site.app");    
        String domainApp = domain;
        if ((app != null) && (!app.equals(""))) {
          domainApp += "/" + app;
        }
        A.log("getDomainApp() domainApp:" + domainApp);        

        s_domainApp = domainApp;
        return domainApp;
    }

    public static String getSecureDomainApp() {
        if (s_secureDomainApp != null) return s_secureDomainApp;
        
        String port = AntwebProps.getProp("site.securePort");    
        if (port == null) {
           s_secureDomainApp = AntwebProps.getDomainApp();
           return s_secureDomainApp;
        }
        //} catch (Exception e) {
        //  s_log.warn("getSecureDomainApp() No AppResources site.securePort= defined.  App as configured will not use https.");
        //  s_secureDomainApp = AntwebProps.getDomainApp();
        //  return s_secureDomainApp;
          // no action required
          // If there is no site.securePort parameter, indicates that we should not use http.
          // If it is specified but with no value, use https on default port
        //}

        String domain = AntwebProps.getDomain();    
        if ((domain != null) && domain.contains("http://")) {
          domain = "https://" + domain.substring(7);
        }

        if ((port != null) && (!"".equals(port))) {
          port = port.trim();
          //s_log.warn("getSecureDomainApp() port:" + port + "-");
          domain += ":" + port; // "8443";  // This port should probably be a configured parameter
        }

        String app = AntwebProps.getProp("site.app");    
        String domainApp = domain;
        if ((app != null) && (!app.equals(""))) {
          domainApp += "/" + app;
        }
        
        
        String protocol = AntwebProps.getProp("site.protocol");    
        //if ((protocol != null) && (!protocol.equals(""))) {
        //  domainApp = protocol + "://" + domainApp;        
        //}

        //s_log.warn("getSecureDomainApp() domain:" + domain + " protocol:" + protocol);
        
        if (AntwebProps.isDevMode()) {
          s_log.warn("getSecureDomainApp() secureDomainApp:" + domainApp);        
          //AntwebUtil.logStackTrace();
        }

        s_secureDomainApp = domainApp;
        return domainApp;    
    } 
        
        
	public static String getGoogleEarthURI() {
	    if (true) return "googleEarth.do";
	
	    String googleEarthURI = AntwebProps.getProp("googleEarthURI");
        if ((googleEarthURI == null) || (googleEarthURI.equals(""))) googleEarthURI = "googleEarth/";
        return googleEarthURI;
	}
		
	public static Boolean isLocal() {
		String localStr = AntwebProps.getProp("isLocal");
        if (localStr != null && localStr.equals("true")) {
          return true;
        } else {
          return false;
        }
	}
		
	public static Boolean isDevMode() {
	  return AntwebProps.getIsDevMode();
	}

	public static Boolean getIsDevMode() {
		String devModeStr = AntwebProps.getProp("isDevMode");
        if (devModeStr.equals("true")) {
          return true;
        } else {
          return false;
        }
	}

	public static Boolean isStageMode() {
	  return AntwebProps.getIsStageMode();
	}

	public static Boolean getIsStageMode() {
		String stageModeStr = AntwebProps.getProp("isStageMode");
        if (stageModeStr.equals("true")) {
          return true;
        } else {
          return false;
        }
	}
	
	public static Boolean isDevOrStageMode() {
	  return isDevMode() || isStageMode();
	}
	
	public static Boolean isLiveMode() {
	  return !AntwebProps.isDevOrStageMode();
	}

	public static String report() {
	  String report = "docRoot:" + getDocRoot() // + " inputFileHome:" + getInputFileHome()
	    + " googleKey:" + AntwebProps.getGoogleMapKey() + " domainApp:" + getDomainApp() 
	    + " devMode:" + getIsDevMode();
	  return report;
	}

	public static String htmlReport() {
	  String report = 
	      " <br>&nbsp;&nbsp;&nbsp;<b>DocRoot:</b> " + getDocRoot() 
	    // + " <br>&nbsp;&nbsp;&nbsp;<b>InputFileHome:</b> " + getInputFileHome()
        + " <br>&nbsp;&nbsp;&nbsp;<b>ImagesDir:</b> " + getImagesDir()
	    + " <br>&nbsp;&nbsp;&nbsp;<b>WebDir:</b> " + getWebDir()
	    + " <br>&nbsp;&nbsp;&nbsp;<b>googleKey:</b> " + getGoogleMapKey()	
	    ; 
	    
	  return report;
	}	
	
	public static String getTechAdminContact() {
	  return "mark@inforaction.com";
	}
}


