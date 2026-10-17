<%@ page language="java" %>
<%@ page import = "org.calacademy.antweb.*" %>
<%@ page import = "org.calacademy.antweb.util.*" %>
<%@ page errorPage = "error.jsp" %>
<%-- fieldHistory-body.jsp: the versions of one page-text field (DescHistory, set by FieldHistoryAction).
     Each version: date, who saved it, size, the changes compared with the current text, a preview,
     its HTML source, and "Restore this version". Restore posts to the page's normal save
     (DescriptionAction.saveDescriptionEdit with restoreFrom), which applies the same rules as Save:
     login, CSRF token, sanitizer, conflict check against currentHash, history copy of the text it replaces. --%>
<%
  DescHistory h = (DescHistory) request.getAttribute(DescHistory.ATTR);
  if (h == null) return;
  String domainApp = AntwebProps.getDomainApp();
  Object showObj = request.getAttribute(FieldHistoryAction.SHOW_ATTR);
  int show = showObj instanceof Integer ? (Integer) showObj : 0;
  String pretty = h.prettyField();
  boolean videos = "videos".equals(h.field);
  String pageUrl = h.page == null ? null : domainApp + "/" + h.page;
  String currentText = h.current == null ? null : h.current.content;
  long[] budget = { TextDiff.PAGE_BUDGET };
  String selfUrl = DescHistory.url(h.key.toString(), h.field, h.page);
  String restoreAction = h.page == null ? null : domainApp + "/" + Csrf.stripParam(h.page);
%>
<link rel="stylesheet" href="<%= domainApp %><%= Asset.url(application, "/common/descEdit/fieldHistory.css") %>">
<div id="page_contents">
  <h1>History of <%= Html.esc(pretty) %></h1>
  <div class="clear"></div>
  <div class="page_divider"></div>
