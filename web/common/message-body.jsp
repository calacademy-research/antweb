<%@ page isErrorPage="true" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.*" %>
<%@ page import="java.lang.*" %>
<%@ page import="org.calacademy.antweb.imageUploader.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.upload.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.*" %>

<%@ page import="java.util.regex.Matcher" %>
<%@ page import="java.util.regex.Pattern" %>

<%
    //AntwebUtil.getRandomNumber();


    // Utility.makeDirTree("/usr/local/antweb/web/log/detail/duplicateEntries1.jsp");
    //LogMgr.appendWebLog("detail/testFile.jsp", "Did it!");


    String message = (String) request.getAttribute("message");
   // A.log("-" + message + "-");

    // testMessage will be displayed on the page and in the logs.
    String testMessage = "";

    if (AntwebProps.isDevMode()) {
//      testMessage = "adm1: " + GeolocaleMgr.getAnyAdm1("Enewetak & Ujelang", "Marshall Islands");

    //String url = "https://www.antweb.org/browse.do?subfamily=incertae_sedis&statussetsize=max&statusset=all&statusset=all&statusset=all&caste=default&statusset=all&orderby=status&statusset=all&orderby=taxonname&statusset=all&project=allantwebants&statusset=valid%20extant&orderBy=species";


//    testMessage = HttpUtil.removeParam(url, "statusset");


       //String url = "https://www.antweb.org/browse.do?caste=yay&subfamily=incertae_sedis&caste=default&project=allantwebants&statusset=valid%20extant&orderBy=species&statusset=all";
       //A.log("0. url:" + url);
       //A.log("1. Minus statusset:" + HttpUtil.getTargetMinusParam(url, "statusset"));
       //A.log("2. Minus statusset and caste:" + HttpUtil.getTargetMinusParams(url, "statusset", "caste"));
       //A.log("3. Replace project:" + HttpUtil.getTargetReplaceParam(url, "project", "taxon=dummy"));

       //A.log("1. Minus statusset:" + HttpUtil.removeParam(url, "statusset"));
       //A.log("2. Minus statusset and caste:" + HttpUtil.removeParams(url, "statusset", "caste"));
       //A.log("3. Replace project:" + HttpUtil.getTargetReplaceParam(url, "project", "taxon=dummy"));

       //testMessage = "Testing done";

    // testeMessage = "ImageUploaded val:" + ImageUploaded.getTestString("ZRC_ENT00000092_D.tif");
    // testMessage = "ImageUploaded val:" + ImageUploaded.getTestString("ZRC_ENT00000092_D_2.tif");
    // testMessage = "ImageUploaded val:" + ImageUploaded.getTestString("UFV_LABECOL_000386_P_2.tif");
    // testMessage = "ImageUploaded val:" + ImageUploaded.getTestString("ZRC_6_1520_H.tif");
    // testMessage = "execute() name:" + TaxonMgr.getPrettyTaxaNames(taxonName) + " species:" + species;
    // testMessage = "message-body.jsp andCriteria:" + StatusSet.getAndCriteria(Project.ALLANTWEBANTS));

      A.log("message-body.jsp " + testMessage);
      if (testMessage != null && !"".equals(testMessage)) {
        message += " " + testMessage;
      }
    }

    // Not messages that hold a form (their CSRF token must not go into a log).
    if (message != null && (
    !message.contains("Scheduler run:") && !message.contains(Csrf.PARAM)
    )) {
      LogMgr.appendDataLog("messages.txt", DateUtil.getFormatDateTimeStr(new java.util.Date()) + " - " + message + " " + HttpUtil.getTarget(request));
    }
%>
<%-- Inside the admin layout (curate/adminMessage.jsp; layouts/admLayout.jsp sets awAdminLayout): the tool-page
     look (common/tools.css). In the public layout (common/message.jsp) the page is as it was. --%>
<% if (Boolean.TRUE.equals(request.getAttribute("awAdminLayout"))) { %>
<%@include file="/common/toolsHead.jsp" %>
<div class="aw-tools" id="admin-message-page">
<p class="aw-crumbs"><a href="<%= AntwebProps.getDomainApp() %>/curate.do">Curator Tools</a></p>
<div class="aw-card aw-result" id="admin-message"><%= message %></div>
</div>
<% } else { %>
<div class="wide_left">
<br>
<br>
<b><%= message %></b>
</div>
<% } %>

