<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.geolocale.*" %>
<%@ page import="org.calacademy.antweb.Utility" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.Taxon" %>
<%@ page import="org.calacademy.antweb.TaxaPage" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<%
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;

    String pageRank = request.getParameter("rank");

    TaxaPage taxaPage = (TaxaPage) request.getAttribute("taxaPage");
%>

<bean:define id="showNav" value="taxonomic"/>

<%
   //AntwebUtil.log("error", "taxonomicPageImages-body.jsp title:" + title + " project:" + project);  

    String thumb_choice= new String();
    String head = "h";
    String cookieName = "thumbs";
    Cookie cookies [] = request.getCookies ();
    Cookie myCookie = null;
    if (cookies != null) {
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(cookieName)) {
                myCookie = cookie;
                break;
            }
        }
    }
    if (myCookie == null) {
      thumb_choice = head;
    } else {
      thumb_choice = myCookie.getValue();
    }

    // An overview is a Museum, Bioregion, Geolocale, (Project?).
    Overview overview = OverviewMgr.getOverview(request);
    //A.log("taxonomicPageImages-body.jsp overview:" + overview);

    boolean isOnlyShowUnImaged = false;
%>

<bean:define id="children" name="taxaPage" property="children"/>
<input id="for_print" type="text" value="AntWeb. Available from: <%= HttpUtil.getFullJspTarget(request)%>">
<input id="for_web" type="text" value="URL: <%= HttpUtil.getFullJspTarget(request)%>">

<div id="page_contents">

<%@ include file="common/overviewHeading.jsp" %>     
        
    </div>
    <div class="page_divider taxonomic"></div>

<% 
    ArrayList<Taxon> childrenList = taxaPage.getChildren();
    int childrenCount = childrenList.size();
    //A.log("count:" + childrenCount);
%>

  <div id="totals_and_tools_container">
    <div id="totals_and_tools">
        <h2 class="display_count"><%= childrenCount %> <%= Rank.getPluralRank(pageRank) %> Imaged (out of <%= childrenCount %> Total)</h2>
<%
    if (false && overview instanceof Geolocale) { 
    %> <font color=red>Page Under Maintenance</font> <% 
    } 

      String statusSet = taxaPage.getStatusSetStr();
      String statusSetSize = taxaPage.getStatusSetSize();
      String browserParams = taxaPage.getBrowserParams();
      //A.log("browserParams:" + browserParams + " statusSetStr:" + statusSet + " statusSetSize:" + statusSetSize);
%>

        <%@ include file="/common/statusesDisplay.jsp" %>

<% 
    if (false && AntwebProps.isDevMode()) { 
%> &nbsp;&nbsp;&nbsp;<a href="<%= AntwebProps.getDomainApp() %>/fieldGuide.do?<%= taxaPage.getBrowserParams() %>">Field Guide</a> <% 
    }
%>

        <div id="thumb_toggle">

       <%
          String imagesTrueStr = "&images=true";  %>
        <%@ include file="/common/imageViewsDisplay.jsp" %>

	<%@ include file="/common/casteViewsDisplay.jsp" %>

    </div>
    <div class="clear"></div>
  </div>

	<%@ include file="/unImagedTaxa.jsp" %>  

</div>


<div id="page_data">

<%
   if (childrenCount == 0) {
      out.println("No Children.");   
      //AntwebUtil.log("no children");
   }

   int childCount = 0;
   int imgCount = 0;
   int index = 0;
   int position = 0;
   int first = 1;
   int fourth = 4;
   
   
   // Hopefully, to avoid ConcurrentModificationException
   ArrayList<Taxon> childrenList2 = new ArrayList<Taxon>(childrenList);
   
   int i = 0;
   for (Taxon thisChild : childrenList2) { //childrenArray?) {


     //A.log("taxonomicPageImages-body.jsp child:" + thisChild );
     
     if ((!isOnlyShowUnImaged) || (!thisChild.getHasImages())) {
          
       //Taxon thisChild = (Taxon) child; 
       if (isOnlyShowUnImaged) {
         out.println(thisChild.getSubfamily() + " ");
       }
       String dagger = "";
       //AntwebUtil.log("taxonomicPage-body.jsp isFossil:" + ((Taxon)child).getIsFossil());
       
	   String maleSpecimen = thisChild.getDefaultSpecimen(Caste.MALE);                    
	   String workerSpecimen = thisChild.getDefaultSpecimen(Caste.WORKER);                    
	   String queenSpecimen = thisChild.getDefaultSpecimen(Caste.QUEEN);        

++i;

  %>
       <%@include file="/taxonChildImageSet.jsp" %>
  <% }

     // AntwebUtil.log("taxonomicPageImages-body childCount:" + childCount);
     ++childCount;
%>
<%
     int childDisplayCount = 50;

   }
   //A.log("taxonomicPageImages i:" + i);
%>
   
   

<input type="text" id="imaged_taxa_count" value="<%= imgCount %>" />
</div>
