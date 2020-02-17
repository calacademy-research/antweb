        <div class="clear"></div>
        <div id="tools">
            <div class="tool_label"><a href="#" onclick="showTools('compare_tools'); return false;">Compare Images</a></div>
            <div class="tool_label"><a href="#" onclick="showTools('map_tools'); return false;">Map Results</a></div>
            <div class="tool_label"><a href="#" onclick="showTools('fieldguide_tools'); return false;">Create Field Guide</a></div>
            <div class="tool_label"><span id="download_data_search_results" onclick="loadSearchResultsData('<%= util.getDomainApp() %>'); return false;">Download Data</a></span></div>
            <div class="clear"></div>
        </div>
    <div id="ie_msg">AntWeb's tools for comparison, mapping, and field guide are optimized for Chrome, Firefox, Safari, and Internet Explorer 8 and greater.</div>
    <div class="clear"></div>

    <div class="tools" id="compare_tools" style="display:none;">
        <div class="tool_select_toggle" title="Click to select all">
            <input type="checkbox" name="selectall" id="selectall">
        </div>
        <div class="tool_text">
            Select results and click "Compare Selected". <input id="taxon_compare_form" class="submit" type="button" value="Compare Selected"> <a href="#" onclick="hideTools('compare_tools'); return false;">Cancel</a>
        </div>
        <div class="clear"></div>
    </div>
    <div class="tools" id="map_tools" style="display:none;">
        <div class="tool_select_toggle" title="Click to select all">
            <input type="checkbox" name="selectall" id="selectall">
        </div>
        <div class="tool_text">
            Select results and click "Map Selected". <input id="map_form" class="submit" type="button" value="Map Selected"> <a href="#" onclick="hideTools('map_tools'); return false;">Cancel</a>
        </div>
        <div class="clear"></div>
    </div>
    <div class="tools" id="fieldguide_tools" style="display:none;">
        <div class="tool_select_toggle" title="Click to select all">
            <input type="checkbox" name="selectall" id="selectall">
        </div>
        <div class="tool_text">
            Select results and click "Create Field Guide". <input id="fieldguide_form" class="submit" type="button" value="Create Field Guide"> <a href="#" onclick="hideTools('fieldguide_tools'); return false;">Cancel</a>
        </div>
        <div class="clear"></div>
    </div>

