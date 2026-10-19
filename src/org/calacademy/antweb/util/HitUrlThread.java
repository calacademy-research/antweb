package org.calacademy.antweb.util;

import java.io.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public class HitUrlThread extends Thread {

    private static final Log s_log = LogFactory.getLog(HitUrlThread.class);

	private final java.util.Map<String, String> m_headers;

	public HitUrlThread(String str) {
		this(str, null);
	}

	/** With request headers (AntWeb's calls to itself send their code in a header, see SchedulerCall). */
	public HitUrlThread(String str, java.util.Map<String, String> headers) {
		super(str);
		m_headers = headers;
	}

	public void run() {
		s_log.debug("name: " + getName());
		try {

  		  if (m_headers == null) HttpUtil.getUrl(getName());
  		  else HttpUtil.fetchUrl(getName(), HttpUtil.DEFAULT_READ_TIMEOUT_MS, m_headers);
		  //for (int i = 0; i < 5; i++) {
			
			sleep((int) (Math.random() * 2000));
		} catch (IOException | InterruptedException e) {
		  s_log.warn("run() e:" + e);
		}
        //}
		s_log.debug("hitUrl() Finished for: " + getName());
	}
}
