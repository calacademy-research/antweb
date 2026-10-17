<!-- data_download_overlay.jsp -->
<%
  if (!LoginMgr.isLoggedIn(request)) {
%>

<div id="download_data_overlay" style="display:none;">
    <div id="download_overlay"><!-- data_download_overlay.jsp 1 -->
        <div class="left"><h3>Download Specimen Data</h3></div><div class="right" id="close_download_data">X</div>
        <div class="clear"></div>
        <p><%= Login.MUST_LOGIN_MESSAGE %></p>
    </div>
</div>

<% } %>

<div id="download_data_overlay" style="display:none;">
    <div id="download_overlay">
        <div class="left"><h3>Download Specimen Data</h3></div>
        <div class="right" id="close_download_data">X</div>
        <div class="clear"></div>
        <p>To download specimen data, Right-click and save to your desktop.</p>
        <ul><!-- data_download_overlay.jsp 2 -->
                <iframe id="tab_data" width="100%" border="0" src="<%= util.getDomainApp() %>/search/loading_search_results_data.jsp"></iframe>
            <%
               String dataLink = (String) request.getAttribute("dataLink");
            %>
        </ul>
    </div>
</div>
