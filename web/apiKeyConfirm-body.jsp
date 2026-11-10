<%-- Body of apiKeyConfirm.do (ApiKeyConfirmAction): by apiKeyConfirm.state, "confirm" (a button that posts the
     token back and shows the key), "done" (the key, shown once, with how to send it: header X-API-Key), or why the
     link does not work (invalid, expired, used, cancelled, error). Tests: tests/e2e/test_api_keys.py. --%><%@ page errorPage = "error.jsp" %>
<%@ page import = "org.calacademy.antweb.util.*" %>

<!-- apiKeyConfirm-body.jsp (ApiKeyConfirmAction) -->
<br><br>
<%
    String secureDomainApp = AntwebProps.getSecureDomainApp();
    ApiKeyMgr.ConfirmPage p = (ApiKeyMgr.ConfirmPage) request.getAttribute("apiKeyConfirm");
    if (p == null) p = new ApiKeyMgr.ConfirmPage();
    String token = (String) request.getAttribute("apiKeyToken");
    String again = "<a href=\"" + secureDomainApp + "/apiKey.do\">register again</a>";
%>
<div class="admin_left" id="api-key-confirm-page">

    <div class="admin_action_module">
        <div class="admin_action_item"><h1>Your AntWeb API key</h1></div>

<% if ("done".equals(p.state)) { %>
<p><b>Your API key is ready.</b> Copy it now; we cannot show it again.</p>
<p><input type="text" id="api-key-value" readonly value="<%= Html.escAttr(p.key) %>" onclick="this.select()" aria-label="Your API key"
   style="font-family: monospace; font-size: 1.1em; padding: 4px; width: 100%; max-width: 460px; box-sizing: border-box; border: 1px solid #B9B9B9;">
   <button type="button" class="tool_label" style="float:none;" onclick="var k=document.getElementById('api-key-value');k.select();if(navigator.clipboard){navigator.clipboard.writeText(k.value);}">Copy</button></p>
<p>Keep it private: anyone with the key can use it in your name. We store only a fingerprint of it, so we cannot send it to you again; if you lose it, register again.</p>
<p><b>How to use it:</b> send the key in the <code>X-API-Key</code> header of every request, never in the web address. For example:</p>
<pre style="white-space: pre-wrap; word-break: break-all;">curl -H "X-API-Key: <%= Html.esc(p.key) %>" "https://www.antweb.org/v4/specimens?genus=Strumigenys&amp;limit=10"</pre>
<p>The keyed API is version 4: <a href="<%= ApiKeys.V4_URL %>"><%= ApiKeys.V4_URL %></a> (its limits, routes and field names). Documentation: <a href="<%= ApiKeys.DOCS_URL %>">AntWeb API</a>. Terms of use: <a href="<%= ApiKeys.TERMS_URL %>">Access and terms of use</a>.</p>
<p>Working on a larger project? Brian L. Fisher welcomes collaborations; write to <a href="mailto:antweb@antweb.org">antweb@antweb.org</a>.</p>

<% } else if ("confirm".equals(p.state) && token != null) { %>
<p>Thank you<%= p.name == null || p.name.isEmpty() ? "" : ", " + Html.esc(p.name) %>. Your email address is confirmed when you click the button. Your key is then shown <b>once</b>, on the next page: be ready to copy it.</p>
<form method="POST" action="<%= secureDomainApp %>/apiKeyConfirm.do" id="api-key-confirm-form"><%= Csrf.field(request) %>
<input type="hidden" name="token" value="<%= Html.escAttr(token) %>">
<p><input type="submit" class="tool_label" style="float:none;" value="Show my API key"></p>
</form>

<% } else if ("used".equals(p.state)) { %>
<p id="api-key-link-used"><b>This link has been used already.</b> The key was shown when it was first opened and cannot be shown again. If you did not copy it, please <%= again %>.</p>
<% } else if ("expired".equals(p.state)) { %>
<p id="api-key-link-expired"><b>This link has expired</b> (links work for <%= ApiKeys.CONFIRM_HOURS %> hours). Please <%= again %>.</p>
<% } else if ("cancelled".equals(p.state)) { %>
<p><b>This registration was cancelled.</b> Questions: <a href="mailto:antweb@antweb.org">antweb@antweb.org</a>.</p>
<% } else if ("error".equals(p.state)) { %>
<p><b>Sorry, there is a problem with our database.</b> Please open the link again later, or write to <a href="mailto:antweb@antweb.org">antweb@antweb.org</a>.</p>
<% } else { %>
<p id="api-key-link-invalid"><b>This link is not valid.</b> Please use the whole link from the email, or <%= again %>.</p>
<% } %>

    </div>
</div>

<div class="admin_right"> </div>
