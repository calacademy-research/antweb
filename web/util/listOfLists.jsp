<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.util.*" %>

<%@ page import="java.util.*" %>

<% 
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
    String domainApp = AntwebProps.getDomainApp();
%>

<%@include file="/common/antweb-defs.jsp" %>

<div class=left>

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br>

<%
    String message = (String) request.getAttribute("message"); 
    if (message != null) out.println("<h2>" + message + "</h2>");

    out.println("<table>");

    ArrayList<ArrayList<String>> listOfLists = (ArrayList<ArrayList<String>>) request.getAttribute("listOfLists"); 
    int i = 0;
    for (ArrayList<String> list : listOfLists) {
      ++i;
      out.println("<tr>");
      for (String item : list) {
        String tag = "th";        
        if (i > 1) tag = "td";
        out.println("<" + tag + ">" + item + "</" + tag + ">");
      }
      out.println("</tr>");
    }

    if (i == 1) {
      out.println("<tr><td>No Data</td><td></td><td></td></tr>");
    }

    out.println("</table>");
%>

<br><br>
Count:<%= i - 1 %>
</div > 
