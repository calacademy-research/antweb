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

    <div class="admin_action_module">
        <div class="admin_action_item">
            <h2>Lookup Instructions</h2>
            <p>Upload a plain <b>.txt</b> file containing specimen codes. The lookup returns a tab-delimited file with the specimen details stored in AntWeb.</p>
            <ul>
                <li>The filename must contain <b><code>specimen</code></b>.</li>
                <li>Good filename: <code>my_specimens.txt</code></li>
                <li>Bad filename: <code>codes.txt</code></li>
                <li>Enter one specimen code per line.</li>
                <li>Do not include spaces or tabs in specimen codes.</li>
                <li>Maximum: <b>5 MB and 100,000 specimen codes</b>, whichever limit is reached first.</li>
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
            <html:form method="POST" action="specimenListLookup.do" enctype="multipart/form-data"
                    styleId="specimenLookupForm" onsubmit="return validateSpecimenLookupFile();">
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
