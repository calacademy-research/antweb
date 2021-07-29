package org.calacademy.antweb.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public abstract class AntwebProps {

  private static final Log s_log = LogFactory.getLog(AntwebProps.class);
    
    public static String guiDefaultContent = "Add your content here";    
    
    private static boolean s_loadedResources = false;
    private static ResourceBundle s_appResources = null;
    private static ResourceBundle s_antwebResources = null;
    private static ResourceBundle s_platformResources = null;

    public static boolean loadResources() {
      //s_log.warn("loadResources()");
      
      if (getAppResources() == null || getAntwebResources() == null || getPlatformResources() == null)
        return false;
      return true;
    }

    private static ResourceBundle getBundle(String resourceName) {
        ResourceBundle bundle = null;
        try {
          bundle = ResourceBundle.getBundle(resourceName);
        } catch (java.util.MissingResourceException e) {
          s_log.warn("getBundle(" + resourceName + ") e:" + e);
          String message = " Antweb misconfigured. Error reading resource:" + resourceName + ".";
          if ("platform".equals(resourceName)) message += " Has a platform.properties file been copied into antweb/WEB-INF/classes/ ?";
          if ("AntwebResources".equals(resourceName)) message = " Has the appropriate antweb/etc/config script been executed?";
          s_log.error("getBundle(" + resourceName + ") message:" + message);
          System.exit(0);
        }
        return bundle;
    }

    private static ResourceBundle getAppResources() {
      if (s_appResources == null) {
        s_appResources = getBundle("ApplicationResources");
      }
      return s_appResources;
    }  
    
    private static ResourceBundle getAntwebResources() {
      if (s_antwebResources == null) {
        s_antwebResources = getBundle("AntwebResources");
      }
      return s_antwebResources;
    }  

    private static ResourceBundle getPlatformResources() {
      if (s_platformResources == null) {
        s_platformResources =  getBundle("platform");
      }
      return s_platformResources;
    }

	public static Boolean isProp(String isProp) {
		String prop = AntwebProps.getProp(isProp);
        if ("true".equals(prop)) {
          return true;
        } else {
          return false;
        }
	}

    private static HashMap<String, String> s_propMap = new HashMap<String, String>();

	public static @Nullable String getProp(String prop) {

        String value;

        if (s_propMap.containsKey(prop)) {
          value = s_propMap.get(prop);
          //A.log("getProp() propMap has key:" + prop + " value:" + value);
          return value;
        }

	    if (getAppResources() == null || getAntwebResources() == null || getPlatformResources() == null) {
	      s_log.warn("getProp() prop:" + prop + " appResources:" + s_appResources + " antwebResources:" + s_antwebResources + " platformResources:" + s_platformResources);
          loadResources();
	    }
        
        value = getProp(prop, "app", getAppResources());

	    if (value == null) {
            value = getProp(prop, "ant", getAntwebResources());
        }
	    if (value == null) {
            value = getProp(prop, "platform", getPlatformResources());
        }

        s_propMap.put(prop, value);

        return value;
	}

	private static @Nullable String getProp(String prop, String resource, ResourceBundle resources) {
      String propVal = null;
      if (resources == null) {
        s_log.error("getProp() resource:" + resource + " is null.");
      }
      try {
          propVal = resources.getString(prop);
	    } catch (MissingResourceException e) {
          if ("platform".equals(resource)) {
              if ( ! (
                  "site.securePort".equals(prop) // Documented that if this is commented out... see AntwebResources.properties.
               || "isLocal".equals(prop)
              ) ) {
                  s_log.warn("getProp(" + prop + ", " + resource + ", " + resources + ") not found in any bundle.  e:" + e);
              }
          }
	    }
      return propVal;
	}

	public static String getAntwebVersion() {
	  return AntwebUtil.getReleaseNum();
	}
		
    /* If a property is not found in the ApplicationResources.properties file it is returned as "" */

    public static String getYuiVersion() {
        //if (AntwebProps.isDevMode()) return "3.5.1";
        //if (!AntwebProps.isDevMode()) return "2.7.0";
        return "2.9.0";    
    }

    public static String getAntwebDir() {
        if (AntwebProps.isDevMode()) {
          // This is used for AntwebFunction.imageCheck() to call a python script in the soruce tree.
          return "/Users/mark/antweb/";
        } else {
          return "/antweb/deploy/";    
        }
    }

	public static String getDocRoot() {
	    // /usr/local/antweb/ points to either /data/antweb (on server) or /usr/local/tomcat/webapps/antweb on dev.
        return "/usr/local/antweb/";
	}

	// Create /usr/local/data softlink. On server: /data  On dev it is a directory.
    
    public static String getDataRoot() {
        // /usr/local/data/ points to either /data/antweb (on server) or /usr/local/tomcat/webapps/antweb on dev.
        return "/usr/local/antweb/";
    }
	
    public static String getTomcatDir() {
        return "/usr/local/tomcat/";
    }

	public static String getImagesDir() {
	    return getDocRoot() + "images/";
	}

	public static String getWebDir() {
		return getDocRoot() + "web/";
	}

    public static String getInputFileHome() { return getWorkingDir(); }

    private static String s_workingDir = null;
    public static String getWorkingDir() {
        if (s_workingDir != null) return s_workingDir;

        String workingDir = getDocRoot() + "workingDir/";

        boolean exists = new File(workingDir).exists();
        if (exists) {
            s_log.warn("getWorkingDir() workingDir:" + workingDir);
        } else {
            boolean success = FileUtil.makeDir(workingDir);
            if (!success) {
                s_log.error("getWorkingDir() Unable to make workingdir:" + workingDir);
                workingDir = null;
            }
        }
        s_workingDir = workingDir;
        return workingDir;
    }

    public static String getPlaziDir() {
        String workingdir = getWorkingDir() + "/plazi/";
        FileUtil.makeDir(workingdir);
        return workingdir;
	}

	public static String getGoogleMapKey() {
        return AntwebProps.getProp("googleMaps.key");
	}

    public static String getDbPwd() {
      return AntwebProps.getProp("dbPwd");
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
        return "https".equals(getProtocol());
    }
    
    public static String getProtocol() {
        // notice that this does not contain a follow / as does getSiteURL.  
        //A.log("getProtocol() protocol:"+ protocol);
        return AntwebProps.getProp("site.protocol");
    }
    
    // This is used for self reflexive requests. When the server calls itself, localhost:80 is not available since the
    // httpd container is separate. Instead, we use the 8080 port, which is not exposed outside the container.
    public static String getThisDomainApp() {
        // return getDomainApp();
        // String domainApp = "http://httpd";
        String domainApp = null;
        domainApp = "http://localhost:8080";

       //if (AntwebProps.isDevMode()) domainApp = "http://127.0.0.1";

        A.log("domainApp:" + domainApp);
        return domainApp;
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
        //A.log("getDomainApp() domainApp:" + domainApp);        
        return domainApp;
    }

    /** notice that this does not contain a follow / as does getSiteURL.   This
     should always be used instead of getSiteURL()
     <p>
     This will return something like:
     http://www.antweb.org
     of http://localhost/antweb
     or http://www.antweb.org/antweb_test
     or http://10.2.22.106
     */
    public static String getDomainApp() {


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
        return localStr != null && localStr.equals("true");
	}

	public static Boolean isDevMode() {
	  return AntwebProps.getIsDevMode();
	}

	public static Boolean getIsDevMode() {
		String devModeStr = AntwebProps.getProp("isDevMode");
        return devModeStr.equals("true");
	}

	public static Boolean isStageMode() {
	  return AntwebProps.getIsStageMode();
	}

	public static Boolean getIsStageMode() {
		String stageModeStr = AntwebProps.getProp("isStageMode");
        return stageModeStr.equals("true");
	}
	
	public static Boolean isDevOrStageMode() {
	  return isDevMode() || isStageMode();
	}
	
	public static Boolean isLiveMode() {
	  return !AntwebProps.isDevOrStageMode();
	}

	public static String report() {
        return "docRoot:" + getDocRoot()
        // + " inputFileHome:" + getInputFileHome()
          + " googleKey:" + AntwebProps.getGoogleMapKey()
          + " domainApp:" + getDomainApp()
          + " devMode:" + getIsDevMode();
	}

    public static String htmlReport() {

        return " <br>&nbsp;&nbsp;&nbsp;<b>DocRoot:</b> " + getDocRoot()
                + " <br>&nbsp;&nbsp;&nbsp;<b>DataRoot:</b> " + getDataRoot()
// + " <br>&nbsp;&nbsp;&nbsp;<b>InputFileHome:</b> " + getInputFileHome()
                + " <br>&nbsp;&nbsp;&nbsp;<b>ImagesDir:</b> " + getImagesDir()
                + " <br>&nbsp;&nbsp;&nbsp;<b>WebDir:</b> " + getWebDir()
                + " <br>&nbsp;&nbsp;&nbsp;<b>googleKey:</b> " + getGoogleMapKey();
    }
	
	public static String getTechAdminContact() {
	  return "re.mark.johnson@gmail.com";
	}
}


