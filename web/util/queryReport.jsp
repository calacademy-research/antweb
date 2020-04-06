<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.home.QueryReport" %>

<%@ page import="java.util.*" %>

<% 
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
    String domainApp = AntwebProps.getDomainApp();
%>

<%@include file="/common/antweb-defs.jsp" %>

<div class=left>

<a href = "<%= domainApp %>">Home</a> | <a href = "<%= domainApp %>/curate.do">Curator Tools</a><br><br>

<%
    QueryReport queryReport = (QueryReport) request.getAttribute("queryReport");
    int count = 0;

    out.println("<h2><b>Query Report:</b> " + queryReport.getName() + "</h2>");

    out.println("<br><b>Desc:</b> " + queryReport.getDesc());

    out.println("<br><br><b>Query:</b> " + queryReport.getQuery());
 
    if (queryReport.getSubquery() != null) {
      out.println("<br><br><b>Subquery:</b> " + queryReport.getSubquery());
    }

    if (queryReport.getError() != null) {
      out.println("<br><br><b>" + queryReport.getError() + "</b>");
      
    } else {
		out.println("<br><br><table>");
		out.println(queryReport.getHeading());
	
		ArrayList<String> list = queryReport.getList();
		for (String listItem : list) {
		  ++count;
		  if (listItem != null && listItem.contains("<hr>")) count = 0;
		  out.println(listItem);
		}

		out.println("</table>");
        out.println("<br><br>Count:" + count + "</div>"); 
    }
%>
