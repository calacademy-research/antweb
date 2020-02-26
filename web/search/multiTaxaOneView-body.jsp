<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>

<!-- MultiTaxaOneView-body.jsp -->

<logic:present parameter="shot">
  <bean:parameter id="shot" name="shot" />
</logic:present>
<logic:notPresent parameter="shot">
  <bean:define id="shot" value="Head" scope="page"/>
</logic:notPresent>

<%
    // A.log("multiTaxaOneView-body.jsp");

    // To test this page:
    // http://www.antweb.org/advancedSearch.do?org.apache.struts.taglib.html.TOKEN=e718b6e719920d0cf12d2e696f8466af&searchMethod=advancedSearch&advanced=true&collGroupOpen=none&specGroupOpen=none&geoGroupOpen=none&typeGroupOpen=none&searchType=equals&name=&subfamilySearchType=equals&subfamily=&genusSearchType=equals&genus=Hagensia&speciesSearchType=contains&species=havilandi&subspeciesSearchType=equals&subspecies=&biogeographicregion=&country=&adm1=&adm2SearchType=equals&adm2=&localityNameSearchType=equals&localityName=&localityCodeSearchType=equals&localityCode=&habitatSearchType=equals&habitat=&methodSearchType=equals&method=&collectedBySearchType=equals&collectedBy=&collectionCodeSearchType=equals&collectionCode=&dateCollectedSearchType=equal&dateCollected=&specimenCodeSearchType=equals&specimenCode=&locatedAtSearchType=equals&locatedAt=&elevationSearchType=equal&elevation=&casteSearchType=contains&caste=&ownedBySearchType=equals&ownedBy=&type=&resultRank=specimen

    java.util.Hashtable labelMap = new java.util.Hashtable();
    labelMap.put("Head","h");
    labelMap.put("Profile","p");
    labelMap.put("Dorsal","d");
    labelMap.put("Ventral","v");
    labelMap.put("Label","l");

    String shot = (String) pageContext.getAttribute("shot");
    if (shot == null) shot = "Head";
    //String shortHand = (String) labelMap.get(shot);
    //String shortHand = "";
    //pageContext.setAttribute("shortHand",shortHand, PageContext.REQUEST_SCOPE);

%>

<script type="text/javascript">
<!--

function changeView(newView) {
  window.location = "<%= AntwebProps.getDomainApp() %>/search/multiTaxaOneView.jsp?shot=" + newView;
}
// -->
</script>

<!-- was session Feb2020 -->
<jsp:useBean id="taxaToCompare" scope="session" class="java.util.TreeMap"/>

<div id="page_contents">
<h1>Comparing Images</h1>
    <div class="clear"></div>
    <div class="page_divider"></div>
    <div id="totals_and_tools_container">
        <div id="totals_and_tools">
            <h2 class="display_count">Comparing <%= shot %> Views of Results</h2>
            <span id="sub_taxon">Click image for higher resolution.</span>
            <div id="thumb_toggle"><b><a href="<%= AntwebProps.getDomainApp() %>/compareResults.do">Refine Selection</a>
            <!-- a href="#" onclick="history.back(); return false;">Back</a -->
            </b></div>
            
		</div>
		<div class="clear"></div>	
        <html:form action="/chooseComparison">
		<div class="tools" id="compare_tools">
			<p>
			View:&nbsp;&nbsp; <input type="radio" name="view" value="head" onClick="changeView('Head');" <%= shot.equals("Head")?"checked":"" %>> Head
			&nbsp;&nbsp; <input type="radio" name="view" onClick="changeView('Profile');" value="profile"  <%= shot.equals("Profile")?"checked":"" %>> Profile
			&nbsp;&nbsp; <input type="radio" name="view" value="dorsal" onClick="changeView('Dorsal');"  <%= shot.equals("Dorsal")?"checked":"" %>> Dorsal
			&nbsp;&nbsp; <input type="radio" name="view" value="ventral" onClick="changeView('Ventral');"  <%= shot.equals("Ventral")?"checked":"" %>> Ventral 
			&nbsp;&nbsp; <input type="radio" name="view" value="label" onClick="changeView('Label');"  <%= shot.equals("Label")?"checked":"" %>> Label 
			</p>
		</div>
		</html:form>
	</div>
