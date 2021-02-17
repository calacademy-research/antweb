package org.calacademy.antweb;

import java.io.*;
import java.sql.*;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.CDATASection;

import org.calacademy.antweb.util.*;

import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public class EOL {

    private static Log s_log = LogFactory.getLog(EOL.class);
    // protected String license = "http://creativecommons.org/licenses/by-nc-sa/1.0/";
    protected String license = "http://creativecommons.org/licenses/by-nc-sa/4.0/";
    
    private static int s_devModeLimit = 100;
    
    /*
       Eli Agbayani eagbayani@eol.org.  updates to support our eol services.
       Characters at beginning.  ConcurrentExceptions.  Old jar files and exceptions on dev machine.
       Need to upgrade Tomcat and Jars.
       Queries to /getEOL.do sometimes don't finish, and when they do, may not be properly closed xml.
     */

    public EOL() {
        super();
    }
    
    public String generateXml(Connection connection) {
        String results = null;
    	ArrayList theTaxa = null;
			
		theTaxa = getAllTaxa(connection,"genus");
        theTaxa.addAll(getAllTaxa(connection,"species"));
			
		if (theTaxa != null) {
            results = writeXml(theTaxa);
		}
		
		return results;
    }
    
    
    private ArrayList getAllTaxa(Connection connection, String rank) {
        ArrayList theTaxa = new ArrayList();
        TaxaPage taxaPage = new TaxaPage();
        Project project = ProjectMgr.getProject("worldants");

        if (AntwebProps.isDevMode()) taxaPage.setLimit(s_devModeLimit);
        int i = 0;
        
        try {
          taxaPage.setChildren(connection, project, rank);
          theTaxa = taxaPage.getChildren();
          s_log.warn("rank " + rank + " found " + theTaxa.size() + " items");
          Iterator childIter = theTaxa.iterator();
          Taxon taxon = null;
          while (childIter.hasNext()) {    
              ++i;
//              if (AntwebProps.isDevMode() && (i > s_devModeMax)) continue;
                            
              if (rank.equals("genus")) {
                  taxon = (Genus) childIter.next();
              } else if (rank.equals("species")) {
                  taxon = (Species) childIter.next();    // This line causes ConcurrentModificationException
              }
              taxon.setConnection(connection);
                
              if (rank.equals("species")) {
                // This will allow for all images instead of 1 set.  See: anoplolepis gracilipes
                taxon.setAllImages();
              } else {
                Project worldantsProject = ProjectMgr.getProject(Project.WORLDANTS);
                taxon.setImages(worldantsProject);                
              }
              //   s_log.warn("Images found for rank:" + rank + " count:" + taxon.getImages().size() + " name:" + taxon.getTaxonName());

              taxon.setRank(rank);
              taxon.setDescription(new DescEditDb(connection).getDescEdits(taxon, false));
              taxon.setConnection(null);
          }
        } catch (SQLException e2) {
          // Thau, nice one.  TaxaPage.setChildren() throws Exception")
          s_log.error("getAllTaxa() 2 WSS.  rank:" + rank + " i:" + i + " exception:" + e2.toString());
        }
          
        return theTaxa;
    }    
    
    private String writeXml(ArrayList taxa) {
        String encoding = "UTF-8"; //"ISO8859_1";  // UTF8?

		String uniq = DateUtil.getEolFormatDateStr();
		String outFileName =  uniq + ".xml";
		String eolDir = AntwebProps.getDocRoot() + "web/eol/";
        Utility.makeDirTree(eolDir);
		String dirFileLoc = eolDir + outFileName;
		String copyFileLoc = eolDir + "eol.xml";

        try {
            OutputStream fout= new FileOutputStream(dirFileLoc);
            OutputStream bout= new BufferedOutputStream(fout);
            OutputStreamWriter outFile = new OutputStreamWriter(bout, encoding);
            outFile.write(getResponseHeader());
   
            Iterator iter = taxa.iterator();
            String domainApp = AntwebProps.getDomainApp();
            Formatter formatter = new Formatter();
            
            int i = 0;
            
            while (iter.hasNext()) {

                ++i;                
               // if (AntwebProps.isDevMode() && (i > s_devModeMax)) continue;


                Taxon thisTaxon = (Taxon) iter.next();
                if (!(thisTaxon.getRank().equals("species") || thisTaxon.getRank().equals("genus"))) {
                    continue;
                }
                
                String taxonName = formatter.capitalizeFirstLetter(thisTaxon.getFullName());
                outFile.write("\n  <taxon>");
                append(outFile, 4, "dc:identifier", thisTaxon.getFullName());

                if (thisTaxon.getRank().equals("genus")) {
                    String uri = "/description.do?name=" + thisTaxon.getGenus() + "&amp;rank=genus";
                    //String source = siteUrl + uri;
                    String source = domainApp + uri;
                    append(outFile, 4, "dc:source", source);
                } else if (thisTaxon.getRank().equals("species")) {
                    String uri = "/description.do?genus=" + thisTaxon.getGenus() + "&amp;name=" + thisTaxon.getName() + "&amp;rank=species";
                    //String source = siteUrl + uri;
                    String source = domainApp + uri;
                    append(outFile, 4, "dc:source", source);
                }                

                append(outFile, 4, "dwc:Phylum", "Arthropoda");
                append(outFile, 4, "dwc:Class", "Insecta");
                append(outFile, 4, "dwc:Order", "Hymenoptera");
                append(outFile, 4, "dwc:Family", "Formicidae");
                append(outFile, 4, "dwc:ScientificName", taxonName);
  
                if (thisTaxon.getImages() != null) {                    
                    Iterator imageIter = thisTaxon.getImages().values().iterator();
                    while (imageIter.hasNext()) {
                        addImageObject(outFile, (SpecimenImage) imageIter.next(), taxonName);
                    }
                }
 
                String revDate=null;
                String author=null;
                if (thisTaxon.getDescription().containsKey("revdate")) {
                    revDate = (String) thisTaxon.getDescription().get("revdate");
                }
                if (thisTaxon.getDescription().containsKey("textauthor")) {
                    author = (String) thisTaxon.getDescription().get("textauthor");
                }

/* 
Mark changed Jun 7, 2012.                 
  Overview has #TaxonBiology subject.  Added.
  Biology has #Description subject.  Modified, was TaxonBiology.
  Identification has DiagnosticDescription subject.  Modified, was GeneralDescription.  
  Identification has #DiagnosticDescription subject.  Modified, was GeneralDescription.
  Taxonomic History has $Description subject.  Added.
  Taxonomic Notes has #Description subject.  Added.
  Taxonomic Treatment has #DiagnosticDescription subject.  Added.                

                // Specimen Data Summary.  Don't know about this.  Would become Habitat.
                // Taxon Page Images.  Don't know about this.  Would become media.  See addImageObject() below.

Eli Agbayani eagbayani@eol.org,
"Katja Schulz" <SchulzK@si.edu>,
Cynthia Parr <parrc@si.edu>,
Eli Sarnat <ndemik@yahoo.com>,
Jen Hammock <hammockj@si.edu>,
"Brian L. Fisher" <bfisher@calacademy.org>

EOL Subjects:  http://eol.org/info/98

Suggested: Katja Jun 11, 2012.
NON SPM subjects should be added in additionalInformation tags
 Species Profile Model (SPM) of the Taxonomic Databases Working Group (TDWG). 

<dataObject> 
  <dc:identifier>eolspecies:nid:1:000000</dc:identifier>
  <dataType> http://purl.org/dc/dcmitype/Text </dataType> 
  <agent role="author">Oscar Meyer</agent>
  <dcterms:created>2009-06-2 16:16:30</dcterms:created> 
  <dcterms:modified>2010-07-8 16:16:16</dcterms:modified>
  <license> http://creativecommons.org/licenses/by-nc/3.0/ </license> 
  <dcterms:rightsHolder>Oscar Meyer</dcterms:rightsHolder> 
  <dc:source> http://eolspecies.org/pages/7664 </dc:source> 
  <subject> http://rs.tdwg.org/ontology/voc/SPMInfoItems#Description </subject> 
  <dc:description xml:lang="en">In nova fert animus mutatas dicere formas corpora.</dc:description> 
  <additionalInformation>
    <subject> http://www.eol.org/voc/table_of_contents#Taxonomy </subject>
  </ additionalInformation> 
</dataObject>

Currently:
<dataObject>
  <dc:identifier>Acromyrmex pubescens/DiagnosticDescription</dc:identifier>
  <dataType>http://purl.org/dc/dcmitype/Text</dataType>
  <mimeType>text/html</mimeType>
  <dc:title>Taxonomic Treatment</dc:title>
  <dc:language>en</dc:language>
  <license>http://creativecommons.org/licenses/by-nc-sa/1.0/</license>
  <subject>
http://rs.tdwg.org/ontology/voc/SPMInfoItems#DiagnosticDescription
  </subject>
  <dc:description>
<![CDATA[
<p></p><b> <a href="http://www.antbase.org/ants/publications/21367/21367.pdf" target="new">Wild, A. L., 2007</a>: </b><br> “Paraguay” (s. loc.) (MCSN, NHMB). Literature records: Pte. Hayes, “Paraguay” (s. loc.) (Emery 1905, Fowler 1985). NEW STATUS . <br> Atta (Acromyrmex) pubescens Emery 1905: 51. [w syntypes examined, MCSN , MHNG ; Paraguay ( Balzán )] . <br> Acromyrmex lundi var. pubescens (Emery) . Bruch 1914: 216. <br> Acromyrmex lundi st. pubescens (Emery) . Santschi 1916: 386. <br> Acromyrmex lundi pubescens (Emery) . Kempf 1972: 13. <br> Fowler (1985a) described the differing habitat associations of Acromyrmex lundii and Acromyrmex pubescens , noting that the former is found in open habitats and the latter in the patchy forest “islands” that occur in the chaco savannah. The two forms are sympatric and structurally similar, both bearing elongate lateral pronotal spines that are longer than the mesonotal spines. However, these ants are distinguishable in pubescence. Much of the integument of A. pubescens is covered in a dense decumbent pubescence while the integument of A. lundi is relatively bare. This difference is easiest to diagnose on the mesopleura, as the mesopleural pubescence of A. pubescens comprises overlapping hairs, while that of A. lundii is sparse and the hairs non-overlapping. Given the ecological and morphological differences in sympatry between these two forms, I elevate A. pubescens to species here. <br>
]]>
  </dc:description>
</dataObject>
*/  

                if (thisTaxon.getDescription().containsKey("overview")) {
                    addTextObject(outFile, "TaxonBiology", "Overview", (String) thisTaxon.getDescription().get("overview"), revDate, author, taxonName);
                }
                
                if (thisTaxon.getDescription().containsKey("biology")) {
                    addTextObject(outFile, "Description", "Biology", (String) thisTaxon.getDescription().get("biology"), revDate, author, taxonName);
                }

                // not modified.
                if (thisTaxon.getDescription().containsKey("distribution")) {
                    addTextObject(outFile,"Distribution","Distribution", (String) thisTaxon.getDescription().get("distribution"), revDate, author, taxonName);
                }

                if (thisTaxon.getDescription().containsKey("identification")) {
                    addTextObject(outFile,"DiagnosticDescription","Identification", (String) thisTaxon.getDescription().get("identification"), revDate, author, taxonName);
                }

                if (thisTaxon.getDescription().containsKey("taxonomichistory")) {
                    addTextObject(outFile,"Description","Taxonomic History", (String) thisTaxon.getDescription().get("taxonomichistory"), revDate, author, taxonName);
                }
                
                if (thisTaxon.getDescription().containsKey("taxonomicnotes")) {
                    addTextObject(outFile,"Description","Taxonomic Notes", (String) thisTaxon.getDescription().get("taxonomicnotes"), revDate, author, taxonName);
                }
                
                if (thisTaxon.getDescription().containsKey("taxonomictreatment")) {
                    addTextObject(outFile,"DiagnosticDescription","Taxonomic Treatment", (String) thisTaxon.getDescription().get("taxonomictreatment"), revDate, author, taxonName);
                }

/*
Modifications made on Jun 7, 2012 by mark.

From: https://docs.google.com/spreadsheet/ccc?key=0AmgLCdM5eQuNdENTR0wtc2Z4SjZYa01PcDRMUFNnT2c#gid=1
Current Antweb	EOL Subject (1)	EOL Subject (2)	EOL Subject (3)
Taxon Page Author History	?		
Overview	#TaxonBiology		
Biology:	#Description	#GeneralDescription	#Biology
Comments:			
Distribution:	#Distribution		
Identification:	#DiagnosticDescription		
Notes:			
References:	not a content subject, these are listed as reference element		
Specimen Data Summary	#Habitat		
Taxon Page Images:	media		
Taxonomic History	#Description		
Taxonomic Notes:	#Description		
Taxonomic Treatment:	#DiagnosticDescription			
*/

                outFile.write("\n  </taxon>");
            }
            outFile.write(getResponseFooter());
            outFile.close();
        } catch (IOException e) {
            s_log.error("writeXmlNotUsingDoc() e:" + e);
        }    

        try {
          (new Utility()).copyFile(dirFileLoc, copyFileLoc);
        } catch (IOException e) {
          s_log.error("writeXML() copy file exception:" + e);
          return "write EOL XML error";
        }
        
		String webLoc = AntwebProps.getDomainApp() + "/web/eol/eol.xml";    // + outFileName;
		String results = "Your file is here: <a href=\"" + webLoc + "\">" + webLoc + "</a>";
          //s_log.info("EOL End xml:" + xml.substring(0, 1000));                 	
		s_log.warn("End EOL.  Generated EOL file:" + dirFileLoc);
        return results;            
    }

    private String getResponseHeader() {
        String header = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" 
          + "<response\r"
          + " xmlns=\'http://www.eol.org/transfer/content/0.3\'\n"
          + " xmlns:xsd=\'http://www.w3.org/2001/XMLSchema\'\n"
          + " xmlns:dc=\'http://purl.org/dc/elements/1.1/\'\n"
          + " xmlns:dcterms=\'http://purl.org/dc/terms/\'\n"
          + " xmlns:geo=\'http://www.w3.org/2003/01/geo/wgs84_pos#\'\n"
          + " xmlns:dwc=\'http://rs.tdwg.org/dwc/dwcore/\'\n"
          + " xmlns:xsi=\'http://www.w3.org/2001/XMLSchema-instance\'\n"
          + " xsi:schemaLocation=\'http://www.eol.org/transfer/content/0.3 http://services.eol.org/schema/content_0_3.xsd\'>";
        return header;
    }

    private String getResponseFooter() {
        return "\n</response>";
    }

    private void append(OutputStreamWriter outFile, int indent, String tag, String value) throws IOException {
        if (value != null) {
            String indentSpace = "";
            if (indent == 4) indentSpace = "    ";
            if (indent == 6) indentSpace = "      ";
            if (indent == 8) indentSpace = "        ";
            outFile.write("\n" + indentSpace + "<" + tag + ">" + utf(value) + "</" + tag + ">");
        }
    }
/*
    private void addTextObject(OutputStreamWriter outFile, String type, String title, String content
      , String revDate, String author, String taxon) throws IOException {
        if ((content != null) && (content.contains("WordDocument"))) return;
        
        outFile.write("\n    <dataObject>"); 

        append(outFile, 6, "dc:identifier", taxon + "/" + type);        
        append(outFile, 6, "dataType","http://purl.org/dc/dcmitype/Text");        
        append(outFile, 6, "mimeType","text/html");        
        if (author != null) {
            author = maybeMakeCData(author);  // 4 of them do.
            outFile.write("\n      <agent role=\"author\">" + author + "</agent>");
        }
        append(outFile, 6, "dcterms:modified", maybeMakeCData(revDate));        
        append(outFile, 6, "dc:title", title);        
        append(outFile, 6, "dc:language", "en");        
        append(outFile, 6, "license", this.license);        
        if (isNonSPMSubject(type)) {
          append(outFile, 6, "subject", "http://rs.tdwg.org/ontology/voc/SPMInfoItems#Description");        
          append(outFile, 6, "dc:description", "<![CDATA[" + content + "]]>");                
          outFile.write("      <additionalInformation><subject>http://www.eol.org/voc/table_of_contents#" + type + "</subject></additionalInformation>");
        } else {
          append(outFile, 6, "subject", "http://rs.tdwg.org/ontology/voc/SPMInfoItems#" + type);        
          append(outFile, 6, "dc:description", "<![CDATA[" + content + "]]>");        
        }
        outFile.write("\n    </dataObject>"); 
    }
    
    // This won't work here.  Above the fields such as Taxonomy (whaterver that is) have
    // already been chosen to have a type of description.
    
    private boolean isNonSPMSubject(String type) {
      String[] nonSPMSubjects = {"Taxonomy"};
      return Arrays.asList(nonSPMSubjects).contains(type);
    }
*/        
    private void addTextObject(OutputStreamWriter outFile, String type, String title, String content
      , String revDate, String author, String taxon) throws IOException {
        if ((content != null) && (content.contains("WordDocument"))) return;
        
        outFile.write("\n    <dataObject>"); 

        append(outFile, 6, "dc:identifier", taxon + "/" + type);        
        append(outFile, 6, "dataType","http://purl.org/dc/dcmitype/Text");        
        append(outFile, 6, "mimeType","text/html");        
        if (author != null) {
            author = maybeMakeCData(author);  // 4 of them do.
            outFile.write("\n      <agent role=\"author\">" + author + "</agent>");
        }
        append(outFile, 6, "dcterms:modified", maybeMakeCData(revDate));        
        append(outFile, 6, "dc:title", title);        
        append(outFile, 6, "dc:language", "en");        
        append(outFile, 6, "license", this.license);        
        append(outFile, 6, "subject", "http://rs.tdwg.org/ontology/voc/SPMInfoItems#" + type);        
        append(outFile, 6, "dc:description", "<![CDATA[" + content + "]]>");        

        outFile.write("\n    </dataObject>"); 
    }
    
    private String maybeMakeCData(String data) {
        if (data == null) return null;
        if ((data.contains("<")) || (data.contains("nbsp;")) || (data.contains("amp;"))) {
            data = "<![CDATA[" + data + "]]>";
        }
        return data;    
    }
    
    private void addImageObject(OutputStreamWriter outFile, SpecimenImage theImage, String taxonName) 
     throws IOException {
        outFile.write("\n    <dataObject>"); 

        append(outFile, 6, "dc:identifier", theImage.getHighres());
        append(outFile, 6, "dataType", "http://purl.org/dc/dcmitype/StillImage");
        append(outFile, 6, "mimeType", "image/jpeg");
        outFile.write("\n      <agent role=\"photographer\">" + theImage.getArtist() + "</agent>");
        
        append(outFile, 6, "dcterms:created", theImage.getDate());
        append(outFile, 6, "dc:title", taxonName + " (" + theImage.getCode() + ") " + theImage.getShotText());
        append(outFile, 6, "license", this.license);
        append(outFile, 6, "dcterms:rightsHolder", theImage.getCopyright());
        append(outFile, 6, "mediaURL", "http://www.antweb.org" + theImage.getHighres());

        outFile.write("\n    </dataObject>"); 
    }    

    
    private String utf(String text) {
      return new Formatter().convertToUTF8(text);
    }

}




