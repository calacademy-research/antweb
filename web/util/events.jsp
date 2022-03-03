<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.home.*" %>
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

<h2>Events Report</h2>

<p><p><a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br><br>


<%
    ArrayList<Event> events = (ArrayList<Event>) request.getAttribute("events"); 
    HashMap<Integer, Login> curators = (HashMap<Integer, Login>) request.getAttribute("curators"); 

    if (events == null) return;
    
    int count = 0;
    out.println("<table cellpadding=10><tr>");
    //out.println("<th>#</th>");
    out.println("<th>ID</th>");
    out.println("<th>Operation</th>");
    out.println("<th>Curator</th>");
    out.println("<th>Name</th>");
    out.println("<th>Created</th>");
    out.println("</tr>");

    for (Event event : events) {

     if (event != null) {

        ++count;
        //out.println("<tr><td>" + count + ".</td>");

        out.println("<td>" + event.getId() + "</td>");
        out.println("<td>" + event.getOperation() + "</td>");
        Login curator = curators.get(new Integer(event.getCuratorId()));
        if (curator != null) {
          out.println("<td>" + curator.getName() + "</td>");
        } else {
          out.println("<td>null</td>");
        }
        String operation = event.getOperation();
        if (Event.TAXON_PAGE_IMAGES.equals(operation) || Event.TAXON_PAGE_VIDEOS.equals(operation) || Event.TAXON_PAGE_OVERVIEW.equals(operation)) {
        out.println("<td><a href='" + domainApp + "/description.do?taxonName=" + event.getName() + "'>" + event.getName() + "</a></td>");
        } else {
          out.println("<td>" + event.getName() + "</td>");
        }
        out.println("<td>" + event.getCreated() + "</td></tr>");
      }
    }
    out.println("</tr></table>");
    if (count == 0) out.println("<br> No events");
%>

</div > 
