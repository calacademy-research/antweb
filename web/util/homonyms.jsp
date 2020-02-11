<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>

<%@ page import="java.util.*" %>

<%@include file="/curate/curatorCheck.jsp" %>

<% 
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
    String domainApp = AntwebProps.getDomainApp();    
 %>

<%@include file="/common/antweb-defs.jsp" %>

<div class=left>

<h2>Homonym Report</h2>

Antweb homonyms are listed below.  
<br>Homonyms that have a matching taxon (with the same taxon name) are indicated in Matching Taxon column.  
<br>Current valid names that are distinct from the homonym name are in bold.

<br><br><p><p><a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>
<%
    ArrayList<Homonym> homonyms = (ArrayList<Homonym>) request.getAttribute("homonyms"); 
    if (homonyms == null) return;
    
    int count = 0;
    out.println("<table cellpadding=10><tr><th>#</th>");
    out.println("<th>Homonym</th>");
    out.println("<th>AntCat</th>");
    out.println("<th>Current Valid Name</th>");
    out.println("<th>Matching Taxon</th>");
    out.println("</tr>");
    for (Homonym homonym : homonyms) {
      ++count;
      out.println("<tr><td>" + count + ".</td>");
      out.println("<td><a href=\"" + domainApp + "/description.do?taxonName=" + homonym.getTaxonName() + "&status=homonym\">" + homonym.getPrettyTaxonName() + "</a></td>");
      out.println("<td><a href='http://www.antcat.org/catalog/" + homonym.getAntcatId() + "'>" + homonym.getAntcatId() + "</a></td>");
      boolean currentValidMatch = true;
      String prettyCurrentValidName = new org.calacademy.antweb.Formatter().capitalizeFirstLetter(homonym.getCurrentValidName());
      if (!homonym.getPrettyTaxonName().equals(prettyCurrentValidName)) {
        currentValidMatch = false;
      }
      if (currentValidMatch) {
        //if (AntwebProps.isDevMode()) AntwebUtil.log("homonym.jsp 1:" + homonym.getPrettyTaxonName() + " != " + prettyCurrentValidName);
        out.println("<td>" + homonym.getCurrentValidName() + "</td>");
      } else {
        out.println("<td><b>" + homonym.getCurrentValidName() + "</b></td>");
      }
      out.println("<td>" + (homonym.getSeniorHomonym() != null) + "</td>");
    }
    out.println("</tr></table>");
    if (count == 0) out.println("<br> No uploads for this group");

%>

</div > 
