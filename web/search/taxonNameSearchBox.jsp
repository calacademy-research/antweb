<%-- Taxa box of the header search bar (static include of common/searchBar.jsp, on every page): a GET form to
     description.do?taxonName=... (the taxon page) with suggestions from search/autoCompleteKeys.jsp. Docs:
     docs/flows/search.md, docs/flows/taxon-pages.md. Tests:
     tests/e2e/test_taxon_pages.py::test_taxon_name_redirects_to_the_taxon_page, test_browser.py. --%><!-- taxonNameSearchBox.jsp -->
            <div class="search">

                <html:form method="GET" action="description">
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