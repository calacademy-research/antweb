<%@ page language="java" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="java.util.*" %>

<jsp:useBean id="taxon" scope="session" class="org.calacademy.antweb.Taxon" /> 
<jsp:useBean id="showTaxon" scope="session" class="org.calacademy.antweb.Taxon" /> 
<jsp:setProperty name="taxon" property="*" />

<jsp:useBean id="specimen" scope="session" class="org.calacademy.antweb.Specimen" />
<jsp:setProperty name="specimen" property="*" />

<%
    String mapRoot = AntwebProps.getProp("mapserver.htdocs") + "?map_AntWebSpecimens_filter=";
    String staticMapRoot = AntwebProps.getProp("mapserver.cgi-bin") + "?mode=map&layers=all&map=" + AntwebProps.getProp("mapserver.mapFile");
   
    String rank = request.getParameter("rank");
    String showRank = request.getParameter("showRank");
    if (showRank == null) showRank = rank;
    
    Overview overview = OverviewMgr.getOverview(request);

    String dagger = "";
    if (taxon.getIsFossil()) dagger = "&dagger;";

    String taxon_object = "taxonName";
    String taxon_objectName = taxon.getTaxonName();
%>

<%@include file="/maps/googleMapPreInclude.jsp" %>   

<div id="page_contents">
<bean:define id="prettyrank" name="showTaxon" property="rank"/>

<%@ include file="/common/taxonTitle.jsp" %>

	<div class="links">
		<ul>
			<li><a class="clean_url overview" href="description.do?<%=taxon.getBrowserParams()%>">Overview</a></li>
			<li><a class="clean_url list" href="browse.do?<%=taxon.getBrowserParams()%>"><%= Rank.getNextPluralRank(rank) %></a></li>
			<li><a class="clean_url images" href="images.do?<%=taxon.getBrowserParams()%>">Images</a></li>

<%  if (taxon.hasMap()) {  %>
			<li><a class="clean_url map" href="bigMap.do?<%= taxon_object %>=<%= taxon_objectName %>&<%= overview.getParams() %>">Map</a></li>
<%  } %>
		</ul>
	</div>

	<%@ include file="/common/viewInAntCat.jsp" %>
	
	<div class="clear"></div>
</div>

<%@ include file="/common/taxonomicHierarchy.jsp" %>

<%
    java.util.ArrayList<Taxon> theChildren = showTaxon.getChildren();

    String orderBy = (String) request.getParameter("orderBy"); // "subgenera";
    if (orderBy != null) {
      A.log("mapComparison-body.jsp sorting...");
      Taxon.sortTaxa(orderBy, theChildren);
    }

    if (theChildren == null) AntwebUtil.log("mapComparison-body.jsp theChildren is null");

    int total = theChildren.size();

//A.log("mapComparison-body.jsp childrenCount:" + total);
%>
<div id="totals_and_tools_container">
	<div id="totals_and_tools">
		<h2>Map Comparison for <%= new Formatter().capitalizeFirstLetter((String) prettyrank) %>
		  <bean:write name="showTaxon" property="prettyName" /> (<!-- span class="numbers" id="mapped"></span --><%= total %> <%=taxon.getNextRank() %> Mapped)</h2>
		<div id="thumb_toggle"><a href="<%= util.getDomainApp() %><%= facet %>?<%=taxon.getBrowserParams()%>">
		  Back to <%= new Formatter().capitalizeFirstLetter((String) prettyrank) %> <bean:write name="showTaxon" property="prettyName" /></a></div>
	</div>
</div>

<div id="page_data">

