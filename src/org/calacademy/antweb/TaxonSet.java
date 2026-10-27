package org.calacademy.antweb;

import java.sql.*;

import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public abstract class TaxonSet {

    private static final Log s_log = LogFactory.getLog(TaxonSet.class);

    protected int subfamilyCount = 0;
    protected int genusCount = 0;
    protected int speciesCount = 0;
    protected int specimenCount = 0;
    private int globalChildCount = 0;
    protected int imageCount = 0;    
    protected boolean isEndemic = false;
        
    protected String taxonName;
    protected String rank;
    protected Timestamp created;

    private String source;
    
    private int curatorId;

    public static final String PROXY = "proxy";
        
    protected boolean exists = false;
    public boolean exists() {
      return exists;
    }
    
  /* Next Subtaxon functionality enables a taxon to report how many subtaxons it has.
     For instance, for formicidae family:
       26 subfamilies, 476 genera, 30000 species, 150000 specimens)
   */

    public TaxonSet() {
    }
    
    public abstract void init(Connection connection) throws SQLException ;
    
    public String getNextGlobalSubtaxon(String rank) {
      if (globalChildCount == 0 && !"specimen".equals(rank)) return "No " + Rank.getNextPluralRank(rank, 1); //"ex: No Specimens";
      return "<span class='numbers'>" + globalChildCount + "</span> " + Rank.getNextPluralRank(rank, 1);    
    }

    public String getNextSubtaxon() {
      return getNextSubtaxon(rank, 1);
    }
    public String getNextSubtaxon(int depth) {
      return getNextSubtaxon(rank, depth);
    }
    public String getNextSubtaxon(String rank, int depth) {
      if (depth > Rank.getRankLevel(rank)) return "";
      int subtaxonCount = getSubtaxonCount(rank, depth);
      if (subtaxonCount == -1) return "";
      //A.log("getNextSubtaxon rank:" + rank + " depth:" + depth + " count:" + subtaxonCount);      
      if (subtaxonCount == 0 && depth > 1) return "";
      if (subtaxonCount == 0 && !"specimen".equals(rank)) return "No " + Rank.getNextPluralRank(rank, depth); //"ex: No Specimens";
      return "<span class='numbers'>" + subtaxonCount + "</span> " + Rank.getNextPluralRank(rank, depth);
    }

    public int getSubtaxonCount(int depth) {
      return getSubtaxonCount(rank, depth);
    }
    public int getSubtaxonCount(String rank, int depth) {
      if (depth == 1) {
        if (Rank.FAMILY.equals(rank)) return subfamilyCount;
        if (Rank.SUBFAMILY.equals(rank)) return genusCount;
        if (Rank.GENUS.equals(rank)) return speciesCount;
        if (Rank.SPECIES.equals(rank)) return specimenCount; //globalChildCount; // was:       
        if (Rank.SUBSPECIES.equals(rank)) return specimenCount; //globalChildCount; // was: specimenCount;      // added Feb 13, 2013
      } else if (depth == 2) {
        if (Rank.FAMILY.equals(rank)) return genusCount;
        if (Rank.SUBFAMILY.equals(rank)) return speciesCount;
        if (Rank.GENUS.equals(rank)) return specimenCount; //globalChildCount; // was: specimenCount;
      } else if (depth == 3) {
        if (Rank.FAMILY.equals(rank)) return speciesCount;
        if (Rank.SUBFAMILY.equals(rank)) return specimenCount; //globalChildCount; // was: specimenCount;
      } else if (depth == 4) {
        if (Rank.FAMILY.equals(rank)) return specimenCount; //globalChildCount; // was: specimenCount;
      }
      return -1;
    }
    
    public String getImageCountStr() {
      if (getImageCount() == 0) return "No Images";
      return "<span class='numbers'>" + getImageCount() + "</span> Images";
    }
        
    public String toString() {
      return "TaxonSet taxonName:" + getTaxonName() + " rank:" + rank
        + " subfamilyCount:" + getSubfamilyCount() + " genusCount:" + getGenusCount()
        + " speciesCount:" + getSpeciesCount() + " specimenCount:" + getSpecimenCount()
        + " imageCount:" + getImageCount() + " source:" + getSource()
        ;
    }

    public String getTaxonName() {
      return taxonName;
    }
    public void setTaxonName(String taxonName) {
      this.taxonName = taxonName;
    }

    public String getRank() {
      return rank;
    }
    public void setRank(String rank) {
      this.rank = rank;
    }

    public Timestamp getCreated() {
      return created;
    }
    public void setCreated(Timestamp created) {
      this.created = created;
    }
    
    public int getSubfamilyCount() {
        return subfamilyCount;
    }
    public void setSubfamilyCount(int subfamilyCount) {
        this.subfamilyCount = subfamilyCount;
    }
    public int getGenusCount() {
        return genusCount;
    }
    public void setGenusCount(int genusCount) {
        this.genusCount = genusCount;
    }
    public int getSpeciesCount() {
        return speciesCount;
    }
    public void setSpeciesCount(int speciesCount) {
        this.speciesCount = speciesCount;
    }
    public int getSpecimenCount() {
        return specimenCount;
    }
    public void setSpecimenCount(int specimenCount) {
        this.specimenCount = specimenCount;
    }
    
    // Loaded during OverviewTaxon.init() from Allantweb values.
    // Global specimenCount in case of species or subspecies.
    public int getGlobalChildCount() {
        return globalChildCount;
    }
    public void setGlobalChildCount(int globalChildCount) {
        this.globalChildCount = globalChildCount;
    }  
    
    // 2026-10-27 (images from anywhere): the images of the ant (labels never count) of the name in AntWeb; -1 when not
    // known. Species and subspecies rows: counted live from the image table (globalImageCountColumn), so it is right
    // before and between nightly runs. Higher ranks: the allantwebants list row's Images (nightly set 4).
    private int globalImageCount = -1;
    private boolean globalImageCountLive = false;
    public int getGlobalImageCount() {
        return globalImageCount;
    }
    public void setGlobalImageCount(int globalImageCount) {
        this.globalImageCount = globalImageCount;
    }
    /** True when getGlobalImageCount was counted live from the image table (species and subspecies rows). The place's
     *  own number cannot be higher; when the place's list row says more, it is an older count (labels included). */
    public boolean isGlobalImageCountLive() {
        return globalImageCountLive;
    }
    public void setGlobalImageCountLive(boolean globalImageCountLive) {
        this.globalImageCountLive = globalImageCountLive;
    }

    /** True for the ranks whose AntWeb-wide images are counted live: species and subspecies (their specimens are
     *  filed under their own name; a genus row's images roll up by the taxon tree, which only the nightly run does). */
    public static boolean countsImagesLive(String rank) {
        return Rank.SPECIES.equals(rank) || Rank.SUBSPECIES.equals(rank);
    }

    /**
     * A column for a list row's query (alias global_image_count): the images of the ant of the name in AntWeb.
     * Species and subspecies: counted live (globalImageCountSql). Until 2026-10-27 (PR #291, first staging run) every
     * rank read the allantwebants row's image_count, which on staging was 0 for every name (the allantwebants rows are
     * zeroed with the other project rows before set 4 recounts the live projects), so the list said "No Images" for
     * names with images and showed only the place's number. Higher ranks still read the allantwebants row (a genus is
     * the sum of its names by the taxon tree, nightly set 4); the Images cell then shows at least the place's number.
     */
    public String globalImageCountColumn(String escapedTaxonName) {
        if (countsImagesLive(getRank())) {
            return " , (" + globalImageCountSql(escapedTaxonName) + ") global_image_count";
        }
        return " , (select g.image_count from proj_taxon g where g.project_name = '" + Project.ALLANTWEBANTS
            + "' and g.taxon_name = '" + escapedTaxonName + "') global_image_count";
    }

    /** The images of the ant (shot type not l, StatisticsDb.ANT_IMAGE) of every specimen filed under the name. */
    public static String globalImageCountSql(String escapedTaxonName) {
        return "select count(i.id) from specimen s join image i on i.image_of_id = s.code where s.taxon_name = '"
            + escapedTaxonName + "' and " + org.calacademy.antweb.home.StatisticsDb.ANT_IMAGE;
    }

    protected void readGlobalImageCount(ResultSet rset) throws SQLException {
        int n = rset.getInt("global_image_count");
        setGlobalImageCount(rset.wasNull() ? -1 : n);
        setGlobalImageCountLive(countsImagesLive(getRank()) && !rset.wasNull());
    }

    public int getImageCount() {
        return imageCount;
    }
    public void setImageCount(int imageCount) {
        this.imageCount = imageCount;
    }

    public boolean getIsEndemic() {
        return isEndemic;
    }
    public void setIsEndemic(boolean isEndemic) {
        this.isEndemic = isEndemic;
    }
        
    public String getSource() {
      return source;
    }
    public void setSource(String source) {
      this.source = source;
    }
    
    public int getCuratorId() {
        return curatorId;
    }
    public void setCuratorId(int curatorId) {
        this.curatorId = curatorId;
    }
        
    //public abstract String getSourceAnchor();    
    //public abstract String getSourceStr();
    public String getSourceStr() {
      return Source.getSourceStr(getSource());
    }

    public String getSourceDisplay() {
       String source = getSource();

       if (source == null) {
         if (this instanceof MuseumTaxon) {
           return ""; // Museum does not have source field.
         }
         String detail = "";
         if (this instanceof BioregionTaxon) detail += " bioregionTaxa:" + ((BioregionTaxon) this).getBioregionName();
         if (this instanceof GeolocaleTaxon) detail += " geolocaleTaxa:" + ((GeolocaleTaxon) this).getGeolocaleId();
         if (this instanceof ProjTaxon) detail += " projTaxa:" + ((ProjTaxon) this).getProjectName();
         s_log.warn("getSourceDisplay() effective source is null for getTaxonName:" + getTaxonName() + " source:" + source + " detail:" + detail);
         return "";
       }
       
       if (source != null && source.contains(PROXY)) {
         source = source.substring(PROXY.length());
         //A.log("getSourceDisplay() source:" + source);
       }

       return Source.getSourceDisplay(source);
    }

}
