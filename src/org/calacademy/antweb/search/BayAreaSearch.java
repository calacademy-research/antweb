package org.calacademy.antweb.search;

import java.io.Serializable;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/** Class BayAreaSearch does the searching of the for the bay area search 
 *   the main difference with the other searches is that you can search on
 *   more than one county.*/

public class BayAreaSearch extends GenericSearch implements Serializable {

    private static Log s_log = LogFactory.getLog(BayAreaSearch.class);

    private String[] adm2s;   // was counties

    public String[] getAdm2s() {
        return (this.adm2s);
    }

    public void setAdm2s(String[] adm2s) {
        this.adm2s = adm2s;
    }
}
