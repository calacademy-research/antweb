
<!-- googleMapInclude.jsp -->


<%@ page import="org.calacademy.antweb.Map" %>
<%@ page import="org.calacademy.antweb.util.UserAgentTracker" %>

<%
    boolean displayMap = true;

    if (HttpUtil.isBot(request)) {
      out.println("<a href='" + AntwebProps.getDomainApp() + "/login.do'>Log In</a> to see maps.");
      displayMap = false;

      if (A.isDebug("userAgents") && UserAgentTracker.isOveractive(request)) A.iLog("googleMapInclude.jsp overactive:" + UserAgentTracker.isOveractive(request) + " summary:" + UserAgentTracker.summary());

      // was: return;
    } %>

<%
/*
    Here we generate the actual map.

Includes /maps/googleMapPreInclude.jsp and /maps/googleMapInclude.jsp
    dynamicMap-body.jsp
    specimen-body.jsp
    group-body.jsp:		
    collection-body.jsp:            
    taxonPage-body.jsp:            
    locality-body.jsp:				
    search/fieldGuide-body.jsp:
    mapComparison-body.jsp
    overview-body.jsp

googleMapPreInclude.jsp
    script /maps/drawGoogleMap.js

googleMapInclude.jsp
    calls.push("< %= googleMapFunction % >");

// V2?
includeMap.jsp
    Not used? Yes it is. In the edit geolocale curator page and for localityOverviews that don't have a map but do have a centroid.
      Probably cases that should be eliminated.
    include /maps/googleMap.jsp

googleMap.jsp
    google.maps.event.addDomListener(window, 'load', makeMap)

map.jsp
    not used? Testing. http://localhost/antweb/maps/map.jsp
    google.maps.event.addDomListener(window, 'load', initialize);    
    
map.jsp, googleMap.jsp and includeMap.jsp to be removed once the above is resolved (see incudeMap.jsp).
*/
%>


<%
  //A.log("googleMapInclude.jsp map:" + map + " displayMap:" + displayMap);

  // We assume that map, object{"specimen", "taxon", "locality", "collection"}, mapType, and objectName are already set
  if (map != null && displayMap) {
  
    String googleMapFunction = map.getGoogleMapFunction();    

    if ((googleMapFunction != null) && (googleMapFunction.length() > 0)) { 

      //A.log("googleMapInclude.jsp object:" + object + " objectName:" + objectName + " mapType:" + mapType);
      //AntwebUtil.logStackTrace();
      Map.addToDisplayMapCount();
      Map.addToDisplayMapCount(mapType);

	  //String divName = map.getMapName();
	  int n = googleMapFunction.indexOf("', '") + 4;
	  int o = googleMapFunction.indexOf("'", n);
	  String divName = googleMapFunction.substring(n, o);
	  String mapSize = "small";  
	  if (object.equals("dynamic")) {
		mapSize = "large";
		googleMapFunction = googleMapFunction.replaceFirst("small","big");      
	  }
		
	  if ("large".equals(mapSize)) {
		if (map.getSubtitle() != null) out.println("<h3>" + map.getSubtitle() + "</h3>");
	  }    
    
      //A.log("googleMapInclude.jsp divName:" + divName + " googleMapFunction:" + googleMapFunction + " object:" + object);
      // String heightAndWidth = (!object.equals("dynamic")) ? "height:232px;width:232px;" : "height:400px;width:926px;";
      String heightAndWidth = new String(); 
      if (object.equals("dynamic")) {
        heightAndWidth = "height:650px;width:974px;";
      } else if (object.equals("thirds")) {  // fieldGuide and mapComparison?
        heightAndWidth = "height:262px;width:262px;";
      } else {
        heightAndWidth = "height:232px;width:232px;";
      }
%>

<div id="<%= divName %>" style="<%= heightAndWidth %>border:1px solid #b9b9b9; overflow:hidden"></div>

<%     
      // We now prevent this on the server by disallowing more than 1000 points.
      //if (googleMapFunction.length() > 70000) {
      //  AntwebUtil.log("info", "taxonPage-body.jsp: googleMapFunction length is " + googleMapFunction.length() + " for " + HttpUtil.getRequestInfo(request));     
      //} 

      if ((!object.equals("thirds")) && (!object.equals("dynamic"))) { 
        String overviewParam = "";
        
        
        //A.log("googleMapInclude.jsp overview:" + overview);        
        // This was thought to be unnecessary. It is here: http://localhost/antweb/description.do?genus=tetramorium&species=simillimum&rank=species&countryName=Brazil
        if (overview != null) {
          overviewParam = overview.getParams(); //"&" + 
        }

        //A.log("googleMapInclude.jsp object:" + object + " objectName:" + objectName + " overviewParam:" + overviewParam);  
        String objectParam = "";
        //when is this necessary?
        if ("taxonName".equals(object)) {
          objectParam = object + "=" + objectName + "&";
        }
        if ("specimen".equals(object)) {
          objectParam = object + "=" + objectName + "&";
        }
        if ("locality".equals(object)) {
          objectParam = object + "=" + objectName + "&";
          overviewParam = "";
        }
        if ("collection".equals(object)) {
          objectParam = object + "=" + objectName + "&";
          overviewParam = "";
        }

%>
<div style="text-align:center"><a href="<%= AntwebProps.getDomainApp() %>/bigMap.do?<%= objectParam %><%= overviewParam %>">Enlarge Map</a></div>
<%
      } // We have a googleMapFunction 
%>

<!-- googleMapFunction -->
<script type="text/javascript">
  calls.push("<%= googleMapFunction %>");
</script>
<!-- end googleMapFunction -->

<%
      //A.log("googleMapInclude.jsp googleMapFunction:" + googleMapFunction);
      if ("large".equals(mapSize)) {  
        if ((AntwebProps.isDevMode() || LoginMgr.isAdmin(request)) && map.getInfo() != null) out.println("Map Info: " + map.getInfo());
      }
%>

<%
    } else {
      if (AntwebProps.isDevMode()) {
        String aMessage = "No googleMapFunction";
        A.log("googleMapInclude.jsp " + aMessage);
        out.println(aMessage);
      }
    }
  } // map != null
%>

<!-- end googleMapInclude.jsp -->