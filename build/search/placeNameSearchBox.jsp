
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