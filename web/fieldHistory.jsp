<%@ page errorPage = "error.jsp" %>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%-- fieldHistory.jsp: versions of one page-text field, with Restore (FieldHistoryAction, DescHistory). Curators only. --%>

<bean:define id="showNav" value="search" toScope="request"/>

<%@include file="common/antweb-defs.jsp" %>
<%
  org.calacademy.antweb.DescHistory fhTitleH = (org.calacademy.antweb.DescHistory) request.getAttribute(org.calacademy.antweb.DescHistory.ATTR);
  String fhTitle = (fhTitleH == null ? "Page text history" : "History of " + fhTitleH.prettyField() + " - " + fhTitleH.key.displayName()) + " - AntWeb";
%>
<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="<%= org.calacademy.antweb.util.Html.escAttr(fhTitle) %>" />
	<tiles:put name="body-content" value="/fieldHistory-body.jsp" />
</tiles:insert>
