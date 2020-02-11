            <% 
            // Must have defined browerParams and imagesTrueStr
            // taxonomicPageImages-body.jsp is same as taxonChildImages.jsp except uses theTaxaPage instead of taxon and has images=true %>
 
         <div id="status_toggle">
         
            <div class="left">View:</div>
            <div id="change_thumbs" class="has_options">
                <span id="which_thumbs">Head</span>
                <div id="thumb_choices" class="options">
                    <ul>
                      <%
                        String changeThumbTarget = HttpUtil.getRequestURI(request) + "?" + browserParams + imagesTrueStr; %>  
                        <li><a class="clean_url head" href="#" onclick="changeThumbView('h', '<%= changeThumbTarget %>'); return false;">Head</a></li>
                        <li><a class="clean_url profile" href="#" onclick="changeThumbView('p', '<%= changeThumbTarget %>'); return false;">Profile</a></li>
                        <li><a class="clean_url dorsal" href="#" onclick="changeThumbView('d', '<%= changeThumbTarget %>'); return false;">Dorsal</a></li>
                        <li><a class="clean_url all" href="#" onclick="changeThumbView('a', '<%= changeThumbTarget %>'); return false;">All</a></li>
                    </ul>
                </div>
                <div class="clear"></div>
            </div>

</div>