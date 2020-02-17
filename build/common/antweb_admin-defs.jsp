<!-- antweb_admin-defs..  This page defines the elements of the layout that are common to a majority
    of the pages: the navigation bar, header and copyright information-->
    
    
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>

<tiles:definition
	id="antweb.default"
	page="/layouts/adminLayout.jsp"
	scope="request">
	
		<tiles:put name="adminHeader" value="/common/adminHeader.jsp" />
				
</tiles:definition>
