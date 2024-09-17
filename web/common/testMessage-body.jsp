<%@ page isErrorPage="true" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.*" %>
<%@ page import="java.lang.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.upload.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<%@ page import="java.sql.*" %>
<%@ page import="org.calacademy.antweb.home.*" %>


<div class="left">

<% 
    // Test this way: http://localhost/testMessage.do
%>
<br>
<br>
</div>

<% 
    //Emailer.sendMail("re.mark.johnson@gmail.com", "Consider!", "This");

    out.println("<br>isOnline:" + HttpUtil.isOnline() + "</br>");

    out.println("<br> genus:" + TaxonProxy.getGenus("camponotus") + "<br>");
    out.println("<br> genus:" + TaxonProxy.getGenus("lasius") + "<br>");

    out.println("<br> taxon:" + TaxonProxy.getTaxon("formicinaecamponotus") + "<br>");

    out.println("<br>subfamily:" + TaxonProxy.getSubfamily("Formicinae") + "<br>");

    String o = DateUtil.runTests();
    out.println(o);
/*
  Group group = GroupMgr.getGroup(1);
  MessageMgr messageMgr = new MessageMgr();
  messageMgr.addToMessages(MessageMgr.latLonNotInCountryBounds, "code", "message");  	
  messageMgr.addToMessages(MessageMgr.notValidBioregion, "bullshit"); 
  messageMgr.compileMessages(group);
  String report = messageMgr.getMessagesReport();
  out.println("Report:" + report);
*/
  //Geolocale hawaii = GeolocaleMgr.getCountry("Hawaii");
  //out.println("<br>Hawaii:" + hawaii.toLog());

  //Group group = GroupMgr.getGroup(48);
  //out.println("cleanCode:" + SpecimenUpload.cleanCode("AMNH #2984", "locality", group));

%>
<div class="right">
<%
  //out.println("isValidSubfamily:" + Subfamily.isValidAntSubfamily("(leptanillinae)") + "<br>");

// Or here, if to show up only upon - http://localhost/antweb/util.do?action=testMessage

//Taxon fossilGenus = TaxonMgr.getGenus("agroecomyrmecinaeagroecomyrmex");
//out.println("<br>taxon:" + fossilGenus + " bioregionMap:" + fossilGenus.getBioregionMap());

//out.println("<br>Worker:" + Caste.isWorker("worker"));
//out.println("<br>Worker:" + Caste.isWorker("1 worker "));

//out.println("<br>");

// Because it has a check in the IntroducedMgr - meaning that it is native to Malagasy.
//out.println("WHY?! :" + TaxonPropMgr.isIntroduced("formicinaebrachymyrmex cordemoyi", "Malagasy"));
 

/*
  isValid: < %= GeolocaleMgr.isValid("Saint-Paul", "Reunion") % >
 <br>1isValid (Anzoátegui): < %= GeolocaleMgr.isValid("Anzoátegui", "Venezuela") % >
 <br>2isValid: < %= GeolocaleMgr.isValid("Anzoategui", "Venezuela") % >
 <br>3isValid: < %= Formatter.stripAccents("Anzoátegui") % >
 <br>4isValid: < %= GeolocaleMgr.isValid(Formatter.stripAccents("Anzoátegui"), "Venezuela") % >

< %= Taxon.getPrettyTaxonName("(scoliinae)(scoliinae) mg01") % >
*/
%>


<br><br>
<%
/*
      String taxonName = "(scoliinae)(scoliinae) mg01";

      String prettyTaxonName = taxonName;
      int inaeIndex = taxonName.indexOf("inae");

      if (taxonName.contains("inae)")) {
        prettyTaxonName = taxonName.substring(inaeIndex + 5);
      } else if (taxonName.contains("inae")) {
        prettyTaxonName = taxonName.substring(inaeIndex + 4);
      }
      
      if (taxonName.contains("incertae_sedis")) {
        prettyTaxonName = taxonName.substring(14);
      }

      prettyTaxonName = prettyTaxonName.trim();

      prettyTaxonName = (new Formatter()).capitalizeFirstLetter(prettyTaxonName);
      //out.println("inaeIndex:" + inaeIndex + " prettyTaxonName:" + prettyTaxonName);
*/
%>

<br><br>
</div>