<%
    int rows = total;
    int loop=0;
    int innerloop = 0;
    for (Taxon thisChild : theChildren) {
        //A.log("mapComparison-body.jsp Child:" + thisChild);

        //AntwebUtil.log("mapComparison-body.jsp taxon:" + thisChild);

        innerloop = 0;
        while (innerloop < 1) {

            //AntwebUtil.log("mapComparison-body.jsp taxon:" + thisChild + " innerloop:" + innerloop);

            if ((thisChild.getMap() != null) && (thisChild.getMap().getGoogleMapFunction() != null)) {
        
                String title = new Formatter().capitalizeFirstLetter(thisChild.getRank()) + ":+"  
                  + new Formatter().capitalizeFirstLetter(thisChild.getName()); 

                String image = "";
                Hashtable childImages = thisChild.getImages();
                if (childImages == null) {
                  // It happens for some ants.  See: https://www.antweb.org/mapComparison.do?subfamily=myrmicinae&genus=procryptocerus&rank=genus&pr=d
                  //AntwebUtil.log("mapComparison-body.jsp images is null for request:" + HttpUtil.getTarget(request));
                } else {
					if ((thisChild.getImages().containsKey("p")) || (thisChild.getImages().containsKey("p1"))) {
						 String key = "p";
						if (!thisChild.getImages().containsKey("p")) {
							key = "p1";
						}
						image = ((org.calacademy.antweb.SpecimenImage) thisChild.getImages().get(key)).getThumbview();
					} // end if p
                }
            } // end map != null
      
            int googleFunctionLength = 0;
            if ((thisChild.getMap() != null) && (thisChild.getMap().getGoogleMapFunction() != null)) {
                googleFunctionLength = thisChild.getMap().getGoogleMapFunction().length();
            }

            if ((thisChild.getMap() != null) && (googleFunctionLength > 0)) { 
%>
           <div class="clear"></div>
           <div class="page_spacer"></div>
<%
                String object = "thirds";
                String objectName = "";
                if (thisChild.getRank().equals("specimen")) { %>
           <h3><a href="specimen.do?name=<%= thisChild.getName() %>">
<%              } else { %>
             <h3><a href="description.do?<%= thisChild.getBrowserParams() %>">
<%              } %>
        <%= thisChild.getPrettyName() %></a></h3>

<%              String mapType = "MapCompare";
                org.calacademy.antweb.Map map = thisChild.getMap();  %>
          <div class="small_map first">         
              <%@include file="/maps/googleMapInclude.jsp" %>   
          </div>
<%              if (thisChild.getImages() != null) {
                    if (thisChild.getImages().containsKey("h")) {
                        String specImageCode = ((SpecimenImage) thisChild.getImages().get("h")).getCode();
%>
            <div class="slide thirds" style="background-image:url('<%= AntwebProps.getImgDomainApp() %><%=((org.calacademy.antweb.SpecimenImage) thisChild.getImages().get("h")).getThumbview() %>');">
<%                      if (thisChild.getRank().equals("specimen")) { %>
                <div class="hover thirds" onclick="window.location='specimen.do?name=<%= thisChild.getName() %>';"></div>
<%                      } else { %>

                <div class="hover thirds" onclick="window.location='<%= AntwebProps.getDomainApp() %>/bigPicture.do?name=<%= specImageCode %>&shot=h&number=1';"></div>
<%                      } %>
            </div>
<%                  } // end contains h 
                    if (thisChild.getImages().containsKey("p")) { 
                        String specImageCode = ((SpecimenImage) thisChild.getImages().get("p")).getCode();
%>
            <div class="slide thirds last" style="background-image:url('<%= AntwebProps.getImgDomainApp() %><%=((org.calacademy.antweb.SpecimenImage) thisChild.getImages().get("p")).getThumbview() %>');">
<%                      if (thisChild.getRank().equals("specimen")) { %>
                <div class="hover thirds" onclick="window.location='specimen.do?name=<%= thisChild.getName() %>';"></div>
<%                      } else { %>
                <div class="hover thirds" onclick="window.location='<%= AntwebProps.getDomainApp() %>/bigPicture.do?name=<%= specImageCode %>&shot=h&number=1';"></div>
<%                      } %>
            </div>
<%                  } // end contains p 
                } // images != null 

            } // if map not null
            innerloop++;

        } // end inner loop
        loop++;
        //AntwebUtil.log("mapComparison-body.jsp rows:" + rows + " loop:" + loop + " innerloop:" + innerloop);
    } // end while loop %>
<input type="hidden" id="mapped_count" value="<%= innerloop %>">
</div>  
