<%@ page import="org.calacademy.antweb.Formatter" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="java.util.ResourceBundle" %>

<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<!-- specimenImages-body.jsp -->

<%
    if (org.calacademy.antweb.util.HttpUtil.isStaticCallCheck(request, out)) return;

    Overview overview = OverviewMgr.getOverview(request);
    if (overview == null) overview = ProjectMgr.getProject(Project.ALLANTWEBANTS);

    Taxon taxon = (Taxon) session.getAttribute("taxon");
    if (taxon == null) return;
   
    Specimen specimen = (Specimen) session.getAttribute("specimen"); 
    //Login accessLogin = LoginMgr.getAccessLogin(request); 
    
    String thePage = HttpUtil.getTarget(request);

    org.calacademy.antweb.Formatter formatter = new Formatter();

    String object = "specimen";
    String objectName = specimen.getName();
 
    String dagger = "";
    if (specimen.getIsFossil()) dagger = "&dagger;";
%>
<input id="for_print" type="text" value="AntWeb. Available from:  <%= HttpUtil.getFullJspTarget(request)%>">
<input id="for_web" type="text" value="URL: <%= HttpUtil.getFullJspTarget(request)%>">
<div id="page_contents">

<%@ include file="/common/taxonTitle.jsp" %>

        <div class="links">
            <ul>
                <li><a href="<%= AntwebProps.getDomainApp() %>/specimen.do?name=<%= specimen.getName() %>">Overview</a></li>
                <li>Images</li>
<%                  
    //String showMapLink = map.getGoogleMapFunction();
    //if ((showMapLink != null) && (showMapLink.length() > 0)) {

    String linkOverviewParams = "";
    if (overview != null) {
      linkOverviewParams = "&" + overview.getParams();
    }
    String code = specimen.getName().toLowerCase();
    
    if (taxon.hasMap()) {
        String link_params = "specimen=" + specimen.getName().toLowerCase() + linkOverviewParams;
%>
                <li><a href="bigMap.do?specimen=<%= specimen.getName() %>&<%= overview.getParams() %>">Map</a></li>
<%          
    }
%>
            </ul>
        </div>
        <div id="antcat_view">
        </div>
        <div class="clear"></div>
<%@ include file="/common/taxonomicHierarchy.jsp" %>

    <!-- 
        taxonName:<%= specimen.getTaxonName() %> 
        details:<%= specimen.getDetails() %>
        fullName:<%= specimen.getFullName() %>
        simpleName:<%= specimen.getSimpleName() %>
        prettyName:<%= specimen.getPrettyName() %>
        fullName:<%= specimen.getFullName() %>
        name:<%= specimen.getName() %>
    -->

<% 
    java.util.ArrayList<String> shotsToShow = new java.util.ArrayList<String>();

    int maxShot = 50;
    for (int i = 1 ; i <= maxShot ; ++i) {
      shotsToShow.add("h" + i);
      shotsToShow.add("p" + i);
      shotsToShow.add("d" + i);
      shotsToShow.add("v" + i);
      shotsToShow.add("l" + i);
    } 
      
    
    int index = 0;
    int position = 0;
    int first = 1;
    int fourth = 4;

	String choice_is = null;
	String all = "a";
	Cookie reallyCookie = null;
	String use_thumb = new String();
    int totalImages = 0;
    
    if (specimen.getImages() != null) {
	  if (!specimen.getImages().isEmpty()) {
		totalImages = specimen.getImages().size();

		String the_cookie = "thumbs";
		Cookie the_cookies [] = request.getCookies ();
 	    if (the_cookies != null) {
		   for (int i = 0; i < the_cookies.length; i++) {
   	         if (the_cookies [i].getName().equals (the_cookie)) {
			   reallyCookie = the_cookies[i];
			   break;
		     }
		   }
        }
      }
	  if (reallyCookie == null) {
	    use_thumb = "h";
	  } else {
	    use_thumb = reallyCookie.getValue();
	  }
	  choice_is = use_thumb;
	%>
	  <h2 class="display_count"><%= totalImages %> Images</h2>
   <% if (choice_is.equals(all)) { %>
        <span id="sub_taxon">Click image for higher resolution.</span>

<%    if (LoginMgr.isAdmin(request)) { %>
        <span id="sub_taxon">&nbsp;&nbsp;&nbsp;&nbsp;Go to:<a target=new href='<%= AntwebProps.getImgDomainApp() %>/images/<%= code %>/'>Image Dir</a></span>
   <% } %>

        
        <div class="clear"></div>
   <% } %>


	  </div>

	  <div class="clear"></div>
	<% 
    }
