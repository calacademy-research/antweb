

        <link rel="stylesheet" type="text/css" href="https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.13.3/themes/base/jquery-ui.min.css" integrity="sha512-8PjjnSP8Bw/WNPxF6wkklW6qlQJdWJc/3w/ZQPvZ/1bjVDkrrSqLe9mfPYrMxtnzsXFPc434+u4FHLnLjXTSsg==" crossorigin="anonymous" referrerpolicy="no-referrer" />
        <link rel="stylesheet" type="text/css" href="<%= AntwebProps.getDomainApp() %><%= org.calacademy.antweb.util.Asset.url(application, "/search/autocomplete.css") %>"/>
        <% // jQuery 3.7.1 and jQuery Migrate are loaded once by the layout %>
        <script src="<%= AntwebProps.getDomainApp() %><%= org.calacademy.antweb.util.Asset.url(application, "/search/autocomplete.js") %>"></script>

        <div class="searchBoxes2">
            <div class="search">
            &nbsp;&nbsp;&nbsp;<a href="<%= domainApp %>/advSearch.do"><img width="22" src="<%= AntwebProps.getDomainApp() %>/image/magnifyGlass.png"></a>
            </div>

            <%@ include file="/search/taxonNameSearchBox.jsp" %>
            <%@ include file="/search/placeNameSearchBox.jsp" %>            

        </div>

