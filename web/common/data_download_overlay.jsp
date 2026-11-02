<%-- The Download Specimen Data overlay of taxon pages, statically included by showBrowse-body.jsp,
     taxonPage-body.jsp and taxonChildImages.jsp. For visitors not logged in it first writes an overlay with
     Login.MUST_LOGIN_MESSAGE; then, for everyone, the overlay whose iframe tab_data starts blank and gets the
     Download Data link's address (antweb.jquery.js). Docs: docs/guides/downloads.md. --%><!-- data_download_overlay.jsp -->
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
                <%-- Starts blank: the Download Data link sets the real src (antweb.jquery.js). It used to load
                     loading_search_results_data.jsp on every page view, hidden, even when nobody opened it. --%>
                <iframe id="tab_data" width="100%" border="0" src="about:blank"></iframe>
            <%
               String dataLink = (String) request.getAttribute("dataLink");
            %>
        </ul>
    </div>
</div>
