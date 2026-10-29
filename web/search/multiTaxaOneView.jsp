<%-- One view of every image comparison row (compareResults.do?shot=..., CompareResultsAction; the view buttons
     request search/multiTaxaOneView.jsp?shot=... directly, which Caddy allows): tiles wrapper of
     search/multiTaxaOneView-body.jsp, title "Comparing Images - AntWeb". It only shows the session's taxaToCompare;
     no login check of its own. --%>
<%@ page errorPage = "/error.jsp" %>

<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Comparing Images - AntWeb" />
	<tiles:put name="body-content" value="/search/multiTaxaOneView-body.jsp" />	
</tiles:insert>
