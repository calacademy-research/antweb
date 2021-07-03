package org.calacademy.antweb;

import org.calacademy.antweb.util.*;

import java.io.*;
import java.util.*;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class SpecimenXML extends DefaultHandler {

    private static Log s_log = LogFactory.getLog(SpecimenXML.class);

    Hashtable theHash = new Hashtable();
    String currentElement = null;

    public String isInvalid(String xmlString) {
        String isInvalid = null;
        Hashtable description = null;
        try {
            //if (xmlString.contains(" ")) s_log.warn("weird xmlString:" + xmlString);   
            // If a file is saved as UTF - with BOM (in Text Wrangler go to save as and see default)
            // then the string may contain a space but it won't be visible in the logs! Cut and
            // paste from Terminal into Text Wrangler and you will see the space.     
            description = parse(xmlString);
        } catch (org.xml.sax.SAXParseException e) {
            String message = "";
        /*
            if (hasSpaceWithinTags(xmlString)) {
              message = "Is file in UTF? Do not use UTF - With BOM. ";
            }
            if (xmlString.contains(" ")) message = "Is file in UTF? Do not use UTF - With BOM. ";
         */
            s_log.error("isInvalid() " + message + " e:" + e + ". FILE COULD BE SAVED AS UTF8 with BOM?");
            s_log.info("isInvalid() " + message + " e:" + e + " for xmlString:" + xmlString + ". FILE COULD BE SAVED AS UTF8 with BOM?");
            isInvalid = message + e.toString() + ". Could file have been erroneously saved as UTF8 with BOM?";
        } catch (Exception e) {
            String message = "";
            s_log.error("isInvalid() " + message + " e:" + e + " for xmlString:" + xmlString);
            isInvalid = message + e.toString();
        }
        return isInvalid;
    }

    private boolean hasSpaceWithinTags(String xmlString) {
      // This only checks the first space. Probably sufficient as dates will come after the tags most likely to cause trouble: specimenCode.

      boolean hasSpace = false;
      int spaceI = xmlString.indexOf(" ");
      if (spaceI > 0) {
        if (xmlString.indexOf(">", spaceI) < xmlString.indexOf(">")) {
          hasSpace = true;
        }
	    A.log("hasSpaceWithinTags() hasSpace:" + hasSpace);
      }
      return hasSpace;
    }

    public Hashtable getHashtable(String xmlString) {
        Hashtable description = null;
        try {
            description = parse(xmlString);
        } catch (Exception e) {
            s_log.info("getHashtable() e:" + e + " for xmlString:" + xmlString);
        }
        return description;        
    }

    public Hashtable parse(String theXML) throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance(); 
        spf.setValidating(false);
        SAXParser saxParser = spf.newSAXParser(); 
        // create an XML reader
        XMLReader reader = saxParser.getXMLReader();
     
        // FileReader file = new FileReader(filename);
        // set handler
        reader.setContentHandler(this);
        // call parse on an input source
        reader.parse(new InputSource(new StringReader(theXML)));
        return theHash;
    }

    public void startElement(String namespaceURI,
                             String lName, // local name
                             String qName, // qualified name
                             Attributes attrs) throws SAXException
    {
        currentElement = lName; // element name
        if ("".equals(currentElement)) {
          currentElement = qName;
        }
    }

    public void characters(char buf[], int offset, int len) throws SAXException {
      String s = new String(buf, offset, len);
      if (theHash.containsKey(currentElement)) {
        s = (String) theHash.get(currentElement) + s;
      }
      theHash.put(currentElement, s);
    }
      
}

