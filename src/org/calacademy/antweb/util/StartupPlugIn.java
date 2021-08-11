/*
 * Created on Feb 9, 2006
 */
package org.calacademy.antweb.util;

import org.apache.struts.action.ActionServlet;
import org.apache.struts.action.PlugIn;
import org.apache.struts.config.ModuleConfig;


import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

    
public class StartupPlugIn implements PlugIn {

    private static Log s_log = LogFactory.getLog(StartupPlugIn.class);

  // This method will be called at application shutdown time
  public void destroy() {
    //s_log.warn("Entering Startup.destroy()");
    //Put hibernate cleanup code here
    s_log.warn("Exiting Startup.destroy()");
  }

  //This method will be called at application startup time
  public void init(ActionServlet actionServlet, ModuleConfig config) {

    System.setProperty ("jsse.enableSNIExtension", "false");
  }

}


