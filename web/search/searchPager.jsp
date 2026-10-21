<%-- Previous / Next links of the advanced search specimen list (included by advancedSearchResults-body.jsp,
     which sets showingHtml, pagerPrevUrl and pagerNextUrl). nofollow: crawlers do not walk the pages. --%>
<% if (pagerPrevUrl != null || pagerNextUrl != null) { %>
<div class="search_pager" style="margin:8px 0;">
<% if (pagerPrevUrl != null) { %><a class="search_pager_prev" rel="nofollow" href="<%= Html.escAttr(pagerPrevUrl) %>">&laquo; Previous</a>&nbsp;&nbsp;<% } %>
<span class="search_pager_showing"><%= showingHtml %></span>
<% if (pagerNextUrl != null) { %>&nbsp;&nbsp;<a class="search_pager_next" rel="nofollow" href="<%= Html.escAttr(pagerNextUrl) %>">Next &raquo;</a><% } %>
</div>
<% } %>
