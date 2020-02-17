<!-- Used in menuBar.jsp.  Implementation by Luke.  Older means.  -->
        <div class="search">
            <form action="<%= domainApp %>/basicSearch.do" id="cse-search-box">
            <input type="hidden" name="cx" value="008538965889557422720:bisxsxkgfmc" />
            <input type="hidden" name="cof" value="FORID:11" />
            <input type="hidden" name="ie" value="UTF-8" />
            Search for: <input id="search_input" name="q" type="text" value=""> <input id="search_submit" type="submit" value="Go"> <span class="small"><a href="<%= domainApp %>/advSearch.do">Advanced Search</a></span>
            </form>
<% if (HttpUtil.isOnline()) { %>
            <script type="text/javascript" src="https://www.google.com/coop/cse/brand?form=cse-search-box&lang=en"></script>
<% } %>
            <style>
            .cse input.gsc-input, input.gsc-input {
                background-image:none !important;
            }
            #search_input { background-image:none !important; }
            </style>
        </div>
