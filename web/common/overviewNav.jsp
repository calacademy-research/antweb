
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.util.*" %>


<% 
  String uri = null;

  try {

    uri = request.getRequestURI();

    //if (AntwebProps.isDevMode()) AntwebUtil.log("siteNav.jsp project:" + project);
    org.calacademy.antweb.Formatter format = new org.calacademy.antweb.Formatter();

    Utility util = new Utility();
    String domainApp = util.getDomainApp();

%>

<%@ include file="menuBar.jsp" %>


<div id="page_context">
    <div id="view">
        <div id="current_view">Current View: Region - Subregion - Country
        </div>
    </div>
    <div class="clear"></div>
        
<%
        
    if (AntwebProps.isDevMode()) AntwebUtil.log("siteNav.jsp requestURI:" + uri);


  } catch (Exception e) {
    AntwebUtil.log("siteNav.jsp e:" + e + " trapped on target:" + HttpUtil.getTarget(request));
    if (AntwebProps.isDevMode()) AntwebUtil.logStackTrace(e);
  }

%>