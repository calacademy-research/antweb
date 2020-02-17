<!-- Must have defined geolocale and validChildren -->

    <select name="validNameId" >
	<%
	  String validSelected = "";
	  boolean somethingSelected = false;
      if (geolocale.getValidName() == null) {
        validSelected = " selected";
        somethingSelected = true;
      } 
%>
      <option name="validNameId" value="-1"<%= validSelected %>>None</option>
<%
	  if (validChildren != null) {
	    for (Geolocale validGeolocale : validChildren) {
		  if (validGeolocale == null) continue;

		  if (validGeolocale.getName().equals(geolocale.getName())) continue;

		  if (validGeolocale.getName().equals(geolocale.getValidName())
             && geolocale.getParent() != null && geolocale.getParent().equals(validGeolocale.getParent())
		     ) {
		       validSelected = "selected"; 
               somethingSelected = true;
             } else {
               validSelected = "";
		  }     
		  String displayName = validGeolocale.getName();
		  if (displayName.length() > 40) displayName = displayName.substring(0, 40) + "...";
%> 
  <option name="validNameId" value='<%= validGeolocale.getId() %>' <%= validSelected %>><%= displayName %></option>
<% 
	  	} // for
	  } // if
	  boolean isOther = geolocale.getValidName() != null && !somethingSelected;
      if (isOther) { %>	  
        <option name="validNameId" value="-2" selected>Other</option> <% 
      } %>
    </select>
    
    
    
    
