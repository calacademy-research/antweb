<!-- pageTools.jsp deprecated in favor of pageToolLinks.jsp -->
        <div class="clear"></div>
        <div id="tools">
            <div class="tool_label"><a href="#" onclick="showTools('compare_tools'); return false;">Compare Images</a></div>
            <div class="tool_label"><span id="download_data" onclick="loadTabData('<%= util.getDomainApp() %>', 'getSpecimenList.do?projectName=<%= projectName %>&taxonName=<%= taxon.getTaxonName() %>'); return false;">Download Data</span></div>
            <div class="clear"></div>
        </div>
    <div id="ie_msg">AntWeb's tools for comparison, mapping, and field guide are optimized for Chrome, Firefox, Safari, and Internet Explorer 8 and greater.</div>
    <div class="clear"></div>

    <div class="tools" id="compare_tools" style="display:none;">
        <div class="tool_select_toggle" title="Click to select all">
            <input type="checkbox" name="selectall" id="selectall">
        </div>
        <div class="tool_text">
            Select <%=taxon.getNextRank().toLowerCase()%> for comparing images, and click "Compare Selected". 
            <input id="list_compare_form" class="submit" type="submit" value="Compare Selected"> <a href="#" onclick="hideTools('compare_tools'); return false;">Cancel</a>
        </div>
        <div class="clear"></div>
    </div>

