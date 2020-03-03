<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%@ page import="java.util.*" %>

<%@ page import = "org.calacademy.antweb.util.AntwebProps" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.search.*" %>
<%@ page import="org.calacademy.antweb.search.FieldGuide" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>

<jsp:useBean id="taxon" scope="session" class="org.calacademy.antweb.Taxon" />
<jsp:useBean id="showTaxon" scope="session" class="org.calacademy.antweb.Taxon" />
<jsp:setProperty name="taxon" property="*" />
<jsp:useBean id="specimen" scope="session" class="org.calacademy.antweb.Specimen" />
<jsp:setProperty name="specimen" property="*" />

<%
   if (AntwebProps.isDevMode()) {
     if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;
   }
   Overview overview = OverviewMgr.getOverview(request); 
    
   Utility fg_util = new Utility(); 

   FieldGuide fieldGuide = (FieldGuide) request.getAttribute("fieldGuide");    
   if (fieldGuide == null) {
       AntwebUtil.log("fieldGuide-body.jsp fieldGuide is null for request:" + HttpUtil.getRequestInfo(request));     
       return;
   }  
   String fgTitle =  fieldGuide.getTitle(); // Sometimes this is null.  After a server restart.  Why?   
   //String title = (String) request.getAttribute("fieldGuideTitle");
   // ArrayList<Taxon> fieldGuideTaxa = (ArrayList<Taxon>) request.getAttribute("fieldGuideTaxa");

   String requestUri = HttpUtil.getQueryString(request);
   boolean from_search = false;
   String search_title = "";
   if (requestUri == "") {
       from_search = true;
       search_title = "Field Guide of Selected Results";
   }
   boolean region_fg = false;
   if (!requestUri.contains("pr=") && requestUri.contains("rank")) {
       region_fg = true;
   }
   String dagger = "";
   if (showTaxon.getIsFossil()) dagger = "&dagger;";
   org.calacademy.antweb.Map map_link = taxon.getMap();
   String taxon_object = "taxonName";
   String taxon_objectName = showTaxon.getTaxonName();
   String rank = request.getParameter("rank");

   if (from_search) {
%>
<div id="page_contents">
  <h1><%= search_title %></h1>
  <span class="right"><b><a href="#" onclick="history.back(); return false;">Back</a></b></span>
  <div class="clear"></div>
  <div class="page_divider"></div>
</div>
<!-- /div -->
<%
   } else if (region_fg) {
%>
<div id="page_contents">
  <h1>Field Guide <%= fgTitle %></h1>
  <span class="right"><b><a href="#" onclick="history.back(); return false;">Back</a></b></span>
  <div class="clear"></div>
  <div class="page_divider"></div>
</div>

<%
   } else {
%>
    <div id="page_contents">
    <% if (showTaxon.getRank() != null) { %>
      <h1>
      <%= new Formatter().capitalizeFirstLetter(showTaxon.getRank()) %>: <%= dagger %><bean:write name="showTaxon" property="prettyName" />
      <% if (showTaxon.getIsValid()) { %>
        <img src="<%= AntwebProps.getDomainApp() %>/image/valid_name.png" border="0" title="Valid Name">
      <% } %>
      </h1>
    <% } %>

    <%  if (AntwebProps.isDevMode()) AntwebUtil.log("fieldGuide-body.jsp rank:" + showTaxon.getRank() + " from_search:" + from_search + " region_fg:" + region_fg);

        String link_params = "";
        String taxonRank = taxon.getRank();

        if (Rank.SUBFAMILY.equals(taxonRank)) {
            link_params = "subfamily=" + taxon.getSubfamily() + "&name=" + taxon.getSubfamily() + "&rank=subfamily&" + overview.getParams();
        } else if (Rank.GENUS.equals(taxonRank)) {
            link_params = "subfamily=" + taxon.getSubfamily() + "&genus=" + taxon.getGenus() + "&name=" + taxon.getGenus() + "&rank=genus&" + overview.getParams();
        } else if (Rank.SPECIES.equals(taxonRank)) {
            link_params = "subfamily=" + taxon.getSubfamily() + "&genus=" + taxon.getGenus() + "&name=" + taxon.getName() + "&rank=species&" + overview.getParams();
        } else if (Rank.SPECIMEN.equals(taxonRank)) {
            link_params = "name=" + specimen.getName().toLowerCase();
        } else {
            link_params = "name=" + taxon.getName() + "&rank=" + taxon.getRank() + "&" + overview.getParams();
        }
    %>

        <div class="links">
            <ul>
                <li><a class="clean_url overview" href="<%= AntwebProps.getDomainApp() %>/description.do?<%=link_params%>">Overview</a></li>
                <li><a class="clean_url list" href="<%= AntwebProps.getDomainApp() %>/browse.do?<%=link_params%>"><%= Rank.getNextPluralRank(rank) %></a></li>
                <li><a class="clean_url images" href="<%= AntwebProps.getDomainApp() %>/images.do?<%=link_params%>">Images</a></li>
            </ul>
        </div>

        <%@ include file="/common/viewInAntCat.jsp" %>

        <div class="clear"></div>
        <!-- /div -->

    <%@ include file="/common/taxonomicHierarchy.jsp" %>

        <div id="totals_and_tools_container">
            <div id="totals_and_tools">
                <h2>Field Guide <%= fgTitle %></h2>
                <div id="thumb_toggle">
               <% A.log("fieldGuide-body.jsp taxon:" + taxon); %>
                    <a href="<%= request.getHeader("Referer") %>">Back to <%= new Formatter().capitalizeFirstLetter(taxon.getRank()) %> 
                        <%= taxon.getTaxonNameDisplay() %>
                    </a>
                </div>
            <div class="clear"></div>
            </div>
        </div>
        <div class="clear"></div>
    </div>
<%
   }
