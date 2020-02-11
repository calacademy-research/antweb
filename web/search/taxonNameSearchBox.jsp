<!-- taxonNameSearchBox.jsp -->
            <div class="search">

                <html:form method="GET" action="description">
                  <!-- input type="hidden" name="project" value="allantwebants" -->
                  <!-- input type="hidden" name="resetProject" value="true" -->   
                <table>
                  <tr>
                    <td>
                      <font color="#fff" size="2">&nbsp;&nbsp;&nbsp;<a href="<%= AntwebProps.getDomainApp() %>/browse.do?name=formicidae&rank=family&project=allantwebants">Taxa:</a></font>
                      <input type="text" class="input_200" id="taxonName" name="taxonName" size="30"/>
                    </td>
                    <td>
                      <input id="search_submit" type="submit" value="Go">
                    </td>	
                  </tr>
                </table>

                <script>
                    // was $("#tax...
                    $("#taxonName").autocomplete("<%= AntwebProps.getDomainApp() %>/search/autoCompleteKeys.jsp"); 
                </script>
    
                </html:form>
        
            </div>