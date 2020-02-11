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

    ArrayList<String> list = (ArrayList) request.getAttribute("listTable"); 
    int count = 0;
    for (String listItem : list) {
      ++count;
      if (listItem != null && listItem.contains("<hr>")) count = 0;
      out.println(listItem);
    }

    out.println("</table>");
%>

<br><br>
Count:<%= count %>
</div > 
