<%-- Static include of editGeolocale-body.jsp and geolocaleMgr-body.jsp: the Valid Name menu (validNameId) of a
     place, from the variables geolocale and validChildren (valid places of the same parent); None, the valid
     places, or Other when the stored valid name is not among them. Writes no extra output. --%><!-- Must have defined geolocale and validChildren -->

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
  <option name="validNameId" value='<%= validGeolocale.getId() %>' <%= validSelected %>><%= org.calacademy.antweb.util.Html.esc(displayName) %></option>
<% 
	  	} // for
	  } // if
	  boolean isOther = geolocale.getValidName() != null && !somethingSelected;
      if (isOther) { %>	  
        <option name="validNameId" value="-2" selected>Other</option> <% 
      } %>
    </select>
    
    
    
    
