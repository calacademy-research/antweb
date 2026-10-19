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

<%= ReportHelp.HOMONYMS %>
Current valid names that differ from the homonym's name are in bold. "Name also used by" says whether AntWeb also holds a taxon spelled the same (the senior name).

<br><br><p><p><a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br>
<%
    ArrayList<Homonym> homonyms = (ArrayList<Homonym>) request.getAttribute("homonyms");
    if (homonyms == null) homonyms = new ArrayList<>();

    if (homonyms.isEmpty()) {
      out.println("<p>No homonyms: AntWeb holds no homonym names.</p>");
    } else {
      out.println("<p><b>" + homonyms.size() + "</b> homonyms.</p>");
      out.println("<table cellpadding=10><tr><th>#</th><th>Homonym</th><th>Author Date</th><th>AntCat</th><th>Current Valid Name</th><th>Name also used by</th></tr>");
      int count = 0;
      for (Homonym homonym : homonyms) {
        if (homonym == null) continue;
        ++count;
        String authorDate = homonym.getAuthorDate();
        String link = domainApp + "/description.do?taxonName=" + Html.url(homonym.getTaxonName()) + "&status=homonym"
          + (authorDate == null ? "" : "&authorDate=" + Html.url(authorDate));
        out.println("<tr><td>" + count + ".</td>");
        out.println("<td><a href=\"" + Html.escAttr(link) + "\">" + Html.esc(homonym.getPrettyTaxonName()) + "</a></td>");
        out.println("<td>" + Html.esc(authorDate == null ? "" : authorDate) + "</td>");
        if (homonym.getAntcatId() > 0) {
          out.println("<td><a href='https://www.antcat.org/catalog/" + homonym.getAntcatId() + "'>" + homonym.getAntcatId() + "</a></td>");
        } else {
          out.println("<td></td>");
        }
        String currentValidName = homonym.getCurrentValidName();
        String prettyCurrentValidName = currentValidName == null ? null : new org.calacademy.antweb.Formatter().capitalizeFirstLetter(currentValidName);
        boolean differs = prettyCurrentValidName != null && !prettyCurrentValidName.equals(homonym.getPrettyTaxonName());
        String cv = Html.esc(currentValidName == null ? "" : currentValidName);
        out.println("<td>" + (differs ? "<b>" + cv + "</b>" : cv) + "</td>");
        out.println("<td>" + (homonym.getSeniorHomonym() != null ? "yes" : "no") + "</td></tr>");
      }
      out.println("</table>");
    }
%>

</div >
