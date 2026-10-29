<%-- Body of the Specimen List Lookup: the action's message and errors, the results (request attribute
     lookupResult: the TSV download link, codes not found and codes with spaces, each downloadable as a text file
     in the browser), instructions, the template file and the upload form (POST, multipart, with the form token). --%>
<%@ page language="java" %>
<%@ page errorPage="/error.jsp" %>
<%@ page import="org.calacademy.antweb.curate.specimen.SpecimenListLookupResult" %>
<%@ page import="org.calacademy.antweb.util.AntwebProps" %>
<%@ page import="java.util.List" %>

<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>

<%
    String domainApp = AntwebProps.getDomainApp();
    String message = (String) request.getAttribute("message");
    SpecimenListLookupResult result = (SpecimenListLookupResult) request.getAttribute("lookupResult");
%>

<div class="admin_left">
    <h1>Specimen List Lookup</h1>
    <div style="color:#666; font-style:italic; margin-bottom:20px;">
        Read-Only tool. Safe to use for reconciliation workflows. No database modifications will occur.
    </div>

    <% if (message != null && !message.isEmpty()) { %>
        <div role="alert" style="border:1px solid #b30000; padding:10px; color:#b30000; background-color:#fee; margin-bottom:20px; font-weight:bold;">
            <%= message %>
            <% if (result != null) { %>
                <ul style="margin-bottom:0;">
                    <% for (String error : result.getErrors()) { %>
                        <li><%= error %></li>
                    <% } %>
                    <% if (result.hasAdditionalErrors()) { %>
                        <li><%= result.getErrorCount() - result.getErrors().size() %> additional errors are not displayed.</li>
                    <% } %>
                </ul>
            <% } %>
        </div>
    <% } %>

    <% if (result != null && result.isSuccess() && result.hasProblems()) {
           java.text.NumberFormat nf = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US);
           List<String> notFound = result.getNotFoundCodes();
           List<String> invalid = result.getInvalidCodes(); %>
    <div class="admin_action_module" id="lookupResults">
        <div class="admin_action_item">
            <h2>Lookup Results</h2>
            <table style="margin-bottom:15px;">
                <tr><td style="padding-right:20px;">Specimen codes submitted:</td><td style="text-align:right;"><b><%= nf.format(result.getSubmittedCount()) %></b></td></tr>
                <tr><td style="padding-right:20px;">Found in AntWeb:</td><td style="text-align:right;"><b><%= nf.format(result.getFoundRowCount()) %></b></td></tr>
                <tr><td style="padding-right:20px;">Not found in AntWeb:</td><td style="text-align:right;"><b><%= nf.format(result.getNotFoundRowCount()) %></b></td></tr>
                <% if (!invalid.isEmpty()) { %>
                <tr><td style="padding-right:20px;">Not looked up (space or tab in the code):</td><td style="text-align:right;"><b><%= nf.format(invalid.size()) %></b></td></tr>
                <% } %>
            </table>
            <% if (result.getNotFoundRowCount() != notFound.size()) { %>
                <p style="color:#666;">The file lists some of the missing codes more than once; each is shown once below.</p>
            <% } %>

            <% if (result.getFoundRowCount() > 0) { %>
                <p><a href="<%= domainApp %>/specimenListLookup.do?download=tsv" id="downloadFound" style="font-weight:bold;">Download specimen data</a>
                   (<%= nf.format(result.getFoundRowCount()) %> rows, specimen_list_lookup.tsv)</p>
            <% } else { %>
                <p><b>None of the specimen codes in the file are in AntWeb, so there is no specimen data to download.</b></p>
            <% } %>

            <% if (!notFound.isEmpty()) { %>
            <h3 style="margin-top:20px;">Codes not found in AntWeb (<%= nf.format(notFound.size()) %>)</h3>
            <p style="margin:5px 0;">One code per line, as typed in your file. Copy them into a file, query or spreadsheet.</p>
            <textarea id="notFoundCodes" readonly rows="<%= Math.min(15, Math.max(3, notFound.size())) %>" cols="40"
                      style="font-family:monospace; width:100%; max-width:420px;"><%= SpecimenListLookupResult.escapeHtml(result.getNotFoundText()) %></textarea>
            <div style="margin-top:8px;">
                <button type="button" onclick="copyCodes('notFoundCodes', 'copyStatus')">Copy to clipboard</button>
                <button type="button" onclick="downloadCodes('notFoundCodes', 'specimen_codes_not_found.txt')">Download specimen_codes_not_found.txt</button>
                <span id="copyStatus" aria-live="polite" style="margin-left:10px; color:#666;"></span>
            </div>
            <% } %>

            <% if (!invalid.isEmpty()) { %>
            <h3 style="margin-top:20px;">Codes with a space or tab, not looked up (<%= nf.format(invalid.size()) %>)</h3>
            <p style="margin:5px 0;">AntWeb specimen codes never contain spaces or tabs. These lines are shown as typed, in file order; correct them and look them up again.</p>
            <textarea id="invalidCodes" readonly rows="<%= Math.min(15, Math.max(3, invalid.size())) %>" cols="40"
                      style="font-family:monospace; width:100%; max-width:420px; white-space:pre;"><%= SpecimenListLookupResult.escapeHtml(result.getInvalidText()) %></textarea>
            <div style="margin-top:8px;">
                <button type="button" onclick="copyCodes('invalidCodes', 'copyStatus2')">Copy to clipboard</button>
                <button type="button" onclick="downloadCodes('invalidCodes', 'specimen_codes_with_spaces.txt')">Download specimen_codes_with_spaces.txt</button>
                <span id="copyStatus2" aria-live="polite" style="margin-left:10px; color:#666;"></span>
            </div>
            <% } %>
        </div>
    </div>
    <% } %>

    <div class="admin_action_module">
        <div class="admin_action_item">
            <h2>Lookup Instructions</h2>
            <p>Upload a plain <b>.txt</b> file containing specimen codes. The lookup returns a tab-delimited file with the specimen details stored in AntWeb.</p>
            <ul>
                <li>The filename must contain <b><code>specimen</code></b>.</li>
                <li>Good filename: <code>my_specimens.txt</code></li>
                <li>Bad filename: <code>codes.txt</code></li>
                <li>Enter one specimen code per line.</li>
                <li>Do not include spaces or tabs in specimen codes. Lines that contain them are listed on the results page and not looked up; the rest of the file is still processed.</li>
                <li>Maximum: <b>5 MB and 100,000 specimen codes</b>, whichever limit is reached first.</li>
                <li>If every code is in AntWeb, the tab-delimited file downloads directly. If some codes are not in AntWeb, they are listed on a results page, and the data for the codes that were found can still be downloaded there.</li>
            </ul>

            <p><b>Example file contents:</b></p>
            <div style="background-color:#f5f5f5; padding:10px; margin:5px 0 15px 0; font-family:monospace;">
                casent0104501<br>
                casent0104502<br>
                casent0179915
            </div>

            <p><a href="<%= domainApp %>/data/specimen_list_lookup_template.txt" download>Download Template File</a></p>
        </div>
    </div>

    <div class="admin_action_module">
        <div class="admin_action_item">
            <h2>Upload File</h2>
            <html:form method="POST" action='<%= org.calacademy.antweb.util.Csrf.action(request, "specimenListLookup.do") %>' enctype="multipart/form-data"
                    styleId="specimenLookupForm" onsubmit="return validateSpecimenLookupFile();"><%= org.calacademy.antweb.util.Csrf.field(request) %>
                <div style="margin-bottom:15px;">
                    <label for="specimenFile" style="display:block; font-weight:bold; margin-bottom:5px;">Choose specimen list file</label>
                    <html:file property="file" styleId="specimenFile" accept=".txt" />
                    <div id="specimenFilenameError" role="alert" aria-live="polite"
                            style="display:none; color:#b30000; font-weight:bold; margin-top:8px;"></div>
                </div>
                <div class="align_left">
                    <input type="submit" value="Run Specimen Lookup" style="padding:5px 15px;" />
                </div>
                <div class="clear"></div>
            </html:form>
        </div>
    </div>
