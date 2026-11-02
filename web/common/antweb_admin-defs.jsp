<%-- The tiles definition antweb.default of the admin and curator tool pages: layout layouts/adminLayout.jsp
     (admLayout.jsp) with adminHeader common/adminHeader.jsp. Statically included by about 40 pages under web/util,
     web/curate and web/imageUploader, three common/descEdit image pages, and permissionDenied.jsp. Docs:
     docs/guides/page-style.md. --%><!-- antweb_admin-defs..  This page defines the elements of the layout that are common to a majority
    of the pages: the navigation bar, header and copyright information-->
    
    
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<tiles:definition
	id="antweb.default"
	page="/layouts/adminLayout.jsp"
	scope="request">
	
		<tiles:put name="adminHeader" value="/common/adminHeader.jsp" />
				
</tiles:definition>
