<%-- Results page of the old Bay Area search (forward bayAreaSearch of prepareMapResults.do and
     prepareCompareResults.do with resultRank=bayArea): tiles wrapper of search/bayAreaSearchResults-body.jsp. Dead
     code: nothing puts Bay Area results in the session (see BayAreaSearchResults). --%>
<%@ taglib uri="/WEB-INF/struts-bean.tld" prefix="bean" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-logic.tld" prefix="logic" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<%@include file="/common/antweb-defs.jsp" %>

<tiles:insert beanName="antweb.default" beanScope="request" flush="true">
	<tiles:put name="title" value="Results of Bay Area Search - AntWeb" />
	<tiles:put name="body-content" value="/search/bayAreaSearchResults-body.jsp" />	
</tiles:insert>