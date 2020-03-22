package org.calacademy.antweb.data;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.regex.*;
import java.sql.*;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.cactus.client.connector.http.DefaultHttpClient;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.params.*;
import org.apache.commons.httpclient.HttpMethod.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import java.sql.Statement;
import java.sql.Connection;
//import com.sun.org.apache.xpath.internal.NodeSet;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class Plazi {

    public static final String IDLE = "idle";
    public static final String UPDATE = "update";
    public static final String FETCH = "fetch";
    public static String s_state = IDLE;
    
/**
  *  Plazi data is stored as description_edit records of title:taxonomictreatment and is displayed 
  *  as Taxonomic Treatment on the taxon page.  It is imported via a struts tag as such:
  *  
  *  /getPlazi.do
  *
  *  It relies on the ApplicationResources properties site.plaziFilesURL, site.plaziDescriptionRoot and site.plaziDir.
  *  It will download lots of xml files.  It takes about 15 minutes to run.
  *
  *  Plazi notFoundCount:2401
  *  Saved Plazi Count:3224
  *
  *  Contact: Donat Agosti <agosti@amnh.org>
  *
  */
    private static Log s_log = LogFactory.getLog(Plazi.class);

    private Pattern fileNamePat = Pattern.compile("exist:resource .*name=\"(.*?)\"",Pattern.CASE_INSENSITIVE);

    private String m_plaziFilesURL = AntwebProps.getProp("site.plaziFilesURL");
    private String m_plaziRoot = AntwebProps.getProp("site.plaziDescriptionRoot");
    private String m_plaziDir = AntwebProps.getPlaziDir(); //AntwebProps.getProp("site.plaziDir");
    
// On prod, plazi dir was /data/antweb/plazi/

    

