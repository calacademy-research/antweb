package org.calacademy.antweb.util;

import java.io.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public class HitUrlThread extends Thread {

    private static Log s_log = LogFactory.getLog(HitUrlThread.class);

	public HitUrlThread(String str) {
		super(str);
	} 
	public void run() {
		s_log.debug("name: " + getName());
		try {

  		  HttpUtil.getUrl(getName());
		  //for (int i = 0; i < 5; i++) {
			
			sleep((int) (Math.random() * 2000));
		} catch (IOException | InterruptedException e) {
		  s_log.warn("run() e:" + e);
		}
        //}
		s_log.debug("hitUrl() Finished for: " + getName());
	}
}
