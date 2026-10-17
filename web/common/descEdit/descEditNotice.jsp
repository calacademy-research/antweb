<%-- descEditNotice.jsp: the outcome of a page-text save, and a warning when the page text could
     not be loaded. Statically included by taxonPage-body, specimen-body and overview-body after
     desc, accessLogin and canEditDesc are set. See DescEditSafety.java. --%>
<%
  {
    DescEditSafety.Result neResult = DescEditSafety.result(request);
    if (neResult != null) {
      if (neResult.saved) {
        String neKey = DescEditSafety.draftKey(request, accessLogin, neResult.field); %>
  <div class="descEditSaved" style="border:2px solid #2e7d32;background:#f1f8f1;padding:8px;margin:8px 0"><b><%= Html.esc(neResult.message) %></b></div>
<%      if (neKey != null) { // The text is stored: the browser draft is no longer needed. %>
  <script>try { window.localStorage.removeItem('<%= Html.js(neKey) %>'); } catch (e) {}</script>
<%      }
      } else if (!neResult.reshowInEditor) { %>
  <div id="descEditMessage" class="descEditRefused" style="border:2px solid #b00000;background:#fff3f3;padding:8px;margin:8px 0">
    <b><%= Html.esc(neResult.message) %></b>
<%      if (neResult.loggedOut) { %>
    <p><a href="<%= AntwebProps.getDomainApp() %>/login.do" target="_blank" rel="noopener">Log in (opens a new tab)</a></p>
<%      }
        if (neResult.submitted != null) { %>
    <p>Your text (copy it if you need it):</p>
    <textarea id="descEditKeptText" readonly style="width:100%;height:16em"><%= Html.esc(neResult.submitted) %></textarea>
<%      } %>
  </div>
<%    } else { %>
  <div class="descEditRefused" style="border:2px solid #b00000;background:#fff3f3;padding:8px;margin:8px 0">
    <b>Your text was not saved. See the message at the <a href="#<%= Html.escAttr(neResult.field) %>"><%= Html.esc(DescEditSafety.prettyField(neResult.field)) %></a> editor below; your text is still there.</b>
  </div>
<%    }
    }
    if (DescEditSafety.loadFailed(desc)) { %>
  <div class="descEditLoadFailed" style="border:2px solid #b07000;background:#fff8e6;padding:8px;margin:8px 0">
    Some of this page's text could not be loaded from the database just now, so parts of the page may be missing. Please reload the page in a minute.
  </div>
<%  }
  } %>
