

        <link rel="stylesheet" type="text/css" href="https://code.jquery.com/ui/1.12.1/themes/base/jquery-ui.css" />
        <link rel="stylesheet" type="text/css" href="<%= AntwebProps.getDomainApp() %>/search/autocomplete.css"/>
        <!-- script src="https://code.jquery.com/jquery-1.12.4.js"></script -->
        <!-- script src="https://code.jquery.com/jquery-3.2.1.js"></script -->    
        <!-- script src="https://code.jquery.com/ui/1.12.1/jquery-ui.js"></script -->
        <script src="https://code.jquery.com/jquery-migrate-1.2.1.js"></script>
        <script src="<%= AntwebProps.getDomainApp() %>/search/autocomplete.js"></script>

        <div class="searchBoxes2">
            <div class="search">
            &nbsp;&nbsp;&nbsp;<a href="<%= domainApp %>/advSearch.do"><img width="22" src="<%= AntwebProps.getDomainApp() %>/image/magnifyGlass.png"></a>
            </div>

            <%@ include file="/search/taxonNameSearchBox.jsp" %>
            <%@ include file="/search/placeNameSearchBox.jsp" %>            

        </div>

