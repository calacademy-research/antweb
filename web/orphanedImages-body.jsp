<%-- Body of the Orphaned Images page (orphanedImages.do, admins): "Found N image files of M specimen codes that are
     not in AntWeb" (request attribute orphanedImageCount and the list orphanedImages), what an orphaned image is,
     and one line per specimen code linked to its images folder. Docs: docs/numbers.md row 99,
     docs/guides/curate-page.md. Tests: tests/e2e/test_count_check.py. --%><%@ page language="java" %>
<%@ page import = "org.calacademy.antweb.Formatter" %>
<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>

<jsp:useBean id="orphanedImages" scope="session" class="java.util.ArrayList" />

<!-- Body of Text Begins -->
<td colspan="3">
<table border="0" cellpadding="1" cellspacing="7" width="800">
<tr>
<td>
<%
  Object imageCountAttr = request.getAttribute("orphanedImageCount");
  long orphanImageCount = imageCountAttr instanceof Long ? (Long) imageCountAttr : 0;
%>
<span id="orphan-counts" data-images="<%= orphanImageCount %>" data-codes="<%= orphanedImages.size() %>">Found <%= Formatter.commaFormat(orphanImageCount) %> image files of <%= Formatter.commaFormat(orphanedImages.size()) %> specimen codes that are not in AntWeb</span>
<p id="orphan-note">An orphaned image is an image whose specimen code has no specimen record in AntWeb (the specimen was deleted, renamed, or never uploaded). Each specimen code below can have several images.</p>
<p>
<ul>

<logic:iterate id="row" name="orphanedImages">
<li>
<a href="images/<bean:write name="row"/>">
<bean:write name="row"/>
</a>

</logic:iterate>

</ul>
</td>
</tr>
</table>
<!-- Body of Text Ends -->
