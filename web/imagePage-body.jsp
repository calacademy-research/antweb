<%@ page import="org.calacademy.antweb.Formatter" %>

<%@ page import="java.util.ResourceBundle" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.Taxon" %>
<%@ page import="org.calacademy.antweb.*" %>

<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%
  Taxon taxon = (Taxon) session.getAttribute("taxon");
  if (taxon == null) return;
  
  Specimen specimen = (Specimen) session.getAttribute("specimen");

  if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;

    String the_page = HttpUtil.getTarget(request);
    String thumb_choice= new String();
    String head = "h";
    String cookieName = "thumbs";
    Cookie cookies [] = request.getCookies ();
    Cookie myCookie = null;
    if (cookies != null) {
        for (int i = 0; i < cookies.length; i++) {
            if (cookies [i].getName().equals (cookieName)) {
                myCookie = cookies[i];
                break;
            }
        }
    }
    if (myCookie == null) {
        thumb_choice = head;
    } else {
        thumb_choice = myCookie.getValue();
    }
      
    Overview overview = OverviewMgr.getOverview(request);  
          
    boolean isMuseum = overview instanceof Museum;
    //  boolean isGeolocale = overview instanceof Geolocale;
    boolean isProject = overview instanceof Project;
    //  boolean isBioregion = overview instanceof Bioregion;

    String pageRank = taxon.getRank();

    boolean displayGlobal = !isProject && (Rank.SUBSPECIES.equals(pageRank) || Rank.SPECIES.equals(pageRank) || Rank.SPECIMEN.equals(pageRank)); // was: isGeolocale && 
    //A.log("imagePage-body.jsp displayGlobal:" + displayGlobal + " pageRank:" + pageRank);
    //A.log("imagePage-body.jsp isMuseum:" + isMuseum + " str:" + HttpUtil.getParamsString("museumCode", request) + " overview:" + overview.getClass());


    String global = request.getParameter("global");
    boolean isGlobal = "true".equals(global);            
          
    Hashtable desc = taxon.getDescription();   
   
    String guiDefaultContent = AntwebProps.guiDefaultContent;

    java.util.Calendar today = java.util.Calendar.getInstance();
    int year = today.get(java.util.Calendar.YEAR);
  
    //String thisPageTarget = java.net.URLEncoder.encode("description.do?" + taxon.getBrowserParams());  
    String thisPageTarget = "description.do?" + taxon.getBrowserParams();
    String editField = request.getParameter("editField");
    if (editField == null) editField = "none";

    //Login accessLogin = LoginMgr.getAccessLogin(request);
    //Group accessGroup = GroupMgr.getAccessGroup(request);
    
    String dagger = "";
    if (taxon.getIsFossil()) dagger = "&dagger;";

    String object = "taxonName";
    String objectName = taxon.getTaxonName();
%> 

    <input id="for_print" type="text" value="AntWeb. Available from: <%= HttpUtil.getFullJspTarget(request)%>">
    <input id="for_web" type="text" value="URL: <%= HttpUtil.getFullJspTarget(request)%>">
    <div id="page_contents">
    <%@ include file="/common/taxonTitle.jsp" %>

        <div class="links">
            <ul>
                <li><a class="clean_url overview" href="<%= AntwebProps.getDomainApp() %>/description.do?<%=taxon.getBrowserParams()%>">Overview</a></li>
                <li><a class="clean_url list" href="<%= AntwebProps.getDomainApp() %>/browse.do?<%=taxon.getBrowserParams()%>"><%= Rank.getNextPluralRank(taxon.getRank()) %></a></li>
                <li>Images</li>
   <% 
   //A.log("imagePage-body.jsp hasMap:" + taxon.hasMap() + " map:" + taxon.getMap());   
            if (taxon.hasMap()) { %>
                <li><a class="clean_url map" href="<%= AntwebProps.getDomainApp() %>/bigMap.do?<%= object %>=<%= objectName %>&<%= overview.getParams() %>">Map</a></li>
         <% } %>
            </ul>
        </div>
        <%@ include file="/common/viewInAntCat.jsp" %>

        <div class="clear"></div>

 <% 
// AntwebUtil.log("***** rank:" + taxon.getRank());
 String rank = taxon.getRank();
 if (rank != null && (rank.equals(Rank.SPECIES) || rank.equals(Rank.SUBSPECIES))) { %>
      <% // @ include file="/curate/defaultSpecimen/defaultSpecimen.jsp" %>
 <% } %>
  
   
        <div class="clear"></div>
    </div>
    <!-- 
        taxonName:<%= taxon.getTaxonName() %> 
        details:<%= taxon.getDetails() %>        
        simpleName:<%= taxon.getSimpleName() %>
        prettyName:<%= taxon.getPrettyName() %>
        fullName:<%= taxon.getFullName() %>
        name:<%= taxon.getName() %>
        execTime:<%= taxon.getExecTime() %>
    -->
<%@ include file="/common/taxonomicHierarchy.jsp" %>

<%
  if (taxon.getRank().equals(Rank.SPECIES) || taxon.getRank().equals(Rank.SUBSPECIES)) {
    String defaultSpecimenTaxon = (String) session.getAttribute("defaultSpecimenTaxon");
    //if (defaultSpecimenTaxon != null) { %> 
        <!-- %@ include file="/curate/defaultSpecimen/defaultSpecimenSelectPanel.jsp" % -->
 <% //} else {
      String message = (String) session.getAttribute("message");
      if (message != null) { 
        session.setAttribute("message", null);  
    %> 
<div id="page_data">
<%= message %>
<hr>
</div>
   <% } %>
<% } 
  //A.log("imagePage-body.jsp taxon:" + taxon);
  
%>
  
<%@include file="/taxonChildImages.jsp" %>  

<div class="clear"></div>
<br /><br />
<a href="mailto:antweb@calacademy.org?subject=Regarding AntWeb page <%= the_page %>">See something amiss? Send us an email.</a>