/* Something like:

site.plaziFilesURL=http://plazi.cs.umb.edu/exist/rest/db/taxonx_docs
site.plaziDescriptionRoot=http://plazi.cs.umb.edu/exist/rest/db/taxonx_docs/getSPM.xq?render=xhtml&description=broad&associations=no&doc=
site.plaziDir=/Users/mark/dev/calAcademy/plazi
*/    
    
    public Plazi() {
        s_log.warn("Plazi() Using PlaziRoot:" + m_plaziRoot);
        s_log.warn("Plazi() Writing to PlaziDir:" + m_plaziDir);
    }

    public ArrayList<String> getFileList(int limit) throws IOException {
        
        s_log.warn("getFileList() plaziFilesDir:" + m_plaziFilesURL);
              
        ArrayList<String> results = new ArrayList<String>();
        
        String parsed = null;
        
       // try {
            URL fileUrl = new URL(m_plaziFilesURL);
            BufferedReader in = new BufferedReader(new InputStreamReader(fileUrl.openStream()));
            String inputLine;
            int count = 0;            
            while ((inputLine = in.readLine()) != null) {
                ++count;
                if ((limit > 0) && (count > limit)) break;
                if ((parsed = parseFileName(inputLine)) != null) {
                    results.add(parsed);
                } 
            }
       /*
        } catch (MalformedURLException e) {
            AntwebUtil.logStackTrace(e);
            return null;
        } catch (IOException e) {
            AntwebUtil.logStackTrace(e);
            return null;
        }
        */
        return results;
    }
    
    
                
    public ArrayList<String> save(Connection connection) throws SQLException {
    /* This method is for testing a single insertion of utf8 */
        ArrayList<String> result = new ArrayList<String>();
        
      String delString = " delete from description_edit where taxon_name = \"pseudomyrmecinaetetraponera rufonigra\" and title = \"taxonomictreatment\"";
      String insertString = "insert into description_edit (taxon_name, title, content, is_manual_entry) values ('pseudomyrmecinaetetraponera rufonigra','taxonomictreatment','<p></p><b> <a href=\"http://hdl.handle.net/10199/\" target=\"new\">Dlussky, G. M., 1990</a>: </b><br>Распространен в Южной и Юго- Восточной Азии, включая Зондские о-ва. Указан Ф.Санчи (Santschi, 1920a) для Северно- го Вьетнама (Тонкин, Хоабинь; Ханой). В нашем распоряжении имеется материал из А н - Ф у (Янушев) и Лао-Кая (Захаров), а также из Южного Китая (Юнь-нань, 30 км ю-в Чэли, Панфилов).<br>', 0)";
      
              //try {
                        
/* utf8 UTF8 special characters work   

This (without html commentation) was added to the top of taxonPage.jsp to make it all work.

<!-- %@page contentType="text/html; charset=UTF-8" % -->

Also notice the different database connection type in PlaziAction.java
*/         

                Statement stmt = connection.createStatement();
                
                // These don't seem to help
                // stmt.execute("set names utf8");
                // stmt.execute("set character set utf8"); 

                stmt.executeUpdate(delString);
                stmt.executeUpdate(insertString);
                
                // If this java string is cut/paste from the log file and inserted in mysql, golden.
                // Which implies that the database is good, and the string above is good.
                s_log.warn("save() insertString:" + insertString);

/* Even when the string is manually inserted into mysql, as such:
delete from description_edit where taxon_name = "pseudomyrmecinaetetraponera rufonigra" and title = "taxonomictreatment";

insert into description_edit (taxon_name, title, content, is_manual_entry) values ('pseudomyrmecinaetetraponera rufonigra','taxonomictreatment','<p></p><b> <a href="http://hdl.handle.net/10199/" target="new">Dlussky, G. M., 1990</a>: </b><br>Распространен в Южной и Юго- Восточной Азии, включая Зондские о-ва. Указан Ф.Санчи (Santschi, 1920a) для Северно- го Вьетнама (Тонкин, Хоабинь; Ханой). В нашем распоряжении имеется материал из А н - Ф у (Янушев) и Лао-Кая (Захаров), а также из Южного Китая (Юнь-нань, 30 км ю-в Чэли, Панфилов).<br>', 0);

<p></p><b> <a href="http://hdl.handle.net/10199/" target="new">Dlussky, G. M., 1990</a>: </b><br>Распространен в Южной и Юго- Восточной Азии, включая Зондские о-ва. Указан Ф.Санчи (Santschi, 1920a) для Северно- го Вьетнама (Тонкин, Хоабинь; Ханой). В нашем распоряжении имеется материал из А н - Ф у (Янушев) и Лао-Кая (Захаров), а также из Южного Китая (Юнь-нань, 30 км ю-в Чэли, Панфилов).<br> 
    
select * from description_edit where taxon_name like '%rufonigra' and title = "taxonomictreatment";

...it does not display correctly on the webpage:
See: http://10.2.22.83/description.do?genus=tetraponera&name=rufonigra&rank=species
And: http://plazi.cs.umb.edu/GgServer/html/304E2A0B5F7F623C0164265EB223964D   

Currently, if inserted through mysql it is correct, inserted through this save method on stage it is correct,
  production, unknown.  When retreieved from database correct, but not displayed correctly on the web page.
*/
   
                stmt.close();
              //} catch (Exception e) {
              //     s_log.error("save() problem saving plazi statement " + e);
              //}
              result.add("pseudomyrmecinaetetraponera rufonigra");
              return result;
    }
                      
    public ArrayList<String> save(Connection connection, HashMap<String, ArrayList<PlaziTaxonDescription>> plaziDescs) 
        throws SQLException {
        ArrayList<String> result = new ArrayList<String>();
        /** Creation of taxonomictreatment description_edit records */
        Set<String> taxa = plaziDescs.keySet();
        
        int notFoundCount = 0;

        A.log("save() taxa:" + taxa.size());

        for (String taxon: taxa) {
            A.log("save() taxon:" + taxon + " size:" + plaziDescs.get(taxon).size());
            if (plaziDescs.get(taxon).size() == 0) {
                continue;
            }
            Species thisSpecies = new Species();
            String[] pieces = taxon.toLowerCase().split(" ");
            thisSpecies.setGenus(pieces[0]);
            String species = pieces[1];
            if (pieces.length > 2) {
                species = species + " " + pieces[2];
            }
            thisSpecies.setSpecies(species);  // was setName()
            thisSpecies.setConnection(connection);
            try {
                thisSpecies.setTaxonomicInfo();
            } catch (Exception e) {
                 s_log.error("save() problem setTaxonomicInfo() e:" + e);
            }
            //String thisName = util.makeNameFromTaxon(thisSpecies);
            String taxonName = thisSpecies.getTaxonName();
            
            A.log("save() taxon:" + taxon + " taxonName:" + taxonName);
            
            if ((thisSpecies.getSubfamily() != null) && (thisSpecies.getSubfamily().length() > 0)) {

              String delString = "delete from description_edit where taxon_name='" + taxonName 
                + "' and title='taxonomictreatment'";              
              String insertString = "insert into description_edit (taxon_name, title, content, is_manual_entry) " 
                + "values ('" + taxonName + "','taxonomictreatment','" + makeDescHTML(plaziDescs.get(taxon)) + "', 0)";
              
              if (AntwebProps.isDevOrStageMode()) {
                if (taxonName.contains("rufonigra")) s_log.warn("save() taxon:" + taxon + " taxonName:" + taxonName + " query:" + insertString);
              }
                
              //try {
                Statement stmt = connection.createStatement();
                
                stmt.executeUpdate(delString);
                stmt.executeUpdate(insertString);
                stmt.close();
              //} catch (Exception e) {
              //     s_log.error("save() problem saving plazi statement " + e);
             // }
              result.add(taxon);
            } else {
                s_log.info("save() plazi could not find: "  + taxonName);
                ++ notFoundCount;
            }
            thisSpecies.setConnection(null);
        }
        
        s_log.warn("save() notFoundCount:" + notFoundCount);
        
        return result;
    }
    
    
    public HashMap<String, ArrayList<PlaziTaxonDescription>> getDescribedTaxa(String fileName) 
        throws IOException {
        
        A.log("getDescribedTaxa() file " + fileName);
                
        String fullFileName = m_plaziRoot + fileName;
        String saveFile = m_plaziDir + "/" + fileName;
        Document doc = null;
        String author = "";
        String url = "";
        String date = "";
        
        try {
            boolean success = fetchFile(fullFileName, saveFile);
            if (!success) {
              A.log("getDescribedTaxa() success:" + success + " fileName:" + fileName);
              return null;
            }

            ////DocumentBuilder.parse(new InpputSource(new InputStreamReader(inputStream, "<real encoding>")));
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true); 
            
            DocumentBuilder builder = factory.newDocumentBuilder();
            //builder.setErrorHandler(new SimpleErrorHandler());
            
            File thisFile = new File(saveFile);
            doc = builder.parse(thisFile);
            
            //NodeList authors = doc.getElementsByTagName("authorship");
            //NodeList authors = doc.getElementsByTagNameNS("http://rs.tdwg.org/ontology/voc/PublicationCitation#", "authorship");
            NodeList authors = doc.getElementsByTagNameNS("http://rs.tdwg.org/ontology/voc/PublicationCitation#", "authorship");
            if ((authors.getLength() > 0) && (authors.item(0).hasChildNodes())) {
                author = authors.item(0).getFirstChild().getNodeValue();
            } else {
                author = "Unknown author";
            }
            
            NodeList dates = doc.getElementsByTagNameNS("http://rs.tdwg.org/ontology/voc/PublicationCitation#", "datePublished");
            if ((dates.getLength() > 0) && (dates.item(0).hasChildNodes())) {
                date = dates.item(0).getFirstChild().getNodeValue();
            } 
            if (!date.equals("")) {
                author = author + ", " + date;
            }
            
            NodeList urls = doc.getElementsByTagNameNS("http://rs.tdwg.org/ontology/voc/PublicationCitation#", "url");
            if (urls.getLength() > 0) {
                NamedNodeMap attributes = urls.item(0).getAttributes();
                Node urlNode = attributes.getNamedItemNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "resource");
                if (urlNode != null) {
                    url = attributes.getNamedItemNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "resource").getNodeValue();
                }
            }
            
            if (!url.equals("")) {
                //String[] stuff = url.split("=");
                //url = stuff[1];
                author = "<a href=\"" + url + "\" target=\"new\">" + author + "</a>";
            }
            //s_log.info("author is " + author + " url is " + url);
