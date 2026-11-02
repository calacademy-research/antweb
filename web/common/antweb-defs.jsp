<%-- The tiles definition antweb.default of the public pages: layout layouts/basicLayout.jsp with academyHeader
     common/academyHeader.jsp, siteNav common/siteNav.jsp and copyright common/copyright.jsp. Statically included by
     about 90 public pages (index.jsp, the place pages, taxon pages, search pages ...), which then insert
     antweb.default with their title and body-content. Docs: docs/guides/page-style.md. Tests:
     tests/e2e/test_tool_pages_style.py. --%><%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>


<tiles:definition
	id="antweb.default"
	page="/layouts/basicLayout.jsp"
	scope="request">
		<tiles:put name="academyHeader" value="/common/academyHeader.jsp" />
		<tiles:put name="siteNav" value="/common/siteNav.jsp" />
		<tiles:put name="copyright" value="/common/copyright.jsp" />
</tiles:definition>



