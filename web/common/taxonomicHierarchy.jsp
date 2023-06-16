<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.io.File" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.util.*" %>

<!-- taxonomicHierarchy -->
<%
  // overview should be defined by the including jsp

  org.calacademy.antweb.Formatter format = new org.calacademy.antweb.Formatter();

  Utility util = new Utility();
  String domainApp = util.getDomainApp();

  String requestURI = request.getRequestURI();

  String slash = "/";
  
  String facet = HttpUtil.getFacet(request);
 
  String strutsTarget = facet;
  String otherViewTarget = facet;
  
   if ((requestURI.indexOf("index.jsp") != -1) || (request.getRequestURI().indexOf(".jsp") == -1)) { %>

<% } else {  %>

<%
	String descUrl = null;
	String otherProjectUrl = null;
	String otherViewUrl = null;
	String targetName = null;
	String targetUrl = null;
       
    if (taxon != null) {
    //if (AntwebProps.isDevMode()) AntwebUtil.log("warn", "taxonomicHierarchy.jsp subspecies:" + taxon.getSubspecies() + " rank:" + taxon.getRank() + " requestURI:" + requestURI + " queryString:" + queryString);

%>

    <div id="classification">        
        <span class="left">Classification:</span>
        <ul>
        <% if (taxon.getOrderName() != null) { %>
            <li>Order: <%= format.capitalizeFirstLetter(taxon.getOrderName()) %></li>
        <% }

        if ((taxon.getFamily() != null) && (taxon.getFamily().length() > 0)) {
            String  browserParams = taxon.getBrowserParams(Rank.FAMILY, overview);
            descUrl = domainApp + facet + "?" + browserParams;
            otherProjectUrl = domainApp + "/" + strutsTarget + "?" + browserParams;
            otherViewUrl = domainApp + "/" + otherViewTarget + "?" + browserParams;
            targetName = format.capitalizeFirstLetter(taxon.getFamily());
            if (Rank.FAMILY.equals(taxon.getRank())) targetUrl = otherViewUrl; else targetUrl = descUrl;
            if (taxon.isAnt()) out.println("<li>Family: <a href=\"" + targetUrl + "\">" + targetName + "</a></li>"); 
              else out.println("<li>Family:" + targetName + "</li>");
            //if (AntwebProps.isDevMode()) AntwebUtil.log("taxonomicHierarchy.jsp family targetName:" + targetName + " taxonClass:" + taxon.getClass());              
        }        
        if ((taxon.getSubfamily() != null) && (taxon.getSubfamily().length() > 0)) {
            String  browserParams = taxon.getBrowserParams(Rank.SUBFAMILY, overview);
            descUrl = domainApp + facet + "?" + browserParams;
            otherProjectUrl = domainApp + "/" + strutsTarget + "?" + browserParams;
            otherViewUrl = domainApp + "/" + otherViewTarget + "?" + browserParams;
            targetName = format.capitalizeFirstLetter(taxon.getSubfamily());
            //A.log("taxonomicHierarchy.jsp 1 targetName:" + targetName + " rank:" + taxon.getRank());
            if (Rank.SUBFAMILY.equals(taxon.getRank())) targetUrl = otherViewUrl; else targetUrl = descUrl;
            if (taxon.isAnt()) out.println("<li>Subfamily: <a href=\"" + targetUrl + "\">" + targetName + "</a></li>"); 
              else out.println("<li>Subfamily: " + targetName + "</li>");
            //if (AntwebProps.isDevMode()) AntwebUtil.log("taxonomicHierarchy.jsp isAnt:" + taxon.isAnt() + " subfamily targetName:" + targetName + " taxonClass:" + taxon.getClass());
        }
        if ((taxon.getGenus() != null) && (taxon.getGenus().length() > 0)) {
            String  browserParams = taxon.getBrowserParams(Rank.GENUS, overview);
            //AntwebUtil.log("taxonomicHierarchy.jsp project:" + project + " browseParams:" + browserParams);
            descUrl = domainApp + facet + "?" + browserParams;
            otherProjectUrl = domainApp + "/" + strutsTarget + "?" + browserParams;
            otherViewUrl = domainApp + "/" + otherViewTarget + "?" + browserParams;
            targetName = format.capitalizeFirstLetter(taxon.getGenus());
            if (Rank.GENUS.equals(taxon.getRank())) targetUrl = otherViewUrl; else targetUrl = descUrl;
            if (taxon.isAnt()) out.println("<li>Genus: <a href=\"" + targetUrl + "\">" + targetName + "</a></li>"); 
              else out.println("<li>Genus: " + targetName + "</li>");
            //if (AntwebProps.isDevMode()) AntwebUtil.log("taxonomicHierarchy.jsp genus targetName:" + targetName + " taxonClass:" + taxon.getClass());
        }
        if ((taxon.getSpecies() != null) && (taxon.getSpecies().length() > 0)) {
            String  browserParams = taxon.getBrowserParams(Rank.SPECIES, overview);
            descUrl = domainApp + facet + "?species=" + taxon.getSpecies() + "&genus=" + taxon.getGenus() + "&rank=species";
            otherProjectUrl = domainApp + "/" + strutsTarget + "?" + browserParams;
            otherViewUrl = domainApp + "/" + otherViewTarget + "?" + browserParams;
            targetName = taxon.getSpecies(); // no capitalization
            if (Rank.SPECIES.equals(taxon.getRank())) targetUrl = otherViewUrl; else targetUrl = descUrl;
            //if (AntwebProps.isDevMode()) AntwebUtil.log("taxonomicHierarchy.jsp targetUrl:" + java.net.URLEncoder.encode(taxon.getSpecies()) + " taxonClass:" + taxon.getClass());
            out.println("<li>Species: <a href=\"" + targetUrl + "\">" + targetName + "</a></li>"); 
        }        

        if ((taxon.getSubspecies() != null) && (taxon.getSubspecies().length() > 0)) {
            String browserParams = taxon.getBrowserParams(Rank.SUBSPECIES, overview);
            descUrl = domainApp + facet + "?" + browserParams;
            otherProjectUrl = domainApp + "/" + strutsTarget + "?" + browserParams;
            otherViewUrl = domainApp + "/" + otherViewTarget + "?" + browserParams;
            targetName = taxon.getSubspecies(); // no capitalization
            if (Rank.SUBSPECIES.equals(taxon.getRank())) targetUrl = otherViewUrl; else targetUrl = descUrl;
            out.println("<li>Subspecies: <a href=\"" + targetUrl + "\">" + targetName + "</a></li>"); 
        }            
            
        if ( Rank.SPECIMEN.equals(taxon.getRank()) ) {
            if (request.getRequestURI().contains("specimen.jsp"))  {
               targetUrl = "specimen.do"; 
            } else {
               targetUrl = "specimenImages.do"; 
            }
            targetUrl = domainApp + "/" + targetUrl;
            //A.log("targetUrl:" + targetUrl);
            if (specimen != null && specimen.getName() != null) {
              out.println("<li>Specimen: <a href=\"" + targetUrl +"?code=" + specimen.getName() + "\">" + specimen.getName().toUpperCase() + "</a></li>"); 
            }
        }
%>
        </ul>
        <div class="clear"></div>
    </div>
<%
    }    
}    
%>
