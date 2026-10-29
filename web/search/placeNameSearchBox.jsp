<%-- Places box of the header search bar (static include of common/searchBar.jsp, on every page): a GET form to
     place.do?placeName=... (geolocale.PlaceNameAction opens the place's page) with suggestions from
     search/autoCompletePlaceKeys.jsp. Docs: docs/flows/search.md. Tests:
     tests/e2e/test_search_pages.py::test_place_name_opens_the_place_page, test_browser.py. --%>
            <!-- placeNameSearchBox.jsp -->
            <%@page pageEncoding="UTF-8"%>

            <div class="search">

                <html:form method="GET" action="place">
                <table>
                  <tr>
                    <td>
                        <font color="#fff" size="2"><a href="<%= AntwebProps.getDomainApp() %>/region.do">Places:</a></font>
                        <input type="text" class="input_200" id="placeName" name="placeName" size="21"/>
                    </td>
                    <td>
                      <input id="search_submit" type="submit" value="Go">      
                    </td>	
                  </tr>
                </table>

                <script>
                    // was $("#tax...
                    $("#placeName").autocomplete("<%= AntwebProps.getDomainApp() %>/search/autoCompletePlaceKeys.jsp"); 
                </script>
        
                </html:form>
            
            </div>