</div>

<script type="text/javascript">
function copyCodes(boxId, statusId) {
    var box = document.getElementById(boxId);
    var status = document.getElementById(statusId);
    var done = function () { status.innerHTML = 'Copied.'; };
    if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(box.value).then(done, function () { box.select(); document.execCommand('copy'); done(); });
    } else {
        box.select();
        document.execCommand('copy');
        done();
    }
}

function downloadCodes(boxId, fileName) {
    var text = document.getElementById(boxId).value + '\n';
    var blob = new Blob([text], {type: 'text/plain;charset=utf-8'});
    var link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    setTimeout(function () { URL.revokeObjectURL(link.href); link.remove(); }, 1000);
}

function validateSpecimenLookupFile() {
    var input = document.getElementById('specimenFile');
    var error = document.getElementById('specimenFilenameError');
    var filename = input && input.value ? input.value.split(/[/\\]/).pop() : '';
    var message = '';

    if (!filename) {
        message = 'Choose a specimen list file before submitting.';
    } else if (filename.toLowerCase().slice(-4) !== '.txt') {
        message = 'File must be a plain .txt file.';
    } else if (filename.toLowerCase().indexOf('specimen') === -1) {
        message = "Filename must contain 'specimen'.";
    }

    if (message) {
        error.innerHTML = message;
        error.style.display = 'block';
        input.focus();
        return false;
    }
    error.innerHTML = '';
    error.style.display = 'none';
    return true;
}
</script>
