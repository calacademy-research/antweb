package org.calacademy.antweb.upload;

import java.io.*;
import java.util.*;
import java.util.Date;
import java.sql.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class SpecimenUploadSupport extends AntwebUpload {
/*
  Class is designed to support SpecimenUploadProcess and SpecimenUploadParse.
*/

    private static Log s_log = LogFactory.getLog(SpecimenUploadSupport.class);

    private ArrayList<String> m_badRankTaxonList = new ArrayList(); 
    
    SpecimenUploadSupport(Connection connection) {
      super(connection, "specimenUpload");    
    }

    public ArrayList<String> getBadRankTaxonList() {
      return m_badRankTaxonList;
    }
      
}