%>
<div id="page_data">

   <%@include file="/maps/googleMapPreInclude.jsp" %>  

<% 
   // To avoid ConcurrentModificationException
   Taxon[] fieldGuideTaxaArray = fieldGuide.getTaxaArray();

   Integer fieldGuideTaxaHashCode = (Integer) request.getAttribute("fieldGuideTaxaHashCode");
   if ((fieldGuideTaxaHashCode != null) && (!fieldGuideTaxaHashCode.equals(fieldGuide.getTaxa().hashCode()))) {
      /* 
         This is a known issue.  It happens on the first request of a field guide after server starts.
         Very strange.  FieldGuide objectId is consistent, but the taxa within are different.  HashCode has changed and set is empty.
         Subsequent call usually seems to work fine.  This could be related to ConcurrentModificationExceptions which
         occasionally happen on subsequent calls.
      */
      //AntwebUtil.log("fieldGuide-body.jsp Incorrect fieldGuide taxa.  size:" + fieldGuideTaxaArray.size() + " request:" + request + " fieldGuide:" + fieldGuide + " theTaxa:" + fieldGuide.getTaxa() + " hashCode:" + fieldGuideTaxaArray.hashCode());
 %>
      <h2><Font color=red>Apologies, known issue - please attempt Reload!</font></h2>
 <%  
   }

    ArrayList<Taxon> fieldGuideArrayList = new ArrayList(Arrays.asList(fieldGuideTaxaArray));

    String orderBy = (String) request.getParameter("orderBy");
    if (orderBy != null) {
      A.log("fieldGuide-body.jsp sorting...");
      Taxon.sortTaxa(orderBy, fieldGuideArrayList);
    }

    int i = 0;
    for (Taxon fgTaxon : fieldGuideArrayList) {
        ++i;
        if (fgTaxon == null) {
           AntwebUtil.log("fieldGuide-body.jsp no taxon");
           return;
        }

     %>
        <div class="clear"></div>
        <div class="page_spacer"></div>
        <h3><%= (new Formatter()).capitalizeFirstLetter(fgTaxon.getRank()) %>: <a href="<%= AntwebProps.getDomainApp() %>/description.do?taxonName=<%= fgTaxon.getTaxonName() %>"><%= fgTaxon.getPrettyName() %></a></h3>
    <%
        //if (AntwebProps.isDevMode()) if (map.getGoogleMapFunction() == null) AntwebUtil.log("fieldGuide-body.jsp taxonName:" + fgTaxon.getTaxonName());

        String object = "thirds"; // "taxon";
        String objectName = fgTaxon.getTaxonName();

    //if (AntwebProps.isDevMode()) AntwebUtil.log("fieldGuide-body.jsp taxon:" + fgTaxon.getTaxonName() + " images:" + fgTaxon.getImages());

        if (fgTaxon.getImages() == null) {
           //if (AntwebProps.isDevMode()) AntwebUtil.log("fieldGuide-body.jsp no taxon images for taxon:" + fgTaxon.getTaxonName() );
        } else {
            String shot = "h";
            SpecimenImage image = (org.calacademy.antweb.SpecimenImage) fgTaxon.getImages().get(shot);
            if (image != null) {
    %>
                <div class="slide thirds first" style="background-image:url('<%= AntwebProps.getImgDomainApp() %><%= image.getThumbview() %>');">
                <div class="hover thirds" onclick="window.location='<%= AntwebProps.getDomainApp() %>/bigPicture.do?name=<%= image.getCode() %>&shot=<%= shot %>&number=1';"></div>
                </div>
    <%      } else {
                //if (AntwebProps.isDevMode()) AntwebUtil.log("info" ,"fieldGuide-body.jsp image null for taxon:" + taxon.getTaxonName() + " shot:" + shot);
            }
            shot = "p";
            image = (org.calacademy.antweb.SpecimenImage) fgTaxon.getImages().get(shot);
            if (image != null) {
    %>
                <div class="slide thirds" style="background-image:url('<%= AntwebProps.getImgDomainApp() %><%= image.getThumbview() %>');">
                <div class="hover thirds" onclick="window.location='<%= AntwebProps.getDomainApp() %>/bigPicture.do?name=<%= image.getCode() %>&shot=<%= shot %>&number=1';"></div>
                </div>
    <%      }
        }

        org.calacademy.antweb.Map map = fgTaxon.getMap();
        if (map != null) {
            String mapType = "FieldGuide";
            %>
            <div class="small_map">

            <%@include file="/maps/googleMapInclude.jsp" %>
            </div>
            <%
        }

        if (fgTaxon.hasSpecimenDataSummary()) {  %>
            <div class="clear"></div>
            <div class="fg_data_summary">
            <h4>Specimen Habitat Summary</h4>
            <p><%= fgTaxon.getSpecimenDataSummary(10) %>
            </div>
     <% } %>

<%  } // for each taxon %>

