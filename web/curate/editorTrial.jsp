<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.curate.EditorTrialAction" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%-- Editor trial page (EditorTrialAction). Read only: nothing on this page writes page text. --%>
<%
    Login accessLogin = LoginMgr.getAccessLogin(request);
    if (!LoginMgr.isCurator(accessLogin)) { response.sendError(404); return; }
    EditorTrialAction.View v = (EditorTrialAction.View) request.getAttribute("editorTrial");
    if (v == null) { response.sendError(404); return; }
    String ctx = request.getContextPath();
    String editors = ctx + "/common/editors";
    String self = ctx + "/editorTrial.do";
    response.setHeader("Cache-Control", "no-store");
%><!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>Editor trial - AntWeb</title>
<link rel="stylesheet" href="<%= AntwebProps.getDomainApp() %><%= org.calacademy.antweb.util.Asset.url(application, "/common/editors/editorTrial.css") %>">
</head>
<body>
<div id="et" class="et"
     data-sanitize-url="<%= Html.escAttr(self) %>"
     data-csrf="<%= Html.escAttr(Csrf.token(request)) %>"
     data-field="<%= Html.escAttr(v.field == null ? "" : v.field) %>"
     data-valid-elements="<%= Html.escAttr(v.validElements) %>"
     data-hugerte-base="<%= Html.escAttr(editors + "/hugerte/1.0.14") %>"
     data-hugerte-suffix="?v=<%= Html.escAttr(Asset.v(application, "/common/editors/hugerte/1.0.14/hugerte.min.js")) %>"
     data-tinymce-base="<%= Html.escAttr(ctx + PageEditor.TINYMCE_BASE) %>"
     data-tinymce-suffix="<%= Html.escAttr(PageEditor.tinyMceCacheSuffix(application)) %>">

<header class="et-head">
  <h1>Editor trial: HugeRTE and TinyMCE 8</h1>
  <p class="et-nav"><a href="<%= ctx %>/curate.do">Curator tools</a> &middot; logged in as <%= Html.esc(accessLogin.getName()) %></p>
  <p class="et-banner"><b>Test page. Nothing here is saved.</b> The stored page text is only read; no button on this page changes it.
  Pick a stored field below (or use the empty editors to try pasting), then compare the two editors.</p>
</header>

<section class="et-box">
  <h2>1. Pick a field</h2>
  <form method="GET" action="<%= Html.escAttr(self) %>" class="et-search">
    <label for="et-q">Taxon name, specimen code or page key contains</label>
    <div class="et-row">
      <input id="et-q" name="q" type="search" value="<%= Html.escAttr(v.query) %>" placeholder="e.g. pheidole megacephala, casent0103190, madagascar" maxlength="100">
      <button type="submit">Find</button>
    </div>
  </form>
  <p><a href="<%= Html.escAttr(self) %>?samples=1">Show sample fields with tables, old formatting, images, videos and Word styles</a>
  <% if (v.selected || v.query.length() > 0 || v.showSamples) { %> &middot; <a href="<%= Html.escAttr(self) %>">Empty editors</a><% } %></p>

<% if (v.error != null) { %>
  <p class="et-error"><%= Html.esc(v.error) %></p>
<% } %>

<%
    List<List<EditorTrialAction.Match>> lists = new ArrayList<>();
    if (v.query.length() >= 2) lists.add(v.matches);
    if (v.showSamples) lists.add(v.samples);
    for (List<EditorTrialAction.Match> list : lists) {
        boolean isSamples = list == v.samples;
%>
  <h3><%= isSamples ? "Sample fields" : "Fields matching \"" + Html.esc(v.query) + "\"" %> (<%= list.size() %>)</h3>
<%      if (list.isEmpty()) { %>
  <p>None found.</p>
<%      } else { %>
  <ul class="et-matches">
<%          String lastGroup = null;
            for (EditorTrialAction.Match m : list) {
                String group = m.src + "|" + m.key + (isSamples ? "|" + m.why : "");
                String href = self + "?src=" + Html.url(m.src) + "&key=" + Html.url(m.key) + "&field=" + Html.url(m.field);
                if (!group.equals(lastGroup)) {
                    if (lastGroup != null) out.print("</li>");
                    out.print("<li><span class=\"et-src\">" + Html.esc(m.src) + "</span> <b>" + Html.esc(m.key) + "</b>"
                        + (isSamples ? " <span class=\"et-why\">" + Html.esc(m.why) + "</span>" : "") + ": ");
                    lastGroup = group;
                }
                out.print("<a href=\"" + Html.escAttr(href) + "\">" + Html.esc(m.field) + "</a> <span class=\"et-len\">("
                    + m.length + ")</span> ");
            }
            if (lastGroup != null) out.print("</li>");
%>
  </ul>
<%      }
    } %>
</section>

