<%-- Tool pages (docs/guides/page-style.md): the shared stylesheet and table helper. A page opts in with
     a static include of this file (include directive, file="/common/toolsHead.jsp") and wraps its own markup in <div class="aw-tools">.
     Static include only (no page directives here). Public layouts never include it
     (tests/e2e/test_tool_pages_style.py). --%>
<link href="<%= org.calacademy.antweb.util.AntwebProps.getDomainApp() %><%= org.calacademy.antweb.util.Asset.url(application, "/common/tools.css") %>" rel="stylesheet" type="text/css">
<script src="<%= org.calacademy.antweb.util.AntwebProps.getDomainApp() %><%= org.calacademy.antweb.util.Asset.url(application, "/common/tools.js") %>" defer></script>