</div>
<!-- /div -->

<div id="page_data">


<%
  Set<Specimen> specimenSet = taxaToCompare.keySet();
  ArrayList<Specimen> specimenList = new ArrayList<Specimen>();
  specimenList.addAll(specimenSet);
  Collections.sort(specimenList);
  //A.log("multiTaxaOneView.jsp specimenList:" + specimenList);

  java.util.Iterator iterator = specimenList.iterator();   
  int index = 1;
  int first = 1;   
  int second = 2;
  int total = taxaToCompare.size();

  // Would be nice to get rid of the iterator and replace with for loop.
  //Set<Taxon> keySet = taxaToCompare.keySet();
  //if (AntwebProps.isDevMode()) AntwebUtil.log("multiTaxaOneView-body.jsp total:" + total + " keySet:" + keySet);

  int rows = (total/3) + 1;
  int loop=0;
  String shortHand = null;
  String preShortHand = null;
  while (loop < rows) {
%>
     <%
    int innerloop = 0;
    int position = 1;
    while (iterator.hasNext()) {  
      //for (Taxon key : keySet) {  // This does not work.  
      if (position == 3) { position = 1; }
      	
      Taxon thisChild = (Taxon) iterator.next(); //key;
      preShortHand = (String) labelMap.get(shot);
      shortHand = preShortHand;
      if (thisChild.getRank().equals("specimen")) {
        shortHand += "1";
      }
        
      Hashtable theseImages = thisChild.getImages();
      if (theseImages != null) {
        SpecimenImage specimenImage = (org.calacademy.antweb.SpecimenImage) theseImages.get(shortHand);
        if (specimenImage == null) {
           //AntwebUtil.log("multiTaxaOneView-body.jsp NPE averted. shortHand:" + shortHand + " taxonName:" + thisChild.getTaxonName() + " theseImages:" + theseImages);
           continue;
        }
        String picture = specimenImage.getThumbview();
        String highres = specimenImage.getHighres();
        String code = specimenImage.getCode();

 	    String theName = "";
		if (thisChild.getRank().equals("specimen")) {
		  //theName = thisChild.getTaxonNameDisplay();

		  theName = thisChild.toString();
		  //if (true) AntwebUtil.log("multiTaxaOneView.jsp prettyName:" + the_name);
		} else {
		  //theName = thisChild.getPrettyName();
          theName = code;
		}
		 
		// Where is the_name output to display in the corner of the image?
        String specimenUrl = AntwebProps.getDomainApp() + "/bigPicture.do?name=" + code + "&number=1&shot=" + preShortHand;
        String taxonUrl = AntwebProps.getDomainApp() + "/images.do?taxonName=" + thisChild.getTaxonName();
%>
        <div class="slide half<% if (position == first) { %> first<% } %><% if (position == second) { %> last<% } %>" style="background-image:url('<%= AntwebProps.getImgDomainApp() %><%= picture %>')" onclick="window.location='<%= specimenUrl %>'">
          <div class="hover half" onclick="window.location='<%= specimenUrl %>'"></div>
          <div class="top_gradient half"></div>
          <div class="name half">
              <a href="<%= taxonUrl %>"><%= Taxon.getPrettyTaxonName(thisChild.getTaxonName()) %></a> <a href="<%= specimenUrl %>"><%= theName %></a>
          </div>
          <div class="clear"></div>
        </div>

<% 
        if (innerloop < 2) { %>
        <div class=compare_divider></div>
<% 
        } 
        innerloop++;
        position++;
      }      //AntwebUtil.log("multiTaxaOneView-body.jsp 1 taxonName:" + thisChild.getTaxonName() + " theseImages:" + theseImages);
    }  // end iterator.hasNext()
    if (position == 2) {  %>
      <div class="clear"></div>
<%  }
    loop++;
  } // end loop through rows  %>
<div class="clear"></div>

<div class="clear"></div>
<div class="page_spacer"></div>

</div>
