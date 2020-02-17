		  <!-- includeMap.jsp -->
		 <% 
            // Must have LocalityOverview mapOverview defined.
		    int zoom = 0;
		    if (mapOverview instanceof Geolocale) {
			  String name = ((Geolocale) mapOverview).getName();
			  if ("Russia".equals(name)) zoom = 2;
			  if ("United States".equals(name)) zoom = 2;
			  if ("Canada".equals(name)) zoom = 2;
			  if ("Marshall Islands".equals(name)) zoom = 5;
 		    }

            String useBoundingBox = mapOverview.useBoundingBox();
            A.log("includeMap.jsp boundingBox:" + useBoundingBox);
            String[] bounds = null;
            if (useBoundingBox != null && !"null".equals(useBoundingBox)) {
              bounds = useBoundingBox.split(", ");
              try {
                A.log("includeMap.jsp mapOverview:" + mapOverview + " zoom:" + zoom + " useBoundingBox:" + useBoundingBox 
                  + " centroid:" + mapOverview.useCentroid() + " bounds[0]" + bounds[0] + " 1]" + bounds[1] + " 2]" + bounds[2] + " 3]" + bounds[3]);
				%>
				<input type="hidden" id="west" name="west" value="<%= bounds[0] %>">
				<input type="hidden" id="south" name="south" value="<%= bounds[1] %>">
				<input type="hidden" id="east" name="east" value="<%= bounds[2] %>">
				<input type="hidden" id="north" name="north" value="<%= bounds[3] %>">			
    			<% 
			  } catch (ArrayIndexOutOfBoundsException e) {
			    AntwebUtil.log("includeMap.jsp mapOverview:" + mapOverview + " mapBoundingBox:" + useBoundingBox + " e:" + e);
			  }
		    } %>

			  <div class="left">
				<div class="small_map">
				  <div id="map-canvas" style="height:232px; width:232px; border:1px solid #b9b9b9; overflow:hidden">
			
					<input type="hidden" id="zoom_level" name="zoom_level" value="<%= zoom %>">
					<input type="hidden" id="coords_for_geo" name="coords_for_geo" value="<%= mapOverview.useCentroid() %>">

					<%@include file="/maps/googleMap.jsp" %>
				  </div>  <!-- end map-canvas -->
				</div> <!-- end small_map -->
			  </div> <!-- end left -->