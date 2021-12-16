package org.calacademy.antweb.util;

import java.io.IOException;
import java.util.Date;

import javax.servlet.http.*;
import org.apache.struts.action.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.imageUploader.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.Map;

import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class AntwebMgr {

    private static final Log s_log = LogFactory.getLog(AntwebMgr.class);
    private static boolean s_oneAtATime = false;
    private static boolean s_isPopulated = false;
    private static boolean isInitializing = false;    

    // Specimen upload is incremented at the start of the try block.
    // It is reloaded at the end of the upload to support the new log.
    // Stored in the upload table.
    private static int s_nextSpecimenUploadId = 0;
    public static int getNextSpecimenUploadId() {
      //A.log("getSpecimenUploadId() specimenUploadId:" + s_specimenUploadId);
      return s_nextSpecimenUploadId;
    }
    public static void setNextSpecimenUploadId(int specimenUploadId) {
      //A.log("setUploadId() specimenUploadId:" + specimenUploadId);
      s_nextSpecimenUploadId = specimenUploadId + 1;
    }
    public static void incrementSpecimenUploadId(Connection connection) {
      setNextSpecimenUploadId(UploadDb.getMaxSpecimenUploadId(connection));
    }

    public static void populate(Connection connection) {
      populate(connection, false);
    }
    
    public static void populate(Connection connection, boolean forceReload) {
      populate(connection, forceReload, false);
    }

    public static void populate(Connection connection, boolean forceReload, boolean inThread) {    
		if (!s_isPopulated || forceReload) {       

			if (!s_oneAtATime) {
			  s_oneAtATime = true;
			} else {
			  return;
			}

            setStartTime();
 		
			incrementSpecimenUploadId(connection);

            try {
                AntwebMgr.serverInitializing();

                A.log("AntwebMgr.populate() 1");

                AntwebMgr.populateMgrs(connection, forceReload);

                A.log("AntwebMgr.populate() 2");

                AntwebMgr.initializationComplete();
            } catch (Exception e) {
                AntwebUtil.log("error running thread e:" + e.getMessage());
            }

// REFACTOR: Problematic. IsPopulated is not trustworthy. It is set before the thread completes.
// Instead,  check for null values...

            if (!s_isPopulated) {

              s_isPopulated = true;

              s_log.warn("populate() server started.");
            }               

			s_oneAtATime = false;          
		}
    }    


    private static boolean isMuseumPopulated = false;
    private static boolean isBioregionPopulated = false;
    private static boolean isGeolocalePopulated = false;
    private static boolean isProjectPopulated = false;
    private static boolean isLoginMgrPopulated = false;
    private static boolean isTaxonMgrPopulated = false;
    private static boolean isTaxonPropMgrPopulated = false;
    private static boolean isUploadMgrPopulated = false;
    private static boolean isArtistMgrPopulated = false; 
    private static boolean isAdminAlertMgrPopulated = false;

    //private static boolean isInitializationComplete = false;

    public static boolean isLog() {
      return true; // false;
    }
    
    public static void populateMgrs(Connection connection, boolean forceReload) {

        populateStats(connection, forceReload);

        Date start = new Date();

        boolean log = true;
        if (log) AntwebUtil.log("populateMgrs() GroupMgr");
        GroupMgr.populate(connection, forceReload);
        if (log) s_log.warn("populateMgrs() AllAntwebMgr");
        AllAntwebMgr.populate(connection);
        if (log) s_log.warn("populateMgrs() LoginMgr");
        LoginMgr.populate(connection, forceReload, true);
        isLoginMgrPopulated = true;
        if (log) s_log.warn("populateMgrs() ProjectMgr");
        ProjectMgr.populate(connection, forceReload);
        isProjectPopulated = true;
        if (log) s_log.warn("populateMgrs() BioregionMgr");
        BioregionMgr.populate(connection, forceReload);
        isBioregionPopulated = true;
        if (log) s_log.warn("populateMgrs() MuseumMgr");
        MuseumMgr.populate(connection, forceReload);
        isMuseumPopulated = true;
        if (log) s_log.warn("populateMgrs() GeolocaleMgr");
        isGeolocalePopulated = true;
        GeolocaleMgr.populate(connection, forceReload, true);  // Slow!
        if (log) s_log.warn("populateMgrs() TaxonPropMgr");
        TaxonPropMgr.populate(connection, forceReload);
        isTaxonPropMgrPopulated = true;
        if (log) s_log.warn("populateMgrs() TaxonMgr");
        TaxonMgr.populate(connection, forceReload, true);
        isTaxonMgrPopulated = true;
        if (log) s_log.warn("populateMgrs() UploadMgr");
        UploadMgr.populate(connection, forceReload);
        isUploadMgrPopulated = true;
        if (log) s_log.warn("populateMgrs() ArtistMgr");
        ArtistMgr.populate(connection, forceReload, true);
        isArtistMgrPopulated = true;
        if (log) s_log.warn("populateMgrs() AdminAlertMgr");
        AdminAlertMgr.populate(connection);
        isAdminAlertMgrPopulated = true;

        ServerStatusAction.populate(connection);

        UserAgentTracker.init(connection);
        MapMgr.refresh();

        if (log) s_log.warn("populateMgrs() done in " + AntwebUtil.reportTime(start));

        callPostInitialize();
    }

    public static String reload(String name, boolean forceReload, Connection connection) {
        String message = null;
        if ("group".equals(name)) {
            GroupMgr.populate(connection, forceReload);
            message = "GroupMgr Reloaded";
        } else if ("allAntweb".equals(name)) {
            AllAntwebMgr.populate(connection);
            message = "AllAntwebMgr Reloaded.";
        } else if ("login".equals(name)) {
            LoginMgr.populate(connection, forceReload, true);
            message = "LoginMgr Reloaded.";
        } else if ("project".equals(name)) {
            ProjectMgr.populate(connection, forceReload);
            message = "ProjectMgr Reloaded.";
        } else if ("bioregion".equals(name)) {
            BioregionMgr.populate(connection, forceReload);
            message = "BioregionMgr Reloaded.";
        } else if ("museum".equals(name)) {
            MuseumMgr.populate(connection, forceReload);
            message = "MuseumMgr Reloaded.";
        } else if ("geolocale".equals(name)) {
          GeolocaleMgr.populate(connection, forceReload, true);  // Slow!
          message = "GeolocaleMgr Reloaded.";
        } else if ("taxonProp".equals(name)) {
            TaxonPropMgr.populate(connection, forceReload);
            message = "TaxonPropMgr Reloaded.";
        } else if ("taxon".equals(name)) {
            TaxonMgr.populate(connection, forceReload, true);
            message = "TaxonMgr Reloaded.";
        } else if ("upload".equals(name)) {
            UploadMgr.populate(connection, forceReload);
            message = "UploadMgr Reloaded.";
        } else if ("artist".equals(name)) {
            ArtistMgr.populate(connection, forceReload, true);
            message = "ArtistMgr Reloaded.";
        } else if ("adminAlert".equals(name)) {
            AdminAlertMgr.populate(connection);
            message = "AdminAlertMgr Reloaded.";
        } else {
            AntwebMgr.populate(connection, true);
            message = "AntwebMgr Reloaded.";
        }
        return message;
    }

    public static void callPostInitialize() {

        String url = AntwebProps.getThisDomainApp() + "/util.do?action=postInstantiate";
        //if (AntwebProps.isDevMode()) url = "https://localhost:8080/util.do?action=postInstantiate";

        A.log("callPostInitialize() url:" + url);
        //s_log.warn("callPostInitialize() url:" + url);
        try {
            HttpUtil.hitUrl(url);
        } catch (IOException e) {
            s_log.error("callPostInitialize() e:" + e);
        }
    }

    //Invoked from UtilAction to, in a separate thread..
    public static void postInitialize(Connection connection) throws SQLException, IOException {
        Date start = new Date();
        s_log.warn("postInitialize() begin");

        GeolocaleMgr.postInitialize(connection); // Not really used.

        LoginMgr.postInitialize(connection);

        ArtistMgr.postInitialize(connection);

        TaxonMgr.postInitialize(connection);

        AntwebUtil.getUploadGroupList();

        AntwebMgr.genRecentContent(connection); // Takes about a second. Mostly redundant but good for new installations.

        s_log.warn("postInitialize() end in " + AntwebUtil.reportTime(start));
    }

    // Dynamically generated content. Useful for home page.
    //   web/genInc/recentImages_gen_inc.jsp
    //   web/genInc/recentDescEdits.jsp
    public static void genRecentContent(Connection connection) throws SQLException, IOException {
        Date startTime = new Date();
        AntwebFunctions.genRecentDescEdits(connection);

        (new ImageUploaderAction()).writeRecentImages(connection);

        A.log("genRecentContent() secs:" + AntwebUtil.secsSince(startTime));
        // (new StatisticsDb(connection)).populateStatistics();
    }

    public static boolean isServerInitializing(String manager) {
        if (Check.MUSEUM.equals(manager) && isMuseumPopulated) return false;
        if (Check.GEOLOCALE.equals(manager) && isGeolocalePopulated) return false;
        if (Check.PROJECT.equals(manager) && isProjectPopulated) return false;
        if (Check.BIOREGION.equals(manager) && isBioregionPopulated) return false;
        if (Check.LOGIN.equals(manager) && isLoginMgrPopulated) return false;
        if (Check.TAXON.equals(manager) && isTaxonMgrPopulated) return false;
        if (Check.TAXONPROP.equals(manager) && isTaxonPropMgrPopulated) return false;
        if (Check.UPLOAD.equals(manager) && isUploadMgrPopulated) return false;
        if (Check.ARTIST.equals(manager) && isArtistMgrPopulated) return false;
        if (Check.ADMINALERT.equals(manager) && isAdminAlertMgrPopulated) return false;

        return isServerInitializing();
    }
    
    public static boolean isServerInitializing(Overview overview) {
        //A.log("isServerInitializing() isMuseumPopulated:" + isMuseumPopulated + " overview:" + overview + " class:" + overview.getClass());
        if (overview instanceof Museum && isMuseumPopulated) return false;
        if (overview instanceof Geolocale && isGeolocalePopulated) return false;
        if (overview instanceof Project && isProjectPopulated) return false;
        if (overview instanceof Bioregion && isBioregionPopulated) return false;        
        boolean isIt = isServerInitializing();
        A.log("isServerInitializing() isIt:" + isIt);
        return isIt;
    }

    // See Check.java.
    public static ActionForward isInitializing(HttpServletRequest request, ActionMapping mapping) {
        if (AntwebMgr.isServerInitializing()) {
             request.setAttribute("message", "Server is initializing...");
             return (mapping.findForward("message"));   
        }
        return null;
    }

    public static boolean isInitialized() {
      return !isServerInitializing();
    }

    public static boolean isServerInitializing() {
        return isInitializing;
    }
    
    public static void serverInitializing() {
        isInitializing = true;
        AntwebMgr.createSiteWarning("<br><font color=lightgreen>&nbsp;&nbsp;&nbsp;Server is initializing....</font>");			
    }
    
    public static void initializationComplete() {
        isInitializing = false;
        AntwebMgr.removeSiteWarning();    
    }

    public static String getReport() {
      String report = " MapMgr:" + MapMgr.report();  
      report += " MapCount:" + Map.getDisplayMapCount() + " MapHashCounts:" + Map.getDisplayMapHashCounts();
      return report;
    }
    public static String getHtmlReport() {
      String report = "<br><b>MapMgr: </b>" + MapMgr.report();
      report += "<br><b>MapCount: </b>" + Map.getDisplayMapCount() + " MapHashCounts:" + Map.getDisplayMapHashCounts();
      return report;
    }

    /*
    The "Site Warning" will be visible to all in the header near the login.
    */
    // Something like: <br><font color=lightgreen></font> 
    public static void createSiteWarning(String siteWarning) {
      AntwebUtil.writeFile(AntwebProps.getWebDir() + "siteWarning.jsp", siteWarning);
    }
    public static void removeSiteWarning() {
      AntwebUtil.remove(AntwebProps.getWebDir() + "siteWarning.jsp");
    }

    /*
    The "Server Messages" will appear on the curate page.
    */
	public static boolean hasServerMessage() {
      // The message would be displayed on the curate page.
        return ServerStatusAction.isInDownTime() || UploadAction.isInUploadProcess() || UtilDataAction.isInComputeProcess() || DBUtil.isServerBusy() || isInitializing;
	}
    public static String getSimpleServerMessage() {
       if (isInitializing) {
		 return "The server is initializing...";       
       }
	   if (ServerStatusAction.isInDownTime()) {
		 return "The Upload Services are down for site maintenance.";
	   }
	   if (UploadAction.isInUploadProcess()) {
		 return "Upload Currently InProcess (" + UploadAction.getIsInUploadProcess() + "). Some services are down.";
	   }
	   if (UtilDataAction.isInComputeProcess()) {
		 return "Computation Currently In Process (" + UtilDataAction.getIsInComputeProcess() + "). Some services are down.";
	   }
	   if (DBUtil.isServerBusy()) {
		 return "Server is busy.  Some services currently inactivated.";
	   }
       return "";
    }    
    public static String getServerMessage() {
       if (isInitializing) {
		 return "<h3><font color='red'>The server is initializing...</font></h3>";       
       }    
	   if (ServerStatusAction.isInDownTime()) {
		 return ServerStatusAction.getDownTimeMessage();
	   }
	   if (UploadAction.isInUploadProcess()) {
		 return "<h3><font color='red'>Upload Currently In <a title='" + UploadAction.getIsInUploadProcess() + "'>Process</a> (some services are down).</font></h3>";
	   }
	   if (UtilDataAction.isInComputeProcess()) {
		 return "<h3><font color='red'>Computation Currently In <a title='" + UtilDataAction.getIsInComputeProcess() + "'>Process</a> (some services are down).</font></h3>";
	   }
	   if (DBUtil.isServerBusy()) {
		 return "<h2><font color='red'>Server is busy.  Service currently inactivated.</font></h2>";
	   }
       return "";
    }     
    
    private static boolean alreadyTriedOnce = false;

    public static boolean isPopulated() {

      if (!alreadyTriedOnce) {
        alreadyTriedOnce = true;
        webPopulate();
      }    
    
      return s_isPopulated;
    }

    public static void webPopulate() {
      //s_log.warn("webPopulated() isPopulated:" + s_isPopulated + " museums:" + MuseumMgr.getMuseums());

      try {
          if (!s_isPopulated) {
            String page = AntwebProps.getThisDomainApp() + "/uptime.do";
            s_log.warn("isPopulated() calling:" + page); 
            String results = HttpUtil.getUrl(page);
          }
      } catch (IOException e) {
          s_log.warn("isPopulated() e:" + e);
      }
    }

    //static int imagedSpecimensCount = 0;
    static int specimenCount = 0;
    static int imagedSpecimensCount = 0;
    static int imagedSpeciesCount = 0;
    static int totalImagesCount = 0;
    static int validSpeciesCount = 0;

    public static void setSpecimensCount(int c) {
      specimenCount = c;
    }
    public static int getSpecimensCount() {
      return specimenCount;
    }
    
    public static void setValidSpeciesCount(int c) {
      validSpeciesCount = c;
    }      
    public static int getValidSpeciesCount() {
      return validSpeciesCount;
    }
    
    public static void setTotalImagesCount(int c) {
      totalImagesCount = c;
    }
    public static int getTotalImagesCount() {
      return totalImagesCount;
    }

    public static void setImagedSpecimensCount(int c) {
      imagedSpecimensCount = c;
    }
    public static int getImagedSpecimensCount() {
      return imagedSpecimensCount;
    }

    public static void setImagedSpeciesCount(int c) {
      imagedSpeciesCount = c;
    }
    public static int getImagedSpeciesCount() {
      return imagedSpeciesCount;
    }
  
/*
            String update = "insert into statistics "
                    + " (action, specimens, extant_taxa, total_taxa, proj_taxa, total_images, specimens_imaged, species_imaged, valid_species_imaged, login_id, exec_time) "  //
                    + " values ('" + action + "'," +  specimenRecords + "," + validTaxa + "," + numberTotalTaxa 
                    + "," + numberProjTaxa 
                    + "," + totalImages + "," + imagedSpecimens 
                    + "," + imagedSpecies + "," + validSpeciesImaged + "," + loginId + ", '" + execTime + "')";   
*/                      
  
    public static void populateStats(Connection connection, boolean forceReload) {
        //A.log("populateStats() c:" + getSpecimensCount() + " forceReload:" + forceReload);
        if (getSpecimensCount() != 0 && !forceReload) return;      
		String query = "select specimens, extant_taxa, total_taxa, total_images, specimens_imaged, species_imaged from statistics order by created desc limit 1";

        Statement stmt = null;
        ResultSet rset = null;
        try { 
            stmt = DBUtil.getStatement(connection, "populateStats()"); 
            rset = stmt.executeQuery(query);
        
            //A.log("populateStats() query:" + query);

            while (rset.next()) {
              setSpecimensCount(rset.getInt("specimens"));
              setValidSpeciesCount(rset.getInt("extant_taxa"));
              setTotalImagesCount(rset.getInt("total_images"));
              setImagedSpecimensCount(rset.getInt("specimens_imaged"));
              setImagedSpeciesCount(rset.getInt("species_imaged"));
            }     
        } catch (SQLException e) {
            s_log.warn("populateStats() query:" + query + " e:" + e);
        } finally {
              DBUtil.close(stmt, rset, "populateStats()");
        }	
    }  

    public String report() { 
        return "" + getStartTime();
    }
       
    public static Date s_startTime = null;        
    public static Date getStartTime() {
      return s_startTime;
    }
    public static void setStartTime() {
      setStartTime(new Date());
    }
    public static void setStartTime(Date time) {
      s_startTime = time;
    }    

}



