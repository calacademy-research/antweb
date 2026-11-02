<%-- Notes at the top of taxon and specimen pages, statically included by specimen-body.jsp and taxonPage-body.jsp:
     Not an ant, Not a Valid Taxon Name (taxon.addNotValidWarning()), and Red Flag with the specimen's issue
     (MessageMgr.getMessageDisplay) for a specimen flagged red. Docs: docs/flows/specimen-and-image-pages.md. --%>
<%@ page import="org.calacademy.antweb.upload.MessageMgr" %>

<%
    // Should not be above Classification
    // Was in web/common/taxonomicHierarchy.jsp
   if (!taxon.isAnt()) { %>
<h3><font color=red>Note</font>: Not an ant</h3>
<% } else if (taxon.addNotValidWarning()) { %>
<h3><font size=small color=red>Note</font>: Not a Valid Taxon Name</h3>
<% }
   //A.log("specimen-body.jsp flag:" + specimen.getFlag());
   if (specimen != null && "red".equals(specimen.getFlag())) {
    %>
<h3><font color=red>Red Flag<a title='Until resolved, specimen will not be available through advanced search and other Antweb features'>*</a>: <%= MessageMgr.getMessageDisplay(specimen.getIssue()) %></font></h3>
<% } %>