/*
        } catch (IOException e) {
            s_log.error("getDescribedTaxa() 4 e:" + e);
        } catch (MalformedURLException e) {
            s_log.error("getDescribedTaxa() 1 e:" + e);
*/
        } catch (ParserConfigurationException e) {
            s_log.error("getDescribedTaxa() 2 e:" + e);
        } catch (SAXException e) {
            s_log.error("getDescribedTaxa() 3 e:" + e);
        }
        NamespaceContext ctx = new NamespaceContext() {
            public String getNamespaceURI(String prefix) {
                String uri;
                if (prefix.equals("tpcit"))
                    uri = "http://rs.tdwg.org/ontology/voc/PublicationCitation#";
                else if (prefix.equals("spm"))
                    uri = "http://rs.tdwg.org/ontology/voc/SpeciesProfileModel#";
                else if (prefix.equals("tc"))
                    uri = "http://rs.tdwg.org/ontology/voc/TaxonConcept#";
                else if (prefix.equals("tn"))
                    uri = "http://rs.tdwg.org/ontology/voc/TaxonName#";
                else if (prefix.equals("spmi"))
                    uri ="http://rs.tdwg.org/ontology/voc/SPMInfoItems#";
                else if (prefix.equals("xhtml"))
                    uri= "http://www.w3.org/1999/xhtml";
                else
                    uri = null;
                return uri;
            }
            public Iterator getPrefixes(String val) {
                return null;
            }
           
            public String getPrefix(String uri) {
                return null;
            }
        };
        
        ArrayList<String> species = getTaxon(doc, ctx, "Species");
        ArrayList<String> subspecies = getTaxon(doc, ctx, "Subspecies");
        species.addAll(subspecies);
        HashMap<String, ArrayList<PlaziTaxonDescription>> result = getDescriptions(doc, ctx, species, author );
        
        return result;   
    }
    

    private boolean fetchFile(String fullFileName, String saveFile) 
        throws IOException {     
            boolean success = false;       
            HttpClient client = new HttpClient();
            HttpMethod method = new GetMethod(fullFileName);
            
            DefaultHttpMethodRetryHandler retryhandler = new DefaultHttpMethodRetryHandler(10,true);
            client.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, retryhandler);
            client.getParams().setParameter(HttpMethodParams.SO_TIMEOUT, 120000);
            
            //client.setConnectionTimeout(60000);
            
            try {
                  // Execute the method.
                  int statusCode = client.executeMethod(method);

                  if (statusCode != HttpStatus.SC_OK) {
                    s_log.error("fetchFile() Method failed statusLine: " + method.getStatusLine());
                  }

                  // Read the response body.
                  BufferedReader responseBody = new BufferedReader(new InputStreamReader(method.getResponseBodyAsStream()));

                  // Deal with the response.
                  // Use caution: ensure correct character encoding and is not binary data
                  BufferedWriter out = new BufferedWriter(new FileWriter(saveFile));
                  String inputLine;
                  while ((inputLine = responseBody.readLine()) != null ) {
                        out.write(inputLine);
                  }
                  responseBody.close();
                  out.close();

                  success = true;
/*
            } catch (HttpException e) {
                  s_log.error("fetchFile() Fatal protocol violation: " + e.getMessage());
                  AntwebUtil.errorStackTrace();
*/
            } catch (IOException e) {
                  s_log.error("fetchFile() file:" + fullFileName + " Fatal transport error: " + e.getMessage());
                  throw e;
            } finally {
                  // Release the connection.
                  method.releaseConnection();
            }  
            return success;
    }            
    
    
    private String makeDescHTML(ArrayList<PlaziTaxonDescription> theList) {
        StringBuffer result = new StringBuffer();
        ArrayList<String> dedup = new ArrayList<String>();
        for (PlaziTaxonDescription item : theList) {
            if (!(dedup.contains(item.getDescription()))) {
                result.append("<p></p><b> " + item.getAuthor() + ": </b><br>\n");
                result.append(item.getDescription());
                dedup.add(item.getDescription());
            }
        }
        
        return result.toString().replaceAll("'", "\''");
    }
    
    
    private HashMap<String, ArrayList<PlaziTaxonDescription>> getDescriptions(Document doc, NamespaceContext ctx, ArrayList<String> species, String author) {
        HashMap<String, ArrayList<PlaziTaxonDescription>> result = new HashMap<String, ArrayList<PlaziTaxonDescription>>();
        
        XPathFactory xpathFact =  XPathFactory.newInstance();
        XPath xpath = xpathFact.newXPath();
        xpath.setNamespaceContext(ctx);
        //String res = xpath.evaluate(xpathStr, doc);
        NodeList nodes = null;
        
        for (String thisSpecies : species) {
            StringBuffer description = new StringBuffer();
            String xpathStr =  "//tc:nameString[.=\"" + thisSpecies + "\"]/../../../spm:hasInformation/spmi:Description/spm:hasContent/xhtml:p";
            try {
                nodes = (NodeList) xpath.evaluate(xpathStr, doc, XPathConstants.NODESET);
            } catch (XPathExpressionException e) {
                s_log.error("getDescriptions() problem getting species:" + thisSpecies);
                //AntwebUtil.errorStackTrace();
            }
            
            int listLen = nodes.getLength();
            
            for (int loop=0; loop < listLen; loop++) {
                Node node = nodes.item(loop);
                if (node == null) {
                  s_log.warn("getDescriptions() Node is null in loop:" + loop);                
                  description.append("<br>\n");
                } else {
                  Node childNode = node.getFirstChild();
                  if (childNode == null) {
                    s_log.warn("getDescriptions() ChildNode is null for node:" + node);                                
                    description.append("<br>\n");
                  } else {
                    String childNodeValue = childNode.getNodeValue();
                    if (childNodeValue == null) {
                      s_log.warn("getDescriptions() value is null for childNode:" + childNode);                
                      description.append("<br>\n");
                    } else {
                      description.append(childNodeValue + "<br>\n");
                    }
                  }
                }
            }
            PlaziTaxonDescription thisDesc = new PlaziTaxonDescription();
            thisDesc.setAuthor(author);
            thisDesc.setTaxonName(thisSpecies);
            thisDesc.setDescription(description.toString());
            if(result.containsKey(thisSpecies)) {
                (result.get(thisSpecies)).add(thisDesc);
            } else {
                ArrayList<PlaziTaxonDescription> newOne = new ArrayList<PlaziTaxonDescription>();
                newOne.add(thisDesc);
                result.put(thisSpecies, newOne);
            }
        }
        return result;
    }

      //<spm:hasInformation> 
      //<spmi:Description xmlns:spmi="http://rs.tdwg.org/ontology/voc/SPMInfoItems#" rdf:ID="_Description_1_1"> 
      //    <spm:hasContent 
    private ArrayList<String> getTaxon(Document doc, NamespaceContext ctx, String rank) {
        
        ArrayList<String> result = new ArrayList<String>();
        String xpathStr =  "//tc:hasName/*/tn:rankString[.=\"" + rank + "\"]/../../../tc:nameString";
        XPathFactory xpathFact =  XPathFactory.newInstance();
        XPath xpath = xpathFact.newXPath();
        xpath.setNamespaceContext(ctx);
        //String res = xpath.evaluate(xpathStr, doc);
        NodeList nodes = null;
        try {
            nodes = (NodeList) xpath.evaluate(xpathStr, doc, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            s_log.error("getTaxon() problem getting taxon of rank:" + rank);
            //AntwebUtil.logStackTrace(e);
        }
        
        if (nodes != null) {
            int listLen = nodes.getLength();
            for (int loop=0; loop < listLen; loop++) {
                result.add(nodes.item(loop).getFirstChild().getNodeValue());
            }
        }
        return result;
    }
    
    private String parseFileName(String inputLine) {
        String result = null;
        Matcher m = fileNamePat.matcher(inputLine);
        if (m.find()) {
            result = m.group(1);
        }
        return result;
    }

    private void print(Node node, PrintStream out) {
        int type = node.getNodeType();
        
        switch (type) {
          case Node.ELEMENT_NODE:
            out.print("<" + node.getNodeName());
            NamedNodeMap attrs = node.getAttributes();
            int len = attrs.getLength();
            for (int i=0; i<len; i++) {
                Attr attr = (Attr)attrs.item(i);
                out.print(" " + attr.getNodeName() + "=\"" +
                          escapeXML(attr.getNodeValue()) + "\"");
            }
            out.print('>');
            NodeList children = node.getChildNodes();
            len = children.getLength();
            for (int i=0; i<len; i++)
              print(children.item(i), out);
            out.print("</" + node.getNodeName() + ">");
            break;
          case Node.ENTITY_REFERENCE_NODE:
            out.print("&" + node.getNodeName() + ";");
            break;
          case Node.CDATA_SECTION_NODE:
            out.print("<![CDATA[" + node.getNodeValue() + "]]>");
            break;
          case Node.TEXT_NODE:
            out.print(escapeXML(node.getNodeValue()));
            break;
          case Node.PROCESSING_INSTRUCTION_NODE:
            out.print("<?" + node.getNodeName());
            String data = node.getNodeValue();
            if (data!=null && data.length()>0)
               out.print(" " + data);
            out.println("?>");
            break;
        }
      }
    
    private String escapeXML(String s) {
        StringBuffer str = new StringBuffer();
        int len = (s != null) ? s.length() : 0;
        for (int i=0; i<len; i++) {
           char ch = s.charAt(i);
           switch (ch) {
           case '<': str.append("&lt;"); break;
           case '>': str.append("&gt;"); break;
           case '&': str.append("&amp;"); break;
           case '"': str.append("&quot;"); break;
           case '\'': str.append("&apos;"); break;
           default: str.append(ch);
         }
        }
        return str.toString();
      }
    
}
