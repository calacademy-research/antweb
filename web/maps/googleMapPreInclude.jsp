
<!-- googleMapPreInclude.jsp -->

<% if (!HttpUtil.isBot(request)) {

  String googleMapKey = AntwebProps.getGoogleMapKey();
  //A.log("googleMapPreInclude.jsp googleMapKey:" + googleMapKey);
%>

<script type="text/javascript" src="https://www.google.com/jsapi"></script>
<script type="text/javascript" src="https://maps.googleapis.com/maps/api/js?v=3&key=<%= googleMapKey %>"></script> 
<!-- was: &sensor=false -->
<script src="<%= AntwebProps.getDomainApp() %>/maps/drawGoogleMap.js" type="text/javascript"></script>

<% } %>

<!-- end googleMapPreInclude.jsp -->