package org.calacademy.antweb.util;


import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.io.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
       
public class CacheTask {

    private static final Log s_log = LogFactory.getLog(CacheTask.class);

    private static int SLEEP_TIME = 2000;

    public CacheTask(){
    }

    public void invokeCaching(){
        while(true){
            try {
                Thread.sleep(SLEEP_TIME);
                
                String url = AntwebProps.getDomainApp() + "/cache.do?action=genCacheItem";
                s_log.warn("invokeCaching() Caching:" + url);
                try { 
                    HttpUtil.getUrl(url);
                } catch (IOException e) {
                    s_log.error("invokeCaching() e:" + e);
                } 
                s_log.warn("invokeCaching() Done Caching");

            } catch (InterruptedException e) {
                s_log.error("invokeCaching() e:" + e);
            }
        }
    }
 
}