<% if (v.selected) { %>
<section class="et-box">
  <h2>2. Stored text</h2>
  <p><span class="et-src"><%= Html.esc(v.src) %></span> <b><%= Html.esc(v.key) %></b>, field <b><%= Html.esc(v.field) %></b>
  <% if ("taxon".equals(v.src)) { %>(<a href="<%= ctx %>/description.do?taxonName=<%= Html.escAttr(Html.url(v.key)) %>" target="_blank" rel="noopener">open the taxon page</a>)<% } %>
  <% if ("specimen".equals(v.src)) { %>(<a href="<%= ctx %>/specimen.do?name=<%= Html.escAttr(Html.url(v.key)) %>" target="_blank" rel="noopener">open the specimen page</a>)<% } %>
  </p>
<%   if (!v.found) { %>
  <p class="et-error">No stored text for this field. The editors start empty.</p>
<%   } else { %>
  <ul class="et-facts">
    <li><%= v.stored.length() %> characters stored.</li>
    <li><%= v.stored.equals(v.storedSanitized)
        ? "The stored text is unchanged by the sanitizer."
        : "The sanitizer changes the stored text (" + v.stored.length() + " to " + v.storedSanitized.length() + " characters). The round-trip check compares against the sanitized form, as a save would store it." %></li>
<%     if (v.dequoteChanges) { %>
    <li class="et-warn">The current pages strip or collapse quote marks in this text when they load it (Formatter.dequote). This page loads the stored text exactly.</li>
<%     } %>
<%     if (v.rowCount > 1) { %>
    <li class="et-warn"><%= v.rowCount %> stored rows have this key and field; the first is shown.</li>
<%     } %>
<%     if (v.videos) { %>
    <li>Videos field: YouTube and Vimeo embeds are allowed, as on save.</li>
<%     } %>
  </ul>
  <details>
    <summary>Stored HTML</summary>
    <pre class="et-pre"><%= Html.esc(v.stored) %></pre>
  </details>
<%   } %>
</section>
<% } %>

<textarea id="et-stored" hidden><%= Html.esc(v.stored) %></textarea>
<textarea id="et-stored-sanitized" hidden><%= Html.esc(v.storedSanitized) %></textarea>

<section class="et-box">
  <h2><%= v.selected ? "3" : "2" %>. Compare the editors</h2>
  <p class="et-opts">
    <label><input type="checkbox" id="et-cleanpaste" checked> Clean Word and Google Docs markup on paste (removes mso- styles, Mso classes and empty spans)</label>
  </p>
  <div class="et-grid">
<%  String[][] eds = {
        {"hugerte", "HugeRTE 1.0.14", "MIT licence"},
        {"tinymce", "TinyMCE " + PageEditor.TINYMCE_VERSION, "GPL 2+ licence, self-hosted"}};
    for (String[] e : eds) { %>
    <div class="et-panel" id="panel-<%= e[0] %>">
      <h3><%= e[1] %> <span class="et-lic"><%= e[2] %></span></h3>
      <p class="et-status" id="status-<%= e[0] %>">Loading the editor...</p>
      <textarea id="ed-<%= e[0] %>" class="et-editor"><%= Html.esc(v.stored) %></textarea>
      <div class="et-buttons">
        <button type="button" data-act="check" data-ed="<%= e[0] %>">Round-trip check</button>
        <button type="button" data-act="html" data-ed="<%= e[0] %>">Show HTML</button>
        <button type="button" data-act="reset" data-ed="<%= e[0] %>">Reset to stored text</button>
      </div>
      <div class="et-result" id="result-<%= e[0] %>" aria-live="polite"></div>
      <pre class="et-pre et-html" id="html-<%= e[0] %>" hidden></pre>
    </div>
<%  } %>
  </div>
</section>

<section class="et-box">
  <h2>How to test</h2>
  <ol>
    <li><b>Round trip.</b> Open a stored field, change nothing, and press <i>Round-trip check</i> under each editor. The check runs the editor's HTML
      through the same sanitizer as a real save and compares it with the stored text after the same sanitizer. <i>Identical</i> is best;
      <i>same after normalising</i> means only attribute order, quoting or white space differ (the page looks the same);
      otherwise the differences are listed (red: would be removed, green: would be added). The check also runs once by itself when each editor has loaded.</li>
    <li><b>Paste from Word.</b> In Word, select a paragraph with bold, italics, a superscript and a small table, copy it, click in an editor and paste
      (Ctrl+V, or Cmd+V on a Mac). Then press <i>Show HTML</i>, or use the editor's source button (<code>&lt;&gt;</code>) to see the HTML it would save.
      Try the same with the box above unticked to see what Word sends without the clean-up.</li>
    <li><b>Paste from Google Docs.</b> The same, copying from a Google Doc in the browser. Google Docs wraps pasted text in a bold tag with normal weight;
      the clean-up removes it.</li>
    <li><b>Tools.</b> Try bold, italic, underline, sub- and superscript, lists, a link, a table (Table menu), an image by address
      (Insert, Image; for example an image address from static.antweb.org), undo and redo, and remove formatting.</li>
    <li><b>Phone.</b> Open this page on a phone. The two editors are shown one above the other.</li>
  </ol>
  <p>Nothing is saved. Reloading the page puts the stored text back in both editors.</p>
</section>
</div>

<script src="<%= AntwebProps.getDomainApp() %><%= org.calacademy.antweb.util.Asset.url(application, "/common/editors/hugerte/1.0.14/hugerte.min.js") %>"></script>
<script src="<%= AntwebProps.getDomainApp() %><%= org.calacademy.antweb.util.Asset.url(application, PageEditor.TINYMCE_JS) %>"></script>
<script src="<%= AntwebProps.getDomainApp() %><%= org.calacademy.antweb.util.Asset.url(application, PageEditor.SETUP_JS) %>"></script>
<script src="<%= AntwebProps.getDomainApp() %><%= org.calacademy.antweb.util.Asset.url(application, "/common/editors/editorTrial.js") %>"></script>
</body>
</html>
