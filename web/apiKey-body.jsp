<%-- Body of apiKey.do (ApiKeyAction): register for a free API key. By the request attribute apiKeyState: "closed"
     (Registration opens soon), "form" (name, email, institution or affiliation, project, agree to the terms, the
     Turnstile check; apiKeyError and apiKeyForm refill it after a problem) or "sent" (check your email). The form
     posts to apiKey.do on the secure domain with the CSRF token. Tests: tests/e2e/test_api_keys.py. --%><%@ page errorPage = "error.jsp" %>
<%@ page import = "org.calacademy.antweb.util.*" %>

<!-- apiKey-body.jsp (ApiKeyAction) -->
<br><br>
<%
    String secureDomainApp = AntwebProps.getSecureDomainApp();
    String apiKeyState = (String) request.getAttribute("apiKeyState");
    String apiKeyError = (String) request.getAttribute("apiKeyError");
    ApiKeys.Registration f = (ApiKeys.Registration) request.getAttribute("apiKeyForm");
    if (f == null) f = new ApiKeys.Registration();
    String box = "border: 1px solid #B9B9B9; font-family: inherit; padding: 2px; width: 100%; max-width: 420px; box-sizing: border-box;";
%>
<div class="admin_left" id="api-key-page">

    <div class="admin_action_module">
        <div class="admin_action_item"><h1>Free AntWeb API key</h1></div>

<% if ("closed".equals(apiKeyState)) { %>
<p id="api-key-closed"><b>Registration opens soon.</b></p>
<p>Free API keys will be available here when the new version of the AntWeb API opens. The <a href="<%= ApiKeys.DOCS_URL %>">API documentation</a> and its <a href="<%= ApiKeys.TERMS_URL %>">terms of use</a> say what is planned.</p>
<p>Working on a larger project? Brian L. Fisher welcomes collaborations; write to <a href="mailto:antweb@antweb.org">antweb@antweb.org</a>.</p>

<% } else if ("sent".equals(apiKeyState)) { %>
<p id="api-key-sent"><b>Thank you. Please check your email.</b></p>
<p>We have sent a link to <b><%= Html.esc(f.email) %></b>. Open it within <%= ApiKeys.CONFIRM_HOURS %> hours to see your API key. The key is shown only once, on that page, so be ready to copy it.</p>
<p>If no email arrives within a few minutes, please check your spam folder. Questions: <a href="mailto:antweb@antweb.org">antweb@antweb.org</a>.</p>

<% } else { %>
<p>A free API key lets your program or research workflow use the <a href="<%= ApiKeys.DOCS_URL %>">AntWeb API</a> with higher limits than requests without a key. It is free and needs no AntWeb account. We will email you a link; the key is shown once when you open it.</p>

<% if (apiKeyError != null) { %>
<p id="api-key-error"><font color="red"><b><%= Html.esc(apiKeyError) %></b></font></p>
<% } %>

<form name="apiKeyForm" id="api-key-form" method="POST" action="<%= secureDomainApp %>/apiKey.do"><%= Csrf.field(request) %>
<p><label for="api-key-name">Your name</label><br>
<input type="text" id="api-key-name" name="name" required maxlength="<%= ApiKeys.MAX_NAME %>" autocomplete="name" value="<%= Html.escAttr(f.name) %>" style="<%= box %>"></p>
<p><label for="api-key-email">Email address</label><br>
<input type="email" id="api-key-email" name="email" required maxlength="<%= ApiKeys.MAX_EMAIL %>" autocomplete="email" value="<%= Html.escAttr(f.email) %>" style="<%= box %>"></p>
<p><label for="api-key-institution">Institution or affiliation (optional)</label><br>
<input type="text" id="api-key-institution" name="institution" maxlength="<%= ApiKeys.MAX_INSTITUTION %>" autocomplete="organization" value="<%= Html.escAttr(f.institution) %>" style="<%= box %>"></p>
<p><label for="api-key-project">Your project: what will you use the API for?</label><br>
<textarea id="api-key-project" name="project" required rows="5" maxlength="<%= ApiKeys.MAX_PROJECT %>" style="<%= box %>"><%= Html.esc(f.project) %></textarea></p>
<p><label><input type="checkbox" name="agree" value="yes"<%= f.agreed ? " checked" : "" %> required> I agree to the <a href="<%= ApiKeys.TERMS_URL %>" target="_blank" rel="noopener">terms of use</a></label></p>
<% if (Turnstile.isEnabled()) { %>
<div class="cf-turnstile" data-sitekey="<%= Html.escAttr(Turnstile.getSiteKey()) %>"></div>
<script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
<% } %>
<p><input type="submit" class="tool_label" style="float:none;" value="Email me the link"></p>
</form>

<p>We keep your name, email address, institution and project description, and count the requests made with your key, to run the service and to see how the API is used (see the terms). We do not share them.</p>
<p>Working on a larger project? Brian L. Fisher welcomes collaborations; write to <a href="mailto:antweb@antweb.org">antweb@antweb.org</a>.</p>
<% } %>

    </div>
</div>

<div class="admin_right"> </div>