%>

    <div id="page_data">
  
<%
    // To avoid ConcurrentModificationException
    Hashtable imagesHash = specimen.getImages();
    int imageCount = imagesHash.size();   

    //AntwebUtil.log("specimenImages-body.jsp imageCount:" + imageCount);
    if (imageCount > 0) {
		String[] keyArray = new String[imageCount];
		imagesHash.keySet().toArray(keyArray);
		SpecimenImage[] imageArray = new SpecimenImage[imageCount];
		imagesHash.values().toArray(imageArray);
 
		for (String shot : shotsToShow) {
		  //AntwebUtil.log("specimenImages-body.jsp shot:" + shot);

		  int i = 0;
		  for (SpecimenImage theImage : imageArray) {

			String thisCode = theImage.getCode();
			String thisKey = keyArray[i];
		
			if (shot.equals(thisKey)) {
 
			  // AntwebUtil.log("specimenImages-body.jsp i:" + i + " code:" + thisCode + " shot:" + shot + " thisKey:" + thisKey); 
		   
			  String shotType = ((String) thisKey).substring(0,1); 
			  String shotNumber = ((String) thisKey).substring(1);
			  ++index;
			  ++position; 
			  if (position == 5) { position = 1; }

			  if (choice_is.equals(all)) { %>
				<div class="slide medium <% if (position == first) { %> first<% } %><% if (position == fourth) { %> last<% } %> ratio">
				  <div class="adjust"><img class="medres" src="<%= AntwebProps.getImgDomainApp() %><%= theImage.getThumbview() %>" onclick="window.location='bigPicture.do?name=<%= specimen.getName().toLowerCase() %>&shot=<%= (String) shotType %>&number=<%= (String) shotNumber %>'"></div>
				</div>
		   <% } else { %>
				<div class="slide medium<% if (position == first) { %> first<% } %><% if (position == fourth) { %> last<% } %>" style="background-image:url('<%= AntwebProps.getImgDomainApp() %><%= theImage.getThumbview() %>');">
				  <div class="hover medium" onclick="window.location='bigPicture.do?name=<%= specimen.getName().toLowerCase() %>&shot=<%= (String) shotType %>&number=<%= (String) shotNumber %>'"></div>
				  <div class="top_gradient medium"></div>
				  <div class="ratio_icon" onclick="window.location='bigPicture.do?name=<%= specimen.getName().toLowerCase() %>&shot=<%= (String) shotType %>&number=<%= (String) shotNumber %>'"><img src="image/magnify.png" title="Click to see large version"></div>
				  <div class="clear"></div>
				</div>
		   <% } 
			} // shot equals code
			++i;
		  } // end of images loop
		} // end of shotsToShow loop 

    } %>

<div class="clear"></div>

<logic:present name="specimen" property="typeStatus"><logic:notEqual name="specimen" property="typeStatus" value=""><br>Type status: <img src="<%= AntwebProps.getDomainApp() %>/image/has_type_status_icon.png"></logic:notEqual></logic:present>
<b><bean:write name="specimen" property="typeStatus"/></b></li>

<br /><br />
<% 
   String amissEmail = AntwebUtil.getAdminEmail();
   if (specimen != null && specimen.getGroup() != null) amissEmail = specimen.getGroup().getAdminEmail();  
%> 
<a href="mailto:<%= amissEmail %>?subject=Regarding AntWeb page <%= thePage %>">See something amiss? Send us an email.</a>

</div>

