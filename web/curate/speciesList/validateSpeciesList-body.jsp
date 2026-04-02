<%@ page language="java" %>
<%@ page errorPage = "/error.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="org.calacademy.antweb.*" %>
<%@ page import="org.calacademy.antweb.util.*" %>
<%@ page import="org.calacademy.antweb.curate.speciesList.*" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<jsp:useBean id="validateSpeciesListForm" scope="request" class="org.calacademy.antweb.curate.speciesList.ValidateSpeciesListForm" />
<jsp:setProperty name="validateSpeciesListForm" property="*" />

<%
    String domainApp = AntwebProps.getDomainApp();
    String message = (String) request.getAttribute("message");
    ValidateSpeciesReport report = (ValidateSpeciesReport) request.getAttribute("validationReport");
%>

<div class="admin_left">

    <h1>Validate Species List</h1>
    <div style="color: #666; font-style: italic; margin-bottom: 20px;">
        Read-Only Validator. Safe to use for reconciliation workflows. No database modifications will occur.
    </div>

    <% if (message != null && !message.isEmpty()) { %>
        <div style="border: 1px solid red; padding: 10px; color: red; background-color: #fee; margin-bottom: 20px; font-weight: bold;">
            <%= message %>
        </div>
    <% } %>

    <div class="admin_action_module">
        <div class="admin_action_item">
            <h2>Validation Instructions</h2>
            <p>Upload a tab-delimited <b>.txt</b> or <b>.tsv</b> file. Maximum file size: <b>5MB (approx. 50,000 rows)</b>. The first row must contain column headers.</p>
            
            <p><b>Two formats are supported:</b></p>
            
            <div style="margin-left: 20px;">
                <b>Format 1 (Separate Columns):</b> Required headers: <code>genus</code>, <code>species</code>. Optional: <code>subfamily</code>, <code>subspecies</code>.<br>
                <div style="background-color: #f5f5f5; padding: 10px; margin: 5px 0 15px 0; font-family: monospace;">
                    subfamily&#9;genus&#9;species&#9;subspecies<br>
                    Myrmicinae&#9;Acromyrmex&#9;balzani&#9;multituber<br>
                    Dorylinae&#9;Aenictus&#9;clavatus&#9;atripennis
                </div>

                <b>Format 2 (Combined Taxon):</b> Required header: <code>taxon_name</code> (case-insensitive).<br>
                <div style="background-color: #f5f5f5; padding: 10px; margin: 5px 0; font-family: monospace;">
                    subfamily&#9;taxon_name<br>
                    Myrmicinae&#9;Acromyrmex balzani multituber<br>
                    Dorylinae&#9;Aenictus clavatus atripennis
                </div>
            </div>

            <br>
            <p><a href="<%= domainApp %>/data/validateSpeciesList_template.txt" download>Download Template File</a></p>
        </div>
    </div>

    <div class="admin_action_module">
        <div class="admin_action_item">
            <h2>Upload File for Validation</h2>
            
            <html:form method="POST" action="validateSpeciesList.do" enctype="multipart/form-data">
                <input type="hidden" name="action" value="" />
                
                <div class="action_browse" style="margin-bottom: 15px;">
                    <html:file property="file" accept=".txt,.tsv"/>
                </div>
                
                <div style="margin-bottom: 15px;">
                    <html:checkbox property="showUnmatched" value="true" />
                    <label for="showUnmatched">Also report valid AntWeb taxa not present in my list</label>
                </div>
                
                <div class="align_left">
                    <input type="submit" value="Validate Species List" style="padding: 5px 15px;" />
                </div>
                <div class="clear"></div>
            </html:form>
        </div>
    </div>


    <% if (report != null) { %>
        <div style="margin-top: 30px;">
            <h2>Validation Report Summary</h2>
            <table border="1" cellpadding="5" cellspacing="0" style="border-collapse: collapse; margin-bottom: 15px;">
                <tr><th align="left">Total Rows Processed</th><td><%= report.getTotalInputRows() %></td></tr>
                <tr><th align="left">Exact Matches</th><td style="color: green; font-weight: bold;"><%= report.getExactMatchCount() %></td></tr>
                <tr><th align="left">Not Found / Ambiguous</th><td style="color: orange; font-weight: bold;"><%= report.getProblemCount() %></td></tr>
                <tr><th align="left">Format Errors</th><td style="color: red; font-weight: bold;"><%= report.getFormatErrorCount() %></td></tr>
                <% if (validateSpeciesListForm.isShowUnmatched()) { %>
                    <tr><th align="left">Unmatched AntWeb Taxa</th><td style="font-weight: bold;"><%= report.getUnmatchedCount() %></td></tr>
                <% } %>
            </table>

            <div style="display: flex; gap: 10px; margin-bottom: 20px;">
                <html:form method="POST" action="validateSpeciesList.do">
                    <input type="hidden" name="action" value="download" />
                    <input type="hidden" name="showUnmatched" value="<%= validateSpeciesListForm.isShowUnmatched() %>" />
                    <input type="submit" value="Download Diagnostic TSV Report" style="padding: 5px 15px;" />
                </html:form>
                <html:form method="POST" action="validateSpeciesList.do">
                    <input type="hidden" name="action" value="downloadCorrected" />
                    <input type="hidden" name="showUnmatched" value="<%= validateSpeciesListForm.isShowUnmatched() %>" />
                    <input type="submit" value="Download Corrected Curated Dataset" style="padding: 5px 15px; font-weight: bold; color: green;" />
                </html:form>
            </div>

            <% if (report.getProblemCount() > 0 || report.getFormatErrorCount() > 0) { %>
                <h3>Problems & Format Errors Log</h3>
                <table border="1" cellpadding="5" cellspacing="0" style="border-collapse: collapse; width: 100%;">
                    <tr style="background-color: #eee;">
                        <th>Row</th>
                        <th>Input Raw</th>
                        <th>Normalized Taxon Name</th>
                        <th>Status</th>
                        <th>Message</th>
                        <th>Suggestion</th>
                    </tr>
                    
                    <% 
                    List<ValidateSpeciesResultItem> issues = new ArrayList<>();
                    issues.addAll(report.getFormatErrors());
                    issues.addAll(report.getProblems());
                    issues.sort((a, b) -> Integer.compare(a.getRowNum(), b.getRowNum()));
                    
                    for (ValidateSpeciesResultItem item : issues) { 
                        String bg = item.getStatus() == ValidateSpeciesResultItem.Status.FORMAT_ERROR ? "#ffe6e6" : "#fff3cd";
                    %>
                        <tr style="background-color: <%= bg %>;">
                            <td><%= item.getRowNum() %></td>
                            <td><%= item.getInputRaw() != null ? item.getInputRaw().replace("<", "&lt;").replace(">", "&gt;") : "" %></td>
                            <td><%= item.getNormalizedName() != null ? item.getNormalizedName() : "" %></td>
                            <td style="font-weight: bold;"><%= item.getStatus().name() %></td>
                            <td><%= item.getMessage() %></td>
                            <% if (item.getSuggestion() != null && !item.getSuggestion().isEmpty()) { %>
                                <td style="font-weight: bold; color: #000;"><%= item.getSuggestion() %>
                                    <% if (item.getStatus() == ValidateSpeciesResultItem.Status.NOT_FOUND) { %>
                                        <div style="font-size: 0.8em; color: green; margin-top: 4px;">↑ Try replacing with this valid name.</div>
                                    <% } %>
                                </td>
                            <% } else { %>
                                <td></td>
                            <% } %>
                        </tr>
                    <% } %>
                </table>
            <% } %>

            <% if (report.getFormatErrorCount() == 0 && report.getProblemCount() == 0) { %>
                <div style="color: green; font-weight: bold; margin-top: 10px;">
                    ✓ All rows matched valid extant taxa exactly.
                </div>
            <% } %>
            
            <% if (validateSpeciesListForm.isShowUnmatched() && !report.getUnmatchedValidTaxa().isEmpty()) { %>
                 <h3 style="margin-top: 30px;">Unmatched Valid AntWeb Taxa (Preview)</h3>
                 <div style="max-height: 200px; overflow-y: scroll; border: 1px solid #ccc; padding: 10px; background-color: #f9f9f9; font-family: monospace;">
                 <% 
                    int maxPreview = Math.min(100, report.getUnmatchedValidTaxa().size());
                    for (int i=0; i<maxPreview; i++) {
                         out.println(report.getUnmatchedValidTaxa().get(i) + "<br>");
                    }
                    if (report.getUnmatchedValidTaxa().size() > 100) {
                         out.println("<br><i>... and " + (report.getUnmatchedValidTaxa().size() - 100) + " more (Download TSV for full list)</i>");
                    }
                 %>
                 </div>
            <% } %>
            
        </div>
    <% } %>

</div>
