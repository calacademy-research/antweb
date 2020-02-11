<!-- taxonTitle.jsp -->
<br>
<!-- functionality unnecessarily replicated in specimen-body.jsp --> 
<h1><%
   if (Utility.notBlank(taxon.getFullName())) {
     if (taxon instanceof Specimen) { %>
  <%= new Formatter().capitalizeFirstLetter(taxon.getRank()) %>: <%= taxon.getPrettyName() %>
<%   } else { %>
  <%= new Formatter().capitalizeFirstLetter(taxon.getRank()) %>: <%= dagger %><%= taxon.getPrettyName() %>
<%   }
   } else {
     String target = HttpUtil.getTarget(request);
     AntwebUtil.log("taxonTitle.jsp isBlank fullName:" + taxon.getFullName() + " taxonName:" + taxon.getTaxonName() + " target:" + target);
     if (target.contains(".jsp")) {
       AntwebUtil.log("taxonTitle.jsp isStaticCall error target:" + target);
       out.println("IsStatic Call Error.");
       return;
     }
     //AntwebUtil.logStackTrace();
     //out.println(title);
   }

   boolean isType = taxon.getIsType();
   if (taxon instanceof Specimen) { 
	 Specimen specimenTaxon = (Specimen) taxon; %>
	 <%= dagger %><%= specimenTaxon.getTaxonPrettyName() %> <%    
	 isType = specimenTaxon.getIsType();
   } 
 
   if (taxon.getAuthorDate() != null) {  %> 
	  <font size=2>&nbsp;&nbsp;<%= taxon.getAuthorDate() %>&nbsp;</font>
<%   }
   // A.log("taxonTitle.jsp taxonPrettyName:" + taxon.getPrettyName() + " class:" + taxon.getClass()); 

%>
<%@include file="/common/statusDisplayTitle.jsp" %>

<% if (isType) { %>
<a href='<%= AntwebProps.getDomainApp() %>/common/statusDisplayPage.jsp#type' target="new">
<img src="<%= AntwebProps.getDomainApp() %>/image/has_type_status_icon.png" title="Has type specimens">
</a>
<% } %>
</h1>