</div>
<div id="page_data" class="fh">
  <p class="fh-page">Page: <% if (pageUrl != null) { %><a href="<%= Html.escAttr(pageUrl + "#" + h.field) %>"><%= Html.esc(h.key.displayName()) %></a><% } else { %><%= Html.esc(h.key.displayName()) %><% } %></p>
  <p class="fh-help">Every save keeps the text it replaces, so each version below is the text as it was saved, with who saved it and when.
    <b>Restore this version</b> puts that text back as the current text; the text it replaces is kept here too, so a restore can itself be undone.
    Changes are shown compared with the current text: <del class="fh-del">struck out</del> is only in the current text (a restore removes it),
    <ins class="fh-ins">underlined</ins> is only in that version (a restore brings it back).</p>
<% if (h.page == null) { %>
  <p class="fh-note">Restore is not available here because the page address is missing. Open History with the link next to the field on its page.</p>
<% } %>

  <div class="fh-version fh-current" id="current">
    <div class="fh-meta"><span class="fh-label">Current text</span>
<% if (h.current != null) { %>
      saved <%= Html.esc(h.current.date()) %> by <b><%= Html.esc(h.current.who) %></b> &middot; <%= org.calacademy.antweb.Formatter.commaFormat(h.current.size()) %> characters
<% } %>
    </div>
<% if (h.current == null) { %>
    <p class="fh-empty">This field is empty now (it was cleared, or never had text).</p>
<% } else { %>
    <details class="fh-preview"><summary>Show the current text</summary>
      <div class="fh-render"><%= HtmlSanitizer.sanitize(h.current.content, videos) %></div>
    </details>
<% } %>
  </div>

<% if (h.versions.isEmpty()) { %>
  <p class="fh-empty">There are no earlier versions of this field.</p>
<% }
   for (DescHistory.Version v : h.versions) {
     String anchor = "v" + v.histId;
     if (v.isClear()) { %>
  <div class="fh-version fh-cleared" id="<%= anchor %>">
    <div class="fh-meta"><span class="fh-label">Cleared</span> <%= Html.esc(v.date()) %> by <b><%= Html.esc(v.who) %></b>
      &middot; the text before this is the version below</div>
  </div>
<%     continue;
     }
     // What a restore would store: the version after the sanitizer (old versions may predate it).
     String restored = HtmlSanitizer.sanitize(v.content, videos);
     boolean same = currentText != null && DescEditSafety.sameText(restored, currentText);
     String diff = null;
     if (currentText != null && !same) diff = TextDiff.readable(currentText, restored, v.histId == show ? null : budget);
%>
  <div class="fh-version" id="<%= anchor %>">
    <div class="fh-meta"><span class="fh-label">Version</span> saved <%= Html.esc(v.date()) %> by <b><%= Html.esc(v.who) %></b>
      &middot; <%= org.calacademy.antweb.Formatter.commaFormat(v.size()) %> characters<% if (same) { %> &middot; <span class="fh-same">the same as the current text</span><% } %></div>
<% if (!same) { %>
    <div class="fh-diff">
<%   if (currentText == null) { %>
      <p class="fh-small">The field is empty now; restoring brings back all of this text.</p>
<%   } else if (diff == null) { %>
      <p class="fh-small"><a href="<%= Html.escAttr(selfUrl + "&show=" + v.histId + "#" + anchor) %>">Show the changes compared with the current text</a> (long page: not shown for every version).</p>
<%   } else if (TextDiff.sameText(currentText, restored)) { %>
      <p class="fh-small">The words are the same as the current text; only the formatting (HTML) differs.</p>
<%   } else { %>
      <div class="fh-changes"><%= diff %></div>
<%   } %>
    </div>
<% } %>
    <details class="fh-preview"><summary>Preview this version</summary>
      <div class="fh-render"><%= restored %></div>
    </details>
    <details class="fh-source"<% if (v.histId == show) { %> open<% } %>><summary>HTML source<%= currentText != null && !same ? " and its changes" : "" %></summary>
<%   if (currentText != null && !same) {
       if (v.histId == show) { %>
      <pre class="fh-pre"><%= TextDiff.source(currentText, restored) %></pre>
<%     } else { %>
      <p class="fh-small"><a href="<%= Html.escAttr(selfUrl + "&show=" + v.histId + "#" + anchor) %>">Compare the HTML source with the current text</a></p>
<%     }
     } %>
      <textarea class="fh-copy" readonly rows="6"><%= Html.esc(v.content) %></textarea>
    </details>
<% if (restoreAction != null && !same) { %>
    <form method="POST" action="<%= Html.escAttr(restoreAction) %>" class="fh-restore"
          data-field="<%= Html.escAttr(pretty) %>" data-date="<%= Html.escAttr(v.date() + ", " + v.who) %>"><%= Csrf.field(request) %>
      <input type="hidden" name="editField" value="<%= Html.escAttr(h.field) %>">
      <input type="hidden" name="isSaveEditField" value="true">
      <input type="hidden" name="restoreFrom" value="<%= v.histId %>">
      <input type="hidden" name="loadedHash" value="<%= Html.escAttr(h.currentHash) %>">
      <input type="hidden" name="editorReady" value="1">
      <input type="hidden" name="clearField" value="0">
      <button type="submit" class="fh-restore-btn">Restore this version</button>
    </form>
<% } %>
  </div>
<% }
   if (h.truncated) { %>
  <p class="fh-note">Only the newest <%= DescHistory.MAX_VERSIONS %> versions are listed. Ask an administrator for older ones.</p>
<% } %>
</div>
<script type="text/javascript">
  window.AntwebFieldHistory = { sessionUrl: '<%= Html.js(domainApp + "/editSession.do") %>', loginUrl: '<%= Html.js(domainApp + "/login.do") %>' };
</script>
<script type="text/javascript" src="<%= domainApp %><%= Asset.url(application, "/common/descEdit/fieldHistory.js") %>"></script>
